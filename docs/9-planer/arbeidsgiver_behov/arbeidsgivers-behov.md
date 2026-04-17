# Forslag til plan for Arbeidsgivers behov

Når en markedskontakt legger til en arbeidsgiver i et rekrutteringstreff, skal de også kunne registrere arbeidsgiverens behov. Behovene er kun synlige for eiere av treffet og brukere med utviklerrollen.

## Felter

Designet har opprinnelig to separate felter for «yrkestittel» og «kompetanse / arbeidsoppgaver». Vi slår disse sammen til **ett kombinert felt** der brukeren kan velge yrkestitler, kompetanser, fagbrev, førerkort og offentlige godkjenninger om hverandre. Dette feltet drives av typeahead-tabellen beskrevet i [typeahead-behov.md](./typeahead-behov.md).

| Felt                  | Type                 | Obligatorisk | Beskrivelse                                                                                                                                                                                                                                                                                                                    |
| --------------------- | -------------------- | ------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| arbeidsoppgaver       | Tagliste (typeahead) | Ja           | Kombinert felt: yrkestittel, kompetanse, autorisasjon, godkjenning, fagdokumentasjon og førerkort. Drives av `GET /rest/typeahead/behov?kategorier=YRKESTITTEL,KOMPETANSE,AUTORISASJON,GODKJENNING,FAGDOKUMENTASJON,FORERKORT` i `pam-ontologi`. Kun valg fra typeahead-forslag, ingen fritekst. Hvert element har `kategori`. |
| arbeidssprak          | Tagliste             | Ja           | Språk som kreves i stillingen. Samme verdier som `workLanguage` på stilling. Behandles som tagliste i DTO.                                                                                                                                                                                                                     |
| antall                | Positivt heltall     | Ja           | Antall stillinger arbeidsgiver ønsker å fylle.                                                                                                                                                                                                                                                                                 |
| ansettelsesform       | Tagliste (nedtrekk)  | Nei          | Fast, Vikariat, Engasjement, Prosjekt, Sesong, osv. Samme verdier som `engagementtype` på stilling. Valgfritt iht. design.                                                                                                                                                                                                     |
| personlige_egenskaper | Tagliste (typeahead) | Nei          | Softskills. Drives av samme endepunkt som arbeidsoppgaver: `GET /rest/typeahead/behov?kategorier=SOFTSKILL`. Se [typeahead-behov.md](./typeahead-behov.md). Kun valg fra typeahead-forslag, ingen fritekst. Valgfritt iht. design.                                                                                             |

### Lagringsformat for taglistene

`arbeidsoppgaver` og `personlige_egenskaper` kommer fra typeahead-APIer som leverer `{label, kategori, konseptId?}`. For å unngå at samme label i to kategorier (f.eks. «Tysk» som både `SPRAAK`/`KOMPETANSE`, eller en softskill med samme navn som en kompetanse) blir flertydig, lagres disse som **JSONB-arrays** med hele strukturen, ikke som `text[]` med kun label.

`arbeidssprak` beholdes som `text[]` inntil videre (liten og velkjent verdimengde).

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
- Obligatoriske felt i `ArbeidsgiverBehovDto` (`arbeidsoppgaver`, `arbeidssprak`, `antall`) må være utfylt ved lagring. `ansettelsesform` og `personligeEgenskaper` er valgfrie. Ved lagring sendes alle verdier, også de som ikke er endret — ingen patching.
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
    behov_id               bigserial PRIMARY KEY,
    arbeidsgiver_id        bigint NOT NULL REFERENCES arbeidsgiver(arbeidsgiver_id),
    arbeidssprak           text[] NOT NULL DEFAULT '{}',
    antall                 int NOT NULL,
    arbeidsoppgaver        jsonb NOT NULL DEFAULT '[]'::jsonb,   -- array av {label, kategori, konseptId?}
    ansettelsesform        text,                                 -- nullable, valgfritt felt
    personlige_egenskaper  jsonb NOT NULL DEFAULT '[]'::jsonb    -- array av {label, kategori, konseptId?}
);

CREATE UNIQUE INDEX idx_arbeidsgiver_behov_arbeidsgiver ON arbeidsgiver_behov(arbeidsgiver_id);
```

Merk: Dette er en egen tabell for behov, knyttet til arbeidsgivertabellen. I API-et håndteres behov likevel sammen med arbeidsgiverressursen.
En av årsakene til delingen er at arbeidsgiver muligens senere skal knyttes til jobbsøker, uten at behovene er viktige. Og at behovene skal skjules for de som ikke trenger tilgang. Samtidig vurderer vi på sikt om behov er noe som skal trekkes ut og finnes utenfor rekrutteringstreff-api.

## DTO

```kotlin
data class BehovTagDto(
  val label: String,
  val kategori: String,     // f.eks. YRKESTITTEL, KOMPETANSE, FORERKORT, SOFTSKILL, …
  val konseptId: Long? = null
)

