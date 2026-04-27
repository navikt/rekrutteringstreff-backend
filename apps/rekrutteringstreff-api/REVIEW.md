# Review: rekrutteringstreff-api

Kodereview gjort 2026-04-27 med fokus på generell best practice og Nav-spesifikke føringer (Nais, sikkerhet, observability, plattform).

Oppsummering på toppen, deretter detaljer per perspektiv. Funnene er sortert etter alvorlighetsgrad innenfor hvert perspektiv.

---

## Sammendrag

| Perspektiv | Vurdering | Hovedinntrykk |
|---|---|---|
| Sikkerhet | ⚠️ | Solid auth-grunnstamme (Azure AD + TokenX + grupper + Modia-pilotkontor), men noen viktige lekkasjepunkter i logging og GitHub Actions. |
| Plattform | ⚠️ | Brudd på Nav-føringer for ressurser (CPU-limits) og HikariCP-pool (15 er høyt for containere). |
| Arkitektur | ✅ | Tydelig lagdeling (Controller → Service → Repository), domeneorientert pakkestruktur, eksplisitt transaksjonshåndtering. |
| Endringssikkerhet | ⚠️ | God testdekning og Flyway, men `deploy_dev_branch` peker på en feature-branch som virker glemt. Mangler runbook/observability-dashboards i repoet. |

**Topp 5 ting å rydde opp i (prioritert):**

1. 🔴 **Fjern CPU-limits i Nais-manifestet** (`limits.cpu: 3000m`) — Nav-føring er kun `requests` på CPU.
2. 🔴 **Senk `maximumPoolSize` fra 15 til 3–5** i HikariCP — 15 forbindelser per pod × min 2 pod = 30+, lett å mette Cloud SQL.
3. 🟠 **Pin GitHub Actions til SHA** — `actions/checkout@v4`, `actions/setup-java@v4`, `nais/deploy/actions/deploy@v2` er ikke SHA-pinnet.
4. 🟠 **`deploy_dev_branch: refs/heads/svar-for-jobbsoker`** — feature-branch deployer til dev. Sjekk om dette er bevisst, eller om det skulle vært fjernet etter merge.
5. 🟠 **Fjern `secureLogger.info("melding før filter: $tekst etter filter: ...")`** i `OpenAiClient` — selv om det går til team-logs, lekker det fri tekst med potensielle PII til SecureLog uten retensjonsbegrensning utover det som ligger i team-logs-policyen.

---

## 1. Sikkerhet

