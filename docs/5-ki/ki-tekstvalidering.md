# KI-tekstvalidering

## Formål

Vi bruker kunstig intelligens (KI) for å validere at tekst i rekrutteringstreff ikke er diskriminerende eller bryter med NAVs retningslinjer. Valideringen sikrer at:

- Tekst ikke er direkte eller indirekte diskriminerende
- Likestillings- og diskrimineringsloven overholdes
- Taushetsbelagte opplysninger ikke eksponeres
- Personopplysninger ikke sendes til ekstern KI-tjeneste

Backend garanterer at tekst ikke kan lagres uten gyldig KI-validering.

---

## Overordnet flyt

```mermaid
flowchart TD
    subgraph Frontend
        EDIT[Bruker redigerer tekst]
        BLUR[onBlur trigger]
        LAGRE_BTN[Bruker klikker lagre]
    end

    subgraph "Backend: KI-validering"
        KI_CTRL[KiController<br/>/api/.../ki/valider]
        PF[PersondataFilter]
        OAC[OpenAiClient]
        SP[SystemPrompt]
    end

    subgraph "Azure OpenAI"
        AOAI[GPT-4.1<br/>+ Content Filter]
    end

    subgraph "Backend: Lagring"
        LAGRE_CTRL[Controller<br/>PUT /api/...]
        KI_SVC[KiValideringsService]
    end

    subgraph Database
        LOGG_DB[(ki_spørring_logg)]
        DATA_DB[(rekrutteringstreff)]
    end

    %% Valideringsflyten
    EDIT --> BLUR
    BLUR -->|1. Valider tekst| KI_CTRL
    KI_CTRL --> PF
    PF -->|Filtrert tekst| OAC
    OAC --> SP
    OAC -->|API-kall| AOAI
    AOAI -->|Vurdering| OAC
    OAC -->|Logg| LOGG_DB
    KI_CTRL -->|loggId + bryterRetningslinjer| Frontend

    %% Lagringsflyten
    LAGRE_BTN -->|2. Lagre med loggId| LAGRE_CTRL
    LAGRE_CTRL --> KI_SVC
    KI_SVC -->|Verifiser loggId| LOGG_DB
    KI_SVC -->|OK| LAGRE_CTRL
    LAGRE_CTRL --> DATA_DB

    style AOAI fill:#e8f5e9,color:#000,stroke:#333
    style LOGG_DB fill:#e1f5ff,color:#000,stroke:#333
    style DATA_DB fill:#e1f5ff,color:#000,stroke:#333
```

---

## Del 1: KI-validering

### Azure OpenAI-oppsett

| Egenskap            | Verdi                     |
| ------------------- | ------------------------- |
| **Modell**          | GPT-4.1                   |
| **Deployment-type** | Standard                  |
| **Azure-region**    | Norway East               |
| **Content Filter**  | Standard (hateful speech) |

### Modellparametere

| Parameter         | Verdi       | Beskrivelse                |
| ----------------- | ----------- | -------------------------- |
| `temperature`     | 0.0         | Deterministisk output      |
| `max_tokens`      | 400         | Maks lengde på respons     |
| `top_p`           | 1.0         | Ingen sampling-begrensning |
| `response_format` | json_object | Krever JSON-output         |

### Innebygd content filter

Azure OpenAI fanger opp hatefulle ytringer, voldelig/seksuelt innhold og selvskading. Ved trigger returneres 400-feil som vi oversetter til en forklarende melding.

### Persondata-filtrering

Før tekst sendes til KI, filtreres personsensitive data ut:

- Fødselsnummer
- Telefonnummer
- E-postadresser
- Kontonummer

### Systemprompt

Systemprompten definerer valideringsreglene. Se [`SystemPrompt.kt`](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/rekrutteringstreff/ki/SystemPrompt.kt).

**Hovedprinsipper:**

