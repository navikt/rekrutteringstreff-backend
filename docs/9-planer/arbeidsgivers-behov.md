# Forslag til plan for Arbeidsgivers behov

Når en markedskontakt legger til en arbeidsgiver i et rekrutteringstreff, skal de også kunne registrere arbeidsgiverens behov. Behovene er kun synlige for eiere av treffet og brukere med utviklerrollen.

## Felter

| Felt                   | Type                 | Beskrivelse                                                                                                      |
| ---------------------- | -------------------- | ---------------------------------------------------------------------------------------------------------------- |
| arbeidsoppgaver        | Tagliste (typeahead) | Oppgaver fra Janzz kompetanser + fagbrev + førerkort + yrkestittel. Velges kun fra Janzz-forslag, uten fritekst  |
| arbeidsspråk           | Enum / Tagliste      | Språk som kreves i stillingen. Samme verdier som `workLanguage` på stilling. **Vurdering**: enum eller tagliste? |
| antall                 | Positivt Heltall     | Antall stillinger arbeidsgiver ønsker å fylle                                                                    |
| ansettelsesform        | Nedtrekksliste       | Fast, Vikariat, Engasjement, Prosjekt, Sesong, osv. Samme verdier som `engagementtype` på stilling               |
| personlige egenskaper? | Tagliste (typeahead) | Janzz personlige egenskaper?                                                                                     |

## Hendelser

Eksisterende hendelsestyper `OPPRETTET` og `SLETTET` beholdes som i dag. Når **behov** endres (opprettes eller oppdateres), skal det opprettes en egen hendelsestype for behovsendring, ikke arbeidsgiverhendelse.

| Hendelsestype | Trigger                      | hendelse_data      |
| ------------- | ---------------------------- | ------------------ |
| OPPRETTET     | Arbeidsgiver legges til      | `null` (som i dag) |
| BEHOV_ENDRET  | Behov opprettes eller endres | TBD                |
| SLETTET       | Arbeidsgiver slettes         | `null` (som i dag) |

## Regler

- Kun eiere av treffet og utvikler ser behov. Andre roller ser arbeidsgiver + orgnummer som i dag, uten knapp for å åpne modal og uten annen behovsvisning.
- Bare eier kan legge til arbeidsgiver.
- Arbeidsgivere legges til **én og én** via `LeggTilArbeidsgiverModal`: søk opp arbeidsgiver i et felt → modalen fylles med behovsfeltene → fylles og lagres sammen. **Behov er obligatorisk for alle arbeidsgivere.** Dette gjelder både ved opprettelse av treff og etter publisering.
- Næringskoder følger eksisterende flyt og lagres som i dag ved opprettelse av arbeidsgiver.
- Alle feltene i `ArbeidsgiverBehovDto` er obligatoriske ved lagring av behov. Vi legger alltid ved alle verdiene, også de som ikke er endret, ingen patching.
- Listefeltene `arbeidssprak` og `arbeidsoppgaver` må inneholde minst ett element ved lagring av behov.
- Arbeidsgiver + behov lagres som en atomisk enhet via `LeggTilArbeidsgiverModal`. Arbeidsgiveren lagres IKKE uten behov ved bruk av modalen. Behov er obligatorisk input.
- Publisering krever minst én arbeidsgiver med behovsbeskrivelse. Sjekklisten (`useSjekklisteStatus`) må oppdateres: arbeidsgiver-punktet er oppfylt når minst én arbeidsgiver har registrert behovsbeskrivelse, ikke bare når det finnes arbeidsgivere.
- Behov kan endres i etterkant. Orgnavn og orgnummer kan ikke endres. Hvis arbeidsgivernavn eller organisasjonsnummer er feil, må arbeidsgiveren slettes og legges inn på nytt.
- Ved endring av arbeidsgiver: orgnavn og orgnummer er ikke redigerbare (vises som `disabled`/grået ut).
- Behov vises og redigeres i modal åpnet fra arbeidsgiverkortet, ikke direkte i kortet. Det er ikke egen visning, endrebehov-modalen åpnes i redigeringsmodus.
- Arbeidsgiver soft-slettes i dag. Behov er knyttet til arbeidsgivertabellen og trenger derfor ikke slettes ved soft delete, men skal heller ikke vises når arbeidsgiver er slettet.

## Flyway-migrasjon (V4)

