# Forslag til plan for Arbeidsgivers behov

Når en markedskontakt legger til en arbeidsgiver i et rekrutteringstreff, skal de også kunne registrere arbeidsgiverens behov. Behovene er kun synlige for eiere av treffet og brukere med utviklerrollen.

## Felter

| Felt            | Type                 | Beskrivelse                                                                                        |
| --------------- | -------------------- | -------------------------------------------------------------------------------------------------- |
| kompetanser     | Tagliste             | Ønskede krav som førerkort, fagbrev osv. Velges kun fra forslag, uten fritekst                     |
| arbeidsspråk    | Tagliste             | Språk som kreves i stillingen. Samme verdier som `workLanguage` på stilling                        |
| antall          | Heltall              | Antall stillinger arbeidsgiver ønsker å fylle                                                      |
| arbeidsoppgaver | Tagliste (typeahead) | Yrkestitler fra Janzz yrkesontologi. Velges kun fra Janzz-forslag, uten fritekst                   |
| ansettelsesform | Nedtrekksliste       | Fast, Vikariat, Engasjement, Prosjekt, Sesong, osv. Samme verdier som `engagementtype` på stilling |

## Hendelser

Eksisterende hendelsestyper `OPPRETTET` og `SLETTET` beholdes som i dag. `OPPDATERT` (finnes i enum, brukes ikke ennå) tas i bruk for endring av behov.

| Hendelsestype | Trigger                 | hendelse_data      |
| ------------- | ----------------------- | ------------------ |
| OPPRETTET     | Arbeidsgiver legges til | `null` (som i dag) |
| OPPDATERT     | Behov endres            | `null`             |
| SLETTET       | Arbeidsgiver slettes    | `null` (som i dag) |

## Regler

- Kun eiere av treffet og utvikler ser behov. Andre roller ser arbeidsgiver + orgnummer som i dag, uten knapp for å åpne modal og uten annen behovsvisning.
- Bare eier kan legge til arbeidsgiver.
- Behov opprettes sammen med arbeidsgiveropprettelsen i treffet (utvid eksisterende opprettelsesflyt).
- Næringskoder følger eksisterende flyt og lagres som i dag ved opprettelse av arbeidsgiver.
- Alle feltene i `ArbeidsgiverBehovDto` er obligatoriske ved opprettelse og oppdatering. Vi legger alltid ved alle verdiene ved oppdatering, også de som ikke er endret, ingen patching.
- Listefeltene `kompetanser`, `arbeidssprak` og `arbeidsoppgaver` må inneholde minst ett element ved opprettelse og oppdatering.
- Behov kan endres i etterkant. Orgnavn og orgnummer kan ikke endres. Hvis arbeidsgivernavn eller organisasjonsnummer er feil, må arbeidsgiveren slettes og legges inn på nytt.
- Ved endring av arbeidsgiver: orgnavn og orgnummer er ikke redigerbare (vises som `disabled`/grået ut).
- Behov vises og redigeres i modal åpnet fra arbeidsgiverkortet, ikke direkte i kortet.
- Arbeidsgiver soft-slettes i dag. Behov trenger derfor ikke slettes ved soft delete, men skal heller ikke vises når arbeidsgiver er slettet.

## Flyway-migrasjon (V4)

```sql
CREATE TABLE arbeidsgiver_behov (
    behov_id         bigserial PRIMARY KEY,
    arbeidsgiver_id  bigint NOT NULL REFERENCES arbeidsgiver(arbeidsgiver_id),
    kompetanser     text[] NOT NULL DEFAULT '{}',
    arbeidssprak    text[] NOT NULL DEFAULT '{}',
    antall          int,
    arbeidsoppgaver text[] NOT NULL DEFAULT '{}',
    ansettelsesform text
);

CREATE UNIQUE INDEX idx_arbeidsgiver_behov_arbeidsgiver ON arbeidsgiver_behov(arbeidsgiver_id);
```

Merk: Dette er en egen tabell for behov, knyttet til arbeidsgivertabellen. I API-et håndteres behov likevel sammen med arbeidsgiverressursen.
En av årsakene til delingen er at arbeidsgiver muligens senere skal knyttes til jobbsøker, uten at behovene er viktige. Og at behovene skal skjules for de som ikke trenger tilgang. Samtidig har vi vurdert om behov på et eller annet tidspunkt er noe som skal trekkes ut og finnes utenfor rekrutteringstreff-api.

## DTO

