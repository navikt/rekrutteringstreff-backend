# OpenSearch-søk for rekrutteringstreff – Plan

## Bakgrunn og motivasjon

I dag henter frontend **alle** rekrutteringstreff fra backend (`GET /api/rekrutteringstreff`) og gjør filtrering, fritekst-søk og sortering i klienten. Dette skalerer ikke, og begrenser mulighetene for avanserte søk. Filterpanelet i frontend har allerede kommentert-ut filter som venter på backend-søk.

Målet er å flytte all søke- og filtreringslogikk til OpenSearch, slik at frontend sender søkeparametere og får paginerte, relevansrangerte resultater tilbake.

## Arkitekturoversikt

```
┌──────────────┐       ┌──────────────────────┐       ┌───────────────┐
│   Frontend   │──────▶│ rekrutteringstreff-api│──────▶│  OpenSearch   │
│  (Next.js)   │ POST  │  /api/treff-sok      │ query │  (Aiven)      │
│              │◀──────│                      │◀──────│               │
│              │  JSON │                      │  hits │               │
└──────────────┘       └──────────────────────┘       └───────────────┘
                                                            ▲
                                                            │ indekserer
                                                      ┌─────┴─────────────┐
                                                      │  rekrutteringstreff│
                                                      │  -indekser (ny)   │
                                                      │                   │
                                                      └─────┬─────────────┘
                                                            │ lytter
                                                      ┌─────┴─────────────┐
                                                      │  PostgreSQL       │
                                                      │  (via Kafka /     │
                                                      │   direkte-poll)   │
                                                      └───────────────────┘
```

Tre komponenter:

| Komponent                                 | Ansvar                                                                        |
| ----------------------------------------- | ----------------------------------------------------------------------------- |
| **rekrutteringstreff-indekser** (ny app)  | Lytter på endringer, indekserer dokumenter til OpenSearch                     |
| **rekrutteringstreff-api** (eksisterende) | Nytt søke-endepunkt som bygger OpenSearch-spørringer og returnerer resultater |
| **Frontend** (eksisterende)               | Sender søkeparametere, viser paginerte resultater                             |

---

## Del 1: Indekser-app (`rekrutteringstreff-indekser`)

### Plassering

Ny app under `rekrutteringstreff-backend/apps/rekrutteringstreff-indekser/`. Følger samme mønster som `toi-stilling-indekser` i `toi-rapids-and-rivers`.

Må legges til i `settings.gradle.kts`:

```kotlin
include("apps:rekrutteringstreff-indekser")
```

### Datakilde – hvordan oppdages endringer?

Tre alternativer å vurdere:

| Alternativ                                                                           | Fordeler                                           | Ulemper                                                          |
| ------------------------------------------------------------------------------------ | -------------------------------------------------- | ---------------------------------------------------------------- |
| **A) Kafka / Rapids & Rivers** – backend publiserer event ved opprett/oppdater/slett | Hendelsesdrevet, lav latens, kjent mønster         | Krever at API-et publiserer events konsekvent for alle endringer |
| **B) Polling mot databasen** – indekseren poller `sist_endret`-kolonne periodisk     | Enklest å implementere, ingen ny Kafka-avhengighet | Høyere latens, mer belastning på DB                              |
| **C) Outbox-pattern** – skriv hendelser til en outbox-tabell, indekserer konsumerer  | Transaksjonelt trygt, ingen duplisering            | Mer infra                                                        |

**Anbefalt: Alternativ A (Kafka).** Backend publiserer allerede hendelser til hendelsestabeller. Vi kan publisere disse til Kafka via Rapids & Rivers. Indekseren lytter og oppdaterer OpenSearch. Ved behov kan vi gjøre full reindeksering ved å lese alle treff fra API-et (tilsvarende `StillingApiClient` i stilling-indekseren).

### Hva indekseres?

