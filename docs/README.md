# Dokumentasjon - rekrutteringstreff-backend

Denne mappen inneholder dokumentasjon for hele rekrutteringstreff-backend monorepoet, som inkluderer:

- **rekrutteringstreff-api** - Hovedapplikasjon med REST API for veiledere og markedskontakter
- **rekrutteringstreff-minside-api** - API for jobbsøkere via MinSide
- **rekrutteringsbistand-aktivitetskort** - Mellomtjeneste for aktivitetskort-synkronisering

## Innhold

### 1. [Oversikt](1-oversikt/)

- [Oversikt](1-oversikt/oversikt.md) - Introduksjon til rekrutteringstreff-domenet og systemarkitektur
- [Ordliste](1-oversikt/ordliste.md) - Forklaring av domenebegreper og tekniske termer

### 2. [Arkitektur](2-arkitektur/)

- [Prinsipper](2-arkitektur/prinsipper.md) - Rammeverk, lagdeling og arkitekturprinsipper
- [Arkitekturbeslutninger](2-arkitektur/arkitekturbeslutninger.md) - FAQ: Hvorfor egen MinSide-backend, appoppdeling, synkron vs. asynkron, hendelse_data-struktur
- [Database](2-arkitektur/database.md) - Databaseskjema, ER-diagram og Flyway-migrasjoner

### 3. [Sikkerhet](3-sikkerhet/)

- [Tilgangsstyring](3-sikkerhet/tilgangsstyring.md) - Roller og tilgangskontroll
- [Synlighet](3-sikkerhet/synlighet.md) - Synlighetsfiltrering via toi-synlighetsmotor
- [Audit-logging](3-sikkerhet/audit-logging.md) - Logging av hendelser for revisjon

### 4. [Integrasjoner](4-integrasjoner/)

- [Rapids and Rivers](4-integrasjoner/rapids-and-rivers.md) - Kafka-basert meldingsutveksling
- [Invitasjon](4-integrasjoner/invitasjon.md) - Flyt for invitasjon av jobbsøkere
- [Varsling](4-integrasjoner/varsling.md) - Varsling via SMS, e-post og MinSide
- [Aktivitetskort](4-integrasjoner/aktivitetskort.md) - Synkronisering med aktivitetsplanen
- [MinSide-flyt](4-integrasjoner/minside-flyt.md) - Jobbsøkers flyt for å se treff og svare
- [Kandidatsøk](4-integrasjoner/kandidatsok.md) - Søk etter jobbsøkere via kandidatsøk-api
- [Enhetsregisteret](4-integrasjoner/enhetsregisteret.md) - Arbeidsgiversøk via pam-search

### 5. [KI](5-ki/)

- [KI-tekstvalidering](5-ki/ki-tekstvalidering.md) - Validering av rekrutteringstreff-innhold med KI

### 6. [Kvalitet](6-kvalitet/)

- [Testing](6-kvalitet/testing.md) - Teststrategi og testnivåer
- [Utvidbarhet](6-kvalitet/utvidbarhet.md) - Planlagte utvidelser og arkitekturprinsipper
- [Vedlikeholdbarhet](6-kvalitet/vedlikeholdbarhet.md) - Design for vedlikehold

### 7. [Akseptansetest og ROS](7-akseptansetest-og-ros/)

- [Akseptansetester](7-akseptansetest-og-ros/akseptansetester.md) - Manuelle tester for domeneeksperter før pilot/prodsetting
- [Automatiske backend-tester](7-akseptansetest-og-ros/automatiske-tester.md) - Plan for automatisering av akseptansetester i backend

## Applikasjoner i monorepoet

| Applikasjon                           | Beskrivelse                                                     |
| ------------------------------------- | --------------------------------------------------------------- |
| `rekrutteringstreff-api`              | Hovedapplikasjon med REST API for veiledere og markedskontakter |
| `rekrutteringstreff-minside-api`      | API for jobbsøkere, kommuniserer med rekrutteringstreff-api     |
| `rekrutteringsbistand-aktivitetskort` | Lytter på Kafka og synkroniserer aktivitetskort                 |

## Mermaid-diagrammer

Dokumentene inneholder Mermaid-diagrammer som kan vises i:

- **GitHub** - Støtter Mermaid direkte
- **VS Code** - Med [Mermaid Preview](https://marketplace.visualstudio.com/items?itemName=bierner.markdown-mermaid) extension
- **IntelliJ IDEA** - Med Mermaid plugin
- **Online** - På [mermaid.live](https://mermaid.live)
