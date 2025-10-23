# Database Schema - Rekrutteringstreff
Vis denne filen i Github for å se en grafisk remstilling av databaseskjemaet ved hjelp av Mermaid.

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
        bigserial rekrutteringstreff_id PK "Intern primærnøkkel"
        uuid id UK "Unik UUID for treffet"
        text tittel "Tittel på rekrutteringstreffet"
        text status "Status: OPPRETTET, PUBLISERT, AVLYST osv."
        text opprettet_av_person_navident "NAV-ident for oppretteren"
        text opprettet_av_kontor_enhetid "NAV-kontor enhetid"
        timestamptz opprettet_av_tidspunkt "Når treffet ble opprettet"
        timestamptz fratid "Start tidspunkt for treffet"
        timestamptz tiltid "Slutt tidspunkt for treffet"
        text gateadresse "Gateadresse for treffstedet"
        text postnummer "Postnummer for treffstedet"
        text poststed "Poststed for treffstedet"
        timestamptz svarfrist "Frist for påmelding/svar"
        text[] eiere "Array av NAV-identer som eier treffet"
        text beskrivelse "Beskrivelse av treffet"
    }
    
    arbeidsgiver {
        bigserial arbeidsgiver_id PK "Intern primærnøkkel"
        bigint rekrutteringstreff_id FK "Referanse til rekrutteringstreff"
        text orgnr "Organisasjonsnummer"
        text orgnavn "Organisasjonsnavn"
        uuid id "Unik UUID for arbeidsgiver i treffet"
    }
    
    jobbsoker {
        bigserial jobbsoker_id PK "Intern primærnøkkel"
        bigint rekrutteringstreff_id FK "Referanse til rekrutteringstreff"
        text fodselsnummer "Fødselsnummer for jobbsøker"
        text fornavn "Fornavn på jobbsøker"
        text etternavn "Etternavn på jobbsøker"
        text kandidatnummer "Kandidatnummer i NAV"
        text navkontor "NAV-kontor som følger opp jobbsøker"
        text veileder_navn "Veilederens navn"
        text veileder_navident "Veilederens NAV-ident"
        uuid id "Unik UUID for jobbsøker i treffet"
    }
    
    innlegg {
        bigserial innlegg_id PK "Intern primærnøkkel"
        uuid id UK "Unik UUID (auto-generert)"
        bigint rekrutteringstreff_id FK "Referanse til rekrutteringstreff"
        text tittel "Tittel på innlegget"
        text opprettet_av_person_navident "NAV-ident til oppretteren"
        text opprettet_av_person_navn "Navn på oppretteren"
        text opprettet_av_person_beskrivelse "Beskrivelse av oppretterens rolle"
        timestamptz sendes_til_jobbsoker_tidspunkt "Når innlegget sendes til jobbsøker"
        text html_content "Innhold i HTML-format"
        timestamptz opprettet_tidspunkt "Når innlegget ble opprettet"
        timestamptz sist_oppdatert_tidspunkt "Sist oppdatert tidspunkt"
    }
    
    jobbsoker_hendelse {
        bigserial jobbsoker_hendelse_id PK "Intern primærnøkkel"
        uuid id "Unik UUID for hendelsen"
        bigint jobbsoker_id FK "Referanse til jobbsøker"
        timestamptz tidspunkt "Når hendelsen skjedde"
        text hendelsestype "Type hendelse (f.eks. PÅMELDT, AVMELDT)"
        text opprettet_av_aktortype "Type aktør (NAV_ANSATT, ARBEIDSGIVER, osv.)"
        text aktøridentifikasjon "Identifikasjon av aktøren"
    }
    
    arbeidsgiver_hendelse {
        bigserial arbeidsgiver_hendelse_id PK "Intern primærnøkkel"
        uuid id "Unik UUID for hendelsen"
        bigint arbeidsgiver_id FK "Referanse til arbeidsgiver"
        timestamptz tidspunkt "Når hendelsen skjedde"
        text hendelsestype "Type hendelse (f.eks. INVITERT, TAKKET_JA)"
        text opprettet_av_aktortype "Type aktør (NAV_ANSATT, ARBEIDSGIVER, osv.)"
        text aktøridentifikasjon "Identifikasjon av aktøren"
    }
    
    rekrutteringstreff_hendelse {
        bigserial rekrutteringstreff_hendelse_id PK "Intern primærnøkkel"
        uuid id "Unik UUID for hendelsen"
        bigint rekrutteringstreff_id FK "Referanse til rekrutteringstreff"
        timestamptz tidspunkt "Når hendelsen skjedde"
        text hendelsestype "Type hendelse (f.eks. OPPRETTET, PUBLISERT, AVLYST)"
        text opprettet_av_aktortype "Type aktør (NAV_ANSATT, SYSTEM, osv.)"
        text aktøridentifikasjon "Identifikasjon av aktøren"
    }
    
    aktivitetskort_polling {
        bigserial aktivitetskort_polling_id PK "Intern primærnøkkel"
        bigint jobbsoker_hendelse_id FK "Referanse til jobbsøker_hendelse"
        timestamptz sendt_tidspunkt "Når aktivitetskortet ble sendt"
    }
    
    ki_spørring_logg {
        bigserial ki_spørring_logg_id PK "Intern primærnøkkel"
        uuid id "Unik UUID (auto-generert)"
        timestamptz opprettet_tidspunkt "Når spørringen ble logget"
        uuid treff_id FK "Referanse til rekrutteringstreff (ON DELETE SET NULL)"
        text felt_type "Hvilken felt-type spørringen gjelder (f.eks. TITTEL, BESKRIVELSE)"
        text spørring_fra_frontend "Originalspørring fra bruker"
        text spørring_filtrert "Filtrert/sanert versjon av spørringen"
        text systemprompt "Systemprompt brukt i KI-kallet"
        jsonb ekstra_parametre "Ekstra parametere sendt til KI"
        boolean bryter_retningslinjer "Om KI-svaret bryter retningslinjer"
        text begrunnelse "Begrunnelse for modereringsbeslutningen"
        text ki_navn "Navn på KI-modellen (f.eks. GPT-4)"
        text ki_versjon "Versjon av KI-modellen"
        integer svartid_ms "Svartid i millisekunder"
        boolean lagret "Om svaret ble lagret/brukt"
        boolean manuell_kontroll_bryter_retningslinjer "Manuell overprøving av moderering"
        text manuell_kontroll_utført_av "NAV-ident for manuell kontrollør"
        timestamptz manuell_kontroll_tidspunkt "Når manuell kontroll ble utført"
    }
    
    naringskode {
        bigserial naringskode_id PK "Intern primærnøkkel"
        bigint arbeidsgiver_id FK "Referanse til arbeidsgiver"
        text kode "Næringskode (f.eks. 47.711)"
        text beskrivelse "Beskrivelse av næringskoden"
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
- `idx_innlegg_rekrutteringstreff_id` - Index på innlegg.rekrutteringstreff_id
- `idx_arbeidsgiver_hendelse_arbeidsgiver_id` - Index på arbeidsgiver_hendelse.arbeidsgiver_id
- `idx_rekrutteringstreff_hendelse_rekrutteringstreff_id` - Index på rekrutteringstreff_hendelse.rekrutteringstreff_id
- `ki_spørring_logg_treff_uuid_idx` - Index på ki_spørring_logg.treff_id
- `naringskode_arbeidsgiver_id_idx` - Index på naringskode.arbeidsgiver_id

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

