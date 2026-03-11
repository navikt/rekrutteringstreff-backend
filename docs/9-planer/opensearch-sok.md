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

| Komponent                                 | Ansvar                                                                                                  |
| ----------------------------------------- | ------------------------------------------------------------------------------------------------------- |
| **Frontend**                              | Sender søkeparametere, viser paginerte resultater. Har også en egen minside for å trigge reindeksering. |
| **rekrutteringstreff-søk** (ny app)       | Søke-endepunkt, bygger OpenSearch-spørringer                                                            |
| **rekrutteringstreff-indekser** (ny app)  | Tynn Kafka-konsument uten REST API. Skriver til OpenSearch basert på hendelser fra Rapids.              |
| **rekrutteringstreff-api** (eksisterende) | Eier databasen, bygger de fulle søkedokumentene, og styrer rutiner for reindeksering.                   |

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
    AKTIVE,             // fraTid asc, kun treff med status PUBLISERT eller SOKNADSFRIST_PASSERT og tilTid i fremtiden
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

Statusene som skal brukes gjennomgående i løsning og UI er:

- `Utkast`
- `Publisert`
- `Søknadsfrist passert`
- `Fullført`
- `Avlyst`

Faktiske treffstatuser i backend i dag er:

- `UTKAST`
- `PUBLISERT`
- `FULLFØRT`
- `AVLYST`
- `SLETTET`

Planen forutsetter at backend utvides med en ny domenestatus `SOKNADSFRIST_PASSERT`, slik at filterstatusene over kan brukes direkte i stedet for å avledes i søket.

`AVPUBLISERT` og `GJENÅPNET` er fortsatt hendelser, ikke statuser.

```kotlin
enum class Visningsstatus {
    UTKAST,
    PUBLISERT,
    SOKNADSFRIST_PASSERT,
    FULLFORT,
    AVLYST,
}
```

| Visningsstatus         | Backend-status         | Regel                                                             |
| ---------------------- | ---------------------- | ----------------------------------------------------------------- |
| `UTKAST`               | `UTKAST`               | Direkte                                                           |
| `PUBLISERT`            | `PUBLISERT`            | Direkte                                                           |
| `SOKNADSFRIST_PASSERT` | `SOKNADSFRIST_PASSERT` | Settes av scheduler når `status = PUBLISERT` og `svarfrist < now` |
| `FULLFORT`             | `FULLFØRT`             | Direkte                                                           |
| `AVLYST`               | `AVLYST`               | Direkte                                                           |

`SLETTET` filtreres alltid bort.

### Endepunkt

```
POST /api/rekrutteringstreff/sok
```

---

## Del 2: Indekseringskø i `rekrutteringstreff-api`

Alle indekseringsrelevante endringer skal legge `treffId` i en komprimert indekseringskø.

Denne køen er for løpende endringer i normal drift. Den er ikke mekanismen som starter full reindeksering. Full reindeksering startes eksplisitt via admin-endepunktet i indekser-appen, men bruker den samme køen til catch-up av endringer som skjer mens fullscan pågår.

### Indekseringsutløsere

Følgende operasjoner må føre til ny eller oppdatert melding til indekseren:

| Kilde                | Operasjon                                                                | Påvirker felter i søkedokument                                               |
| -------------------- | ------------------------------------------------------------------------ | ---------------------------------------------------------------------------- |
| `rekrutteringstreff` | opprett, oppdater, publiser, avpubliser, gjenåpne, fullfør, avlys, slett | toppnivåfelter, status, tider, sted, `sistEndret`                            |
| `scheduler`          | overgang fra `PUBLISERT` til `SOKNADSFRIST_PASSERT`                      | `status`, `sistEndret`                                                       |
| `eier`               | legg til/fjern eier                                                      | `eiere`, indirekte tilgang i visning `MINE`                                  |
| `kontor`             | legg til kontor                                                          | `kontorer`, indirekte tilgang i visning `MITT_KONTOR`                        |
| `arbeidsgiver`       | legg til/fjern arbeidsgiver                                              | `arbeidsgivere`, `antallArbeidsgivere`                                       |
| `innlegg`            | opprett/oppdater/slett innlegg                                           | `innlegg`, fritekstgrunnlag                                                  |
| `jobbsøker`          | legg til, gjenopprett, slett, inviter, svar                              | `antallJobbsøkere` og eventuelle senere søkefelter basert på jobbsøkerstatus |

