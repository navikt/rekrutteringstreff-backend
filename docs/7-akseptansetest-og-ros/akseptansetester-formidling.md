# Manuelle akseptansetester – Etterregistrering / Formidling

Testscenarier for domeneeksperter for etterregistrering av formidlinger ("Fått jobben") fra et rekrutteringstreff. Testene er organisert etter situasjoner slik brukerne opplever dem.

> **Målgruppe:** Domeneeksperter uten dyp teknisk bakgrunn. Testene er skrevet slik at du kan følge instruksjonene uten hjelp fra utviklere. Noen få tester er merket "Utvikler" – disse krever utviklertilgang og kan hoppes over av domeneeksperter.

> **Status:** Funksjonalitet under utvikling. Enkelte tester gjelder oppførsel som ennå ikke er implementert; disse er merket med _(planlagt)_.

## Testmiljø

| System                 | URL (dev)                              | Brukes av                          | Også kalt                |
| ---------------------- | -------------------------------------- | ---------------------------------- | ------------------------ |
| rekrutteringsbistand   | rekrutteringsbistand.intern.dev.nav.no | Veileder, Markedskontakt, Utvikler | -                        |
| Stillingsregisteret    | (åpnes via lenke fra etterregistrering) | Markedskontakt, Veileder           | "Stilling"               |
| Aktivitetsplan         | veilarbpersonflate.intern.dev.nav.no   | Veileder                           | -                        |
| Datavarehus / DVH      | (intern)                               | Statistikk                         | "Statistikk"             |

> **Terminologi:**
>
> - **Etterregistrering** = registrering av at en jobbsøker fra treffet har fått jobb hos en arbeidsgiver fra treffet. Skjer i etterkant av selve treffet.
> - **Formidling** = den interne datamodellen for "Fått jobben"-innslaget. Lagres append-only i tabellen `formidling` (persontreffid + orgnr + orgnavn + timestamp + slettet).
> - **Stillingskategori** = type stilling i stillingsregisteret. Etterregistrering bruker (planlagt) en egen kategori `rekrutteringstreff_formidling`.
> - **Eier** = den markedskontakten som opprettet treffet.
> - **Egen jobbsøker** (for veileder) = jobbsøker hvor innlogget veileder er den faktiske Nav-veilederen for personen (ikke bare den som la personen til på treffet).

## Forutsetninger før testing

For å teste etterregistrering må følgende være på plass:

1. Et publisert rekrutteringstreff finnes
2. Treffet har minst én arbeidsgiver
3. Treffet har minst én jobbsøker (helst en blanding av synlige, skjulte og slettede)
4. Du er logget inn med riktig rolle (se rolletabell under)

---

## 1. Tilgang til "Opprett etterregistrering"-knappen

Knappen "Opprett etterregistrering" åpner modalen som styrer hele flyten. Hvem som ser knappen avhenger av rolle og forhold til treffet.

**Hvor:** rekrutteringsbistand → treffsiden → header / handlingsmeny

**Hva skjer:** Knappen vises kun for brukere som har lov til å etterregistrere minst én jobbsøker fra treffet.

### Rollematrise – tilgang til knappen

| #     | Rolle                                                         | Forventet resultat                                                                                                    | ✅❌ | Notat |
| ----- | ------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 1.1.1 | Utvikler                                                      | Knapp vises. Tilgang til alle jobbsøkere på treffet (også skjulte), unntatt slettede.                                 |      |       |
| 1.1.2 | Markedskontakt (arbeidsgiverrettet) – eier av treffet         | Knapp vises. Tilgang til alle jobbsøkere på treffet (også skjulte), unntatt slettede.                                 |      |       |
| 1.1.3 | Markedskontakt (arbeidsgiverrettet) – ikke eier               | Knapp vises ikke. _(Unntak: vises hvis personen samtidig er veileder for minst én jobbsøker på treffet.)_              |      |       |
| 1.1.4 | Veileder (jobbsøkerrettet) – ikke eier, har egne jobbsøkere   | Knapp vises. Tilgang kun til egne jobbsøkere (også skjulte), unntatt slettede.                                        |      |       |
| 1.1.5 | Veileder (jobbsøkerrettet) – ikke eier, ingen egne jobbsøkere | Knapp vises ikke.                                                                                                     |      |       |
| 1.1.6 | Veileder (jobbsøkerrettet) – eier _(todo, må avklares)_       | _Ikke avklart om dette skal være mulig. Test og noter observert oppførsel._                                           |      |       |