1. Identifisere diskriminering basert på kjønn, alder, etnisitet, religion, funksjonsevne, tiltak/ytelser eller bosted
2. Sikre taushetsplikt (ikke avsløre NAV-tiltak som KVP, AAP, IPS)
3. Godta satsningsområder: flyktninger og ungdom (18-30) med eksplisitt aldersspenn
4. Kun tillate språkkrav der språket er oppgitt som arbeidsspråk

**Versjonering:** Prompten har versjonsnummer, tidsstempel og SHA-256 hash for sporbarhet.

### API-respons

```json
{
  "loggId": "7f1f5a2c-6d2a-4a7b-9c2b-1f0d2a3b4c5d",
  "bryterRetningslinjer": false,
  "begrunnelse": "Teksten beskriver et åpent rekrutteringstreff uten diskriminerende elementer.",
  "validertTekst": "Vi søker etter en blid og motivert medarbeider."
}
```

| Felt                   | Beskrivelse                                                   |
| ---------------------- | ------------------------------------------------------------- |
| `loggId`               | Referanse til KI-logg, sendes tilbake ved lagring             |
| `bryterRetningslinjer` | Om KI mener teksten bryter retningslinjene                    |
| `begrunnelse`          | KIs forklaring på vurderingen                                 |
| `validertTekst`        | Den validerte teksten (etter eventuell persondata-filtrering) |

---

## Del 2: Backend-sikring ved lagring

### Verifiseringsflyt

Ved lagring sjekker `KiValideringsService`:

```mermaid
flowchart TD
    START[Motta lagre-request] --> ENDRET{Tekst endret?}
    ENDRET -->|Nei| OK1[✓ Lagre direkte]
    ENDRET -->|Ja| TOM{Tom tekst?}

    TOM -->|Ja| OK2[✓ Lagre]
    TOM -->|Nei| LOGG_ID{Har loggId?}

    LOGG_ID -->|Nei| AVVIS1[✗ KI_VALIDERING_MANGLER]
    LOGG_ID -->|Ja| OPPSLAG[Slå opp i ki_spørring_logg]

    OPPSLAG --> FINNES{Finnes?}
    FINNES -->|Nei| AVVIS2[✗ KI_LOGG_ID_UGYLDIG]
    FINNES -->|Ja| FELT_TYPE{feltType matcher?}

    FELT_TYPE -->|Nei| AVVIS5[✗ KI_FEIL_FELT_TYPE]
    FELT_TYPE -->|Ja| TREFF_ID{treffId matcher?}

    TREFF_ID -->|Nei| AVVIS6[✗ KI_FEIL_TREFF]
    TREFF_ID -->|Ja| MATCH{Tekst matcher<br/>spørringFraFrontend?}

    MATCH -->|Nei| AVVIS3[✗ KI_TEKST_ENDRET]
    MATCH -->|Ja| BRYTER{bryterRetningslinjer?}

    BRYTER -->|Nei| OK3[✓ Lagre]
    BRYTER -->|Ja| BEKREFTET{lagreLikevel=true?}

    BEKREFTET -->|Nei| AVVIS4[✗ KI_KREVER_BEKREFTELSE]
    BEKREFTET -->|Ja| OK4[✓ Lagre]

    style AVVIS1 fill:#ffcdd2,color:#000,stroke:#c62828
    style AVVIS2 fill:#ffcdd2,color:#000,stroke:#c62828
    style AVVIS3 fill:#ffcdd2,color:#000,stroke:#c62828
    style AVVIS4 fill:#ffcdd2,color:#000,stroke:#c62828
    style AVVIS5 fill:#ffcdd2,color:#000,stroke:#c62828
    style AVVIS6 fill:#ffcdd2,color:#000,stroke:#c62828
    style OK1 fill:#c8e6c9,color:#000,stroke:#2e7d32
    style OK2 fill:#c8e6c9,color:#000,stroke:#2e7d32
    style OK3 fill:#c8e6c9,color:#000,stroke:#2e7d32
    style OK4 fill:#c8e6c9,color:#000,stroke:#2e7d32
```

### Tekstsammenligning

