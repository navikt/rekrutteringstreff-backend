# Typeahead for arbeidsgivers behov (inkl. personlige egenskaper) i pam-ontologi

Dette dokumentet dekker **alle** pam-ontologi-endringene som trengs for å drive «arbeidsgivers behov»-feltene i rekrutteringstreff:

- Arbeidsoppgaver-feltet (kombinert: yrkestittel, kompetanse, autorisasjon, godkjenning, fagdokumentasjon, førerkort).
- Personlige egenskaper-feltet (softskills).

Begge driftes av **én** typeahead-tabell og **ett** endepunkt. Frontend skiller mellom feltene ved å velge `kategorier`-parameter per felt.

## Bakgrunn

Arbeidsgiver beskriver sine behov ved å velge fra Janzz-forslag innen flere kategorier: **yrkestittel**, **kompetanse**, **fagbrev**, **førerkort**, **offentlige godkjenninger** og **softskills/personlige egenskaper**. Vi trenger én samlet typeahead som dekker alle disse, med et felt som angir kategori per rad.

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

**Softskills** finnes allerede i `konsept`-tabellen med `type='SS'` (`KonseptType.SOFTSKILL`, se `InnlastingService.finnKonseptTypeForNode(...)`), men det finnes **ingen typeahead-tabell** for dem i dag, og `slettOgIndekserTypeahead*` kalles ikke for `KonseptType.SOFTSKILL`.

Importen kjøres nattlig av `JanzzDownloadScheduledTask` og følger et **staging-mønster**: alle `tmp_`-tabeller fylles først, deretter truncates prod-tabellene og `tmp_`-tabellene overføres via `overforTypeahead(...)` i `InnlastingService.overforNedlastetOntologi()`. Til slutt tømmes `tmp_`-tabellene i `trunkerArbeidstabeller()`.

### Forkastede alternativer

Vi vurderte UNION ALL over eksisterende tabeller (vanskelig sortering, ingen samlet indeks), direkte spørring mot `konsept`-tabellen (for komplekst pga. subset-logikk for godkjenninger/fagdokumentasjon), og database VIEW (GIN-indekser brukes ikke gjennom UNION-views). Alle hadde svakheter rundt ytelse eller kompleksitet.

Vi vurderte også å ha **to separate typeahead-tabeller/endepunkter** (én kombinert + én egen for softskills). Det ble forkastet fordi:

- Softskill-datasettet er lite; én ekstra kategori i samme tabell påvirker ikke ytelse merkbart (GIN-trigram dominerer, `kategori` er et billig btree-filter).
- Én ranking-implementasjon å vedlikeholde; ingen fare for at personlige egenskaper-søket driver fra kombinert-søket når bugfikses gjøres.
- Én Flyway-migrasjon, én `finnTypeahead…`-metode, ett controller-handler, ett testoppsett.
- Frontend gjenbruker samme `Combobox` + `BehovTag`-DTO for begge felt, kun ulik `kategorier`-parameter.