### Tilgjengelighet før jobbsøkere er lagt til / før publisering _(todo, må avklares)_

| #     | Test                                                              | Forventet resultat                                                  | ✅❌ | Notat |
| ----- | ----------------------------------------------------------------- | ------------------------------------------------------------------- | ---- | ----- |
| 1.2.1 | Markedskontakt (eier) – treff i kladd, ingen jobbsøkere           | _Avklar: skal knappen vises eller skjules?_                         |      |       |
| 1.2.2 | Markedskontakt (eier) – treff publisert, ingen jobbsøkere lagt til | _Avklar: skal knappen vises eller skjules?_                         |      |       |
| 1.2.3 | Markedskontakt (eier) – treff publisert, minst én jobbsøker       | Knapp vises.                                                        |      |       |
| 1.2.4 | Markedskontakt (eier) – treff fullført                            | Knapp vises (etterregistrering skjer i etterkant).                  |      |       |
| 1.2.5 | Markedskontakt (eier) – treff avlyst                              | _Avklar: skal etterregistrering være mulig på avlyst treff?_        |      |       |

---

## 2. Steg 1 – Velg arbeidsgiver

Modalen åpner på steg 1 av 4. Brukeren velger hvilken arbeidsgiver fra treffet jobbsøkeren skal formidles til.

**Hvor:** rekrutteringsbistand → modal "Opprett etterregistrering"

**Hva skjer:** Liste over treffets arbeidsgivere vises som radioknapper. Hvis treffet har nøyaktig én arbeidsgiver, er den forhåndsvalgt.

| #     | Test                                                          | Forventet resultat                                                                                  | ✅❌ | Notat |
| ----- | ------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | ---- | ----- |
| 2.1.1 | Åpne modal – treff med flere arbeidsgivere                    | Steghoder viser "Velg arbeidsgiver (1 av 4)". Alle arbeidsgivere listes med navn og orgnr. Ingen forhåndsvalgt. |      |       |
| 2.1.2 | Åpne modal – treff med én arbeidsgiver                        | Den ene arbeidsgiveren er forhåndsvalgt. "Neste" er aktiv.                                          |      |       |
| 2.1.3 | Åpne modal – treff uten arbeidsgivere                         | Info-boks: "Treffet har ingen arbeidsgivere. Legg til en arbeidsgiver i treffet før du oppretter etterregistrering." "Neste" er deaktivert. |      |       |
| 2.1.4 | Velg arbeidsgiver, klikk "Neste"                              | Går til steg 2. Valgt arbeidsgiver er bevart.                                                       |      |       |
| 2.1.5 | Klikk "Avbryt" på steg 1                                      | Modal lukkes. Ved ny åpning er valg nullstilt.                                                      |      |       |
| 2.1.6 | Klikk utenfor modal / Esc                                     | Modal lukkes uten å lagre.                                                                          |      |       |

---

## 3. Steg 2 – Fyll inn informasjon om formidlingen

Steg 2 viser et skjema for stillingsinformasjon (yrkestittel, sektor, ansettelsesform, arbeidstidsordning, omfang, sted, inkludering). Skjemaet har valideringskrav som må være oppfylt for å gå videre.

**Hvor:** rekrutteringsbistand → modal "Opprett etterregistrering" → steg 2

**Hva skjer:** Verdiene mellomlagres i minnet og blir prefylt på rediger-siden etter submit.

### Validering – obligatoriske felter

For å gå videre må alle disse være satt:

- Yrkestittel (minst én)
- Sektor
- Ansettelsesform
- Sted (minst ett)
- Omfang: enten "Heltid", eller "Deltid" med stillingsprosent

| #     | Test                                                                | Forventet resultat                                  | ✅❌ | Notat |
| ----- | ------------------------------------------------------------------- | --------------------------------------------------- | ---- | ----- |
| 3.1.1 | Tomt skjema                                                         | "Neste" er deaktivert.                              |      |       |
| 3.1.2 | Fyll inn alle obligatoriske felter                                  | "Neste" blir aktiv.                                 |      |       |
| 3.1.3 | Fjern yrkestittel etter at skjemaet var gyldig                      | "Neste" blir deaktivert igjen.                      |      |       |
| 3.1.4 | Velg "Deltid" uten stillingsprosent                                 | "Neste" forblir deaktivert.                         |      |       |
| 3.1.5 | Velg "Deltid" og fyll inn stillingsprosent                          | "Neste" blir aktiv.                                 |      |       |
| 3.1.6 | Velg "Heltid"                                                       | "Neste" blir aktiv (ingen stillingsprosent kreves). |      |       |