```kotlin
data class ArbeidsgiverBehovDto(
  val kompetanser: List<String>,
  val arbeidssprak: List<String>,
  val antall: Int,
  val arbeidsoppgaver: List<String>,
  val ansettelsesform: String,
)
```

Brukes i:

- Request-body for `PUT .../behov`
- Obligatorisk felt i `LeggTilArbeidsgiver` ved opprettelse: `behov: ArbeidsgiverBehovDto`
- Valgfritt felt `behov` i respons fra `GET .../arbeidsgiver?include=behov`
- Alle feltene i `ArbeidsgiverBehovDto` valideres som obligatoriske, og listefeltene må ha minst ett element.

## Plassering i backend

- Gjenbruk `ArbeidsgiverController`, `ArbeidsgiverService` og utvid `ArbeidsgiverRepository`, siden behov er en del av arbeidsgiverressursen og naturlig hører hjemme i eksisterende arbeidsgiverflyt.
- `leggTilArbeidsgiverHandler()` kan fortsatt være inngangen for opprettelse, men service-laget bør få ansvar for å lagre arbeidsgiver, næringskoder, hendelse og eventuelt behov samlet.
- Endringen skal ikke påvirke eksisterende håndtering av `næringskoder`; de følger fortsatt arbeidsgiveropprettelsen og trenger ikke egne endepunkter eller egen UI i denne planen.
- Behov leses via eksisterende arbeidsgiver-endepunkt med `include=behov`, ikke via eget `GET .../behov`.
- `GET .../arbeidsgiver` uten `include=behov` følger dagens tilgang og respons.
- `GET .../arbeidsgiver?include=behov` krever eier eller utvikler. Andre roller får `403` når de eksplisitt ber om behov.
- `PUT .../behov` kan implementeres i `ArbeidsgiverController`, med samme autorisasjonsmønster som dagens eierbeskyttede arbeidsgiver-endepunkter: eiere og utvikler har tilgang.

## Backend

- [ ] Flyway-migrasjon V4 (SQL over)
- [ ] `ArbeidsgiverBehov`-modell og utvidelser i `ArbeidsgiverRepository`
- [ ] Utvid `LeggTilArbeidsgiver`-DTOen med obligatorisk `behov: ArbeidsgiverBehovDto`
- [ ] Utvid `ArbeidsgiverService` til å lagre behov i samme transaksjon som arbeidsgiveropprettelse
- [ ] Utvid `leggTilArbeidsgiverHandler()` i `ArbeidsgiverController` til å sende behov videre i eksisterende opprettelsesflyt
- [ ] Utvid `GET /api/rekrutteringstreff/{id}/arbeidsgiver` med støtte for `include=behov`
- [ ] Nytt endepunkt: `PUT /api/rekrutteringstreff/{id}/arbeidsgiver/{arbeidsgiverId}/behov`
- [ ] Tilgangskontroll: `GET .../arbeidsgiver?include=behov` og `PUT .../behov` krever eier eller utvikler
- [ ] `OPPDATERT`-hendelse brukes ved endring av behov med `hendelse_data = null`

## Frontend

- [ ] Utvid `LeggTilArbeidsgiverForm` med behovfelter under arbeidsgiverseksjonen
  - Kompetanser: `Combobox` uten fritekst, kun valg fra forslag
  - Arbeidsspråk: `Combobox` med `allowNewValues`
  - Antall: tallfelt
  - Arbeidsoppgaver: `Combobox` med Janzz yrkesontologi-typeahead, kun valg fra Janzz-forslag
  - Ansettelsesform: `Select` med samme faste verdier som stillingens `engagementtype`
- [ ] Legg til en egen knapp på arbeidsgiverkortet ved siden av slett knappen, for å åpne redigering av behov i modal
- [ ] Rediger arbeidsgiver i modal: orgnavn og orgnummer vises som `disabled` (grået ut, ikke redigerbare). Kun behovfelter er redigerbare.
- [ ] Valgte `kompetanser` og `arbeidsoppgaver` kan fjernes med små kryss, etter samme mønster som andre multivalg i løsningen
- [ ] Skjul behovfelter for brukere som ikke eier treffet. De skal ikke se knappen som åpner modalen, og behov skal ikke vises andre steder.

## Testliste

### Backend komponenttester