```sql
CREATE TABLE arbeidsgiver_behov (
    behov_id         bigserial PRIMARY KEY,
    arbeidsgiver_id  bigint NOT NULL REFERENCES arbeidsgiver(arbeidsgiver_id),
    arbeidssprak    text[] NOT NULL DEFAULT '{}',
    antall          int,
    arbeidsoppgaver text[] NOT NULL DEFAULT '{}',
    ansettelsesform text
);

CREATE UNIQUE INDEX idx_arbeidsgiver_behov_arbeidsgiver ON arbeidsgiver_behov(arbeidsgiver_id);
```

Merk: Dette er en egen tabell for behov, knyttet til arbeidsgivertabellen. I API-et håndteres behov likevel sammen med arbeidsgiverressursen.
En av årsakene til delingen er at arbeidsgiver muligens senere skal knyttes til jobbsøker, uten at behovene er viktige. Og at behovene skal skjules for de som ikke trenger tilgang. Samtidig vurderer vi på sikt om behov er noe som skal trekkes ut og finnes utenfor rekrutteringstreff-api.

## DTO

```kotlin
data class ArbeidsgiverBehovDto(
  val arbeidsoppgaver: List<String>,
  val arbeidssprak: List<String>,  // Eller enum, se vurdering under Felter
  val antall: Int,
  val ansettelsesform: String,
)
```

Brukes i:

- Request-body for `PUT .../behov`
- Valgfritt felt `behov` i respons fra `GET .../arbeidsgiver?include=behov` (null hvis behov ikke er registrert)
- Alle feltene i `ArbeidsgiverBehovDto` valideres som obligatoriske ved lagring, og listefeltene (`arbeidsoppgaver`, `arbeidssprak`) må ha minst ett element.

## Plassering i backend

- Gjenbruk `ArbeidsgiverController`, `ArbeidsgiverService` og utvid `ArbeidsgiverRepository`, siden behov er en del av arbeidsgiverressursen og naturlig hører hjemme i eksisterende arbeidsgiverflyt.
- Ny handler `leggTilArbeidsgiverMedBehovHandler()` som oppretter arbeidsgiver + behov i én atomisk operasjon. **Alle arbeidsgivere krever behov** — det er ikke tillatt å lagre arbeidsgiver uten behov. Behov er obligatorisk og må sendes i request.
- Denne handler brukes både ved opprettelse av treff (via `LeggTilArbeidsgiverModal`), etter publisering (via `LeggTilArbeidsgiverModal`), og i batch-operasjoner hvis behov er inkludert.
- Endringen skal ikke påvirke eksisterende håndtering av `næringskoder`; de følger fortsatt arbeidsgiveropprettelsen og trenger ikke egne endepunkter eller egen UI i denne planen.
- Behov leses via eksisterende arbeidsgiver-endepunkt med `include=behov`, ikke via eget `GET .../behov`.
- `GET .../arbeidsgiver` uten `include=behov` følger dagens tilgang og respons.
- `GET .../arbeidsgiver?include=behov` krever eier eller utvikler. Andre roller får `403` når de eksplisitt ber om behov.
- `PUT .../behov` oppretter behov hvis det ikke finnes, eller oppdaterer eksisterende. Implementeres i `ArbeidsgiverController`, med samme autorisasjonsmønster som dagens eierbeskyttede arbeidsgiver-endepunkter: eiere og utvikler har tilgang.

## Backend

- [ ] Flyway-migrasjon V4 (SQL over)
- [ ] `ArbeidsgiverBehov`-modell og utvidelser i `ArbeidsgiverRepository`
- [ ] Ny handler `leggTilArbeidsgiverMedBehovHandler()`: POST `/api/rekrutteringstreff/{id}/arbeidsgiver` som tar både arbeidsgiver og behov i request-body. Lagrer begge atomisk i én transaksjon.
- [ ] Behov er obligatorisk input — handler avviser request hvis behovsfeltene mangler eller er ufullstendige.
- [ ] Utvid `GET /api/rekrutteringstreff/{id}/arbeidsgiver` med støtte for `include=behov`
- [ ] Nytt endepunkt: `PUT /api/rekrutteringstreff/{id}/arbeidsgiver/{arbeidsgiverId}/behov` (upsert — oppdaterer behov etter opprettelse)
- [ ] Tilgangskontroll: `leggTilArbeidsgiverMedBehovHandler` og `PUT .../behov` krever eier eller utvikler
- [ ] `BEHOV_ENDRET`-hendelse opprettes ved opprettelse og endring av behov (hendelse_data TBD)

