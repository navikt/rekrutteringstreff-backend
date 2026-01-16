# Tilgangsstyring

Løsningen har tre brukergrupper med ulike tilganger.

## 1. Personbruker

Personbrukere autentiseres via ID-porten. Tilgangen gir mulighet til å:

- Se rekrutteringstreff de er invitert til
- Svare på invitasjoner
- Se relevant informasjon om treffet

## 2. NAV-ansatt

NAV-ansatte autentiseres via Azure AD og får roller basert på AD-gruppemedlemskap. Det er tre roller:

### Jobbsøkerrettet

Grunnleggende lesetilgang og mulighet til å jobbe med jobbsøkere:

- Se rekrutteringstreff
- Legge til jobbsøkere på treff

### Arbeidsgiverrettet

Utvidet tilgang for å opprette og administrere rekrutteringstreff:

- Opprette og administrere egne rekrutteringstreff
- Administrere deltakere på egne treff
- Bidra med jobbsøkere på andres treff
- Se hendelseslogg for egne treff

### Utvikler/Admin

Full tilgang til alle funksjoner, inkludert:

- Administrere alle treff uavhengig av eierskap
- Tilgang til utviklerverktøy og diagnostikk

## 3. System

Systemtilgang brukes for maskin-til-maskin-kommunikasjon mellom tjenester.

## Pilotkontorer

I pilotperioden må NAV-ansatte (unntatt utviklere) være innlogget på et pilotkontor for å få tilgang.
