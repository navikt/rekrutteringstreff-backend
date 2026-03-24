# Plan: Søk og filtrering av jobbsøkere i rekrutteringstreff

**Status:** Plan  
**Omfang:** Backend API + frontend UI for søk og filtrering av jobbsøkere innad i et treff

Når eiere og markeder kontakter skal administrere et rekrutteringstreff med mange jobbsøkere (opp til 10 000), må de kunne søke og filtrere kandidatene for å finne relevante deltakere.

---

## Oversikt

Planen beskriver arkitektur for søk og filtrering av jobbsøkere innad i et treff. Én handler håndterer både "hent alle" og "søk med filtre":

1. **Filterkriterier** – hvilke feltdimensjoner som kan filtreres (valgfrie)
2. **API-kontrakt** – request/response DTOs med påkrevd paginering
3. **Database-strategi** – denormalisert status i jobbsoker
4. **Implementering** – oppgaver for MVP

---

## Filterkriterier

Basert på behovene fra arbeidsgiverens behov-plan og stilling sine jobsøkere-løsning, skal følgende kunne filtreres:

### Navn og identifikator

- **Fritekst (navn)**: Søk i `fornavn` + `etternavn` kombinert
- **Fødselsnummer**: Eksakt søk på `fodselsnummer`

### Jobbsøkers deltakerstatus

- **Status-filter**: `LAGT_TIL`, `INVITERT`, `SVART_JA`, `SVART_NEI`
- Lagret som denormalisert `status`-felt i `jobbsoker`-tabellen for enkel filtrering

### Veileder og kontor

- **Nav-kontor**: Filter på `navkontor` (lagret ved innleggelse)
- **Veileder**: Filter på `veileder_navident` eller `veileder_navn` (lagret ved innleggelse)

**Notat:** Disse feltene fylles inn ved opprettelse av jobbsøker og er allerede tilgjengelig i tabellen – ingen ekstra oppslag nødvendig.

---

## API-kontrakt

### Query-parametere (MVP)

**Påkrevd (paginering):**

- `side` (1-indeksert, integer)
- `sideStørrelse` (integer, default 20)

**Valgfrie (filtre):**

- `fritekst` – søk i navn
- `fodselsnummer` – eksakt søk
- `status` – kommaseparert liste (f.eks. `INVITERT,SVART_JA`)
- `navkontor` – filter på kontor
- `veileder` – filter på veileder (navident)
- `sortering` – `navn`, `invitert_dato`, eller `status` (default: `navn`)

**Eksempel uten filtre (hent alle):**

```
GET /api/rekrutteringstreff/{treffId}/jobbsoker?side=1&sideStørrelse=20
```

**Eksempel med filtre:**

```
GET /api/rekrutteringstreff/{treffId}/jobbsoker
  ?fritekst=Ola
  &status=INVITERT,SVART_JA
  &navkontor=0301
  &side=1
  &sideStørrelse=20
  &sortering=navn
```

### Response DTO (MVP)

```
{
  "totalt": 9996,                               // Totalt antall AKTIVE treff (skjulte/slettede ekskludert)
  "side": 1,                                    // Nåværende side (paginering)
  "sideStørrelse": 20,                          // Treff per side (garantert fulle sider)
  "jobbsØkere": [
    {
      "jobbsøkerId": "<uuid>",
      "personTreffId": "<uuid>",
      "fornavn": "Ola",
      "etternavn": "Nordmann",
      "fodselsnummer": "12345678901",
      "navkontor": "0301",
      "veilederNavn": "Per Pål",
      "veilederNavident": "B654321",
      "status": "INVITERT",
      "erSynlig": true
    }
  ]
}
```

**Notat:** `totalt` reflekterer kun aktive jobbsøkere (med `er_synlig = true`). Skjulte og slettede jobbsøkere telles ikke og vises ikke, hvilket sikrer fulle sider.

---

## Database-strategi: Denormalisert status i jobbsoker

**jobbsoker-tabell** inneholder nå:

