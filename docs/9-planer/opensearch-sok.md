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
`FULLFØRT`-treff hentes ved å velge sorteringen `FULLFØRTE`, som filtrerer til `status = FULLFØRT` og sorterer på `tilTid` desc. `SLETTET` filtreres alltid bort.

### Endepunkt

```
POST /api/rekrutteringstreff/sok
```

---

## Del 2: Hendelse-publisering og outbox i `rekrutteringstreff-api`

`rekrutteringstreff-api` publiserer én Rapids-melding per **indekseringsrelevant endring**. Det inkluderer ikke bare rene treff-hendelser, men også endringer i relaterte domeneobjekter som påvirker søkedokumentet: eiere, kontorer, arbeidsgivere, innlegg og jobbsøkertellinger.

Dette er viktig fordi dagens data for søkedokumentet er spredt over flere moduler. En plan som kun reagerer på rader i `rekrutteringstreff_hendelse` vil gi foreldet indeks når f.eks. arbeidsgivere, innlegg eller jobbsøkere endres uten at det samtidig skrives en treff-hendelse.

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

Det anbefales at vi innfører ett eksplisitt «treff må reindekseres»-signal per påvirket `treffId`, uavhengig av hvilken modul endringen kom fra. Her betyr «snapshot» bare et komplett, denormalisert dokumentgrunnlag for ett treff, ikke en egen snapshot-tabell. Dokumentgrunnlaget bygges on demand fra databasen med én felles builder/transformer.

### Mønster: komprimert reindekseringskø per `treffId`

Siden meldingen til indekseren bare inneholder `treffId`, trenger vi ikke én outbox-rad per domenehendelse. Det er enklere å bruke en **komprimert reindekseringskø** der det bare kan finnes én ventende rad per `treffId`.

Det betyr at vi med vilje komprimerer flere endringer på samme treff til én pending rad. Hvis et treff allerede ligger usendt i køen, legger vi ikke til en ny rad. Neste kjøring bygger uansett hele dokumentet fra databasen og får dermed med seg alle endringene som har skjedd siden raden ble opprettet.

| Tabell                             | Rolle                                                    |
| ---------------------------------- | -------------------------------------------------------- |
| `rekrutteringstreff_reindeksering` | Pending-kø for treff som må bygges og indekseres på nytt |

**Anbefalte kolonner:**

| Kolonne                 | Type                     | Beskrivelse                     |
| ----------------------- | ------------------------ | ------------------------------- |
| `treff_id`              | `uuid` (PK)              | Treff som skal reindekseres     |
| `opprettet_tidspunkt`   | `timestamptz` (NOT NULL) | Når treffet først ble lagt i kø |
| `sist_endret_tidspunkt` | `timestamptz` (NOT NULL) | Når kø-raden sist ble berørt    |

**Flyt:**

1. Ved en indekseringsrelevant endring skriver samme service `treffId` til `rekrutteringstreff_reindeksering`
2. Innskriving gjøres i **samme database-transaksjon** som domeneendringen, med `insert ... on conflict (treff_id) do update set sist_endret_tidspunkt = now()`
3. Hvis transaksjonen rollbackes, rollbackes også kø-innskrivingen
4. En scheduler (med leader election) plukker pending `treffId`-er fra køen
5. For hvert `treffId`: bygg fullt dokumentgrunnlag, send melding med `treffId`, og slett raden etter vellykket sending

Denne modellen er enklere enn kvitteringsmønsteret når payloaden bare er `treffId`, fordi den unngår at mange raske endringer på samme treff genererer en lang kø av overflødige meldinger.

**Viktig egenskap:** Køen er en best effort-representasjon av hvilke treff som må bygges på nytt, ikke et revisjonsspor over alle hendelser. Hvis et treff endres ti ganger før scheduler kjører, holder det at det finnes én pending rad så lenge indekseren alltid bygger hele dokumentet.

**Transaksjonskrav:** For å unngå tap av meldinger må innlegging i `rekrutteringstreff_reindeksering` skje atomisk sammen med selve domeneendringen. Det betyr at alle skrivende operasjoner som påvirker søkedokumentet må gjøre begge deler i samme `executeInTransaction`-blokk: oppdatere domenedata og legge `treffId` i køen. Vi skal ikke være avhengige av en asynkron etterprosess som først observerer endringen senere og deretter prøver å legge `treffId` i køen.

