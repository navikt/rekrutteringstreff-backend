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

Fritekst-feltet søker på tvers av tittel, beskrivelse, innleggsinnhold og arbeidsgivernavn – ikke et eget arbeidsgiver-søkefelt. Tabs (Alle / Mine / Mitt kontor) er mutually exclusive og erstatter separate boolean-flagg.

Søkeformatet modelleres etter mønsteret fra `rekrutteringsbistand-kandidatsok-api` i NAV sitt repo.

### Request

```kotlin
data class RekrutteringstreffSøkRequest(
    val fritekst: String? = null,
    val statuser: List<RekrutteringstreffStatus>? = null,
    val fylker: List<String>? = null,           // fylkesnummer
    val kommuner: List<String>? = null,         // kommunenummer
    val navkontor: List<String>? = null,        // enhetId-er
    val visning: Visning = Visning.ALLE,
    val sortering: Sortering = Sortering.DATO,
    val side: Int = 0,
    val antallPerSide: Int = 20,
)

enum class Visning {
    ALLE,           // ingen ekstra filter
    MINE,           // eiere inneholder innlogget navident
    MITT_KONTOR,    // opprettetAvNavkontorEnhetId = innlogget kontor
}

enum class Sortering {
    RELEVANS,           // _score – default når fritekst er satt
    PUBLISERINGSDATO,
    DATO,               // fraTid – default uten fritekst
}
```

### Respons

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
    val svarfrist: ZonedDateTime?,
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

### Endepunkt

```
POST /api/rekrutteringstreff-sok
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
2. En scheduler (med leader election) kjører periodisk og finner usendterade ved LEFT JOIN + `WHERE kvittering IS NULL`
3. For hver usent hendelse: send Rapids-melding, deretter INSERT kvitteringsrad

```sql
-- Henter usendterade (samme mønster som AktivitetskortRepository)
SELECT th.*
FROM treff_hendelse th
LEFT JOIN treff_rapids_kvittering k ON th.treff_hendelse_id = k.treff_hendelse_id
WHERE k.treff_rapids_kvittering_id IS NULL
ORDER BY th.tidspunkt;

-- Skrives KUN etter vellykket sending
INSERT INTO treff_rapids_kvittering(treff_hendelse_id, sendt_tidspunkt)
VALUES (?, NOW());
```

```sql
CREATE TABLE treff_rapids_kvittering (
    treff_rapids_kvittering_id bigserial PRIMARY KEY,
    treff_hendelse_id          bigint                   NOT NULL,
    sendt_tidspunkt            timestamp with time zone NOT NULL,
    CONSTRAINT fk_treff_hendelse
        FOREIGN KEY (treff_hendelse_id) REFERENCES treff_hendelse (treff_hendelse_id)
);
```

**Viktig forskjell fra tradisjonell outbox:** Kvitteringsraden skrives _etter_ sending, ikke i samme transaksjon som domeneendringen. Det betyr at hvis appen krasjer mellom sending og kvitteringsskriving, kan meldingen sendes to ganger. Indekseren må derfor være idempotent (upsert på dokument-ID).

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
  "kommune": "Oslo",
  "fylkesnummer": "03",
  "fylke": "Oslo",
  "opprettetAvPersonNavident": "Z993102",
  "opprettetAvNavkontorEnhetId": "0318",
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

### Appstruktur

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

Ny app (eller modul) i `rekrutteringstreff-backend` som eksponerer søke-endepunktet.

### Alternativ A: Etter mal fra rekrutteringsbistand-kandidatsok-api

Bruker same arkitektur og OpenSearch-klient som eksisterende kandidatsøk. Fordel: kjent mønster i NAV. Ulempe: tar inn kandidatsøk-avhengigheter som kanskje ikke passer direkte.

### Alternativ B: Query builder i rekrutteringstreff-backend (anbefalt)

Enkel controller → service → OpenSearch-klient uten ekstra abstraksjonslag. Backend tar imot `RekrutteringstreffSøkRequest`, bygger OpenSearch `bool`-query og returnerer `RekrutteringstreffSøkRespons`.

```
RekrutteringstreffSøkController
    ↓
RekrutteringstreffSøkService       ← bygger query, kaller klient
    ↓