- Grunnleggende personalia (`fornavn`, `etternavn`, `fodselsnummer`)
- Veileder-info (`navkontor`, `veileder_navn`, `veileder_navident`)
- **Status (`status` ENUM)** – denormalisert, oppdatert når hendelser oppstår
- Synlighet (`er_synlig` fra synlighetsmotor)

**MVP-strategi:** Søk direkte mot `jobbsoker`-tabellen – ingen view, ingen joins. Søket blir streitforward SQL med WHERE-klausuler.

**Indekser som trengs:**

1. **B-tree på navn** – `idx_jobbsoker_navn_search ON (LOWER(fornavn || ' ' || etternavn))`
2. **B-tree på fnr** – `idx_jobbsoker_fodselsnummer`
3. **B-tree på status** – `idx_jobbsoker_status`
4. **B-tree på navkontor** – `idx_jobbsoker_navkontor`
5. **B-tree på veileder** – `idx_jobbsoker_veileder_navident`

---

## Implementering

### MVP – Én handler, søk og filtrering direkte mot jobbsoker-tabell

**Database-valg:** Denormalisert `status` i `jobbsoker`, søk uten view eller joins.

**Oppgaver:**

- [ ] Flyway-migrasjon: Legg til `status` ENUM-kolonne i `jobbsoker` og opprett indekser
  - Alter table: `ALTER TABLE jobbsoker ADD COLUMN status VARCHAR(50)`
  - B-tree indekser:
    - `idx_jobbsoker_navn_search ON (LOWER(fornavn || ' ' || etternavn))`
    - `idx_jobbsoker_fodselsnummer ON (fodselsnummer)`
    - `idx_jobbsoker_status ON (status)`
    - `idx_jobbsoker_navkontor ON (navkontor)`
    - `idx_jobbsoker_veileder_navident ON (veileder_navident)`
- [ ] Oppdater hendelseslogikk: når `jobbsoker_hendelse` opprettes, oppdater `jobbsoker.status` atomisk
- [ ] Lag `JobbsøkerSøkResultat` DTO med paginering + sortering
- [ ] **Erstatt `hentJobbsøkereHandler()` med én unified handler** som:
  - Tar query-parametere: `side` (påkrevd), `sideStørrelse` (påkrevd), + valgfrie filtre
  - Hvis ingen filtre: returnerer alle aktive jobbsøkere (med paginering)
  - Hvis filtre: returnerer filtrert resultat (med paginering)
- [ ] Implementer `JobbsøkerRepository.sok()` med WHERE-klausuler:
  - `(fornavn || ' ' || etternavn) ILIKE ?` (navn-fritekst, valgfritt)
  - `fodselsnummer = ?` (fnr eksakt oppslag, valgfritt)
  - `status IN (?)` (status-liste, valgfritt)
  - `navkontor = ?` (kontor, valgfritt)
  - `veileder_navident = ?` (veileder, valgfritt)
  - **`er_synlig = true`** (filtrere bort skjulte jobbsøkere fra synlighetsmotor)
- [ ] Implementer paginering (LIMIT/OFFSET) og sortering
  - **Rekkefølge:** WHERE-filtrering → ORDER BY sortering → LIMIT/OFFSET paginering
  - Sikrer fulle sider: hver side har `sideStørrelse` aktive jobbsøkere (bortsett fra mulig siste side)
  - `totalt` = COUNT(\*) etter WHERE-filtrering, før paginering
  - Garanterer ingen "hull" eller tomme plasser på sidene fra skjulte/slettede
- [ ] **Backend-tester:**
  - Komponenttester: Full søk-flow mot Testcontainers-database inkl. autorisasjon
    - Test: Hent alle uten filtre (no-filter case)
    - Test: Søk med filtre (filtered case)
    - Test: Paginering kreves (skal feile uten side/sideStørrelse)
  - Service-tester: Filtrerings-logikk, paginering, sortering
  - Repository-tester: SQL-queries mot mock-tabell
