# Gap-analyse: Spring Boot vs. vår Javalin-stack

Spring Boot gir mange ting «gratis» som vi i vår Javalin-baserte applikasjon enten har implementert selv, delvis har, eller mangler helt. Denne oversikten kartlegger hva Spring Boot tilbyr out-of-the-box og vurderer status i vår app.

## Oppsummering

| Kategori                                                               | Spring Boot                        | Vår status        | Prioritet |
| ---------------------------------------------------------------------- | ---------------------------------- | ----------------- | --------- |
| [Request correlation (callId/MDC)](#1-request-correlation-callidmdc)   | Auto via Sleuth/Micrometer Tracing | **Mangler**       | Høy       |
| [Request-/responslogging](#2-request-responslogging)                   | CommonsRequestLoggingFilter        | **Mangler**       | Høy       |
| [Metrikker og monitorering](#3-metrikker-og-monitorering)              | Actuator + Micrometer auto-config  | **Delvis**        | Høy       |
| [Sikkerhetshoder](#4-sikkerhetshoder)                                  | Spring Security defaults           | **Mangler**       | Middels   |
| [Helsesjekk med dybde](#5-helsesjekk-med-dybde)                        | Actuator health med DB-indikator   | **Mangler dybde** | Middels   |
| [Graceful shutdown](#6-graceful-shutdown)                              | Innebygd med draining              | **Delvis**        | Middels   |
| [Feilrespons-standardisering](#7-feilrespons-standardisering-rfc-7807) | RFC 7807 Problem Details           | **Delvis**        | Middels   |
| [Input-validering](#8-input-validering)                                | Bean Validation (JSR 380)          | **Manuell**       | Middels   |
| [Konfigurasjonshåndtering](#9-konfigurasjonshåndtering)                | Type-safe @ConfigurationProperties | **Enkel**         | Lav       |
| [Komprimering (gzip)](#10-komprimering-gzip)                           | `server.compression.enabled=true`  | **Mangler**       | Lav       |
| [Management-endepunkter](#11-management-endepunkter)                   | /info, /env, /beans, /mappings     | **Mangler**       | Lav       |
| [Circuit breaker](#12-circuit-breaker)                                 | Spring Cloud Circuit Breaker       | **Mangler**       | Lav       |
| [Tråd-pool-konfigurasjon](#13-tråd-pool-konfigurasjon)                 | `server.tomcat.threads.*`          | **Mangler**       | Lav       |
| [API-versjonering](#14-api-versjonering)                               | Ingen standard, men god støtte     | **Mangler**       | Lav       |
| [Database query-timeouts](#15-database-query-timeouts)                 | Spring Data defaults               | **Mangler**       | Lav       |

---

## Ting vi mangler eller bør vurdere

### 1. Request correlation (callId/MDC)

**Spring Boot:** Micrometer Tracing (tidl. Spring Cloud Sleuth) legger automatisk `traceId` og `spanId` i MDC. Alle logglinjer får dermed korrelasjons-ID. I NAV-kontekst propageres `Nav-Call-Id` og `Nav-Consumer-Id` via filtere.

**Vår app:** Ingen MDC-bruk, ingen `callId`/`requestId`-propagering. Det betyr at vi ikke kan korrelere logglinjer som tilhører samme request, og vi sender ikke `Nav-Call-Id` videre til downstream-tjenester.

**Anbefaling:** Implementer et Javalin `before`-filter som:

1. Leser `Nav-Call-Id` fra innkommende header (eller genererer UUID)
2. Setter verdien i SLF4J MDC
3. Sender den videre i utgående HTTP-kall
4. Rydder MDC i `after`-filter

```kotlin
app.before { ctx ->
    val callId = ctx.header("Nav-Call-Id") ?: UUID.randomUUID().toString()
    MDC.put("callId", callId)
    ctx.header("Nav-Call-Id", callId)
}
app.after { MDC.clear() }
```

Logback-config (`logback.xml`) må oppdateres til å inkludere `callId` i meldingsformatet.

### 2. Request-/responslogging

**Spring Boot:** `CommonsRequestLoggingFilter` logger method, URI, headers, payload og varighet. Kan aktiveres med én bean.

**Vår app:** Ingen forespørselslogging. Vi logger ikke metode, path, statuskode eller responstid for innkommende requests.

**Anbefaling:** Legg til et `after`-filter som logger request-info:

```kotlin
app.after { ctx ->
    val duration = System.currentTimeMillis() - ctx.attribute<Long>("startTime")!!
    log.info("${ctx.method()} ${ctx.path()} -> ${ctx.status()} (${duration}ms)")
}
app.before { ctx ->
    ctx.attribute("startTime", System.currentTimeMillis())
}
```

Dette gir oss oversikt over trafikkmønstre, trege endepunkter og feilrater uten ekstern verktøy.

### 3. Metrikker og monitorering

**Spring Boot:** Actuator + Micrometer gir automatisk:

- JVM-metrikker (heap, GC, threads)
- HTTP request-metrikker (per endepunkt: antall, latens, feilrate)
- Connection pool-metrikker (HikariCP)
- Cache-metrikker
- Custom metrikker via `MeterRegistry`

**Vår app:**

- Prometheus-scraping er aktivert i NAIS-config
- HikariCP-metrikker er konfigurert i aktivitetskort-appen, men **ikke i hoved-API-et**
- Ingen JVM-metrikker
- Ingen HTTP request-metrikker per endepunkt
- Ingen custom forretningsmetrikker

**Anbefaling:**

1. Legg til Micrometer Prometheus-registry i hoved-API-et
2. Koble HikariCP til Micrometer (`hikariConfig.metricRegistry = prometheusMeterRegistry`)
3. Legg til Javalin-plugin eller `after`-filter for HTTP request-metrikker
4. Eksponer JVM-metrikker via `JvmMemoryMetrics`, `JvmGcMetrics`, `JvmThreadMetrics`
5. Vurder forretningsmetrikker (antall treff opprettet, invitasjoner sendt, etc.)

### 4. Sikkerhetshoder

**Spring Boot:** Spring Security legger automatisk til:

- `X-Content-Type-Options: nosniff`
- `X-Frame-Options: DENY`
- `Strict-Transport-Security: max-age=31536000`
- `X-XSS-Protection: 0` (disabled, erstattet av CSP)
- `Cache-Control: no-cache, no-store`

**Vår app:** Ingen sikkerhetshoder settes. API-et er ikke direkte eksponert for nettlesere (frontend proxyer), men det er god praksis å ha de likevel som defense-in-depth.

**Anbefaling:** Legg til et `after`-filter:

```kotlin
app.after { ctx ->
    ctx.header("X-Content-Type-Options", "nosniff")
    ctx.header("X-Frame-Options", "DENY")
    ctx.header("Cache-Control", "no-cache, no-store, must-revalidate")
}
```

### 5. Helsesjekk med dybde

**Spring Boot Actuator:** `/actuator/health` sjekker automatisk:

- Databasetilkobling (kjører `SELECT 1`)
- Diskplass
- Custom health indicators

Readiness vs. liveness er separert:

- `/actuator/health/liveness` — appen er oppe
- `/actuator/health/readiness` — appen er klar til å ta trafikk

**Vår app:** `/isalive` og `/isready` returnerer statiske strenger uten noen sjekk. Readiness rapporteres som OK selv om databasen er nede eller Flyway-migrering ikke er ferdig.

**Anbefaling:**

- `/isalive`: Behold enkel (appen lever)
- `/isready`: Sjekk databasetilkobling og evt. at Flyway er ferdig:

```kotlin
app.get("/isready") { ctx ->
    try {
        dataSource.connection.use { it.prepareStatement("SELECT 1").execute() }
        ctx.result("ready")
    } catch (e: Exception) {
        ctx.status(503).result("not ready: ${e.message}")
    }
}
```

### 6. Graceful shutdown

**Spring Boot:** `server.shutdown=graceful` + `spring.lifecycle.timeout-per-shutdown-phase=30s` gir:

1. Stopp å ta nye requests
2. Vent på at pågående requests fullføres (med timeout)
3. Stopp connection pools og schedulers

**Vår app:** `App.close()` stopper schedulere og Javalin, men:

- Ingen JVM shutdown hook registrert
- Ingen draining-fase (pågående requests kan bli avbrutt)
- Avhenger av at K8s SIGTERM håndteres riktig av Javalin/Jetty

**Anbefaling:** Vurder om Javalin/Jetty sin innebygde SIGTERM-håndtering er tilstrekkelig, eller om vi trenger:

- `Runtime.getRuntime().addShutdownHook` som kaller `app.close()`
- NAIS `terminationGracePeriodSeconds` justert
- En readiness-gate som settes til false før shutdown starter

### 7. Feilrespons-standardisering (RFC 7807)

**Spring Boot 3:** Innebygd støtte for RFC 7807 Problem Details (`application/problem+json`) med feltene `type`, `title`, `status`, `detail`, `instance`.

**Vår app:** Egendefinert feilformat med `feil`/`hint`-felt, men:

- KI-valideringsfeil bruker `feilkode`/`melding` (inkonsistent med resten)
- Ingen `type`-URI for maskinlesbar feilklassifisering
- Mangel på `status`-felt i body (klienten må lese HTTP-statuskoden)

**Anbefaling:** Vurder å standardisere feilresponser. Full RFC 7807 er overkill for et internt API, men konsistent format på tvers av alle feiltyper er viktig. Minimum: sørg for at KI-feil og andre feil bruker samme feltstruktur.

### 8. Input-validering

**Spring Boot:** Jakarta Bean Validation (`@Valid`, `@NotBlank`, `@Size`, `@Email`, etc.) med automatisk 400-respons.

**Vår app:** All validering er manuell med `require()`, `IllegalArgumentException` og domene-spesifikke sjekker.

**Vurdering:** Manuell validering fungerer fint for vår kodestørrelse. Bean Validation gir mest verdi i større prosjekter. Ingen umiddelbar handling nødvendig, men vær oppmerksom på at valideringslogikk kan bli spredd utover og inkonsistent over tid.

### 9. Konfigurasjonshåndtering

**Spring Boot:** Type-safe `@ConfigurationProperties`, profiler (`application-dev.yml`), validering av config ved oppstart.

**Vår app:** Direkte `System.getenv()` med en hjelpefunksjon som kaster exception ved manglende verdi. Ingen type-validering, ingen gruppering.

**Vurdering:** Fungerer greit for vår skala. Ved behov kan et lett config-bibliotek som [Hoplite](https://github.com/sksamuel/hoplite) vurderes, men dette er lav prioritet.

### 10. Komprimering (gzip)

**Spring Boot:** `server.compression.enabled=true` aktiverer gzip for responser over en viss størrelse.

**Vår app:** Ingen komprimering. Responser sendes ukomprimert.

**Vurdering:** Lav prioritet da API-et brukes server-til-server (Next.js-proxy) i et internt nettverk. Men for store responser (f.eks. jobbsøker-lister) kan gzip redusere nettverkstid.

### 11. Management-endepunkter

**Spring Boot Actuator:**

- `/info` — app-versjon, git-info, bygg-tidspunkt
- `/env` — miljøvariabler (filtrert)
- `/beans` — alle registrerte beans
- `/mappings` — alle HTTP-endpoints
- `/configprops` — config-verdier
- `/threaddump` — tråddump
- `/heapdump` — heap-dump

**Vår app:** Kun `/isalive`, `/isready`, `/swagger`, `/openapi`.

**Vurdering:** De fleste Actuator-endepunkter er nice-to-have. Et `/internal/info`-endepunkt med app-versjon og byggtidspunkt kan være nyttig for debugging i produksjon.

### 12. Circuit breaker

**Spring Boot:** Spring Cloud Circuit Breaker med Resilience4j gir automatisk circuit breaking med half-open/open/closed states.

**Vår app:** Resilience4j Retry brukes for tilgangstoken og ModiaKlient, men ingen circuit breaker. Ved vedvarende feil i downstream-tjenester vil vi fortsette å sende forespørsler som feiler.

**Vurdering:** Lav prioritet gitt få eksterne avhengigheter, men bør vurderes for ModiaKlient og OpenAI-kallet.

### 13. Tråd-pool-konfigurasjon

**Spring Boot:** Konfigurerbar via `server.tomcat.threads.max`, `server.tomcat.threads.min-spare`, etc.

**Vår app:** Javalin/Jetty kjører med default tråd-pool. Ingen eksplisitt konfigurasjon.

**Vurdering:** Default er vanligvis greit, men ved lastproblemer bør vi vite hva defaults er og kunne justere.

### 14. API-versjonering

**Spring Boot:** Ingen innebygd standard, men god støtte via path-prefiks eller header-basert routing.

**Vår app:** Ingen versjonering. API-endepunkter bruker `/api/rekrutteringstreff/...`.

**Vurdering:** Ikke nødvendig nå, men verdt å ha en strategi klar (f.eks. path-prefiks `/api/v2/...`) dersom vi må gjøre breaking changes.

### 15. Database query-timeouts

**Spring Boot:** Spring Data setter default query-timeouts via `spring.jpa.properties.jakarta.persistence.query.timeout`.

**Vår app:** HikariCP har `connectionTimeout` og `leakDetectionThreshold`, men ingen query-level timeout (`Statement.setQueryTimeout`). En treg query kan blokkere en tråd i ubegrenset tid.

**Anbefaling:** Vurder å sette `socketTimeout` på JDBC-URL-en eller bruke `statement_timeout` i PostgreSQL.

---

## Ting Spring Boot tilbyr som vi ikke trenger

| Feature                    | Hvorfor vi ikke trenger det                             |
| -------------------------- | ------------------------------------------------------- |
| CORS-konfigurasjon         | API-et er ikke eksponert direkte for nettlesere         |
| Session management         | Stateless API med JWT                                   |
| CSRF-beskyttelse           | Ikke relevant for API-til-API-kommunikasjon             |
| Thymeleaf / view resolving | Ingen server-side rendering                             |
| Spring Data / JPA          | Vi bruker ren SQL med vilje (arkitekturbeslutning)      |
| Spring Cloud Config        | NAIS håndterer config via yaml og secrets               |
| Spring Profiles            | NAIS yaml-varianter (dev-gcp/prod-gcp) dekker behovet   |
| Auto-configuration magic   | Eksplisitt oppsett gir oss bedre kontroll og forståelse |

## Anbefalt prioritering

**Bør gjøres snart:**

1. Request correlation med MDC (callId) — kritisk for feilsøking i produksjon
2. Request-logging (metode, path, status, varighet) — grunnleggende observability
3. Micrometer-metrikker i hoved-API-et (JVM + HikariCP + HTTP request-metrikker)

**Bør vurderes:** 4. Sikkerhetshoder (defense-in-depth) 5. Dybde-helsesjekk i `/isready` (database-sjekk) 6. Konsistent feilformat på tvers av alle exception-typer

**Kan vente:** 7. Gzip-komprimering 8. Circuit breaker for eksterne kall 9. Database query-timeouts 10. `/info`-endepunkt med versjon og byggtidspunkt
