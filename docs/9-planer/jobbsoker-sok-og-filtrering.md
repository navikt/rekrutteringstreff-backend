# Plan: Søk og filtrering av jobbsøkere i rekrutteringstreff

**Status:** Implementert (MVP)  
**Omfang:** Backend API + frontend UI for søk og filtrering av jobbsøkere innad i et treff

Når eiere og markedskontakter skal administrere et rekrutteringstreff med mange jobbsøkere (opp til 10 000), må de kunne søke og filtrere deltakerne for å finne relevante kandidater.

---

## Implementert arkitektur (MVP)

MVP-en er implementert med en enklere arkitektur enn opprinnelig planlagt. Her er en oppsummering av de viktigste valgene:

### Database: View i stedet for parallell tabell

I stedet for en separat `jobbsoker_sok`-tabell med denormalisert data og trigram-indekser, bruker MVP-en en **database-view** (`jobbsoker_sok_view`) som joiner `jobbsoker`-tabellen med `rekrutteringstreff` og henter `lagt_til_dato`/`lagt_til_av` via korrelerte subqueries mot `jobbsoker_hendelse`. Viewet filtrerer automatisk ut skjulte (`er_synlig = false`) og slettede (`status = 'SLETTET'`).

**Fordeler over parallell tabell:** Ingen synkroniseringsproblematikk, ingen ekstra writes ved hendelser, enklere kode.

Flyway-migrasjoner:

- `R__jobbsoker_sok_view.sql` (repeatable) – oppretter/oppdaterer viewet
- `V4__jobbsoker_sok.sql` – oppretter partial indexes på `jobbsoker`-tabellen

### API: POST med JSON body

Søkeendepunktet bruker **POST** (ikke GET med query-params som opprinnelig planlagt):

```
POST /api/rekrutteringstreff/{treffId}/jobbsoker/sok
Content-Type: application/json

{
  "fritekst": "Ola",
  "status": ["LAGT_TIL", "INVITERT"],
  "sortering": "navn",
  "retning": "asc",
  "side": 1,
  "antallPerSide": 25
}
```

**Begrunnelse:** Fødselsnummer sendes i body (fritekst-feltet brukes for fnr-oppslag ved 11-sifret input), og POST er konsistent med andre søkeendepunkter i Nav-plattformen.

### Fritekst: Prefix-søk på navn + eksakt fnr-match

I stedet for trigram-indeksert `sok_tekst` over mange felter, bruker MVP-en:

- **Fødselsnummer (11 siffer):** Eksakt match på `fodselsnummer`
- **Annen tekst:** Prefix-match (`LIKE 'tekst%'`) på `LOWER(fornavn)` og `LOWER(etternavn)` separat

Enklere enn trigram, ingen pg_trgm-avhengighet. Tilstrekkelig for MVP.

### Filtre implementert i MVP

- **Fritekst** (navn-prefix + fnr-eksakt)
- **Status** (`LAGT_TIL`, `INVITERT`, `SVART_JA`, `SVART_NEI`)
- **Sortering** (`navn` med standard ASC, `lagt-til` med standard DESC)
- **Paginering** (`side` + `antallPerSide`, 1–100)

### Filtre planlagt for senere

- Innsatsgruppe
- Geografi (fylke/kommune)
- Nav-kontor
- Veileder

### Frontend-arkitektur

- **`useJobbsøkerSøk()`** – SWR-basert POST-hook med Zod-validering
- **`useSWRPost()`** – generisk SWR-hook for POST med cache-nøkkel basert på JSON-body
- **`JobbsøkerSøkContext`** – URL-synkronisert filtertilstand via `nuqs` (query-params i URL)
- **`JobbsøkerFilterrad`** – søkefelt + statusfilter-popover + chips for aktive filtre
- **MSW-mock** – komplett mock av søke-backend for lokal utvikling og Playwright-tester

---

## Oversikt

Planen beskriver arkitektur for søk og filtrering av jobbsøkere innad i et treff. Det nye søkeendepunktet **erstatter** det eksisterende `GET /api/rekrutteringstreff/{id}/jobbsoker` med et POST-basert søkeendepunkt med paginering og filtre. Det eksisterende hendelsesendepunktet (`GET .../jobbsoker/hendelser`) beholdes uendret for statushistorikk.

1. **Tilgjengelig data fra kandidatsøk** – hva som finnes og hva vi kopierer ved opprettelse
2. **Filterkriterier** – hvilke feltdimensjoner som kan filtreres (valgfrie)
3. **API-kontrakt** – request/response DTOs med påkrevd paginering
4. **Database-strategi** – parallell søketabell `jobbsoker_sok`
5. **Implementering** – oppgaver for MVP

