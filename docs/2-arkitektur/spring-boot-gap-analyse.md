# Gap-analyse: Spring Boot vs. Javalin

Denne oversikten kartlegger funksjonalitet Spring Boot gir ut-av-boksen, som vi mangler eller kun delvis har i vår Javalin-applikasjon. Detaljene for utbedring tas i egne oppgaver.

## Prioriteringer

### Kritisk (Må utbedres)

_Dette er funksjonalitet som er essensiell for drift, sikkerhet og feilsøking i produksjon._

1. **Request correlation (callId/MDC)**
   - **I Spring:** Autokonfigurert via Micrometer Tracing for sporing.
   - **Status hos oss:** Mangler. `Nav-Call-Id` propageres ikke, og logglinjer mangler korrelasjons-ID.
   - **Tiltak:** Implementer Javalin-filter for håndtering av `Nav-Call-Id` og logg-korrelasjon (MDC).

2. **Request- og responslogging**
   - **I Spring:** Innebygde filtere for logging av trafikk (metode, path, statuskode, responstid).
   - **Status hos oss:** Mangler forespørselslogging for innkommende HTTP-kall.
   - **Tiltak:** Legg til filter for å systematisk logge grunnleggende HTTP-request-informasjon.

3. **Metrikker og monitorering (HTTP og Database)**
   - **I Spring:** Actuator og Micrometer eksponerer automatisk HTTP- og connection pool-metrikker.
   - **Status hos oss:** Delvis innført. Aktivitetskort-appen har Prometheus-metrikker for HikariCP, men rekrutteringstreff-api mangler dette. Ingen systematisk HTTP-instrumentering.
   - **Tiltak:** Legg til HTTP-metrikker for Javalin-endepunkter og registrer HikariCP `metricRegistry` i rekrutteringstreff-api.

### Viktig (Bør utbedres)

_Funksjonalitet som øker robustheten og standardiseringen i API-et._

4. **Sikkerhetsheaders**
   - **I Spring:** Spring Security setter automatisk anbefalte HTTP-headere.
   - **Status hos oss:** Mangler. API-et nås riktignok kun via frontend-proxy, men headere er viktig defense-in-depth.
   - **Tiltak:** Legg til filter som setter standard sikkerhetshoder (f.eks. `X-Frame-Options`, `X-Content-Type-Options`).

5. **Dypere helsesjekk (/isready)**
   - **I Spring:** Actuator sjekker automatisk underliggende avhengigheter, inkludert database-liveness og kjørte migreringer.
   - **Status hos oss:** `/isready` er i dag statisk, og rapporterer OK selv om db er uklar/nede.
   - **Tiltak:** Utvid eksisterende readiness-sjekk til å prøve en rask databaseoperasjon (f.eks. `SELECT 1`).

6. **Standardisert feilrespons (RFC 7807)**
   - **I Spring:** Innebygd støtte for Problem Details for HTTP APIs (RFC 7807).
   - **Status hos oss:** Delvis og ulik struktur (særlig KI-validering vs andre domene-feil).
   - **Tiltak:** Standardiser feilmodellen på tvers av hele API-laget.

7. **Graceful shutdown og draining**
   - **I Spring:** Innebygd støtte for app-draining ved SIGTERM-mottak i Kubernetes.
   - **Status hos oss:** Delvis. Javalin og schedulers stanses via `App.close()`, men vi har ikke avklart full draining-fase for kjørende kall.
   - **Tiltak:** Vurder om Javalin sin shutdown er god nok, eller legg til runtime hooks for riktig stenging.

### Kjekt å ha (Vurderes ved behov)

_Optimaliseringer vi fint kan leve uten foreløpig, men som bør nevnes i en gap-analyse._

8. **Management-endepunkter (/info, /env)**
   - **I Spring:** Actuator gir detaljert systemtilstand og kjøretidsinformasjon på en standardisert måte.
   - **Status hos oss:** Mangler `/info` etc for å raskt verifisere versjon og byggtidspunkt i produksjon.

9. **Circuit Breaker og Timeouts**
   - **I Spring:** Spring Cloud Circuit Breaker og robuste konfigurasjoner for database-/query-timeouts.
   - **Status hos oss:** Har `resilience4j-retry`, men mangler circuit breaker for eksterne kall. Ingen egne query-timeouts på databasenivå.

10. **Input-validering**
    - **I Spring:** Jakarta Bean Validation (JSR-380, f.eks. `@Valid`).
    - **Status hos oss:** Manuelle sjekker med `require()` i koden.
11. **Komprimering (Gzip/Brotli)**
    - **I Spring:** Slås på med en enkel property («server.compression.enabled»).
    - **Status hos oss:** Mangler komprimering av større payloads på API-nivå.

12. **Konfigurasjonshåndtering**
    - **I Spring:** Typesikker, automatisk oppsett med `@ConfigurationProperties`.
    - **Status hos oss:** Enkel mapping via miljøvariabler og `System.getenv()`.

## Funksjonalitet fra Spring vi avstår fra

Noen deler av Spring-økosystemet vil vi uansett ikke benytte oss av da de erstattes av NAV-standarder eller andre verktøy:

- **CORS / CSRF-beskyttelse:** API-et rutes via Next.js backend, og brukes for øyeblikket backend-to-backend.
- **Session management:** Vi baserer oss utelukkende på stateløse innlogginger med JWT (TokenX / Entra-ID).
- **Spring Data / JPA:** Arkitekturbeslutning tatt på å bruke Kotlin, ren SQL, og JDBC istedenfor tunge ORM-rammeverk.
- **Spring Cloud Config/Profiles:** Løses ved hjelp av NAIS sin yaml-konfigurasjon for dev og prod.
- **Structured logging:** Allerede løst med LogstashEncoder i logback.xml — JSON-formatert logging til stdout og team-logs.
