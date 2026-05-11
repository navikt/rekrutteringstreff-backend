# BigQuery-tabeller for statistikk på rekrutteringstreff

Tabellene er designet for å føre statistikk uten persondata. Jobbsøkere identifiseres kun via `jobbsoker_treff_id` (person_treff_id), og fødselsnummer, navn og veileder-ident er utelatt.

## Tabelloversikt

| Tabell | Formål |
|---|---|
| `bq_rekrutteringstreff` | Hovedtabell med treffinfo, sted, status og tidspunkter |
| `bq_treff_kontor` | Hvilke Nav-kontorer som eier treffet |
| `bq_arbeidsgiver` | Deltagende arbeidsgivere (orgnr, orgnavn) |
| `bq_arbeidsgiver_naringskode` | Næringskoder per arbeidsgiver |
| `bq_jobbsoker` | Jobbsøkere uten persondata — kun person_treff_id, navkontor og status |
| `bq_jobbsoker_statushistorikk` | Statusendringer over tid for ombestemmelser og andeler |

## Diagram

```mermaid
erDiagram
    bq_rekrutteringstreff {
        STRING treff_id PK "UUID"
        STRING tittel
        STRING status "UTKAST/PUBLISERT/FULLFØRT/AVLYST"
        STRING gateadresse
        STRING postnummer
        STRING poststed
        STRING kommune
        STRING kommunenummer
        STRING fylke
        STRING fylkesnummer
        TIMESTAMP opprettet_tidspunkt
        TIMESTAMP fra_tid
        TIMESTAMP til_tid
        TIMESTAMP svarfrist
        TIMESTAMP sist_endret
        INT64 antall_eiere
        INT64 antall_kontorer
    }

    bq_treff_kontor {
        STRING treff_id FK
        STRING kontor_enhetid
    }

    bq_arbeidsgiver {
        STRING arbeidsgiver_treff_id PK "UUID"
        STRING treff_id FK
        STRING orgnr
        STRING orgnavn
        STRING status "AKTIV/SLETTET"
    }

    bq_arbeidsgiver_naringskode {
        STRING arbeidsgiver_treff_id FK
        STRING naringskode
        STRING naringskode_beskrivelse
    }

    bq_jobbsoker {
        STRING jobbsoker_treff_id PK "UUID (person_treff_id)"
        STRING treff_id FK
        STRING navkontor
        STRING status "LAGT_TIL/INVITERT/SVART_JA/SVART_NEI/SLETTET"
        BOOLEAN svar_gitt_av_eier
        TIMESTAMP lagt_til_tidspunkt
        TIMESTAMP invitert_tidspunkt
        TIMESTAMP svar_tidspunkt
    }

    bq_jobbsoker_statushistorikk {
        STRING jobbsoker_treff_id FK
        STRING fra_status
        STRING til_status
        STRING utfort_av_type "EIER/JOBBSØKER"
        TIMESTAMP tidspunkt
    }

    bq_rekrutteringstreff ||--o{ bq_treff_kontor : "har kontorer"
    bq_rekrutteringstreff ||--o{ bq_arbeidsgiver : "har arbeidsgivere"
    bq_rekrutteringstreff ||--o{ bq_jobbsoker : "har jobbsøkere"
    bq_arbeidsgiver ||--o{ bq_arbeidsgiver_naringskode : "har næringskoder"
    bq_jobbsoker ||--o{ bq_jobbsoker_statushistorikk : "har statusendringer"
```

## Dekker følgende målinger

- ✅ Sted, adresse, fylke, kommune
- ✅ Nav-kontor til eiere
- ✅ Fullført / avlyst
- ✅ Antall arbeidsgivere + næringskoder
- ✅ Antall foreslåtte, inviterte, svart ja/nei, ikke svart (via status)
- ✅ Ombestemmelser (via statushistorikk)
- ✅ Eier svart på vegne (via `svar_gitt_av_eier` og `utfort_av_type`)
- ✅ Jobbsøkere fra andre kontorer (navkontor vs treff-kontor)
- ✅ Ingen persondata (fødselsnummer, navn, veileder-ident er utelatt)
