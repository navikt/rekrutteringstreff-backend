# OpenSearch-søk for rekrutteringstreff

## Bakgrunn og motivasjon

I dag henter frontend alle rekrutteringstreff fra backend og gjør filtrering, søk og sortering i klienten. Målet er å flytte dette til OpenSearch.

## Arkitekturoversikt

```
┌──────────────┐       ┌──────────────────────┐       ┌───────────────┐
│   Frontend   │──────▶│ rekrutteringstreff-  │──────▶│  OpenSearch   │
│  (Next.js)   │ POST  │  søk (ny app)        │ query │ (Aiven/Nais)  │
│              │◀──────│                      │◀──────│               │
└──────────────┘       └──────────────────────┘       └───────────────┘
                                                            ▲
                                  Indekserer og nais config │
                                               ┌────────────┴────────────┐
                                               │  rekrutteringstreff-    │
                                               │  indekser (ny app)      │
                                               └────────────┬────────────┘
                                                            │ hendelser / reindeksering
                                               ┌────────────┴───────────────────┐
                                               │  rekrutteringstreff-api        │
                                               │ (hendelser + indekseringskø)   │
                                               └────────────────────────────────┘
```

| Komponent                                 | Ansvar                                                                                                                                                                                       |
| ----------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Frontend**                              | Sender søkeparametere, viser paginerte resultater.                                                                                                                                           |
| **rekrutteringstreff-søk** (ny app)       | Søke-endepunkt, bygger OpenSearch-spørringer                                                                                                                                                 |
| **rekrutteringstreff-indekser** (ny app)  | Tynn Kafka-konsument uten REST API. Skriver til OpenSearch basert på hendelser fra Rapids.                                                                                                   |
| **rekrutteringstreff-api** (eksisterende) | Eier databasen og indekseringskøen. Ved domeneendringer legges `treffId` i køen (i samme transaksjon). En scheduler bygger fulle søkedokumenter og publiserer dem på Rapids for indeksering. |

---

## Del 1: Frontend – søkeformat

Frontend skal sende hele søketilstanden i hver forespørsel. Fritekst søker på tvers av tittel, beskrivelse, innlegg og arbeidsgivernavn.

### Skisse

```
┌─────────────────────────────────────────────────────────────────────────┐
│  [Søk i rekrutteringstreff]                                             │
├─────────────────────┬───────────────────────────────────────────────────┤
│                     │  Aktive filtre: [Oslo] [Publisert] [Fjern alle]   │
│  Sorter             │                                                   │
│  ○ Sist oppdaterte  │  Tabs: [ Alle | Mine | Mitt kontor ]              │
│  ○ Nyeste           │                                                   │
│  ○ Eldste           │  ┌──────────────────────────────────────────────┐ │
│  ○ Aktive           │  │ Rekrutteringstreff for nyutdannede ...       │ │
│  ○ Fullførte        │  │ 24. mai 2026, kl 12:00                       │ │
│                     │  │ Ravinevegen 11                               │ │
│  Steder             │  │ Mitt oppdrag                                 │ │
│  ☐ Agder            │  └──────────────────────────────────────────────┘ │
│  ☐ Akershus         │  ┌──────────────────────────────────────────────┐ │
│  ☐ Buskerud         │  │ Rekrutteringstreff for nyutdannede ...       │ │
│                     │  │ 24. mai 2026                                 │ │
│  Treffstatus        │  │ Eies av Benjamin Hansen                      │ │
│  ☐ Utkast           │  └──────────────────────────────────────────────┘ │
│  ☐ Publisert        │                                                   │
│  ☐ Søknadsfrist     │                                                   │
│    passert          │                                                   │
│  ☐ Fullført         │                                                   │
│  ☐ Avlyst           │                         1-100 av 4000   < >       │
│                     │                                                   │
│                     │                                                   │
│  Kontor             │                                                   │
│  ☐ Agder            │                                                   │
│  ☐ Akershus         │                                                   │
└─────────────────────┴───────────────────────────────────────────────────┘
```

### Request

