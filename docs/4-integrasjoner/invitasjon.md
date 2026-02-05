# Invitasjon av jobbsøkere

Denne flyten beskriver hvordan veiledere og markedskontakter inviterer jobbsøkere til rekrutteringstreff.

## Flyt

### 1. Registrering av invitasjon (Synkron flyt)

Her legges jobbsøker til og status oppdateres. Hendelser lagres i databasen for senere utsending.

```mermaid
sequenceDiagram
    participant Veileder
    participant Frontend as rekrutteringsbistand-frontend
    participant API as rekrutteringstreff-api
    participant DB as Database
    participant Synlighet as toi-synlighetsmotor

    Note over Veileder,Synlighet: Legge til jobbsøker på treff
    Veileder->>Frontend: Legger til jobbsøker
    Frontend->>API: POST /jobbsoker
    API-->>Synlighet: synlighetRekrutteringstreff (behov)
    Synlighet-->>API: Synlighetssvar
    API->>DB: Lagre jobbsøker

    Note over Veileder,Synlighet: Invitere jobbsøker
    Veileder->>Frontend: Inviterer jobbsøker
    Frontend->>API: PUT /jobbsoker/inviter
    API->>DB: Oppdater status til INVITERT
    API->>DB: Lagre INVITERT-hendelse (for scheduler)
```

### 2. Utsending av hendelser (Scheduler)

En jobb kjører periodisk for å plukke opp hendelser som skal ut på Kafka.

```mermaid
sequenceDiagram
    participant Scheduler as JobbsøkerScheduler
    participant DB as Database
    participant Kafka as Rapids & Rivers

    loop Hvert 10. sekund
        Scheduler->>DB: Hent usendte INVITERT-hendelser
        DB-->>Scheduler: Liste med hendelser
        Scheduler-->>Kafka: Publiser rekrutteringstreffinvitasjon
        Scheduler->>DB: Marker hendelser som sendt
    end
```

### 3. Konsumenter (Varsling og Aktivitetskort)

Systemene lytter på Kafka-hendelsen og utfører sine oppgaver.

```mermaid
sequenceDiagram
    participant Kafka as Rapids & Rivers
    participant Varsel as kandidatvarsel-api
    participant Aktivitetskort as rekrutteringsbistand-aktivitetskort

    par Parallelle prosesser
        Kafka-->>Varsel: rekrutteringstreffinvitasjon (event)
        Varsel->>Varsel: Sender SMS/e-post
        Varsel-->>Kafka: minsideVarselSvar (status)
    and
        Kafka-->>Aktivitetskort: rekrutteringstreffinvitasjon (event)
        Aktivitetskort->>Aktivitetskort: Oppretter aktivitetskort
    end
```

> **Tegnforklaring:**
>
> - Hel linje (`->>`): Synkron/direkte kommunikasjon
> - Stiplet linje (`-->>`): Asynkron kommunikasjon via Kafka (Rapids & Rivers)

## Steg-for-steg

### 1. Registrering

Veileder eller markedskontakt legger til en jobbsøker på et rekrutteringstreff. Systemet sjekker automatisk synlighetsstatus via toi-synlighetsmotor for å verifisere at jobbsøkeren har gyldig CV og samtykke.

### 2. Invitere jobbsøker

Når jobbsøkeren inviteres, skjer følgende parallelt:

- **Varsling**: SMS og e-post sendes til jobbsøker med lenke til MinSide
- **Aktivitetskort**: Et aktivitetskort opprettes i jobbsøkerens aktivitetsplan

### 3. Jobbsøker mottar invitasjon

Jobbsøker kan åpne rekrutteringstreffet på MinSide via to innganger:

- **Via varsel**: SMS/e-post inneholder lenke direkte til MinSide-landingssiden
- **Via aktivitetskortet**: Aktivitetskortet i aktivitetsplanen har samme lenke til MinSide

Begge veier leder til samme landingsside der jobbsøker kan se treffdetaljer og svare på invitasjonen. Se [minside-flyt.md](minside-flyt.md) for detaljer om jobbsøkerens flyt.

## Relaterte dokumenter

- [Varsling](varsling.md) - Detaljer om varslingsmekanismen
- [MinSide-flyt](minside-flyt.md) - Jobbsøkerens flyt for å se og svare
- [Aktivitetskort](aktivitetskort.md) - Detaljer om aktivitetskort-integrasjonen
- [MinSide-flyt](minside-flyt.md) - Jobbsøkerens flyt for å se og svare
