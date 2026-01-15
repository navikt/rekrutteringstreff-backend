# Vedlikeholdbarhet

_Dette dokumentet er under utarbeidelse._

## Modularitet

Applikasjonen er strukturert i separate moduler med klare ansvarsområder:

- **Controller-lag**: Håndterer HTTP-forespørsler og autorisasjon
- **Service-lag**: Forretningslogikk og orkestrering
- **Repository-lag**: Database-tilgang med SQL

Hver modul (jobbsøker, arbeidsgiver, rekrutteringstreff, innlegg) følger samme struktur.

## Hendelsesdrevet arkitektur

Nye funksjoner kan legges til uten å endre eksisterende kode:

- Publiser hendelser til Rapids & Rivers
- Opprett nye lyttere som reagerer på hendelsene
- Schedulere håndterer periodiske operasjoner

## Testbarhet

- Repository-klasser kan mockes for enhetstester
- Integrasjonstester bruker testdatabase og MockOAuth2Server
- WireMock simulerer eksterne tjenester

## Forslag til videre dokumentasjon

- Kodekonvensjoner og navnestandarder
- Feilhåndteringsstrategi
- Logging og overvåking
