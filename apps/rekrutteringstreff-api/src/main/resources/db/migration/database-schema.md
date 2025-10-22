# Database Schema - Rekrutteringstreff

Dette er en grafisk oversikt over databaseskjemaet for rekrutteringstreff-systemet.

Husk å endre denne filen ved endringer i flywayscriptene. Bruk gjerne copilot til å oppdatere denne filen.

## Entity Relationship Diagram

```mermaid
erDiagram
    rekrutteringstreff ||--o{ arbeidsgiver : "har"
    rekrutteringstreff ||--o{ jobbsoker : "har"
    rekrutteringstreff ||--o{ innlegg : "har"
    rekrutteringstreff ||--o{ rekrutteringstreff_hendelse : "logger"
    rekrutteringstreff ||--o{ ki_spørring_logg : "refererer til"
    
    arbeidsgiver ||--o{ naringskode : "har"
    arbeidsgiver ||--o{ arbeidsgiver_hendelse : "logger"
    
    jobbsoker ||--o{ jobbsoker_hendelse : "logger"
    
    jobbsoker_hendelse ||--o{ aktivitetskort_polling : "sender"
    
    rekrutteringstreff {
        bigserial db_id PK
        uuid id UK "Unik UUID"
        text tittel
        text status
        text opprettet_av_person_navident
        text opprettet_av_kontor_enhetid
        timestamptz opprettet_av_tidspunkt
        timestamptz fratid
        timestamptz tiltid
        text gateadresse
        text postnummer
        text poststed
        timestamptz svarfrist
        text[] eiere "Array av eiere"
        text beskrivelse
    }
    
    arbeidsgiver {
        bigserial db_id PK
        bigint treff_db_id FK
        text orgnr "Organisasjonsnummer"
        text orgnavn
        uuid id
    }
    
    jobbsoker {
        bigserial db_id PK
        bigint treff_db_id FK
        text fodselsnummer
        text fornavn
        text etternavn
        text kandidatnummer
        text navkontor
        text veileder_navn
        text veileder_navident
        uuid id
    }
    
    innlegg {
        bigserial db_id PK
        uuid id UK "Default: gen_random_uuid()"
        bigint treff_db_id FK
        text tittel
        text opprettet_av_person_navident
        text opprettet_av_person_navn
        text opprettet_av_person_beskrivelse
        timestamptz sendes_til_jobbsoker_tidspunkt
        text html_content
        timestamptz opprettet_tidspunkt "Default: now()"
        timestamptz sist_oppdatert_tidspunkt "Default: now()"
    }
    
    jobbsoker_hendelse {
        bigserial db_id PK
        uuid id
        bigint jobbsoker_db_id FK
        timestamptz tidspunkt
        text hendelsestype
        text opprettet_av_aktortype
        text aktøridentifikasjon
    }
    
    arbeidsgiver_hendelse {
        bigserial db_id PK
        uuid id
        bigint arbeidsgiver_db_id FK
        timestamptz tidspunkt
        text hendelsestype
        text opprettet_av_aktortype
        text aktøridentifikasjon
    }
    
    rekrutteringstreff_hendelse {
        bigserial db_id PK
        uuid id
        bigint rekrutteringstreff_db_id FK
        timestamptz tidspunkt
        text hendelsestype
        text opprettet_av_aktortype
        text aktøridentifikasjon
    }
    
    aktivitetskort_polling {
        bigserial db_id PK
        bigint jobbsoker_hendelse_db_id FK
        timestamptz sendt_tidspunkt
    }
    
    ki_spørring_logg {
        bigserial db_id PK
        uuid id "Default: gen_random_uuid()"
        timestamptz opprettet_tidspunkt "Default: now()"
        uuid treff_id FK "ON DELETE SET NULL"
        text felt_type
        text spørring_fra_frontend
        text spørring_filtrert
        text systemprompt
        jsonb ekstra_parametre
        boolean bryter_retningslinjer
        text begrunnelse
        text ki_navn
        text ki_versjon
        integer svartid_ms "Svartid i millisekunder"
        boolean lagret "Default: false"
        boolean manuell_kontroll_bryter_retningslinjer
        text manuell_kontroll_utført_av
        timestamptz manuell_kontroll_tidspunkt
    }
    
    naringskode {
        bigserial db_id PK
        bigint arbeidsgiver_db_id FK
        text kode
        text beskrivelse
    }
```

## Forklaring av tabellene

### Hovedtabeller

- **rekrutteringstreff**: Hovedtabellen som representerer et rekrutteringstreff/arrangement
- **arbeidsgiver**: Arbeidsgivere som deltar i et treff
- **jobbsoker**: Jobbsøkere som deltar i et treff
- **innlegg**: Innlegg/meldinger knyttet til et treff

### Hendelselogging

- **rekrutteringstreff_hendelse**: Logger hendelser på treffnivå
- **arbeidsgiver_hendelse**: Logger hendelser for arbeidsgivere
- **jobbsoker_hendelse**: Logger hendelser for jobbsøkere
- **aktivitetskort_polling**: Logger når aktivitetskort sendes til jobbsøkere

### Støttetabeller

- **naringskode**: Næringskoder for arbeidsgivere (kan ha flere per arbeidsgiver)
- **ki_spørring_logg**: Logger AI/KI-spørringer med metadata og modereringsinfo

## Indexes

Viktige indexes for performance:
- `rekrutteringstreff_id_uq` - Unik index på rekrutteringstreff.id
- `idx_innlegg_treff_db_id` - Index på innlegg.treff_db_id
- `idx_arbeidsgiver_hendelse_arbeidsgiver_db_id` - Index på arbeidsgiver_hendelse
- `idx_rekrutteringstreff_hendelse_rekrutteringstreff_db_id` - Index på rekrutteringstreff_hendelse
- `ki_spørring_logg_treff_uuid_idx` - Index på ki_spørring_logg.treff_id
- `naringskode_arbeidsgiver_db_id_idx` - Index på naringskode.arbeidsgiver_db_id

## Constraints

- Alle foreign keys er definert med navngitte constraints
- `naringskode_unik_per_arbeidsgiver` - Sikrer at samme næringskode ikke legges til flere ganger for samme arbeidsgiver
- `ki_spørring_logg.treff_id` har `ON DELETE SET NULL` - bevarer logger selv om treff slettes

## Visning av diagrammet

Dette diagrammet kan vises i:
- GitHub (støtter Mermaid direkte)
- IntelliJ IDEA (installer Mermaid plugin)
- VS Code (installer Mermaid preview extension)
- Online på [mermaid.live](https://mermaid.live)

