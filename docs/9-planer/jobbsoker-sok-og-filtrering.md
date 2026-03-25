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
- **Fødselsnummer**: Eksakt oppslag via POST-body (ikke query-param, av sikkerhetshensyn – samme mønster som stilling sitt kandidatsøk)

### Jobbsøkers deltakerstatus

- **Status-filter**: `LAGT_TIL`, `INVITERT`, `SVART_JA`, `SVART_NEI`
- Lagret som denormalisert `status`-felt i `jobbsoker`-tabellen for enkel filtrering

### Veileder og kontor

- **Nav-kontor**: Filter på `navkontor` (lagret ved innleggelse)
- **Veileder**: Filter på `veileder_navident` eller `veileder_navn` (lagret ved innleggelse)

**Notat:** Disse feltene fylles inn ved opprettelse av jobbsøker og er allerede tilgjengelig i tabellen – ingen ekstra oppslag nødvendig. Dersom veileder-felter er NULL ignoreres de i filtrering (jobbsøkeren vises ikke for veileder-filter, men vises ellers).

---

## API-kontrakt

### Query-parametere (MVP)

**Påkrevd (paginering):**

- `side` (1-indeksert, integer)
- `sideStørrelse` (integer, default 20)

**Valgfrie (filtre):**

- `fritekst` – søk i navn
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

**Eksempel fødselsnummer-oppslag (POST):**

```
POST /api/rekrutteringstreff/{treffId}/jobbsoker/sok
Content-Type: application/json

{ "fodselsnummer": "12345678901" }
```

Fødselsnummer sendes i body, ikke som query-param – samme mønster som `rekrutteringsbistand-kandidatsok-api` bruker for `/kandidatsok/navn` og `/kandidatsok/arena-kandidatnr`.

### Response DTO (MVP)

```
{
  "totalt": 9996,                               // Totalt antall AKTIVE treff (skjulte/slettede ekskludert)
  "side": 1,                                    // Nåværende side (paginering)
  "sideStørrelse": 20,                          // Treff per side (garantert fulle sider)
  "jobbsøkere": [
    {
      "jobbsøkerId": "<uuid>",
      "personTreffId": "<uuid>",
      "fornavn": "Ola",
      "etternavn": "Nordmann",
      "navkontor": "0301",
      "veilederNavn": "Per Pål",
      "veilederNavident": "B654321",
      "status": "INVITERT",
      "invitertDato": "2026-03-20T10:00:00Z",
      "erSynlig": true
    }
  ]
}
```

**Notat:** `totalt` reflekterer kun aktive jobbsøkere (med `er_synlig = true` og `status != 'SLETTET'`). Skjulte og slettede jobbsøkere telles ikke og vises ikke, hvilket sikrer fulle sider.

**Notat:** `fodselsnummer` er bevisst utelatt fra response. Fødselsnummer er kun relevant som input-filter (oppslag), ikke som visningsdata.

---

## Database-strategi: Denormaliserte søkefelter i jobbsoker

**jobbsoker-tabell** inneholder nå:

- Grunnleggende personalia (`fornavn`, `etternavn`, `fodselsnummer`)
- Veileder-info (`navkontor`, `veileder_navn`, `veileder_navident`)
- **Status (`status`)** – denormalisert, oppdatert når hendelser oppstår
- **Invitert-dato (`invitert_dato`)** – denormalisert, settes når hendelsestype = `INVITERT`
- Synlighet (`er_synlig` fra synlighetsmotor)

### Retning: Flat søketabell

Vi denormaliserer felter fra `jobbsoker_hendelse` inn i `jobbsoker`-tabellen for å unngå joins i søk. Hvert felt som er relevant for filtrering eller sortering bør ligge direkte på `jobbsoker`. Hendelsestabellen forblir kilden til sannhet, men `jobbsoker`-raden oppdateres atomisk når hendelser oppstår.

Dette er et bevisst valg: `jobbsoker` fungerer som en flat søketabell, mens `jobbsoker_hendelse` er audit-loggen. Dersom flere felter fra hendelser blir relevante for søk i fremtiden, denormaliserer vi dem inn etter samme mønster.

**MVP-strategi:** Søk direkte mot `jobbsoker`-tabellen – ingen view, ingen joins. Søket blir rett-frem SQL med WHERE-klausuler.

**Indekser som trengs:**

