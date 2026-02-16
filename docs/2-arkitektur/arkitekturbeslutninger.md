# Arkitekturbeslutninger

Dette dokumentet forklarer sentrale arkitekturbeslutninger og avveininger i rekrutteringstreff-løsningen. Dokumentet er strukturert som spørsmål og svar (FAQ) basert på spørsmål som har dukket opp under arkitekturgjennomgang.

---

## Innhold

- [Hvorfor er løsningen fordelt på flere applikasjoner?](#hvorfor-er-løsningen-fordelt-på-flere-applikasjoner)
- [Hvorfor asynkron meldingsflyt fremfor synkrone kall med retries?](#hvorfor-asynkron-meldingsflyt-fremfor-synkrone-kall-med-retries)
- [hendelse_data: Polymorfe JSON-objekter i hendelsestabellene](#hendelse_data-polymorfe-json-objekter-i-hendelsestabellene)

---

## Hvorfor er løsningen fordelt på flere applikasjoner?

> **Spørsmål:** Burde minside-api, rekrutteringsbistand-aktivitetskort og rekrutteringstreff-api vært samme applikasjon? Kunne rekrutteringstreff-bruker (frontend) gått direkte mot rekrutteringstreff-api? Hvorfor er kandidatvarsel-api og toi-synlighetsmotor egne applikasjoner?

### Kort svar

Applikasjonene er delt basert på tre kriterier: **filtrering og API-isolasjon** (MinSide-API), **ulik deploy-livssyklus** (aktivitetskort), og **gjenbruk på tvers av domener** (kandidatvarsel, synlighetsmotor).

### Applikasjoner i rekrutteringstreff-backend (monorepo)

Disse tre appene tilhører samme monorepo fordi de deler domenet, men er separate NAIS-applikasjoner:

| App                                     | Begrunnelse for separat deploy                                                                |
| --------------------------------------- | --------------------------------------------------------------------------------------------- |
| **rekrutteringstreff-api**              | Azure AD + TokenX-autentisering, REST API for NAV-ansatte og mottak av jobbsøker-kall         |
| **rekrutteringstreff-minside-api**      | ID-porten → TokenX-veksling, filtrering og API-isolasjon for jobbsøkere                       |
| **rekrutteringsbistand-aktivitetskort** | Ren Kafka-konsument, ingen REST-endepunkter (utover helsejekker). Trenger ikke Javalin-server |

### Hvorfor er MinSide-API-et en egen app?

rekrutteringstreff-api aksepterer allerede TokenX-tokens, og Next.js-BFF-en gjør allerede TokenX OBO-veksling. Dagens flyt er en **dobbel veksling**: BFF → minside-api → rekrutteringstreff-api. MinSide-API-et er altså ikke nødvendig for autentisering, men gir:

- **Filtrering**: Fjerner sensitiv data (fødselsnummer, NAV-intern info) før det sendes til jobbsøker
- **API-isolasjon**: Eksponerer kun endepunkter jobbsøkere trenger

Alternativet ville vært at BFF-en veksler direkte mot rekrutteringstreff-api, med filtrering i dedikerte `/borger/`-endepunkter. Det gir enklere arkitektur og lavere latency, men krever at rekrutteringstreff-api skiller tydelig mellom NAV-ansatt- og jobbsøker-endepunkter.

Vi beholder MinSide-API-et fordi separasjonen gjør det lettere å resonnere om jobbsøkertilgang, og driftskostnaden er lav. Om det forblir en tynn proxy, bør vi vurdere å eliminere det.

### Hvorfor er aktivitetskort-appen separat?

Ren Kafka-konsument uten REST-endepunkter – bruker `RapidApplication` (Ktor), ingen Javalin-avhengighet. Kan deployes, skaleres og feile uavhengig av API-et.

### Støttetjenester utenfor monorepoet

Egne repoer fordi de gjenbrukes av andre domener:

- **kandidatvarsel-api**: Generisk varseltjeneste for hele rekrutteringsbistand-økosystemet
- **toi-synlighetsmotor**: Primært for kandidatindeksering i Elasticsearch, rekrutteringstreff er bare én konsument
- **aktivitetskort**: Planlagt gjenbruk i kandidatliste-domenet via Aktivitetskort Self-Service API

### Synkront vs. asynkront

Separate tjenester betyr ikke nødvendigvis asynkron kommunikasjon. MinSide-API → rekrutteringstreff-api er synkron REST. Prinsippet: synkron REST når klienten trenger umiddelbar respons, asynkron Kafka når flere lyttere reagerer uavhengig.

---

## Hvorfor asynkron meldingsflyt fremfor synkrone kall med retries?

> **Spørsmål:** Hvordan ville en synkron, retry-basert løsning sett ut for invitasjonsscenariet?

Ved invitasjon lagrer API-et hendelsen i DB og returnerer 200 OK umiddelbart. En scheduler publiserer hendelser til Kafka, der aktivitetskort og kandidatvarsel lytter uavhengig.

Synkront alternativ: API-et kaller begge nedstrømssystemene direkte med retries før det svarer klienten. Problemene med det:

- **Responstid**: Bruker venter på at alle kall fullføres
- **Feilhåndtering**: Hvis ett system er nede, blokkerer hele requesten. Hva rulles tilbake?
- **Kobling**: API-et må kjenne til hvert nedstrømssystem. Ny konsument = endre API-koden
- **Idempotens**: Retry-logikk per kall, risiko for duplikater

Synkrone kall brukes kun når klienten trenger umiddelbar respons (f.eks. MinSide-API → rekrutteringstreff-api, frontend → kandidatsøk). Eventual consistency (sekunder) er akseptabelt for varsler og aktivitetskort.

---

## hendelse_data: Polymorfe JSON-objekter i hendelsestabellene

> **Spørsmål:** Hvordan henger `hendelse_data`-kolonnens JSON-objekter sammen med hendelsestyper?

`hendelse_data` er en `jsonb`-kolonne i hendelsestabellene. De fleste hendelsestyper har `null` her. Kun disse lagrer data:

| Hendelsestype                                 | JSON-struktur                 | Kotlin-klasse                    |
| --------------------------------------------- | ----------------------------- | -------------------------------- |
| `TREFF_ENDRET_ETTER_PUBLISERING`              | `Rekrutteringstreffendringer` | `Rekrutteringstreffendringer.kt` |
| `TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON` | `Rekrutteringstreffendringer` | `Rekrutteringstreffendringer.kt` |
| `MOTTATT_SVAR_FRA_MINSIDE`                    | `MinsideVarselSvarData`       | `MinsideVarselSvarData.kt`       |

**Rekrutteringstreffendringer** lagrer hva som endret seg i et publisert treff (gammelVerdi/nyVerdi/skalVarsle per felt). Brukes til å bygge SMS-tekst og vise endringer i frontend.

**MinsideVarselSvarData** lagrer tilbakemelding fra kandidatvarsel-api om varselets status (sendt/feilet, kanal, feilmelding etc.).

### Typesikkerhet

- **Backend**: Typede Kotlin-klasser (sealed interface `HendelseDataDto`) ved lesing/skriving. Swagger dokumenterer polymorfismen via `@OneOf`
- **Frontend**: Zod-schemaer med `.transform()` parser til riktig type basert på hendelsestype
- **Evolusjon**: Strukturen er additiv – nye nullable felter kan legges til uten å bryte eksisterende data
