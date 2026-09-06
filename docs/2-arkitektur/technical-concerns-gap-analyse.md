# Gap-analyse: Konfigurasjon og arkitektur

Denne oversikten kartlegger funksjonalitet vi mangler eller kun delvis har, gruppert etter kilde. Detaljene for utbedring tas i egne oppgaver.

Prioriteringer: **Kritisk** (produksjonskritisk), **Viktig** (bør utbedres), **Kjekt å ha** (vurderes ved behov).

---

## 1. NAIS-plattformfunksjoner vi ikke bruker ennå

Funksjoner NAIS tilbyr som vi kan aktivere via konfigurasjon.

### Kritisk

1. **Observability: auto-instrumentation**
   - **Tilgjengelig i NAIS:** `spec.observability.autoInstrumentation` gir OTel Java Agent uten kodeendringer.
   - **Status hos oss:** `observability.logging` er aktivert (Loki + Elastic), men vi mangler auto-instrumentation. Ingen trace-ID i logglinjer, ingen systematisk HTTP-instrumentering, og `Nav-Call-Id` propageres ikke.
   - **Tiltak:**
     1. Aktiver `autoInstrumentation` i nais.yaml for alle tre apper:
        ```yaml
        spec:
          observability:
            autoInstrumentation:
              enabled: true
              runtime: java
        ```
     2. Legg til `opentelemetry-logback-mdc-1.0` som dependency og wrapp logback-appenderen med `OpenTelemetryAppender` for trace-log-korrelasjon i Grafana.
     3. Valgfritt: Javalin-filter som leser `Nav-Call-Id`-headeren og setter den i MDC for bakoverkompatibilitet.
   - **Hva dette dekker:** Distribuert tracing (HTTP, JDBC, Kafka), trace-ID/span-ID i MDC, HTTP-metrikker til Prometheus, og W3C Trace Context-propagering — uten kodeendringer.
   - **Referanse:** [NAIS auto-instrumentation](https://doc.nais.io/observability/how-to/auto-instrumentation/), [Trace-log-korrelasjon](https://doc.nais.io/observability/tracing/how-to/correlate-traces-logs/)

### Viktig

2. **CloudSQL-herding (prod)**
   - **Tilgjengelig i NAIS:** `pointInTimeRecovery`, `highAvailability`, `maintenance.day/hour` og `insights.enabled` på `gcp.sqlInstances`.
   - **Status hos oss:** Ingen av disse er konfigurert. Vi har kun `diskAutoresize: true` i prod.
   - **Tiltak:** Legg til i nais.yaml for begge apper med database:
     ```yaml
     gcp:
       sqlInstances:
         - pointInTimeRecovery: true # Gjenoppretting til vilkårlig tidspunkt
           highAvailability: true # Failover-replika i prod
           insights:
             enabled: true # Query insights for ytelsessporing
           maintenance:
             day: 1 # Mandag
             hour: 4 # Kl. 04 UTC
     ```
   - `highAvailability` koster ekstra, men sikrer mot soneproblemer.

3. **Startup-probe (JVM-oppstart)**
   - **Tilgjengelig i NAIS:** `spec.startup` med egne terskler, uavhengig av liveness/readiness.
   - **Status hos oss:** Ikke konfigurert. `aktivitetskort` bruker `initialDelay: 40` på liveness i stedet.
   - **Tiltak:**
     ```yaml
     spec:
       startup:
         path: /isalive
         initialDelay: 10
         periodSeconds: 2
         failureThreshold: 30 # 10 + 2×30 = 70s maks oppstartstid
     ```
   - **Fordel:** Kubernetes skiller mellom treg oppstart og hengt prosess, og liveness slipper høy `initialDelay`.

4. **Graceful shutdown: `preStopHook` og `terminationGracePeriodSeconds`**
   - **Tilgjengelig i NAIS:** `spec.preStopHook` og `spec.terminationGracePeriodSeconds` (0–180, default 30).
   - **Status hos oss:** Ikke eksplisitt konfigurert. NAIS injiserer default `preStopHook` med `sleep 5`, men vi har ikke verifisert at dette er tilstrekkelig.
   - **Tiltak:** Vurder å sette eksplisitt dersom vi opplever draining-problemer:
     ```yaml
     spec:
       terminationGracePeriodSeconds: 60
       preStopHook:
         exec:
           command: ["sleep", "5"]
     ```

5. **SBOM (Software Bill of Materials)**
   - **Tilgjengelig i NAIS:** `nais/docker-build-push` støtter `salsa: true` for SBOM-generering og SLSA-attestering.
   - **Status hos oss:** Vi bruker `nais/docker-build-push@v0` uten `salsa: true`. Trivy-scan er på plass som eget steg.
   - **Tiltak:** Legg til i deploy-template.yaml:
     ```yaml
     - uses: nais/docker-build-push@v0
       with:
         salsa: true
     ```
   - **Hva dette gir:** Supply chain-attestering og synlighet i NAIS Console.

### Kjekt å ha

6. **Utvidede Prometheus-alarmer**
   - **Status hos oss:** Tre grunnleggende alarmer i `alerts.yaml`: app-down, error-logging og warning-logging. Kun for `rekrutteringstreff-api`.
   - **Tiltak:** Vurder alarmer for:
     - HTTP-latens (p95/p99 over terskel)
     - Feilrate (5xx over X%)
     - Database connection pool-metrikker
     - Alarmer for `minside-api` og `aktivitetskort` (mangler helt i dag)

7. **Alerts i dev**
   - **Status hos oss:** Alerts deployes kun til prod (deploy-template.yaml: `RESOURCE: ...nais.yaml,...alerts.yaml` kun i prod-jobben).
   - **Tiltak:** Deploy alerts også til dev for å teste alarmregler før prod.

8. **Feature toggling (Unleash)**
   - **Tilgjengelig i NAIS:** Unleash som plattformtjeneste.
   - **Status hos oss:** Bruker miljøvariabelen `pilotkontorer` for gradvis utrulling.
   - **Vurdering:** Kan være nyttig ved flere feature-flagg. Foreløpig fungerer `pilotkontorer` for enkel pilot-styring.

---

## 2. Spring-inspirerte applikasjonsforbedringer

Funksjonalitet Spring Boot gir ut-av-boksen, som vi mangler eller kun delvis har.

### Viktig

9. **Sikkerhetsheaders**
   - **I Spring:** Spring Security setter automatisk anbefalte HTTP-headere.
   - **Status hos oss:** Mangler. API-et nås kun via frontend-proxy, men headere er viktig defense-in-depth.
   - **Tiltak:** Legg til filter som setter standard sikkerhetshoder (`X-Frame-Options`, `X-Content-Type-Options` m.fl.).

10. **Dypere helsesjekk (/isready)**
    - **I Spring:** Actuator sjekker automatisk underliggende avhengigheter, inkludert database-liveness.
    - **Status hos oss:** `/isready` er statisk og rapporterer OK selv om db er nede.
    - **Tiltak:** Utvid readiness-sjekk til å prøve en rask databaseoperasjon (f.eks. `SELECT 1`).

11. **Standardisert feilrespons (RFC 7807)**
    - **I Spring:** Innebygd støtte for Problem Details for HTTP APIs.
    - **Status hos oss:** Delvis og ulik struktur (særlig KI-validering vs andre domene-feil).
    - **Tiltak:** Standardiser feilmodellen på tvers av API-laget.

12. **Graceful shutdown (app-nivå)**
    - **I Spring:** Innebygd app-draining ved SIGTERM.
    - **Status hos oss:** Javalin og schedulers stanses via `App.close()`, men full draining-fase for kjørende kall er ikke avklart.
    - **Tiltak:** Verifiser at Javalin sin shutdown er tilstrekkelig, eventuelt legg til runtime hooks.
    - **Se også:** Seksjon 1, punkt 4 for NAIS-konfig (`preStopHook` / `terminationGracePeriodSeconds`).

### Kjekt å ha

13. **Management-endepunkter (/info)**
    - **I Spring:** Actuator gir systemtilstand og kjøretidsinformasjon.
    - **Status hos oss:** Mangler `/info` for å raskt verifisere versjon/byggtidspunkt i prod.

14. **Circuit Breaker**
    - **I Spring:** Spring Cloud Circuit Breaker.
    - **Status hos oss:** Har `resilience4j-retry`, men mangler circuit breaker for eksterne kall.

15. **Input-validering**
    - **I Spring:** Jakarta Bean Validation (JSR-380, `@Valid`).
    - **Status hos oss:** Manuelle sjekker med `require()`.

16. **Komprimering (Gzip/Brotli)**
    - **I Spring:** Slås på med én property.
    - **Status hos oss:** Mangler komprimering av større payloads på API-nivå.

17. **Konfigurasjonshåndtering**
    - **I Spring:** Typesikker, automatisk oppsett med `@ConfigurationProperties`.
    - **Status hos oss:** `System.getenv()` med manuell feilhåndtering.

    ## Funksjonalitet vi avstår fra

Noen deler av Spring-økosystemet vil vi uansett ikke benytte oss av da de erstattes av NAV-standarder eller andre verktøy:

- **CORS / CSRF-beskyttelse:** API-et rutes via Next.js backend, og brukes for øyeblikket backend-to-backend.
- **Session management:** Vi baserer oss utelukkende på stateløse innlogginger med JWT (TokenX / Entra-ID).
- **Spring Data / JPA:** Arkitekturbeslutning tatt på å bruke Kotlin, ren SQL, og JDBC istedenfor tunge ORM-rammeverk.
- **Spring Cloud Config/Profiles:** Løses ved hjelp av NAIS sin yaml-konfigurasjon for dev og prod.
- **Structured logging:** Allerede løst med LogstashEncoder i logback.xml — JSON-formatert logging til stdout og team-logs.

---

## 3. Andre forbedringer

Ting som ikke er dekket av NAIS-konfig eller Spring-paralleller, men som er verdt å vurdere.

### Viktig

18. **HikariCP-metrikker til Prometheus**
    - **Status hos oss:** `aktivitetskort` eksponerer pool-metrikker via `metricRegistry = meterRegistry`, men `rekrutteringstreff-api` gjør det ikke — til tross for at den har det største poolet (15 tilkoblinger).
    - **Tiltak:** Sett `metricRegistry` på HikariConfig i `App.kt` slik at pool-metrikker (aktive/ledige/ventende tilkoblinger) blir synlige i Grafana.
    - **Sammenheng:** Gjør punkt 6 (alarmer) mer nyttig — vi kan alarmere på connection pool-utnyttelse.

19. **Database query-timeouts**
    - **Status hos oss:** HikariCP har `connectionTimeout`, `idleTimeout` og `maxLifetime`, men ingen `statement_timeout`. En treg query kan holde en tilkobling og blokkere poolen.
    - **Tiltak:** Sett `statement_timeout` via HikariCP `connectionInitSql` eller `dataSourceProperties`:
      ```kotlin
      connectionInitSql = "SET statement_timeout = '30s'"
      ```

### Kjekt å ha

20. **Automatisk avhengighetsoppdatering (Dependabot/Renovate)**
    - **Status hos oss:** Ingen `.github/dependabot.yml` eller `renovate.json` funnet. Avhengigheter oppdateres manuelt.
    - **Tiltak:** Konfigurer Dependabot for Gradle og npm på tvers av repos.

---
