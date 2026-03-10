# OpenSearch-søk for rekrutteringstreff

## Bakgrunn og motivasjon

I dag henter frontend alle rekrutteringstreff fra backend og gjør filtrering, søk og sortering i klienten. Målet er å flytte dette til OpenSearch.

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

Frontend skal sende hele søketilstanden i hver forespørsel. Fritekst søker på tvers av tittel, beskrivelse, innlegg og arbeidsgivernavn.

### Skisse

```
┌─────────────────────────────────────────────────────────────────────────┐
│  [Søk i rekrutteringstreff]                                            │
├─────────────────────┬───────────────────────────────────────────────────┤
│                     │  Aktive filtre: [Oslo] [Åpen] [Fjern alle]       │
│  Sorter             │                                                   │
│  ○ Sist oppdaterte  │  Tabs: [ Alle | Mine | Mitt kontor ]             │
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
│  Status             │  │ Eies av Benjamin Hansen                      │ │
│  ☐ Åpen             │  └──────────────────────────────────────────────┘ │
│  ☐ Stengt           │                                                   │
│  ☐ Utløpt           │                         1-100 av 4000   < >      │
│  ☐ Ikke publiserte  │                                                   │
│  Vis avlyste        │                                                   │
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
    val visAvlyste: Boolean = false,
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

### Visningsstatus (brukervendt vs. backend)

Frontend opererer med visningsstatuser som er avledet fra backend-status og tid.

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
`FULLFØRT`-treff hentes ved å velge sorteringen `FULLFØRTE`, som filtrerer til `status = FULLFØRT` og sorterer på `tilTid` desc. `SLETTET` filtreres alltid bort.

### Endepunkt

```
POST /api/rekrutteringstreff/sok
```

---

## Del 2: Reindekseringskø i `rekrutteringstreff-api`

Alle indekseringsrelevante endringer skal legge `treffId` i en komprimert reindekseringskø.

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

### Mønster: komprimert reindekseringskø per `treffId`

Det skal bare kunne finnes én ventende rad per `treffId`. Hvis treffet allerede ligger i køen, oppdateres raden i stedet for å opprette en ny.

| Tabell                             | Rolle                                                    |
| ---------------------------------- | -------------------------------------------------------- |
| `rekrutteringstreff_reindeksering` | Pending-kø for treff som må bygges og indekseres på nytt |

**Anbefalte kolonner:**

| Kolonne                 | Type                     | Beskrivelse                     |
| ----------------------- | ------------------------ | ------------------------------- |
| `treff_id`              | `uuid` (PK)              | Treff som skal reindekseres     |
| `opprettet_tidspunkt`   | `timestamptz` (NOT NULL) | Når treffet først ble lagt i kø |
| `sist_endret_tidspunkt` | `timestamptz` (NOT NULL) | Når kø-raden sist ble berørt    |

**Flyt**

1. Ved en indekseringsrelevant endring skriver samme service `treffId` til `rekrutteringstreff_reindeksering`
2. Innskriving gjøres i **samme database-transaksjon** som domeneendringen, med `insert ... on conflict (treff_id) do update set sist_endret_tidspunkt = now()`
3. Hvis transaksjonen rollbackes, rollbackes også kø-innskrivingen
4. En scheduler (med leader election) plukker pending `treffId`-er fra køen
5. For hvert `treffId`: bygg fullt dokumentgrunnlag, send melding med `treffId`, og slett raden etter vellykket sending

**Transaksjonskrav:** For å unngå tap av meldinger må innlegging i `rekrutteringstreff_reindeksering` skje atomisk sammen med selve domeneendringen. Det betyr at alle skrivende operasjoner som påvirker søkedokumentet må gjøre begge deler i samme `executeInTransaction`-blokk: oppdatere domenedata og legge `treffId` i køen. Vi skal ikke være avhengige av en asynkron etterprosess som først observerer endringen senere og deretter prøver å legge `treffId` i køen.

**Feiltoleranse:** Hvis appen krasjer mellom sending og sletting av kø-raden, kan samme `treffId` sendes flere ganger. Indekseren må derfor fortsatt være **idempotent**.

Idempotensen sikres ved at indekseren bruker OpenSearch `index` med dokument-ID = treffets UUID.

Samme builder brukes både ved full reindeksering og ved inkrementelle endringer. Hele dokumentet skrives hver gang.

Meldingen til indekseren inneholder bare `treffId`.

### Konsekvens for implementasjon

Alle relaterte moduler må ende i samme resultat: ett `treffId` som legges i reindekseringskø i samme transaksjon som domeneendringen. `rekrutteringstreff_hendelse` alene er ikke nok; planen må eksplisitt dekke relaterte domener.

---

## Del 3: Indekser-app (`rekrutteringstreff-indekser`)

Ny app under `rekrutteringstreff-backend/apps/rekrutteringstreff-indekser/`.

### Ansvar

- Lytter på Rapids-meldinger fra `rekrutteringstreff-api`
- Indekserer/oppdaterer/sletter dokument i OpenSearch basert på `@event_name`
- Støtter full reindeksering ved å lese treff fra databasen og bruke samme builder til å lage fullt dokument per treff ved ny indeksversjon
- Alias-bytte for zero-downtime reindeksering

Indekseren bygger alltid ett komplett dokument per `treffId` og skriver hele dokumentet til OpenSearch.

### Reindekseringsflyt med nytt alias

Normal drift og full reindeksering løses med to mekanismer:

1. Den komprimerte reindekseringskøen per `treffId` håndterer løpende endringer.
2. Versjonert indeks + alias håndterer trygg full reindeksering.

Full reindeksering bør kjøres slik:

1. Opprett ny indeks, for eksempel `rekrutteringstreff-v2`, uten å endre aktivt alias.
2. Bygg alle dokumenter fra databasen og skriv dem til den nye indeksen.
3. Mens dette pågår, fortsetter alle nye domeneendringer å legge `treffId` i reindekseringskøen i samme transaksjon som før.
4. Etter første fullscan kjøres en catch-up-fase der alle pending `treffId`-er bygges og skrives til den nye indeksen.
5. Når køen er tom og den nye indeksen er ajour, byttes alias atomisk til den nye indeksen.
6. Etter aliasbytte fortsetter normal inkrementell indeksering mot den aktive indeksen bak aliaset.

Tommelfingerregel:

- Ved vanlig drift: legg berørt `treffId` i reindekseringskøen.
- Ved full reindeksering: bygg alt til ny indeks, drener køen mot ny indeks, og bytt deretter alias.

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
        ├── IndexClient.kt
        ├── OpenSearchService.kt       ← indeks-livssyklus, alias-bytte
        ├── TreffDokument.kt
        ├── IndekserTreffLytter.kt     ← Rapids-lytter
        ├── TreffDokumentBuilder.kt    ← samler ett fullstendig dokument for ett treff
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

| Filter             | OpenSearch-clause                                                                                                                  |
| ------------------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| `fritekst`         | `match` på `all_text_no` + nested `match` på `arbeidsgivere.orgnavn`, `innlegg.tittel` og `innlegg.tekstinnhold` (lav boost)       |
| `visningsstatuser` | Sammensatt: `term` på `status` + `range` på `svarfrist`/`tilTid` per visningsstatus. For aggregeringer brukes Filter Aggregations. |
| `visAvlyste`       | Hvis false: `must_not` `term` `status=AVLYST`. Hvis true: inkludert.                                                               |
| `fylkesnummer`     | `terms` på `fylkesnummer`                                                                                                          |
| `kommunenummer`    | `terms` på `kommunenummer`                                                                                                         |
| `kontorer`         | `terms` på `kontorer`                                                                                                              |
| `MINE`             | `term` på `eiere` = innlogget navident                                                                                             |
| `MITT_KONTOR`      | `term` på `kontorer` = innlogget kontor                                                                                            |

`SLETTET`-status filtreres alltid bort (`must_not` `term` `status=SLETTET`).

`fritekst` legges i `must`, alle andre i `filter`.

Fritekst søker på `all_text_no` for toppnivåfelter og egne nested-queries for `arbeidsgivere` og `innlegg`.

---

## Uavklarte statusspørsmål – krever egen diskusjon

### 1. Ingen automatisk statusovergang ved utløp

I dag settes ikke domenestatus automatisk når `svarfrist` eller `tilTid` passerer. Visningsstatusene avledes derfor i søket.

Foreslått løsning:

| Hendelse             | Foreslått ny domenestatus |
| -------------------- | ------------------------- |
| `svarfrist` passerer | `STENGT` (ny)             |
| `tilTid` passerer    | `UTLØPT` (ny)             |

Åpne spørsmål:

- Skal `STENGT` og `UTLØPT` inn som domenestatuser?
- Trengs overgangsregel for eksisterende `PUBLISERT`-treff?

### 2. Terminologigap mellom domenemodell og søkegrensesnitt

Frontend bruker andre statusnavn enn backend. Dette bør helst fjernes ved å gjøre domenestatus og filterstatus like.

---

## Oppgaver

Rekkefølgen er foreslått, men hver oppgave beskriver et selvstendig leverbart steg.

### Oppgave 1: Komprimert reindekseringskø i rekrutteringstreff-api

1. Opprett `rekrutteringstreff_reindeksering`-tabell (Flyway-migrasjon) med `treff_id` som primærnøkkel
2. Legg alle indekseringsrelevante endringer inn i køen i samme transaksjon som domeneendringen, med `insert ... on conflict do update`
3. Scheduler plukker pending `treffId`-er fra køen
4. Scheduler sender Rapids-melding med `treffId` og sletter raden etter vellykket sending
5. Legg til tester som verifiserer at domeneendring og kø-innskriving rollbackes samlet ved feil

### Oppgave 2: Indekser-app

1. Opprett `rekrutteringstreff-indekser`-modul
2. Definer mapping og settings (norsk analyzer, nested for arbeidsgivere og innlegg)
3. Implementer `IndexClient`, `OpenSearchService`, alias-logikk
4. Implementer `TreffDokumentBuilder` og repositories/spørringer for å bygge fullt dokument per treff
5. Implementer `IndekserTreffLytter` for inkrementelle oppdateringer
6. Implementer full reindekseringsflyt: ny indeks, fullscan, catch-up fra reindekseringskø og atomisk aliasbytte
7. Deploy til dev, verifiser data

### Oppgave 3: Søke-app

1. Opprett søke-app/-modul med OpenSearch lesekonfig
2. Implementer query builder for fritekst, status, paginering og visning
3. Legg til geografi-filtre
4. Legg til visning-filter og rollevalidering (ALLE / MINE / MITT_KONTOR)
5. Komponenttester med OpenSearch Testcontainers

Hvis dagens brukeropplevelse skal bevares, må query-builderen følge dagens regler. Hvis ikke, må dette avklares som funksjonell endring.

### Oppgave 4: Frontend

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

| Risiko                        | Avbøting                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| ----------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Indeks og database ut av synk | Full reindeksering som fallback. Monitorer lag i `sendt_tidspunkt`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| OpenSearch utilgjengelig      | Returner feilmelding (503). Ingen fallback til gammelt endepunkt – risikoen for å vise feil data/statuser er for høy.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                      |
| Query-ytelse                  | Start enkelt, profiler med reelle data, juster boost-verdier                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| Visningsstatus-aggregeringer  | Rotårsaken er at `visningsstatus` ikke er lagret som felt – det avledes av tidsverdier mot `now` på søketidspunktet, og kan derfor ikke aggregeres direkte. Den egentlige løsningen er en scheduler (utenfor dette scope) som periodisk oppdaterer et `visningsstatus`-felt i OpenSearch basert på `svarfrist`/`tilTid`. Da blir aggregeringen triviell (`terms` på `visningsstatus`). Inntil det er på plass må aggregeringene bygges som én Filter Aggregation per visningsstatus med sammensatte tidsbetingelser – mer komplekst å implementere og teste. Anbefales at oppgaven utenfor scope utføres først slik at vi slipper midlertidig kompleks implementasjon her. |