| Vurdering | Funn |
|---|---|
| ✅ | Tre auth-konfigurasjoner: Azure AD (saksbehandler), TokenX (borger via min-side-API), fakedings (kun dev). Korrekt valg per kallertype. |
| ✅ | Eksplisitt rollebasert autorisasjon med `verifiserAutorisasjon(...)` per endepunkt + ekstra Modia-sjekk på pilotkontor. Solid og typesikker. |
| ✅ | `SecureLog` med `TEAM_LOGS`-marker som skiller team-logs fra applog. Korrekt Nav-mønster. |
| ✅ | Audit-log via `no.nav.common.audit-log` (CEF) for visning av jobbsøkere — riktig Nav-praksis. |
| ✅ | Alle SQL-spørringer bruker `PreparedStatement` med parameter-binding. Ingen string-konkateninert SQL funnet. |
| ⚠️ | **PII-lekkasje i SecureLog**: `OpenAiClient.validateRekrutteringstreffOgLogg` logger hele input-teksten både før og etter `PersondataFilter` til SecureLog. Dette er i prinsippet brukerinput og kan inneholde PII til tross for filteret. Vurder å droppe pre-filter-logging eller redusere til lengde/hash. |
| ⚠️ | **`PersondataFilter` er en svak primær forsvarslinje**: Regex matcher kun e-post og 3+ sifre. Navn, fødselsdato uten skilletegn, adresser går gjennom. OK som *defense-in-depth* for OpenAI-prompten, men dokumenter at dette ikke er en anonymisering. |
| ⚠️ | **GitHub Actions ikke SHA-pinnet** i `deploy-template.yaml` og `deploy-rekrutteringstreff-api.yaml`: `actions/checkout@v4`, `actions/setup-java@v4`, `nais/docker-build-push@v0`, `nais/deploy/actions/deploy@v2`. Bryter Nav-føring (OWASP A08). Bruk `@<sha> # v4.x.x`. |
| ⚠️ | **OpenAiClient leser `OPENAI_API_KEY` fra env med fallback `"test-key"`** som default-parameter. I prod er secret tilgjengelig via `envFrom: openai-toi-rekrutteringstreff` — sjekk at appen feiler tidlig hvis nøkkelen mangler i prod, ellers kalles OpenAI med "test-key". |
| ⚠️ | **OpenAI API-key sendes ikke i request** — i utdraget jeg har sett brukes `apiKey`-feltet ikke i HTTP-requesten. Sjekk at nøkkelen faktisk attacheres som `api-key`-header (standard for Azure OpenAI), ellers feiler kallet i prod. |
| ⚠️ | **Generisk catch-all logger stack-trace til applog**: `exception(Exception::class.java) { e, ctx -> log.error("Uventet feil", e) }` i `ExceptionMapping`. Bruk `secureLog` for stack-traces, eller filtrer eksplisitt for at exception-meldinger ikke skal nå applog. |
| ⚠️ | **`/swagger`, `/openapi`, `/webjars` eksponert i dev-ingress** uten autentisering (auth-filteret matcher kun `/api/rekrutteringstreff`). Akseptabelt i dev, men bekreft at det er bevisst valg og at swagger-UI ikke lekker sensitive eksempel-data. |
| ⚠️ | `verifyJwt` itererer gjennom flere verifiers og fanger `JWTVerificationException`/`SigningKeyNotFoundException`/`IllegalArgumentException`. Logg på `warn`-nivå hvor mange forsøk som feilet — i dag er det vanskelig å skille "feil issuer" fra "ugyldig token". |
| 🚫 | Ingen funn av hardkodede secrets i koden. ✅ |

**Anbefalinger:**
- Pinning av GitHub Actions: kjør `pinact run` eller bruk Renovate til å pinne automatisk.
- Vurder å hashe input-teksten i SecureLog i stedet for å logge fri tekst.
- Legg til eksplisitt `require(System.getenv("OPENAI_API_KEY") != null)` ved oppstart.

---

## 2. Plattform (Nais)

