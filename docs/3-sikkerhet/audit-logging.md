# Audit-logging

Audit-logging brukes for å spore hvem som har sett eller endret sensitive data. Dette er et lovpålagt krav for NAV-systemer som behandler personopplysninger.

## Formål

- **Sporbarhet**: Dokumentere hvem som har gjort hva og når
- **Revisjon**: Muliggjøre etterprøving av tilgang
- **Sikkerhet**: Oppdage uautorisert tilgang eller misbruk

---

## Implementasjon

### AuditLog-klassen

Audit-logging implementeres via `AuditLog`-klassen som bruker NAVs felles `audit-log`-bibliotek:

```kotlin
import no.nav.common.audit_log.cef.CefMessage
import no.nav.common.audit_log.log.AuditLogger

class AuditLog {
    companion object {
        private val auditLogger: AuditLogger = AuditLoggerImpl()

        fun loggVisningAvJobbsøkereTilhørendesRekrutteringstreff(
            navId: String,
            treffId: TreffId,
        ) {
            val loggmelding = "Jobbsøkere tilhørendes rekrutteringstreff med uuid $treffId er vist til Nav ansatt"
            val cefMessage = createCefMessage(null, navId, CefMessageEvent.ACCESS, loggmelding)
            auditLogger.log(cefMessage)
        }
    }
}
```

### CEF-format (Common Event Format)

Alle audit-logger skrives i CEF-format som er standard i NAV:

| Felt                | Beskrivelse                              | Eksempel                                       |
| ------------------- | ---------------------------------------- | ---------------------------------------------- |
| `applicationName`   | Applikasjonsnavn                         | `Rekrutteringsbistand`                         |
| `loggerName`        | Logger-identifikator                     | `rekrutteringstreff-api`                       |
| `event`             | Type hendelse                            | `ACCESS`                                       |
| `sourceUserId`      | NAV-ident som utførte handlingen         | `Z999999`                                      |
| `destinationUserId` | Fnr for personen det gjelder (valgfritt) | `12345678901`                                  |
| `msg`               | Beskrivelse av hendelsen                 | `Jobbsøkere tilhørendes rekrutteringstreff...` |

---

## Hva logges

### Visning av persondata

| Hendelse              | Trigger                                       | Logget informasjon  |
| --------------------- | --------------------------------------------- | ------------------- |
| Visning av jobbsøkere | GET `/api/rekrutteringstreff/{id}/jobbsokere` | NAV-ident, treff-ID |

### Fremtidige loggpunkter

Følgende hendelser bør vurderes for audit-logging:

- Opprettelse av jobbsøker på treff
- Sletting av jobbsøker fra treff
- Visning av enkeltpersons hendelseslogg
- Eksport av persondata

---

## SecureLog for sensitiv data

I tillegg til audit-logging bruker vi `SecureLog` for å logge sensitive data til egen logg som kun er tilgjengelig for teamet:

```kotlin
private val secureLogger = SecureLog(log)

// Persondata logges til secure log, ikke vanlig applikasjonslogg
secureLogger.info("Henter jobbsøker for fnr: ${fnr.asString}")
```

### Hvordan SecureLog fungerer

`SecureLog` wrapper en vanlig SLF4J-logger og legger til `TEAM_LOGS`-markeren:

```kotlin
private val teamLogsMarker: Marker = MarkerFactory.getMarker("TEAM_LOGS")

class SecureLog(private val logger: Logger): Logger {
    override fun info(msg: String?) = logger.info(teamLogsMarker, msg)
    // ... øvrige metoder
}
```

Logback-konfigurasjonen sender meldinger med `TEAM_LOGS`-marker til en egen loggfil som bare er tilgjengelig i Kibana for teammedlemmer.

---

## Tilgang til logger

### Audit-logger

Audit-logger er tilgjengelige i NAVs sentrale Arcsight-løsning og kan hentes ut ved revisjonsforespørsler.

### Secure logs

Secure logs er tilgjengelige i Kibana med filter på `TEAM_LOGS`-marker:

```
application: rekrutteringstreff-api AND markers: TEAM_LOGS
```

---

## Beste praksis

1. **Logg kun det nødvendige** - Ikke logg mer enn det som trengs for revisjon
2. **Aldri logg til vanlig logg** - Persondata skal alltid til SecureLog
3. **Bruk standardformater** - CEF for audit, strukturert JSON for app-logger
4. **Dokumenter loggpunkter** - Hold denne dokumentasjonen oppdatert

---

## Relaterte dokumenter

- [Tilgangsstyring](tilgangsstyring.md) - Hvem har tilgang til hva
- [Vedlikeholdbarhet](../6-kvalitet/vedlikeholdbarhet.md) - Logging-mønstre i koden
