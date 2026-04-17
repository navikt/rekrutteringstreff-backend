# Typeahead for arbeidsgivers behov

## Bakgrunn

Arbeidsgiver beskriver sine behov ved å velge fra Janzz-forslag innen flere kategorier: **yrkestittel**, **kompetanse**, **fagbrev**, **førerkort** og **offentlige godkjenninger**. Vi trenger én samlet typeahead som dekker alle disse, med et felt som angir behovskategori per rad.

### Dagens arkitektur i pam-ontologi

Typeahead-data er i dag splittet i 6 separate tabeller, alle denormaliserte utsnitt av `konsept` + `term`:

| Tabell                       | KonseptType | Ekstra filtrering                           |
| ---------------------------- | ----------- | ------------------------------------------- |
| `typeahead_stilling`         | `S`         | Kun `styrk08ssb` > 3 tegn                   |
| `typeahead_kompetanse`       | `K`         | Ingen                                       |
| `typeahead_autorisasjon`     | `A`         | Ingen                                       |
| `typeahead_godkjenninger`    | `K`         | Etterkommere av konsept 567151              |
| `typeahead_fagdokumentasjon` | `U`         | Etterkommere av konsept 534435/11777/407667 |
| `typeahead_geografi`         | `LO`        | Bruker `no_description` som kode            |

Alle har GIN trigram-indeks (`pg_trgm`) på `verdi_lc`. Førerkort finnes **ikke** i Janzz — i dag hardkodet som enum i frontend.

Importen kjøres nattlig av `JanzzDownloadScheduledTask` og følger et **staging-mønster**: alle `tmp_`-tabeller fylles først, deretter truncates prod-tabellene og `tmp_`-tabellene overføres via `overforTypeahead(...)` i `InnlastingService.overforNedlastetOntologi()`. Til slutt tømmes `tmp_`-tabellene i `trunkerArbeidstabeller()`.

### Forkastede alternativer

Vi vurderte UNION ALL over eksisterende tabeller (vanskelig sortering, ingen samlet indeks), direkte spørring mot `konsept`-tabellen (for komplekst pga. subset-logikk for godkjenninger/fagdokumentasjon), og database VIEW (GIN-indekser brukes ikke gjennom UNION-views). Alle hadde svakheter rundt ytelse eller kompleksitet.

---

## Valgt løsning: Materialisert typeahead-tabell

Ny tabell `typeahead_arbeidsgivers_behov` med kun feltene som trengs, populert under Janzz-import fra de eksisterende `tmp_typeahead_*`-tabellene. Førerkort settes inn statisk i samme tabell under import.

Vi bruker `FORERKORT` (ASCII, uten æøå) som kategori-verdi i database og internt i backend, for å unngå encoding-fallgruver i SQL-litteraler og kolonneverdier. Enum-navnet i Kotlin er `FORERKORT`; hvis frontend trenger `FØRERKORT` i JSON kan det styres med Jackson-annotasjoner på enumen.

### Database-skjema

`typeahead_arbeidsgivers_behov` er en prod-tabell og får GIN trigram-indeks. Staging-tabellen opprettes bevisst **uten** GIN-indeks, i tråd med mønsteret i øvrige `tmp_typeahead_*`-tabeller (indeksen bygges først når data er ferdig populert på prod-tabellen ved import).

```sql
CREATE TABLE typeahead_arbeidsgivers_behov (
    id         serial PRIMARY KEY,
    konsept_id integer,                        -- nullable, NULL for førerkort
    label      varchar(500) NOT NULL,
    label_lc   varchar(500) NOT NULL,
    kategori   varchar(30) NOT NULL            -- YRKESTITTEL | KOMPETANSE | AUTORISASJON | GODKJENNING | FAGDOKUMENTASJON | FORERKORT
);

CREATE TABLE tmp_typeahead_arbeidsgivers_behov (
    id         serial PRIMARY KEY,
    konsept_id integer,
    label      varchar(500) NOT NULL,
    label_lc   varchar(500) NOT NULL,
    kategori   varchar(30) NOT NULL
);

CREATE INDEX trgm_idx_behov_label_lc ON typeahead_arbeidsgivers_behov USING gin (label_lc gin_trgm_ops);
CREATE INDEX idx_behov_kategori      ON typeahead_arbeidsgivers_behov (kategori);
```

