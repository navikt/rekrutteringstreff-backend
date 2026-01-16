# Ytelsestesting

## Mål

Vi bør ytelsesteste løsningen for å sikre at den skalerer for fremtidige behov, spesielt hvis vi skal støtte større arrangementer som jobbmesser.

### Testscenarioer

Vi bør teste invitasjon av:

- **1 000 jobbsøkere** - For store rekrutteringstreff
- **10 000 jobbsøkere** - For potensielle fremtidige jobbmesser

### Utfordringer

- **Testdata**: Det kan være en utfordring å generere eller finne nok realistiske testkandidater (jobbsøkere med fnr) i preprod-miljøene som har nødvendige data (CV, oppfølgingsstatus) for å kunne inviteres. Vi må undersøke muligheter for syntetiske testdata eller mocking av eksterne avhengigheter for i volumtester.
