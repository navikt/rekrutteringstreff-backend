# Manuelle akseptansetester

Testscenarier for domeneeksperter før pilot og prodsetting. Testene er organisert etter situasjoner slik brukerne opplever dem.

## Testmiljø

| System                    | URL (dev)                              | Brukes av    |
| ------------------------- | -------------------------------------- | ------------ |
| rekrutteringsbistand      | rekrutteringsbistand.intern.dev.nav.no | Veileder, MK |
| rekrutteringstreff-bruker | rekrutteringstreff.ekstern.dev.nav.no  | Jobbsøker    |
| Aktivitetsplan (veileder) | veilarbpersonflate.intern.dev.nav.no   | Veileder     |
| Aktivitetsplan (bruker)   | aktivitetsplan.ekstern.dev.nav.no      | Jobbsøker    |
| MinSide                   | min-side.dev.nav.no                    | Jobbsøker    |

> **MK** = Markedskontakt

---

## 1. Opprette rekrutteringstreff

Markedskontakt oppretter et nytt rekrutteringstreff. Dette er første steg, og treffet er kun synlig for den som opprettet det.

**Hvor:** rekrutteringsbistand

**Hva skjer:** Treffet lagres i databasen. Ingen varsler eller aktivitetskort - det skjer først ved invitasjon.

| #   | Test                                          | Forventet resultat                          |
| --- | --------------------------------------------- | ------------------------------------------- |
| 1.1 | MK - Opprett treff med påkrevde felter        | Treff opprettes, vises i "Mine treff"       |
| 1.2 | MK - Opprett treff, fyll ut alle felter       | Alle felter lagres og vises korrekt         |
| 1.3 | MK - Opprett treff med ugyldig data           | Valideringsfeil vises, treff opprettes ikke |
| 1.4 | MK - Sjekk at andre ikke ser upublisert treff | Treffet vises kun for oppretter             |

---

## 2. Legge til arbeidsgiver

Markedskontakt legger til arbeidsgivere på treffet. Dette kan gjøres både før publisering (i kladd-modus) og etter publisering (i egen fane).

**Hvor:** rekrutteringsbistand

**Hva skjer:** Arbeidsgiver kobles til treffet. Arbeidsgiverne blir synlige for jobbsøkere i rekrutteringstreff-bruker. Ingen varsler sendes til arbeidsgiver.

### I kladd-modus (før publisering)

| #   | Test                               | Forventet resultat                     |
| --- | ---------------------------------- | -------------------------------------- |
| 2.1 | MK - Legg til arbeidsgiver via søk | Arbeidsgiver vises i listen på treffet |
| 2.2 | MK - Legg til flere arbeidsgivere  | Alle vises i listen                    |
| 2.3 | MK - Fjern arbeidsgiver            | Arbeidsgiver fjernes fra listen        |

### Etter publisering

| #   | Test                                      | Forventet resultat                              |
| --- | ----------------------------------------- | ----------------------------------------------- |
| 2.4 | MK - Åpne "Arbeidsgivere"-fanen           | Ser liste over arbeidsgivere på treffet         |
| 2.5 | MK - Legg til ny arbeidsgiver             | Arbeidsgiver legges til og vises i listen       |
| 2.6 | MK - Fjern arbeidsgiver etter publisering | Arbeidsgiver fjernes fra listen                 |
| 2.7 | Jobbsøker - Sjekk arbeidsgiverliste       | Ser oppdatert liste i rekrutteringstreff-bruker |

---

## 3. Publisere rekrutteringstreff

Markedskontakt publiserer treffet slik at veiledere kan se det og legge til jobbsøkere.

**Hvor:** rekrutteringsbistand

**Hva skjer:** Treffet blir synlig for alle veiledere i rekrutteringsbistand. Fortsatt ingen varsler eller aktivitetskort.

| #   | Test                                 | Forventet resultat                     |
| --- | ------------------------------------ | -------------------------------------- |
| 3.1 | MK - Publiser treff                  | Status endres til "Publisert"          |
| 3.2 | Veileder - Søk etter publisert treff | Treffet dukker opp i søkeresultater    |
| 3.3 | Veileder - Åpne publisert treff      | Kan se treffdetaljer og jobbsøkerliste |

