# Plan: Søk og filtrering av jobbsøkere i rekrutteringstreff

**Status:** Plan  
**Omfang:** Backend API + frontend UI for søk og filtrering av jobbsøkere innad i et treff

Når eiere og markedskontakter skal administrere et rekrutteringstreff med mange jobbsøkere (opp til 10 000), må de kunne søke og filtrere deltakerne for å finne relevante kandidater.

---

## Oversikt

Planen beskriver arkitektur for søk og filtrering av jobbsøkere innad i et treff. Det nye søkeendepunktet **erstatter** det eksisterende `GET /api/rekrutteringstreff/{id}/jobbsoker` med paginering og filtre. Det eksisterende hendelsesendepunktet (`GET .../jobbsoker/hendelser`) beholdes uendret for statushistorikk.

1. **Tilgjengelig data fra kandidatsøk** – hva som finnes og hva vi kopierer ved opprettelse
2. **Filterkriterier** – hvilke feltdimensjoner som kan filtreres (valgfrie)
3. **API-kontrakt** – request/response DTOs med påkrevd paginering
4. **Database-strategi** – parallell søketabell `jobbsoker_sok`
5. **Implementering** – oppgaver for MVP

---

## Tilgjengelig data fra kandidatsøk

Når en veileder legger til en jobbsøker via «Finn og legg til» (kandidatsøk), returnerer API-et 80+ felt per kandidat. I dag lagrer vi bare `fodselsnummer`, `fornavn` og `etternavn`. Følgende felt er tilgjengelige og relevante å kopiere ved opprettelse:

### Felt vi kopierer til søketabellen

| Felt                   | Kilde i kandidatsøk    | Formål                                             |
| ---------------------- | ---------------------- | -------------------------------------------------- |
| `fornavn`, `etternavn` | `fornavn`, `etternavn` | Navnevisning + fritekst                            |
| `innsatsgruppe`        | `innsatsgruppe`        | Filter (Gode muligheter, Trenger veiledning, etc.) |
| `fylke`                | `fylkeNavn`            | Geografisk filter                                  |
| `kommune`              | `kommuneNavn`          | Geografisk filter                                  |
| `poststed`             | `poststed`             | Visning                                            |
| `navkontor`            | `navkontor`            | Filter (allerede lagret)                           |
| `veileder_navident`    | `veilederIdent`        | Filter (allerede lagret)                           |
| `veileder_navn`        | `veilederVisningsnavn` | Visning (allerede lagret)                          |

### Felt vi IKKE kopierer

| Felt                                           | Begrunnelse                                                                                                                                                                       |
| ---------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| CV-data (kompetanse, yrkeserfaring, utdanning) | For mye data, ikke relevant for listefiltring                                                                                                                                     |
| Reisevei / reiseavstand                        | Ikke relevant for treff-konteksten                                                                                                                                                |
| Hull i CV                                      | Kompleks algoritme (2 år uten jobb/utdanning siste 5 år) – krever enten kandidatsøk-API-endring for å eksponere flagget, eller lokal beregning fra CV-data. **Vurderes separat.** |
| Førerkort, språk, sertifikater                 | For detaljert for listefiltring                                                                                                                                                   |
| Kontaktinfo (telefon, e-post)                  | Ikke relevant for søk/filtrering                                                                                                                                                  |

### Prioriterte målgrupper

Prioriterte målgrupper tas ikke med nå. Både alder og hull i CV holdes utenfor første versjon, siden målgruppene kan endre seg og det er uklart når de skal brukes i prosessen.

---

## Filterkriterier

Basert på stilling sin kandidatliste, kandidatsøk-filtere, og designskisser, skal følgende kunne filtreres:

### Navn og identifikator

- **Fritekst**: Søk i trigram-indeksert `sok_tekst` (navn + poststed + kommune + fylke)
- **Fødselsnummer**: Eksakt oppslag via POST-body (ikke query-param, av sikkerhetshensyn – samme mønster som stilling sitt kandidatsøk)

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

### Query-parametere (MVP)

**Påkrevd (paginering):**

- `side` (1-indeksert, integer, >= 1)
- `antallPerSide` (integer, default 20, 1–100)

Validering: `side >= 1` og `antallPerSide in 1..100`, ellers `IllegalArgumentException` → 400. Samme mønster som `RekrutteringstreffSokController`.

