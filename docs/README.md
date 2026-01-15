# Dokumentasjon - rekrutteringstreff-backend

Denne mappen inneholder dokumentasjon for hele rekrutteringstreff-backend monorepoet, som inkluderer:

- **rekrutteringstreff-api** - Hovedapplikasjon med REST API for veiledere og markedskontakter
- **rekrutteringstreff-minside-api** - API for jobbsøkere via MinSide
- **rekrutteringsbistand-aktivitetskort** - Mellomtjeneste for aktivitetskort-synkronisering

## Innhold

1. **[Oversikt](01-oversikt.md)** - Oversikt over rekrutteringstreff-domenet og systemarkitektur
2. **Tekniske prinsipper og arkitektur**
   - [Tekniske prinsipper](02-tekniske-prinsipper.md) - Tekniske prinsipper og arkitekturbeslutninger
   - [Tilgangsstyring](03-tilgangsstyring.md) - Håndtering av tilgangskontroll
   - [Audit logging](04-audit-logging.md) - Logging av hendelser for revisjon
3. **Personvern**
   - [Synlighet](05-synlighet.md) - Integrasjon med toi-synlighetsmotor for synlighetsfiltrering
4. **Integrasjoner og flyt**
   - [Varsling](06-varsling.md) - Varsling til jobbsøkere via SMS, e-post og MinSide
   - [Aktivitetskort](07-aktivitetskort.md) - Aktivitetskort-integrasjon med aktivitetsplanen
5. **KI og innholdsvalidering**
   - [KI-moderering](08-ki-moderering.md) - Bruk av KI for å validere rekrutteringstreff-innhold
6. **[Database-schema](09-database-schema.md)** - Databaseskjema med ER-diagram og Flyway-migrasjoner

## Applikasjoner i monorepoet

| Applikasjon                        | Beskrivelse                                                      |
| ---------------------------------- | ---------------------------------------------------------------- |
| `rekrutteringstreff-api`           | Hovedapplikasjon med REST API for veiledere og markedskontakter  |
| `rekrutteringstreff-minside-api`   | API for jobbsøkere, kommuniserer med rekrutteringstreff-api      |
| `rekrutteringsbistand-aktivitetskort` | Lytter på Kafka og synkroniserer aktivitetskort                |

## Mermaid-diagrammer

Dokumentene inneholder Mermaid-diagrammer som kan vises i:

- **GitHub** - Støtter Mermaid direkte
- **VS Code** - Med [Mermaid Preview](https://marketplace.visualstudio.com/items?itemName=bierner.markdown-mermaid) extension
- **IntelliJ IDEA** - Med Mermaid plugin
- **Online** - På [mermaid.live](https://mermaid.live)