Softskills blandes **ikke** inn i arbeidsoppgaver-feltet — det er et rent API-designvalg: frontend sender `kategorier=SOFTSKILL` for personlige egenskaper, og utelater `SOFTSKILL` fra kategorilisten for arbeidsoppgaver. Se [API-endepunkt](#api-endepunkt) nedenfor.

---

## Valgt løsning: Materialisert typeahead-tabell

Ny tabell `typeahead_behov` med kun feltene som trengs, populert under Janzz-import fra de eksisterende `tmp_typeahead_*`-tabellene pluss en softskill-spesifikk populering fra `tmp_konsept`/`tmp_term`. Førerkort settes inn statisk i samme tabell under import.

Vi bruker `FORERKORT` (ASCII, uten æøå) som kategori-verdi i database og internt i backend, for å unngå encoding-fallgruver i SQL-litteraler og kolonneverdier. Enum-navnet i Kotlin er `FORERKORT`; hvis frontend trenger `FØRERKORT` i JSON kan det styres med Jackson-annotasjoner på enumen.

### Database-skjema

`typeahead_behov` er en prod-tabell og får GIN trigram-indeks. Staging-tabellen opprettes bevisst **uten** GIN-indeks, i tråd med mønsteret i øvrige `tmp_typeahead_*`-tabeller (indeksen bygges først når data er ferdig populert på prod-tabellen ved import).

```sql
CREATE TABLE typeahead_behov (
    id         serial PRIMARY KEY,
    konsept_id integer,                        -- nullable, NULL for førerkort
    label      varchar(500) NOT NULL,
    label_lc   varchar(500) NOT NULL,
    kategori   varchar(30) NOT NULL            -- YRKESTITTEL | KOMPETANSE | AUTORISASJON | GODKJENNING | FAGDOKUMENTASJON | FORERKORT | SOFTSKILL
);

CREATE TABLE tmp_typeahead_behov (
    id         serial PRIMARY KEY,
    konsept_id integer,
    label      varchar(500) NOT NULL,
    label_lc   varchar(500) NOT NULL,
    kategori   varchar(30) NOT NULL
);

CREATE INDEX trgm_idx_behov_label_lc ON typeahead_behov USING gin (label_lc gin_trgm_ops);
CREATE INDEX idx_behov_kategori      ON typeahead_behov (kategori);
```

### Populering under import

I `InnlastingService.lastInnTypeahead()` kalles `slettOgIndekserTypeaheadBehov("tmp_")` **etter** at alle de eksisterende `slettOgIndekserTypeaheadFor*("tmp_")`-kallene er ferdige, slik at den kan bygge på allerede filtrerte `tmp_typeahead_*`-tabeller.

Fordi vi leser fra de allerede rensede tmp_typeahead-tabellene for de fem eksisterende kategoriene, arver vi automatisk den eksisterende filtreringen (godkjenninger under 567151, fagdokumentasjon under 534435/11777/407667, stilling med styrk08ssb > 3 tegn) — og slipper å duplisere subset-logikken mot `konsept_relasjon`.

Softskills har ingen egen `tmp_typeahead_*`-tabell i dag, så de populeres direkte fra `tmp_konsept` + `tmp_term` i samme insert, filtrert på `type = 'SS'` og samme kriterier som `slettOgIndekserTypeahead` bruker ellers (`tag in ('p','l')`, `lang='no'`, `no_label is not null`, `umbrella=0`).

```sql
-- Del 1: Yrkestittel/kompetanse/autorisasjon/godkjenning/fagdokumentasjon fra eksisterende tmp_-tabeller
INSERT INTO tmp_typeahead_behov (konsept_id, label, label_lc, kategori)
SELECT konsept_id, verdi, verdi_lc, 'YRKESTITTEL'      FROM tmp_typeahead_stilling
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'KOMPETANSE'       FROM tmp_typeahead_kompetanse
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'AUTORISASJON'     FROM tmp_typeahead_autorisasjon
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'GODKJENNING'      FROM tmp_typeahead_godkjenninger
UNION ALL
SELECT konsept_id, verdi, verdi_lc, 'FAGDOKUMENTASJON' FROM tmp_typeahead_fagdokumentasjon;

-- Del 2: Softskills (ingen eksisterende typeahead-tabell for disse)
INSERT INTO tmp_typeahead_behov (konsept_id, label, label_lc, kategori)
SELECT k.konsept_id, t.verdi, t.verdi_lc, 'SOFTSKILL'
FROM tmp_konsept k, tmp_term t
WHERE t.konsept_id = k.konsept_id
  AND k.type = 'SS'
  AND t.tag IN ('p','l')
  AND t.lang = 'no'
  AND k.no_label IS NOT NULL
  AND k.umbrella = 0;

-- Del 3: Førerkort hardkodes (listen speiler gyldigeFørerkort i CV-prosjektet)
INSERT INTO tmp_typeahead_behov (konsept_id, label, label_lc, kategori) VALUES
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

- `trunkerArbeidstabeller()` må også droppe `tmp_typeahead_behov`.
- `overforNedlastetOntologi()` må truncate `typeahead_behov` før overføring og kalle `janzzRepository.overforTypeahead("typeahead_behov")`.

Merk: `overforTypeahead(tabellnavn)` forventer i dag kolonnene `(id, konsept_id, verdi, verdi_lc, styrk08ssb, styrk08ssb_label, isco08, esco, esco_label, undertype)`. Det passer ikke vår smalere tabell. Vi legger derfor til en liten variant:

```kotlin
fun overforTypeaheadSmal(tabellnavn: String) {
    val felter = "id, konsept_id, label, label_lc, kategori"
    namedJdbcTemplate.update(
        "insert into $tabellnavn($felter) select $felter from tmp_$tabellnavn",
        MapSqlParameterSource()
    )
}
```

### Søk og sortering

Tre alternativer er vurdert. Anbefalingen står nederst.

#### Alternativ 1 — Enkel prefix/substring med CASE-ranking (speiler dagens `finnTypeahead`)

Samme mønster som dagens `finnTypeahead(tabellnavn, oppslagsord)` i `JanzzRepository` (LIKE-basert, CASE-rangering A/B/C), men utvidet til å støtte **alle** tokens i søkestrengen i stedet for kun de to første (som er en kjent begrensning i nåværende implementasjon).

```sql
-- Én-token:
SELECT id, konsept_id, label, kategori
FROM typeahead_behov
WHERE label_lc LIKE :q_llike                    -- '%q%'
  AND kategori = ANY(:kategorier)
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
FROM typeahead_behov
WHERE label_lc LIKE :t1_llike
  AND label_lc LIKE :t2_llike
  AND label_lc LIKE :t3_llike              -- osv. for alle tokens
  AND kategori = ANY(:kategorier)
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
FROM typeahead_behov
WHERE label_lc ILIKE :t1_llike
  AND label_lc ILIKE :t2_llike                -- osv. for alle tokens, generert dynamisk
  AND kategori = ANY(:kategorier)
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
FROM typeahead_behov
WHERE label_lc % :q                            -- similarity-terskel (krever pg_trgm)
  AND kategori = ANY(:kategorier)
ORDER BY label_lc <-> :q                       -- trigram distance (KNN)
LIMIT 50;
```

- **Fordeler:** Best "fuzzy"-opplevelse (toleranse for typo og ordrekkefølge). Enkel SQL.
- **Ulemper:** KNN-operator (`<->`) krever **GiST**-indeks, ikke GIN. Det bryter eksisterende mønster hvor alle `*_lc`-kolonner har GIN trigram. Vanskelig å garantere at eksakt-match rangerer først uten ekstra CASE-wrapping. Har ikke naturlig plass til multi-token AND-logikk.

#### Anbefaling

**Velg alternativ 2.** Det gir merkbar bedre relevans enn alt. 1 for norske sammensatte ord, uten å bryte det etablerte GIN-indeks-mønsteret som alt. 3 vil kreve. Implementasjonen er en naturlig videreføring av dagens `finnTypeahead`, bare uten to-token-begrensningen.

Rangeringen brukes **identisk** for både arbeidsoppgaver-feltet og personlige egenskaper-feltet — de skiller seg kun på `kategorier`-filteret. Personlige egenskaper får dermed samme støtte for søk midt i ord, eksakt-/prefix-prioritering og flertoken-AND som arbeidsoppgaver.

Hvis opplevelsen i praksis viser seg utilstrekkelig, kan vi senere legge til en GiST-indeks i tillegg og falle tilbake på trigram-similarity for ord som ikke rangeres godt nok.

### Flersøk (flere ord)

Alle alternativene over skal støtte **n tokens** — ikke bare to. SQL-en genereres dynamisk i repository ved å splitte søkestrengen på whitespace, filtrere bort tokens < 2 tegn, og bygge én `AND label_lc ILIKE :tN_llike`-predikat per token (navngitte parametre `:t1`, `:t2`, ...).

### API-endepunkt

```
GET /rest/typeahead/behov?q=<søkeord>&kategorier=<kommaseparert>
```

- `q`: minimum 2 tegn (samme som øvrige typeahead-endepunkter i `TypeaheadController`). 1 tegn → `400`.
- `kategorier`: **obligatorisk**, komma-separert liste. Ugyldige eller tomme verdier gir `400`.

`kategorier` er gjort obligatorisk med vilje: det finnes ingen fornuftig default når tabellen blander arbeidsoppgaver og softskills. Å kreve eksplisitt valg gir entydig kontrakt og hindrer at feil kategorier lekker inn i feil felt.

**Bruk fra rekrutteringstreff-frontend:**

- Arbeidsoppgaver-feltet:
  `?q=…&kategorier=YRKESTITTEL,KOMPETANSE,AUTORISASJON,GODKJENNING,FAGDOKUMENTASJON,FORERKORT`
- Personlige egenskaper-feltet:
  `?q=…&kategorier=SOFTSKILL`

### Respons-DTO

```kotlin
enum class BehovKategori {
    YRKESTITTEL,
    KOMPETANSE,
    AUTORISASJON,
    GODKJENNING,
    FAGDOKUMENTASJON,
    FORERKORT,
    SOFTSKILL
}

data class BehovTypeahead(
    val konseptId: Long? = null,    // null for førerkort
    val label: String,
    val kategori: BehovKategori
)
```

Intern kategori-verdi (db + enum) er `FORERKORT`. Hvis frontend ønsker `FØRERKORT` i API-responsen, kan det styres med Jackson-annotasjoner på enumen uten å påvirke intern modell eller SQL.

### Konsistens mot `arbeidsgivers-behov`-plan

[arbeidsgivers-behov.md](./arbeidsgivers-behov.md) lagrer `arbeidsoppgaver` og `personlige_egenskaper` som **JSONB-arrays** av `{label, kategori, konseptId?}`. Typeahead-APIet leverer nøyaktig denne strukturen (med `konseptId = null` for førerkort), så frontend kan lagre forslaget direkte uten ekstra mapping.

---

## Implementeringsplan

Rekkefølge slik at hver commit kan kjøres og testes isolert.

1. **Flyway-migrasjon** (neste ledige V-nummer, antatt `V10__typeahead_behov.sql`):
   - `CREATE TABLE typeahead_behov` + `tmp_typeahead_behov` (uten GIN på staging).
   - GIN trigram-indeks på `typeahead_behov.label_lc`.
   - B-tree-indeks på `kategori`.
   - _Ingen_ statiske førerkort-rader i migrasjonen — de settes inn under import (se punkt 3), slik at truncate-and-reload-flyten forblir kilden til alt innhold.

2. **Repository** — `pam-ontologi/.../repository/JanzzRepository.kt`:
   - `slettOgIndekserTypeaheadBehov(prefix: String = "")`: kjører UNION ALL-insert fra `${prefix}typeahead_*`-tabellene, softskill-inserten fra `${prefix}konsept` + `${prefix}term`, og den statiske førerkort-inserten mot `${prefix}typeahead_behov`. Merk at vi leser fra `tmp_typeahead_*` (rensede data) for de fem eksisterende kategoriene — vi leser **ikke** direkte fra `konsept_relasjon` der, og slipper dermed staging-leak-problemet som finnes i `slettOverflodingeGodkjenninger`/`slettOverflodigeFagdokumentasjoner`/`settUndertyperFor*`. For softskills leser vi direkte fra `${prefix}konsept`/`${prefix}term` siden det ikke finnes en pre-renset `tmp_typeahead_softskill`.
   - `finnTypeaheadBehov(oppslagsord: String, kategorier: List<BehovKategori>): List<BehovTypeahead>`: implementerer alternativ 2 med dynamisk n-token SQL. `kategorier` er alltid ikke-null (påkrevd i API). Egen row-mapper for `(konsept_id, label, kategori)`.
   - `overforTypeaheadSmal(tabellnavn: String)`: ny liten variant som flytter `(id, konsept_id, label, label_lc, kategori)` fra `tmp_*` til prod-tabellen.

3. **InnlastingService** — `pam-ontologi/.../service/InnlastingService.kt`:
   - I `lastInnTypeahead()`: legg til `janzzRepository.slettOgIndekserTypeaheadBehov("tmp_")` **sist** (etter de andre `slettOgIndekser*`-kallene).
   - I `overforNedlastetOntologi()`: legg til `janzzRepository.slettTabell("typeahead_behov")` blant truncate-kallene og `janzzRepository.overforTypeaheadSmal("typeahead_behov")` blant overføringskallene.
   - I `trunkerArbeidstabeller()`: legg til `janzzRepository.slettTabell("tmp_typeahead_behov")`.

4. **Service** — ny eller utvidet `TypeaheadService`:
   - `finnBehov(q: String, kategorier: List<BehovKategori>): List<BehovTypeahead>` som delegerer til repository og mapper til DTO.

5. **Controller** — `pam-ontologi/.../rest/TypeaheadController.kt`:
   - `GET /rest/typeahead/behov` med query-param `q` (min 2 tegn, samme sjekk som eksisterende endepunkter) og **obligatorisk** `kategorier` (komma-separert, parses til `List<BehovKategori>`; manglende eller ugyldig gir `400`).
   - Returner `List<BehovTypeahead>` som JSON.

6. **Tester** — se egen seksjon [Testing](#testing) nedenfor.

## Relevante filer

- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/repository/JanzzRepository.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/service/TypeaheadService.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/rest/TypeaheadController.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/service/InnlastingService.kt`
- `pam-ontologi/src/main/resources/db/migration/`

## Testing

`pam-ontologi` har i dag tilnærmet ingen aktive tester — de eksisterende testklassene (`TypeaheadServiceTest`, `SynonymServiceTest`, `InnlastingServiceTest`, `JanzzDownloaderTest`) har kommenterte `//@Test`-annotasjoner og kjører ikke. Denne planen innfører derfor ikke bare én test, men etablerer et **testoppsett basert på Testcontainers** for prosjektet (samme mønster som `rekrutteringstreff-api`), og bruker det til å dekke `typeahead_behov` grundig.

### Oppsett — Testcontainers

- Legg til `org.testcontainers:postgresql` og `org.testcontainers:junit-jupiter` i `build.gradle.kts` (testscope).
- En felles `TestDatabase`-hjelper (tilsvarende `rekrutteringstreff-backend/apps/rekrutteringstreff-api/src/test/kotlin/TestDatabase.kt`) som starter én `PostgreSQLContainer` per testklasse/testsuite og kjører Flyway-migrasjonene fra `pam-ontologi/src/main/resources/db/migration/` mot den.
- Hjelperen eksponerer en `DataSource` + `NamedParameterJdbcTemplate` + en metode for å populere minimalt testdata direkte i `konsept` / `term` / `konsept_relasjon` (og `tmp_*`-varianter) slik at hver test kan sette opp akkurat de nodene den trenger uten å kjøre hele Janzz-importen.
- Aktive `@Test`-annotasjoner — ikke `//@Test`.

### Avgrensning — hva testes nå vs. senere

**Fullt dekket i denne planen: `typeahead_behov`.** Det er ny funksjonalitet, ingen legacy-brukere, og det er her risikoen ligger (dynamisk n-token SQL, korrekt populering fra `tmp_*` inkl. softskills, kategori-filter, rangering).

**Ikke utvidet test-dekning for eksisterende typeahead-tabeller** (stilling, kompetanse, autorisasjon, godkjenninger, fagdokumentasjon, geografi) som del av denne planen. De har allerede kjørt i produksjon lenge og har implisitt dekning via at de allerede rensede `tmp_typeahead_*`-tabellene er input til `typeahead_behov` — hvis én av dem skulle være tom eller feil, fanges det opp av `typeahead_behov`-testen som leser fra samme kilde.

Hvis det viser seg naturlig (få ekstra linjer, samme testoppsett), kan vi utvide til smoke-tester på de eksisterende tabellene i samme PR. Men det er _ikke_ et krav.

### Konkrete tester

**Repository (`JanzzRepositoryTest`, Testcontainers-basert):**

- `slettOgIndekserTypeaheadBehov("tmp_")` populerer fra alle fem `tmp_typeahead_*`-kilder med korrekt `kategori`-verdi per rad.
- Softskill-delen populerer kun rader der `konsept.type = 'SS'`, `umbrella=0`, `no_label IS NOT NULL`, `term.lang='no'`, `term.tag in ('p','l')`. Rader som bryter ett av disse filtrene ekskluderes.
- Populeringen inkluderer de hardkodede førerkort-radene (antall = 19) med `kategori='FORERKORT'` og `konsept_id=NULL`.
- `overforTypeaheadSmal("typeahead_behov")` flytter rader fra `tmp_*` til prod-tabellen.
- `finnTypeaheadBehov` med én-token query returnerer riktig rangering: eksakt match først, deretter prefix-match, deretter ordgrense, deretter substring.
- `finnTypeaheadBehov` med to-token og tre-token query bruker AND-logikk — alle tokens må matche.
- `finnTypeaheadBehov` med `kategorier=[SOFTSKILL]` returnerer kun softskills (validerer at personlige egenskaper-feltet får samme rangering som arbeidsoppgaver, bare filtrert).
- `finnTypeaheadBehov` med kombinert kategori-liste (uten `SOFTSKILL`) returnerer ingen softskills.
- Søk er case-insensitivt (via `label_lc`) og norske tegn (æøå) håndteres riktig.
- Søk midt i ord treffer (f.eks. `kunde` matcher `Kundebehandler`) — testes eksplisitt både for `SOFTSKILL` og for en annen kategori.
- Resultatet er begrenset til 50 rader.

**Controller (`TypeaheadControllerTest`):**

- `GET /rest/typeahead/behov?q=a&kategorier=YRKESTITTEL` (1 tegn i `q`) returnerer `400`.
- `GET /rest/typeahead/behov?q=sy` (mangler `kategorier`) returnerer `400`.
- `GET /rest/typeahead/behov?q=sy&kategorier=` (tom `kategorier`) returnerer `400`.
- `GET /rest/typeahead/behov?q=sy&kategorier=TULL` (ugyldig verdi) returnerer `400`.
- `GET /rest/typeahead/behov?q=sy&kategorier=YRKESTITTEL,KOMPETANSE` returnerer `200` med JSON-array.
- `GET /rest/typeahead/behov?q=ku&kategorier=SOFTSKILL` returnerer `200` med kun `kategori=SOFTSKILL` i responsen.
- Responsen serialiserer `kategori` som enum-navn (samt `FORERKORT` hvis vi velger å eksponere som `FØRERKORT` via Jackson — testes eksplisitt).

**Service (`TypeaheadServiceTest`):**

- Delegerer til repository og mapper resultatet til `BehovTypeahead`-DTO uten tap av felter.

### Kjørbarhet

- Testene skal kjøre både lokalt (`./gradlew :pam-ontologi:test`) og i CI. Docker må være tilgjengelig for Testcontainers.
- Testene skal ikke avhenge av at `downloadJanzz.sh` er kjørt eller at faktiske Janzz-data ligger i databasen — all nødvendig `konsept`/`term`-data settes inn programmatisk i test-setup.

## Kjente sideobservasjoner (ikke blokkerende)

Under review kom det frem at `slettOverflodingeGodkjenninger`, `slettOverflodigeFagdokumentasjoner`, `settUndertyperForTypeahead` og `settUndertyperForKonsepter` i `JanzzRepository` leser fra `konsept_relasjon` **uten** `${prefix}`, selv når de kalles som del av staging-flyten (`"tmp_"`). Det er ikke introdusert av denne planen, men bør flagges i egen sak — denne implementasjonen er ikke avhengig av at det er fikset først, fordi vi for de fem eksisterende kategoriene leser fra allerede rensede `tmp_typeahead_*`-tabeller, og for softskills bruker vi `${prefix}konsept`/`${prefix}term` direkte uten `konsept_relasjon`-joins.