---

## Tilgjengelig data fra kandidatsøk

Når en veileder legger til en jobbsøker via «Finn og legg til» (kandidatsøk), returnerer API-et 80+ felt per kandidat. I dag lagrer vi bare `fodselsnummer`, `fornavn` og `etternavn`. Følgende felt er tilgjengelige og relevante å kopiere ved opprettelse:

### Felt vi kopierer til søketabellen

| Felt                   | Kilde i kandidatsøk              | Formål                                             |
| ---------------------- | -------------------------------- | -------------------------------------------------- |
| `fornavn`, `etternavn` | `fornavn`, `etternavn`           | Navnevisning + fritekst                            |
| `innsatsgruppe`        | `innsatsgruppe`                  | Filter (Gode muligheter, Trenger veiledning, etc.) |
| `fylke`                | `fylkeNavn`                      | Geografisk filter                                  |
| `kommune`              | `kommuneNavn`                    | Geografisk filter                                  |
| `poststed`             | `poststed`                       | Visning                                            |
| `navkontor`            | `navkontor`                      | Filter (allerede lagret)                           |
| `veileder_navident`    | `veilederIdent`                  | Filter (allerede lagret)                           |
| `veileder_navn`        | `veilederVisningsnavn`           | Visning (allerede lagret)                          |
| `telefonnummer`        | `mobiltelefon` / `telefon`       | Visning + fritekstsøk                              |
| `lagt_til_dato`        | Settes ved opprettelse           | Sortering og visning                               |
| `lagt_til_av`          | Nav-ident fra opprettelsesflyten | Visning (hvem som la til)                          |

### Felt vi IKKE kopierer

| Felt                                           | Begrunnelse                                                                                                                                                                       |
| ---------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| CV-data (kompetanse, yrkeserfaring, utdanning) | For mye data, ikke relevant for listefiltrering                                                                                                                                   |
| Reisevei / reiseavstand                        | Ikke relevant for treff-konteksten                                                                                                                                                |
| Hull i CV                                      | Kompleks algoritme (2 år uten jobb/utdanning siste 5 år) – krever enten kandidatsøk-API-endring for å eksponere flagget, eller lokal beregning fra CV-data. **Vurderes separat.** |
| Førerkort, språk, sertifikater                 | For detaljert for listefiltrering                                                                                                                                                 |
| E-post                                         | Ikke relevant for søk/filtrering                                                                                                                                                  |

### Prioriterte målgrupper

Prioriterte målgrupper tas ikke med nå. Både alder og hull i CV holdes utenfor første versjon, siden målgruppene kan endre seg og det er uklart når de skal brukes i prosessen.

---

## Filterkriterier

Basert på stilling sin kandidatliste, kandidatsøk-filtere, og designskisser, skal følgende kunne filtreres:

### Navn og identifikator

- **Fritekst (navn)**: Prefix-match (`LIKE 'tekst%'`) på `LOWER(fornavn)` og `LOWER(etternavn)` separat
- **Fødselsnummer**: Eksakt match på `fodselsnummer` når fritekst-input er 11 siffer (sendes i POST-body, ikke query-param, av sikkerhetshensyn)

### Jobbsøkers deltakerstatus

- **Status-filter**: `LAGT_TIL`, `INVITERT`, `SVART_JA`, `SVART_NEI`
- Lagret i `jobbsoker` og projisert til `jobbsoker_sok` for enkel filtrering

### Innsatsgruppe

- **Innsatsgruppe-filter**: `STANDARD_INNSATS`, `SITUASJONSBESTEMT_INNSATS`, `SPESIELT_TILPASSET_INNSATS`, `VARIG_TILPASSET_INNSATS`, `GRADERT_VARIG_TILPASSET_INNSATS`
- Kopiert fra kandidatsøk ved opprettelse, lagret i søketabellen
- UI-labels: «Gode muligheter», «Trenger veiledning», «Trenger veiledning, nedsatt arbeidsevne», etc.

### Geografi

- **Fylke**: Filter på `fylke` (kopiert fra kandidatsøk ved opprettelse)
- **Kommune**: Filter på `kommune` (kopiert fra kandidatsøk ved opprettelse)

### Veileder og kontor

- **Nav-kontor**: Filter på `navkontor` (lagret ved opprettelse)
- **Veileder**: Filter på `veileder_navident` (lagret ved opprettelse)