**Valgfrie (filtre):**

- `fritekst` – søk i trigram-indeksert `sok_tekst` (navn + poststed + kommune + fylke)
- `status` – kommaseparert liste (f.eks. `INVITERT,SVART_JA`)
- `innsatsgruppe` – kommaseparert liste (f.eks. `STANDARD_INNSATS,SITUASJONSBESTEMT_INNSATS`)
- `fylke` – filter på fylke
- `kommune` – filter på kommune
- `navkontor` – filter på kontor
- `veileder` – filter på veileder (navident)
- `sortering` – `navn`, `invitert_dato`, `status` (default: `navn`)

**Eksempel uten filtre (hent alle):**

```
GET /api/rekrutteringstreff/{treffId}/jobbsoker?side=1&antallPerSide=20
```

**Eksempel med filtre:**

```
GET /api/rekrutteringstreff/{treffId}/jobbsoker
  ?fritekst=Ola
  &status=INVITERT,SVART_JA
  &innsatsgruppe=STANDARD_INNSATS
  &fylke=Oslo
  &side=1
  &antallPerSide=20
  &sortering=navn
```

**Eksempel fødselsnummer-oppslag (POST):**

```
POST /api/rekrutteringstreff/{treffId}/jobbsoker/sok
Content-Type: application/json

{ "fodselsnummer": "12345678901" }
```

Fødselsnummer sendes i body, ikke som query-param – samme mønster som `rekrutteringsbistand-kandidatsok-api` bruker for `/kandidatsok/navn` og `/kandidatsok/arena-kandidatnr`.

Oppslaget er treff-skopet: backend slår opp på `rekrutteringstreff_id + fodselsnummer`, ikke bare `fodselsnummer` alene.

### Response DTO (MVP)

```
{
  "totalt": 9996,                               // Totalt antall AKTIVE jobbsøkere (skjulte/slettede ekskludert)
  "side": 1,                                    // Nåværende side (paginering)
  "antallPerSide": 20,                          // Jobbsøkere per side
  "jobbsøkere": [
    {
      "personTreffId": "<uuid>",
      "fornavn": "Ola",
      "etternavn": "Nordmann",
      "innsatsgruppe": "STANDARD_INNSATS",
      "fylke": "Oslo",
      "kommune": "Oslo",
      "poststed": "Oslo",
      "navkontor": "0301",
      "veilederNavn": "Per Pål",
      "veilederNavident": "B654321",
      "status": "INVITERT",
      "invitertDato": "2026-03-20T10:00:00Z"
    }
  ]
}
```

**Notat:** `totalt` reflekterer kun aktive jobbsøkere (med `er_synlig = true` og `status != 'SLETTET'`). Skjulte og slettede jobbsøkere telles ikke og vises ikke, hvilket sikrer fulle sider.

**Notat:** `fodselsnummer` er bevisst utelatt fra response. Fødselsnummer er kun relevant som input-filter (oppslag), ikke som visningsdata.

**Notat:** `erSynlig` er fjernet fra response – skjulte jobbsøkere vises aldri, så feltet er alltid `true` og dermed overflødig.

**Notat:** Kun `personTreffId` brukes som ekstern identifikator – dette er den eksisterende UUID-en (kolonnen `id` i `jobbsoker`). Den interne `jobbsoker_id` (bigserial) eksponeres aldri i API-et.

**Notat:** `invitertDato` mappes til `Instant` i Kotlin (ikke `ZonedDateTime`) – den kommer fra JDBC `Timestamp.toInstant()` og er en søke-DTO (listevisning), i tråd med tidstype-prinsippene i prinsipper.md.

---

## Database-strategi: Parallell søketabell `jobbsoker_sok`

I stedet for å fortsette å legge søkefelter direkte på tabellen `jobbsoker`, innfører vi en dedikert søketabell. Tabellen `jobbsoker` forblir domenetabell, mens `jobbsoker_sok` er søkeprojeksjonen.

### Hvorfor egen søketabell?

- **Separasjon:** Tabellen `jobbsoker` er domenetabell med hendelsesdrevet status. Tabellen `jobbsoker_sok` er optimert for filtrering.
- **Ekstra felter:** Innsatsgruppe og geografi – data fra kandidatsøk som ikke er del av kjernedomenet.
- **Trigram-indeksert fritekstsøk:** Generert kolonne `sok_tekst` + `pg_trgm` for rask `ILIKE '%...%'` over flere felter.
- **Uavhengig evolusjon:** Nye søkbare felt kan legges til uten å endre domenetabellen.

