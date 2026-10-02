# rekrutteringstreff-backend

## Bygg og deploy

### Automatisk oppdatering av Docker run-time base-image

Hver app har en scheduled workflow `oppdater-docker-baseimage-<app>.yaml` som potensielt bygger og deployer appen til prod ukentlig, uten manuelle trinn.
Hensikten med det er å få med nye patcher av sikkerhets-issues i appens Docker run-time base-image.

## Swagger

URL; https://rekrutteringstreff-api.intern.dev.nav.no/swagger

For å kunne generere token for dev kan man gjøre en GET mot
https://fakedings.intern.dev.nav.no/fake/aad?aud=dev-gcp:toi:rekrutteringstreff-api&NAVident=Z999999

## Kode generert av GitHub Copilot

Dette repoet bruker GitHub Copilot til å generere kode.

## Henvendelser

### For Nav-ansatte

- Dette Git-repositoriet eies av [team Toi](https://teamkatalog.nav.no/team/76f378c5-eb35-42db-9f4d-0e8197be0131).
- Slack: [#arbeidsgiver-toi-dev](https://nav-it.slack.com/archives/C02HTU8DBSR)

### For folk utenfor Nav

- Teknologiavdelingen i [Arbeids- og velferdsdirektoratet](https://www.nav.no/no/NAV+og+samfunn/Kontakt+NAV/Relatert+informasjon/arbeids-og-velferdsdirektoratet-kontorinformasjon)