| Vurdering | Funn |
|---|---|
| ✅ | `tokenx.enabled: true` og `azure.application.enabled: true` med `allowAllUsers: false` + grupper. Korrekt. |
| ✅ | `accessPolicy.inbound` er eksplisitt og minimal — kun rekrutteringsbistand-frontend(2) og minside-api. |
| ✅ | `accessPolicy.outbound` har eksplisitte applikasjoner og `external` med port 443. Bra. |
| ✅ | Health-endepunkter (`/isalive`, `/isready`, `/metrics`) er på plass og brukt av Nais-probes. |
| ✅ | `observability.autoInstrumentation` aktivert for Java (OpenTelemetry). Logging til både Loki og Elastic. |
| ✅ | Flyway-migrasjoner på oppstart, `leaderElection: true` for schedulers. |
| ✅ | Alerts-fil med sensible regler (replicas==0, error-logger, warning-økning). |
| 🔴 | **CPU-limits satt** (`limits.cpu: 3000m`) — bryter Nav-føring. CPU-throttling under last gir uforutsigbar latens. Fjern `limits.cpu` helt; behold kun `requests`. Memory-limit er OK å ha. |
| 🔴 | **HikariCP `maximumPoolSize = 15`** + `minimumIdle = 3` — for høyt for K8s-containere. Med min 2 pod (prod min 2, dev fast 2) er det 30 connections som baseline, opp til 60 under last. Cloud SQL `db-custom-1-3840` har max ~100 connections totalt. Anbefalt start: `maximumPoolSize = 5`, `minimumIdle = 1`. |
| ⚠️ | **`transactionIsolation = "TRANSACTION_REPEATABLE_READ"` med kommentar "PostgreSQL standard"** — feil. PostgreSQL-standarden er `READ_COMMITTED`. `REPEATABLE_READ` er gyldig, men gir flere serialiseringsfeil og er ikke nødvendigvis det dere ønsker. Kommentaren bør fikses og valget begrunnes (eller endres til `READ_COMMITTED`). |
| ⚠️ | **`maxLifetime = 1_800_000` (30 min)** sammen med Cloud SQL er marginalt. Cloud SQL stenger idle-connections rundt 10 min. `idleTimeout = 600_000` (10 min) er OK, men `maxLifetime` bør være litt under det Cloud SQL eller proxy-en aksepterer — mange Nav-team bruker 30 min - litt margin (f.eks. `1_770_000`). |
| ⚠️ | **`min_replicas = max_replicas = 2` i dev** — låst. OK, men da har dere ikke testet autoskaleringen før prod. |
| ⚠️ | **Memory `requests: 512Mi`, `limits: 2048Mi`** — stor differanse. Hvis appen jevnlig bruker 1.5Gi vil node-pakking bli uforutsigbar. Vurder å sette `requests` nærmere reelt forbruk. |
| ⚠️ | **`Thread { rapidsConnection.start() ... System.exit(1) }`**: Hard exit ved feil i Rapids er en pragmatisk pattern, men gir lite kontrollert nedstigning. Ved exit(1) kjører ikke shutdownHook fullt. Vurder å logge og la liveness-probe ta over (men da må probe sjekke Rapids-helse). |
| ⚠️ | **Ingen eksplisitt graceful shutdown av Javalin med drain-timeout** — `javalin.stop()` kan kutte in-flight requests. På Nais er dette mindre kritisk pga preStop-hook (`sleep 5`), men dokumenter det. |

**Anbefalinger:**
- Endre Nais-manifest:
  ```yaml
  resources:
    requests:
      cpu: 100m
      memory: 1Gi   # nærmere reelt forbruk
    limits:
      memory: 2Gi   # behold memory-limit, fjern cpu-limit
  ```
- Endre HikariCP til `maximumPoolSize = 5`, `minimumIdle = 1`, og dokumenter valgte verdier.

---

## 3. Arkitektur