Det brukes ett eksplisitt «treff må reindekseres»-signal per `treffId`. Dokumentet bygges on demand fra databasen med én felles builder.

### Mønster: komprimert indekseringskø per `treffId`

Det skal bare kunne finnes én ventende rad per `treffId`. Hvis treffet allerede ligger i køen, oppdateres raden i stedet for å opprette en ny.

Det gjelder også mens full reindeksering pågår. Kontinuerlige endringer og full reindeksering skal samarbeide via samme pending-kø: hvis et `treffId` allerede er uprosessert, skal vi ikke legge inn en ny rad, bare oppdatere `sist_endret_tidspunkt`.

| Tabell                           | Rolle                                                                            |
| -------------------------------- | -------------------------------------------------------------------------------- |
| `rekrutteringstreff_indeksering` | Pending-kø for treff som må bygges og indekseres på nytt etter løpende endringer |

**Anbefalte kolonner:**

| Kolonne                 | Type                     | Beskrivelse                     |
| ----------------------- | ------------------------ | ------------------------------- |
| `treff_id`              | `uuid` (PK)              | Treff som skal reindekseres     |
| `opprettet_tidspunkt`   | `timestamptz` (NOT NULL) | Når treffet først ble lagt i kø |
| `sist_endret_tidspunkt` | `timestamptz` (NOT NULL) | Når kø-raden sist ble berørt    |

**Flyt**

1. Ved en indekseringsrelevant endring skriver samme service `treffId` til `rekrutteringstreff_indeksering`
2. Innskriving gjøres i **samme database-transaksjon** som domeneendringen, med `insert ... on conflict (treff_id) do update set sist_endret_tidspunkt = now()`
3. Hvis transaksjonen rollbackes, rollbackes også kø-innskrivingen
4. En scheduler (med leader election eller kun en node, eller lockingi db) plukker pending `treffId`-er fra køen
5. For hvert `treffId`: Bygg det _fulle søkedokumentet_ (JSON) fra databasen. Send hendelse på Rapids med hele dokumentet, og slett deretter raden fra køen etter vellykket utsendelse.

Raden bør slettes etter vellykket sending. `rekrutteringstreff_indeksering` er en pending-kø, ikke en historikktabell. Et `fullfort`-flagg vil gi mer opprydding, mer filtrering og større tabell uten å gi bedre robusthet i selve kømekanismen.

**Transaksjonskrav:** Innlegging i `rekrutteringstreff_indeksering` må skje atomisk sammen med domeneendringen (i samme `executeInTransaction`-blokk). Dette garanterer at endringen plukkes opp for indeksering.

**Feiltoleranse:** Hvis appen krasjer mellom utsendelse på Rapids og sletting av kø-raden, vil identisk dokument bli lagt på køen igjen. Indekser-appen overskriver dokumentet i OpenSearch (idempotent via `index`-operasjon på treffets UUID).

**Rapids-hendelsen** inneholder ikke bare id-en, men den _komplette og ferdigbygde søkemodellen_ for det treffet. Det betyr at konsumenter (`rekrutteringstreff-indekser`) ikke trenger å gjøre database- eller REST-oppslag for å hente innholdet. All berikelse er unnagjort i `rekrutteringstreff-api`.

### Konsekvens for implementasjon

Alle relaterte moduler må ende i samme resultat: ett `treffId` som legges i indekseringskøen i samme transaksjon som domeneendringen. `rekrutteringstreff_hendelse` alene er ikke nok; planen må eksplisitt dekke relaterte domener.

---

## Del 3: Indekser-app (`rekrutteringstreff-indekser`)

Ny app under `rekrutteringstreff-backend/apps/rekrutteringstreff-indekser/`.

Dette er en **"tynn" applikasjon**. Den har **ingen REST-endepunkter** eller admin-grensesnitt. Den er en ren Kafka-konsument som gjør én ting: omsetter konvensjonsstyrte JSON-meldinger på Rapids til operasjoner i OpenSearch.

### Ansvar

- Lytte på Rapids-meldinger fra `rekrutteringstreff-api`.
- Mottar `@event_name: rekrutteringstreff.oppdatert`: tar JSON-modellen (som allerede er ferdig utledet med eier, sted, fylke, etc.) og u-betinget `upsert`'er den til aktiv OpenSearch-indeks (samt evt. målindeks hvis indeksering pågår).
- Mottar `@event_name: reindeksering.start` med konfigurasjon (navn på ny indeks): klargjør og oppretter rykende fersk indeks i OpenSearch med riktig mapping og settings, og aktiverer dual-write lokalt.
- Mottar `@event_name: reindeksering.dokument`: Lagrer opphentet dokument direkte i ny målindeks (utenfor alias).
- Mottar `@event_name: reindeksering.ferdig`: utfører alias-swap mot OpenSearch i bakkant og muliggjør zero-downtime cutover. Deretter skrur den av dual-write mode.