### Felter og innhold

| #     | Test                                              | Forventet resultat                                                 | ✅❌ | Notat |
| ----- | ------------------------------------------------- | ------------------------------------------------------------------ | ---- | ----- |
| 3.2.1 | Søk i yrkestittel (STYRK)                         | Får forslag fra STYRK-katalogen.                                   |      |       |
| 3.2.2 | Velg flere yrkestitler                            | Alle vises som tag i feltet.                                       |      |       |
| 3.2.3 | Velg sted via stedsøk                             | Sted vises som tag.                                                |      |       |
| 3.2.4 | Velg flere steder                                 | Alle vises.                                                        |      |       |
| 3.2.5 | Velg sektor (offentlig / privat)                  | Valg lagres.                                                       |      |       |
| 3.2.6 | Velg ansettelsesform                              | Valg lagres.                                                       |      |       |
| 3.2.7 | Velg arbeidstidsordning                           | Valg lagres.                                                       |      |       |
| 3.2.8 | Velg inkluderingstagger                           | Valg lagres.                                                       |      |       |

### Navigasjon mellom steg

| #     | Test                                              | Forventet resultat                                              | ✅❌ | Notat |
| ----- | ------------------------------------------------- | --------------------------------------------------------------- | ---- | ----- |
| 3.3.1 | Klikk "Tilbake" fra steg 2                        | Tilbake til steg 1, valgt arbeidsgiver bevart.                  |      |       |
| 3.3.2 | Gå tilbake til steg 1, endre arbeidsgiver         | Gå frem til steg 2 igjen – tidligere innfylte verdier er bevart. |      |       |
| 3.3.3 | Klikk "Neste" med gyldig skjema                   | Går til steg 3.                                                 |      |       |

---

## 4. Steg 3 – Velg jobbsøkere

Steg 3 viser jobbsøkerne fra treffet som brukeren har tilgang til (basert på rolle, se seksjon 1). Bruker velger én eller flere via avhuking.

**Hvor:** rekrutteringsbistand → modal "Opprett etterregistrering" → steg 3

**Hva skjer:** Listen hentes paginert (25 per side) fra backend. Søkefelt filtrerer på navn / fødselsnummer (debouncet, søker mot backend etter ~300 ms).

### Liste, paginering og søk

| #     | Test                                                       | Forventet resultat                                                              | ✅❌ | Notat |
| ----- | ---------------------------------------------------------- | ------------------------------------------------------------------------------- | ---- | ----- |
| 4.1.1 | Åpne steg 3 – treff med <25 jobbsøkere                     | Alle jobbsøkere vises på én side. Pagineringskontroll er skjult.                |      |       |
| 4.1.2 | Åpne steg 3 – treff med >25 jobbsøkere                     | Side 1 vises, pagineringskontroll synlig. Totalt antall vises i tellingen.     |      |       |
| 4.1.3 | Bla til side 2                                             | Neste 25 jobbsøkere vises. Tidligere valg på side 1 er bevart.                  |      |       |
| 4.1.4 | Søk på fornavn                                             | Listen filtreres etter ~300 ms. Side resettes til 1.                            |      |       |
| 4.1.5 | Søk på fødselsnummer                                       | Riktig jobbsøker matches.                                                       |      |       |
| 4.1.6 | Søk uten treff                                             | Tekst: "Ingen jobbsøkere matcher søket."                                        |      |       |
| 4.1.7 | Tøm søk                                                    | Hele listen vises igjen.                                                        |      |       |
| 4.1.8 | Treff uten jobbsøkere                                      | Tekst: "Treffet har ingen jobbsøkere å legge til."                              |      |       |

### Synlighet og filtrering på rolle

Etterregistrering skal også vise **skjulte** (ikke-synlige) jobbsøkere, men ikke **slettede**. Hva brukeren ser i listen avhenger av rolle.