**Notat:** Alle felt kopieres fra kandidatsøk ved opprettelse. Dersom veilederfelter er `NULL`, ignoreres de i filtrering. Det samme gjelder geografifelter.

---

## API-kontrakt

### Gradvis innføring med dato-basert feature toggle

For å unngå ujevn datakvalitet på eldre treff innføres de nye filtrene gradvis.

- **Frontend-styrt innføring:** Frontend leser opprettetdato på treffet og viser bare de nye filtrene for treff opprettet etter en definert cut-off-dato.
- **Cut-off-dato:** Legges i frontend-konfigurasjon som en enkel feature toggle, for eksempel `JOBBSOKER_SOK_FILTER_CUTOFF_DATE`.
- **Praktisk effekt:**
  - Treff opprettet **før** cut-off-dato: viser bare basisvisning uten nye filtre, eller et redusert filtersett.
  - Treff opprettet **etter** cut-off-dato: viser fullt filtersett.
- **Begrunnelse:** Nye treff får søketabelldata ved opprettelse, mens eldre treff må backfilles best effort. Frontend kan derfor skjule filtre der vi vet at datagrunnlaget kan være ufullstendig.

Dette er i praksis en enkel feature toggle. Den styres av opprettetdato på treffet, ikke av bruker eller miljø per se.

### Søkeendepunkt (POST med JSON body)

Søket bruker **POST** med JSON body. Alle parametere sendes i body, inkludert fødselsnummer via `fritekst`-feltet.

**Påkrevd:**

- `side` (1-indeksert, integer, >= 1)
- `antallPerSide` (integer, default 25, 1–100)

Validering: `side >= 1` og `antallPerSide in 1..100`, ellers `IllegalArgumentException` → 400.

**Valgfrie:**

- `fritekst` – prefix-match på fornavn/etternavn, eller eksakt match på fødselsnummer (11 siffer)
- `status` – JSON-array av statuser (f.eks. `["INVITERT", "SVART_JA"]`)
- `sortering` – `navn` (default), `lagt-til`
- `retning` – `asc`, `desc` (default: `asc` for navn, `desc` for lagt-til)

**Eksempel uten filtre:**

```
POST /api/rekrutteringstreff/{treffId}/jobbsoker/sok
Content-Type: application/json

{ "side": 1, "antallPerSide": 25 }
```

**Eksempel med filtre:**

```
POST /api/rekrutteringstreff/{treffId}/jobbsoker/sok
Content-Type: application/json

{
  "fritekst": "Ola",
  "status": ["INVITERT", "SVART_JA"],
  "sortering": "navn",
  "retning": "asc",
  "side": 1,
  "antallPerSide": 25
}
```

**Eksempel fødselsnummer-oppslag (via fritekst):**

```
POST /api/rekrutteringstreff/{treffId}/jobbsoker/sok
Content-Type: application/json

{ "fritekst": "12345678901", "side": 1, "antallPerSide": 25 }
```

Fødselsnummer sendes i body via `fritekst`-feltet. Når verdien er 11 siffer, gjør backend eksakt match på `fodselsnummer`. Ellers gjøres prefix-match på `LOWER(fornavn)` og `LOWER(etternavn)`.

### Response DTO (MVP)

```json
{
  "totalt": 9996,
  "antallSkjulte": 3,
  "antallSlettede": 1,
  "side": 1,
  "antallPerSide": 25,
  "jobbsøkere": [
    {
      "personTreffId": "<uuid>",
      "fodselsnummer": "12345678901",
      "fornavn": "Ola",
      "etternavn": "Nordmann",
      "navkontor": "Nav Grünerløkka",
      "veilederNavn": "Per Pål",
      "veilederNavident": "B654321",
      "status": "INVITERT",
      "lagtTilDato": "2026-03-18T09:00:00Z",
      "lagtTilAv": "A123456",
      "minsideHendelser": [
        {
          "id": "<uuid>",
          "tidspunkt": "2026-03-20T10:05:00Z",
          "hendelsestype": "MOTTATT_SVAR_FRA_MINSIDE",
          "opprettetAvAktørType": "SYSTEM",
          "aktørIdentifikasjon": null,
          "hendelseData": { "eksternStatus": "SENDT", "minsideStatus": "AKTIV" }
        }
      ]
    }
  ]
}
```

**Notat:** `totalt` reflekterer kun aktive jobbsøkere (med `er_synlig = true` og `status != 'SLETTET'`). `antallSkjulte` og `antallSlettede` returneres separat for informasjon i UI.

**Notat:** `fodselsnummer` inkluderes i respons for visning i kandidatkortet (f.nr. under navn). Fødselsnummer-oppslag gjøres via eget POST-endepunkt.

