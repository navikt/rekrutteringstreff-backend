# Audit-logging

Audit-logging brukes for å spore hvem som har sett eller endret sensitive data. Dette er et lovpålagt krav for NAV-systemer som behandler personopplysninger.

## Formål og prinsipper

Audit-logging brukes primært for å kunne svare på **innsynsbegjæringer** der en borger lurer på hvem som har sett deres data.

Det er viktig å merke seg følgende prinsipper:

- **Fokus på oppslag**: Audit-logging brukes for å spore hvem som har _sett_ (lest) data. Det skal _ikke_ brukes for å logge operasjoner som utføres _på_ en person (f.eks. vedtak eller endringer), da dette dekkes av andre mekanismer.
- **Entry point logging**: Det er ikke nødvendig å logge i flere ledd i samme kall. Det holder oftest å logge i entry point, for eksempel i en controller, for å dekke behovet for sporbarhet.

## Koordinering med ArcSight

ArcSight er systemet som har kontroll på visningen og aggregeringen av audit-loggene i NAV.

Vi har et eget dokument i **Loop** som brukes for å koordinere hvilke felt og eventer som logges med ArcSight-teamet. Dette sikrer at loggene blir tolket og vist riktig i sentrale oversikter.

---

## Implementasjon

### AuditLog

Audit-logging gjøres ved hjelp av NAVs fellesbibliotek `audit-log`. Vi logger i CEF-format (Common Event Format), som er standarden ArcSight forventer.

De viktigste feltene vi må huske på er:

- **Event**: Hva slags hendelse det er (f.eks. `ACCESS` for visning).
- **Source User**: Hvem som gjør oppslaget (NAV-ident).
- **Destination User**: Hvem oppslaget gjelder (Fødselsnummer).
- **Message**: En lesbar beskrivelse av hva som har skjedd.

### Hva logges

| Hendelse              | Trigger                                       | Logget informasjon  |
| --------------------- | --------------------------------------------- | ------------------- |
| Visning av jobbsøkere | GET `/api/rekrutteringstreff/{id}/jobbsokere` | NAV-ident, treff-ID |

---

## SecureLog for sensitiv data

I tillegg til audit-logging bruker vi `SecureLog` for å logge sensitive data (som feilmeldinger med persondata) til en egen loggfil som kun er tilgjengelig for utviklingsteamet i Kibana.

### Hva du må tenke på

Når du skal bruke SecureLog, er det viktig å huske på:

1.  **Ingen persondata i app-logger**: Vanlig applikasjonslogg (stdout) skal aldri inneholde fødselsnumre eller annen sensitiv informasjon.
2.  **Bruk SecureLog-wrapperen**: Bruk `SecureLog`-klassen i koden. Den sørger for å markere meldingene riktig (`TEAM_LOGS`).
3.  **Filtrering skjer automatisk**: Infrastrukturen er satt opp slik at meldinger sendt via SecureLog automatisk rutes til secure-logs-indeksen og filtreres _bort_ fra den vanlige app-loggen.

Dermed unngår vi data-lekkasjer til åpne logger, samtidig som vi har full sporbarhet ved feilsøking.
<!-- ... encoder ... -->
</appender>

    <root level="INFO">
        <appender-ref ref="appLog"/>
        <appender-ref ref="team-logs" />
    </root>

</configuration>
```

Loggene blir da tilgjengelige i Kibana for teammedlemmer, men ikke i de åpne loggene.

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