Dette følger mønsteret som allerede brukes i backend: service-laget åpner en database-transaksjon, gjør alle relevante SQL-operasjoner på samme `Connection`, og committer først når alt er vellykket. Hvis en operasjon feiler, rulles alt tilbake. Reindekseringskøen må behandles på samme måte.

**Feiltoleranse:** Hvis appen krasjer mellom sending og sletting av kø-raden, kan samme `treffId` sendes flere ganger. Indekseren må derfor fortsatt være **idempotent**.

Idempotensen sikres ved at indekseren bruker OpenSearch **index** med eksplisitt dokument-ID = treffets UUID, altså full utskifting av dokumentet ved hver oppdatering. Å indeksere samme dokument to ganger med samme data gir nøyaktig samme resultat. Dette er enklere enn i f.eks. kandidatvarsel-api, som bruker meldingsid-sjekk i databasen for å hindre duplikatsending. Her er OpenSearch-operasjonen naturlig idempotent når hele dokumentet overskrives med samme ID.

For å holde løsningen enkel brukes samme builder/transformer både ved full reindeksering og ved inkrementelle endringer. I praksis betyr det at vi alltid bygger hele dokumentet for ett `treffId` og skriver hele dokumentet til OpenSearch, i stedet for å forsøke delvise patch-operasjoner.

Meldingen til indekseren skal derfor bare uttrykke at ett bestemt `treffId` må bygges og indekseres på nytt. Selve dokumentet bygges i indekseren med samme builder som brukes ved full reindeksering.

### Konsekvens for implementasjon

Alle relaterte moduler må ende i samme resultat: ett `treffId` som legges i reindekseringskø i samme transaksjon som domeneendringen. `rekrutteringstreff_hendelse` alene er ikke nok; planen må eksplisitt dekke relaterte domener.

---

## Del 3: Indekser-app (`rekrutteringstreff-indekser`)

Ny app under `rekrutteringstreff-backend/apps/rekrutteringstreff-indekser/`. Følger samme mønster som `toi-stilling-indekser` i `toi-rapids-and-rivers`.

### Ansvar

- Lytter på Rapids-meldinger fra `rekrutteringstreff-api`
- Indekserer/oppdaterer/sletter dokument i OpenSearch basert på `@event_name`
- Støtter full reindeksering ved å lese treff fra databasen og bruke samme builder til å lage fullt dokument per treff ved ny indeksversjon
- Alias-bytte for zero-downtime reindeksering

Indekseren skal ikke anta at bare toppnivå-treffet endres. Den må kunne motta oppdateringer som skyldes relaterte endringer, men alltid ende opp med ett komplett dokument per `treffId`. Den enkleste strategien er å bygge hele dokumentet på nytt ved hver relevant endring og skrive hele dokumentet til OpenSearch.

### Reindekseringsflyt med nytt alias

Normal drift og full reindeksering løses med to ulike mekanismer som virker sammen:

1. Den komprimerte reindekseringskøen per `treffId` håndterer løpende endringer.
2. Versjonert indeks + alias håndterer trygg full reindeksering.

Full reindeksering bør kjøres slik:

1. Opprett ny indeks, for eksempel `rekrutteringstreff-v2`, uten å endre aktivt alias.
2. Bygg alle dokumenter fra databasen og skriv dem til den nye indeksen.
3. Mens dette pågår, fortsetter alle nye domeneendringer å legge `treffId` i reindekseringskøen i samme transaksjon som før.
4. Etter første fullscan kjøres en catch-up-fase der alle pending `treffId`-er bygges og skrives til den nye indeksen.
5. Når køen er tom og den nye indeksen er ajour, byttes alias atomisk til den nye indeksen.
6. Etter aliasbytte fortsetter normal inkrementell indeksering mot den aktive indeksen bak aliaset.

Dette er viktig fordi en ren fullscan ikke er nok. Uten catch-up-fasen vil endringer som skjer underveis kunne mangle i den nye indeksen ved aliasbytte.

Det er ikke nødvendig å legge alle ID-er i outbox som en separat reindekseringsjobb for fullscan. Fullscan skal hente alle treff direkte fra databasen. Køen brukes for å fange opp endringer som skjer underveis og for vanlig inkrementell drift.

Den praktiske tommelfingerregelen blir derfor:

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

Ny, separat app under `rekrutteringstreff-backend/apps/rekrutteringstreff-sok/` som eksponerer søke-endepunktet.
Dette gjør at vi kan skalere lesning uavhengig av API-et for skriving.

### Query builder