| Vurdering | Funn |
|---|---|
| ✅ | Tydelig lagdeling: `Controller → Service → Repository` per domene (jobbsoker, arbeidsgiver, rekrutteringstreff, eier, innlegg, ki, sok). |
| ✅ | Domeneorientert pakkestruktur med egne `dto`-mapper. |
| ✅ | Eksplisitt transaksjonshåndtering via `DataSource.executeInTransaction { conn -> ... }` — ren og lesbar. |
| ✅ | Custom exceptions per domene (`RekrutteringstreffIkkeFunnetException`, `JobbsøkerIkkeSynligException`, osv.) mappet sentralt i `ExceptionMapping`. ProblemDetails (RFC 7807-aktig) som response. |
| ✅ | Schedulers er gated med `leaderElection.isLeader()` og avgrenset til egen klasse per ansvar. |
| ✅ | Rapids & Rivers brukt for asynkrone hendelser, REST for synkrone — riktig per Nav-mønster. |
| ⚠️ | **`App.kt` er stor og har mye wiring**: `startJavalin` instansierer ~12 controllere og services manuelt. Vurder enten Koin (lett-DI) eller å splitte i `WiringJavalin.kt`/`WiringSchedulers.kt`. Ikke kritisk, men gjør testing og lesbarhet bedre. |
| ⚠️ | **Dobbel-instansiering av repositories**: `arbeidsgiverRepository` og `kiLoggRepository` opprettes både i `start()` og `startJavalin()`. Den første instansen brukes ikke. Cleanup. |
| ⚠️ | **`ProblemDetails` har "bakoverkompatibilitets"-felter** (`hint`, `feil`, `feilkode`) — gir tre forskjellige feltnavn for "samme ting". Lag en plan for å konsolidere mot RFC 7807 (`type`, `title`, `detail`). |
| ⚠️ | **`AuthenticatedUser.fromJwt` returnerer null-instans hvis hverken NAVident eller PID finnes** — egentlig kaster den ikke, men `pid`-claim kalles på null hvis NAVident mangler og pid også mangler. Sjekk at `withClaimPresence(identClaim)` i JWTVerifier fanger dette tidligere. |
| ⚠️ | **Auth-filter-regex `/api/rekrutteringstreff(?:$|/.*)`**: Hvis dere noen gang legger til endepunkt som `/api/jobbsoker/...` (ikke under `/rekrutteringstreff/`), faller det utenfor auth. Vurder en allowlist-pattern eller en før-filter som default-deny + eksplisitt unntak for `/isalive`, `/isready`, `/metrics`, `/swagger`, `/openapi`, `/webjars`. |
| ⚠️ | **`AuthenticatedNavUser.veiledersKontor` er muterbar** og settes via `verifiserAutorisasjon`. Bivirkning på en domeneklasse. Vurder å gjøre det immutable: hent kontoret én gang i `fromJwt` eller via separat metode som returnerer `Either<Forbidden, Kontor>`. |

**Anbefalinger:**
- Sett opp en lett DI-løsning (Koin) eller et eksplisitt `Wiring.kt` med byggefunksjoner per modul.
- Når API har stabilisert seg: dropp legacy-feltene i `ProblemDetails` med v2-bump.

---

## 4. Endringssikkerhet og operasjon

| Vurdering | Funn |
|---|---|
| ✅ | Omfattende test-suite: 50+ testklasser med Testcontainers, MockOAuth2Server, WireMock, MockK. Egne autorisasjonstester per controller. |
| ✅ | Karakteriseringstester for SQL-views (sok-tester) — viktig for refactoring. |
| ✅ | Trivy-scan i CI (`navikt/toi-github-actions-workflows/.github/workflows/trivy-security-scan.yaml@v1`). |
| ✅ | Flyway-migrasjoner forsvarlig versjonert (`V1__init.sql`, `V2__kontorer.sql`, `V3__sok_indekser.sql`, `V4__jobbsoker_sok.sql`) + repeatable views (`R__...`). |
| ✅ | `prometheus.enabled: true` + `path: /metrics`. |
| 🟠 | **`deploy_dev_branch: refs/heads/svar-for-jobbsoker`** i `deploy-rekrutteringstreff-api.yaml` — ser ut som en glemt feature-branch som fortsatt kan deploye til dev. Bekreft og fjern hvis ikke aktivt brukt. |
| ⚠️ | **`logback.xml` ligger i app-roten med absolutt path-trigger** (`-Dlogback.configurationFile=/logback.xml` i Dockerfile). Uvanlig men dokumentert i Dockerfile-kommentar — OK valg, men sårbart hvis noen flytter filen. |
| ⚠️ | **Ingen runbook eller alert-actions med direktelink til logs** — alerts har link til logs.az.nav.no, men ingen "what to do"-tekst. Legg til kort runbook med vanlige feil og første handlinger. |
| ⚠️ | **Ingen forretningsmetrikker funnet** — kun JVM/HTTP-metrikker via auto-instrumentation. Vurder å eksponere `rekrutteringstreff_opprettet_total`, `jobbsoker_invitert_total`, `ki_validering_total{resultat=...}` som Prometheus-counters. Gir mye bedre monitorering enn error-log-alarm alene. |
| ⚠️ | **Alert "Appen har logget en error"** med `for: 1s` og `severity: critical` er svært støyende. En enkelt feil under deploy/oppstart vil paige folk. Vurder `> 5 errors over 10m` eller skill mellom predictable og uventede feil. |
| ⚠️ | **Ingen post-deploy-verifikasjon** dokumentert. Anbefalt: smoke-test-script som kalles etter deploy, eller minimum sjekkliste i README. |
| ⚠️ | **Distroless `java21` (Google) brukes** i Dockerfile, ikke Chainguard fra Nav-registry. Ikke kritisk, men Nav-anbefalingen er Chainguard for færre CVEs. |

