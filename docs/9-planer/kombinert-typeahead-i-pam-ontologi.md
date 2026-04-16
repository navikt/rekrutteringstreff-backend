# Typeahead for arbeidsgivers behov

## Bakgrunn

Arbeidsgiver beskriver sine behov ved å velge fra Janzz-forslag innen flere kategorier: **yrkestittel**, **kompetanse**, **fagbrev**, **førerkort** og **offentlige godkjenninger**. Vi trenger én samlet typeahead som dekker alle disse, med et felt som angir behovskategori per rad.

### Dagens arkitektur i pam-ontologi

Typeahead-data er i dag splittet i 6 separate tabeller, alle denormaliserte utsnitt av `konsept` + `term`:

| Tabell | KonseptType | Ekstra filtrering |
|---|---|---|
| `typeahead_stilling` | `S` | Kun `styrk08ssb` > 3 tegn |
| `typeahead_kompetanse` | `K` | Ingen |
| `typeahead_autorisasjon` | `A` | Ingen |
| `typeahead_godkjenninger` | `K` | Etterkommere av konsept 567151 |
| `typeahead_fagdokumentasjon` | `U` | Etterkommere av konsept 534435/11777/407667 |
| `typeahead_geografi` | `LO` | Bruker `no_description` som kode |

Alle har GIN trigram-indeks (`pg_trgm`) på `verdi_lc`. Førerkort finnes **ikke** i Janzz — i dag hardkodet som enum i frontend.

### Forkastede alternativer

Vi vurderte UNION ALL over eksisterende tabeller (vanskelig sortering, ingen samlet indeks), direkte spørring mot `konsept`-tabellen (for komplekst pga. subset-logikk for godkjenninger/fagdokumentasjon), og database VIEW (GIN-indekser brukes ikke gjennom UNION-views). Alle hadde svakheter rundt ytelse eller kompleksitet.

---

## Valgt løsning: Materialisert typeahead-tabell

Ny tabell `typeahead_arbeidsgivers_behov` med kun feltene som trengs, populert under Janzz-import. Førerkort hardkodes inn.

### Database-skjema

```sql
CREATE TABLE typeahead_arbeidsgivers_behov (
    id         serial PRIMARY KEY,
    konsept_id integer,                        -- nullable, NULL for førerkort
    label      varchar(500) NOT NULL,
    label_lc   varchar(500) NOT NULL,
    kategori   varchar(30) NOT NULL            -- YRKESTITTEL, KOMPETANSE, AUTORISASJON, GODKJENNING, FAGDOKUMENTASJON, FORERKORT
);

CREATE TABLE tmp_typeahead_arbeidsgivers_behov (LIKE typeahead_arbeidsgivers_behov INCLUDING ALL);

CREATE INDEX trgm_idx_behov ON typeahead_arbeidsgivers_behov USING gin (label_lc gin_trgm_ops);
CREATE INDEX idx_behov_kategori ON typeahead_arbeidsgivers_behov (kategori);
```

### Populering

Under Janzz-import (`InnlastingService`), etter at de eksisterende typeahead-tabellene er populert:

```sql
INSERT INTO tmp_typeahead_arbeidsgivers_behov (konsept_id, label, label_lc, kategori)
SELECT konsept_id, verdi, verdi_lc, 'YRKESTITTEL' FROM tmp_typeahead_stilling
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'KOMPETANSE' FROM tmp_typeahead_kompetanse
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'AUTORISASJON' FROM tmp_typeahead_autorisasjon
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'GODKJENNING' FROM tmp_typeahead_godkjenninger
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'FAGDOKUMENTASJON' FROM tmp_typeahead_fagdokumentasjon;
```

Førerkort settes inn statisk (i migrasjonen og i import-logikken):