```kotlin
data class RekrutteringstreffSøkRequest(
    val fritekst: String? = null,
    val visningsstatuser: List<Visningsstatus>? = null,
    val fylkesnummer: List<String>? = null,
    val kommunenummer: List<String>? = null,
    val kontorer: List<String>? = null,
    val visning: Visning = Visning.ALLE,
    val sortering: Sortering = Sortering.SIST_OPPDATERTE,
    val side: Int = 0,
    val antallPerSide: Int = 20,
)

enum class Visning {
    ALLE,           // ingen ekstra filter
    MINE,           // eiere inneholder innlogget navident
    MITT_KONTOR,    // kontorer inneholder innlogget kontor
}

enum class Sortering {
    RELEVANS,           // _score – brukes når fritekst er satt
    SIST_OPPDATERTE,    // sistEndret desc – default uten fritekst
    NYESTE,             // opprettetAvTidspunkt desc
    ELDSTE,             // opprettetAvTidspunkt asc
    AKTIVE,             // fraTid asc, kun treff med status PUBLISERT og tilTid i fremtiden
    FULLFØRTE,          // tilTid desc, typisk brukt sammen med status FULLFØRT
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
    val kontorer: List<FilterValg>,
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
    val kommune: String?,
    val fylke: String?,
    val opprettetAvTidspunkt: ZonedDateTime,
    val sistEndret: ZonedDateTime,
    val antallArbeidsgivere: Int,
    val antallJobbsøkere: Int,
    val eiere: List<String>,
    val kontorer: List<String>,
)
```

### Visningsstatus

Statusene som vises i UI og brukes som filterverdier er:

- `Utkast`
- `Publisert`
- `Søknadsfrist passert`
- `Fullført`
- `Avlyst`

Backend-statuser i databasen er uendret:

- `UTKAST`
- `PUBLISERT`
- `FULLFØRT`
- `AVLYST`
- `SLETTET`

`SOKNADSFRIST_PASSERT` er **ikke** en domenestatus. Den avledes i OpenSearch-queryen som `status = PUBLISERT AND svarfrist < now`. Dette gir korrekt visningsstatus i sanntid uten scheduler, og unngår å utvide domenemodellen for et rent søke-behov.

```kotlin
enum class Visningsstatus {
    UTKAST,
    PUBLISERT,
    SOKNADSFRIST_PASSERT,
    FULLFORT,
    AVLYST,
}
```

| Visningsstatus         | Backend-status | OpenSearch-clause                                                  |
| ---------------------- | -------------- | ------------------------------------------------------------------ |
| `UTKAST`               | `UTKAST`       | `term` `status=UTKAST`                                             |
| `PUBLISERT`            | `PUBLISERT`    | `bool`: `term` `status=PUBLISERT` AND (`svarfrist >= now` OR null) |
| `SOKNADSFRIST_PASSERT` | `PUBLISERT`    | `bool`: `term` `status=PUBLISERT` AND `svarfrist < now`            |
| `FULLFORT`             | `FULLFØRT`     | `term` `status=FULLFØRT`                                           |
| `AVLYST`               | `AVLYST`       | `term` `status=AVLYST`                                             |

