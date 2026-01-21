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

### Egendefinerte exceptions

Domenespesifikke exceptions kastes fra service-laget:

| Exception                               | Bruksområde              | HTTP-status |
| --------------------------------------- | ------------------------ | ----------- |
| `RekrutteringstreffIkkeFunnetException` | Ressurs finnes ikke      | 404         |
| `UlovligOppdateringException`           | Ugyldig tilstandsendring | 409         |
| `UlovligSlettingException`              | Sletting ikke tillatt    | 409         |

### Global exception-håndtering

Javalin fanger opp exceptions og mapper til HTTP-statuskoder:

```kotlin
javalin.exception(RekrutteringstreffIkkeFunnetException::class.java) { e, ctx ->
    ctx.status(404).json(mapOf("feil" to (e.message ?: "Fant ikke rekrutteringstreffet")))
}

javalin.exception(UlovligOppdateringException::class.java) { e, ctx ->
    ctx.status(409).json(mapOf("feil" to (e.message ?: "Konflikt ved oppdatering")))
}
```

### Feilrespons-format

Alle feilresponser returneres som JSON med `feil`-felt:

```json
{
  "feil": "Kan ikke slette rekrutteringstreff fordi avhengige rader finnes.",
  "hint": "Fjern først alle jobbsøkere og arbeidsgivere fra treffet."
}
```

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