### Tabellen `jobbsoker_sok`

```sql
CREATE TABLE jobbsoker_sok (
    jobbsoker_id           bigint PRIMARY KEY REFERENCES jobbsoker(jobbsoker_id),
    rekrutteringstreff_id  bigint NOT NULL,

    -- Kopiert fra jobbsøker-raden (oppdateres ved hendelser)
    status                 text NOT NULL DEFAULT 'LAGT_TIL',
    invitert_dato          timestamptz,
    er_synlig              boolean NOT NULL DEFAULT TRUE,

    -- Personalia (kopiert fra kandidatsøk ved opprettelse)
    fornavn                text,
    etternavn              text,

    -- Geografi (kopiert fra kandidatsøk ved opprettelse)
    fylke                  text,
    kommune                text,
    poststed               text,

    -- Nav-data (kopiert ved opprettelse)
    navkontor              text,
    veileder_navident      text,
    veileder_navn          text,
    innsatsgruppe          text,

    -- Grunnlag for trigram-indeksert fritekstsøk
    sok_tekst              text GENERATED ALWAYS AS (
        LOWER(
            COALESCE(fornavn, '') || ' ' ||
            COALESCE(etternavn, '') || ' ' ||
            COALESCE(poststed, '') || ' ' ||
            COALESCE(kommune, '') || ' ' ||
            COALESCE(fylke, '')
        )
    ) STORED
);
```

`sok_tekst` brukes sammen med `pg_trgm`, slik at `ILIKE '%...%'` kan bruke indeks og ikke ende i full tabellskann.

### Synkronisering

- **Ved opprettelse:** En rad i `jobbsoker_sok` opprettes atomisk sammen med raden i `jobbsoker`, i samme transaksjon.
- **Ved hendelser:** Når en rad i `jobbsoker_hendelse` opprettes, oppdateres `jobbsoker.status` og `jobbsoker_sok.status` + `jobbsoker_sok.invitert_dato` atomisk.
- **Ved synlighetsoppdatering:** `jobbsoker_sok.er_synlig` oppdateres sammen med `jobbsoker.er_synlig`.
- **Kandidatdata (innsatsgruppe, geografi):** Settes én gang ved opprettelse, endres ikke.

Dette bør eies av felles servicemetoder i service-laget, ikke spres utover flere kallesteder. Service-laget orkestrerer oppdateringene mot `jobbsoker`, `jobbsoker_sok` og eventuelt `jobbsoker_hendelse` i én transaksjon, slik at vi unngår avvik mellom domenetabell og søketabell.

### Indekser

Trigram-indeksert fritekstsøk forutsetter `pg_trgm`:

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_jobbsoker_sok_tekst
  ON jobbsoker_sok USING gin (sok_tekst gin_trgm_ops);