### Populering under import

I `InnlastingService.lastInnTypeahead()` kalles `slettOgIndekserTypeaheadArbeidsgiversBehov("tmp_")` **etter** at alle de eksisterende `slettOgIndekserTypeaheadFor*("tmp_")`-kallene er ferdige, slik at den kan bygge på allerede filtrerte `tmp_typeahead_*`-tabeller.

Fordi vi leser fra de allerede rensede tmp_typeahead-tabellene, arver vi automatisk den eksisterende filtreringen (godkjenninger under 567151, fagdokumentasjon under 534435/11777/407667, stilling med styrk08ssb > 3 tegn) — og slipper å duplisere subset-logikken mot `konsept_relasjon`.

```sql
-- Del 1: Yrkestittel/kompetanse/autorisasjon/godkjenning/fagdokumentasjon fra tmp_
INSERT INTO tmp_typeahead_arbeidsgivers_behov (konsept_id, label, label_lc, kategori)
SELECT konsept_id, verdi, verdi_lc, 'YRKESTITTEL'      FROM tmp_typeahead_stilling
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'KOMPETANSE'       FROM tmp_typeahead_kompetanse
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'AUTORISASJON'     FROM tmp_typeahead_autorisasjon
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'GODKJENNING'      FROM tmp_typeahead_godkjenninger
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'FAGDOKUMENTASJON' FROM tmp_typeahead_fagdokumentasjon;

-- Del 2: Førerkort hardkodes (listen speiler gyldigeFørerkort i CV-prosjektet)
INSERT INTO tmp_typeahead_arbeidsgivers_behov (konsept_id, label, label_lc, kategori) VALUES
(NULL, 'A - Motorsykkel',                     'a - motorsykkel',                     'FORERKORT'),
(NULL, 'A1 - Lett motorsykkel',               'a1 - lett motorsykkel',               'FORERKORT'),
(NULL, 'A2 - Mellomtung motorsykkel',         'a2 - mellomtung motorsykkel',         'FORERKORT'),
(NULL, 'AM - Moped',                          'am - moped',                          'FORERKORT'),
(NULL, 'AM 147 - Mopedbil',                   'am 147 - mopedbil',                   'FORERKORT'),
(NULL, 'B - Personbil',                       'b - personbil',                       'FORERKORT'),
(NULL, 'B 78 - Personbil med automatgir',     'b 78 - personbil med automatgir',     'FORERKORT'),
(NULL, 'B 96 - Personbil med tilhenger',      'b 96 - personbil med tilhenger',      'FORERKORT'),
(NULL, 'BE - Personbil med tilhenger',        'be - personbil med tilhenger',        'FORERKORT'),
(NULL, 'C - Lastebil',                        'c - lastebil',                        'FORERKORT'),
(NULL, 'C1 - Lett lastebil',                  'c1 - lett lastebil',                  'FORERKORT'),
(NULL, 'C1E - Lett lastebil med tilhenger',   'c1e - lett lastebil med tilhenger',   'FORERKORT'),
(NULL, 'CE - Lastebil med tilhenger',         'ce - lastebil med tilhenger',         'FORERKORT'),
(NULL, 'D - Buss',                            'd - buss',                            'FORERKORT'),
(NULL, 'D1 - Minibuss',                       'd1 - minibuss',                       'FORERKORT'),
(NULL, 'D1E - Minibuss med tilhenger',        'd1e - minibuss med tilhenger',        'FORERKORT'),
(NULL, 'DE - Buss med tilhenger',             'de - buss med tilhenger',             'FORERKORT'),
(NULL, 'S - Snøscooter',                      's - snøscooter',                      'FORERKORT'),
(NULL, 'T - Traktor',                         't - traktor',                         'FORERKORT');
```

I tillegg må tabellen bakes inn i overførings- og oppryddingsflyten i `InnlastingService`:

