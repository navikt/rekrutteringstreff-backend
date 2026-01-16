# Utvidbarhet

Rekrutteringstreff er designet for å kunne utvides til nye brukergrupper og bruksscenarier uten store arkitekturendringer.

## Diskuterte utvidelsesmuligheter

### Uavhengighet fra CV-krav

I dag kreves det at jobbsøkere har CV og samtykke for å være synlige. Vi tilrettelegger for at vi muligens kan fjerne dette kravet:

- Synlighetssjekk er isolert i egen integrasjon mot toi-synlighetsmotor
- Kan enkelt gjøres valgfri eller betinget per treff-type
- Jobbsøkere uten CV skal kunne delta på rekrutteringstreff

### Brukere utenfor oppfølging

Dagens løsning er knyttet til NAVs oppfølgingsløp, men vi tilrettelegger for at vi muligens kan støtte brukere som ikke er under oppfølging:

- **Aktivitetskort er valgfritt**: Integrasjonen skal kunne deaktiveres per bruker/treff dersom det blir ønskelig.
- **MinSide som primærkanal**: To innganger til MinSide (varsel + aktivitetskort) sikrer at brukere uten aktivitetsplan fortsatt kan nås via varsel
- **Hendelsesdrevet**: Aktivitetskort-tjenesten lytter på events og kan filtrere hvilke brukere som skal få kort

### Alternative innganger for påmelding

Løsningen bør ikke være låst til internt kandidatsøk som eneste kilde til jobbsøkere. Vi bør tilrettelegge for alternative innganger hvis behovet oppstår:

- **Stillingssøk**: Rekrutteringstreff kan vises sammen med stillinger, med mulighet for selvpåmelding
- **Målrettede landingssider**: Egne sider for f.eks. ungdom eller spesifikke målgrupper med direktelenke til påmelding
- **Ekstern påmelding**: API-et bør kunne støtte at jobbsøkere registreres fra flere kilder

### Skalerbarhet for store arrangementer

Selv om dagens fokus er mindre treff, forbereder vi for større arrangementer:

- Asynkron hendelseshåndtering via Rapids & Rivers tåler høy last
- Schedulere håndterer batch-operasjoner (varsling, aktivitetskort)
- Database-skjema støtter mange jobbsøkere per treff

> **Merknad**: Detaljert skalerbarhetsdokumentasjon kan utvides ved behov.

### Tilpasning til Workops-møter

NAV har et konsept med "Workops" der det arrangeres formøter med kandidater før selve møtet med arbeidsgivere. Dette kan kreve tilpasninger:

- **Formøte uten arbeidsgivere**: I et formøte med kandidater er det ikke nødvendigvis behov for å legge til arbeidsgivere. For Workops-møter kan det bli aktuelt å tillate at treff arrangeres uten arbeidsgivere.
- **Kobling mellom formøte og hovedmøte**: Det kan bli relevant å koble et formøte med kandidater til det påfølgende møtet med arbeidsgivere, slik at kandidatene følger med fra formøte til hovedmøte.

> **Innsikt nødvendig**: Det kreves ytterligere brukerinnsikt for å forstå behovene og finne riktig løsning for Workops-flyten.

## Arkitekturprinsipper for utvidbarhet

### Hendelsesdrevet utvidelse

Nye operasjoner kan enkelt legges til ved å:

1. Definere nye hendelsestyper
2. Opprette lyttere/schedulere som reagerer på hendelsene
3. Hendelser publiseres til Rapids & Rivers og kan konsumeres av nye tjenester

Dette fungerer godt for operasjoner som tåler eventual consistency.

### Løs kobling mellom moduler

- MinSide-API kommuniserer med hovedAPI via REST, ikke direkte databasetilgang
- Aktivitetskort-tjenesten er helt frakoblet og lytter kun på events
- Synlighetssjekk er en ekstern tjeneste som kan byttes ut