OpenSearchKlient                   ← wrapper rundt opensearch-java
```

Avhengigheter i `build.gradle.kts`:

```kotlin
implementation("org.opensearch.client:opensearch-java:2.22.0")
implementation("org.apache.httpcomponents.client5:httpclient5:5.4.2")
```

### Filtre og OpenSearch-clauses

| Filter        | OpenSearch-clause                                                                                                            |
| ------------- | ---------------------------------------------------------------------------------------------------------------------------- |
| `fritekst`    | `multi_match` på `tittel`, `beskrivelse`, `innlegg.tittel`, `innlegg.tekstinnhold` + nested match på `arbeidsgivere.orgnavn` |
| `statuser`    | `terms` på `status`                                                                                                          |
| `fylker`      | `terms` på `fylkesnummer`                                                                                                    |
| `kommuner`    | `terms` på `kommunenummer`                                                                                                   |
| `navkontor`   | `terms` på `opprettetAvNavkontorEnhetId`                                                                                     |
| `MINE`        | `term` på `eiere` = innlogget navident                                                                                       |
| `MITT_KONTOR` | `term` på `opprettetAvNavkontorEnhetId` = innlogget kontor                                                                   |

`fritekst` legges i `must`, alle andre i `filter`.

---

## Implementeringsrekkefølge

### Fase 1: Outbox i rekrutteringstreff-api

1. Opprett `treff_rapids_kvittering`-tabell (Flyway-migrasjon)
2. Service skriver rad til kvitteringstabellen etter eksisterende hendelsesskriving
3. Scheduler plukker usendte rader, sender Rapids-melding, setter `sendt_tidspunkt`

### Fase 2: Indekser-app

1. Opprett `rekrutteringstreff-indekser`-modul
2. Definer mapping og settings (norsk analyzer, nested for arbeidsgivere og innlegg)
3. Implementer `IndexClient`, `OpenSearchService`, alias-logikk
4. Implementer `TreffApiClient` for full reindeksering ved oppstart
5. Implementer `IndekserTreffLytter` for inkrementelle oppdateringer
6. Deploy til dev, verifiser data

### Fase 3: Søke-app

1. Opprett søke-app/-modul med OpenSearch lesekonfig
2. Implementer query builder (fritekst + status + paginering først)
3. Legg til geografi-filtre
4. Legg til visning-filter (MINE / MITT_KONTOR)
5. Komponenttester med OpenSearch Testcontainers

### Fase 4: Frontend

1. Ny hook `useRekrutteringstreffSøk` – POST med SWR, request body som cache-nøkkel
2. Utvid kontekst med alle filterfelter + `visning`-tab
3. Erstatt client-side filtrering i `RekrutteringstreffSøk.tsx`
4. Legg til paginering
5. Debounce (300ms) på fritekst
6. Fjern `useRekrutteringstreffOversikt` (hent alle) når søk er stabilt

---

## Åpne spørsmål

| #   | Spørsmål                                                        | Status                                                             |
| --- | --------------------------------------------------------------- | ------------------------------------------------------------------ |
| 1   | **Søke-app som egen app eller modul i rekrutteringstreff-api?** | Uavklart – egen app gir separat deploy/skalering, modul er enklere |
| 2   | **Innlegg som søkefelt – nødvendig?**                           | Kan gi støy, vurder å utelate eller gi lav boost                   |
| 3   | **Facets/aggregeringer?**                                       | Antall treff per fylke/status – kan legges til i responsmodellen   |
| 4   | **Tilgangskontroll – hva ser veileder vs. markedskontakt?**     | Veileder ser alle publiserte + egne; markedskontakt ser alle?      |
| 5   | **Val av Alternativ A vs. B for søke-app**                      | Se Del 4 over                                                      |

---

## Risiko

| Risiko                        | Avbøting                                                         |
| ----------------------------- | ---------------------------------------------------------------- |
| Indeks og database ut av synk | Full reindeksering som fallback. Monitorer sendt_tidspunkt-lag.  |
| OpenSearch utilgjengelig      | Fallback til `GET /api/rekrutteringstreff` inntil søk er stabilt |
| Query-ytelse                  | Start enkelt, profiler med reelle data, juster boost-verdier     |