| #     | Rolle / situasjon                                                                | Forventet resultat                                                                              | ✅❌ | Notat |
| ----- | -------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------- | ---- | ----- |
| 4.2.1 | Utvikler                                                                         | Ser alle jobbsøkere på treffet, inkludert skjulte. Slettede vises ikke.                         |      |       |
| 4.2.2 | Markedskontakt (eier)                                                            | Ser alle jobbsøkere på treffet, inkludert skjulte. Slettede vises ikke.                         |      |       |
| 4.2.3 | Markedskontakt (eier) – jobbsøker som har blitt skjult                           | Jobbsøkeren vises i listen og kan velges.                                                       |      |       |
| 4.2.4 | Markedskontakt (eier) – jobbsøker som er slettet fra treffet                     | Jobbsøkeren vises ikke.                                                                         |      |       |
| 4.2.5 | Veileder (ikke eier) med 2 av 10 jobbsøkere som egne                             | Ser kun sine 2 jobbsøkere, inkludert hvis disse er skjulte.                                      |      |       |
| 4.2.6 | Veileder (ikke eier) – egen jobbsøker er slettet fra treffet                     | Jobbsøkeren vises ikke.                                                                         |      |       |
| 4.2.7 | Markedskontakt (ikke eier) som også er veileder for 1 jobbsøker                  | Ser kun den ene jobbsøkeren der vedkommende er veileder.                                        |      |       |

### Valg av jobbsøkere

| #     | Test                                                       | Forventet resultat                                                       | ✅❌ | Notat |
| ----- | ---------------------------------------------------------- | ------------------------------------------------------------------------ | ---- | ----- |
| 4.3.1 | Huk av én jobbsøker                                        | Telleren viser "1 valgt av N". "Neste" blir aktiv.                       |      |       |
| 4.3.2 | Huk av flere jobbsøkere                                    | Telleren oppdateres. Alle valgte er bevart ved bla / søk.                |      |       |
| 4.3.3 | Fjern avhuking                                             | Telleren går ned. "Neste" blir deaktivert hvis 0 valgt.                  |      |       |
| 4.3.4 | Velg jobbsøkere på side 1, bla til side 2, velg flere     | Alle valg bevart på tvers av sider.                                      |      |       |
| 4.3.5 | Velg jobbsøker, søk etter en annen, velg den, tøm søk      | Begge er fortsatt valgt.                                                 |      |       |
| 4.3.6 | "Neste" med 0 valgte                                       | "Neste" er deaktivert.                                                   |      |       |
| 4.3.7 | "Neste" med ≥1 valgt                                       | Går til steg 4.                                                          |      |       |
| 4.3.8 | Klikk "Tilbake" fra steg 3                                 | Tilbake til steg 2, skjemaverdiene er bevart.                            |      |       |

---

## 5. Steg 4 – Oppsummering og opprett

Steg 4 viser oppsummering av valgt arbeidsgiver, valgte jobbsøkere og innfylte stillingsfelter. "Opprett" sender forespørsel til backend.

**Hvor:** rekrutteringsbistand → modal "Opprett etterregistrering" → steg 4

**Hva skjer ved "Opprett":**

1. Backend oppretter en stilling med kategori `Rekrutteringstreff` _(planlagt: bytte til `rekrutteringstreff_formidling`)_.
2. Stilling kobles til `rekrutteringstreffId`.
3. Stillingen får valgt arbeidsgiver, valgte kandidater og prefylte verdier.
4. Bruker forwardes til rediger-siden for stillingen (`/etterregistrering/{uuid}/rediger`).
5. _(Planlagt)_ Jobbsøker får status "Fått jobb" på treffet, og en rad legges til i tabellen `formidling` (persontreffid + orgnr + orgnavn + timestamp).

### Innhold i oppsummering

| #     | Test                                              | Forventet resultat                                                                  | ✅❌ | Notat |
| ----- | ------------------------------------------------- | ----------------------------------------------------------------------------------- | ---- | ----- |
| 5.1.1 | Åpne steg 4                                       | Viser arbeidsgiver (navn + orgnr), liste av valgte jobbsøkere, og stillingsfelter.  |      |       |
| 5.1.2 | Sjekk at alle valgte jobbsøkere er listet         | Antall og navn samsvarer med valg fra steg 3.                                       |      |       |
| 5.1.3 | Sjekk at stillingsfelt fra steg 2 vises korrekt   | Yrkestittel, sektor, ansettelsesform, sted, omfang, ev. inkludering vises.          |      |       |

### Justeringer i oppsummering

| #     | Test                                                       | Forventet resultat                                                              | ✅❌ | Notat |
| ----- | ---------------------------------------------------------- | ------------------------------------------------------------------------------- | ---- | ----- |
| 5.2.1 | Fjern én jobbsøker fra oppsummeringen                      | Jobbsøker forsvinner fra listen. Antall reduseres.                              |      |       |
| 5.2.2 | Fjern siste jobbsøker fra oppsummeringen                   | Modalen hopper tilbake til steg 3 (krever minst én jobbsøker).                  |      |       |
| 5.2.3 | Klikk "Tilbake" fra steg 4                                 | Tilbake til steg 3. Valg er bevart.                                             |      |       |
| 5.2.4 | Gå tilbake til steg 2 fra steg 4 og endre yrkestittel      | Endring reflekteres i oppsummeringen ved retur til steg 4.                      |      |       |