```

#### MVP-indekser (start med disse)

1. **GIN på trigram-fritekst** – `idx_jobbsoker_sok_tekst ON jobbsoker_sok USING gin (sok_tekst gin_trgm_ops)`
2. **Partiell indeks** – `idx_jobbsoker_sok_aktiv ON jobbsoker_sok (rekrutteringstreff_id) WHERE er_synlig = true AND status != 'SLETTET'`
3. **Komposittindeks** – `idx_jobbsoker_sok_treff_status ON (rekrutteringstreff_id, status) WHERE er_synlig = true`

Alle spørringer er skopet til `rekrutteringstreff_id`, så enkeltstående B-tree-indekser på `status`, `fylke` osv. vil trolig ikke brukes av query planner alene. Start med de tre over, mål ytelse, og legg til komposittindekser ved behov.

#### Kandidatindekser (legg til ved behov etter ytelsesmåling)

4. **B-tree** – `idx_jobbsoker_sok_treff_innsatsgruppe ON (rekrutteringstreff_id, innsatsgruppe)`
5. **B-tree** – `idx_jobbsoker_sok_treff_fylke ON (rekrutteringstreff_id, fylke)`
6. **B-tree** – `idx_jobbsoker_sok_treff_navkontor ON (rekrutteringstreff_id, navkontor)`
7. **B-tree** – `idx_jobbsoker_sok_treff_invitert_dato ON (rekrutteringstreff_id, invitert_dato)`

### Tabellen `jobbsoker` forblir uendret

`jobbsoker` beholder sine eksisterende kolonner (personalia, status, synlighet). Ingen nye kolonner legges til der – alle søkespesifikke felter går i `jobbsoker_sok`.

---

## Implementering

### MVP – Parallell søketabell + utvidet datainnhenting

**Database-valg:** Egen `jobbsoker_sok`-tabell med denormalisert status, kandidatdata fra kandidatsøk, og trigram-indeksert fritekstsøk.

**Oppgaver:**

- [ ] Flyway-migrasjon: Opprett `jobbsoker_sok`-tabell
  - `CREATE TABLE jobbsoker_sok (...)` med alle kolonner inkl. generert `sok_tekst`
  - `CREATE EXTENSION IF NOT EXISTS pg_trgm`
  - Opprett GIN trigram-indeks på `sok_tekst` med `gin_trgm_ops`
  - MVP-indekser som beskrevet i database-strategi-seksjonen (start med 3, utvid ved behov)
  - Backfill fra eksisterende data: `INSERT INTO jobbsoker_sok SELECT ... FROM jobbsoker`
  - Backfill `invitert_dato` fra hendelser: subquery mot `jobbsoker_hendelse`
  - **Merk:** Eldre jobbsøkere backfilles med `NULL` for `innsatsgruppe`, `fylke`, `kommune`, `poststed` – disse feltene finnes først fra kandidatsøk og fylles kun ved nye opprettelser. Dette er bevisst og grunnen til cut-off-datoen i frontend.
- [ ] Utvid opprettelse-DTO: Frontend sender med ekstra felter fra kandidatsøk
  - Nye felter i request: `innsatsgruppe`, `fylke`, `kommune`, `poststed`
  - Backend validerer og lagrer i `jobbsoker_sok` atomisk sammen med raden i `jobbsoker`
  - **Frontend-endring nødvendig:** I dag sender frontend kun `fødselsnummer`, `fornavn`, `etternavn` ved opprettelse. Dataen fra kandidatsøk (innsatsgruppe, geografi) er tilgjengelig i frontend, men sendes ikke med. Frontend må utvides til å inkludere disse feltene i request-body.
- [ ] Lag felles servicemetoder for transaksjonssikre oppdateringer
  - Én metode for opprettelse som skriver til `jobbsoker`, `jobbsoker_sok` og `jobbsoker_hendelse` i samme transaksjon
  - Én metode for hendelsesdrevet oppdatering som oppdaterer både domenetabell og søketabell i samme transaksjon
  - Én metode for synlighetsoppdatering som oppdaterer `jobbsoker.er_synlig` og `jobbsoker_sok.er_synlig` i samme transaksjon
- [ ] Implementer søke-endepunkt med dynamisk SQL
  - Controller: parser og validerer query-parametere (`side`, `antallPerSide`, filtre)
  - Repository: bygger dynamisk SQL med parameteriserte WHERE-betingelser
  - Response-DTO: `JobbsøkerSokOutboundDto` med paginert resultat
- [ ] Frontend: Oppdater opprettelse til å sende innsatsgruppe, fylke, kommune, poststed
- [ ] Skriv komponenttester for paginert søk med filtre
  - Test filtrering, paginering, fritekst, tom resultatsett, kombinasjonsfiltre
  - Samme mønster som eksisterende komponenttester med Testcontainers + ekte HTTP-kall
- [ ] Legg inn dato-basert feature toggle i frontend
  - Les opprettetdato på treffet fra treffdetaljene
  - Skjul nye filtre for treff opprettet før cut-off-dato
  - Vis fullt filtersett for treff opprettet etter cut-off-dato
- [ ] Oppdater hendelseslogikk: når en hendelse opprettes i `jobbsoker_hendelse`, oppdater `jobbsoker.status` og `jobbsoker_sok.status` + `jobbsoker_sok.invitert_dato` atomisk
- [ ] Lag `JobbsøkerSøkResultat` DTO med paginering + sortering
- [ ] **Erstatt `hentJobbsøkereHandler()` med én unified handler** som:
  - Tar query-parametere: `side` (påkrevd), `antallPerSide` (påkrevd), + valgfrie filtre
  - Hvis ingen filtre: returnerer alle aktive jobbsøkere (med paginering)
  - Hvis filtre: returnerer filtrert resultat (med paginering)
- [ ] Implementer `JobbsøkerSokRepository.sok()` med WHERE-klausuler mot `jobbsoker_sok`:
  - `sok_tekst ILIKE ?` (trigram-indeksert fritekst over navn + poststed + kommune + fylke, valgfritt)
  - `status IN (?)` (status-liste, valgfritt)
  - `innsatsgruppe IN (?)` (innsatsgruppe-liste, valgfritt)
  - `fylke = ?` (fylke, valgfritt)
  - `kommune = ?` (kommune, valgfritt)
  - `navkontor = ?` (kontor, valgfritt)
  - `veileder_navident = ?` (veileder, valgfritt)
  - **`er_synlig = true`** (filtrere bort skjulte jobbsøkere)
  - **`status != 'SLETTET'`** (filtrere bort slettede jobbsøkere)
- [ ] Implementer `JobbsøkerRepository.hentViaFodselsnummer()` – eget POST-endepunkt
  - Oppslag på `rekrutteringstreff_id = ? AND fodselsnummer = ?` mot `jobbsoker`-tabellen
  - Fødselsnummer sendes i request body, mens `rekrutteringstreff_id` kommer fra path-parametret
- [ ] Implementer paginering (LIMIT/OFFSET) og sortering
  - **Rekkefølge:** WHERE-filtrering (inkl. `er_synlig = true AND status != 'SLETTET'`) → ORDER BY sortering → LIMIT/OFFSET paginering
  - `totalt` = COUNT(\*) med WHERE `er_synlig = true AND status != 'SLETTET'` (+ eventuelle filtre), utført i samme query
  - Skjulte og slettede er _aldri_ med i hverken telling eller resultatsett
  - Garanterer fulle sider uten hull
- [ ] **Backend-tester:**
  - Komponenttester: Full søk-flow mot Testcontainers-database inkl. autorisasjon
    - Test: Hent alle uten filtre (no-filter case)
    - Test: Søk med filtre (filtered case)
    - Test: Paginering kreves (skal feile uten side/antallPerSide)
  - Service-tester: Filtrerings-logikk, paginering, sortering
  - Repository-tester: SQL-queries mot mock-tabell
- [ ] Frontend-UI: søkebar + filter-sidebar (lignende stilling)
  - **Én custom hook `useJobbsøkerSøk()`** som håndterer både "hent alle" og "søk med filtre"
    - Input: `{ treffId, side, antallPerSide, filtre?: {...} }`
    - Output: `{ data, isLoading, error, mutate }`
    - Bruker SWR med query-params (kun filtre som er satt sendes med)
    - Eksempel uten filtre: `GET /api/rekrutteringstreff/{treffId}/jobbsoker?side=1&antallPerSide=20`
    - Eksempel med filtre: `GET /api/rekrutteringstreff/{treffId}/jobbsoker?side=1&antallPerSide=20&fritekst=Ola&status=INVITERT&innsatsgruppe=STANDARD_INNSATS`
  - **Utvid `leggTilJobbsøkere`-mutasjon**: Send med ekstra felter fra kandidatsøk-resultat
    - Nye felter: `innsatsgruppe`, `fylke`, `kommune`, `poststed`
    - Data plukkes fra kandidatsøk-resultat som allerede er tilgjengelig i frontend
  - Dynamisk **MSW-mock-store** (som rekrutteringstreff-søk) for rask lokal utvikling
  - Paginering-komponenter og sortering-dropdown
  - Responsiv design for mobile/tablet/desktop
- [ ] **Frontend-tester:**
  - Playwright e2e-tester: Søk, filtrering, paginering, sortering
  - Responsivitet og UX-validering

### Ytelsestesting

Følger samme mønster som `RekrutteringstreffSokYtelsestest` – egne terskelkonstanter, seed-data, warmup + målt kall:

- **Seed:** Opprett ett treff med 10 000 jobbsøkere (ulike statuser, navkontorer, veiledere)
- **Warmup-kall:** Hent alle uten filtre, terskel 2 000 ms
- **Målt kall:** Søk med navn-fritekst + status-filter + sortering, terskel 500 ms
- **Sorteringskall:** Sorter på `invitert_dato` (annen sortering enn naturlig rekkefølge), terskel 500 ms

Ytelsen måles ende-til-ende (SQL-query inkl. indeksbruk), ikke bare DB-tid. Med trigram-indeksert fritekstsøk, B-tree-indekser på filtreringsfelter og 10K rader bør dette holde komfortabelt.

---

## Design og UI-referanser

Søk- og filtreringsgrenselaget er inspirert av **stilling-siden sin kandidatliste**. Vi gjenbruker etablert UX-pattern:

- **Søkebar** (fritekst) – søker i navn, poststed, kommune, fylke via trigram-indeksert `sok_tekst`
- **Filter-sidebar** (høyre side, som stilling):
  - **Hendelser** (status): Lagt til, Invitert, Svart ja, Svart nei – med antall i parentes
  - **Innsatsgruppe (§ 14 a)**: Gode muligheter, Trenger veiledning, Trenger veiledning nedsatt arbeidsevne, Jobbe delvis, Liten mulighet til å jobbe – med antall
  - **(ev.) Fylke / Kommune**: Geografisk filtrering
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
FROM jobbsoker_sok
WHERE rekrutteringstreff_id = ?
  AND er_synlig = true
  AND status != 'SLETTET'
  AND (øvrige filtre);

SELECT *
FROM jobbsoker_sok
WHERE rekrutteringstreff_id = ?
  AND er_synlig = true
  AND status != 'SLETTET'
  AND (øvrige filtre)
ORDER BY (sortering)
LIMIT 20 OFFSET (side-1)*20;
```

