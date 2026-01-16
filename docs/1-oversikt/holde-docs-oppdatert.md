# Holde dokumentasjon oppdatert med kode

En av de største utfordringene med dokumentasjon er å holde den i sync med koden etter hvert som systemet utvikler seg. I dette prosjektet bruker vi **GitHub Copilot** aktivt for å automatisere vedlikeholdet av dokumentasjonen.

For generelle tips om bruk av AI-verktøyene, se [Copilot Arbeidsflyt](../5-ki/copilot-arbeidsflyt.md).

## Fremgangsmåte

Bruk Copilot i **Agent-modus** (`@workspace`) i chaten i VS Code. Sørg for at du bruker en kapabel modell (f.eks. Claude 3.5 Sonnet eller Gemini Pro) som håndterer stor kontekst.

### Oppdatering av databasedokumentasjon

Når du har lagt til nye tabeller eller endret skjemaet, kan du be Copilot oppdatere dokumentasjonen ved å lese migrasjonsfilene.

**Forslag til prompt:**
> "@workspace Les alle SQL-migrasjonsfilene i `apps/rekrutteringstreff-api/src/main/resources/db/migration/`. Oppdater `docs/2-arkitektur/database.md` slik at ER-diagrammet og tabellbeskrivelsene stemmer overens med nåværende skjema. Behold eksisterende formatering."

### Oppdatering av flyt og sekvensdiagrammer

Hvis logikken for en prosess endres (f.eks. invitasjonsløpet), kan Copilot lese koden og oppdatere Mermaid-diagrammene.

**Forslag til prompt for invitasjon:**
> "@workspace Analyser koden i pakken `no.nav.rekrutteringstreff.api.invitasjon` (og tilhørende lyttere). Se spesielt på hvordan statusendringer håndteres. Oppdater sekvensdiagrammet og teksten i `docs/4-integrasjoner/invitasjon.md` slik at det reflekterer den faktiske koden nøyaktig."

Du kan gjøre tilsvarende for andre integrasjoner (f.eks. varsling eller synlighet) ved å peke på de relevante kodepakkene og dokumentasjonsfilene.

### Oppdatering av hele dokumentasjonsmappen

Det er også mulig å be agenten forsøke å gjennomgå hele dokumentasjonsstrukturen for å finne avvik.

**Forslag til prompt:**
> "@workspace Skann gjennom alle markdown-filer i `docs/`-mappen. Sjekk innholdet opp mot koden i `src/`. Lag en liste over områder som ser ut til å være utdaterte, eller foreslå direkte endringer for å bringe dokumentasjonen i sync med koden."

**Merk:** Resultatet av en slik "helsesjekk" på hele mappen avhenger sterkt av kontekstvinduet til KI-modellen du bruker. Med dagens modeller kan det forekomme at den overser detaljer i store kodebaser, men dette vil fungere stadig bedre etter hvert som modellene i fremtiden får større kapasitet og bedre forståelse av hele repoet.