1. **B-tree på navn** – `idx_jobbsoker_navn_search ON (LOWER(fornavn || ' ' || etternavn))`
2. **B-tree på fnr** – `idx_jobbsoker_fodselsnummer` (eksisterer allerede)
3. **B-tree på status** – `idx_jobbsoker_status`
4. **B-tree på navkontor** – `idx_jobbsoker_navkontor`
5. **B-tree på veileder** – `idx_jobbsoker_veileder_navident`
6. **B-tree på invitert_dato** – `idx_jobbsoker_invitert_dato`

---

## Implementering

### MVP – Én handler, søk og filtrering direkte mot jobbsoker-tabell

**Database-valg:** Denormalisert `status` i `jobbsoker`, søk uten view eller joins.

**Oppgaver:**

- [ ] Flyway-migrasjon: Legg til `invitert_dato`-kolonne og søkeindekser
  - Alter table: `ALTER TABLE jobbsoker ADD COLUMN invitert_dato TIMESTAMPTZ`
  - Backfill: `UPDATE jobbsoker SET invitert_dato = (SELECT tidspunkt FROM jobbsoker_hendelse WHERE hendelsestype = 'INVITERT' AND jobbsoker_hendelse.jobbsoker_id = jobbsoker.jobbsoker_id ORDER BY tidspunkt LIMIT 1)`
  - B-tree indekser:
    - `idx_jobbsoker_navn_search ON (LOWER(fornavn || ' ' || etternavn))`
    - `idx_jobbsoker_status ON (status)`
    - `idx_jobbsoker_navkontor ON (navkontor)`
    - `idx_jobbsoker_veileder_navident ON (veileder_navident)`
    - `idx_jobbsoker_invitert_dato ON (invitert_dato)`
  - Merk: `idx_jobbsoker_fodselsnummer` eksisterer allerede
- [ ] Oppdater hendelseslogikk: når `jobbsoker_hendelse` opprettes, oppdater `jobbsoker.status` og `jobbsoker.invitert_dato` atomisk
- [ ] Lag `JobbsøkerSøkResultat` DTO med paginering + sortering
- [ ] **Erstatt `hentJobbsøkereHandler()` med én unified handler** som:
  - Tar query-parametere: `side` (påkrevd), `sideStørrelse` (påkrevd), + valgfrie filtre
  - Hvis ingen filtre: returnerer alle aktive jobbsøkere (med paginering)
  - Hvis filtre: returnerer filtrert resultat (med paginering)
- [ ] Implementer `JobbsøkerRepository.sok()` med WHERE-klausuler:
  - `(fornavn || ' ' || etternavn) ILIKE ?` (navn-fritekst, valgfritt)
  - `status IN (?)` (status-liste, valgfritt)
  - `navkontor = ?` (kontor, valgfritt)
  - `veileder_navident = ?` (veileder, valgfritt)
  - **`er_synlig = true`** (filtrere bort skjulte jobbsøkere)
  - **`status != 'SLETTET'`** (filtrere bort slettede jobbsøkere)
- [ ] Implementer `JobbsøkerRepository.hentViaFodselsnummer()` – eget POST-endepunkt
  - `fodselsnummer = ?` (eksakt oppslag, sendt i request body, ikke query-param)
- [ ] Implementer paginering (LIMIT/OFFSET) og sortering
  - **Rekkefølge:** WHERE-filtrering (inkl. `er_synlig = true AND status != 'SLETTET'`) → ORDER BY sortering → LIMIT/OFFSET paginering
  - `totalt` = COUNT(\*) med WHERE `er_synlig = true AND status != 'SLETTET'` (+ eventuelle filtre), utført i samme query
  - Skjulte og slettede er _aldri_ med i hverken telling eller resultatsett
  - Garanterer fulle sider uten hull
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

### Ytelsestesting

Følger samme mønster som `RekrutteringstreffSokYtelsestest` – egne terskelkonstanter, seed-data, warmup + målt kall:

- **Seed:** Opprett ett treff med 10 000 jobbsøkere (ulike statuser, navkontorer, veiledere)
- **Warmup-kall:** Hent alle uten filtre, terskel 2 000 ms
- **Målt kall:** Søk med navn-fritekst + status-filter + sortering, terskel 500 ms
- **Sorteringskall:** Sorter på `invitert_dato` (annen sortering enn naturlig rekkefølge), terskel 500 ms

Ytelsen måles ende-til-ende (SQL-query inkl. indeksbruk), ikke bare DB-tid. Med B-tree indekser og 10K rader bør dette holde komfortabelt.

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