### Opprett – happy path

| #     | Test                                                       | Forventet resultat                                                                                          | ✅❌ | Notat |
| ----- | ---------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 5.3.1 | Klikk "Opprett" med 1 jobbsøker                            | Knappen viser loading. Bruker forwardes til `/etterregistrering/{uuid}/rediger`. Skjema er prefylt.        |      |       |
| 5.3.2 | Klikk "Opprett" med flere jobbsøkere                       | Alle jobbsøkere er prefylt som kandidater på rediger-siden.                                                 |      |       |
| 5.3.3 | Sjekk Umami-event _(utvikler)_                             | Event `Sidebar.opprettet_etterregistrering` registreres.                                                    |      |       |
| 5.3.4 | Trykk to ganger på "Opprett" raskt                         | Kun én etterregistrering opprettes. Knappen er deaktivert under loading.                                    |      |       |

### Opprett – feilsituasjoner

| #     | Test                                                                       | Forventet resultat                                                                          | ✅❌ | Notat |
| ----- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- | ---- | ----- |
| 5.4.1 | Backend returnerer feil ved opprettelse av stilling _(utvikler/mocking)_   | Feilmelding vises i modal og som toast. Modal forblir åpen. "Opprett" kan prøves igjen.    |      |       |
| 5.4.2 | Backend returnerer manglende uuid _(utvikler/mocking)_                     | Feilmelding "Manglende uuid ved opprettelse av etterregistrering" vises.                    |      |       |
| 5.4.3 | Pam-search finner ikke arbeidsgiver via orgnr _(utvikler/mocking)_         | Feilmelding "Fant ikke arbeidsgiver med orgnr ..." vises.                                   |      |       |
| 5.4.4 | Nettverksfeil _(utvikler/mocking)_                                         | Generisk feilmelding vises. Modal forblir åpen.                                             |      |       |
| 5.4.5 | "Avbryt" mens opprett pågår                                                | Avbryt er deaktivert mens opprett pågår.                                                    |      |       |

---

## 6. Effekter på treffet etter etterregistrering _(planlagt)_

Etter at etterregistreringen er opprettet og redigert ferdig på stillingssiden, skal følgende skje på treffet og i datavarehuset.

**Hvor:** rekrutteringsbistand → treffsiden → Jobbsøker-fanen

| #     | Test                                                                  | Forventet resultat                                                                                | ✅❌ | Notat |
| ----- | --------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | ---- | ----- |
| 6.1.1 | Etter opprettelse – sjekk jobbsøkers status i treffets jobbsøkerliste | Status endres til "Fått jobb".                                                                    |      |       |
| 6.1.2 | Hover på status-tag                                                   | Tooltip viser orgnr, orgnavn og timestamp for formidlingen.                                       |      |       |
| 6.1.3 | Filtrer jobbsøkerlisten på "Fått jobben"                              | Kun jobbsøkere med formidling vises.                                                              |      |       |
| 6.1.4 | Etterregistrer samme jobbsøker to ganger med ulike arbeidsgivere      | Begge formidlinger lagres (append-only). Siste vises som status.                                  |      |       |
| 6.1.5 | Sjekk databasen _(utvikler)_                                          | Rad i tabellen `formidling` med persontreffid, orgnr, orgnavn, timestamp (db now), slettet=null.  |      |       |
| 6.1.6 | Sjekk Kafka-event _(utvikler)_                                        | Hendelse publiseres. Stillingsinfo har riktig `rekrutteringstreffId`.                             |      |       |

---

## 7. Slett "Fått jobben" _(planlagt)_

Bruker skal kunne angre en formidling via burgermenyen på jobbsøkerraden.

**Hvor:** rekrutteringsbistand → treffsiden → Jobbsøker-fanen → burgermeny på rad

**Hva skjer:** Backend markerer raden i tabellen `formidling` som slettet (timestamp). Append-only — raden fjernes ikke fysisk. Status på jobbsøker går tilbake til forrige tilstand. _(Avklar med datavarehus om vi sender "presentert" eller annen status.)_

