# Aktivitetskort for Rekrutteringstreff

## Formål

Når en jobbsøker inviteres til et rekrutteringstreff, opprettes automatisk et **aktivitetskort** i jobbsøkerens aktivitetsplan. Dette sikrer at:

- Jobbsøker ser invitasjonen i sin aktivitetsplan
- Veileder kan følge opp deltakelse via aktivitetsplanen
- Status holdes synkronisert mellom rekrutteringstreff og aktivitetsplan

---

## Teknisk flyt

```mermaid
graph TB
    subgraph Frontend
        UI[Veileder inviterer jobbsøker]
    end

    subgraph "rekrutteringstreff-api"
        API[API endpoint]
        DB[(Database)]
        Scheduler[AktivitetskortJobbsøkerScheduler]
        FeilLytter[AktivitetskortFeilLytter]
    end

    subgraph "rekrutteringsbistand-aktivitetskort"
        Lyttere["Lyttere (Kafka-konsumenter)"]
        AktDB[(Database)]
        AktJobb[AktivitetskortJobb]
        FeilJobb[AktivitetskortFeilJobb]
    end

    subgraph "Ekstern"
        Aktivitetsplan[Aktivitetsplanen]
    end

    %% Hovedflyt
    UI --> API
    API --> DB
    DB --> Scheduler
    Scheduler -.->|Rapids events| Lyttere
    Lyttere --> AktDB
    AktDB --> AktJobb
    AktJobb -.->|aktivitetskort-v1.1| Aktivitetsplan

    %% Feilflyt
    Aktivitetsplan -.->|dab.aktivitetskort-feil-v1| FeilJobb
    FeilJobb --> AktDB
    AktDB --> FeilJobb
    FeilJobb -.->|aktivitetskort-feil| FeilLytter
    FeilLytter --> DB

    style UI fill:#e1f5ff,color:#000,stroke:#333
    style Scheduler fill:#fff4e1,color:#000,stroke:#333
    style AktJobb fill:#fff4e1,color:#000,stroke:#333
    style Lyttere fill:#e8f5e9,color:#000,stroke:#333
    style FeilLytter fill:#ffebee,color:#000,stroke:#333
    style FeilJobb fill:#ffebee,color:#000,stroke:#333
    style Aktivitetsplan fill:#f3e5f5,color:#000,stroke:#333
    style API fill:#fff,color:#000,stroke:#333
    style DB fill:#fff,color:#000,stroke:#333
    style AktDB fill:#fff,color:#000,stroke:#333
```

**Tegnforklaring:** Hel linje = synkron, stiplet = Kafka

---

## Komponenter

| Komponent                          | App                                 | Beskrivelse                                                                       |
| ---------------------------------- | ----------------------------------- | --------------------------------------------------------------------------------- |
| `AktivitetskortJobbsøkerScheduler` | rekrutteringstreff-api              | Poller DB hvert 10s, publiserer til Rapids                                        |
| `AktivitetskortFeilLytter`         | rekrutteringstreff-api              | Lytter på `aktivitetskort-feil`, lagrer feil i DB                                 |
| `Lyttere`                          | rekrutteringsbistand-aktivitetskort | Konsumerer Rapids-events, lagrer i DB                                             |
| `AktivitetskortJobb`               | rekrutteringsbistand-aktivitetskort | Poller DB hvert minutt, sender til `aktivitetskort-v1.1`                          |
| `AktivitetskortFeilJobb`           | rekrutteringsbistand-aktivitetskort | Konsumerer `dab.aktivitetskort-feil-v1`, lagrer i DB, poller og sender til Rapids |

---

## Hendelser og aktivitetskort-status

| Hendelse                           | Rapids event                     | Aktivitetskort-status |
| ---------------------------------- | -------------------------------- | --------------------- |
| Inviter jobbsøker                  | `rekrutteringstreffinvitasjon`   | PLANLAGT              |
| Jobbsøker svarer ja                | `rekrutteringstreffSvarOgStatus` | GJENNOMFORES          |
| Jobbsøker svarer nei               | `rekrutteringstreffSvarOgStatus` | AVBRUTT               |
| Svart ja → treff fullført          | `rekrutteringstreffSvarOgStatus` | FULLFORT              |
| Svart ja → treff avlyst            | `rekrutteringstreffSvarOgStatus` | AVBRUTT               |
| Svart nei → treff fullført/avlyst  | `rekrutteringstreffSvarOgStatus` | AVBRUTT               |
| Ikke svart → treff fullført/avlyst | `rekrutteringstreffSvarOgStatus` | AVBRUTT               |
| Treff endret                       | `rekrutteringstreffoppdatering`  | (oppdaterer detaljer) |

---

## Relatert dokumentasjon

- [Varsling](varsling.md) - SMS/e-post-varsling til jobbsøkere
- [Database-schema](../2-arkitektur/database.md) - Databaseoversikt