---

## 4. Legge til jobbsøker

Veileder legger til en jobbsøker på et publisert treff. Jobbsøkere kan kun legges til etter at treffet er publisert. Systemet sjekker automatisk om jobbsøkeren er synlig (har CV, samtykke, ikke adressebeskyttelse etc.).

**Hvor:** rekrutteringsbistand (Jobbsøker-fanen på treffet)

**Hva skjer:** Jobbsøkeren legges til med status "Lagt til". En synlighetssjekk kjører i bakgrunnen via Kafka. Jobbsøkere som ikke oppfyller synlighetskravene vil forsvinne fra listen.

| #   | Test                                 | Forventet resultat                             |
| --- | ------------------------------------ | ---------------------------------------------- |
| 4.1 | Veileder - Legg til synlig jobbsøker | Jobbsøker vises i listen med status "Lagt til" |
| 4.2 | Veileder - Legg til flere jobbsøkere | Alle vises i listen                            |
| 4.3 | Veileder - Fjern jobbsøker fra treff | Jobbsøker fjernes fra listen                   |
| 4.4 | MK - Legg til jobbsøker              | MK kan også legge til jobbsøkere               |

### Synlighet

Synlighetsregler evalueres asynkront. Test disse ved å endre egenskaper på testperson i Dolly.

| #   | Test                                              | Forventet resultat                             |
| --- | ------------------------------------------------- | ---------------------------------------------- |
| 4.4 | Legg adressebeskyttelse på person som er lagt til | Person forsvinner fra jobbsøkerlisten (~1 min) |
| 4.5 | Fjern adressebeskyttelse                          | Person dukker opp igjen i listen               |
| 4.6 | Marker person som død                             | Person forsvinner fra listen                   |
| 4.7 | Fjern dødmarkering                                | Person dukker opp igjen                        |
| 4.8 | Slett CV for person                               | Person blir ikke-synlig (kan ta lengre tid)    |

---

## 5. Invitere jobbsøker

Veileder inviterer jobbsøker til treffet. Dette trigger både varsling og opprettelse av aktivitetskort.

**Hvor:**

- Veileder: rekrutteringsbistand
- Jobbsøker: SMS/e-post → rekrutteringstreff-bruker, aktivitetsplan

**Hva skjer:**

1. Status endres til "Invitert" i rekrutteringsbistand
2. Varsel sendes til jobbsøker (SMS, e-post eller MinSide)
3. Aktivitetskort opprettes i jobbsøkers aktivitetsplan med status "Planlagt"
4. Jobbsøker kan åpne treffet via lenke i varsel eller aktivitetskort

| #   | Test                                       | Forventet resultat                                      |
| --- | ------------------------------------------ | ------------------------------------------------------- |
| 5.1 | Veileder - Inviter jobbsøker               | Status endres til "Invitert"                            |
| 5.2 | Veileder - Sjekk varselstatus (~1 min)     | Varselstatus viser "Sendt"                              |
| 5.3 | Jobbsøker - Motta SMS                      | SMS med lenke til rekrutteringstreff-bruker             |
| 5.4 | Jobbsøker - Klikk lenke i SMS              | Kommer til rekrutteringstreff-bruker, ser treffdetaljer |
| 5.5 | Jobbsøker - Sjekk aktivitetskort           | Aktivitetskort finnes med status "Planlagt"             |
| 5.6 | Jobbsøker - Klikk lenke i aktivitetskort   | Kommer til rekrutteringstreff-bruker                    |
| 5.7 | Veileder - Se aktivitetskort for jobbsøker | Ser samme kort med status "Planlagt"                    |

### Varselkanaler

Hvilken kanal som brukes avhenger av jobbsøkers registrering i Kontakt- og reservasjonsregisteret (KRR).