data class ArbeidsgiverBehovDto(
  val arbeidsoppgaver: List<BehovTagDto>,                       // min. 1
  val arbeidssprak: List<String>,                               // min. 1
  val antall: Int,                                              // > 0
  val ansettelsesform: String? = null,                          // valgfritt
  val personligeEgenskaper: List<BehovTagDto> = emptyList()     // valgfritt
)
```

Brukes i:

- Request-body for `PUT .../behov`
- Valgfritt felt `behov` i respons fra `GET .../arbeidsgiver?include=behov` (null hvis behov ikke er registrert)
- Obligatoriske felt valideres ved lagring. `arbeidsoppgaver` og `arbeidssprak` må ha minst ett element. `ansettelsesform` og `personligeEgenskaper` kan utelates eller være tom.

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
- [ ] `ArbeidsgiverBehov`-modell og utvidelser i `ArbeidsgiverRepository` (inkl. JSONB-mapping for `arbeidsoppgaver` og `personlige_egenskaper`)
- [ ] Ny handler `leggTilArbeidsgiverMedBehovHandler()`: POST `/api/rekrutteringstreff/{id}/arbeidsgiver` som tar både arbeidsgiver og behov i request-body. Lagrer begge atomisk i én transaksjon.
- [ ] Behov er obligatorisk input — handler avviser request hvis obligatoriske behovsfelt mangler eller er ufullstendige.
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
  - Arbeidsoppgaver (kombinert): `Combobox` med typeahead mot `GET /rest/typeahead/behov?kategorier=YRKESTITTEL,KOMPETANSE,AUTORISASJON,GODKJENNING,FAGDOKUMENTASJON,FORERKORT` i pam-ontologi. Viser både label og kategori i forslag. Lagrer `{label, kategori, konseptId?}`. Ingen fritekst.
  - Arbeidsspråk: `Combobox` eller enum-select (eksisterende språkliste)
  - Antall: tallfelt (positivt heltall)
  - Ansettelsesform: `Select` med samme faste verdier som stillingens `engagementtype`. **Valgfritt.**
  - Personlige egenskaper: `Combobox` mot `GET /rest/typeahead/behov?kategorier=SOFTSKILL` (samme endepunkt som arbeidsoppgaver, bare annet kategori-filter). **Valgfritt.** Ingen fritekst.
- [ ] Redigeringsknapp på arbeidsgiverkortet: åpner samme `LeggTilArbeidsgiverModal` for å redigere behov. Arbeidsgiverfeltet er disabled (søkefelt låst), kun behovsfeltene er redigerbare.
- [ ] Frontendvalidering i `LeggTilArbeidsgiverModal` (brukes både for opprettelse og redigering): `arbeidssprak` og `arbeidsoppgaver` må ha minst ett element, `antall` må være positivt heltall. `ansettelsesform` og `personlige_egenskaper` valideres ikke som obligatoriske. Vis feilmeldinger inline under hvert felt. Lagre-knappen er `disabled` inntil skjemaet er gyldig.
- [ ] Modal viser tydelig hvilke felt som er obligatoriske og hvilke som er valgfrie (i tråd med designet: «(Valgfritt)» på de to siste).
- [ ] Oppdater `useSjekklisteStatus`: arbeidsgiver-punktet krever minst én arbeidsgiver med behovsbeskrivelse (ikke bare at det finnes arbeidsgivere). Frontend henter arbeidsgivere med `include=behov` for eiere.
- [ ] Valgte tagger kan fjernes med små kryss, etter samme mønster som andre multivalg i løsningen.
- [ ] Skjul behovknappen og behov for brukere som ikke eier treffet.

### UX-vurderinger

**Arbeidsgiver og behov i samme operasjon — behov er obligatorisk.** Modal åpnes fra "Legg til arbeidsgiver"-knapp. Vedkommende søker opp arbeidsgiver i et felt. Modalen viser så behovsfeltene for den valgte arbeidsgiveren. Alt fylles ut, valideres og sendes som en kombinert request. Backend lagrer begge atomisk. Arbeidsgiveren lagres IKKE uten behov. Redigering av behov gjøres senere via redigeringsknapp på arbeidsgiverkortet, og bruker da separate endepunkt.

**Kombinert «hva arbeidsgiver leter etter»-felt.** Designet skisserer to separate felter for yrkestittel og kompetanse, men vi slår disse sammen til ett. Kombinasjonen yrkestittel + kompetanse + fagbrev + førerkort + godkjenning dekkes av én typeahead med kategoriangivelse per rad, slik at brukeren slipper å velge «riktig» felt for et begrep som kan tolkes på flere måter. Se [typeahead-behov.md](./typeahead-behov.md).

**Forholdet til stilling:** `OmVirksomheten` i stilling bruker en inline `Combobox` for å velge én arbeidsgiver direkte i skjemaet, uten modal og uten behovskonsept. Rekrutteringstreff har flere arbeidsgivere med behov koblet til hver. Det er ingen komponentkonflikt — kontekstene er helt ulike (`/stilling/` vs `/rekrutteringstreff/`), og `VelgArbeidsgiver`-komponentene er allerede separate implementasjoner.

**Konsistent design gjenbrukt.** Samme modal brukes både ved opprettelse av treff og etter publisering. Samme operasjon: søk arbeidsgiver → fylles behov → lagrer. Brukere gjenkjenner mønsteret uansett hvor de legger til arbeidsgivere.

## Spec/Forslag til tester

### Backend komponenttester

- [ ] POST arbeidsgiver med behov oppretter både arbeidsgiver og behov atomisk
- [ ] POST arbeidsgiver uten behov eller med ufullstendige obligatoriske felt gir valideringsfeil — **behov er obligatorisk**
- [ ] POST arbeidsgiver med tom `arbeidssprak` eller `arbeidsoppgaver` gir valideringsfeil
- [ ] POST arbeidsgiver uten `ansettelsesform` eller `personligeEgenskaper` lykkes (valgfrie felt)
- [ ] `PUT .../behov` oppdaterer eksisterende behov
- [ ] `PUT .../behov` oppretter `BEHOV_ENDRET`-hendelse
- [ ] `GET .../arbeidsgiver?include=behov` returnerer lagrede verdier for eier, inkl. JSONB-strukturen for `arbeidsoppgaver` og `personlige_egenskaper`
- [ ] `GET .../arbeidsgiver?include=behov` returnerer `behov: null` for arbeidsgiver uten registrerte behov (skulle ikke forekomme hvis behov er obligatorisk)
- [ ] Ikke-eier får `403` ved POST arbeidsgiver med behov
- [ ] Ikke-eier med arbeidsgiverrettet rolle får `403` på `GET .../arbeidsgiver?include=behov`
- [ ] Ikke-eier med arbeidsgiverrettet rolle får `403` på `PUT .../behov`
- [ ] Utvikler får tilgang til POST arbeidsgiver med behov og `PUT .../behov`
- [ ] Jobbsøkerrettet rolle får `403` på alle behov-operasjoner
- [ ] `GET .../arbeidsgiver` uten `include=behov` fungerer som i dag for roller som allerede har lesetilgang
- [ ] Soft-slettet arbeidsgiver eksponerer ikke behov i arbeidsgivervisningene

### Backend repositorietester

- [ ] `ArbeidsgiverRepository` lagrer og henter behov korrekt, inkl. JSONB-strukturen for `arbeidsoppgaver` og `personlige_egenskaper`
- [ ] `ArbeidsgiverRepository` oppdaterer eksisterende behov korrekt
- [ ] Obligatoriske felt lagres uten `NULL`-verdier
- [ ] `arbeidsgiver_id` er unik i `arbeidsgiver_behov`

### Backend servicetester

- [ ] `ArbeidsgiverService` oppretter behov via upsert og oppretter `BEHOV_ENDRET`-hendelse
- [ ] `ArbeidsgiverService` oppdaterer eksisterende behov via upsert og oppretter `BEHOV_ENDRET`-hendelse
- [ ] `ArbeidsgiverService` avviser lagring når et obligatorisk felt i `behov` mangler
- [ ] `ArbeidsgiverService` avviser lagring når et obligatorisk listefelt i `behov` er tomt
- [ ] `ArbeidsgiverService` godtar lagring uten `ansettelsesform` og `personligeEgenskaper`

### Frontend Playwright

- [ ] Eier kan legge til arbeidsgiver ved å søke i modal → velge arbeidsgiver → fylles behovsfeltene → sender arbeidsgiver + behov som én request
- [ ] Legg-til-modal åpner når «Legg til arbeidsgiver»-knapp klikkes
- [ ] Søkefelt finner arbeidsgiver fra kandidatsøk-API
- [ ] Etter at arbeidsgiver er valgt, vises behovsfeltene i samme modal
- [ ] Eier kan ikke lagre hvis behovsfeltene `arbeidsspråk` eller `arbeidsoppgaver` er tomme
- [ ] Eier kan ikke lagre hvis `antall` ikke er positivt heltall
- [ ] Eier kan lagre uten `ansettelsesform` (valgfritt)
- [ ] Eier kan lagre uten `personlige_egenskaper` (valgfritt)
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
- [ ] Arbeidsoppgaver kan legges til via kombinert typeahead uten fritekst, og blandede kategorier (f.eks. et yrke + et fagbrev + et førerkort) lagres og vises riktig
- [ ] Personlige egenskaper kan legges til via egen typeahead uten fritekst
- [ ] Arbeidsspråk-felt fungerer som tagliste