**Anbefalinger:**
- Fjern eller flytt `deploy_dev_branch` etter merge.
- Skriv en kort `RUNBOOK.md` med de 3-5 vanligste feilene.
- Legg til forretnings-metrikker i `OpenAiClient`, `JobbsøkerService`, `RekrutteringstreffService`.
- Når Dockerfile endres neste gang: bytt til `europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-21`.

---

## Generell best practice (på tvers av perspektiver)

### Bra valg å fortsette med
- Kotlin-idiomatisk kode med data classes, sealed-klasser og extension-functions.
- Eksplisitt `Connection`-håndtering i transactionManager — lettere å resonnere om enn ThreadLocal.
- Konsekvent norsk navngivning på domeneobjekter (`Jobbsøker`, `Arbeidsgiver`, `Rekrutteringstreff`).
- `nowOslo()` som sentralt tidspunkt-API med ms-truncation for cross-platform-konsistens.
- Resilience4j + EhCache rundt token-utveksling.

### Småting å rydde
- `JacksonConfig.mapper` brukes konsistent — sjekk at `LeaderElection` også bruker den (i dag har den egen `jacksonObjectMapper()`).
- `companion object` med `log: Logger` i noen filer, top-level `log` extension i andre. Konsolider på én stil (forslag: `private val log = noClassLogger()` på toppen av filen).
- Mange `RuntimeException("Noe feil skjedde ved ...")` i `AccessTokenClient` — bruk en navngitt subklasse for å gi bedre exception-mapping.
- Sjekk at alle DTOs har `@JsonInclude(JsonInclude.Include.NON_NULL)` der det er ønskelig — i dag er det inkonsekvent.
- `apps/rekrutteringstreff-api/build/` er sjekket inn i fil-listen (snapshot) — verifiser at `.gitignore` faktisk holder build/ ute av git.

### Ting å vurdere på sikt
- Migrasjon fra rå JDBC til Kotliquery (Nav-standard). Mye repetitiv `prepareStatement().use { ... }`-kode i repositories kan skrives kortere og tryggere.
- API-versjonering: i dag finnes ett "v1" via `info.version = "1.0.0"`, men ingen URL-versjonering (`/api/v1/...`). Når breaking changes kommer er det smertefritt å begynne med `/api/v1` allerede nå.
- ProblemDetails-feltene `hint`/`feil`/`feilkode` er Nav-spesifikke. Vurder en plan for å konvergere mot ren RFC 7807.

---

## Konklusjon

**Godkjent med endringer.** Appen følger i hovedsak Nav-mønstre og har solid test- og auth-grunnstamme. De viktigste funnene er plattform-relaterte (CPU-limits, pool-størrelse) og enkelt å rette. Sikkerhetsmessig er kjernen god, men SHA-pinning av actions og logging av brukerinput i SecureLog bør fikses.

**Forslag til neste steg:**
1. Lag oppgaver i Trello/Jira for de fem prioriterte funnene øverst.
2. Vurder å delegere SHA-pinning til en automatisk PR via Renovate eller pinact.
3. Skriv en kort `RUNBOOK.md` og legg til forretnings-metrikker — gir mest verdi for minst innsats.

---

*Review utført av nav-pilot. Spør gjerne `@security-champion-agent` for grundigere trusselmodell, `@nais-agent` for plattform-detaljer eller `@observability-agent` for konkret metrikk-design.*