| #    | Test                                     | Forventet resultat                              |
| ---- | ---------------------------------------- | ----------------------------------------------- |
| 5.8  | Inviter jobbsøker med mobilnr i KRR      | SMS sendes, varselstatus = "Sendt"              |
| 5.9  | Inviter jobbsøker med kun e-post i KRR   | E-post sendes, varselstatus = "Sendt"           |
| 5.10 | Inviter jobbsøker uten kontaktinfo i KRR | Varsel på MinSide, status = "Lagret på MinSide" |

### Feilsituasjoner

| #    | Test                                   | Forventet resultat                       |
| ---- | -------------------------------------- | ---------------------------------------- |
| 5.11 | Dobbelt-klikk på inviter-knapp         | Kun én invitasjon registreres            |
| 5.12 | Inviter jobbsøker som blir ikke-synlig | Jobbsøker forsvinner, varsel sendes ikke |

---

## 6. Jobbsøker svarer på invitasjon

Jobbsøker åpner treffet og svarer ja eller nei. Svaret synkroniseres tilbake til veileder og oppdaterer aktivitetskortet.

**Hvor:**

- Jobbsøker: rekrutteringstreff-bruker, aktivitetsplan
- Veileder: rekrutteringsbistand, aktivitetsplan (veiledervisning)

**Hva skjer:**

1. Jobbsøker ser treffdetaljer og svarknapper i rekrutteringstreff-bruker
2. Ved svar oppdateres status i rekrutteringsbistand
3. Aktivitetskort oppdateres: Ja → "Gjennomføres", Nei → "Avbrutt"
4. Jobbsøker kan endre svar før svarfrist

| #   | Test                                       | Forventet resultat                                     |
| --- | ------------------------------------------ | ------------------------------------------------------ |
| 6.1 | Jobbsøker - Svar "Ja"                      | Bekreftelse vises, svarknapper erstattes med status    |
| 6.2 | Veileder - Sjekk status etter ja-svar      | Status viser "Påmeldt" / "Svart ja"                    |
| 6.3 | Jobbsøker - Sjekk aktivitetskort etter ja  | Status er "Gjennomføres"                               |
| 6.4 | Veileder - Sjekk aktivitetskort etter ja   | Ser samme status "Gjennomføres"                        |
| 6.5 | Jobbsøker - Svar "Nei"                     | Bekreftelse på avmelding vises                         |
| 6.6 | Veileder - Sjekk status etter nei-svar     | Status viser "Avmeldt" / "Svart nei"                   |
| 6.7 | Jobbsøker - Sjekk aktivitetskort etter nei | Status er "Avbrutt"                                    |
| 6.8 | Jobbsøker - Endre svar fra ja til nei      | Nytt svar registreres, aktivitetskort → "Avbrutt"      |
| 6.9 | Jobbsøker - Endre svar fra nei til ja      | Nytt svar registreres, aktivitetskort → "Gjennomføres" |

### Tilstander i rekrutteringstreff-bruker

Test at jobbsøker ser riktig informasjon basert på status.

| #    | Test                                | Forventet resultat                                       |
| ---- | ----------------------------------- | -------------------------------------------------------- |
| 6.10 | Åpne invitasjon før svarfrist       | Ser svarknapper, svarfrist, treffinfo                    |
| 6.11 | Åpne etter svarfrist utløpt         | Ser "Svarfrist er utløpt", ingen svarknapper             |
| 6.12 | Åpne treff man ikke er invitert til | Ser info om begrenset plass, tips om å kontakte veileder |

### Feilsituasjoner

| #    | Test                                    | Forventet resultat       |
| ---- | --------------------------------------- | ------------------------ |
| 6.13 | Jobbsøker - Åpne ugyldig treff-ID       | Vennlig feilmelding      |
| 6.14 | Jobbsøker - Dobbelt-klikk på svar-knapp | Kun ett svar registreres |

---

## 7. Endre publisert treff

Markedskontakt endrer et publisert treff som allerede har inviterte jobbsøkere. Ved lagring åpnes en dialog der MK velger om det skal sendes varsel, og hvilke felter som skal nevnes i varselet.

**Hvor:**

- MK: rekrutteringsbistand
- Jobbsøker: SMS/e-post (hvis varsel sendes), rekrutteringstreff-bruker

**Hva skjer:**