Enkel controller → service → OpenSearch-klient uten ekstra abstraksjonslag. Backend tar imot `RekrutteringstreffSøkRequest`, bygger OpenSearch `bool`-query og returnerer `RekrutteringstreffSøkRespons`. Tilgangskontroll håndteres strengt i backend basert på roller og innlogget bruker (fra token).

```
RekrutteringstreffSøkController
    ↓
RekrutteringstreffSøkService       ← bygger query, kaller klient
    ↓
OpenSearchKlient                   ← wrapper rundt opensearch-java
```

### Viktig avgrensning mot dagens GET-endepunkter

Søke-appen er et nytt lesegrensesnitt for oversikt og filtrering. Den skal **ikke** i første omgang erstatte alle eksisterende GET-endepunkter i `rekrutteringstreff-api`.

Dagens endepunkter brukes fortsatt i andre flyter enn listevisningen, blant annet ved valg av treff i andre skjermbilder og ved detaljvisning. Planen må derfor være:

1. Ny oversiktsliste i frontend flyttes til `POST /api/rekrutteringstreff/sok`.
2. Eksisterende detaljendepunkter beholdes uendret i første fase.
3. Full reindeksering kan ikke baseres på dagens eksisterende listeendepunkter alene, siden de ikke returnerer hele søkedokumentet.

Full reindeksering skal derfor bygge dokumentene direkte fra databasen i indekseren, med samme builder-/repository-lag som brukes ved inkrementelle oppdateringer. Det viktige designvalget er at samme builder/transformer brukes både for full reindeksering og inkrementelle oppdateringer, og at OpenSearch alltid får et komplett dokument som erstatter det gamle.

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

**Fritekst-søk med `copy_to`:**

Alle toppnivå-tekstfelter (`tittel`, `beskrivelse`, `fylke`, `kommune`, `poststed`, `gateadresse`) er konfigurert med `copy_to: ["all_text_no"]` i mappingen. Det betyr at verdiene fra disse feltene automatisk kopieres til ett samlefelt (`all_text_no`).

Når brukeren søker på fritekst, kan query-builderen gjøre ett enkelt søk på `all_text_no` i stedet for å bygge komplekse `multi_match`-queries. Dette blir **mer robust** fordi:

1. **Én analyzer** – `all_text_no` bruker norsk analyzer for alle kopierte felter konsistent, uansett om det er tittel, adresse eller sted.
2. **Enklere query** – Søket blir enklere å vedlikeholde (én `match` + separate `nested`-queries for arbeidsgivere og innlegg).
3. **Unngår clause explosion** – `multi_match` mot N felter med M ord genererer N×M clauses. Aiven har en anbefalt maksimumsgrense, og stillingssøk treffer denne med mange ord kombinert med mange felt. Med `match` på `all_text_no` holder clause-antallet seg lavt uavhengig av antall ord i fritekstfeltet.

**Nested-felter utelatt fra kopiering** (arbeidsgivere, innlegg) må fortsatt løses med eksplisitte nested-queries i query-builderen, siden OpenSearch ikke støtter `copy_to` fra nested til toppnivå.

**Konkret:** Hvis fritekst-søk skal implementeres, anbefales det å søke på `all_text_no` for toppnivåfelter + egne nested-queries for `arbeidsgivere.orgnavn`, `innlegg.tittel` og `innlegg.tekstinnhold`.

---

## Uavklarte statusspørsmål – krever egen diskusjon

### 1. Ingen automatisk statusovergang ved utløp

I dag settes ikke treffets domenestatus automatisk når `svarfrist` eller `tilTid` passerer. Et treff med `status=PUBLISERT` forblir `PUBLISERT` gjennom hele livssyklusen – fra åpent for påmelding til lenge etter at det er over. Visningsstatusene `STENGT_FOR_SØKERE`, `UTLØPT` og `ÅPEN_FOR_SØKERE` avledes derfor på søketidspunktet, ikke lagret.

Den rette løsningen er en **scheduler i `rekrutteringstreff-api`** som setter faktisk domenestatus ved tidsstyrt overgang:

| Hendelse             | Foreslått ny domenestatus |
| -------------------- | ------------------------- |
| `svarfrist` passerer | `STENGT` (ny)             |
| `tilTid` passerer    | `UTLØPT` (ny)             |

Med eksplisitte statuser kan:

- OpenSearch aggregere direkte på `status`-feltet uten tidsbetingelser
- Visningsstatuser mappes 1:1 til domenestatuser, ingen beregningslogikk i søke-appen
- Statusen veileders UI (legge til jobbsøkere) styres av `PUBLISERT` (åpen) vs. `STENGT` (søkefrist ute, men treff ikke passert) – nødvendig distinksjon som i dag mangler

