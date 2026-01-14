# Rekrutteringstreff - Oversikt

## Formål

Rekrutteringstreff er en tjeneste for å opprette møter mellom arbeidsgivere og jobbsøkere, der de kan snakke sammen med formål om at jobbsøkerne skal få muligheter til jobb.

Tjenesten er primært designet for markedskontakter i NAV, men veiledere skal også kunne legge til sine jobbsøkere i et treff.

### Tjenester for jobbsøkere

- **Varsling**: SMS, e-post og varsel på MinSide når de inviteres til treff
- **Landingsside**: Jobbsøkere kan se og svare på invitasjoner
- **Aktivitetskort**: Oppdatering av aktivitetskort i aktivitetsplanen for brukere under oppfølging

### Fremtidige planer

- Tilby tjenesten også for jobbsøkere som ikke er under oppfølging i NAV
- Legge til inngangsporter fra andre NAV-tjenester, som for eksempel stillingssøket

## Systemarkitektur

```mermaid
graph TB
    subgraph Frontend
        RBF[rekrutteringsbistand-frontend<br/>Brukergrensesnitt for veiledere<br/>og markedskontakter]
    end

    subgraph Backend
        API[rekrutteringstreff-api<br/>Forretningslogikk og REST API<br/>Håndterer treff og invitasjoner]
    end

    subgraph Støttetjenester
        AK[rekrutteringsbistand-aktivitetskort<br/>Oppretter og vedlikeholder<br/>aktivitetskort]
        VARSEL[rekrutteringsbistand-kandidatvarsel-api<br/>Sender SMS og e-post<br/>til jobbsøkere]
        SYN[toi-synlighetsmotor<br/>Evaluerer om kandidater<br/>er synlige for arbeidsgivere]
    end

    subgraph Eksterne_systemer
        RAPIDS[Rapids & Rivers<br/>Kafka event-bus]
        MINSIDE[MinSide<br/>NAVs brukerportal]
        AKTIVITET[Aktivitetsplan]
    end

    RBF -->|REST| API
    API -->|Publiserer events| RAPIDS
    API -->|Ber om synlighet| RAPIDS

    RAPIDS -->|Invitasjon og oppdateringer| AK
    RAPIDS -->|Invitasjon og oppdateringer| VARSEL
    RAPIDS -->|Synlighetsbehov| SYN

    SYN -->|Synlighetssvar| RAPIDS
    RAPIDS -->|Synlighetssvar| API

    VARSEL -->|Sender varsler| MINSIDE
    VARSEL -->|Varselstatus| RAPIDS
    RAPIDS -->|Varselstatus| API

    AK -->|Oppretter kort| AKTIVITET
```

### Applikasjonsbeskrivelser

#### rekrutteringsbistand-frontend

Brukergrensesnitt der veiledere og markedskontakter:

- Oppretter og administrerer rekrutteringstreff
- Legger til jobbsøkere og arbeidsgivere
- Inviterer jobbsøkere til treff
- Følger opp svar og deltakerstatus

#### rekrutteringstreff-api

Hovedapplikasjon som:

- Tilbyr REST API for frontend
- Lagrer og administrerer rekrutteringstreff, jobbsøkere og arbeidsgivere
- Publiserer events når jobbsøkere inviteres eller treff oppdateres
- Mottar synlighetsevalueringer fra toi-synlighetsmotor
- Mottar varselstatus fra kandidatvarsel-api

#### rekrutteringsbistand-aktivitetskort

Mellomtjeneste som:

- Lytter på events om invitasjoner og oppdateringer
- Oppretter og oppdaterer aktivitetskort i brukerens aktivitetsplan
- Mapper rekrutteringstreff-statuser til aktivitetskort-statuser

#### rekrutteringsbistand-kandidatvarsel-api

Varseltjeneste som:

- Lytter på events om nye invitasjoner
- Sender SMS og e-post til jobbsøkere via MinSide
- Publiserer tilbake status på om varsel ble sendt

#### toi-synlighetsmotor

Evalueringstjeneste som:

- Besvarer behov om synlighetsstatus for jobbsøkere
- Kontrollerer om kandidaten har CV, samtykke og annen nødvendig informasjon
- Sikrer at kun synlige kandidater vises for arbeidsgivere

## Hendelsesflyt

### Invitasjon av jobbsøker

```mermaid
sequenceDiagram
    participant Veileder
    participant Frontend
    participant API
    participant Rapids
    participant Synlighet
    participant Varsel
    participant Aktivitetskort

    Veileder->>Frontend: Legger til jobbsøker på treff
    Frontend->>API: POST /jobbsoker
    API->>Rapids: synlighetRekrutteringstreff (behov)
    Rapids->>Synlighet: Sjekk synlighet
    Synlighet->>Rapids: Synlighetssvar
    Rapids->>API: Synlighetssvar
    API->>API: Lagrer jobbsøker med synlighetsstatus

    Veileder->>Frontend: Inviterer jobbsøker
    Frontend->>API: PUT /jobbsoker/inviter
    API->>API: Oppdaterer status til INVITERT
    API->>Rapids: rekrutteringstreffinvitasjon (event)

    Rapids->>Varsel: Ny invitasjon
    Varsel->>Varsel: Sender SMS/e-post via MinSide
    Varsel->>Rapids: minsideVarselSvar (status)
    Rapids->>API: Varselstatus

    Rapids->>Aktivitetskort: Ny invitasjon
    Aktivitetskort->>Aktivitetskort: Oppretter aktivitetskort
```

### Jobbsøker svarer på invitasjon

```mermaid
sequenceDiagram
    participant Jobbsøker
    participant Landingsside
    participant API
    participant Rapids
    participant Aktivitetskort

    Jobbsøker->>Landingsside: Åpner invitasjonslink
    Landingsside->>API: GET /minside/treff
    API->>Landingsside: Treffdetaljer

    Jobbsøker->>Landingsside: Svarer JA/NEI
    Landingsside->>API: PUT /minside/svar
    API->>API: Oppdaterer status
    API->>Rapids: rekrutteringstreffSvarOgStatus (event)

    Rapids->>Aktivitetskort: Oppdatert status
    Aktivitetskort->>Aktivitetskort: Oppdaterer aktivitetskort
```