**Notat:** `telefonnummer` kopieres fra kandidatsøk (`mobiltelefon` med fallback til `telefon`) og vises i listevisningen + inngår i fritekst.

**Notat:** `lagtTilDato` og `lagtTilAv` brukes for sortering og visning av hvem som la til jobbsøkeren.

**Notat:** `minsideHendelser` inkluderer kun hendelser av type `MOTTATT_SVAR_FRA_MINSIDE`, brukt for leverings- og svar-status i UI.

**Notat:** Kun `personTreffId` brukes som ekstern identifikator – dette er den eksisterende UUID-en (kolonnen `id` i `jobbsoker`). Den interne `jobbsoker_id` (bigserial) eksponeres aldri i API-et.

**Notat:** `lagtTilDato` mappes til `Instant` i Kotlin (ikke `ZonedDateTime`) – den kommer fra JDBC `Timestamp.toInstant()` og er en søke-DTO (listevisning), i tråd med tidstype-prinsippene i prinsipper.md.

---

## Database-strategi: View `jobbsoker_sok_view`

MVP-en bruker en **database-view** i stedet for en parallell søketabell. Viewet `jobbsoker_sok_view` joiner `jobbsoker` med `rekrutteringstreff` og henter `lagt_til_dato`/`lagt_til_av` via subquery mot `jobbsoker_hendelse`.

### Hvorfor view i stedet for parallell tabell?

- **Ingen synkronisering:** Viewet leser alltid oppdatert data fra domenetabellene
- **Enklere kode:** Ingen ekstra writes ved hendelser, ingen transaksjonssikring mellom tabeller
- **Tilstrekkelig ytelse:** Med riktige indekser holder viewet godt for 10K jobbsøkere per treff (verifisert med ytelsestest)

### Viewet `jobbsoker_sok_view`

```sql
CREATE OR REPLACE VIEW jobbsoker_sok_view AS
SELECT
    j.jobbsoker_id,
    j.id AS person_treff_id,
    rt.id AS treff_id,
    j.rekrutteringstreff_id,
    j.fodselsnummer,
    j.fornavn,
    j.etternavn,
    j.navkontor,
    j.veileder_navn,
    j.veileder_navident,
    j.status,
    (SELECT jh.tidspunkt
     FROM jobbsoker_hendelse jh
     WHERE jh.jobbsoker_id = j.jobbsoker_id
       AND jh.hendelsestype = 'OPPRETTET'
     ORDER BY jh.tidspunkt ASC
     LIMIT 1) AS lagt_til_dato,
    (SELECT jh.aktøridentifikasjon
     FROM jobbsoker_hendelse jh
     WHERE jh.jobbsoker_id = j.jobbsoker_id
       AND jh.hendelsestype = 'OPPRETTET'
     ORDER BY jh.tidspunkt ASC
     LIMIT 1) AS lagt_til_av
FROM jobbsoker j
JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
WHERE j.er_synlig = true
  AND j.status != 'SLETTET';
```

Viewet filtrerer automatisk ut skjulte og slettede jobbsøkere. `lagt_til_dato` og `lagt_til_av` hentes fra den eldste `OPPRETTET`-hendelsen.

### Indekser (V4-migrasjon)

```sql
-- Prefix-søk på fornavn/etternavn (text_pattern_ops for LIKE 'tekst%')
CREATE INDEX idx_jobbsoker_fornavn
    ON jobbsoker (rekrutteringstreff_id, LOWER(fornavn) text_pattern_ops)
    WHERE er_synlig = true AND status != 'SLETTET';

CREATE INDEX idx_jobbsoker_etternavn
    ON jobbsoker (rekrutteringstreff_id, LOWER(etternavn) text_pattern_ops)
    WHERE er_synlig = true AND status != 'SLETTET';

-- Aktive jobbsøkere per treff (partiell indeks)
CREATE INDEX idx_jobbsoker_sok_aktiv
    ON jobbsoker (rekrutteringstreff_id) WHERE er_synlig = true AND status != 'SLETTET';

-- Status-filtrering
CREATE INDEX idx_jobbsoker_sok_treff_status
    ON jobbsoker (rekrutteringstreff_id, status) WHERE er_synlig = true AND status != 'SLETTET';

-- Hendelser for lagt-til-dato (subquery i viewet)
CREATE INDEX idx_jobbsoker_hendelse_opprettet
    ON jobbsoker_hendelse (jobbsoker_id, tidspunkt ASC)
    WHERE hendelsestype = 'OPPRETTET';
```

