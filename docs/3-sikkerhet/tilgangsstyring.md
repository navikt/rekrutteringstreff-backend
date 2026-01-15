# Tilgangsstyring

Løsningen har tre typer tilganger:

## 1. Sluttbruker (Borger)

Sluttbrukere autentiseres via TokenX/ID-porten. Denne tilgangen brukes av:

- **rekrutteringstreff-minside-api**: Fronter sluttbrukerflaten. Autentiserer borgere med `pid`-claim (personnummer) fra TokenX.
- **rekrutteringstreff-api**: Støtter også `BORGER`-rollen, men kun fordi minside-api videresender kall med TokenX-token. Borger har tilgang til:
  - Hente informasjon om rekrutteringstreff de er invitert til
  - Svare ja/nei på invitasjoner
  - Se innlegg og arbeidsgivere knyttet til treffet

## 2. NAV-ansatt

NAV-ansatte autentiseres via Azure AD og får roller basert på AD-gruppemedlemskap. Det er tre roller:

### Veileder (`JOBBSØKER_RETTET`)

- **AD-gruppe**: `AD_GRUPPE_REKRUTTERINGSBISTAND_JOBBSOKERRETTET`
- **Tilgang**:
  - Hente og se rekrutteringstreff
  - Legge til jobbsøkere (kandidater) til treff
  - Se innlegg
- **Ikke tilgang til**:
  - Opprette, oppdatere eller slette rekrutteringstreff
  - Administrere arbeidsgivere
  - KI-funksjonalitet

### Markedskontakt (`ARBEIDSGIVER_RETTET`)

- **AD-gruppe**: `AD_GRUPPE_REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET`
- **Tilgang**:
  - Opprette rekrutteringstreff
  - Oppdatere og slette egne treff (der de er eier)
  - Administrere arbeidsgivere og jobbsøkere på egne treff
  - Legge til jobbsøkere på andres treff
  - Hente og se alle rekrutteringstreff
  - Se hendelseslogg for egne treff
- **Eierskap**: Kun eier av et treff kan oppdatere/slette det og se detaljert hendelseslogg

### Administrator/Utvikler (`UTVIKLER`)

- **AD-gruppe**: `AD_GRUPPE_REKRUTTERINGSBISTAND_UTVIKLER`
- **Tilgang**:
  - Full tilgang til alle funksjoner, uavhengig av eierskap
  - KI-validering og logging (kun tilgjengelig for utviklere)
  - Hente og administrere alle treff

## 3. System

Systemtilgang brukes for maskin-til-maskin-kommunikasjon, for eksempel:

- Lytting på Kafka-meldinger (rapids-and-rivers)
- Interne API-kall mellom tjenester

## Pilotkontorer

I tillegg til rollekrav må NAV-ansatte (unntatt utviklere) i pilotperioden være innlogget på et pilotkontor for å få tilgang til rekrutteringstreff-funksjonalitet.
