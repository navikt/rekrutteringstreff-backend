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

| Komponent                                 | Ansvar                                                                                        |
| ----------------------------------------- | --------------------------------------------------------------------------------------------- |
| **Frontend**                              | Sender søkeparametere, viser paginerte resultater.                                            |
| **rekrutteringstreff-søk** (ny app)       | Søke-endepunkt, bygger OpenSearch-spørringer                                                  |
| **rekrutteringstreff-indekser** (ny app)  | Tynn Kafka-konsument uten REST API. Skriver til OpenSearch basert på hendelser fra Rapids.    |
| **rekrutteringstreff-api** (eksisterende) | Eier databasen, bygger de fulle søkedokumentene, og publiserer dem på Rapids for indeksering. |

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

Alle indekseringsrelevante endringer skal legge `treffId` i en komprimert indekseringskø.

Denne køen er for løpende endringer i normal drift. Den er ikke mekanismen som starter full reindeksering. Full reindeksering startes eksplisitt via admin-endepunktet i indekser-appen, men bruker den samme køen til catch-up av endringer som skjer mens fullscan pågår.

### Indekseringsutløsere

Følgende operasjoner må føre til ny eller oppdatert melding til indekseren:

| Kilde                | Operasjon                                                                | Påvirker felter i søkedokument                                               |
| -------------------- | ------------------------------------------------------------------------ | ---------------------------------------------------------------------------- |
| `rekrutteringstreff` | opprett, oppdater, publiser, avpubliser, gjenåpne, fullfør, avlys, slett | toppnivåfelter, status, tider, sted, `sistEndret`                            |
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
3. **Trigger fullscan**: API-et har et internt endepunkt eller bakgrunnsjobb som porsjonsvis publiserer alle treff som `rekrutteringstreff.oppdatert`-meldinger på Rapids.
4. **Dual-write**: Under reindeksering kjører appen to konsumenter parallelt:
   - Én som skriver til **gammel** indeks (`INDEKS_VERSJON`)
   - Én som skriver til **ny** indeks (`REINDEKSER_INDEKS`)
   - Begge prosesserer `rekrutteringstreff.oppdatert`-meldinger, slik at ingen oppdateringer går tapt.
5. **Verifiser**: Sjekk at ny indeks har forventet antall dokumenter.
6. **Swap**: Oppdater `INDEKS_VERSJON=rekrutteringstreff-v2`. Deploy. Appen peker aliaset til ny indeks.
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