1. MK gjør endringer og trykker "Lagre"
2. Dialog åpnes med valg: "Send varsel til inviterte?" (ja/nei)
3. Hvis ja: Switch-knapper for hvert endret felt (tidspunkt, sted, svarfrist, etc.)
4. Valgte felter nevnes i SMS-teksten til jobbsøker
5. De som svarte nei får IKKE varsel

### Varseldialog og feltvalg

| #   | Test                                      | Forventet resultat                                |
| --- | ----------------------------------------- | ------------------------------------------------- |
| 7.1 | MK - Endre felt og lagre                  | Dialog åpnes med spørsmål om varsel               |
| 7.2 | MK - Velg "Ikke send varsel"              | Endring lagres, ingen varsel sendes               |
| 7.3 | MK - Velg "Send varsel", alle felt på     | Varsel sendes med alle endrede felt nevnt         |
| 7.4 | MK - Velg "Send varsel", kun tidspunkt på | Varsel nevner kun tidspunkt, ikke andre endringer |
| 7.5 | MK - Velg "Send varsel", kun sted på      | Varsel nevner kun sted                            |
| 7.6 | MK - Velg "Send varsel", ingen felt valgt | Varsel sendes med generell melding om endring     |

### Mottakere og varselinnhold

| #    | Test                                        | Forventet resultat                       |
| ---- | ------------------------------------------- | ---------------------------------------- |
| 7.7  | Jobbsøker (invitert) - Motta endringsvarsel | SMS/e-post med info om valgte felt       |
| 7.8  | Jobbsøker (svart ja) - Motta endringsvarsel | SMS/e-post med info om valgte felt       |
| 7.9  | Jobbsøker (svart nei) - Sjekk varsel        | Skal IKKE motta varsel                   |
| 7.10 | Jobbsøker - Sjekk SMS-tekst                 | Teksten inneholder de valgte feltnavnene |

### Oppdatering i systemer

| #    | Test                                           | Forventet resultat                                  |
| ---- | ---------------------------------------------- | --------------------------------------------------- |
| 7.11 | Jobbsøker - Åpne treff etter endring           | Ser oppdaterte detaljer i rekrutteringstreff-bruker |
| 7.12 | Jobbsøker - Sjekk aktivitetskort etter endring | Aktivitetskort har oppdaterte detaljer              |
| 7.13 | Veileder - Sjekk aktivitetskort etter endring  | Ser oppdaterte detaljer                             |

---

## 8. Avlyse treff

Markedskontakt avlyser et treff. Kun jobbsøkere som har svart ja varsles.

**Hvor:**

- MK: rekrutteringsbistand
- Jobbsøker: SMS/e-post, rekrutteringstreff-bruker, aktivitetsplan

**Hva skjer:**

1. Treffstatus endres til "Avlyst"
2. Varsel sendes KUN til de som svarte ja
3. Aktivitetskort for alle inviterte settes til "Avbrutt"
4. Jobbsøker ser avlysningsmelding i rekrutteringstreff-bruker

| #   | Test                                          | Forventet resultat                               |
| --- | --------------------------------------------- | ------------------------------------------------ |
| 8.1 | MK - Avlys treff                              | Status endres til "Avlyst"                       |
| 8.2 | Jobbsøker (svart ja) - Motta avlysningsvarsel | SMS/e-post om at treffet er avlyst               |
| 8.3 | Jobbsøker (svart ja) - Sjekk aktivitetskort   | Status er "Avbrutt"                              |
| 8.4 | Jobbsøker (invitert) - Sjekk varsel           | Skal IKKE motta varsel                           |
| 8.5 | Jobbsøker (invitert) - Sjekk aktivitetskort   | Status er "Avbrutt"                              |
| 8.6 | Jobbsøker (svart nei) - Sjekk varsel          | Skal IKKE motta varsel                           |
| 8.7 | Jobbsøker (svart nei) - Sjekk aktivitetskort  | Status er "Avbrutt"                              |
| 8.8 | Jobbsøker - Åpne avlyst treff                 | Ser tydelig avlysningsmelding                    |
| 8.9 | Veileder - Se jobbsøkerliste etter avlysning  | Alle jobbsøkere vises fortsatt med sine statuser |