### Mulig evolusjon til parallell tabell

Dersom vi senere trenger trigram-fritekst over mange felter (poststed, kommune, etc.) eller denormaliserte kandidatdata fra kandidatsøk, kan viewet erstattes med en materialisert tabell. Viewet er designet slik at repoet (`JobbsøkerSokRepository`) bare refererer til `jobbsoker_sok_view`-navnet, og kan dermed byttes ut uten endring i applikasjonskoden.

---

## Implementering

### MVP – View + prefix-søk på navn

**Database-valg:** Database-view (`jobbsoker_sok_view`) over `jobbsoker`-tabellen med partial indexes og prefix-søk (`text_pattern_ops`). Ingen parallell tabell, ingen pg_trgm.

**Oppgaver:**

## Implementering MVP – Gjennomført

### Database

- [x] `R__jobbsoker_sok_view.sql` – repeatable Flyway-migrasjon som oppretter viewet `jobbsoker_sok_view`
- [x] `V4__jobbsoker_sok.sql` – partial indexes for søk, sortering og fritekst (text_pattern_ops)
- [x] Ingen parallell tabell, ingen pg_trgm, ingen backfill nødvendig

### Backend (Kotlin/Javalin)

- [x] `JobbsøkerSokRepository` – søk mot viewet med dynamisk SQL og parameteriserte WHERE-klausuler
  - Fritekst: 11-sifret → eksakt fnr-match, ellers prefix-match på `LOWER(fornavn)` / `LOWER(etternavn)`
  - Status-filter: `IN (?)`-klausul
  - Paginering: `LIMIT/OFFSET` med side-clamping
  - Sortering: `navn` (etternavn + fornavn) eller `lagt-til` (dato fra hendelse)
  - Tellinger (skjulte/slettede) i egen query
  - Minside-hendelser hentes separat for treff på paginert side
- [x] `JobbsøkerSøkModeller` – request/response DTOer + sorteringsenums med Jackson-serialisering
- [x] `JobbsøkerController.søkJobbsøkereHandler()` – POST-endepunkt som erstatter `hentJobbsøkereHandler()`
  - Validerer `side >= 1` og `antallPerSide in 1..100`
  - Autorisasjon: kun eiere og utviklere
- [x] `JobbsøkerService.søkJobbsøkere()` – delegerer til `JobbsøkerSokRepository`
- [x] Refaktorert `leggTilJobbsøkere()` med hjelpemetoder for bedre lesbarhet

### Frontend (Next.js/TypeScript)

- [x] `useJobbsøkerSøk()` – SWR POST-hook med Zod-validert respons og body-basert cache-nøkkel
- [x] `useSWRPost()` – generisk SWR-hook for POST-forespørsler
- [x] `JobbsøkerSøkContext` – URL-synkronisert filter/sortering/paginering via `nuqs`
- [x] `JobbsøkerFilterrad` – søkefelt med enter-submit + statusfilter-popover
- [x] `StatusFilter` – checkboxgruppe for LAGT_TIL, INVITERT, SVART_JA, SVART_NEI
- [x] `JobbsøkerSøkChips` – aktive filtre som fjernbare chips
- [x] `JobbsøkerSortHeader` – sorteringsknapper med retningsikon
- [x] `Jobbsøkere` – hovedkomponent med paginering, bulk-avkrysning og invitasjonsflyt
- [x] MSW-mock (`jobbsøkereMswBackend.ts`) – komplett mock av søke-backend for lokal utvikling
- [x] `useJobbsøkere` beholdt som kompatibilitetswrapper for andre komponenter

### Tester

- [x] **Backend komponenttester** (`JobbsøkerSokKomponenttest`): 27 tester
  - Paginering, side-clamping, filtrering, kombinerte filtre, sortering (begge felt, begge retninger)
  - Fritekst (navn-prefix og fnr-eksakt), autorisasjon (403), skjulte/slettede ekskludert
  - Null-håndtering, minsideHendelser, treff-isolasjon
- [x] **Backend ytelsestest** (`JobbsøkerSokYtelsestest`): 10K jobbsøkere, warmup 2s, målt 500ms
- [x] **Frontend Playwright-tester**: Statusfiltrering, fritekst-søk, sortering, paginering, URL-synk
- [x] **Frontend visuell snapshot-testing**: Basisvisning og filtrert visning

### Ikke implementert i MVP (planlagt for senere)