- `trunkerArbeidstabeller()` må også droppe `tmp_typeahead_arbeidsgivers_behov`.
- `overforNedlastetOntologi()` må truncate `typeahead_arbeidsgivers_behov` før overføring og kalle `janzzRepository.overforTypeahead("typeahead_arbeidsgivers_behov")`.

### Søk og sortering

Tre alternativer er vurdert. Anbefalingen står nederst.

#### Alternativ 1 — Enkel prefix/substring med CASE-ranking (speiler dagens `finnTypeahead`)

Samme mønster som dagens `finnTypeahead(tabellnavn, oppslagsord)` i `JanzzRepository` (LIKE-basert, CASE-rangering A/B/C), men utvidet til å støtte **alle** tokens i søkestrengen i stedet for kun de to første (som er en kjent begrensning i nåværende implementasjon).

```sql
-- Én-token:
SELECT id, konsept_id, label, kategori
FROM typeahead_arbeidsgivers_behov
WHERE label_lc LIKE :q_llike                    -- '%q%'
  AND (:kategorier::text[] IS NULL OR kategori = ANY(:kategorier))
ORDER BY
  CASE
    WHEN label_lc = :q              THEN 'A'
    WHEN label_lc LIKE :q_like      THEN concat('B ', label_lc)   -- 'q%'
    ELSE                                 concat('C ', label_lc)
  END,
  label_lc
LIMIT 50;

-- Flere tokens (AND-logikk, dynamisk generert i Kotlin):
SELECT id, konsept_id, label, kategori
FROM typeahead_arbeidsgivers_behov
WHERE label_lc LIKE :t1_llike
  AND label_lc LIKE :t2_llike
  AND label_lc LIKE :t3_llike              -- osv. for alle tokens
  AND (:kategorier::text[] IS NULL OR kategori = ANY(:kategorier))
ORDER BY
  CASE
    WHEN label_lc = :q                                      THEN 'A'
    WHEN label_lc LIKE :t1_like AND label_lc LIKE :t2_llike THEN concat('B ', label_lc)
    ELSE                                                         concat('C ', label_lc)
  END,
  label_lc
LIMIT 50;
```

- **Fordeler:** Minimal avvik fra eksisterende mønster, lett å reviewe, predikerbart.
- **Ulemper:** Bare grov rangering (A/B/C). Treff midt i et ord rangeres likt. Ingen reell "fuzziness".

#### Alternativ 2 — Word-boundary-rangering + GIN trigram for filter

Bygger videre på alternativ 1, men legger til ordgrense-rangering slik at "syke" rangerer høyere i "**syke**pleier" / "psyk. **syke**pleier" enn i "analyse**syke**r". GIN trigram-indeksen dekker `ILIKE '%…%'`-filteret.

```sql
SELECT id, konsept_id, label, kategori
FROM typeahead_arbeidsgivers_behov
WHERE label_lc ILIKE :t1_llike
  AND label_lc ILIKE :t2_llike                -- osv. for alle tokens, generert dynamisk
  AND (:kategorier::text[] IS NULL OR kategori = ANY(:kategorier))
ORDER BY
  CASE
    WHEN label_lc = :q                                 THEN 0   -- eksakt
    WHEN label_lc LIKE :q_like                         THEN 1   -- starter med hele søket
    WHEN label_lc LIKE :t1_like                        THEN 2   -- starter med første token
    WHEN label_lc LIKE '% ' || :t1 || '%'              THEN 3   -- token på ordgrense
    ELSE                                                    4   -- substring
  END,
  length(label_lc),
  label_lc
LIMIT 50;
```

- **Fordeler:** Tydelig bedre relevans enn alt. 1, spesielt for sammensatte norske ord. Bruker GIN trigram-indeksen som allerede er standard på `*_lc`-kolonner. Fortsatt transparent SQL.
- **Ulemper:** Mer håndplukket ranking-logikk. Flere tokens → mer dynamisk SQL-bygging i Kotlin.

#### Alternativ 3 — pg_trgm similarity / KNN (`<->`) med GiST

Bruk trigram-distanse for relevans:

```sql
SELECT id, konsept_id, label, kategori
FROM typeahead_arbeidsgivers_behov
WHERE label_lc % :q                            -- similarity-terskel (krever pg_trgm)
  AND (:kategorier::text[] IS NULL OR kategori = ANY(:kategorier))
ORDER BY label_lc <-> :q                       -- trigram distance (KNN)
LIMIT 50;
```

- **Fordeler:** Best "fuzzy"-opplevelse (toleranse for typo og ordrekkefølge). Enkel SQL.
- **Ulemper:** KNN-operator (`<->`) krever **GiST**-indeks, ikke GIN. Det bryter eksisterende mønster hvor alle `*_lc`-kolonner har GIN trigram. Vanskelig å garantere at eksakt-match rangerer først uten ekstra CASE-wrapping. Har ikke naturlig plass til multi-token AND-logikk.

#### Anbefaling

**Velg alternativ 2.** Det gir merkbar bedre relevans enn alt. 1 for norske sammensatte ord, uten å bryte det etablerte GIN-indeks-mønsteret som alt. 3 vil kreve. Implementasjonen er en naturlig videreføring av dagens `finnTypeahead`, bare uten to-token-begrensningen.

Hvis opplevelsen i praksis viser seg utilstrekkelig, kan vi senere legge til en GiST-indeks i tillegg og falle tilbake på trigram-similarity for ord som ikke rangeres godt nok.

### Flersøk (flere ord)

Alle alternativene over skal støtte **n tokens** — ikke bare to. SQL-en genereres dynamisk i repository ved å splitte søkestrengen på whitespace, filtrere bort tokens < 2 tegn, og bygge én `AND label_lc ILIKE :tN_llike`-predikat per token (navngitte parametre `:t1`, `:t2`, ...).

### API-endepunkt

```
GET /rest/typeahead/arbeidsgivers-behov?q=<søkeord>
GET /rest/typeahead/arbeidsgivers-behov?q=sykepleier&kategorier=YRKESTITTEL,KOMPETANSE
```

- Minimum lengde på `q`: 2 tegn (samme som øvrige typeahead-endepunkter i `TypeaheadController`).
- `kategorier` er komma-separert liste, valgfri. Ugyldige verdier gir 400.

### Respons-DTO

```kotlin
enum class BehovKategori {
    YRKESTITTEL,
    KOMPETANSE,
    AUTORISASJON,
    GODKJENNING,
    FAGDOKUMENTASJON,
    FORERKORT
}

data class ArbeidsgiversBehov(
    val konseptId: Long? = null,    // null for førerkort
    val label: String,
    val kategori: BehovKategori
)
```

Intern kategori-verdi (db + enum) er `FORERKORT`. Hvis frontend ønsker `FØRERKORT` i API-responsen, kan det styres med Jackson-annotasjoner på enumen uten å påvirke intern modell eller SQL.

### Konsistens mot `arbeidsgivers-behov`-plan

Planen for lagring av arbeidsgivers behov (`arbeidsgivers-behov.md`) definerer i dag `arbeidsoppgaver` og `arbeidssprak` som plain `text[]` (kun labels). Det er greit **så lenge** selve typeahead-APIet leverer både `label`, `konseptId` og `kategori` — da kan frontend selv velge å lagre kun label der det er hensiktsmessig, men beholde muligheten til å oppgradere til strukturert lagring (konseptId + kategori) senere uten breaking change i typeahead-APIet.

Før lagringsplanen realiseres bør det avklares om vi skal persistere `{label, kategori, konseptId?}` i stedet for plain strings, for å unngå at samme label i to kategorier (f.eks. "Tysk" som både SPRAAK/KOMPETANSE) blir flertydig.

---

## Implementeringsplan

Rekkefølge slik at hver commit kan kjøres og testes isolert.

1. **Flyway-migrasjon** (neste ledige V-nummer, antatt `V10__typeahead_arbeidsgivers_behov.sql`):
   - `CREATE TABLE typeahead_arbeidsgivers_behov` + `tmp_typeahead_arbeidsgivers_behov` (uten GIN på staging).
   - GIN trigram-indeks på `typeahead_arbeidsgivers_behov.label_lc`.
   - B-tree-indeks på `kategori`.
   - _Ingen_ statiske førerkort-rader i migrasjonen — de settes inn under import (se punkt 3), slik at truncate-and-reload-flyten forblir kilden til alt innhold.