---

## 9. Treff gjennomføres og avsluttes

Treffet passerer i tid. Aktivitetskort oppdateres automatisk basert på jobbsøkers svar.

**Hvor:**

- Jobbsøker: aktivitetsplan, rekrutteringstreff-bruker
- Veileder: aktivitetsplan (veiledervisning)

**Hva skjer:**

1. Når sluttidspunkt passerer, markeres treffet som gjennomført
2. Aktivitetskort for de som svarte ja → "Fullført"
3. Aktivitetskort for invitert/svart nei → "Avbrutt"
4. rekrutteringstreff-bruker viser "Treffet er over"

| #   | Test                                         | Forventet resultat                      |
| --- | -------------------------------------------- | --------------------------------------- |
| 9.1 | Jobbsøker - Åpne treff som pågår             | Ser "Treffet er i gang"                 |
| 9.2 | Jobbsøker - Åpne treff som er passert        | Ser "Treffet er over"                   |
| 9.3 | Jobbsøker (svart ja) - Sjekk aktivitetskort  | Status er "Fullført"                    |
| 9.4 | Jobbsøker (invitert) - Sjekk aktivitetskort  | Status er "Avbrutt"                     |
| 9.5 | Jobbsøker (svart nei) - Sjekk aktivitetskort | Status er "Avbrutt"                     |
| 9.6 | Veileder - Sjekk aktivitetskort (svart ja)   | Status er "Fullført"                    |
| 9.7 | Veileder - Se jobbsøkerliste etter treff     | Alle jobbsøkere vises med sine statuser |

---

## 10. Innlegg på treff

Markedskontakt legger til et innlegg (introduksjonstekst) på treffet som jobbsøkere kan se. Det er kun étt innlegg per treff - å "legge til" betyr å redigere det ene innlegget.

**Hvor:**

- MK: rekrutteringsbistand
- Jobbsøker: rekrutteringstreff-bruker

**Hva skjer:** Innlegget vises under "Siste aktivitet" i rekrutteringstreff-bruker. Ingen varsel sendes for innlegg.

| #    | Test                                        | Forventet resultat                              |
| ---- | ------------------------------------------- | ----------------------------------------------- |
| 10.1 | MK - Legg til innlegg                       | Innlegg vises på treffet i rekrutteringsbistand |
| 10.2 | Jobbsøker - Se innlegg                      | Innlegg vises under "Siste aktivitet"           |
| 10.3 | MK - Rediger eksisterende innlegg           | Samme innlegg oppdateres, ikke nytt             |
| 10.4 | MK - Sjekk at det ikke kan legges til flere | Ingen knapp for å legge til nytt innlegg        |
| 10.5 | MK - Tøm innlegget                          | Innlegget fjernes fra visningen                 |

---

## 11. KI-moderering og autolagring

Når markedskontakt skriver tittel eller introduksjon, valideres teksten automatisk av KI for å sjekke om den er diskriminerende eller bryter retningslinjer. Med utviklertilgang kan man se KI-loggen.

**Hvor:**

- MK: rekrutteringsbistand (ved skriving av tittel/introduksjon)
- Admin: rekrutteringsbistand → KI-logg (krever utviklertilgang)

**Hva skjer:**

1. Teksten sendes til Azure OpenAI for validering
2. KI returnerer om teksten bryter retningslinjer + begrunnelse
3. Resultatet logges i databasen
4. "lagret"-feltet i logg avhenger av modus:
   - **Før publisering (kladd):** Autolagring - lagret=true umiddelbart når felt endres
   - **Etter publisering:** lagret=true kun når MK åpner endringsdialog og trykker "Lagre"

### Autolagring (før publisering)

I kladd-modus lagres endringer automatisk uten at MK må trykke lagre.