---

## Reindeksering og Admin-API (i `rekrutteringstreff-api`)

I stedet for at indekseren trekker data fra databasen, er det **`rekrutteringstreff-api`** som eier reindekseringsprosessen og skyver ferdigbygde dokumenter og status-kommandoer over Kafka (Rapids).

Dette eksponeres i API-et via egne admin-endepunkter:

```text
POST /api/internal/reindeksering/start
GET /api/internal/reindeksering/status
```

Denne API-funksjonaliteten vil bli brukt fra **Frontend**, på en egen mini-side for utviklere/admin med "Start reindeksering"-knapp + bekreftelsesdialog, pluss visning av status for indekseringen. Tilgang styres med `UTVIKLER`-rollen (samme rolle som allerede brukes for utviklertilgang i API-et). Eksakt hvor denne inngangen skal bo avklares senere.

### Reindekseringsflyt og Alias-bytte uten nedetid (Zero-downtime cutover)

Reindeksering skjer helt uten nedetid. Dette løses i OpenSearch ved bruk av logiske alias. Søke-appen (klientene) peker aldri direkte på en spesifikk indeks (f.eks. `rekrutteringstreff-v1`), men på et logisk alias (f.eks. `rekrutteringstreff-alias`).

Prosedyren med hendelser på Kafka ser slik ut:

1. **Start**: Reindekseringen trigges via frontend-minisiden som kaller `POST /api/internal/reindeksering/start`.
2. **Klargjøring** (`@event_name: reindeksering.start`):
   - API-et genererer et unikt målindeksnavn (f.eks. timestampbasert: `rekrutteringstreff-20261103-1430`) og sender det på køen.
   - Indekser-appen mottar dette, kontakter OpenSearch og oppretter den _nye_ indeksen med ferske mappinger/settings fra koden.
   - Indekser-appen setter seg i lokal "reindeksering pågår"-tilstand under dette indeksnavnet. (Dette prepper for dual-write).
3. **Fullscan pluss historikk-pumping** (`@event_name: reindeksering.dokument`):
   - API-et starter en asynkron bakgrunnsjobb som porsjonsvis itererer over _alle_ treff fra databasen.
   - For hvert treff bygger API-et det komplette JSON-søkedokumentet, og sender det over Rapids. Indekseren legger dem rett inn i den nye (og inntil videre kalde) målindeksen.
4. **Dual-write underveis** (`@event_name: rekrutteringstreff.oppdatert`):
   - Siden databasetømmingen kan ta noe tid, vil eventuelle vanlige oppdateringshendelser fra brukere sendes og fanges opp samtidig.
   - Ettersom indekseren er i "reindekserings-modus" (fra punkt 2) rutes endringene til _både_ nåværende aktiv indeks (via alias) og den nyopprettede målindeksen, slik at de inntil videre holdes i sync.
5. **Selve byttet** (`@event_name: reindeksering.ferdig`):
   - Når API-et er 100% ferdig med utkastelsen av databasen, sender API-et en "ferdig"-kommando over Rapids.
   - Indekser-appen sender da en `/aliases` POST request til OpenSearch som sier: _Fjern det gamle indeks-navnet fra aliaset og legg inn den nye målindeksen i aliaset_.
   - OpenSearch gjør denne peker-omkoblingen 100% atomisk. Etterfølgende søk rutes umiddelbart til den nye og ferdig-populerte indeksen, og oppdateringen er live.
