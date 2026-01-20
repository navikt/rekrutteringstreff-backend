# Aktivitetskort for Rekrutteringstreff

Løsningen synker automatisk status for rekrutteringstreff med aktivitetskort i aktivitetsplanen. Når en jobbsøker inviteres til et treff, opprettes et aktivitetskort. Kortet oppdateres basert på jobbsøkerens svar og treffets status.

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

| Komponent                          | App                                 | Beskrivelse                                                    |
| ---------------------------------- | ----------------------------------- | -------------------------------------------------------------- |
| `AktivitetskortJobbsøkerScheduler` | rekrutteringstreff-api              | Poller DB hvert 10s, publiserer til Rapids                     |
| `AktivitetskortFeilLytter`         | rekrutteringstreff-api              | Lytter på `aktivitetskort-feil`, lagrer feil i DB              |
| `Lyttere`                          | rekrutteringsbistand-aktivitetskort | Konsumerer Rapids-events, lagrer i DB                          |
| `AktivitetskortJobb`               | rekrutteringsbistand-aktivitetskort | Poller DB hvert minutt, sender til `aktivitetskort-v1.1`       |
| `AktivitetskortFeilJobb`           | rekrutteringsbistand-aktivitetskort | Konsumerer `dab.aktivitetskort-feil-v1`, publiserer til Rapids |

---

## Status-mapping

| Svar         | Treffstatus | AktivitetsStatus |
| ------------ | ----------- | ---------------- |
| JA           | (uendret)   | GJENNOMFORES     |
| JA           | fullført    | FULLFORT         |
| JA           | avlyst      | AVBRUTT          |
| NEI          | \*          | AVBRUTT          |
| (ikke svart) | fullført    | AVBRUTT          |
| (ikke svart) | avlyst      | AVBRUTT          |

---

## Hendelse → Rapids-event → Aktivitetskort

| Hendelse       | Rapids event                     | Aktivitetskort     |
| -------------- | -------------------------------- | ------------------ |
| Inviter        | `rekrutteringstreffinvitasjon`   | Opprett (PLANLAGT) |
| Svar ja        | `rekrutteringstreffSvarOgStatus` | GJENNOMFORES       |
| Svar nei       | `rekrutteringstreffSvarOgStatus` | AVBRUTT            |
| Treff fullført | `rekrutteringstreffSvarOgStatus` | FULLFORT/AVBRUTT   |
| Treff avlyst   | `rekrutteringstreffSvarOgStatus` | AVBRUTT            |
| Treff endret   | `rekrutteringstreffoppdatering`  | Oppdater detaljer  |

---

## Relatert dokumentasjon

- [Varsling](varsling.md)
- [Database-schema](../2-arkitektur/09-database-schema.md)