- [ ] Frontend-UI: søkebar + filter-sidebar (lignende stilling)
  - **Én custom hook `useJobbsøkerSøk()`** som håndterer både "hent alle" og "søk med filtre"
    - Input: `{ treffId, side, sideStørrelse, filtre?: {...} }`
    - Output: `{ data, isLoading, error, mutate }`
    - Bruker SWR med query-params (kun filtre som er satt sendes med)
    - Eksempel uten filtre: `GET /api/rekrutteringstreff/{treffId}/jobbsoker?side=1&sideStørrelse=20`
    - Eksempel med filtre: `GET /api/rekrutteringstreff/{treffId}/jobbsoker?side=1&sideStørrelse=20&fritekst=Ola&status=INVITERT`
  - Dynamisk **MSW-mock-store** (som rekrutteringstreff-søk) for rask lokal utvikling
  - Paginering-komponenter og sortering-dropdown
  - Responsiv design for mobile/tablet/desktop
- [ ] **Frontend-tester:**
  - Playwright e2e-tester: Søk, filtrering, paginering, sortering
  - Responsivitet og UX-validering

**Navn-søk ytelsestesting:** Bekreft at navn-søk over 10K jobbsøkere tar < 200 ms med B-tree indeks.

---

## Design og UI-referanser

Søk- og filtreringsgrenselaget er inspirert av **stilling-siden sin kandidatliste**. Vi gjenbruker etablert UX-pattern:

- **Søkebar** (fritekst) topps, med quick-filter-pills under (Status, Kontor, Veileder)
- **Paginering** med side-info og resultat-tall (lignende stilling sin pagineringsstil)
- **Sortering-dropdown** med aktiv sortering (navn, invitert_dato, status)
- **Resultat-liste** med denormalisert datavisning (navn, kontakt, status, etc.)

Dette sikrer konsistens på tvers av Nav-plattformen og reduserer design-arbeid.

---

## Testing-strategi

### Backend (Kotlin/Javalin)

- **Komponenttester** (primært): Full Testcontainers-oppsett med ekte PostgreSQL og HTTP-kall
  - Tester søk mot oppfylt `jobbsoker`-tabell med faktisk status
  - Validerer paginering, sortering, auth
  - Bekreft hver indeks fungerer som forventet
- **Service-tester**: Isolert filtrerings-logikk (boundary-testing av WHERE-klausuler)
- **Repository-tester**: SQL-queries mot mock-data

### Frontend (Next.js/TypeScript)

- **Playwright e2e-tester**: Søk-flow, filtrering, paginering, sortering i ekte browser
  - Responsivitets-testing (mobile breakpoints)
  - Validerer MSW-mock-respons og UI-rendering
- **Vitest/React testing library**: Komponent-unit-tester (filter-pills, sortering-dropdown, etc.)

---

## Autorisasjon og synlighet

- **Søk innad i treff:** Kun tilgjengelig for **eiere** og **utvikler**
- **Arbeidsgiver-brukere:** Får varsler om kandidater via separat mekanisme, ikke direkte søk
- **Jobbsøker-bruker:** Ser sitt eget treff-svar, ikke søkbar liste

---

## Avklaringer og åpne spørsmål

### 1. Paginering med skjulte/slettede jobbsøkere

**Valg:** Skjulte og slettede jobbsøkere filtreres ut FØR paginering, ikke etter.

**Implementering:**

```sql
SELECT * FROM jobbsoker
WHERE er_synlig = true
  AND (øvrige filtre)
ORDER BY (sortering)
LIMIT 20 OFFSET (side-1)*20
```

**Konsekvenser:**

- `totalt` = antall aktive jobbsøkere kun (ikke inkludert skjulte/slettede)
- Hver side garanteres fulle `sideStørrelse` rader (bortsett fra potensielt siste side)
- Ingen "hull" eller tomme plasser fra skjulte/slettede
- Brukeren opplever konsistent paginering

**Eksempel:** Hvis 10 000 jobbsøkere totalt, 4 skjulte/slettede → totalt = 9996, side 1 = 20 aktive.