`SLETTET` filtreres alltid bort. Slettede treff fjernes også fysisk fra OpenSearch (se [Sletting](#sletting-fra-opensearch)).

### Endepunkt

```
POST /api/rekrutteringstreff/sok
```

---

## Del 2: Indekseringskø i `rekrutteringstreff-api`

Alle indekseringsrelevante endringer skal legge `treffId` i indekseringskøen.

Denne køen er for løpende endringer i normal drift. Den er ikke mekanismen som starter full reindeksering. Full reindeksering trigges av indekser-appen som kaller et internt REST-endepunkt på API-et (se [Reindeksering](#reindeksering)). Køen fanger opp løpende endringer som skjer mens fullscan pågår.

### Indekseringsutløsere

Følgende operasjoner må føre til ny rad i indekseringskøen:

| Kilde                | Operasjon                                                                | Påvirker felter i søkedokument                                               |
| -------------------- | ------------------------------------------------------------------------ | ---------------------------------------------------------------------------- |
| `rekrutteringstreff` | opprett, oppdater, publiser, avpubliser, gjenåpne, fullfør, avlys, slett | toppnivåfelter, status, tider, sted, `sistEndret`                            |
| `eier`               | legg til/fjern eier                                                      | `eiere`, indirekte tilgang i visning `MINE`                                  |
| `kontor`             | legg til kontor                                                          | `kontorer`, indirekte tilgang i visning `MITT_KONTOR`                        |
| `arbeidsgiver`       | legg til/fjern arbeidsgiver                                              | `arbeidsgivere`, `antallArbeidsgivere`                                       |
| `innlegg`            | opprett/oppdater/slett innlegg                                           | `innlegg`, fritekstgrunnlag                                                  |
| `jobbsøker`          | legg til, gjenopprett, slett, inviter, svar                              | `antallJobbsøkere` og eventuelle senere søkefelter basert på jobbsøkerstatus |

Det brukes ett eksplisitt «treff må reindekseres»-signal per `treffId`. Dokumentet bygges on demand fra databasen med én felles builder.

### Mønster: Event-basert indekseringskø (Outbox pattern)

Det legges inn en ny rad i køen for _hver_ endring, selv om det allerede finnes rader for samme `treffId`. Dette er et enklere mønster som fjerner all tvil rundt sortering, tabell-låsinger og rekkefølge (i motsetning til upsert-basert kø). Om det blir to identiske `treffId` i køen rett etter hverandre, har det i praksis null betydning (den bygger og sender dokumentet to ganger, OpenSearch er idempotent).

Det gjelder også mens full reindeksering pågår. Kontinuerlige endringer og full reindeksering kan skrive til samme pending-kø uten å trenge komplisert konflikt-håndtering.

| Tabell                           | Rolle                                                                            |
| -------------------------------- | -------------------------------------------------------------------------------- |
| `rekrutteringstreff_indeksering` | Pending-kø for treff som må bygges og indekseres på nytt etter løpende endringer |

**Anbefalte kolonner:**

| Kolonne               | Type                     | Beskrivelse                         |
| --------------------- | ------------------------ | ----------------------------------- |
| `id`                  | `serial` (PK)            | Unik auto-inkrement id for oppgaven |
| `treff_id`            | `uuid` (NOT NULL)        | Treff som skal reindekseres         |
| `opprettet_tidspunkt` | `timestamptz` (NOT NULL) | Når raden ble lagt i kø             |

**Flyt**

1. Ved en indekseringsrelevant endring skriver samme service en ny rad med `treffId` til `rekrutteringstreff_indeksering`
2. Innskriving gjøres i **samme database-transaksjon** som domeneendringen, med en vanlig `INSERT`. Dette er ukomplisert, robust og gir ikke feil ved parallelle oppdateringer. Scheduleren bygger uansett hele dokumentet på nytt når den plukker oppgaven.
3. En scheduler (med leader election eller kun en node, eller lockingi db) plukker pending `treffId`-er fra køen
4. For hvert `treffId`: Bygg det _fulle søkedokumentet_ (JSON) fra databasen. Send hendelse på Rapids med hele dokumentet, og slett deretter raden fra køen etter vellykket utsendelse.

Raden slettes etter vellykket sending. Sporbarhet ivaretas av `rekrutteringstreff_hendelse`-tabellene (som allerede logger alle domeneendringer) — indekseringskøen er kun en transient meldingskø, ikke en historikktabell.

**Transaksjonskrav:** Innlegging i `rekrutteringstreff_indeksering` må skje atomisk sammen med domeneendringen (i samme `executeInTransaction`-blokk). Dette garanterer at endringen plukkes opp for indeksering.

**Feiltoleranse:** Hvis appen krasjer mellom utsendelse på Rapids og sletting av kø-raden, vil identisk dokument bli lagt på køen igjen. Indekser-appen overskriver dokumentet i OpenSearch (idempotent via `index`-operasjon på treffets UUID).

**Rapids-hendelsen** inneholder ikke bare id-en, men den _komplette og ferdigbygde søkemodellen_ for det treffet. Det betyr at konsumenter (`rekrutteringstreff-indekser`) ikke trenger å gjøre database- eller REST-oppslag for å hente innholdet. All berikelse er unnagjort i `rekrutteringstreff-api`.

### Konsekvens for implementasjon

Alle relaterte moduler må ende i samme resultat: ett `treffId` som legges i indekseringskøen i samme transaksjon som domeneendringen. `rekrutteringstreff_hendelse` alene er ikke nok; planen må eksplisitt dekke relaterte domener.

---

## Del 3: Indekser-app (`rekrutteringstreff-indekser`)

Ny app under `rekrutteringstreff-backend/apps/rekrutteringstreff-indekser/`.

Appen følger samme mønster som `toi-stilling-indekser`: en tynn Kafka-konsument som indekserer ferdigbygde dokumenter til OpenSearch, med env-var-styrt reindeksering.

### Ansvar

- Lytte på Rapids-meldinger fra `rekrutteringstreff-api`.
- Mottar `@event_name: rekrutteringstreff.oppdatert`: upsert av ferdigbygd JSON-dokument til aktiv OpenSearch-indeks.
- Mottar `@event_name: rekrutteringstreff.slettet`: sletter dokumentet fra OpenSearch (se [Sletting](#sletting-fra-opensearch)).
- Håndtere reindeksering via dual-consumer-mønster (se [Reindeksering](#reindeksering)).

---

## Reindeksering

Reindeksering følger mønsteret fra `toi-stilling-indekser`: env-var-styrt, med dual-write under reindeksering og manuell alias-swap. Ingen admin-endepunkter eller admin-UI.

### Env-variabler

| Variabel             | Eksempel                | Beskrivelse                    |
| -------------------- | ----------------------- | ------------------------------ |
| `INDEKS_VERSJON`     | `rekrutteringstreff-v1` | Nåværende aktiv indeks (alias) |
| `REINDEKSER_ENABLED` | `true`                  | Aktiverer reindekseringsmodus  |
| `REINDEKSER_INDEKS`  | `rekrutteringstreff-v2` | Målindeks for reindeksering    |

### Flyten steg for steg

1. **Forbered**: Sett `REINDEKSER_ENABLED=true` og `REINDEKSER_INDEKS=rekrutteringstreff-v2` i nais-config. Deploy.
2. **Oppstart**: Appen oppdager at `REINDEKSER_INDEKS` ikke finnes i OpenSearch, oppretter den med mapping/settings fra koden.
3. **Trigger fullscan**: Indekser-appen kaller et internt REST-endepunkt på `rekrutteringstreff-api` (f.eks. `POST /internal/reindeksering/start`). API-et itererer porsjonsvis over alle treff, bygger komplett søkedokument med `TreffDokumentBuilder`, og publiserer hvert treff som `rekrutteringstreff.oppdatert` på Rapids.
4. **Dual-write**: Under reindeksering kjører appen to konsumenter parallelt:
   - Én som skriver til **gammel** indeks (`INDEKS_VERSJON`)
   - Én som skriver til **ny** indeks (`REINDEKSER_INDEKS`)
   - Begge prosesserer `rekrutteringstreff.oppdatert`-meldinger, slik at ingen oppdateringer går tapt.
5. **Verifiser (manuelt)**: Utvikler sjekker at ny indeks har forventet antall dokumenter (f.eks. via OpenSearch Dashboard eller API). Det finnes ingen automatisk "ferdig"-signalering — fullscan kjører og dual-write holder begge indeksene oppdatert i mellomtiden.
6. **Swap (deploy-drevet)**: Når utvikler er fornøyd, oppdateres `INDEKS_VERSJON=rekrutteringstreff-v2` i nais-config og deployes. Ved oppstart oppdager appen at alias peker på feil indeks og swapper automatisk.
7. **Rydd opp**: Sett `REINDEKSER_ENABLED=false`. Slett gammel indeks manuelt ved behov.

### Alias

Søke-appen leser fra et logisk alias (f.eks. `rekrutteringstreff`), aldri fra en spesifikk indeks. Alias-swap er atomisk i OpenSearch.

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
  "opprettetAvTidspunkt": "2026-03-02T10:00:00+01:00",
  "sistEndret": "2026-03-02T12:00:00+01:00",
  "eiere": ["Z993102", "Z990659"],
  "kontorer": ["0318", "0314"],
  "arbeidsgivere": [{ "orgnr": "912345678", "orgnavn": "Eksempel AS" }],
  "antallArbeidsgivere": 1,
  "antallJobbsøkere": 4,
  "innlegg": [{ "tittel": "Velkommen" }]
}
```

Alle feltene over skal forstås som et **denormalisert snapshot** av ett treff. Kilden er ikke bare `rekrutteringstreff`-tabellen, men også eier-, kontor-, arbeidsgiver-, innlegg- og jobbsøkerdata.

Geografi-feltene (`kommune`, `kommunenummer`, `fylke`, `fylkesnummer`) hentes fra rekrutteringstreffets eksisterende databasefelter. Se [Geografi-berikelse](#geografi-berikelse) for strategi for å sikre at disse alltid er utfylt.

### `sistEndret`

`sistEndret` er tidspunktet for siste databaseendring for treffet, satt innenfor transaksjonen som utfører endringen (eller så nærme som mulig etterpå). Enhver endring som trigger indekseringsutløser oppdaterer dette feltet.

### `all_text_no` (fritekst-felt)

I OpenSearch-mappingen defineres et `all_text_no`-felt med norsk analyzer som brukes til fritekst-søk. Følgende toppnivåfelter kopieres inn via `copy_to`: `tittel`, `beskrivelse`, `fylke`, `kommune`, `poststed`, `gateadresse`.

Nested-felter (`arbeidsgivere`, `innlegg`) støtter ikke `copy_to` til toppnivå i OpenSearch. Fritekst-søk i disse løses med eksplisitte nested-queries i query-builderen.

### Sletting fra OpenSearch

Når et treff slettes (`status = SLETTET`), skal dokumentet **fysisk fjernes** fra OpenSearch-indeksen. Dette gir en renere og mindre indeks over tid.

Flyten:

1. `rekrutteringstreff-api` markerer treffet som `SLETTET` i databasen.
2. I samme transaksjon legges `treffId` i indekseringskøen.
3. Scheduleren plukker opp raden, men i stedet for å bygge et fullt dokument, sender den en `@event_name: rekrutteringstreff.slettet`-melding på Rapids med bare `treffId`.
4. Indekser-appen mottar meldingen og kaller `client.delete()` med treffets UUID mot OpenSearch.

Dokumentbuilderen sjekker status: hvis `SLETTET`, skrives det _ingen_ søkedokument-melding – bare slette-meldingen sendes.

### Tilgangskontroll

Søke-endepunktet (`POST /api/rekrutteringstreff/sok`) krever autentisering og er tilgjengelig for rollene:

- `JOBBSØKERRETTET` (veileder)
- `ARBEIDSGIVERRETTET` (markedskontakt)
- `UTVIKLER`

Innlogget brukers navident og kontor leses fra tokenet via auth-klassen (som igjen henter fra Modia Context Holder). Disse brukes for `MINE`- og `MITT_KONTOR`-visningene.

### Forslag til appstruktur

```
apps/rekrutteringstreff-indekser/
├── build.gradle.kts
├── Dockerfile
├── nais-dev.yaml / nais-prod.yaml
├── opensearch.yaml
└── src/main/
    ├── resources/
    │   ├── treff-mapping.json       ← norsk analyzer, nested for arbeidsgivere/innlegg, copy_to for all_text_no
    │   └── treff-settings.json
    └── kotlin/no/nav/toi/rekrutteringstreff/indekser/
        ├── Application.kt            ← oppstart, reindekseringslogikk (env-var-sjekk)
        ├── OpenSearchConfig.kt
        ├── OpenSearchClient.kt        ← kommunikasjon med Aiven/OpenSearch (inkl. alias, delete)
        ├── TreffDokumentLytter.kt     ← Lytter på 'rekrutteringstreff.oppdatert'
        ├── TreffSlettetLytter.kt      ← Lytter på 'rekrutteringstreff.slettet'
        └── Liveness.kt
```

---

## Del 4: Søke-app (`rekrutteringstreff-søk`)

Ny app under `rekrutteringstreff-backend/apps/rekrutteringstreff-sok/` som eksponerer søke-endepunktet.

### Query builder

Controller tar imot `RekrutteringstreffSøkRequest`, bygger OpenSearch-query og returnerer `RekrutteringstreffSøkRespons`.

```
RekrutteringstreffSøkController
    ↓
RekrutteringstreffSøkService       ← bygger query, kaller klient
    ↓
OpenSearchKlient                   ← wrapper rundt opensearch-java
```

**Klient:** `opensearch-java` (offisiell Java-klient med typed DSL). Queryen bygges med `SearchRequest.Builder` og `BoolQuery.Builder`. Filtere implementeres som separate klasser med et felles `Filter`-interface, etter mønsteret i `rekrutteringsbistand-kandidatsok-api`:

```kotlin
typealias FilterFunksjon = BoolQuery.Builder.() -> ObjectBuilder<BoolQuery>

interface Filter {
    fun erAktiv(): Boolean
    fun lagQuery(): FilterFunksjon
}
```

Hvert filter (fritekst, status, geografi, visning) er en egen klasse som avgjør om den er aktiv basert på requesten, og legger til `must`/`filter`-clauses på `BoolQuery.Builder`. Queryen komponeres ved å kjede aktive filtere.

### Viktig avgrensning mot dagens GET-endepunkter

Søke-appen erstatter oversiktslisten, ikke alle eksisterende GET-endepunkter.

Plan:

1. Ny oversiktsliste i frontend flyttes til `POST /api/rekrutteringstreff/sok`.
2. Eksisterende detaljendepunkter beholdes uendret i første fase.
3. Full reindeksering bygger dokumentene i `rekrutteringstreff-api` med `TreffDokumentBuilder` og publiserer dem på Rapids. Indekseren konsumerer derfra.

### Filtre og OpenSearch-clauses

| Filter             | OpenSearch-clause                                                                                                           |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------- |
| `fritekst`         | `match` på `all_text_no` + nested `match` på `arbeidsgivere.orgnr`, `arbeidsgivere.orgnavn` og `innlegg.tittel` (lav boost) |
| `visningsstatuser` | `filters`-aggregering. `SOKNADSFRIST_PASSERT` avledes som `status=PUBLISERT AND svarfrist < now` (se Visningsstatus-tabell) |
| `fylkesnummer`     | `terms` på `fylkesnummer`                                                                                                   |
| `kommunenummer`    | `terms` på `kommunenummer`                                                                                                  |
| `kontorer`         | `terms` på `kontorer`                                                                                                       |
| `MINE`             | `term` på `eiere` = innlogget navident                                                                                      |
| `MITT_KONTOR`      | `term` på `kontorer` = innlogget kontor                                                                                     |

`fritekst` legges i `must`, alle andre i `filter`.

---

## Geografi-berikelse

Databasen har allerede feltene `kommune`, `kommunenummer`, `fylke` og `fylkesnummer` på rekrutteringstreff. Disse brukes direkte i søkedokumentet og i geografi-filteret. Utfordringen er at feltene er fritekst og ikke nødvendigvis alltid utfylt.

For at geografi-filtrene skal fungere pålitelig, må kommune/fylke alltid være populert.

### Strategi: PAM geografi-tjeneste

Nav har en intern geografi-tjeneste (brukt av bl.a. `toi-geografi`) som mapper postnummer til kommune/fylke via REST (`PAM_GEOGRAFI_URL`). Tjenesten er alltid oppdatert og brukes allerede av andre Nav-team.

- **Implementasjon**: `rekrutteringstreff-api` kaller PAM geografi-tjenesten ved opprettelse/oppdatering av et treff med postnummer, og setter `kommunenummer`, `kommune`, `fylkesnummer` og `fylke` automatisk.
- For eksisterende treff uten kommune/fylke kan en engangsmigrasjon berike basert på postnummeret.

---

## Foreslått førsteversjon av OpenSearch settings og mapping

Dette er et konkret utgangspunkt for `apps/rekrutteringstreff-indekser/src/main/resources/treff-settings.json` og `treff-mapping.json`.

### `treff-settings.json` (forslag)

> `norwegian`-analysatoren er innebygd i OpenSearch og trenger ikke defineres her. `norwegian_html` (egendefinert med `html_strip`) brukes av `all_text_no` som sikkerhetsnett for eventuell HTML-markering.

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
    "kontorer": {
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
    "fylke": {
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
    "kommune": {
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
      "analyzer": "norwegian",
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
- `copy_to` er utelatt fra nested-felter (`arbeidsgivere`, `innlegg`) fordi OpenSearch ikke støtter `copy_to` fra nested til toppnivå.
- Aggregeringer (antall per fylke, status og navkontor) trengs for å vise tall i filterpanelet. Disse bygges som `terms`-aggregeringer på `keyword`-feltene i mapping.

---

## Risiko

| Risiko                        | Fiks.                                                                                                                                                    |
| ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Indeks og database ut av synk | Full reindeksering som fallback. Monitorer alder og antall rader i `rekrutteringstreff_indeksering`.                                                     |
| OpenSearch utilgjengelig      | Returner feilmelding (503). Ingen fallback til gammelt endepunkt – risikoen for å vise feil data/statuser er for høy.                                    |
| Query-ytelse                  | Start enkelt, profiler med reelle data, juster boost-verdier.                                                                                            |
| Kafka-volum ved reindeksering | Bakgrunnsjobben sender mange meldinger raskt. Porsjonering med throttling. Sjekk topic-retensjon og partisjonering før første kjøring.                   |
| Nested query-ytelse           | `arbeidsgivere` og `innlegg` som nested-felter krever nested queries for fritekst, som er tregere enn flat struktur. Profiler med realistisk datamengde. |

---

## TODO

### Oppgave 1: Indekseringskø (Outbox) i rekrutteringstreff-api

- [ ] Opprett Flyway-migrasjon for `rekrutteringstreff_indeksering`
- [ ] Legg kø-innskriving inn i samme transaksjon som alle indekseringsrelevante domeneendringer
- [ ] Implementer scheduler/worker som plukker pending `treffId`-er og publiserer melding
- [ ] Slett kø-rad etter vellykket sending
- [ ] Implementer `TreffDokumentBuilder` som bygger komplett søkedokument fra databasen
- [ ] Scheduler må skille status `SLETTET` (send `rekrutteringstreff.slettet`) fra andre statuser (bygg fullt dokument med `TreffDokumentBuilder` og send `rekrutteringstreff.oppdatert`)
- [ ] Legg til tester for rollback og idempotent resend

### Oppgave 2: Reindekserings-støtte (rekrutteringstreff-api)

- [ ] Implementer internt REST-endepunkt (`POST /internal/reindeksering/start`) som porsjonsvis publiserer alle treff på Rapids (bruker `TreffDokumentBuilder` fra Oppgave 1)
- [ ] Endepunktet skal kun være tilgjengelig internt (nais ingress)

### Oppgave 3: Geografi-berikelse med PAM geografi-tjeneste

- [ ] Integrer `rekrutteringstreff-api` med PAM geografi-tjeneste (`PAM_GEOGRAFI_URL`)
- [ ] Ved opprettelse/oppdatering av treff med postnummer: sett `kommunenummer`, `kommune`, `fylkesnummer`, `fylke` automatisk
- [ ] Engangsmigrasjon for eksisterende treff uten kommune/fylke

### Oppgave 4: Indekser-app (den tynne konsumenten)

- [ ] Opprett `rekrutteringstreff-indekser`-modul
- [ ] Sett opp Aiven/OpenSearch (`opensearch.yaml`, `nais.yaml` og nødvendige dev/prod envs)
- [ ] Legg inn mapping og settings i resources
- [ ] Implementer lytter for `rekrutteringstreff.oppdatert` (upsert til OpenSearch)
- [ ] Implementer lytter for `rekrutteringstreff.slettet` (fysisk sletting fra OpenSearch)
- [ ] Implementer env-var-styrt reindeksering med dual-consumer (`INDEKS_VERSJON`, `REINDEKSER_INDEKS`)
- [ ] Implementer alias-håndtering og opprettelse av ny indeks ved reindeksering
- [ ] Kall `POST /internal/reindeksering/start` på rekrutteringstreff-api ved oppstart med `REINDEKSER_ENABLED=true`
- [ ] Verifiser ende-til-ende i dev

### Oppgave 5: Søke-app

- [ ] Opprett `rekrutteringstreff-sok`-modul
- [ ] Implementer query builder for fritekst, status, sortering, paginering og visning
- [ ] Implementer sorteringslogikk for alle 6 alternativer (RELEVANS, SIST_OPPDATERTE, NYESTE, ELDSTE, AKTIVE, FULLFØRTE)
- [ ] Legg til geografi-filtre
- [ ] Legg til rollevalidering for `ALLE`, `MINE` og `MITT_KONTOR`
- [ ] Implementer aggregeringer for status, fylke og kontor
- [ ] Legg til komponenttester med OpenSearch Testcontainers

### Oppgave 6: Frontend

> I dag henter `useRekrutteringstreffOversikt` hele listen uten query-parametre, og all filtrering/sortering skjer klientsiden i `RekrutteringstreffSøk.tsx` med `useMemo`. Denne oppgaven bytter til server-side søk.
>
> **Uberørt i denne oppgaven:** Detaljvisning (`GET /api/rekrutteringstreff/{id}`), opprettelse, redigering, publisering, avlysing, fullføring, eier-/arbeidsgiver-/jobbsøker-endringer — alt som skriver til `rekrutteringstreff-api` — beholder eksisterende endepunkter og hooks.

**Datahenting**

- [ ] Nytt SWR-hook `useRekrutteringstreffSøk` som kaller `POST /api/rekrutteringstreff/sok`
- [ ] Synk alle søkeparametre til URL query-params (fritekst, visningsstatuser, sortering, visning, side) slik at søk er delbart og bokmerkvennlig

**Tabs (visning)**

- [ ] Implementer tab-rad med `Alle`, `Mine`, `Mitt kontor` — mapper til `visning`-parameteret i requesten

**Fritekst**

- [ ] Søkefelt som sender `fritekst`-parameteret — søker på tvers av tittel, beskrivelse, innlegg og arbeidsgivernavn

**Filter og sortering**

- [ ] Statusfilter (checkboxes): `Utkast`, `Publisert`, `Søknadsfrist passert`, `Fullført`, `Avlyst`
- [ ] Sortering (radio): sist oppdaterte, nyeste, eldste, aktive, fullførte (+ relevans når fritekst er satt)
- [ ] Aktiver geografi- og kontorfiltre (UI-komponentene finnes allerede, men er deaktivert)
- [ ] Vis antall treff per filterverdi fra `aggregeringer` i responsen (f.eks. «Publisert (42)»)
- [ ] Vis aktive filter-chips med «Fjern alle»-knapp over resultatlisten

**Paginering**

- [ ] Vis «1–20 av N» med forrige/neste-knapper, basert på `totaltAntall`, `side` og `antallPerSide` fra responsen

**Treffkort**

- [ ] Vis tittel, dato/klokkeslett (`fraTid`), adresse, og eierskap («Mitt oppdrag» hvis innlogget er eier, ellers «Eies av [navn]»)

**Opprydding**

- [ ] Fjern klientside-filtrering og sortering i `RekrutteringstreffSøk.tsx` (`useMemo`-logikken)
- [ ] Fjern `useRekrutteringstreffOversikt` når ny flyt er verifisert