Geografi-feltene (`kommune`, `kommunenummer`, `fylke`, `fylkesnummer`) hentes fra rekrutteringstreffets eksisterende databasefelter. Se [Åpen avklaring: Geografi-berikelse](#åpen-avklaring-geografi-berikelse) for strategi for å sikre at disse alltid er utfylt.

### `sistEndret`

`sistEndret` er tidspunktet for siste databaseendring for treffet, satt innenfor transaksjonen som utfører endringen (eller så nærme som mulig etterpå). Enhver endring som trigger indekseringsutløser oppdaterer dette feltet.

### `all_text_no` (fritekst-felt)

I OpenSearch-mappingen defineres et `all_text_no`-felt med norsk analyzer som brukes til fritekst-søk. Følgende felter kopieres inn via `copy_to`:

- `tittel`
- `arbeidsgivere.orgnr`
- `arbeidsgivere.orgnavn`
- `innlegg.tittel`

Innleggs tekstinnhold inkluderes ikke i fritekst-søk i første versjon. Bare tittel (maks 20 tegn i visning, men fullt felt i indeks) brukes.

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

### Viktig avgrensning mot dagens GET-endepunkter

Søke-appen erstatter oversiktslisten, ikke alle eksisterende GET-endepunkter.

Plan:

1. Ny oversiktsliste i frontend flyttes til `POST /api/rekrutteringstreff/sok`.
2. Eksisterende detaljendepunkter beholdes uendret i første fase.
3. Full reindeksering kan ikke baseres på dagens eksisterende listeendepunkter alene, siden de ikke returnerer hele søkedokumentet.

Full reindeksering bygger dokumentene direkte fra databasen i indekseren, med samme builder som ved inkrementelle oppdateringer.

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

Slettede treff finnes ikke i indeksen (se [Sletting](#sletting-fra-opensearch)).

`fritekst` legges i `must`, alle andre i `filter`.

Fritekst søker på `all_text_no` for toppnivåfelter og egne nested-queries for `arbeidsgivere` og `innlegg`.

---

## Statusmodell

Planen endrer ikke domenestatusene. Eksisterende statusoverganger i backend er uendret:

| Fra status            | Trigger                     | Til status  |
| --------------------- | --------------------------- | ----------- |
| `UTKAST`              | manuell publisering         | `PUBLISERT` |
| `UTKAST`              | sletting (ingen jobbsøkere) | `SLETTET`   |
| `PUBLISERT`           | manuell fullføring          | `FULLFØRT`  |
| `PUBLISERT`           | manuell avlysning           | `AVLYST`    |
| `FULLFØRT` / `AVLYST` | gjenåpning                  | `PUBLISERT` |

Visningsstatusen `SOKNADSFRIST_PASSERT` avledes i OpenSearch-queryen (se [Visningsstatus](#visningsstatus)). Ingen scheduler, ingen ny enum-verdi i backend.

---

## Åpen avklaring: Geografi-berikelse

Databasen har allerede feltene `kommune`, `kommunenummer`, `fylke` og `fylkesnummer` på rekrutteringstreff. Disse brukes direkte i søkedokumentet og i geografi-filteret. Utfordringen er at feltene er fritekst og ikke nødvendigvis alltid utfylt.

For at geografi-filtrene skal fungere pålitelig, trengs en strategi for å sikre at kommune/fylke alltid er populert:

### Alternativ 1: Postnummerregister (anbefalt)

Posten/Bring publiserer et offisielt postnummerregister (TSV/CSV) som mapper postnummer til poststed, kommunenummer og kommunenavn. Fra kommunenummer kan fylkesnummer avledes (de to første sifrene).

- **Fordel**: Selvforsynt, ingen ekstern avhengighet i runtime. Postnummerregisteret oppdateres sjelden (noen ganger i året).
- **Implementasjon**: Bygg inn en statisk mapping-fil i `rekrutteringstreff-api`. Når et treff opprettes eller oppdateres med postnummer, slår backend opp kommune og fylke automatisk. Filen oppdateres ved behov.
- **Risiko**: Filen kan bli utdatert, men endringer i postnummerregisteret er svært sjeldne.

### Alternativ 2: PAM geografi-tjeneste

Nav har en intern geografi-tjeneste (brukt av `toi-geografi`) som mapper postnummer til kommune/fylke via REST (`PAM_GEOGRAFI_URL`).

- **Fordel**: Alltid oppdatert.
- **Ulempe**: Legger til en ekstern runtime-avhengighet for en enkel oppslag. Overkill for dette formålet.

### Alternativ 3: Bruker-input

La brukeren velge kommune/fylke eksplisitt i UI (dropdown med kjente verdier fra frontend-mapping).

- **Fordel**: Eksakt. Ingen berikelse nødvendig.
- **Ulempe**: Mer komplekst UI, dobbeltarbeid for bruker som allerede har skrevet postnummer.

**Anbefaling**: Alternativ 1 (postnummerregister) gir best balanse mellom pålitelighet og enkelhet. For eksisterende treff uten kommune/fylke kan en engangsmigrasjon berike basert på postnummeret.

---

## Oppgaver

Rekkefølgen er foreslått, men hver oppgave beskriver et selvstendig leverbart steg.

### Oppgave 1: Komprimert indekseringskø i rekrutteringstreff-api

1. Opprett `rekrutteringstreff_indeksering`-tabell (Flyway-migrasjon) med `treff_id` som primærnøkkel
2. Legg alle indekseringsrelevante endringer inn i køen i samme transaksjon som domeneendringen, med `insert ... on conflict do update`
3. Scheduler plukker pending `treffId`-er fra køen (med leader election)
4. For hvert treffId: bygg fullt søkedokument fra databasen, send `rekrutteringstreff.oppdatert` på Rapids (eller `rekrutteringstreff.slettet` ved status `SLETTET`), og slett raden etter vellykket sending
5. Legg til tester som verifiserer at domeneendring og kø-innskriving rollbackes samlet ved feil

### Oppgave 2: Reindekserings-støtte (rekrutteringstreff-api)

1. Implementer databaselogikken for å bygge et komplett søkedokument (`TreffDokumentBuilder` e.l.)
2. Implementer publisering over Rapids av oppdaterte, komplette dokument-JSON-modeller
3. Implementer bakgrunnsjobben som porsjonsvis publiserer alle treff som `rekrutteringstreff.oppdatert`-meldinger (trigges av indekser-appen ved oppstart med `REINDEKSER_ENABLED=true`)

### Oppgave 3: Indekser-app

1. Opprett `rekrutteringstreff-indekser`-modul etter mønster fra `toi-stilling-indekser`
2. Definer mapping og settings (norsk analyzer, nested for arbeidsgivere og innlegg, `all_text_no` med `copy_to`)
3. Sett opp Aiven/OpenSearch (`opensearch.yaml`, `nais.yaml`)
4. Implementer lytter for `rekrutteringstreff.oppdatert` (vanlig indeksering)
5. Implementer lytter for `rekrutteringstreff.slettet` (fysisk sletting fra indeks)
6. Implementer env-var-styrt reindeksering med dual-consumer
7. Deploy til dev, verifiser flyten

### Oppgave 4: Søke-app

1. Opprett søke-app/-modul med OpenSearch lesekonfig og auth (jobbsøkerrettet, arbeidsgiverrettet, utvikler)
2. Implementer query builder for fritekst, paginering og sortering
3. Implementer visningsstatus-filter med avledet `SOKNADSFRIST_PASSERT` (`status=PUBLISERT AND svarfrist < now`)
4. Legg til geografi-filtre (fylkesnummer, kommunenummer)
5. Legg til visning-filter (ALLE / MINE / MITT_KONTOR)
6. Legg til aggregeringer for filtre
7. Komponenttester med OpenSearch Testcontainers

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