| #    | Test                                  | Forventet resultat                       |
| ---- | ------------------------------------- | ---------------------------------------- |
| 11.1 | MK - Skriv tittel i kladd             | Tittel lagres automatisk                 |
| 11.2 | MK - Lukk og åpne treffet på nytt     | Tittel er bevart                         |
| 11.3 | MK - Skriv introduksjon i kladd       | Introduksjon lagres automatisk           |
| 11.4 | MK - Lukk og åpne treffet på nytt     | Introduksjon er bevart                   |
| 11.5 | MK - Endre flere felt, lukk nettleser | Alle felt er bevart ved neste innlogging |

### Validering av tekst

| #    | Test                                             | Forventet resultat                      |
| ---- | ------------------------------------------------ | --------------------------------------- |
| 11.6 | MK - Skriv nøytral tittel                        | Ingen advarsel, tekst godkjennes        |
| 11.7 | MK - Skriv diskriminerende tekst                 | Advarsel vises med begrunnelse fra KI   |
| 11.8 | MK - Skriv tekst med aldersgruppe (18-30)        | Godkjennes (satsningsområde for ungdom) |
| 11.9 | MK - Skriv tekst som ekskluderer basert på alder | Advarsel vises                          |

### KI-logg - lagret-felt (krever utviklertilgang)

| #     | Test                                       | Forventet resultat                                  |
| ----- | ------------------------------------------ | --------------------------------------------------- |
| 11.10 | Admin - Sjekk logg for kladd-treff         | lagret=true for tekst som ble autolagret            |
| 11.11 | Admin - Sjekk logg etter publisert endring | lagret=true kun når MK trykket "Lagre" i dialog     |
| 11.12 | Admin - Sjekk tekst som ble forkastet      | lagret=false for tekst som ble endret før lagring   |
| 11.13 | Admin - Åpne KI-logg                       | Ser liste over alle KI-valideringer                 |
| 11.14 | Admin - Legg inn manuell vurdering         | Kan registrere egen vurdering for kvalitetskontroll |
| 11.15 | Admin - Filtrer på avvik                   | Kan finne tilfeller der KI vurderte feil            |

### Validering av tekst

| #    | Test                                             | Forventet resultat                                     |
| ---- | ------------------------------------------------ | ------------------------------------------------------ |
| 11.1 | MK - Skriv nøytral tittel                        | Ingen advarsel, tekst godkjennes                       |
| 11.2 | MK - Skriv diskriminerende tekst                 | Advarsel vises med begrunnelse fra KI                  |
| 11.3 | MK - Skriv tekst med aldersgruppe (18-30)        | Godkjennes (satsningsområde for ungdom)                |
| 11.4 | MK - Skriv tekst som ekskluderer basert på alder | Advarsel vises                                         |
| 11.5 | MK - Lagre tekst som er godkjent av KI           | Teksten lagres, "lagret"-feltet i logg settes til true |
| 11.6 | MK - Ikke lagre etter KI-validering              | "lagret"-feltet i logg forblir false                   |

### KI-logg (krever utviklertilgang)

| #     | Test                               | Forventet resultat                                  |
| ----- | ---------------------------------- | --------------------------------------------------- |
| 11.7  | Admin - Åpne KI-logg               | Ser liste over alle KI-valideringer                 |
| 11.8  | Admin - Sjekk at "lagret" er true  | Treff som ble publisert har lagret=true             |
| 11.9  | Admin - Sjekk at "lagret" er false | Tekst som ble forkastet har lagret=false            |
| 11.10 | Admin - Legg inn manuell vurdering | Kan registrere egen vurdering for kvalitetskontroll |
| 11.11 | Admin - Filtrer på avvik           | Kan finne tilfeller der KI vurderte feil            |

---

## 12. Søke etter rekrutteringstreff

Veiledere og markedskontakter kan finne publiserte rekrutteringstreff for å legge til sine jobbsøkere.

**Hvor:** rekrutteringsbistand (forsiden/oversikt)

**Hva skjer:** Publiserte treff vises i oversikten. Brukeren kan åpne treff for å se detaljer og eventuelt legge til jobbsøkere.