6. **Opprydding (fremtidig forbedring, pt. manuell)**: Dual-write avsluttes. Gamle utilknyttede OpenSearch-indekser fjernes. Dette gjøres enten direkte i reindeksering-konsumenten eller plukkes opp ved en definert ILM/Lifecycle.

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
  "innlegg": [
    { "tittel": "Velkommen", "tekstinnhold": "Ren tekst, for fritekst-søk" }
  ]
}
```

Alle feltene over skal forstås som et **denormalisert snapshot** av ett treff. Her betyr snapshot bare "full dokumentrepresentasjon akkurat nå". Det er ikke et krav om å lagre dette i en egen tabell. Kilden er ikke bare `rekrutteringstreff`-tabellen, men også eier-, kontor-, arbeidsgiver-, innlegg- og jobbsøkerdata.

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
        ├── OpenSearchClient.kt        ← kommunikasjon med aiven/OpenSearch (inkl alias)
        ├── TreffDokumentLytter.kt     ← Lytter på 'rekrutteringstreff.oppdatert' mm.
        ├── ReindekseringLytter.kt     ← Lytter på 'reindeksering.start/ferdig/dokument'
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

### Viktig avgrensning mot dagens GET-endepunkter

Søke-appen erstatter oversiktslisten, ikke alle eksisterende GET-endepunkter.

Plan:

1. Ny oversiktsliste i frontend flyttes til `POST /api/rekrutteringstreff/sok`.
2. Eksisterende detaljendepunkter beholdes uendret i første fase.
3. Full reindeksering kan ikke baseres på dagens eksisterende listeendepunkter alene, siden de ikke returnerer hele søkedokumentet.

Full reindeksering bygger dokumentene direkte fra databasen i indekseren, med samme builder som ved inkrementelle oppdateringer.

### Filtre og OpenSearch-clauses

| Filter             | OpenSearch-clause                                                                                                            |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------- |
| `fritekst`         | `match` på `all_text_no` + nested `match` på `arbeidsgivere.orgnavn`, `innlegg.tittel` og `innlegg.tekstinnhold` (lav boost) |
| `visningsstatuser` | `terms` på `status`. `SOKNADSFRIST_PASSERT` er en egen status satt av scheduler.                                             |
| `fylkesnummer`     | `terms` på `fylkesnummer`                                                                                                    |
| `kommunenummer`    | `terms` på `kommunenummer`                                                                                                   |
| `kontorer`         | `terms` på `kontorer`                                                                                                        |
| `MINE`             | `term` på `eiere` = innlogget navident                                                                                       |
| `MITT_KONTOR`      | `term` på `kontorer` = innlogget kontor                                                                                      |

`SLETTET`-status filtreres alltid bort (`must_not` `term` `status=SLETTET`).

`fritekst` legges i `must`, alle andre i `filter`.

Fritekst søker på `all_text_no` for toppnivåfelter og egne nested-queries for `arbeidsgivere` og `innlegg`.

---

## Statusoverganger

Planen legger til én ny domenestatus: `SOKNADSFRIST_PASSERT`.

### Komplett statusmodell

| Fra status             | Trigger                      | Til status             |
| ---------------------- | ---------------------------- | ---------------------- |
| `UTKAST`               | manuell publisering          | `PUBLISERT`            |
| `UTKAST`               | sletting (ingen jobbsøkere)  | `SLETTET`              |
| `PUBLISERT`            | scheduler: `svarfrist < now` | `SOKNADSFRIST_PASSERT` |
| `PUBLISERT`            | manuell fullføring           | `FULLFØRT`             |
| `PUBLISERT`            | manuell avlysning            | `AVLYST`               |
| `SOKNADSFRIST_PASSERT` | manuell fullføring           | `FULLFØRT`             |
| `SOKNADSFRIST_PASSERT` | manuell avlysning            | `AVLYST`               |
| `FULLFØRT` / `AVLYST`  | gjenåpning                   | `PUBLISERT`            |

> **Merk:** Avpublisering (`PUBLISERT → UTKAST`) finnes i koden i dag, men det er uavklart om dette skal videreføres. Foreløpig tas den ikke med i den planlagte statusmodellen.

### Nye overganger innført av denne planen

De nye radene i tabellen over er:

| Fra status             | Trigger                      | Til status             |
| ---------------------- | ---------------------------- | ---------------------- |
| `PUBLISERT`            | scheduler: `svarfrist < now` | `SOKNADSFRIST_PASSERT` |
| `SOKNADSFRIST_PASSERT` | manuell fullføring           | `FULLFØRT`             |
| `SOKNADSFRIST_PASSERT` | manuell avlysning            | `AVLYST`               |

Dette krever:

- En scheduler som periodisk finner treff med `status = PUBLISERT` og `svarfrist < now`, oppdaterer status til `SOKNADSFRIST_PASSERT`, og legger `treffId` i indekseringskøen i samme transaksjon.
- At `fullfør()` og `avlys()` i `RekrutteringstreffService` oppdateres til å akseptere `SOKNADSFRIST_PASSERT` i tillegg til `PUBLISERT`.

---

## Oppgaver

Rekkefølgen er foreslått, men hver oppgave beskriver et selvstendig leverbart steg.

### Oppgave 1: Komprimert indekseringskø i rekrutteringstreff-api

1. Opprett `rekrutteringstreff_indeksering`-tabell (Flyway-migrasjon) med `treff_id` som primærnøkkel
2. Legg alle indekseringsrelevante endringer inn i køen i samme transaksjon som domeneendringen, med `insert ... on conflict do update`
3. Scheduler plukker pending `treffId`-er fra køen
4. Scheduler sender Rapids-melding med `treffId` og sletter raden etter vellykket sending
5. Legg til tester som verifiserer at domeneendring og kø-innskriving rollbackes samlet ved feil

### Oppgave 2: Statusovergang for søknadsfrist passert

1. Innfør ny domenestatus `SOKNADSFRIST_PASSERT`
2. Implementer scheduler som finner treff med `status = PUBLISERT` og `svarfrist < now`
3. Oppdater status og legg `treffId` i indekseringskøen i samme transaksjon
4. Avklar og implementer hvilke manuelle overganger som skal være tillatt fra `SOKNADSFRIST_PASSERT`
5. Legg til tester for scheduler, statusovergang og reindeksering

### Oppgave 3: Reindekserings-håndtering (rekrutteringstreff-api)

1. Implementer databaselogikken for å bygge et komplett søkedokument (`TreffDokumentBuilder` e.l.).
2. Implementer publisering over Rapids av oppdaterte, komplette dokument-JSON-modeller.
3. Legg til admin-endepunkter (`/api/internal/reindeksering/start` og `/status`) bak tilgangskontroll.
4. Implementer bakgrunnsjobben (fullscan av database) som publiserer `reindeksering.dokument`-meldinger.

### Oppgave 4: Indekser-app (den tynne konsumenten)

1. Opprett `rekrutteringstreff-indekser`-modul.
2. Definer mapping og settings (norsk analyzer, nested for arbeidsgivere og innlegg).
3. Sett opp Aiven/OpenSearch (`opensearch.yaml`, `nais.yaml`).
4. Implementer lytter for `rekrutteringstreff.oppdatert` (vanlig indeksering).
5. Implementer lyttere og logikk for reindekseringshendelsene over Rapids.
6. Deploy til dev, verifiser flyten.

### Oppgave 5: Søke-app

1. Opprett søke-app/-modul med OpenSearch lesekonfig
2. Implementer query builder for fritekst, status, paginering og visning
3. Legg til geografi-filtre
4. Legg til visning-filter og rollevalidering (ALLE / MINE / MITT_KONTOR)
5. Komponenttester med OpenSearch Testcontainers

Hvis dagens brukeropplevelse skal bevares, må query-builderen følge dagens regler. Hvis ikke, må dette avklares som funksjonell endring.

### Oppgave 6: Frontend

1. Bytt kun oversiktsvisningen for rekrutteringstreff til nytt søke-endepunkt.
2. Behold detaljvisning, mutasjoner og hjelpelister på eksisterende endepunkter i første omgang.
3. La query-parametre i frontend speile `RekrutteringstreffSøkRequest`, slik at URL og backend-modell samsvarer.
4. Innfør egne komponent- og integrasjonstester for rolle × visning × filterkombinasjoner før gammel klientfiltrering fjernes.

---

## Foreslått førsteversjon av OpenSearch settings og mapping

Dette er et konkret utgangspunkt for `apps/rekrutteringstreff-indekser/src/main/resources/treff-settings.json` og `treff-mapping.json`.

### `treff-settings.json` (forslag)

> `norwegian`-analysatoren er innebygd i OpenSearch og trenger ikke defineres her. Kun `norwegian_html` (egendefinert med `html_strip`) defineres eksplisitt.

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
- Aggregeringer (antall per fylke, status og navkontor) trengs for å vise tall i filterpanelet. Disse bygges som `terms`-aggregeringer på `keyword`-feltene i mapping.

---

## Risiko

| Risiko                         | Avbøting                                                                                                                                                      |
| ------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Indeks og database ut av synk  | Full reindeksering som fallback. Monitorer alder og antall rader i `rekrutteringstreff_indeksering`.                                                          |
| OpenSearch utilgjengelig       | Returner feilmelding (503). Ingen fallback til gammelt endepunkt – risikoen for å vise feil data/statuser er for høy.                                         |
| Query-ytelse                   | Start enkelt, profiler med reelle data, juster boost-verdier.                                                                                                 |
| Statusovergang på tid          | Scheduler må kjøre stabilt og oppdatere både domenestatus og indekseringskø. Overvåk antall treff som står for lenge i `PUBLISERT` etter passert `svarfrist`. |
| Ny status påvirker andre apper | `SOKNADSFRIST_PASSERT` må håndteres i MinSide-API, aktivitetskort-lytter og begge frontender. Kartlegg alle steder som matcher på `RekrutteringstreffStatus`. |
| Kafka-volum ved reindeksering  | Bakgrunnsjobben sender mange meldinger raskt. Porsjonering med throttling. Sjekk topic-retensjon og partisjonering før første kjøring.                        |
| Nested query-ytelse            | `arbeidsgivere` og `innlegg` som nested-felter krever nested queries for fritekst, som er tregere enn flat struktur. Profiler med realistisk datamengde.      |

---

## TODO

### Oppgave 1: Statusovergang for søknadsfrist passert

- [ ] Innfør ny domenestatus `SOKNADSFRIST_PASSERT`
- [ ] Implementer scheduler som finner `PUBLISERT` med passert `svarfrist`
- [ ] Oppdater status og legg `treffId` i reindekseringskøen i samme transaksjon
- [ ] Avklar og implementer lovlige manuelle overganger fra `SOKNADSFRIST_PASSERT`
- [ ] Legg til tester for statusovergang og reindeksering

### Oppgave 2: Komprimert indekseringskø i rekrutteringstreff-api

- [ ] Opprett Flyway-migrasjon for `rekrutteringstreff_indeksering`
- [ ] Legg kø-innskriving inn i samme transaksjon som alle indekseringsrelevante domeneendringer
- [ ] Implementer scheduler/worker som plukker pending `treffId`-er og publiserer melding
- [ ] Slett kø-rad etter vellykket sending
- [ ] Legg til tester for rollback, deduplisering og idempotent resend

### Oppgave 3: Reindekserings-håndtering (rekrutteringstreff-api)

- [ ] Implementer bygging av fullverdig dokument (`TreffDokumentBuilder`) og pakking i Rapids-melding
- [ ] Implementer `POST /api/internal/reindeksering/start` og `GET /api/internal/reindeksering/status` i API-et
- [ ] Definer logikk/bakgrunnsjobb for full databasetømming og utsending av `reindeksering.dokument`
- [ ] Styr livssyklusen via hendelser på Rapids (`reindeksering.start`, `reindeksering.ferdig`)

### Oppgave 4: Indekser-app (den tynne konsumenten)

- [ ] Opprett `rekrutteringstreff-indekser`-modul
- [ ] Sett opp Aiven/OpenSearch (`opensearch.yaml`, `nais.yaml` og nødvendige dev/prod envs)
- [ ] Legg inn mapping og settings i resources
- [ ] Implementer lytter for vanlige domeneoppdateringer (`rekrutteringstreff.oppdatert`)
- [ ] Implementer lyttere for innkommende reindekserings-kommandoer (start, dokument, ferdig)
- [ ] Implementer OpenSearch opprettelse av indeks og utførende alias-bytte
- [ ] Verifiser ende-til-ende i dev

### Oppgave 5: Søke-app

- [ ] Opprett `rekrutteringstreff-sok`-modul
- [ ] Implementer query builder for fritekst, status, paginering og visning
- [ ] Legg til geografi-filtre
- [ ] Legg til rollevalidering for `ALLE`, `MINE` og `MITT_KONTOR`
- [ ] Implementer aggregeringer for status, fylke og kontor
- [ ] Legg til komponenttester med OpenSearch Testcontainers

### Oppgave 6: Frontend

- [ ] Bytt oversiktsvisningen til nytt søke-endepunkt
- [ ] Behold detaljvisning og mutasjoner på eksisterende endepunkter i første fase
- [ ] La query-parametre speile `RekrutteringstreffSøkRequest`
- [ ] Oppdater filter-UI til statusene `Utkast`, `Publisert`, `Søknadsfrist passert`, `Fullført`, `Avlyst`
- [ ] Legg til tester for rolle × visning × filterkombinasjoner
- [ ] Fjern gammel klientfiltrering når ny flyt er verifisert
- [ ] Lag en mini-side for utviklere/admin (tilgangsstyrt med `UTVIKLER`-rollen) for å trigge reindeksering. Skal ha "Start"-knapp (med bekreftelsesdialog) og "Status"-knapp.