Backend sammenligner mot `spørringFraFrontend` (originalteksten), **ikke** `spørringFiltrert` (persondatafiltrert). Frontend kjenner ikke til filtreringen.

**Normalisering:** HTML-tagger fjernes, whitespace kollapses, trim utføres.

### Feilkoder (HTTP 422)

| Feilkode                | Årsak                                       | Frontend-håndtering        |
| ----------------------- | ------------------------------------------- | -------------------------- |
| `KI_VALIDERING_MANGLER` | Ingen loggId oppgitt                        | Vent på validering         |
| `KI_LOGG_ID_UGYLDIG`    | LoggId finnes ikke                          | Trigger ny validering      |
| `KI_FEIL_FELT_TYPE`     | LoggId tilhører feil felttype               | Trigger ny validering      |
| `KI_FEIL_TREFF`         | LoggId tilhører et annet rekrutteringstreff | Trigger ny validering      |
| `KI_TEKST_ENDRET`       | Tekst endret etter validering               | Vent på ny validering      |
| `KI_KREVER_BEKREFTELSE` | Bruker må bekrefte advarsel                 | Vis "Lagre likevel"-dialog |

### Request-format

```json
{
  "tittel": "Jobbtreff for IT-bransjen",
  "tittelKiLoggId": "7f1f5a2c-6d2a-4a7b-9c2b-1f0d2a3b4c5d",
  "lagreLikevel": false
}
```

### Felter som valideres

| Felt    | Controller                   | Operasjon   | DTO-felt          |
| ------- | ---------------------------- | ----------- | ----------------- |
| Tittel  | RekrutteringstreffController | PUT         | `tittelKiLoggId`  |
| Innlegg | InnleggController            | POST og PUT | `innleggKiLoggId` |

---

## Frontend: Autolagring med KI-validering

Frontend integrerer KI-validering med autolagring via `useFormFeltMedKiValidering`-hooken.

### Flyt for kladdemodus (utkast)

```mermaid
sequenceDiagram
    participant Bruker
    participant Frontend
    participant KI_API as KI-validering API
    participant Lagre_API as Lagre API
    participant KI_Logg as KI-logg API

    Bruker->>Frontend: Redigerer tittel/innlegg
    Bruker->>Frontend: onBlur (forlater felt)

    Frontend->>Frontend: Sjekk om tekst er endret
    alt Tekst uendret
        Frontend->>Frontend: Ingen handling
    else Tekst endret
        Frontend->>KI_API: POST /ki/valider
        KI_API-->>Frontend: {loggId, bryterRetningslinjer, begrunnelse}

        alt bryterRetningslinjer = false
            Frontend->>Lagre_API: PUT (med loggId)
            Lagre_API-->>Frontend: OK
            Frontend->>KI_Logg: PUT /logg/{id}/lagret
        else bryterRetningslinjer = true
            Frontend->>Frontend: Vis KI-analyse panel
            Bruker->>Frontend: Klikk "Lagre likevel"
            Frontend->>Lagre_API: PUT (med loggId + lagreLikevel=true)
            Lagre_API-->>Frontend: OK
            Frontend->>KI_Logg: PUT /logg/{id}/lagret
        end
    end
```

### Flyt for redigering av publisert treff

Ved redigering av publisert treff er autolagring deaktivert. KI-validering skjer fortsatt på onBlur, men lagring skjer først ved eksplisitt "Lagre"-klikk.

### Blokkering av autolagring

Autolagring blokkeres når:

- KI-validering pågår (`validating = true`)
- KI har rapportert brudd som ikke er godkjent (`harKiFeil = true && !harGodkjentKiFeil`)
- Felt ikke er KI-sjekket etter endring (`kiSjekket = false`)

### Form-state for KI

For hvert felt som valideres lagres ekstra state i skjemaet:

| Felt              | Beskrivelse                                    |
| ----------------- | ---------------------------------------------- |
| `{felt}KiLoggId`  | Siste gyldige loggId fra KI-validering         |
| `{felt}KiSjekket` | Om feltet er KI-sjekket etter siste endring    |
| `{felt}KiFeil`    | Om KI rapporterte brudd (før evt. godkjenning) |