| #    | Test                                          | Forventet resultat                            |
| ---- | --------------------------------------------- | --------------------------------------------- |
| 12.1 | Veileder - Åpne rekrutteringstreff-oversikten | Ser liste over publiserte treff               |
| 12.2 | MK - Åpne oversikten                          | Ser publiserte treff + egne upubliserte treff |
| 12.3 | Veileder - Klikk på et treff                  | Åpner treffet i lesemodus                     |
| 12.4 | MK (ikke eier) - Klikk på andres treff        | Åpner treffet i lesemodus                     |

---

## 13. Finn jobbsøkere (kandidatsøk)

Markedskontakt eller veileder kan søke etter kandidater i CV-databasen for å legge dem til på treffet.

**Hvor:** rekrutteringsbistand → Treff → Jobbsøkere-fanen → "Finn jobbsøkere"

**Hva skjer:** Åpner kandidatsøk med filter. Brukeren kan søke, filtrere og legge til kandidater på treffet.

| #    | Test                                           | Forventet resultat             |
| ---- | ---------------------------------------------- | ------------------------------ |
| 13.1 | MK (eier) - Klikk "Finn jobbsøkere"            | Åpner kandidatsøk med filter   |
| 13.2 | MK - Søk med kompetansefilter                  | Kandidater som matcher vises   |
| 13.3 | MK - Legg til kandidat fra søk                 | Kandidat legges til på treffet |
| 13.4 | MK - Legg til flere kandidater                 | Alle legges til på treffet     |
| 13.5 | Veileder (ikke eier) - Klikk "Finn jobbsøkere" | Åpner kandidatsøk              |
| 13.6 | Veileder - Legg til kandidat                   | Kandidat legges til på treffet |

---

## 14. Hendelseslogg

Markedskontakt (eier) kan se en logg over alle hendelser som har skjedd på treffet.

**Hvor:** rekrutteringsbistand → Treff → Hendelser-fanen

**Hva skjer:** Viser kronologisk liste over alle hendelser: opprettelse, publisering, jobbsøkere lagt til/invitert, arbeidsgivere lagt til, etc.

> **Tips:** Test dette på et treff der du allerede har gjort mange andre tester, slik at det finnes data for alle hendelsestyper. Hvis noen hendelsestyper mangler, utfør de relevante handlingene først (legg til/fjern jobbsøker, inviter, endre treff, etc.).

| #    | Test                                        | Forventet resultat                            |
| ---- | ------------------------------------------- | --------------------------------------------- |
| 14.1 | MK (eier) - Åpne Hendelser-fanen            | Ser liste over alle hendelser på treffet      |
| 14.2 | Sjekk at opprettelse vises                  | "Opprettet" med tidspunkt og utført av        |
| 14.3 | Sjekk at publisering vises                  | "Publisert" med tidspunkt                     |
| 14.4 | Sjekk at jobbsøker-hendelser vises          | "Lagt til", "Invitert", "Svart ja/nei" etc.   |
| 14.5 | Sjekk at arbeidsgiver-hendelser vises       | "Lagt til", "Fjernet" etc.                    |
| 14.6 | Sjekk at endringshendelser vises            | "Endret" med info om hva som ble endret       |
| 14.7 | Sjekk kronologisk rekkefølge                | Nyeste hendelser øverst eller tydelig sortert |
| 14.8 | Veileder (ikke eier) - Prøv Hendelser-fanen | Fanen er ikke tilgjengelig                    |

---

## 15. Tilgangsstyring og roller

Løsningen har tre roller med ulike tilganger. Test at hver rolle kun kan gjøre det de skal.

**Roller:**

- **Jobbsøkerrettet (veileder)**: Kan se treff og legge til jobbsøkere
- **Arbeidsgiverrettet (MK)**: Kan opprette og administrere egne treff
- **Utvikler**: Full tilgang til alt

### Veileder (jobbsøkerrettet)

Veileder skal kunne se publiserte treff og legge til egne jobbsøkere, men IKKE se andre jobbsøkere eller redigere treffet.