Hvert rekrutteringstreff blir ett dokument i OpenSearch. Dokumentet er denormalisert – det inkluderer data fra flere tabeller:

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
  "kommune": "Oslo",
  "kommunenummer": "0301",
  "fylke": "Oslo",
  "fylkesnummer": "03",

  "opprettetAvPersonNavident": "Z993102",
  "opprettetAvNavkontorEnhetId": "0318",
  "opprettetAvTidspunkt": "2026-03-02T10:00:00+01:00",
  "sistEndret": "2026-03-02T12:00:00+01:00",

  "eiere": ["Z993102", "Z990659"],

  "arbeidsgivere": [
    {
      "orgnr": "912345678",
      "orgnavn": "Eksempel AS",
      "kommune": "Oslo",
      "fylke": "Oslo"
    }
  ],
  "antallArbeidsgivere": 1,
  "antallJobbsøkere": 4,

  "innlegg": [
    {
      "tittel": "Velkommen",
      "tekstinnhold": "Ren tekst fra html_content, for fritekst-søk"
    }
  ]
}
```

### OpenSearch-mapping (utkast)

```json
{
  "properties": {
    "id": { "type": "keyword" },
    "tittel": {
      "type": "text",
      "analyzer": "norwegian",
      "fields": { "keyword": { "type": "keyword" } }
    },
    "beskrivelse": { "type": "text", "analyzer": "norwegian_html" },
    "status": { "type": "keyword" },
    "fraTid": { "type": "date" },
    "tilTid": { "type": "date" },
    "svarfrist": { "type": "date" },
    "gateadresse": {
      "type": "text",
      "fields": { "keyword": { "type": "keyword" } }
    },
    "postnummer": { "type": "keyword" },
    "poststed": {
      "type": "text",
      "fields": { "keyword": { "type": "keyword" } }
    },
    "kommune": {
      "type": "text",
      "fields": { "keyword": { "type": "keyword" } }
    },
    "kommunenummer": { "type": "keyword" },
    "fylke": { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
    "fylkesnummer": { "type": "keyword" },
    "opprettetAvPersonNavident": { "type": "keyword" },
    "opprettetAvNavkontorEnhetId": { "type": "keyword" },
    "opprettetAvTidspunkt": { "type": "date" },
    "sistEndret": { "type": "date" },
    "eiere": { "type": "keyword" },
    "antallArbeidsgivere": { "type": "integer" },
    "antallJobbsøkere": { "type": "integer" },
    "arbeidsgivere": {
      "type": "nested",
      "properties": {
        "orgnr": { "type": "keyword" },
        "orgnavn": {
          "type": "text",
          "analyzer": "norwegian",
          "fields": { "keyword": { "type": "keyword" } }
        },
        "kommune": { "type": "keyword" },
        "fylke": { "type": "keyword" }
      }
    },
    "innlegg": {
      "type": "nested",
      "properties": {
        "tittel": { "type": "text", "analyzer": "norwegian" },
        "tekstinnhold": { "type": "text", "analyzer": "norwegian_html" }
      }
    }
  }
}
```

### Index-settings

Tilsvarende `stilling-common.json`: norsk analyzer med stemmer, stoppord, og `html_strip` for innhold med HTML.

### Appstruktur (filtreet)

```
apps/rekrutteringstreff-indekser/
├── build.gradle.kts
├── Dockerfile
├── nais.yaml / nais-dev.yaml / nais-prod.yaml
├── opensearch.yaml                   ← Aiven OpenSearch-ressurs
└── src/main/
    ├── resources/
    │   ├── treff-mapping.json
    │   └── treff-settings.json
    └── kotlin/no/nav/toi/rekrutteringstreff/indekser/
        ├── Application.kt            ← Oppstart, DI
        ├── OpenSearchConfig.kt       ← Klient-konfigurasjon
        ├── IndexClient.kt            ← CRUD mot OpenSearch
        ├── OpenSearchService.kt      ← Indeks-livssyklus (opprett, alias, reindekser)
        ├── TreffDokument.kt          ← Dokumentmodell
        ├── IndekserTreffLytter.kt    ← Rapids-lytter for treff-hendelser
        ├── TreffApiClient.kt         ← Henter alle treff for full reindeksering
        └── Liveness.kt
```

### Reindeksering

Støtte for full reindeksering (alle treff) ved:

- Oppstart med ny indeksversjon (`INDEKS_VERSJON` env var)
- Manuell trigger via et admin-endepunkt eller Kafka-melding

Bruker alias-bytte for zero-downtime: indekser til ny indeks, bytt alias når ferdig.

### NAIS / OpenSearch-ressurs

Egen `opensearch.yaml` (Aiven) tilsvarende den i `toi-stilling-indekser`. Indekseren og `rekrutteringstreff-api` bruker samme OpenSearch-instans. Begge trenger credentials via NAIS-config.

---

## Del 2: Søke-API i `rekrutteringstreff-api`

### Arkitekturvalg: Proxy vs. query builder

| Tilnærming                                                                    | Fordeler                                                                    | Ulemper                                                                                      |
| ----------------------------------------------------------------------------- | --------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------- |
| **Proxy** – frontend sender OpenSearch-query body, backend videresender       | Fleksibelt, frontend har full kontroll                                      | Sikkerhetsproblem (query injection), tight coupling til OpenSearch DSL, vanskelig å validere |
| **Query builder** – backend tar imot enkle parametere, bygger queries internt | Trygt, rent API, lett å validere, kan endre søkemotor uten å endre frontend | Backend-endring ved nye filtre                                                               |

**Anbefalt: Query builder.** Grunner:

1. Sikkerheten – vi eksponerer ikke OpenSearch DSL til klienten
2. Validering – backend kontrollerer hva som kan søkes på
3. Enklere frontend – sender enkle parametere, ikke komplekse JSON-queries
4. Vedlikehold – søkelogikk samlet på ett sted

### Implementeringsvalg for query-bygging

Tre tilnærminger for å bygge OpenSearch-queries i Kotlin:

| Tilnærming                              | Beskrivelse                                                                                          |
| --------------------------------------- | ---------------------------------------------------------------------------------------------------- |
| **OpenSearch Java-klient**              | Bruk `org.opensearch.client:opensearch-java` sin builder-API direkte. Typesikkert. Kan være verbose. |
| **Rå JSON-templates**                   | Bygg query som JSON-strenger med string interpolation. Enkelt, men feilutsatt.                       |
| **Kotlin DSL (egendefinert eller lib)** | Lag en thin Kotlin DSL rundt OpenSearch-klienten for lesbarhet.                                      |

**Anbefalt: OpenSearch Java-klient direkte**, evt. med lette Kotlin-hjelpefunksjoner for vanlige mønstre (f.eks. `mustMatch`, `shouldMatch`). Unngå å introdusere ekstra avhengigheter. Kandidatsøk-API bruker Elasticsearch REST high-level client, men vi bør bruke den nyere `opensearch-java`-klienten som allerede er i bruk i `toi-stilling-indekser`.

### Endepunkt

```
POST /api/rekrutteringstreff-sok
```

Bruker POST fordi søkeparameterne kan bli komplekse (lister, nested filters).

### Request-modell

```kotlin
data class RekrutteringstreffSøkRequest(
    val fritekst: String? = null,
    val statuser: List<RekrutteringstreffStatus>? = null,
    val fylker: List<String>? = null,           // fylkesnummer
    val kommuner: List<String>? = null,         // kommunenummer
    val navkontor: List<String>? = null,        // enhetId-er
    val kunMittKontor: Boolean = false,         // filter på innlogget brukers kontor
    val kunMineTreff: Boolean = false,          // filter på innlogget brukers navident (eier)
    val arbeidsgiver: ArbeidsgiverSøk? = null,
    val sortering: Sortering = Sortering.RELEVANS,
    val side: Int = 0,
    val antallPerSide: Int = 20,
)

data class ArbeidsgiverSøk(
    val fritekst: String? = null,    // søk i orgnavn
    val orgnr: String? = null,       // eksakt match på orgnr
)

enum class Sortering {
    RELEVANS,
    PUBLISERINGSDATO,
    DATO,            // fraTid – dato for treffet
}
```

### Respons-modell

```kotlin
data class RekrutteringstreffSøkRespons(
    val treff: List<RekrutteringstreffSøkTreff>,
    val totaltAntall: Long,
    val side: Int,
    val antallPerSide: Int,
)

data class RekrutteringstreffSøkTreff(
    val id: UUID,
    val tittel: String,
    val status: RekrutteringstreffStatus,
    val fraTid: ZonedDateTime?,
    val tilTid: ZonedDateTime?,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val kommune: String?,
    val fylke: String?,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
    val antallArbeidsgivere: Int,
    val antallJobbsøkere: Int,
    val eiere: List<String>,
)
```

### Query-bygging – oversikt over filtre

| Filter                  | OpenSearch-clause                                                                  | Detaljer                                                                               |
| ----------------------- | ---------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------- |
| `fritekst`              | `multi_match` på `tittel`, `beskrivelse`, `innlegg.tittel`, `innlegg.tekstinnhold` | Type: `best_fields` eller `cross_fields` med norsk analyzer. Nested query for innlegg. |
| `statuser`              | `terms` på `status`                                                                | F.eks. `["PUBLISERT", "OPPRETTET"]`                                                    |
| `fylker`                | `terms` på `fylkesnummer`                                                          |                                                                                        |
| `kommuner`              | `terms` på `kommunenummer`                                                         |                                                                                        |
| `navkontor`             | `terms` på `opprettetAvNavkontorEnhetId`                                           |                                                                                        |
| `kunMittKontor`         | `term` på `opprettetAvNavkontorEnhetId` = innlogget brukers kontor                 | Krever at innlogget brukers kontor er tilgjengelig fra token/context                   |
| `kunMineTreff`          | `term` på `eiere` = innlogget brukers navident                                     |                                                                                        |
| `arbeidsgiver.fritekst` | `nested` query → `match` på `arbeidsgivere.orgnavn`                                |                                                                                        |
| `arbeidsgiver.orgnr`    | `nested` query → `term` på `arbeidsgivere.orgnr`                                   | Eksakt match                                                                           |

Alle filtre kombineres i en `bool`-query med `must` (fritekst) og `filter` (eksakte filtre).

### Lagdeling i backend

```
RekrutteringstreffSøkController        ← Nytt, HTTP-endepunkt
    ↓
RekrutteringstreffSøkService           ← Bygger OpenSearch-query, kaller klienten
    ↓
OpenSearchKlient                       ← Wrapper rundt opensearch-java-klienten
```

Følger eksisterende mønster, men `Repository` erstattes av `OpenSearchKlient` siden datakilden er OpenSearch, ikke PostgreSQL.

### Avhengigheter (build.gradle.kts)

Legg til i `rekrutteringstreff-api`:

```kotlin
implementation("org.opensearch.client:opensearch-java:2.22.0")
implementation("org.apache.httpcomponents.client5:httpclient5:5.4.2")
```

### NAIS-konfig

`rekrutteringstreff-api` trenger tilgang til OpenSearch-instansen. Legges til i nais-config:

```yaml
openSearch:
  instance: opensearch-rekrutteringstreff
  access: read
```

(Indekseren trenger `readwrite`.)

---

## Del 3: Frontend-endringer

### Erstatte client-side søk med API-kall

**Nåsituasjon:**

- `useRekrutteringstreffOversikt` henter alle treff
- `RekrutteringstreffSøk.tsx` filtrerer i minnet med `matcherFritekst()`
- `RekrutteringstreffSøkContext` holder `fritekst` og `sortering`

**Ny flyt:**

1. `RekrutteringstreffSøkContext` utvides med alle filterfelter (status, fylke, kommune, kontor, arbeidsgiver, side)
2. Ny hook `useRekrutteringstreffSøk` sender `POST /api/rekrutteringstreff-sok` med current state fra context. Bruker SWR med request body som cache-nøkkel.
3. `RekrutteringstreffSøk.tsx` fjerner client-side filtrering, viser resultater direkte fra API
4. Paginering-komponent legges til
5. Filter-komponenter aktiveres (de som er kommentert ut i `RekrutteringstreffFilter.tsx` erstattes med nye ut fra faktiske filtre)

### Nye filterkomponenter

| Filter                   | Komponent-type                                     | Plassering i sidepanel          |
| ------------------------ | -------------------------------------------------- | ------------------------------- |
| Fritekst                 | `<Search>` (som i dag)                             | Topp                            |
| Status                   | Checkboxes (Kladd / Publisert / Fullført / Avlyst) | Under sortering                 |
| Fylke / Kommune          | Hierarkisk dropdown eller combobox                 | Under status                    |
| Mitt kontor / Mine treff | Toggle/checkbox                                    | Under geografi                  |
| Arbeidsgiver             | `<Search>` for navn + orgnr                        | Under mine treff                |
| Sortering                | RadioGroup (Relevans / Publiseringsdato / Dato)    | Som i dag, utvides med relevans |

### Debounce

Fritekst-søk bør ha debounce (f.eks. 300ms) for å unngå for mange API-kall under typing.

---

## Implementeringsrekkefølge

### Fase 1: Indekser (backend)

1. Opprett `rekrutteringstreff-indekser`-modul med OpenSearch-konfig
2. Definer mapping (`treff-mapping.json`) og settings (`treff-settings.json`)
3. Implementer `IndexClient` og `OpenSearchService` (opprett indeks, alias, bulk-indeksering)
4. Implementer `TreffApiClient` – henter alle treff fra `rekrutteringstreff-api` for full reindeksering
5. Implementer full reindeksering ved oppstart
6. Implementer `IndekserTreffLytter` (Kafka-lytter for inkrementelle oppdateringer)
7. Deploy til dev med Aiven OpenSearch
8. Verifiser at data er korrekt indeksert

### Fase 2: Søke-API (backend)

1. Legg til OpenSearch-avhengigheter i `rekrutteringstreff-api`
2. Implementer `OpenSearchKlient` med lesekonfig
3. Implementer `RekrutteringstreffSøkService` – query-bygging
4. Implementer `RekrutteringstreffSøkController` med `POST /api/rekrutteringstreff-sok`
5. Start med fritekst + status + paginering
6. Legg til geografi-filtre (fylke, kommune)
7. Legg til kontor-filtre og mine treff
8. Legg til arbeidsgiver-søk (nested query)
9. Komponenttester med OpenSearch Testcontainers

### Fase 3: Frontend

1. Utvid `RekrutteringstreffSøkContext` med alle filterfelter
2. Ny hook `useRekrutteringstreffSøk` med POST-request
3. Erstatte client-side filtrering i `RekrutteringstreffSøk.tsx`
4. Legg til paginering
5. Bygg filterkomponenter (status, geografi, kontor, arbeidsgiver)
6. Legg til debounce for fritekst
7. Fjern gammel `useRekrutteringstreffOversikt`-hook (eller behold for andre bruksområder)

### Fase 4: Opprydding

1. Vurder om `GET /api/rekrutteringstreff` (hent alle) kan fjernes eller begrenses
2. Dokumentere ny arkitektur

---

## Åpne spørsmål

| #   | Spørsmål                                                                                 | Kommentar                                                                                                                                                   |
| --- | ---------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | **Skal indekseren leve i `rekrutteringstreff-backend` eller i `toi-rapids-and-rivers`?** | Plan: i `rekrutteringstreff-backend` for å holde alt relatert samlet. Alternativet er `toi-rapids-and-rivers` der lignende indeksere allerede bor.          |
| 2   | **Egen OpenSearch-instans eller dele med stilling-indekseren?**                          | Anbefaler egen instans – enklere å forvalte, og vi unngår å blande domener.                                                                                 |
| 3   | **Kafka-events – finnes de allerede, eller må de publiseres?**                           | Backend skriver til hendelsestabeller. Vi må legge til publisering til Kafka i service-laget. Alternativt kan indekseren polle en hendelseslogg.            |
| 4   | **Tilgangskontroll i søk?**                                                              | Søkeresultater bør respektere roller. F.eks. skal en veileder se alle publiserte treff, men kun egne kladder? Eller er oversikten åpen for alle innloggede? |
| 5   | **Innlegg som søkefelt – relevant?**                                                     | Innleggets tekst kan gi relevante treff, men kan også gi støy. Vurder vekting.                                                                              |
| 6   | **Sortering på relevans – default?**                                                     | Når fritekst brukes bør relevans være default. Uten fritekst faller det tilbake til dato.                                                                   |
| 7   | **Trengs facets/aggregeringer?**                                                         | F.eks. antall treff per fylke, per status – for å vise tall i filter-UI. Kan legges til som del av søke-responsen.                                          |

---

## Risiko og avbøtende tiltak

| Risiko                               | Avbøting                                                                                                    |
| ------------------------------------ | ----------------------------------------------------------------------------------------------------------- |
| Indeks og database kommer ut av synk | Full reindeksering som fallback. Monitorer lag mellom hendelse og indeksering.                              |
| OpenSearch utilgjengelig             | Fallback til eksisterende `GET /api/rekrutteringstreff` (hent alle). Frontend bør håndtere feil gracefully. |
| Query-ytelse                         | Start med enkelt, profiler. Juster analyzer/boost-verdier basert på tester med reelle data.                 |
| Sikkerhet – lekkasje av data via søk | Query builder-mønsteret hindrer query injection. Tilgangskontroll i controller.                             |