**Konsekvenser:**

- `totalt` returneres alltid, også når resultatsiden er tom
- Skjulte (`er_synlig = false`) og slettede (`status = 'SLETTET'`) er _ekskludert_ fra alt
- Hver side garanteres fulle `antallPerSide` rader (bortsett fra potensielt siste side)
- Brukeren opplever konsistent paginering

**Eksempel:** Hvis 10 000 jobbsøkere totalt, 4 skjulte/slettede → totalt = 9996, side 1 = 20 aktive.

### 2. Volume og akseptabel ytelse

- **Antall treff i systemet:** ~1M (ikke søkt alle samtidig)
- **Jobbsøkere per treff:** Opp til 10 000 (søkes innad ett treff av gangen)
- **Akseptabel søketid:** < 200 ms for navn-søk + filtrering over full treff-liste

**Løsning:** `pg_trgm` + GIN-indeks på `sok_tekst`, B-tree-indekser på filtreringsfelter og partiell indeks på aktive håndterer disse volumene.

### 2. Kandidatdata kopiert ved opprettelse

**Valg:** Kopier utvalgte felt fra kandidatsøkresultatet ved opprettelse. Feltene lagres i `jobbsoker_sok`.

**Felter som kopieres:**

- `innsatsgruppe` – for § 14 a-filtrering
- `fylke`, `kommune`, `poststed` – for geografisk filtrering og visning