- [ ] Dato-basert feature toggle for utvidede filtre
- [ ] Innsatsgruppe-filter
- [ ] Geografi-filtre (fylke/kommune)
- [ ] Nav-kontor-filter
- [ ] Veileder-filter
- [ ] Trigram-fritekst over flere felter (krever enten parallell tabell eller pg_trgm på view)

Ytelsen måles ende-til-ende (SQL-query inkl. indeksbruk), ikke bare DB-tid. Med trigram-indeksert fritekstsøk, B-tree-indekser på filtreringsfelter og 10K rader bør dette holde komfortabelt.

---

## Design og UI-referanser

Søk- og filtreringsgrenselaget er inspirert av **stilling-siden sin kandidatliste**. Vi gjenbruker etablert UX-pattern:

- **Søkebar** (fritekst) – søker i navn, veiledernavn/-ident, poststed, kommune og fylke via trigram-indeksert `sok_tekst`
- **Status-filter**: Lagt til, Invitert, Svart ja, Svart nei
- **Navkontor-combobox**: valg av ett kontor om gangen
- **Sted-combobox**: valg av ett sted om gangen, med kommune/fylke-format
- **Sortering-dropdown** med aktiv sortering (navn, invitert_dato, status)
- **Paginering** med side-info og resultat-tall

### Ikke inkludert i designet

- **Reisevei / reiseavstand** – ikke relevant for treff-konteksten
- **Vis slettede** – slettede er permanent ekskludert fra søk
- **Prioriterte målgrupper** – tas ikke med nå
- **Hull i CV** – tas ikke med nå
- **Notater / intern status** (Vurderes, Kontaktet, Til intervju etc.) – synlig i designskisser fra stilling, men vurderes som eget tillegg

Dette sikrer konsistens på tvers av Nav-plattformen og reduserer design-arbeid.

---

## Testing-strategi

### Backend (Kotlin/Javalin)

- **Komponenttester** (primært): Full Testcontainers-oppsett med ekte PostgreSQL og HTTP-kall
  - Tester søk mot oppfylt `jobbsoker_sok`-tabell med kandidatdata + status
  - Validerer paginering, sortering, auth
  - Verifiserer synkronisering mellom tabellene `jobbsoker` og `jobbsoker_sok`
  - Bekreft at trigram-indeksert fritekstsøk via `sok_tekst` fungerer over navn + geografi
- **Service-tester**: Isolert filtrerings-logikk (boundary-testing av WHERE-klausuler)
- **Repository-tester**: SQL-queries mot mock-data

### Gradvis innføring / backfill

- Test at eldre treff ikke får nye filtre aktivert i frontend når opprettetdato er før cut-off.
- Test at nyere treff får fullt filtersett.
- Test at søket fungerer selv om enkelte backfill-felter er `NULL` på eldre data.

### Frontend (Next.js/TypeScript)

- **Playwright e2e-tester**: Søk-flow, filtrering, paginering, sortering i ekte browser
  - Responsivitets-testing (mobile breakpoints)
  - Validerer MSW-mock-respons og UI-rendering
- **Vitest/React testing library**: Komponent-unit-tester (filter-pills, sortering-dropdown, etc.)

---

## Flytting av eldre data til søketabellen

For eldre treff må vi flytte så mye data som mulig til `jobbsoker_sok`, men akseptere at vi ikke nødvendigvis får komplett datadekning for alle søkefelt.

### Mål

- Alle eksisterende jobbsøkere skal få en rad i `jobbsoker_sok`.
- Alle felter vi allerede har lokalt skal backfilles deterministisk.
- Eksterne kandidatdata berikes best effort der det er mulig.

### Trinnvis plan

1. **Lokal backfill fra egne tabeller**

- Opprett rader i `jobbsoker_sok` for alle eksisterende jobbsøkere.
- Fyll inn felter vi allerede har i egne tabeller:
  - `jobbsoker_id`
  - `rekrutteringstreff_id`
  - `status`
  - `invitert_dato` fra `jobbsoker_hendelse`
  - `er_synlig`
  - `fornavn`, `etternavn`
  - `navkontor`, `veileder_navident`, `veileder_navn`

2. **Best-effort berikelse fra kandidatsøk**

- For jobbsøkere der vi har `fodselsnummer`, gjør vi et batch- eller iterativt oppslag mot kandidatsøk for å hente:
  - `innsatsgruppe`
  - `fylke`
  - `kommune`
  - `poststed`
- Dersom oppslag feiler, beholdes raden med delvis data.
- Backfillen skal ikke stoppe på enkeltsvikt.

3. **Kvalitetssikring og aktivering**

- Mål hvor stor andel av eldre treff som har fått de nye feltene fylt ut.
- Bruk denne dekningen som grunnlag for om cut-off-dato kan flyttes bakover senere.