```sql
INSERT INTO tmp_typeahead_arbeidsgivers_behov (konsept_id, label, label_lc, kategori) VALUES
(NULL, 'A1 - Lett motorsykkel', 'a1 - lett motorsykkel', 'FORERKORT'),
(NULL, 'A2 - Mellomtung motorsykkel', 'a2 - mellomtung motorsykkel', 'FORERKORT'),
(NULL, 'A - Tung motorsykkel', 'a - tung motorsykkel', 'FORERKORT'),
(NULL, 'B - Personbil', 'b - personbil', 'FORERKORT'),
(NULL, 'BE - Personbil med tilhenger', 'be - personbil med tilhenger', 'FORERKORT'),
(NULL, 'C1 - Lett lastebil', 'c1 - lett lastebil', 'FORERKORT'),
(NULL, 'C1E - Lett lastebil med tilhenger', 'c1e - lett lastebil med tilhenger', 'FORERKORT'),
(NULL, 'C - Lastebil', 'c - lastebil', 'FORERKORT'),
(NULL, 'CE - Lastebil med tilhenger', 'ce - lastebil med tilhenger', 'FORERKORT'),
(NULL, 'D1 - Minibuss', 'd1 - minibuss', 'FORERKORT'),
(NULL, 'D1E - Minibuss med tilhenger', 'd1e - minibuss med tilhenger', 'FORERKORT'),
(NULL, 'D - Buss', 'd - buss', 'FORERKORT'),
(NULL, 'DE - Buss med tilhenger', 'de - buss med tilhenger', 'FORERKORT'),
(NULL, 'T - Traktor', 't - traktor', 'FORERKORT'),
(NULL, 'S - Snøscooter', 's - snøscooter', 'FORERKORT');
```

### Søk og sortering

Søk bruker `pg_trgm` GIN-indeks med `ILIKE` for treff midt i ord:

```sql
SELECT id, konsept_id, label, kategori
FROM typeahead_arbeidsgivers_behov
WHERE label_lc ILIKE '%' || :sokeord || '%'
ORDER BY
  CASE
    WHEN label_lc = :sokeord THEN 0                          -- eksakt treff først
    WHEN label_lc LIKE :sokeord || '%' THEN 1                -- starter med søkeord
    WHEN label_lc LIKE '% ' || :sokeord || '%' THEN 2        -- ordgrense-treff
    ELSE 3                                                    -- substring-treff
  END,
  length(label_lc),                                           -- kortere labels prioriteres
  label_lc
LIMIT 50
```

Dersom sorteringen ikke gir god nok opplevelse, kan vi vurdere `pg_trgm`-similarity-funksjoner:

```sql
ORDER BY label_lc <-> :sokeord   -- trigram distance, krever GiST-indeks
LIMIT 50
```

Eller PostgreSQL fulltekstsøk med `ts_rank` for mer avansert relevansrangering.

### Flersøk (flere ord)

Når søkeordet inneholder mellomrom, split på ord og krev at **alle** ordene matcher (AND-logikk). Dette gir presise treff — søk på "syke pleier" gir kun rader som inneholder både "syke" og "pleier":

```sql
WHERE label_lc ILIKE '%' || :ord1 || '%'
  AND label_lc ILIKE '%' || :ord2 || '%'
```

### API-endepunkt

```
GET /rest/typeahead/arbeidsgivers-behov?q=<søkeord>
```

Valgfritt kategori-filter:

```
GET /rest/typeahead/arbeidsgivers-behov?q=sykepleier&kategorier=YRKESTITTEL,KOMPETANSE
```

### Respons-DTO

```kotlin
enum class BehovKategori {
    YRKESTITTEL, KOMPETANSE, AUTORISASJON, GODKJENNING, FAGDOKUMENTASJON, FORERKORT
}

data class ArbeidsgiversBehov(
    val konseptId: Long? = null,    // null for førerkort
    val label: String,
    val kategori: BehovKategori
)
```

---

## Implementeringsplan

1. **Flyway-migrasjon** (V8): `typeahead_arbeidsgivers_behov` + `tmp_typeahead_arbeidsgivers_behov` + GIN-indeks + statiske førerkort-rader
2. **Repository**: `slettOgIndekserTypeaheadArbeidsgiversBehov()` for populering under import + `finnTypeaheadArbeidsgiversBehov(oppslagsord)` for søk
3. **Service**: `finnTypeaheadArbeidsgiversBehov()` → mapping til `ArbeidsgiversBehov`
4. **Controller**: `GET /rest/typeahead/arbeidsgivers-behov?q=...`
5. **InnlastingService**: Kall `slettOgIndekserTypeaheadArbeidsgiversBehov()` i `lastInnTypeahead()`

## Relevante filer

- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/repository/JanzzRepository.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/service/TypeaheadService.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/rest/TypeaheadController.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/service/InnlastingService.kt`
- `pam-ontologi/src/main/resources/db/migration/`