2. **Repository** — `pam-ontologi/.../repository/JanzzRepository.kt`:
   - `slettOgIndekserTypeaheadArbeidsgiversBehov(prefix: String = "")`: kjører UNION ALL-insert fra `${prefix}typeahead_*`-tabellene + den statiske førerkort-inserten mot `${prefix}typeahead_arbeidsgivers_behov`. Merk at vi leser fra `tmp_typeahead_*` (rensede data) — vi leser **ikke** direkte fra `konsept_relasjon` her og slipper dermed staging-leak-problemet som finnes i `slettOverflodingeGodkjenninger`/`slettOverflodigeFagdokumentasjoner`/`settUndertyperFor*`.
   - `finnTypeaheadArbeidsgiversBehov(oppslagsord: String, kategorier: List<BehovKategori>?): List<ArbeidsgiversBehov>`: implementerer alternativ 2 med dynamisk n-token SQL. Egen row-mapper for `(konsept_id, label, kategori)`.

3. **InnlastingService** — `pam-ontologi/.../service/InnlastingService.kt`:
   - I `lastInnTypeahead()`: legg til `janzzRepository.slettOgIndekserTypeaheadArbeidsgiversBehov("tmp_")` **sist** (etter de andre `slettOgIndekser*`-kallene).
   - I `overforNedlastetOntologi()`: legg til `janzzRepository.slettTabell("typeahead_arbeidsgivers_behov")` blant truncate-kallene og `janzzRepository.overforTypeahead("typeahead_arbeidsgivers_behov")` blant overføringskallene.
   - I `trunkerArbeidstabeller()`: legg til `janzzRepository.slettTabell("tmp_typeahead_arbeidsgivers_behov")`.

4. **Service** — ny eller utvidet `TypeaheadService`:
   - `finnArbeidsgiversBehov(q: String, kategorier: List<BehovKategori>?): List<ArbeidsgiversBehov>` som delegerer til repository og mapper til DTO.

5. **Controller** — `pam-ontologi/.../rest/TypeaheadController.kt`:
   - `GET /rest/typeahead/arbeidsgivers-behov` med query-param `q` (min 2 tegn, samme sjekk som eksisterende endepunkter) og valgfri `kategorier` (komma-separert, parses til `List<BehovKategori>`).
   - Returner `List<ArbeidsgiversBehov>` som JSON.

6. **Tester** — `pam-ontologi/src/test/...`:
   - Det finnes per i dag nesten ingen aktive tester i dette prosjektet (`TypeaheadServiceTest`, `SynonymServiceTest`, `InnlastingServiceTest`, `JanzzDownloaderTest` har alle `//@Test`). Vi introduserer minst:
     - Én repository-test mot Testcontainers/Postgres som verifiserer populering fra `tmp_typeahead_*` + førerkort, og som kjører `finnTypeaheadArbeidsgiversBehov` med én-token, to-token og tre-token query og bekrefter rangering.
     - Én controller-test som verifiserer 400 på `q` < 2 tegn og korrekt parsing av `kategorier`.
   - Disse skal skrives med aktiv `@Test`-annotasjon (ikke `//@Test`) slik at de faktisk kjører.

## Relevante filer

- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/repository/JanzzRepository.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/service/TypeaheadService.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/rest/TypeaheadController.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/service/InnlastingService.kt`
- `pam-ontologi/src/main/resources/db/migration/`

## Kjente sideobservasjoner (ikke blokkerende)

Under review kom det frem at `slettOverflodingeGodkjenninger`, `slettOverflodigeFagdokumentasjoner`, `settUndertyperForTypeahead` og `settUndertyperForKonsepter` i `JanzzRepository` leser fra `konsept_relasjon` **uten** `${prefix}`, selv når de kalles som del av staging-flyten (`"tmp_"`). Det er ikke introdusert av denne planen, men bør flagges i egen sak — denne implementasjonen er ikke avhengig av at det er fikset først, fordi vi leser fra allerede rensede `tmp_typeahead_*`-tabeller.