| #    | Test                                     | Forventet resultat                     |
| ---- | ---------------------------------------- | -------------------------------------- |
| 15.1 | Veileder - Åpne publisert treff          | Ser treffdetaljer i lesemodus          |
| 15.2 | Veileder - Prøv å redigere treffdetaljer | Ingen redigeringsknapper synlige       |
| 15.3 | Veileder - Se jobbsøkerlisten            | Ser IKKE andre veilederes jobbsøkere   |
| 15.4 | Veileder - Legg til jobbsøker            | Kan legge til jobbsøker på treffet     |
| 15.5 | Veileder - Se egen jobbsøker             | Ser jobbsøkeren man selv la til        |
| 15.6 | Veileder - Inviter jobbsøker             | Kan invitere jobbsøker man selv la til |
| 15.7 | Veileder - Prøv å se Hendelser-fanen     | Fanen er ikke synlig/tilgjengelig      |
| 15.8 | Veileder - Prøv å opprette nytt treff    | Knapp for opprett treff ikke synlig    |

### Markedskontakt (arbeidsgiverrettet) - ikke eier

MK som ikke er eier av treffet har samme rettigheter som veileder, men kan også opprette egne treff.

| #     | Test                                    | Forventet resultat               |
| ----- | --------------------------------------- | -------------------------------- |
| 15.9  | MK - Åpne andres publiserte treff       | Ser treffdetaljer i lesemodus    |
| 15.10 | MK - Prøv å redigere andres treff       | Ingen redigeringsknapper synlige |
| 15.11 | MK - Se jobbsøkerlisten på andres treff | Ser IKKE andres jobbsøkere       |
| 15.12 | MK - Legg til jobbsøker på andres treff | Kan legge til jobbsøker          |
| 15.13 | MK - Opprette eget treff                | Knapp synlig, kan opprette       |

### Markedskontakt (arbeidsgiverrettet) - eier

MK som er eier har full tilgang til eget treff.

| #     | Test                                       | Forventet resultat                   |
| ----- | ------------------------------------------ | ------------------------------------ |
| 15.14 | MK (eier) - Åpne eget treff                | Ser alle faner inkl. Hendelser       |
| 15.15 | MK (eier) - Redigere treffdetaljer         | Kan redigere tittel, tid, sted, etc. |
| 15.16 | MK (eier) - Se alle jobbsøkere             | Ser alle jobbsøkere på treffet       |
| 15.17 | MK (eier) - Invitere alle jobbsøkere       | Kan invitere alle, ikke bare egne    |
| 15.18 | MK (eier) - Publisere treff                | Publiser-knapp synlig og fungerer    |
| 15.19 | MK (eier) - Avlyse treff                   | Avlys-knapp synlig og fungerer       |
| 15.20 | MK (eier) - Legge til/fjerne arbeidsgivere | Kan administrere arbeidsgiverlisten  |

### Pilotkontor-tilgang

I pilotperioden må brukeren være innlogget på et pilotkontor for å få tilgang.

| #     | Test                                            | Forventet resultat                                       |
| ----- | ----------------------------------------------- | -------------------------------------------------------- |
| 15.21 | Bruker på pilotkontor - Åpne rekrutteringstreff | Får tilgang til rekrutteringstreff                       |
| 15.22 | Bruker på ikke-pilotkontor - Åpne               | Ser melding "Du har ikke tilgang til rekrutteringstreff" |
| 15.23 | Utvikler - Åpne uansett kontor                  | Får alltid tilgang                                       |

---

## Relaterte dokumenter

- [Tilgangsstyring](../3-sikkerhet/tilgangsstyring.md) - Teknisk dokumentasjon roller og tilgang
- [Synlighet](../3-sikkerhet/synlighet.md) - Teknisk dokumentasjon synlighetsfiltrering
- [Invitasjon](../4-integrasjoner/invitasjon.md) - Teknisk flyt for invitasjon
- [Varsling](../4-integrasjoner/varsling.md) - Varslingsmekanismer og maler
- [Aktivitetskort](../4-integrasjoner/aktivitetskort.md) - Aktivitetskort-synkronisering
- [MinSide-flyt](../4-integrasjoner/minside-flyt.md) - Jobbsøkerflyt og rekrutteringstreff-bruker
- [KI-moderering](../5-ki/ki-moderering.md) - Teknisk dokumentasjon KI-validering og logging