### 2. Volume og akseptabel ytelse

- **Antall treff i systemet:** ~1M (ikke søkt alle samtidig)
- **Jobbsøkere per treff:** Opp til 10 000 (søkes innad ett treff av gangen)
- **Akseptabel søketid:** < 200 ms for navn-søk + filtrering over full treff-liste

**Løsning:** B-tree indekser på `jobbsoker`-tabellen håndterer disse volumene uten view-overhead. Enkel, direkte SQL.

### 2. MVP-fokus – kun navn/status, ikke kandidatdata

**Valg:** Dropp Arena-kandidatdata fra MVP. Start med navn/fnr/status-filtrering.

**Begrunnelse:**

- Enklest design – ingen ekstra tabellkolonner, ingen Arena-integrasjon
- B-tree indekser gir tilstrekkelig ytelse for navn/status-søk
- Minimalt kompleksitet – denormalisert status håndteres per hendelse
- Lett å vedlikeholde og utforsker brukerdom før kommende utvidelser

### 3. Paginering og sortering

**Valg:** Backend håndterer både paginering og sortering via query-params.

**Implementering:**

- Paginering: `side` (1-indeksert) + `sideStørrelse` (default 20)
- Sortering: `sortering` med verdier `navn`, `invitert_dato`, `status`
- Backend returnerer `totalt` (antall rader uten paginering) for frontend-UI

**Begrunnelse:** Samme mønster som stilling-søk. Mindre data over nettet, bedre for mobile.

---

## Vedlegg: Flow diagram – Søk og filtrering (MVP)

```
MVP – Én handler for alle jobbsøkere (med eller uten filtre)
──────────────────────────────────

Frontend uten filtre:
  GET /api/rekrutteringstreff/{treffId}/jobbsoker?side=1&sideStørrelse=20
    ↓ (returnerer alle aktive jobbsøkere med paginering)

Frontend med filtre:
  GET /api/rekrutteringstreff/{treffId}/jobbsoker
    ?fritekst=Ola&status=INVITERT&side=1&sideStørrelse=20
    ↓ (returnerer filtrert resultat med paginering)

Begge casene:
    ↓
SQL mot jobbsoker-tabellen direkte (ingen view/join):
  WHERE
    AND (LOWER(fornavn || ' ' || etternavn) ILIKE ? OR fritekst IS NULL)
    AND (fodselsnummer = ? OR fodselsnummer IS NULL)
    AND (status = ANY(?) OR status-liste IS NULL)
    AND (navkontor = ? OR navkontor IS NULL)
    AND (veileder_navident = ? OR veileder_navident IS NULL)
    AND er_synlig = true
  ORDER BY (sortering)
  LIMIT sideStørrelse OFFSET (side-1)*sideStørrelse
    ↓
B-tree indekser håndterer hver WHERE-klausul raskt
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

## Avgjørelser gjort

✅ **Backend:** Én handler – erstatter `hentJobbsøkereHandler()` – håndterer både "hent alle" og "søk med filtre"  
✅ **Frontend:** Én custom hook `useJobbsøkerSøk()` – håndterer begge casene (filtre valgfrie)  
✅ **Veileder og navkontor:** Allerede lagret ved innleggelse  
✅ **Database-strategi:** Denormalisert `status` i `jobbsoker`-tabell, direkte søk (ingen view)  
✅ **Indekser:** B-tree på navn, fnr, status, navkontor, veileder_navident  
✅ **OpenSearch:** Utelukket – synkron PostgreSQL-løsning  
✅ **MVP-fokus:** Navn / fnr / status / kontor / veileder-filtrering  
✅ **Paginering:** Backend-håndtert via `side` + `sideStørrelse` – **påkrevd**  
✅ **Filtre:** Alle søk-parametere valgfrie (ingen filtre = hent alle)  
✅ **Sortering:** `navn`, `invitert_dato`, `status`  
✅ **Skjulte/slettede:** Filtreres ut før paginering – fulle sider garantert, totalt reflekterer kun aktive