- [ ] Opprette arbeidsgiver med behov lagrer arbeidsgiver og behov i samme transaksjon
- [ ] Opprette arbeidsgiver med næringskoder og behov lagrer både næringskoder og behov
- [ ] Opprette arbeidsgiver uten behov gir valideringsfeil
- [ ] Opprette arbeidsgiver med manglende felt i `behov` gir valideringsfeil
- [ ] Opprette arbeidsgiver med tom `kompetanser` gir valideringsfeil
- [ ] Opprette arbeidsgiver med tom `arbeidssprak` gir valideringsfeil
- [ ] Opprette arbeidsgiver med tom `arbeidsoppgaver` gir valideringsfeil
- [ ] `GET .../arbeidsgiver?include=behov` returnerer lagrede verdier for eier
- [ ] Oppdatere behov endrer verdiene og oppretter `OPPDATERT`-hendelse
- [ ] `PUT .../behov` med manglende felt gir valideringsfeil
- [ ] `PUT .../behov` med tom `kompetanser`, `arbeidssprak` eller `arbeidsoppgaver` gir valideringsfeil
- [ ] Ikke-eier får `403` ved opprettelse av arbeidsgiver
- [ ] Ikke-eier med arbeidsgiverrettet rolle får `403` på `GET .../arbeidsgiver?include=behov`
- [ ] Ikke-eier med arbeidsgiverrettet rolle får `403` på `PUT .../behov`
- [ ] Utvikler får tilgang til `GET .../arbeidsgiver?include=behov` og `PUT .../behov`
- [ ] Jobbsøkerrettet rolle får `403` på `GET .../arbeidsgiver?include=behov` og `PUT .../behov`
- [ ] `GET .../arbeidsgiver` uten `include=behov` fungerer som i dag for roller som allerede har lesetilgang
- [ ] Soft-slettet arbeidsgiver eksponerer ikke behov i arbeidsgivervisningene

### Backend repositorietester

- [ ] `ArbeidsgiverRepository` lagrer og henter behov korrekt
- [ ] `ArbeidsgiverRepository` oppdaterer eksisterende behov korrekt
- [ ] Obligatoriske felt lagres uten `NULL`-verdier
- [ ] `arbeidsgiver_id` er unik i `arbeidsgiver_behov`

### Backend servicetester

- [ ] `ArbeidsgiverService` lagrer behov ved opprettelse når DTO inneholder behov
- [ ] `ArbeidsgiverService` avviser opprettelse når DTO mangler behov
- [ ] `ArbeidsgiverService` avviser opprettelse når et obligatorisk felt i `behov` mangler
- [ ] `ArbeidsgiverService` avviser opprettelse når et listefelt i `behov` er tomt
- [ ] `ArbeidsgiverService` oppdaterer behov og oppretter `OPPDATERT`-hendelse
- [ ] `ArbeidsgiverService` avviser oppdatering når et obligatorisk felt i `behov` mangler
- [ ] `ArbeidsgiverService` avviser oppdatering når et listefelt i `behov` er tomt
- [ ] Transaksjonen ruller tilbake hvis lagring av behov feiler under opprettelse

### Frontend Playwright

- [ ] Eier kan legge til arbeidsgiver med behov ved opprettelse av treff
- [ ] Eier kan ikke lagre arbeidsgiver hvis et obligatorisk behovsfelt mangler
- [ ] Eier kan ikke lagre arbeidsgiver hvis `kompetanser`, `arbeidsspråk` eller `arbeidsoppgaver` er tomme
- [ ] Eier kan åpne redigeringsmodal fra arbeidsgiverkortet og redigere kun behovfeltene
- [ ] Eier kan ikke lagre oppdatering hvis et obligatorisk behovsfelt mangler
- [ ] Eier kan ikke lagre oppdatering hvis `kompetanser`, `arbeidsspråk` eller `arbeidsoppgaver` er tomme
- [ ] Eier ser lagrede behov i redigeringsmodalen etter reload
- [ ] Ikke-eier ser arbeidsgiver uten behovfelter, uten knapp for å åpne modal og uten annen behovsvisning
- [ ] Utvikler ser behov på samme måte som eier
- [ ] Ansettelsesform viser samme verdier som stillingsfeltet
- [ ] Arbeidsoppgaver kan legges til via Janzz-typeahead uten fritekst
- [ ] Kompetanser kan kun velges fra forslag, uten fritekst
- [ ] Valgte `kompetanser` og `arbeidsoppgaver` kan fjernes med små kryss i modal
- [ ] Arbeidsspråk støtter flere valg og fritekst