### Viktige føringer

- Backfill for eldre treff er **best effort**, ikke en forutsetning for at søketabellen skal kunne tas i bruk.
- Treff uten komplett backfill skal fortsatt fungere med basisdata.
- Frontend-cut-off beskytter brukeropplevelsen mens vi bygger opp dekning.

---

## Mulige løpende oppdateringer utover synlighet

I første versjon oppdateres `jobbsoker_sok` løpende for:

- `status`
- `invitert_dato`
- `er_synlig`

Det kan senere være aktuelt å oppdatere flere felter dersom vi får meldinger om endrede data eller ser et tydelig behov.

### Aktuelle felter å kunne oppdatere senere

- `veileder_navident` / `veileder_navn`
- `navkontor`
- `fornavn` / `etternavn`
- `innsatsgruppe`
- `fylke`, `kommune`, `poststed`

### Vurdering

- Dette er mulig dersom vi får data fra kandidatfeed, etter samme mønster som synlighet oppdateres fra synlighetsmotoren.
- Det er ikke nødvendig for MVP så lenge målet er god søkbarhet ved opprettelse + korrekt status/synlighet over tid.
- Dersom vi senere ser at søkeresultatene blir merkbart utdaterte, kan `jobbsoker_sok` utvides med en egen berikelsesjobb eller hendelsesdrevet oppdatering.

Poenget er at søketabellen ikke bare trenger å tenke på synlighet. Den kan også være et sted for kontrollert oppdatering av andre kopierte felter dersom det viser seg viktig.

---

## Autorisasjon og synlighet

- **Søk innad i treff:** Kun tilgjengelig for **eiere** og **utvikler** (admin = utvikler, samme tilgang)
- **Arbeidsgiver-brukere:** Får varsler om kandidater via separat mekanisme, ikke direkte søk
- **Jobbsøker-bruker:** Ser sitt eget treff-svar, ikke søkbar liste

---

## Avklaringer og åpne spørsmål

### 1. Paginering med skjulte/slettede jobbsøkere

**Valg:** Skjulte og slettede jobbsøkere filtreres ut FØR paginering, ikke etter. De er aldri med i hverken resultat eller telling.

**Implementering:**

```sql
SELECT COUNT(*)
FROM jobbsoker_sok_view
WHERE rekrutteringstreff_id = ?
  AND (øvrige filtre);

SELECT *
FROM jobbsoker_sok_view
WHERE rekrutteringstreff_id = ?
  AND (øvrige filtre)
ORDER BY (sortering)
LIMIT 25 OFFSET (side-1)*25;
```

**Konsekvenser:**

- `totalt` returneres alltid, også når resultatsiden er tom
- Skjulte (`er_synlig = false`) og slettede (`status = 'SLETTET'`) er _ekskludert_ fra alt
- Hver side garanteres fulle `antallPerSide` rader (bortsett fra potensielt siste side)
- Brukeren opplever konsistent paginering

**Eksempel:** Hvis 10 000 jobbsøkere totalt, 4 skjulte/slettede → totalt = 9996, side 1 = 25 aktive.

### 2. Volume og akseptabel ytelse

- **Antall treff i systemet:** ~1M (ikke søkt alle samtidig)
- **Jobbsøkere per treff:** Opp til 10 000 (søkes innad ett treff av gangen)
- **Akseptabel søketid:** < 200 ms for navn-søk + filtrering over full treff-liste

**Løsning:** `pg_trgm` + GIN-indeks på `sok_tekst`, B-tree-indekser på filtreringsfelter og partiell indeks på aktive håndterer disse volumene.

### 3. Kandidatdata kopiert ved opprettelse

**Valg:** Kopier utvalgte felt fra kandidatsøkresultatet ved opprettelse. Feltene lagres i `jobbsoker_sok`.

**Felter som kopieres:**

- `innsatsgruppe` – for § 14 a-filtrering
- `fylke`, `kommune`, `poststed` – for geografisk filtrering og visning
- `telefonnummer` – for visning og fritekst
- `lagt_til_dato`, `lagt_til_av` – for sortering og visning

**Felter som IKKE kopieres:**

- CV-data (kompetanse, yrkeserfaring, utdanning) – for detaljert, ikke relevant for listefiltrering
- Prioriterte målgrupper – tas ikke med nå
- Hull i CV – tas ikke med nå
- Reisevei – ikke relevant for treff