| #     | Test                                                              | Forventet resultat                                                                            | ✅❌ | Notat |
| ----- | ----------------------------------------------------------------- | --------------------------------------------------------------------------------------------- | ---- | ----- |
| 7.1.1 | Markedskontakt (eier) – åpne burgermeny på "Fått jobb"-jobbsøker | Ser valg "Slett Fått jobben".                                                                 |      |       |
| 7.1.2 | Klikk "Slett Fått jobben"                                         | Bekreftelsesdialog vises.                                                                     |      |       |
| 7.1.3 | Bekreft sletting                                                  | Status på jobbsøker går tilbake. Tooltipen viser ikke lenger formidlingsdata.                |      |       |
| 7.1.4 | Avbryt sletting                                                   | Ingen endring.                                                                                |      |       |
| 7.1.5 | Sjekk databasen _(utvikler)_                                      | Raden i `formidling` har `slettet`-timestamp satt. Raden er ikke fjernet.                     |      |       |
| 7.1.6 | Veileder – burgermeny på egen jobbsøker som har "Fått jobb"       | _Avklar: skal veileder kunne slette?_                                                         |      |       |
| 7.1.7 | Markedskontakt (ikke eier) – burgermeny                           | Sletteknapp vises ikke.                                                                       |      |       |

---

## 8. Etterregistrering fra etterregistreringsfanen _(planlagt)_

I tillegg til å starte etterregistrering fra treffsiden skal det gå an å starte fra en egen etterregistreringsfane / oversikt. Brukeren må først finne riktig treff.

**Hvor:** rekrutteringsbistand → etterregistreringsfanen

| #     | Test                                                                        | Forventet resultat                                                                          | ✅❌ | Notat |
| ----- | --------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- | ---- | ----- |
| 8.1.1 | Markedskontakt – åpne etterregistreringsfanen                               | Ser sine egne treff i nedtrekksliste / søk.                                                 |      |       |
| 8.1.2 | Veileder – åpne etterregistreringsfanen                                     | Ser treff knyttet til eget Nav-kontor _(avklar omfang)_.                                    |      |       |
| 8.1.3 | Velg treff                                                                  | Modal "Opprett etterregistrering" åpnes som beskrevet i seksjon 2–5.                        |      |       |
| 8.1.4 | Forward fra rekrutteringsbistand til riktig treff                           | Lenke / forward går direkte til treffsiden for valgt treff.                                 |      |       |

---

## 9. Stillingssøk og statistikk _(planlagt)_

Etterregistreringer skal være søkbare i rekrutteringsbistand stillingssøk og rapporterbare til datavarehus.

**Hvor:** rekrutteringsbistand → stillingssøk; statistikk / DVH

| #     | Test                                                                         | Forventet resultat                                                                          | ✅❌ | Notat |
| ----- | ---------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- | ---- | ----- |
| 9.1.1 | Stillingssøk – filtrer på rekrutteringstreff                                 | Nytt filter "Rekrutteringstreff" finnes. Treffer etterregistrerte formidlinger.             |      |       |
| 9.1.2 | Søk uten filter                                                              | Etterregistreringer vises sammen med øvrige stillinger.                                     |      |       |
| 9.1.3 | Statistikk _(utvikler / dataeier)_                                           | Avro-skjema oppdatert til å støtte stillingskategori `rekrutteringstreff_formidling`.       |      |       |
| 9.1.4 | Statistikk _(utvikler / dataeier)_                                           | `rekrutteringstreffId` følger med på stillingsinfo i datavarehuset.                         |      |       |
| 9.1.5 | Sletting reflekteres i DVH _(utvikler / dataeier)_                           | Slettet formidling sendes med riktig status (avklar: "presentert" eller annen).             |      |       |

---

## 10. Tilgjengelighet og generelt UX

| #      | Test                                                | Forventet resultat                                                              | ✅❌ | Notat |
| ------ | --------------------------------------------------- | ------------------------------------------------------------------------------- | ---- | ----- |
| 10.1.1 | Tab gjennom modal på alle 4 steg                    | Alle interaktive elementer er nåbare i logisk rekkefølge.                       |      |       |
| 10.1.2 | Esc lukker modal                                    | Modal lukkes (men ikke under loading).                                          |      |       |
| 10.1.3 | Skjermleser leser steghoder ("1 av 4" osv.)         | Steg leses opp tydelig.                                                         |      |       |
| 10.1.4 | Feilmeldinger har `role="alert"` / leses opp        | Feilmeldinger blir kunngjort.                                                   |      |       |
| 10.1.5 | Modal er responsiv                                  | Innhold er lesbart og brukbart på smal skjermbredde.                            |      |       |
