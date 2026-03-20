# Vedlikeholdbarhet

## Modularitet

Applikasjonen er strukturert i separate moduler med klare ansvarsområder:

- **Controller-lag**: Håndterer HTTP-forespørsler og autorisasjon
- **Service-lag**: Forretningslogikk og orkestrering
- **Repository-lag**: Database-tilgang med SQL

Hver modul (jobbsøker, arbeidsgiver, rekrutteringstreff, innlegg) følger samme struktur.

## Hendelsesdrevet arkitektur

Nye funksjoner kan legges til uten å endre eksisterende kode:

- Publiser hendelser til Rapids & Rivers
- Opprett nye lyttere som reagerer på hendelsene
- Schedulere håndterer periodiske operasjoner

## Testbarhet

- Repository-klasser kan mockes for enhetstester
- Integrasjonstester bruker testdatabase og MockOAuth2Server
- WireMock simulerer eksterne tjenester

---

## Kodekonvensjoner og navnestandarder

### Klassenavn

| Type              | Suffiks       | Eksempler                                             |
| ----------------- | ------------- | ----------------------------------------------------- |
| HTTP-håndtering   | `*Controller` | `RekrutteringstreffController`, `JobbsøkerController` |
| Forretningslogikk | `*Service`    | `RekrutteringstreffService`, `ArbeidsgiverService`    |
| Databasetilgang   | `*Repository` | `RekrutteringstreffRepository`, `InnleggRepository`   |

### Handler-metoder

Metoder som håndterer HTTP-forespørsler navngis med suffiks `Handler()`:

```kotlin
opprettRekrutteringstreffHandler()
hentAlleRekrutteringstreffHandler()
oppdaterRekrutteringstreffHandler()
slettRekrutteringstreffHandler()
```

### API-endepunkter

Endepunkter følger RESTful konvensjoner med små bokstaver og kebab-case:

```
/api/rekrutteringstreff
/api/rekrutteringstreff/{id}
/api/rekrutteringstreff/{id}/hendelser
/api/rekrutteringstreff/{id}/publiser
```

---

## Feilhåndteringsstrategi

### ProblemDetails (RFC 7807)

For vedlikeholdbarhet skal alle feil gå gjennom `ExceptionMapping.kt` og returneres som `ProblemDetails`.

Dette gjør løsningen enklere å vedlikeholde fordi vi får:

- ett felles feilformat mot klientene
- ett sted å endre mapping fra exceptions til HTTP-responser
- enklere tester og mindre spesiallogikk i controllere

Typiske exceptions:

| Exception                               | Bruksområde              | HTTP-status |
| --------------------------------------- | ------------------------ | ----------- |
| `IllegalArgumentException`              | Ugyldig input            | 400         |
| `SvarfristUtløptException`              | Svarfrist utgått         | 400         |
| `JobbsøkerIkkeSynligException`          | Jobbsøker ikke synlig    | 403         |
| `RekrutteringstreffIkkeFunnetException` | Ressurs finnes ikke      | 404         |
| `JobbsøkerIkkeFunnetException`          | Jobbsøker finnes ikke    | 404         |
| `UlovligOppdateringException`           | Ugyldig tilstandsendring | 409         |
| `UlovligSlettingException`              | Sletting ikke tillatt    | 409         |
| `KiValideringsException`                | KI-validering feilet     | 422         |

### Unngå Javalins HttpResponseException for valideringsfeil

Ikke bruk Javalins `BadRequestResponse`, `ForbiddenResponse` osv. for valideringsfeil. De omgår `ExceptionMapping` og gjør feilhåndteringen mindre ensartet.

**Gjør dette:**

```kotlin
throw IllegalArgumentException("Ugyldig visning: $it")
```

**Ikke gjør dette:**

```kotlin
throw BadRequestResponse("Ugyldig visning: $it")  // Omgår ProblemDetails!
```

`ForbiddenResponse` kan brukes for autorisasjon, men ikke for inputvalidering.

---

## Logging og overvåking

### Logger-instanser

To mønstre for å opprette loggere:

```kotlin
// Extension property for klasser
val Any.log: Logger
    get() = LoggerFactory.getLogger(this::class.java)

// Top-level funksjon
fun noClassLogger(): Logger = LoggerFactory.getLogger(callerClassName)
```

### SecureLog for sensitiv data

Persondata logges til separat logg med `TEAM_LOGS`-marker:

```kotlin
private val secureLogger = SecureLog(log)

// Brukes ved logging av fnr, navn osv:
secureLogger.info("Henter jobbsøker for fnr: ${fnr.asString}")
```

### AuditLog for sporingslogg

Sporingslogg i CEF-format for revisjonsformål:

```kotlin
AuditLog.loggVisningAvJobbsøkereTilhørendesRekrutteringstreff(navId, treffId)
```

Se [audit-logging.md](../3-sikkerhet/audit-logging.md) for detaljer.

### Health-endepunkter

Standard Kubernetes health-sjekker:

| Endepunkt  | Formål          |
| ---------- | --------------- |
| `/isalive` | Liveness-probe  |
| `/isready` | Readiness-probe |