Eksempel: For tittel-feltet brukes `tittelKiLoggId`, `tittelKiSjekket`, `tittelKiFeil`.

---

## Logging og sporbarhet

### Hva vi logger

Alle KI-spørringer logges i `ki_spørring_logg`:

| Felt                    | Beskrivelse                               |
| ----------------------- | ----------------------------------------- |
| `treff_id`              | Referanse til rekrutteringstreffet        |
| `felt_type`             | `tittel` eller `innlegg`                  |
| `spørring_fra_frontend` | Original tekst fra bruker                 |
| `spørring_filtrert`     | Tekst etter persondata-filtrering         |
| `systemprompt`          | Systemprompt brukt i kallet               |
| `ekstra_parametre`      | JSON med promptversjon, hash, tidsstempel |
| `bryter_retningslinjer` | Boolean resultat fra KI                   |
| `begrunnelse`           | KIs begrunnelse                           |
| `ki_navn`               | azure-openai                              |
| `ki_versjon`            | Deployment-navn (f.eks. toi-gpt-4.1)      |
| `svartid_ms`            | Responstid i millisekunder                |
| `lagret`                | Om teksten ble brukt/lagret               |

### Manuell kontroll

Domeneeksperter kan overprøve KI-vurderinger via admin-grensesnitt:

| Felt                                     | Beskrivelse                |
| ---------------------------------------- | -------------------------- |
| `manuell_kontroll_bryter_retningslinjer` | Domeneekspertens vurdering |
| `manuell_kontroll_utført_av`             | NAV-ident for kontrollør   |
| `manuell_kontroll_tidspunkt`             | Når kontrollen ble utført  |

### Admin-grensesnitt

```mermaid
flowchart LR
    ADMIN[Administrator] --> LOGUI[Loggvisning]
    LOGUI -->|GET /api/ki/logg| KC[KiController]
    KC --> DB[(ki_spørring_logg)]

    ADMIN --> VURD[Manuell vurdering]
    VURD -->|PUT /api/ki/logg/{id}/manuell| KC
```

---

## Testing og forbedring

### Forbedringsprosess

```mermaid
flowchart LR
    LOG[KI-logger] --> AVVIK[Identifiser avvik]
    MANUELL[Manuelle vurderinger] --> AVVIK
    AVVIK --> TESTSUITE[Utvid testsuite]
    TESTSUITE --> PROMPT[Juster systemprompt]
    PROMPT --> BENCHMARK[Kjør benchmark]
    BENCHMARK --> DEPLOY[Deploy forbedring]
```

### Testklasser

| Testklasse                 | Beskrivelse                                              |
| -------------------------- | -------------------------------------------------------- |
| `KiAutorisasjonsTest`      | Tester tilgangskontroll til KI-endepunktene              |
| `KiLoggRepositoryTest`     | Tester logging av KI-spørringer                          |
| `KiValideringsServiceTest` | Tester verifiseringslogikken (feilkoder, normalisering)  |
| `KiTest`                   | Integrasjonstest for KI-validering med Azure OpenAI mock |
| `OpenAiTestClient`         | Hjelpeklasse for testing mot Azure OpenAI                |

---

## Konfigurasjon

### Miljøvariabler

| Variabel            | Beskrivelse                                |
| ------------------- | ------------------------------------------ |
| `OPENAI_API_URL`    | URL til Azure OpenAI deployment            |
| `OPENAI_API_KEY`    | API-nøkkel for autentisering               |
| `OPENAI_DEPLOYMENT` | Navn på deployment (brukes som ki_versjon) |

---

## Relaterte dokumenter

- [Database](../2-arkitektur/database.md) - `ki_spørring_logg`-tabellen
- [Plan for automatiske tester](../7-akseptansetest-og-ros/automatiske-tester.md) - Sikkerhetstester for KI
