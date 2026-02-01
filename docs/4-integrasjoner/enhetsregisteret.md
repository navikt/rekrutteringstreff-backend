# Enhetsregisteret (Arbeidsgiversøk)

Når markedskontakt legger til arbeidsgivere på et rekrutteringstreff, søkes det i Enhetsregisteret via pam-search API.

> **Merk:** Arbeidsgiversøket er en **ekstern tjeneste** som rekrutteringsbistand-frontend kaller direkte. Søket går **ikke** via rekrutteringstreff-api.

## Arkitekturoversikt

```mermaid
graph TB
    subgraph "rekrutteringsbistand-frontend"
        UI[Arbeidsgiversøk-felt]
    end

    subgraph "Backend"
        ROUTE["/api/pam-search/underenhet"]
        PAM[pam-search]
    end

    subgraph "Datakilder"
        ES[(ElasticSearch<br/>underenhet-indeks)]
        BRREG[Brønnøysundregistrene]
    end

    UI -->|GET ?q=søkeord| ROUTE
    ROUTE -->|Proxy| PAM
    PAM -->|Søk| ES
    BRREG -.->|Synkronisering| ES

    style BRREG fill:#e8f5e9,color:#000,stroke:#333
    style ES fill:#e1f5ff,color:#000,stroke:#333
```

## Hvordan det fungerer

### Søkeflyt

1. Markedskontakt skriver inn firmanavn i søkefeltet
2. Frontend sender søkeord til `/api/pam-search/underenhet`
3. pam-search søker i ElasticSearch-indeks med underenheter
4. Resultater returneres med organisasjonsinfo

```mermaid
sequenceDiagram
    participant MK as Markedskontakt
    participant FE as Frontend
    participant API as pam-search
    participant ES as ElasticSearch

    MK->>FE: Skriver "Bedrift AS"
    FE->>API: GET /underenhet?q=Bedrift AS
    API->>ES: Søk i underenhet-indeks
    ES-->>API: Treff med orgnr, navn, adresse
    API-->>FE: Liste over arbeidsgivere
    FE-->>MK: Viser søkeresultater

    MK->>FE: Velger arbeidsgiver
    FE->>FE: Lagrer på treffet
```

### Data fra Enhetsregisteret

Følgende informasjon hentes fra Enhetsregisteret:

| Felt                  | Beskrivelse                   | Eksempel     |
| --------------------- | ----------------------------- | ------------ |
| `organisasjonsnummer` | 9-sifret orgnummer            | "123456789"  |
| `navn`                | Virksomhetsnavn               | "Bedrift AS" |
| `organisasjonsform`   | Type enhet                    | "BEDR", "AS" |
| `antallAnsatte`       | Antall ansatte                | 25           |
| `overordnetEnhet`     | Hovedenhet (for underenheter) | "987654321"  |
| `adresse`             | Forretningsadresse            | Se under     |
| `næringskoder`        | NACE-koder                    | Se under     |

#### Adresseinformasjon

```json
{
  "land": "Norge",
  "landkode": "NO",
  "kommune": "Oslo",
  "kommunenummer": "0301",
  "poststed": "Oslo",
  "postnummer": "0154",
  "adresse": "Storgata 1"
}
```

#### Næringskoder

```json
{
  "kode": "62.010",
  "beskrivelse": "Programmeringstjenester"
}
```

## Underenheter vs. hovedenheter

Vi søker i **underenhet-indeksen**, ikke hovedenheter. Dette er fordi:

- Underenheter representerer fysiske arbeidsplasser
- Hovedenheter er juridiske enheter som kan ha flere underenheter
- Jobbsøkere møter opp på underenhetens adresse

```mermaid
graph TD
    HOVED[Hovedenhet<br/>Konsern AS<br/>Orgnr: 987654321]

    UNDER1[Underenhet<br/>Konsern AS avd. Oslo<br/>Orgnr: 123456789]
    UNDER2[Underenhet<br/>Konsern AS avd. Bergen<br/>Orgnr: 234567890]
    UNDER3[Underenhet<br/>Konsern AS avd. Trondheim<br/>Orgnr: 345678901]

    HOVED --> UNDER1
    HOVED --> UNDER2
    HOVED --> UNDER3
```

## Dataoppdatering

Enhetsregisteret oppdateres daglig fra Brønnøysundregistrene. pam-search indekserer endringer løpende.

## Se også

- [Kandidatsøk](kandidatsok.md) - Søk etter jobbsøkere
- [Tilgangsstyring](../3-sikkerhet/tilgangsstyring.md) - Hvem har tilgang til arbeidsgiversøk