- **Søk innad i treff:** Kun tilgjengelig for **eiere** og **utvikler** (admin = utvikler, samme tilgang)
- **Arbeidsgiver-brukere:** Får varsler om kandidater via separat mekanisme, ikke direkte søk
- **Jobbsøker-bruker:** Ser sitt eget treff-svar, ikke søkbar liste

---

## Avklaringer og åpne spørsmål

### 1. Paginering med skjulte/slettede jobbsøkere

**Valg:** Skjulte og slettede jobbsøkere filtreres ut FØR paginering, ikke etter. De er aldri med i hverken resultat eller telling.

**Implementering:**

```sql
SELECT *, COUNT(*) OVER() AS totalt
FROM jobbsoker
WHERE er_synlig = true
  AND status != 'SLETTET'
  AND (øvrige filtre)
ORDER BY (sortering)
LIMIT 20 OFFSET (side-1)*20
```

**Konsekvenser:**

- `totalt` = `COUNT(*) OVER()` – antall rader som matcher WHERE-klausulen (uten LIMIT/OFFSET)
- Skjulte (`er_synlig = false`) og slettede (`status = 'SLETTET'`) er _ekskludert_ fra alt
- Hver side garanteres fulle `sideStørrelse` rader (bortsett fra potensielt siste side)
- Brukeren opplever konsistent paginering

**Eksempel:** Hvis 10 000 jobbsøkere totalt, 4 skjulte/slettede → totalt = 9996, side 1 = 20 aktive.

### 2. Volume og akseptabel ytelse

- **Antall treff i systemet:** ~1M (ikke søkt alle samtidig)
- **Jobbsøkere per treff:** Opp til 10 000 (søkes innad ett treff av gangen)
- **Akseptabel søketid:** < 200 ms for navn-søk + filtrering over full treff-liste

**Løsning:** B-tree indekser på `jobbsoker`-tabellen håndterer disse volumene uten view-overhead. Enkel, direkte SQL.

### 2. MVP-fokus – kun navn/status, ikke kandidatdata

**Valg:** Dropp Arena-kandidatdata fra MVP. Start med navn/status-filtrering + fnr-oppslag via POST.

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
- Backend returnerer `totalt` via `COUNT(*) OVER()` i samme query – kun aktive jobbsøkere (ikke skjulte/slettede)

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

Fnr-oppslag (eget endepunkt):
  POST /api/rekrutteringstreff/{treffId}/jobbsoker/sok
    body: { "fodselsnummer": "12345678901" }
    ↓ (returnerer match eller 404)

Begge søke-casene:
    ↓
SQL mot jobbsoker-tabellen direkte (flat søketabell, ingen view/join):
  SELECT *, COUNT(*) OVER() AS totalt FROM jobbsoker
  WHERE
    er_synlig = true
    AND status != 'SLETTET'
    AND (LOWER(fornavn || ' ' || etternavn) ILIKE ? OR fritekst IS NULL)
    AND (status = ANY(?) OR status-liste IS NULL)
    AND (navkontor = ? OR navkontor IS NULL)
    AND (veileder_navident = ? OR veileder_navident IS NULL)
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
✅ **Database-strategi:** Flat søketabell – `jobbsoker` med denormalisert `status` og `invitert_dato`, direkte søk (ingen view/join)  
✅ **Indekser:** B-tree på navn, fnr (eksisterer), status, navkontor, veileder_navident, invitert_dato  
✅ **OpenSearch:** Utelukket – synkron PostgreSQL-løsning  
✅ **MVP-fokus:** Navn / status / kontor / veileder-filtrering + fnr-oppslag via POST  
✅ **Fødselsnummer:** Ikke i response, kun i request (POST body) for oppslag  
✅ **Veileder:** NULL-felter ignoreres i filtrering – jobbsøker uten veileder vises i alle-søk, ikke i veileder-filter  
✅ **Paginering:** Backend-håndtert via `side` + `sideStørrelse` – **påkrevd**  
✅ **Paginering og totalt:** `COUNT(*) OVER()` – skjulte/slettede er aldri med i telling eller resultat  
✅ **Filtre:** Alle søk-parametere valgfrie (ingen filtre = hent alle)  
✅ **Sortering:** `navn`, `invitert_dato`, `status`  
✅ **Admin:** admin = utvikler, samme tilgang  
✅ **Ytelsestest:** Følger `RekrutteringstreffSokYtelsestest`-mønster med 10K jobbsøkere, warmup 2s, målt 500ms