**Begrunnelse:** Backend beriker jobbsøkere med data fra kandidatsøk-API ved opprettelse. Dersom frontend allerede sender feltene, brukes de direkte; ellers henter backend dem via `/api/multiple-lookup-cv`. Oppslaget er graceful – jobbsøkere som ikke finnes i kandidatsøk legges til uten berikelse, og frontend får beskjed om hvilke som mangler (HTTP 207, se under).

### 4. Paginering og sortering

**Valg:** Backend håndterer både paginering og sortering via query-params.

**Implementering:**

- Paginering: `side` (1-indeksert) + `antallPerSide` (default 25)
- Sortering: `sortering` med verdier `navn`, `lagt-til` + `retning` (`asc`, `desc`) med standardretning per felt
- Backend returnerer `totalt` via egen `COUNT(*)`-query med samme filtre som resultatsøket

**Begrunnelse:** Samme mønster som stilling-søk. Mindre data over nettet, bedre for mobile.

---

## Vedlegg: Flow diagram – Søk og filtrering (MVP)

```
MVP – POST-basert søk mot database-view
──────────────────────────────────

Frontend søk (POST med JSON body):
  POST /api/rekrutteringstreff/{treffId}/jobbsoker/sok
  body: { "side": 1, "antallPerSide": 25 }
    ↓ (returnerer alle aktive jobbsøkere med paginering)

Frontend søk med filtre (POST med JSON body):
  POST /api/rekrutteringstreff/{treffId}/jobbsoker/sok
  body: {
    "fritekst": "Ola",
    "status": ["INVITERT"],
    "sortering": "navn",
    "retning": "asc",
    "side": 1,
    "antallPerSide": 25
  }
    ↓ (returnerer filtrert resultat med paginering)

Begge søke-casene:
    ↓
SQL mot jobbsoker_sok_view (database-view):
  -- Telling
  SELECT count(*) FROM jobbsoker_sok_view v
  WHERE v.treff_id = ?
    AND (LOWER(v.fornavn) LIKE ? OR LOWER(v.etternavn) LIKE ?)  -- prefix-søk
    AND (v.status IN (?))                                        -- status-filter

  -- Treff
  SELECT ... FROM jobbsoker_sok_view v
  WHERE (samme filtre)
  ORDER BY LOWER(v.etternavn) ASC, LOWER(v.fornavn) ASC
  LIMIT ? OFFSET ?

  -- Tellinger (skjulte/slettede)
  SELECT COUNT(*) FILTER (WHERE status != 'SLETTET' AND er_synlig = FALSE) ...
    ↓
Partial indexes på jobbsoker + hendelse-indeks håndterer WHERE-klausulene
    ↓
JSON-respons med resultater + side-info
    ↓
Frontend viser resultater + chips + paginering
```

---

## Relatert dokumentasjon

- [Utvidbarhet.md](../6-kvalitet/utvidbarhet.md) – søk-strategi generelt
- [Arbeidsgivers behov](arbeidsgivers-behov.md) – arbeidsgiverns behovs-filterkriterier
- [Database.md](../2-arkitektur/database.md) – tabell-struktur
- [Kandidatsøk-integrasjon](../4-integrasjoner/) – Arena/kandidatsøk-API

## Antagelser og valg

✅ **Backend:** POST-basert søkeendepunkt erstatter `hentJobbsøkereHandler()` – håndterer både "hent alle" og "søk med filtre"  
✅ **Frontend:** `useJobbsøkerSøk()` + `JobbsøkerSøkContext` med URL-synkronisert tilstand via `nuqs`  
✅ **Database-strategi:** View `jobbsoker_sok_view` mot eksisterende tabeller – enklere enn parallell tabell  
✅ **Fritekst:** Prefix-match på navn + eksakt match på fødselsnummer  
✅ **Indekser:** Partial B-tree med `text_pattern_ops` for prefix-søk, partiell indeks for aktive jobbsøkere  
✅ **Sortering:** `navn` (etternavn + fornavn) og `lagt-til` (dato) med eksplisitt retning  
✅ **Paginering:** Backend-håndtert med `side` + `antallPerSide`, side-clamping ved for høy side  
✅ **OpenSearch:** Utelukket – synkron PostgreSQL-løsning  
✅ **Autorisasjon:** Kun eiere og utviklere  
✅ **Ytelsestest:** 10K jobbsøkere, warmup 2s, målt 500ms  
✅ **MinsideHendelser:** Kun `MOTTATT_SVAR_FRA_MINSIDE`-hendelser, hentet separat per side  
⬜ **Planlagt:** Innsatsgruppe, geografi, navkontor, veileder-filtre (krever evt. parallell tabell)