## Frontend

- [ ] `LeggTilArbeidsgiverModal`: åpnes fra "Legg til arbeidsgiver"-knapp. Inneholder:
  - Søkefelt for arbeidsgiver (søk/velg fra kandidatsøk-API)
  - Behovsfeltene nedenfor (vises etter at arbeidsgiver er valgt)
  - Sender arbeidsgiver + behov sammen via én POST request som en atomisk operasjon
  - Samme design gjenbrukes både ved opprettelse og etter publisering
- [ ] Behovfeltene i modalen:
  - Arbeidsoppgaver: `Combobox` med Janzz yrkesontologi-typeahead, kun valg fra Janzz-forslag
  - Arbeidsspråk: `Combobox` eller Enum-select (**vurdering**: antall verdier og UX)
  - Antall: tallfelt
  - Ansettelsesform: `Select` med samme faste verdier som stillingens `engagementtype`
- [ ] Redigeringsknapp på arbeidsgiverkortet: åpner samme `LeggTilArbeidsgiverModal` for å redigere behov. Arbeidsgiverfeltet er disabled (søkefelt låst), kun behovsfeltene er redigerbare.
- [ ] Frontendvalidering i `LeggTilArbeidsgiverModal` (brukes både for opprettelse og redigering): listefeltene (`arbeidssprak`, `arbeidsoppgaver`) må ha minst ett element, `antall` må være positivt heltall, og `ansettelsesform` må være valgt. Vis feilmeldinger inline under hvert felt. Lagre-knappen er `disabled` inntil skjemaet er gyldig.
- [ ] Modal viser tydelig at behov er obligatorisk.
- [ ] Legg til redigeringsknapp på arbeidsgiverkortet. Knappen viser «Rediger behovsbeskrivelse». Åpner samme `LeggTilArbeidsgiverModal` med arbeidsgiverfeltet låst.
- [ ] Oppdater `useSjekklisteStatus`: arbeidsgiver-punktet krever minst én arbeidsgiver med behovsbeskrivelse (ikke bare at det finnes arbeidsgivere). Frontend henter arbeidsgivere med `include=behov` for eiere.
- [ ] Valgte `arbeidsoppgaver` kan fjernes med små kryss, etter samme mønster som andre multivalg i løsningen
- [ ] Skjul behovknappen og behov for brukere som ikke eier treffet.

### UX-vurderinger

**Arbeidsgiver og behov i samme operasjon — behov er obligatorisk.** Modal åpnes fra "Legg til arbeidsgiver"-knapp. Vedkommende søker opp arbeidsgiver i et felt. Modalen viser så behovsfeltene for den valgte arbeidsgiveren. Alt fylles ut, valideres og sendes som en kombinert request. Backend lagrer begge atomisk. Arbeidsgiveren lagres IKKE uten behov. Redigering av behov gjøres senere via redigeringsknapp på arbeidsgiverkortet, og bruker da separate endepunkt.

**Forholdet til stilling:** `OmVirksomheten` i stilling bruker en inline `Combobox` for å velge én arbeidsgiver direkte i skjemaet, uten modal og uten behovskonsept. Rekrutteringstreff har flere arbeidsgivere med behov koblet til hver. Det er ingen komponentkonflikt — kontekstene er helt ulike (`/stilling/` vs `/rekrutteringstreff/`), og `VelgArbeidsgiver`-komponentene er allerede separate implementasjoner.

**Konsistent design gjenbrukt.** Samme modal brukes både ved opprettelse av treff og etter publisering. Samme operasjon: søk arbeidsgiver → fylles behov → lagrer. Brukere gjenkjenner mønsteret uansett hvor de legger til arbeidsgivere.

## Spec/Forslag til tester

### Backend komponenttester