**Felter som IKKE kopieres:**

- CV-data (kompetanse, yrkeserfaring, utdanning) – for detaljert, ikke relevant for listefiltring
- Prioriterte målgrupper – tas ikke med nå
- Hull i CV – tas ikke med nå
- Reisevei – ikke relevant for treff

**Begrunnelse:** Disse feltene er allerede tilgjengelige i frontend når kandidater velges via «Finn og legg til». Ingen ekstra API-kall er nødvendig; vi utvider bare opprettelses-DTO-en.

### 3. Paginering og sortering

**Valg:** Backend håndterer både paginering og sortering via query-params.

**Implementering:**

- Paginering: `side` (1-indeksert) + `antallPerSide` (default 20)
- Sortering: `sortering` med verdier `navn`, `invitert_dato`, `status`
- Backend returnerer `totalt` via egen `COUNT(*)`-query med samme filtre som resultatsøket

**Begrunnelse:** Samme mønster som stilling-søk. Mindre data over nettet, bedre for mobile.

---

## Vedlegg: Flow diagram – Søk og filtrering (MVP)

```
MVP – Én handler for alle jobbsøkere (med eller uten filtre)
──────────────────────────────────

Frontend uten filtre:
  GET /api/rekrutteringstreff/{treffId}/jobbsoker?side=1&antallPerSide=20
    ↓ (returnerer alle aktive jobbsøkere med paginering)

Frontend med filtre:
  GET /api/rekrutteringstreff/{treffId}/jobbsoker
    ?fritekst=Ola&status=INVITERT&side=1&antallPerSide=20
    ↓ (returnerer filtrert resultat med paginering)

Fnr-oppslag (eget endepunkt):
  POST /api/rekrutteringstreff/{treffId}/jobbsoker/sok
    body: { "fodselsnummer": "12345678901" }
    ↓ (returnerer match eller 404)

Begge søke-casene:
    ↓
SQL mot jobbsoker_sok-tabellen (parallell søketabell):
  SELECT COUNT(*) FROM jobbsoker_sok
  WHERE
    rekrutteringstreff_id = ?
    AND er_synlig = true
    AND status != 'SLETTET'
    AND (samme filtre som under)

  SELECT * FROM jobbsoker_sok
  WHERE
    rekrutteringstreff_id = ?
    AND er_synlig = true
    AND status != 'SLETTET'
    AND (sok_tekst ILIKE ? OR fritekst IS NULL)  -- bruker pg_trgm-indeks
    AND (status = ANY(?) OR status-liste IS NULL)
    AND (innsatsgruppe = ANY(?) OR innsatsgruppe-liste IS NULL)
    AND (fylke = ? OR fylke IS NULL)
    AND (kommune = ? OR kommune IS NULL)
    AND (navkontor = ? OR navkontor IS NULL)
    AND (veileder_navident = ? OR veileder IS NULL)
  ORDER BY (sortering)
  LIMIT antallPerSide OFFSET (side-1)*antallPerSide
    ↓
GIN- og B-tree-indekser håndterer WHERE-klausulene raskt
    ↓
JSON-respons med resultater + side-info
    ↓
Frontend viser resultater + paginering
```

