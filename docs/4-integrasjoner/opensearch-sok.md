# OpenSearch-søk for rekrutteringstreff

## Bakgrunn og motivasjon

I dag henter frontend **alle** rekrutteringstreff fra backend (`GET /api/rekrutteringstreff`) og gjør filtrering, fritekst-søk og sortering i klienten. Dette skalerer ikke. Målet er å flytte all søke- og filtreringslogikk til OpenSearch, og dele implementasjonen på fire ansvarsområder.

## Arkitekturoversikt

```
┌──────────────┐       ┌──────────────────────┐       ┌───────────────┐
│   Frontend   │──────▶│ rekrutteringstreff-  │──────▶│  OpenSearch   │
│  (Next.js)   │ POST  │  søk (ny app)        │ query │  (Aiven)      │
│              │◀──────│                      │◀──────│               │
└──────────────┘       └──────────────────────┘       └───────────────┘
                                                            ▲
                                                            │ indekserer
                                               ┌────────────┴────────────┐
                                               │  rekrutteringstreff-    │
                                               │  indekser (ny app)      │
                                               └────────────┬────────────┘
                                                            │ lytter på Rapids
                                               ┌────────────┴────────────┐
                                               │  rekrutteringstreff-api │
                                               │  (hendelser + outbox)   │
                                               └─────────────────────────┘
```

| Komponent                                 | Ansvar                                                |
| ----------------------------------------- | ----------------------------------------------------- |
| **Frontend**                              | Sender søkeparametere, viser paginerte resultater     |
| **rekrutteringstreff-søk** (ny app)       | Søke-endepunkt, bygger OpenSearch-spørringer          |
| **rekrutteringstreff-indekser** (ny app)  | Lytter på Rapids-meldinger, indekserer til OpenSearch |
| **rekrutteringstreff-api** (eksisterende) | Publiserer treff-hendelser via outbox til Rapids      |

---

## Del 1: Frontend – søkeformat