- [ ] POST arbeidsgiver med behov oppretter både arbeidsgiver og behov atomisk
- [ ] POST arbeidsgiver uten behov eller med ufullstendige behov gir valideringsfeil — **behov er obligatorisk**
- [ ] POST arbeidsgiver med tom `arbeidssprak` eller `arbeidsoppgaver` gir valideringsfeil
- [ ] `PUT .../behov` oppdaterer eksisterende behov
- [ ] `PUT .../behov` oppretter `BEHOV_ENDRET`-hendelse
- [ ] `GET .../arbeidsgiver?include=behov` returnerer lagrede verdier for eier
- [ ] `GET .../arbeidsgiver?include=behov` returnerer `behov: null` for arbeidsgiver uten registrerte behov (skulle ikke forekomme hvis behov er obligatorisk)
- [ ] Ikke-eier får `403` ved POST arbeidsgiver med behov
- [ ] Ikke-eier med arbeidsgiverrettet rolle får `403` på `GET .../arbeidsgiver?include=behov`
- [ ] Ikke-eier med arbeidsgiverrettet rolle får `403` på `PUT .../behov`
- [ ] Utvikler får tilgang til POST arbeidsgiver med behov og `PUT .../behov`
- [ ] Jobbsøkerrettet rolle får `403` på alle behov-operasjoner
- [ ] `GET .../arbeidsgiver` uten `include=behov` fungerer som i dag for roller som allerede har lesetilgang
- [ ] Soft-slettet arbeidsgiver eksponerer ikke behov i arbeidsgivervisningene

### Backend repositorietester

- [ ] `ArbeidsgiverRepository` lagrer og henter behov korrekt
- [ ] `ArbeidsgiverRepository` oppdaterer eksisterende behov korrekt
- [ ] Obligatoriske felt lagres uten `NULL`-verdier
- [ ] `arbeidsgiver_id` er unik i `arbeidsgiver_behov`

### Backend servicetester

- [ ] `ArbeidsgiverService` oppretter behov via upsert og oppretter `BEHOV_ENDRET`-hendelse
- [ ] `ArbeidsgiverService` oppdaterer eksisterende behov via upsert og oppretter `BEHOV_ENDRET`-hendelse
- [ ] `ArbeidsgiverService` avviser lagring når et obligatorisk felt i `behov` mangler
- [ ] `ArbeidsgiverService` avviser lagring når et listefelt i `behov` er tomt

### Frontend Playwright

- [ ] Eier kan legge til arbeidsgiver ved å søke i modal → velge arbeidsgiver → fylles behovsfeltene → sender arbeidsgiver + behov som én request
- [ ] Legg-til-modal åpner når «Legg til arbeidsgiver»-knapp klikkes
- [ ] Søkefelt finner arbeidsgiver fra kandidatsøk-API
- [ ] Etter at arbeidsgiver er valgt, vises behovsfeltene i samme modal
- [ ] Eier kan ikke lagre hvis behovsfeltene `arbeidsspråk` eller `arbeidsoppgaver` er tomme
- [ ] Eier kan ikke lagre hvis `antall` ikke er positivt heltall
- [ ] Eier kan ikke lagre hvis `ansettelsesform` ikke er valgt
- [ ] Arbeidsgiver + behov sendes sammen og lagres atomisk (arbeidsgiverkortet vises hvis lagring lyktes)
- [ ] Hvis lagring feiler, vises feilmeldinger og arbeidsgiver lagres IKKE uten behov
- [ ] Eier kan åpne `LeggTilArbeidsgiverModal` fra redigeringsknapp på arbeidsgiverkortet (arbeidsgivervalg er låst, bare behov redigerbar)
- [ ] Modalen preloader eksisterende behov-verdier ved redigering
- [ ] Redigering av behov bruker separat `PUT` endepunkt (arbeidsgiver er allerede lagret)
- [ ] Publiseringsknappen er `disabled` når ingen arbeidsgiver har behovsbeskrivelse
- [ ] Publiseringsknappen aktiveres når minst én arbeidsgiver har behovsbeskrivelse (og øvrige krav er oppfylt)
- [ ] `LeggTilArbeidsgiverModal` brukes både for opprettelse (arbeidsgivervalg aktivt) og redigering (arbeidsgivervalg låst)
- [ ] Ikke-eier ser arbeidsgiver uten behovknapp og uten behovsvisning
- [ ] Utvikler ser behov på samme måte som eier
- [ ] Ansettelsesform viser samme verdier som stillingsfeltet
- [ ] Arbeidsoppgaver kan legges til via Janzz-typeahead uten fritekst
- [ ] Arbeidsspråk-felt fungerer som enum eller tagliste etter design-vedtak