---

## Relatert dokumentasjon

- [Utvidbarhet.md](../6-kvalitet/utvidbarhet.md) – søk-strategi generelt
- [Arbeidsgivers behov](arbeidsgivers-behov.md) – arbeidsgiverns behovs-filterkriterier
- [Database.md](../2-arkitektur/database.md) – tabell-struktur
- [Kandidatsøk-integrasjon](../4-integrasjoner/) – Arena/kandidatsøk-API

## Antagelser gjort

✅ **Backend:** Én handler – erstatter `hentJobbsøkereHandler()` – håndterer både "hent alle" og "søk med filtre"  
✅ **Frontend:** Én custom hook `useJobbsøkerSøk()` – håndterer begge casene (filtre valgfrie)  
✅ **Veileder og navkontor:** Allerede lagret ved innleggelse  
✅ **Database-strategi:** Parallell søketabell `jobbsoker_sok` – separert fra domenetabellen `jobbsoker`  
✅ **Kandidatdata:** `innsatsgruppe`, `fylke`, `kommune`, `poststed` kopieres fra kandidatsøk ved opprettelse  
✅ **Trigram-indeksert fritekst:** Generert kolonne `sok_tekst` med navn + poststed + kommune + fylke, brukt med `pg_trgm`  
✅ **Indekser:** GIN på `sok_tekst` via `gin_trgm_ops`, samt B-tree på status, innsatsgruppe, fylke, kommune, navkontor, veileder og invitert_dato  
✅ **OpenSearch:** Utelukket – synkron PostgreSQL-løsning  
✅ **Filtre:** Status, innsatsgruppe, fylke/kommune, navkontor, veileder  
✅ **Fødselsnummer:** Ikke i response, kun i request (POST body) for oppslag  
✅ **Fødselsnummer-oppslag:** Treff-skopet via `rekrutteringstreff_id + fodselsnummer`  
✅ **Veileder:** NULL-felter ignoreres i filtrering  
✅ **Paginering:** Backend-håndtert via `side` + `antallPerSide` – **påkrevd**  
✅ **Paginering og totalt:** Egen `COUNT(*)`-query – skjulte/slettede er aldri med i telling eller resultat  
✅ **Sortering:** `navn`, `invitert_dato`, `status`  
✅ **Admin:** admin = utvikler, samme tilgang  
✅ **Ytelsestest:** Følger `RekrutteringstreffSokYtelsestest`-mønster med 10K jobbsøkere, warmup 2s, målt 500ms  
✅ **Ikke inkludert:** Reisevei, vis slettede, prioriterte målgrupper, hull i CV, notater/intern status