> **Figma-design:** [Rekrutteringstreff – liste og søk](https://www.figma.com/design/g0uypsepFJoFx3RRgtaw55/Team-ToI---Rekrutteringsbistand-og-Rekrutteringstreff?node-id=1-14565&p=f&m=dev) (krever NAV-tilgang)

### Konseptskisse – søk og filtrering

```
┌─────────────────────────────────────────────────────────────────────────┐
│  [Søk i rekrutteringstreff 🔍]                                        │
├─────────────────────┬───────────────────────────────────────────────────┤
│                     │  Aktive filter-chips:                            │
│  Sorter (radio)     │  [Oslo ✕] [Status: Åpen ✕]  [Fjern alle filtre]  │
│  ○ Sist oppdaterte  │                                                  │
│  ○ Nyeste           │  Tabs (radio):  [ Alle | Mine | Mitt kontor ]    │
│  ○ Eldste           │                                                  │
│  ○ Aktive           │  ┌──────────────────────────────────────────────┐ │
│  ○ Fullførte        │  │ Rekrutteringstreff for nyutdannede ...       │ │
│                     │  │ 📅 24. mai 2026, kl 12:00    Åpen for søkere│ │
│  Steder (checkbox)  │  │ 📍 Ravinevegen 11  ⏰ Frist om 24 dager     │ │
│  ☐ Agder (100)      │  │ 👤 Mitt oppdrag  Publisert for 2 dager ...  │ │
│  ☐ Akershus (100)   │  └──────────────────────────────────────────────┘ │
│  ☐ Buskerud (100)   │  ┌──────────────────────────────────────────────┐ │
│                     │  │ Rekrutteringstreff for nyutdannede ...       │ │
│  Status (checkbox)  │  │ 📅 24. mai 2026                              │ │
│  ☐ Åpen for søkere  │  │ Eies av Benjamin Hansen                     │ │
│  ☐ Stengt for søkere│  └──────────────────────────────────────────────┘ │
│  ☐ Utløpt           │                                                  │
│  ☐ Ikke publiserte  │                        1-100 av 4000   < >       │
│  🔘 Vis avlyste(200)│                                                  │
│                     │                                                  │
│  Kontor (checkbox)  │                                                  │
│  ☐ Agder (100)      │                                                  │
│  ☐ Akershus (100)   │                                                  │
└─────────────────────┴───────────────────────────────────────────────────┘
```

### Interaksjonsmønstre

| UI-element            | Type                   | Oppførsel                                                                  |
| --------------------- | ---------------------- | -------------------------------------------------------------------------- |
| **Sorter**            | Radioknapper (én av)   | Kun én aktiv sortering om gangen                                           |
| **Alle/Mine/Mitt k.** | Tabs (én av)           | Gjensidig utelukkende – fungerer som radioknapper, men rendret som tabs    |
| **Steder**            | Sjekkbokser (flervalg) | Flere fylker/kommuner kan velges samtidig                                  |
| **Status**            | Sjekkbokser (flervalg) | Flere visningsstatuser kan velges samtidig                                 |
| **Vis avlyste**       | Toggle/switch (av/på)  | Uavhengig av alt annet – kan kombineres fritt med tabs, statuser og steder |
| **Kontor**            | Sjekkbokser (flervalg) | Flere kontorer kan velges samtidig                                         |
| **Fritekst**          | Tekstfelt              | Kombineres fritt med alle andre filtre                                     |

### Kombinasjon av filtre

Alle filtergrupper kan brukes **samtidig**. Requestobjektet sender hele tilstanden i hver forespørsel, og backend bygger én samlet OpenSearch-query. Eksempler på gyldige kombinasjoner:

- Tab «Mine» + kommune «Oslo» + visningsstatus «Åpen for søkere»
- Tab «Mitt kontor» + fritekst «barnehage» + fylke «Vestland» + sortering «Nyeste»
- Tab «Alle» + status «Stengt for søkere» + status «Utløpt» + toggle «Vis avlyste» på

I OpenSearch-queryen legges filtergruppene som separate `filter`-clauses i en `bool`-query. Flere valg innad i én gruppe (f.eks. to fylker) kombineres med `OR` (`terms`), mens grupper seg imellom kombineres med `AND` (separate `filter`-clauses).

Fritekst-feltet søker på tvers av tittel, beskrivelse, innleggsinnhold og arbeidsgivernavn – ikke et eget arbeidsgiver-søkefelt.

Søkeformatet modelleres etter mønsteret fra `rekrutteringsbistand-kandidatsok-api` i NAV sitt repo.

### Request

```kotlin
data class RekrutteringstreffSøkRequest(
    val fritekst: String? = null,
    val visningsstatuser: List<Visningsstatus>? = null,
    val fylkesnummer: List<String>? = null,
    val kommunenummer: List<String>? = null,
    val navkontorEnhetIder: List<String>? = null,
    val visAvlyste: Boolean = false,
    val visning: Visning = Visning.ALLE,
    val sortering: Sortering = Sortering.SIST_OPPDATERTE,
    val side: Int = 0,
    val antallPerSide: Int = 20,
)

enum class Visning {
    ALLE,           // ingen ekstra filter
    MINE,           // eiere inneholder innlogget navident
    MITT_KONTOR,    // navkontorEnhetId = innlogget kontor
}

enum class Sortering {
    RELEVANS,           // _score – default når fritekst er satt
    SIST_OPPDATERTE,    // sistEndret desc – default uten fritekst
    NYESTE,             // opprettetAvTidspunkt desc
    ELDSTE,             // opprettetAvTidspunkt asc
    AKTIVE,             // fraTid asc, kun treff med status PUBLISERT og fraTid i fremtiden
    FULLFØRTE,          // tilTid desc, kun FULLFØRT
}
```

### Respons

```kotlin
data class RekrutteringstreffSøkRespons(
    val treff: List<RekrutteringstreffSøkTreff>,
    val totaltAntall: Long,
    val side: Int,
    val antallPerSide: Int,
    val aggregeringer: RekrutteringstreffAggregeringer,
)

// Kan brukes til blant annet visning av antall i filtrene
data class RekrutteringstreffAggregeringer(
    val fylkesnummer: List<FilterValg>,
    val visningsstatuser: List<FilterValg>,
    val navkontorEnhetIder: List<FilterValg>,
)

data class FilterValg(
    val verdi: String,
    val antall: Long,
)

data class RekrutteringstreffSøkTreff(
    val id: UUID,
    val tittel: String,
    val status: RekrutteringstreffStatus,
    val fraTid: ZonedDateTime?,
    val tilTid: ZonedDateTime?,
    val svarfrist: ZonedDateTime?,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val kommunenavn: String?,
    val fylkesnavn: String?,
    val opprettetAvNavident: String,
    val opprettetAvNavn: String?,
    val navkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
    val sistEndret: ZonedDateTime,
    val antallArbeidsgivere: Int,
    val antallJobbsøkere: Int,
    val eiere: List<String>,
)
```

### Visningsstatus (brukervendt vs. backend)

Frontend opererer med **visningsstatuser** som er avledet fra backend-status + tidsverdier. Søke-appen oversetter disse til OpenSearch-queries:

```kotlin
enum class Visningsstatus {
    ÅPEN_FOR_SØKERE,       // backend: PUBLISERT + svarfrist ikke passert
    STENGT_FOR_SØKERE,     // backend: PUBLISERT + svarfrist passert, men tilTid ikke passert
    UTLØPT,                // backend: PUBLISERT + tilTid passert (aldri manuelt fullført)
    IKKE_PUBLISERTE,       // backend: UTKAST (kun arbeidsgiverrettet/utvikler)
}
```

| Visningsstatus    | Backend-status | Tidsfilter                                 |
| ----------------- | -------------- | ------------------------------------------ |
| ÅPEN_FOR_SØKERE   | `PUBLISERT`    | `svarfrist >= now` (eller svarfrist null)  |
| STENGT_FOR_SØKERE | `PUBLISERT`    | `svarfrist < now` AND `tilTid >= now`      |
| UTLØPT            | `PUBLISERT`    | `tilTid < now`                             |
| IKKE_PUBLISERTE   | `UTKAST`       | Kun synlig for arbeidsgiverrettet/utvikler |

`visAvlyste`-flagget (toggle, default av) legger til `AVLYST` i filteret.

Merk: Backend-status `FULLFØRT` og `SLETTET` er ikke eksponert som eget filter i UI.
`FULLFØRT` vises via sorteringen `FULLFØRTE`. `SLETTET` filtreres alltid bort.

### Endepunkt

```
POST /api/rekrutteringstreff/sok
```

---

## Del 2: Hendelse-publisering og outbox i `rekrutteringstreff-api`

`rekrutteringstreff-api` publiserer én Rapids-melding per treff-hendelse (opprett, oppdater, slett, publiser, avlys, fullfør). Meldingens `@event_name` identifiserer hendelsestypen.

### Mønster (etter jobbsøker-hendelse + aktivitetskort_polling)

Dette er **ikke** en tradisjonell outbox (der payload skrives til en dedikert outbox-tabell i samme transaksjon og slettes/markeres etter sending). Vi bruker det samme **kvitteringsbordet-mønsteret** som `aktivitetskort_polling`:

| Tabell                    | Rolle                                                                                       |
| ------------------------- | ------------------------------------------------------------------------------------------- |
| `treff_hendelse`          | Domenehendelsene (finnes allerede, skrives transaksjonelt med domeneendringen)              |
| `treff_rapids_kvittering` | Kvitteringstabell – tilstedeværelse av en rad betyr «sendt», fravær betyr «ikke sendt ennå» |

**Flyt:**

1. Service skriver til `treff_hendelse` som i dag (samme transaksjon som domeneendringen)
2. En scheduler (med leader election) kjører periodisk og finner usendte rader ved LEFT JOIN + `WHERE kvittering IS NULL`
3. For hver usent hendelse: send Rapids-melding, deretter INSERT kvitteringsrad

Tabellen `treff_rapids_kvittering` har følgende kolonner:

| Kolonne                      | Type                     | Beskrivelse                              |
| ---------------------------- | ------------------------ | ---------------------------------------- |
| `treff_rapids_kvittering_id` | `bigserial` (PK)         | Primærnøkkel                             |
| `treff_hendelse_id`          | `bigint` (FK, NOT NULL)  | Fremmednøkkel til `treff_hendelse`       |
| `sendt_tidspunkt`            | `timestamptz` (NOT NULL) | Tidspunkt meldingen ble sendt til Rapids |

Usendte rader finnes ved LEFT JOIN mot kvitteringstabellen.

**Viktig forskjell fra tradisjonell outbox:** Kvitteringsraden skrives _etter_ sending, ikke i samme transaksjon som domeneendringen. Det betyr at hvis appen krasjer mellom sending og kvitteringsskriving, kan meldingen sendes to ganger. Indekseren må derfor være **idempotent**.

Idempotensen sikres ved at indekseren bruker OpenSearch **upsert** (index med eksplisitt dokument-ID = treffets UUID). Å indeksere samme dokument to ganger med samme data gir nøyaktig samme resultat. Dette er enklere enn i f.eks. kandidatvarsel-api (som bruker en database med meldingsid-sjekk for å hindre duplikatsending), fordi OpenSearch sin index-operasjon er naturlig idempotent – den overskriver dokumentet med samme ID uten sideeffekter.

Meldingen inneholder nok data til at indekseren kan bygge fullt dokument uten eget DB-oppslag (denormalisert payload).

---

## Del 3: Indekser-app (`rekrutteringstreff-indekser`)

Ny app under `rekrutteringstreff-backend/apps/rekrutteringstreff-indekser/`. Følger samme mønster som `toi-stilling-indekser` i `toi-rapids-and-rivers`.

### Ansvar

- Lytter på Rapids-meldinger fra `rekrutteringstreff-api`
- Indekserer/oppdaterer/sletter dokument i OpenSearch basert på `@event_name`
- Støtter full reindeksering via `TreffApiClient` (poll alle treff fra API ved ny indeksversjon)
- Alias-bytte for zero-downtime reindeksering

### Dokument som indekseres

```json
{
  "id": "uuid",
  "tittel": "Treff for jobbsøkere i Oslo",
  "beskrivelse": "Kom og møt arbeidsgivere ...",
  "status": "PUBLISERT",
  "fraTid": "2026-03-31T08:00:00+02:00",
  "tilTid": "2026-03-31T09:00:00+02:00",
  "svarfrist": "2026-03-29T23:59:59+02:00",
  "gateadresse": "Osloveien 2",
  "postnummer": "0301",
  "poststed": "Oslo",
  "kommunenummer": "0301",
  "kommunenavn": "Oslo",
  "fylkesnummer": "03",
  "fylkesnavn": "Oslo",
  "opprettetAvNavident": "Z993102",
  "opprettetAvNavn": "Benjamin Hansen",
  "navkontorEnhetId": "0318",
  "opprettetAvTidspunkt": "2026-03-02T10:00:00+01:00",
  "sistEndret": "2026-03-02T12:00:00+01:00",
  "eiere": ["Z993102", "Z990659"],
  "arbeidsgivere": [{ "orgnr": "912345678", "orgnavn": "Eksempel AS" }],
  "antallArbeidsgivere": 1,
  "antallJobbsøkere": 4,
  "innlegg": [
    { "tittel": "Velkommen", "tekstinnhold": "Ren tekst, for fritekst-søk" }
  ]
}
```

### Forslag til appstruktur

```
apps/rekrutteringstreff-indekser/
├── build.gradle.kts
├── Dockerfile
├── nais.yaml / nais-dev.yaml / nais-prod.yaml
├── opensearch.yaml
└── src/main/
    ├── resources/
    │   ├── treff-mapping.json       ← norsk analyzer, nested for arbeidsgivere/innlegg
    │   └── treff-settings.json
    └── kotlin/no/nav/toi/rekrutteringstreff/indekser/
        ├── Application.kt
        ├── OpenSearchConfig.kt
        ├── IndexClient.kt
        ├── OpenSearchService.kt       ← indeks-livssyklus, alias-bytte
        ├── TreffDokument.kt
        ├── IndekserTreffLytter.kt     ← Rapids-lytter
        ├── TreffApiClient.kt          ← for full reindeksering
        └── Liveness.kt
```

---

## Del 4: Søke-app (`rekrutteringstreff-søk`)

Ny, separat app under `rekrutteringstreff-backend/apps/rekrutteringstreff-sok/` som eksponerer søke-endepunktet.
Dette gjør at vi kan skalere lesning uavhengig av APIet for skriving.

### Query builder

Enkel controller → service → OpenSearch-klient uten ekstra abstraksjonslag. Backend tar imot `RekrutteringstreffSøkRequest`, bygger OpenSearch `bool`-query og returnerer `RekrutteringstreffSøkRespons`. Tilgangskontroll håndteres strengt i backend basert på roller og innlogget bruker (fra token).

```
RekrutteringstreffSøkController
    ↓
RekrutteringstreffSøkService       ← bygger query, kaller klient
    ↓
OpenSearchKlient                   ← wrapper rundt opensearch-java
```

### Filtre og OpenSearch-clauses

| Filter               | OpenSearch-clause                                                                                                                  |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| `fritekst`           | `match` på `all_text_no` + nested `match` på `arbeidsgivere.orgnavn`, `innlegg.tittel` og `innlegg.tekstinnhold` (lav boost)       |
| `visningsstatuser`   | Sammensatt: `term` på `status` + `range` på `svarfrist`/`tilTid` per visningsstatus. For aggregeringer brukes Filter Aggregations. |
| `visAvlyste`         | Hvis false: `must_not` `term` `status=AVLYST`. Hvis true: inkludert.                                                               |
| `fylkesnummer`       | `terms` på `fylkesnummer`                                                                                                          |
| `kommunenummer`      | `terms` på `kommunenummer`                                                                                                         |
| `navkontorEnhetIder` | `terms` på `navkontorEnhetId`                                                                                                      |
| `MINE`               | `term` på `eiere` = innlogget navident                                                                                             |
| `MITT_KONTOR`        | `term` på `navkontorEnhetId` = innlogget kontor                                                                                    |

`SLETTET`-status filtreres alltid bort (`must_not` `term` `status=SLETTET`).

`fritekst` legges i `must`, alle andre i `filter`.

**Fritekst-søk med `copy_to`:**

Alle toppnivå-tekstfelter (`tittel`, `beskrivelse`, `fylkesnavn`, `kommunenavn`, `poststed`, `gateadresse`) er konfigurert med `copy_to: ["all_text_no"]` i mappingen. Det betyr at verdiene fra disse feltene automatisk kopieres til ett samlefelt (`all_text_no`).

Når brukeren søker på fritekst, kan query-builderen gjøre ett enkelt søk på `all_text_no` i stedet for å bygge komplekse `multi_match`-queries. Dette blir **mer robust** fordi:

1. **Én analyzer** – `all_text_no` bruker norsk analyzer for alle kopierte felter konsistent, uansett om det er tittel, adresse eller sted.
2. **Enklere query** – Søket blir enklere å vedlikeholde (én `match` + separate `nested`-queries for arbeidsgivere og innlegg).
3. **Mindre ytelsesproblem** – `multi_match` på mange felter kan bli langsommere; ett samlesøk-felt unngår tungt query-parsing.

**Nested-felter utelatt fra kopiering** (arbeidsgivere, innlegg) må fortsatt løses med eksplisitte nested-queries i query-builderen, siden OpenSearch ikke støtter `copy_to` fra nested til toppnivå.

**Konkret:** Hvis fritekst-søk skal implementeres, anbefales det å søke på `all_text_no` for toppnivåfelter + egne nested-queries for `arbeidsgivere.orgnavn`, `innlegg.tittel` og `innlegg.tekstinnhold`.

---

## Oppgaver

Rekkefølgen er foreslått, men hver oppgave beskriver et selvstendig leverbart steg.

### Oppgave 1: Outbox i rekrutteringstreff-api

1. Opprett `treff_rapids_kvittering`-tabell (Flyway-migrasjon)
2. Scheduler plukker usendte rader fra `treff_hendelse` (LEFT JOIN mot kvittering)
3. Scheduler sender Rapids-melding og skriver kvitteringsrad med `sendt_tidspunkt` etter vellykket sending

### Oppgave 2: Indekser-app

1. Opprett `rekrutteringstreff-indekser`-modul
2. Definer mapping og settings (norsk analyzer, nested for arbeidsgivere og innlegg)
3. Implementer `IndexClient`, `OpenSearchService`, alias-logikk
4. Implementer `TreffApiClient` for full reindeksering ved oppstart
5. Implementer `IndekserTreffLytter` for inkrementelle oppdateringer
6. Deploy til dev, verifiser data

### Oppgave 3: Søke-app

1. Opprett søke-app/-modul med OpenSearch lesekonfig
2. Implementer query builder (fritekst + status + paginering først)
3. Legg til geografi-filtre
4. Legg til visning-filter og rollevalidering (ALLE / MINE / MITT_KONTOR)
5. Komponenttester med OpenSearch Testcontainers

### Oppgave 4: Frontend

1. Ny hook `useRekrutteringstreffSøk` – POST med SWR, request body som cache-nøkkel
2. Utvid kontekst med alle filterfelter + `visning`-tab
3. Erstatt client-side filtrering i `RekrutteringstreffSøk.tsx`
4. Legg til paginering
5. Debounce (300ms) på fritekst
6. Fjern `useRekrutteringstreffOversikt` (hent alle) når søk er stabilt

---

## Tilgang i søk

Søket har tre **visninger** (faner i frontend): `ALLE`, `MINE` og `MITT_KONTOR`. Visningen bestemmer **scope** – altså hvilke treff som er med i resultatet. Rollen bestemmer **tilgang** – om du i det hele tatt får lov til å bruke en visning, og om noen statuser filtreres bort.

Roller (fra AD-grupper): **Jobbsøkerrettet** (lesetilgang), **Arbeidsgiverrettet** (opprette/administrere treff), **Utvikler/Admin** (full tilgang, ingen pilotkontor-krav). Se [tilgangsstyring.md](../3-sikkerhet/tilgangsstyring.md) for detaljer.

Pilotkontor-krav håndheves som pre-flight-sjekk i controller (403 før søk kjøres) og gjelder alle roller unntatt utvikler.

| Visning       | Jobbsøkerrettet                                | Arbeidsgiverrettet                 | Utvikler/Admin                     |
| ------------- | ---------------------------------------------- | ---------------------------------- | ---------------------------------- |
| `ALLE`        | Alle publiserte treff (ikke avlyste/fullførte) | Alle statuser                      | Alle statuser                      |
| `MINE`        | Ikke tillatt (403) – kan ikke opprette treff   | `eiere` inneholder brukerens ident | `eiere` inneholder brukerens ident |
| `MITT_KONTOR` | `navkontorEnhetId = aktivEnhet` + `PUBLISERT`  | `navkontorEnhetId = aktivEnhet`    | `navkontorEnhetId = aktivEnhet`    |

Rollefilter legges alltid server-side, uavhengig av hva klienten sender inn. Ugyldig visning/rolle-kombinasjon gir 403.

### Hva vises i UI per rolle og visning

**Arbeidsgiverrettet / Utvikler** (likt):

| UI-element    | `ALLE`                                                    | `MINE`                                                    | `MITT_KONTOR`                                                  |
| ------------- | --------------------------------------------------------- | --------------------------------------------------------- | -------------------------------------------------------------- |
| **Tabs**      | Alle · **Mine** · Mitt kontor                             | Alle · **Mine** · Mitt kontor                             | Alle · Mine · **Mitt kontor**                                  |
| **Fritekst**  | Ja                                                        | Ja                                                        | Ja                                                             |
| **Sortering** | Alle valg                                                 | Alle valg                                                 | Alle valg                                                      |
| **Steder**    | Ja                                                        | Skjules (egne treff er ikke avgrenset til sted)           | Skjules (allerede avgrenset til eget kontor)                   |
| **Kontor**    | Ja                                                        | Skjules                                                   | Skjules                                                        |
| **Status**    | Alle visningsstatuser + «Ikke publiserte» + «Vis avlyste» | Alle visningsstatuser + «Ikke publiserte» + «Vis avlyste» | Alle visningsstatuser + «Vis avlyste» (ikke «Ikke publiserte») |

**Jobbsøkerrettet:**

| UI-element          | `ALLE`                                        | `MINE`            | `MITT_KONTOR`                                |
| ------------------- | --------------------------------------------- | ----------------- | -------------------------------------------- |
| **Tabs**            | **Alle** · Mitt kontor (Mine-tab vises ikke)  | Ikke tilgjengelig | Alle · **Mitt kontor**                       |
| **Fritekst**        | Ja                                            | –                 | Ja                                           |
| **Sortering**       | Alle unntatt «Fullførte» (ser ikke fullførte) | –                 | Alle unntatt «Fullførte»                     |
| **Steder**          | Ja                                            | –                 | Skjules                                      |
| **Kontor**          | Ja                                            | –                 | Skjules                                      |
| **Status**          | Åpen for søkere · Stengt for søkere · Utløpt  | –                 | Åpen for søkere · Stengt for søkere · Utløpt |
| **Vis avlyste**     | Nei (ser aldri avlyste)                       | –                 | Nei                                          |
| **Ikke publiserte** | Nei (kan ikke opprette treff)                 | –                 | Nei                                          |

Merk: Skjuling av filtre i frontend er UX-tilpasning – backend håndhever uansett rollebaserte begrensninger uavhengig av hva klienten sender.

---

## Konsistens med `rekrutteringsbistand-kandidatsok-api`

Treff-søk følger samme mønster som kandidatsøk:

- Backend håndhever rolle per visning – frontend-faner er ikke sikkerhetsmekanisme.
- Ett endepunkt (`POST /api/rekrutteringstreff/sok`) med `visning` i request. Ugyldig `visning`/rolle-kombinasjon gir `403`.
- Parameteriserte tilgangstester (rolle × visning → forventet HTTP-status) etter mønster fra `KandidatsøkTest`.

---

## Inspirasjon fra `toi-stilling-indekser` og frontend stillingssøk

### Fra `toi-stilling-indekser` (backend-indeksering)

1. **Alias + versjonert indeks som standardmønster**
   - Stilling bruker fast alias (`stilling`) som peker på versjonert indeks (`INDEKS_VERSJON`).
   - Treff-indekser bør gjøre tilsvarende: fast alias (f.eks. `rekrutteringstreff`) + versjonerte indekser for trygg bytte/revert.

2. **Separat håndtering av full indeksering og inkrementelle oppdateringer**
   - Stilling skiller på lyttere for vanlig indeksering og reindeksering.
   - Stilling bruker to env-variabler: `INDEKS_VERSJON` (normal drift) og `REINDEKSER_INDEKS` (reindeksering til ny versjon). Anbefaler samme oppsett for treff-indekseren.
   - Treff-opplegget bør beholde samme prinsipp: tydelig flyt for initial/reindex vs. løpende hendelser.

3. **Mapping-prinsipper som treffer godt for likt domene**
   - Filterfelter som brukes i `term`/`terms` bør være `keyword`.
   - Fritekstfelter bør være `text` med norsk analyzer.
   - Repeaterende objekter (`arbeidsgivere`, `innlegg`) bør være `nested` når de skal søkes korrekt per objekt.
   - Vurder `copy_to` til ett samlesøk-felt for robust fritekst på tvers av flere felter.

4. **Teststrategi med OpenSearch Testcontainers**
   - Stilling tester alias-bytte, indeksopprettelse, reindeksering og oppdateringsflyt mot ekte OpenSearch-container.
   - Treff-indekser bør ha tilsvarende testdekning tidlig, spesielt for alias-bytte og idempotent indeksering.

### Fra frontend stillingssøk (filter og søk)

1. **Skille mellom treff-query og aggregerings-query**
   - Stilling bygger én query for hits og én for aggregeringer (`size=0`).
   - Treff-søk kan bruke samme mønster hvis vi trenger facets i UI (fylke/status-antall).

2. **Bruke `post_filter` for visningsstatus uten å ødelegge aggregater**
   - Stilling bruker `post_filter` slik at facets representerer totalen, mens listevisning filtreres.
   - Relevant for treff hvis vi introduserer fasetterte filterchips med antall.

3. **Portefølje-/visningsfilter som egen query-modul**
   - Stilling har egen `portefølje-query` med tydelige regler per visning.
   - Treff-søk bør gjøre tilsvarende (`visning-query`) i backend for enklere vedlikehold og tydelig autorisasjon.

4. **Paging og sortering**
   - Ugyldig `side` eller `antallPerSide` gir 400 (Bad Request), ingen silent fallback.
   - Sortering mappes eksplisitt fra enum til OpenSearch-sort:

     | `Sortering`       | OpenSearch sort-felt   | Retning | Ekstra filter                           |
     | ----------------- | ---------------------- | ------- | --------------------------------------- |
     | `RELEVANS`        | `_score`               | desc    | Kun meningsfull med fritekst            |
     | `SIST_OPPDATERTE` | `sistEndret`           | desc    | –                                       |
     | `NYESTE`          | `opprettetAvTidspunkt` | desc    | –                                       |
     | `ELDSTE`          | `opprettetAvTidspunkt` | asc     | –                                       |
     | `AKTIVE`          | `fraTid`               | asc     | `status = PUBLISERT` og `fraTid >= now` |
     | `FULLFØRTE`       | `tilTid`               | desc    | `status = FULLFØRT`                     |

---

## Foreslått førsteversjon av OpenSearch settings og mapping

Dette er et konkret utgangspunkt for `apps/rekrutteringstreff-indekser/src/main/resources/treff-settings.json` og `treff-mapping.json`.

### `treff-settings.json` (forslag)

```json
{
  "index": {
    "number_of_shards": 3,
    "number_of_replicas": 2
  },
  "analysis": {
    "filter": {
      "norwegian_stop": {
        "type": "stop",
        "stopwords": "_norwegian_"
      },
      "norwegian_stemmer": {
        "type": "stemmer",
        "language": "norwegian"
      }
    },
    "char_filter": {
      "custom_trim": {
        "type": "pattern_replace",
        "pattern": "^\\s+|\\s+$",
        "replacement": ""
      }
    },
    "normalizer": {
      "trim_normalizer": {
        "type": "custom",
        "char_filter": ["custom_trim"]
      },
      "lowercase_normalizer": {
        "type": "custom",
        "char_filter": ["custom_trim"],
        "filter": ["lowercase"]
      },
      "lowercase_folding_normalizer": {
        "type": "custom",
        "char_filter": ["custom_trim"],
        "filter": ["lowercase", "asciifolding"]
      }
    },
    "analyzer": {
      "norwegian_html": {
        "tokenizer": "standard",
        "filter": ["lowercase", "norwegian_stop", "norwegian_stemmer"],
        "char_filter": ["html_strip"]
      }
    }
  }
}
```

### `treff-mapping.json` (forslag)

```json
{
  "date_detection": false,
  "dynamic": false,
  "properties": {
    "id": {
      "type": "keyword"
    },
    "status": {
      "type": "keyword"
    },
    "fraTid": {
      "type": "date",
      "format": "strict_date_optional_time"
    },
    "tilTid": {
      "type": "date",
      "format": "strict_date_optional_time"
    },
    "svarfrist": {
      "type": "date",
      "format": "strict_date_optional_time"
    },
    "opprettetAvTidspunkt": {
      "type": "date",
      "format": "strict_date_optional_time"
    },
    "sistEndret": {
      "type": "date",
      "format": "strict_date_optional_time"
    },
    "opprettetAvNavident": {
      "type": "keyword",
      "normalizer": "lowercase_normalizer"
    },
    "opprettetAvNavn": {
      "type": "keyword"
    },
    "navkontorEnhetId": {
      "type": "keyword"
    },
    "eiere": {
      "type": "keyword",
      "normalizer": "lowercase_normalizer"
    },
    "antallArbeidsgivere": {
      "type": "integer"
    },
    "antallJobbsøkere": {
      "type": "integer"
    },
    "fylkesnummer": {
      "type": "keyword"
    },
    "fylkesnavn": {
      "type": "text",
      "analyzer": "norwegian",
      "copy_to": ["all_text_no"],
      "fields": {
        "keyword": {
          "type": "keyword",
          "normalizer": "lowercase_folding_normalizer"
        }
      }
    },
    "kommunenummer": {
      "type": "keyword"
    },
    "kommunenavn": {
      "type": "text",
      "analyzer": "norwegian",
      "copy_to": ["all_text_no"],
      "fields": {
        "keyword": {
          "type": "keyword",
          "normalizer": "lowercase_folding_normalizer"
        }
      }
    },
    "postnummer": {
      "type": "keyword"
    },
    "poststed": {
      "type": "text",
      "analyzer": "norwegian",
      "copy_to": ["all_text_no"],
      "fields": {
        "keyword": {
          "type": "keyword",
          "normalizer": "lowercase_folding_normalizer"
        }
      }
    },
    "gateadresse": {
      "type": "text",
      "copy_to": ["all_text_no"]
    },
    "tittel": {
      "type": "text",
      "analyzer": "norwegian",
      "copy_to": ["all_text_no"]
    },
    "beskrivelse": {
      "type": "text",
      "analyzer": "norwegian_html",
      "copy_to": ["all_text_no"]
    },
    "all_text_no": {
      "type": "text",
      "analyzer": "norwegian_html",
      "index": true
    },
    "arbeidsgivere": {
      "type": "nested",
      "properties": {
        "orgnr": {
          "type": "keyword"
        },
        "orgnavn": {
          "type": "text",
          "analyzer": "norwegian"
        }
      }
    },
    "innlegg": {
      "type": "nested",
      "properties": {
        "tittel": {
          "type": "text",
          "analyzer": "norwegian"
        },
        "tekstinnhold": {
          "type": "text",
          "analyzer": "norwegian_html"
        }
      }
    }
  }
}
```

### Operasjonelle avklaringer før produksjon

- `number_of_shards`/`number_of_replicas` bør justeres etter datamengde og miljø (dev/prod) før endelig låsing.
- `dynamic: false` er valgt for kontroll på schema; nye felter krever eksplisitt mapping-endring.
- Navident-felt er normalisert til lowercase for trygg matching mot token-claims i søkefiltre.
- `copy_to` er bevisst utelatt fra nested-felter (`arbeidsgivere`, `innlegg`) fordi OpenSearch ikke støtter `copy_to` fra nested til toppnivå. Fritekst-søk i nested-felter løses med eksplisitte nested-queries i query-builderen.
- Aggregeringer (antall per fylke, visningsstatus og navkontor) trengs for å vise tall i filterpanelet. Disse bygges som `terms`-aggregeringer på `keyword`-feltene i mapping, bortsett fra visningsstatuser som krever Filter Aggregations fordi de er sammensatt av status + tidsverdier.

---

## Risiko

| Risiko                        | Avbøting                                                                                                                                                                                                |
| ----------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Indeks og database ut av synk | Full reindeksering som fallback. Monitorer lag i `sendt_tidspunkt`.                                                                                                                                     |
| OpenSearch utilgjengelig      | Returner feilmelding (503). Ingen fallback til gammelt endepunkt – risikoen for å vise feil data/statuser er for høy.                                                                                   |
| Query-ytelse                  | Start enkelt, profiler med reelle data, juster boost-verdier                                                                                                                                            |
| Visningsstatus-aggregeringer  | Tidsbaserte visningsstatuser krever komplekse agg-queries. Prototype tidlig i oppgave 3, vurder forenkling hvis ytelsen er dårlig.                                                                      |
| Clause explosion (fritekst)   | Stillingssøk treffer Aivens clause-grense med `multi_match` mot mange felt × mange ord. Her brukes `match` på `all_text_no`, som holder clause-antallet lavt uavhengig av antall ord eller kommunevalg. |