**Åpne spørsmål:** Skal `STENGT` og `UTLØPT` opprettes som nye domenestatuser, eller gjenbrukes/omdøpes `FULLFØRT`? Trengs det en overgangsregel for eksisterende `PUBLISERT`-treff der `svarfrist` allerede er passert?

Merk: Hvis `STENGT` innføres som domenestatus, er intensjonen at den erstatter visningsstatusen `STENGT_FOR_SØKERE` med en 1:1-mapping – slik at søke-appens oversettingslag kan fjernes (se punkt 2 under).

### 2. Terminologigap mellom domenemodell og søkegrensesnitt

Frontend-filtre bruker brukervennlige termer (`Åpen for søkere`, `Stengt for søkere`, `Utløpt`) som i dag ikke finnes som faktiske statuser i backend. Søkeappen inneholder en eksplisitt mapping mellom disse to begrepsverdener, noe som skaper friksjon: nye filtre eller statuser må oppdateres på to steder.

En alternativ løsning er at statusfeltet i søkeindeksen har en 1:1-mapping med taggene som allerede vises i søkeresultatene i frontend – samme ordlyd, ingen oversettelse. Det vil si at domenestatusene (se punkt 1) bør navngis slik at de kan brukes direkte som filterverdi og som tag-tekst, uten et ekstra omsettingslag i verken søkeklient eller frontend.

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

> Tabellen under beskriver **målbildet for det nye søke-endepunktet**. Dette er ikke identisk med dagens semantikk i `GET /api/rekrutteringstreff` og `GET /api/rekrutteringstreff/mittkontor`, og må derfor behandles som en bevisst funksjonell endring.

Søket har tre **visninger** (faner i frontend): `ALLE`, `MINE` og `MITT_KONTOR`. Visningen bestemmer **scope** – altså hvilke treff som er med i resultatet. Rollen bestemmer **tilgang** – om du i det hele tatt får lov til å bruke en visning, og om noen statuser filtreres bort.

Roller (fra AD-grupper): **Jobbsøkerrettet** (lesetilgang), **Arbeidsgiverrettet** (opprette/administrere treff), **Utvikler/Admin** (full tilgang, ingen pilotkontor-krav). Se [tilgangsstyring.md](../3-sikkerhet/tilgangsstyring.md) for detaljer.

Pilotkontor-krav håndheves som pre-flight-sjekk i controller (403 før søk kjøres) og gjelder alle roller unntatt utvikler.

| Visning       | Jobbsøkerrettet                                | Arbeidsgiverrettet                 | Utvikler/Admin                     |
| ------------- | ---------------------------------------------- | ---------------------------------- | ---------------------------------- |
| `ALLE`        | Alle publiserte treff (ikke avlyste/fullførte) | Alle statuser                      | Alle statuser                      |
| `MINE`        | Ikke tillatt (403) – kan ikke opprette treff   | `eiere` inneholder brukerens ident | `eiere` inneholder brukerens ident |
| `MITT_KONTOR` | `kontorer` inneholder aktivEnhet + `PUBLISERT` | `kontorer` inneholder aktivEnhet   | `kontorer` inneholder aktivEnhet   |

Rollefilter legges alltid server-side, uavhengig av hva klienten sender inn. Ugyldig visning/rolle-kombinasjon gir 403.

### Dagens semantikk som avviker fra målbildet

Dette må synliggjøres før implementasjon slik at vi ikke uforvarende bygger produktendringer under dekke av teknisk migrering:

- Dagens `GET /api/rekrutteringstreff` for Nav-brukere betyr i praksis «mine eller publiserte», ikke «alle`.
- Dagens `GET /api/rekrutteringstreff/mittkontor` betyr i praksis «publiserte treff opprettet av mitt kontor med `tilTid` i fremtiden», ikke generisk filter på `kontorer`.
- Dagens backend har ikke status `AVPUBLISERT`; avpublisering setter status tilbake til `UTKAST` og skriver hendelsen `AVPUBLISERT`.

Hvis vi ønsker å bevare dagens brukeropplevelse, må query-builderen implementere dagens regler. Hvis vi ønsker nytt målbilde, må endringen avklares eksplisitt med produkt før frontend kobles over.

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

### Migrering i frontend

Første migreringssteg bør være smalt og reverserbart:

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
