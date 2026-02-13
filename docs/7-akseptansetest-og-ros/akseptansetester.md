# Manuelle akseptansetester

Testscenarier for domeneeksperter før pilot og prodsetting. Testene er organisert etter situasjoner slik brukerne opplever dem.

> **Målgruppe:** Domeneeksperter uten dyp teknisk bakgrunn. Testene er skrevet slik at du kan følge instruksjonene uten hjelp fra utviklere. Noen få tester er merket "Utvikler" - disse krever utviklertilgang og kan hoppes over av domeneeksperter.

## Testmiljø

| System                    | URL (dev)                              | Brukes av                | Også kalt        |
| ------------------------- | -------------------------------------- | ------------------------ | ---------------- |
| rekrutteringsbistand      | rekrutteringsbistand.intern.dev.nav.no | Veileder, Markedskontakt | -                |
| rekrutteringstreff-bruker | rekrutteringstreff.ekstern.dev.nav.no  | Jobbsøker                | "Treffsiden"     |
| Aktivitetsplan (veileder) | veilarbpersonflate.intern.dev.nav.no   | Veileder                 | -                |
| Aktivitetsplan (bruker)   | aktivitetsplan.ekstern.dev.nav.no      | Jobbsøker                | -                |
| MinSide                   | min-side.dev.nav.no                    | Jobbsøker                | "MinSide-varsel" |

> **Terminologi:**
>
> - **Treffsiden** = rekrutteringstreff-bruker der jobbsøker ser treffdetaljer og svarer
> - **MinSide-varsel** = Varsler på min-side.dev.nav.no for jobbsøkere uten KRR-kontaktinfo
> - **SMS/e-post** = Varsler som sendes med lenke til treffsiden (ikke selve endringene)

---

## 1. Opprette rekrutteringstreff

Markedskontakt oppretter et nytt rekrutteringstreff. Dette er første steg, og treffet er kun synlig for den som opprettet det.

**Hvor:** rekrutteringsbistand

**Hva skjer:** Treffet lagres i databasen. Ingen varsler eller aktivitetskort - det skjer først ved invitasjon.

### Grunnleggende opprettelse

| #     | Test                                                | Forventet resultat                             | ✅❌ | Notat |
| ----- | --------------------------------------------------- | ---------------------------------------------- | ---- | ----- |
| 1.1.1 | Markedskontakt - Opprett treff med påkrevde felter  | Treff opprettes, vises i oversikten over treff |      |       |
| 1.1.2 | Markedskontakt - Opprett treff, fyll ut alle felter | Alle felter lagres og vises korrekt            |      |       |
| 1.1.3 | Markedskontakt - Opprett treff med ugyldig data     | Valideringsfeil vises, treff opprettes ikke    |      |       |

### Autolagring (kladd-modus)

I kladd-modus lagres endringer automatisk. Felter som er lagret vises med avhukning i sidepanelet.

| #     | Test                                              | Forventet resultat                          | ✅❌ | Notat |
| ----- | ------------------------------------------------- | ------------------------------------------- | ---- | ----- |
| 1.2.1 | Markedskontakt - Skriv tittel i kladd             | Tittel lagres automatisk, hake i sidepanel  |      |       |
| 1.2.2 | Markedskontakt - Lukk og åpne treffet på nytt     | Tittel er bevart                            |      |       |
| 1.2.3 | Markedskontakt - Skriv innlegg i kladd            | Innlegg lagres automatisk, hake i sidepanel |      |       |
| 1.2.4 | Markedskontakt - Lukk og åpne treffet på nytt     | Innlegg er bevart                           |      |       |
| 1.2.5 | Markedskontakt - Endre flere felt, lukk nettleser | Alle felt er bevart ved neste innlogging    |      |       |
| 1.2.6 | Markedskontakt - Se sidepanel etter lagring       | Felter vises med avhukning i sidepanel      |      |       |

### Sletting av kladd (ROS 27486)

| #     | Test                               | Forventet resultat                         | ✅❌ | Notat |
| ----- | ---------------------------------- | ------------------------------------------ | ---- | ----- |
| 1.3.1 | Markedskontakt - Slett kladd-treff | Bekreftelsesdialog vises før sletting      |      |       |
| 1.3.2 | Markedskontakt - Bekreft sletting  | Treffet slettes, forsvinner fra oversikten |      |       |
| 1.3.3 | Markedskontakt - Avbryt sletting   | Treffet beholdes, ingen endring            |      |       |

### Adressefelt (ROS 27223)

| #     | Test                                    | Forventet resultat                 | ✅❌ | Notat |
| ----- | --------------------------------------- | ---------------------------------- | ---- | ----- |
| 1.4.1 | Markedskontakt - Skriv for lang adresse | Tegnbegrensning hindrer flere tegn |      |       |

---

## 2. Legge til arbeidsgiver

> **ROS:** 27482, 27222, 27483

Markedskontakt legger til arbeidsgivere på treffet. Dette kan gjøres både før publisering (i kladd-modus) og etter publisering (i egen fane).

**Hvor:** rekrutteringsbistand

**Hva skjer:** Arbeidsgiver kobles til treffet. Arbeidsgiverne blir synlige for jobbsøkere i rekrutteringstreff-bruker. Ingen varsler sendes til arbeidsgiver.

### I kladd-modus (før publisering)

| #     | Test                                          | Forventet resultat                     | ✅❌ | Notat |
| ----- | --------------------------------------------- | -------------------------------------- | ---- | ----- |
| 2.1.1 | Markedskontakt - Legg til arbeidsgiver        | Arbeidsgiver vises i listen på treffet |      |       |
| 2.1.2 | Markedskontakt - Legg til flere arbeidsgivere | Alle vises i listen                    |      |       |
| 2.1.3 | Markedskontakt - Fjern arbeidsgiver           | Arbeidsgiver fjernes fra listen        |      |       |

### Arbeidsgiversøk (pam-search)

Søket henter data fra Enhetsregisteret via pam-search API.

| #     | Test                                             | Forventet resultat                              | ✅❌ | Notat |
| ----- | ------------------------------------------------ | ----------------------------------------------- | ---- | ----- |
| 2.2.1 | Markedskontakt - Søk på firmanavn                | Søkeresultater vises med navn, orgnr og adresse |      |       |
| 2.2.2 | Markedskontakt - Søk på organisasjonsnummer      | Finn eksakt treff på orgnummer                  |      |       |
| 2.2.3 | Markedskontakt - Søk med delvis navn             | Får treff på delvise søkeord                    |      |       |
| 2.2.4 | Markedskontakt - Velg arbeidsgiver fra søkeliste | Orgnr, navn og adresse fylles ut                |      |       |
| 2.2.5 | Markedskontakt - Søk uten treff                  | Melding om ingen treff vises                    |      |       |

### Etter publisering

| #     | Test                                                  | Forventet resultat                              | ✅❌ | Notat |
| ----- | ----------------------------------------------------- | ----------------------------------------------- | ---- | ----- |
| 2.3.1 | Markedskontakt - Åpne "Arbeidsgivere"-fanen           | Ser liste over arbeidsgivere på treffet         |      |       |
| 2.3.2 | Markedskontakt - Legg til ny arbeidsgiver             | Arbeidsgiver legges til og vises i listen       |      |       |
| 2.3.3 | Markedskontakt - Fjern arbeidsgiver etter publisering | Arbeidsgiver fjernes fra listen                 |      |       |
| 2.3.4 | Jobbsøker - Sjekk arbeidsgiverliste                   | Ser oppdatert liste i rekrutteringstreff-bruker |      |       |

### Feilhåndtering (ROS 27483)

| #     | Test                                        | Forventet resultat                   | ✅❌ | Notat                                      |
| ----- | ------------------------------------------- | ------------------------------------ | ---- | ------------------------------------------ |
| 2.4.1 | Markedskontakt - Legg til ugyldig orgnummer | Ingen treff                          |      |                                            |
| 2.4.2 | Markedskontakt - Nettverksfeil ved oppslag  | Feilmelding vises, kan prøve på nytt |      | Utvikler: Testes via mocking av nettverket |

---

## 3. Publisere rekrutteringstreff

Markedskontakt publiserer treffet. Dette gjør at:

- Veiledere og andre markedskontakter kan se treffet
- Markedskontakt kan invitere jobbsøkere
- Jobbsøkere kan legges til av alle

**Hvor:** rekrutteringsbistand

**Hva skjer:** Treffet blir synlig for alle i rekrutteringsbistand. Fortsatt ingen varsler eller aktivitetskort - det skjer først ved invitasjon.

| #     | Test                            | Forventet resultat            | ✅❌ | Notat |
| ----- | ------------------------------- | ----------------------------- | ---- | ----- |
| 3.1.1 | Markedskontakt - Publiser treff | Status endres til "Publisert" |      |       |

> **Se også:** Tester for at veileder/markedskontakt finner og åpner publiserte treff er i [seksjon 12 – Søke etter rekrutteringstreff](#12-søke-etter-rekrutteringstreff).

---

## 4. Legge til jobbsøker

Både veileder og markedskontakt kan legge til jobbsøkere på et publisert treff. Systemet sjekker automatisk om jobbsøkeren er synlig (har CV, samtykke, ikke adressebeskyttelse etc.).

**Hvor:** rekrutteringsbistand (Jobbsøker-fanen på treffet)

**Hva skjer:** Jobbsøkeren legges til med status "Lagt til". En synlighetssjekk kjører i bakgrunnen via Kafka. Jobbsøkere som ikke oppfyller synlighetskravene vil forsvinne fra listen.

### Grunnleggende

| #     | Test                                        | Forventet resultat                                  | ✅❌ | Notat |
| ----- | ------------------------------------------- | --------------------------------------------------- | ---- | ----- |
| 4.1.1 | Veileder - Legg til synlig jobbsøker        | Jobbsøker vises i listen med status "Lagt til"      |      |       |
| 4.1.2 | Markedskontakt - Legg til synlig jobbsøker  | Jobbsøker vises i listen med status "Lagt til"      |      |       |
| 4.1.3 | Legg til flere jobbsøkere                   | Alle vises i listen                                 |      |       |
| 4.1.4 | Fjern jobbsøker fra treff                   | Jobbsøker fjernes fra listen                        |      |       |
| 4.1.5 | Legg til jobbsøker som tidligere er slettet | Jobbsøker legges til igjen, "Slettet"-teller synker |      |       |

### Synlighet

Synlighetsregler evalueres asynkront via toi-synlighetsmotor. Test disse ved å endre egenskaper på testperson i Dolly. **Alle** kriterier må være oppfylt for at jobbsøker skal være synlig.

> **Forutsetning:** Før synlighetstestene kan starte må testpersonen være **synlig** og allerede **lagt til på treffet**. Test deretter at endringer i Dolly propagerer til rekrutteringstreff.

> **Tips:** Synlighetsendringer kan ta ~1 minutt å propagere. For negative tester (fjerne synlighet), vent til personen forsvinner. For positive tester, vent til personen dukker opp igjen.

#### CV og jobbprofil

| #     | Test                                | Forventet resultat | ✅❌ | Notat |
| ----- | ----------------------------------- | ------------------ | ---- | ----- |
| 4.2.1 | Person med aktiv CV                 | Synlig ✅          |      |       |
| 4.2.2 | Person uten CV eller med slettet CV | Ikke synlig ❌     |      |       |

#### Arbeidssøkerregister (ny regel)

| #     | Test                                       | Forventet resultat | ✅❌ | Notat |
| ----- | ------------------------------------------ | ------------------ | ---- | ----- |
| 4.3.1 | Person registrert i arbeidssøkerregisteret | Synlig ✅          |      |       |
| 4.3.2 | Person IKKE i arbeidssøkerregisteret       | Ikke synlig ❌     |      |       |
| 4.3.3 | Person med avsluttet arbeidssøkerperiode   | Ikke synlig ❌     |      |       |

#### Oppfølging

| #     | Test                                    | Forventet resultat | ✅❌ | Notat |
| ----- | --------------------------------------- | ------------------ | ---- | ----- |
| 4.4.1 | Person under aktiv oppfølging           | Synlig ✅          |      |       |
| 4.4.2 | Person med avsluttet oppfølgingsperiode | Ikke synlig ❌     |      |       |

#### Adressebeskyttelse

PDL har 4 graderinger: UGRADERT, FORTROLIG (kode 7), STRENGT_FORTROLIG (kode 6), STRENGT_FORTROLIG_UTLAND (§19).

| #     | Test                                         | Forventet resultat         | ✅❌ | Notat |
| ----- | -------------------------------------------- | -------------------------- | ---- | ----- |
| 4.5.1 | Person med UGRADERT                          | Synlig ✅                  |      |       |
| 4.5.2 | Person med FORTROLIG (kode 7)                | Ikke synlig ❌             |      |       |
| 4.5.3 | Person med STRENGT_FORTROLIG (kode 6)        | Ikke synlig ❌             |      |       |
| 4.5.4 | Person med STRENGT_FORTROLIG_UTLAND (§19)    | Ikke synlig ❌             |      |       |
| 4.5.5 | Fjern adressebeskyttelse (sett til UGRADERT) | Person dukker opp igjen ✅ |      |       |

#### KVP (Kvalifiseringsprogram)

| #     | Test                           | Forventet resultat | ✅❌ | Notat |
| ----- | ------------------------------ | ------------------ | ---- | ----- |
| 4.6.1 | Person uten aktiv KVP          | Synlig ✅          |      |       |
| 4.6.2 | Person med aktiv KVP (startet) | Ikke synlig ❌     |      |       |

#### Endring av fødselsnummer

| #     | Test                | Forventet resultat                         | ✅❌ | Notat |
| ----- | ------------------- | ------------------------------------------ | ---- | ----- |
| 4.7.1 | Person får nytt fnr | Personen oppdateres korrekt, ikke duplikat |      |       |

#### Andre ekskluderingskriterier

| #     | Test                              | Forventet resultat | ✅❌ | Notat |
| ----- | --------------------------------- | ------------------ | ---- | ----- |
| 4.8.1 | Person som ikke er død            | Synlig ✅          |      |       |
| 4.8.2 | Person markert som død            | Ikke synlig ❌     |      |       |
| 4.8.3 | Person som ikke er sperret ansatt | Synlig ✅          |      |       |
| 4.8.4 | Person markert som sperret ansatt | Ikke synlig ❌     |      |       |

---

## 5. Invitere jobbsøker

> **ROS:** 27485, 28065

Markedskontakt inviterer jobbsøker til treffet. Dette trigger både varsling og opprettelse av aktivitetskort. Merk: Kun markedskontakt med arbeidsgiverrettet tilgang kan invitere - veileder kan kun legge til jobbsøkere.

**Hvor:**

- Markedskontakt: rekrutteringsbistand
- Jobbsøker: SMS/e-post → rekrutteringstreff-bruker, aktivitetsplan

**Hva skjer:**

1. Status endres til "Invitert" i rekrutteringsbistand
2. Varsel sendes til jobbsøker (SMS, e-post eller MinSide)
3. Aktivitetskort opprettes i jobbsøkers aktivitetsplan med status "Planlagt"
4. Jobbsøker kan åpne treffet via lenke i varsel eller aktivitetskort

### Grunnleggende invitasjon

| #     | Test                                             | Forventet resultat                                      | ✅❌ | Notat |
| ----- | ------------------------------------------------ | ------------------------------------------------------- | ---- | ----- |
| 5.1.1 | Markedskontakt - Inviter jobbsøker               | Status endres til "Invitert"                            |      |       |
| 5.1.2 | Markedskontakt - Sjekk varselstatus (~1 min)     | Varselstatus viser "Sendt"                              |      |       |
| 5.1.3 | Jobbsøker - Motta SMS                            | SMS med lenke til rekrutteringstreff-bruker             |      |       |
| 5.1.4 | Jobbsøker - Klikk lenke i SMS                    | Kommer til rekrutteringstreff-bruker, ser treffdetaljer |      |       |
| 5.1.5 | Jobbsøker - Sjekk aktivitetskort                 | Aktivitetskort finnes med status "Planlagt"             |      |       |
| 5.1.6 | Jobbsøker - Klikk lenke i aktivitetskort         | Kommer til rekrutteringstreff-bruker                    |      |       |
| 5.1.7 | Markedskontakt - Se aktivitetskort for jobbsøker | Ser samme kort med status "Planlagt"                    |      |       |
| 5.1.8 | Veileder - Prøv å invitere jobbsøker             | Inviter-knapp er IKKE synlig for veileder               |      |       |

### Varselkanaler

Hvilken kanal som brukes avhenger av jobbsøkers registrering i Kontakt- og reservasjonsregisteret (KRR).

| #     | Test                                     | Forventet resultat                           | ✅❌ | Notat |
| ----- | ---------------------------------------- | -------------------------------------------- | ---- | ----- |
| 5.2.1 | Inviter jobbsøker med mobilnr i KRR      | SMS sendes, varselstatus = "Sendt"           |      |       |
| 5.2.2 | Inviter jobbsøker med kun e-post i KRR   | E-post sendes, varselstatus = "Sendt"        |      |       |
| 5.2.3 | Inviter jobbsøker uten kontaktinfo i KRR | Varsel på MinSide, status = "Varsel MinSide" |      |       |

### MinSide-varsel for jobbsøkere uten KRR

Jobbsøkere uten registrert kontaktinfo i KRR får varsel på MinSide i stedet for SMS/e-post.

| #     | Test                                     | Forventet resultat                                 | ✅❌ | Notat |
| ----- | ---------------------------------------- | -------------------------------------------------- | ---- | ----- |
| 5.3.1 | Jobbsøker uten KRR - Logg inn på MinSide | Ser varsel om invitasjon til rekrutteringstreff    |      |       |
| 5.3.2 | Jobbsøker uten KRR - Klikk på varsel     | Kommer til rekrutteringstreff-bruker               |      |       |
| 5.3.3 | Jobbsøker uten KRR - Sjekk varselinnhold | Varselet inneholder tittel og lenke til treffsiden |      |       |

### Feilsituasjoner

| #     | Test                                   | Forventet resultat                       | ✅❌ | Notat |
| ----- | -------------------------------------- | ---------------------------------------- | ---- | ----- |
| 5.4.1 | Trykk to ganger på inviter-knapp       | Kun én invitasjon registreres            |      |       |
| 5.4.2 | Inviter jobbsøker som blir ikke-synlig | Jobbsøker forsvinner, varsel sendes ikke |      |       |

### Invitasjonsspråk og frivillighet (ROS 27485)

> **Juridisk avklaring påkrevd:** Tekstene i SMS, e-post og på treffsiden må godkjennes av jurist før pilot. Avklar at invitasjonsspråket og frivillighetsinformasjonen er tilstrekkelig for å ivareta krav til informasjonsplikt og frivillighet.

| #     | Test                                              | Forventet resultat                                                              | ✅❌ | Notat |
| ----- | ------------------------------------------------- | ------------------------------------------------------------------------------- | ---- | ----- |
| 5.5.1 | Jobbsøker - Sjekk SMS-tekst                       | SMS bruker invitasjonsspråk (ikke påbudsspråk) og lenker til treffsiden         |      |       |
| 5.5.2 | Jobbsøker - Sjekk e-post                          | E-post bruker invitasjonsspråk (ikke påbudsspråk) og lenker til treffsiden      |      |       |
| 5.5.3 | Jobbsøker - Sjekk frivillighetsinfo på treffsiden | Treffsiden viser tydelig at deltakelse er frivillig før jobbsøker svarer ja/nei |      |       |

---

## 6. Jobbsøker svarer på invitasjon

> **ROS:** 28065, 27487, 27485

Jobbsøker åpner treffet og svarer ja eller nei. Svaret synkroniseres tilbake til aktivitetsplanen og oppdaterer aktivitetskortet.

**Hvor:**

- Jobbsøker: rekrutteringstreff-bruker, aktivitetsplan
- Markedskontakt: rekrutteringsbistand (ser status i jobbsøkerlisten)

**Hva skjer:**

1. Jobbsøker ser treffdetaljer og svarknapper i rekrutteringstreff-bruker
2. Ved svar oppdateres status i rekrutteringsbistand
3. Aktivitetskort oppdateres: Ja → "Gjennomføres", Nei → "Avbrutt"
4. Jobbsøker kan endre svar før svarfrist

### Grunnleggende svar

| #     | Test                                           | Forventet resultat                                     | ✅❌ | Notat |
| ----- | ---------------------------------------------- | ------------------------------------------------------ | ---- | ----- |
| 6.1.1 | Jobbsøker - Svar "Ja"                          | Bekreftelse vises, svarknapper erstattes med status    |      |       |
| 6.1.2 | Markedskontakt - Sjekk status etter ja-svar    | Status viser "Påmeldt" / "Svart ja"                    |      |       |
| 6.1.3 | Jobbsøker - Sjekk aktivitetskort etter ja      | Status er "Gjennomføres"                               |      |       |
| 6.1.4 | Markedskontakt - Sjekk aktivitetskort etter ja | Ser samme status "Gjennomføres"                        |      |       |
| 6.1.5 | Jobbsøker - Svar "Nei"                         | Bekreftelse på avmelding vises                         |      |       |
| 6.1.6 | Markedskontakt - Sjekk status etter nei-svar   | Status viser "Avmeldt" / "Svart nei"                   |      |       |
| 6.1.7 | Jobbsøker - Sjekk aktivitetskort etter nei     | Status er "Avbrutt"                                    |      |       |
| 6.1.8 | Jobbsøker - Endre svar fra ja til nei          | Nytt svar registreres, aktivitetskort → "Avbrutt"      |      |       |
| 6.1.9 | Jobbsøker - Endre svar fra nei til ja          | Nytt svar registreres, aktivitetskort → "Gjennomføres" |      |       |

### Tilstander i rekrutteringstreff-bruker

Test at jobbsøker ser riktig informasjon basert på status.

| #     | Test                                | Forventet resultat                                       | ✅❌ | Notat |
| ----- | ----------------------------------- | -------------------------------------------------------- | ---- | ----- |
| 6.2.1 | Åpne invitasjon før svarfrist       | Ser svarknapper, svarfrist, treffinfo                    |      |       |
| 6.2.2 | Åpne etter svarfrist utløpt         | Ser "Svarfrist er utløpt", ingen svarknapper             |      |       |
| 6.2.3 | Åpne treff man ikke er invitert til | Ser info om begrenset plass, tips om å kontakte veileder |      |       |

### Feilsituasjoner

| #     | Test                                      | Forventet resultat       | ✅❌ | Notat |
| ----- | ----------------------------------------- | ------------------------ | ---- | ----- |
| 6.3.1 | Jobbsøker - Åpne ugyldig treff-ID         | Vennlig feilmelding      |      |       |
| 6.3.2 | Jobbsøker - Trykk to ganger på svar-knapp | Kun ett svar registreres |      |       |

---

## 7. Endre publisert treff

> **ROS:** 28065, 27482, 27383

Markedskontakt endrer et publisert treff som allerede har inviterte jobbsøkere. Ved lagring åpnes en dialog der markedskontakt velger om det skal sendes varsel, og hvilke felter som skal nevnes i varselet.

**Hvor:**

- Markedskontakt: rekrutteringsbistand
- Jobbsøker: SMS/e-post (hvis varsel sendes), treffsiden (rekrutteringstreff-bruker)

**Hva skjer:**

1. Markedskontakt gjør endringer og trykker "Lagre"
2. Dialog åpnes med valg: "Send varsel til inviterte?" (ja/nei)
3. Hvis ja: Switch-knapper for hvert endret felt (tidspunkt, sted, svarfrist, etc.)
4. Valgte felter nevnes i SMS/e-post-teksten til jobbsøker
5. Mottakere:
   - **Svart ja:** Får varsel
   - **Invitert (ikke svart):** Får IKKE varsel
   - **Svart nei:** Får IKKE varsel
6. **Treffsiden oppdateres umiddelbart** - jobbsøker ser alltid siste versjon når de åpner treffsiden

### Varseldialog og feltvalg

| #     | Test                                              | Forventet resultat                                | ✅❌ | Notat |
| ----- | ------------------------------------------------- | ------------------------------------------------- | ---- | ----- |
| 7.1.1 | Markedskontakt - Endre felt og lagre              | Dialog åpnes med spørsmål om varsel               |      |       |
| 7.1.2 | Markedskontakt - Velg "Ikke send varsel"          | Endring lagres, ingen varsel sendes               |      |       |
| 7.1.3 | Markedskontakt - Velg "Send varsel", alle felt på | Varsel sendes med alle endrede felt nevnt         |      |       |
| 7.1.4 | Markedskontakt - "Send varsel", kun tidspunkt på  | Varsel nevner kun tidspunkt, ikke andre endringer |      |       |
| 7.1.5 | Markedskontakt - Velg "Send varsel", kun sted på  | Varsel nevner kun sted                            |      |       |
| 7.1.6 | Markedskontakt - "Send varsel", ingen felt valgt  | Varsel sendes med generell melding om endring     |      |       |

### Mottakere og varselinnhold

| #     | Test                                            | Forventet resultat                       | ✅❌ | Notat |
| ----- | ----------------------------------------------- | ---------------------------------------- | ---- | ----- |
| 7.2.1 | Jobbsøker (invitert, ikke svart) - Sjekk varsel | Skal IKKE motta varsel                   |      |       |
| 7.2.2 | Jobbsøker (svart ja) - Motta endringsvarsel     | SMS/e-post med info om valgte felt       |      |       |
| 7.2.3 | Jobbsøker (svart nei) - Sjekk varsel            | Skal IKKE motta varsel                   |      |       |
| 7.2.4 | Jobbsøker - Sjekk SMS-tekst                     | Teksten inneholder de valgte feltnavnene |      |       |

### Oppdatering i treffsiden og aktivitetskort (ROS 27383)

Test at jobbsøker ser korrekt og oppdatert info på treffsiden (rekrutteringstreff-bruker) etter at markedskontakt har gjort endringer.

| #     | Test                                           | Forventet resultat                                           | ✅❌ | Notat |
| ----- | ---------------------------------------------- | ------------------------------------------------------------ | ---- | ----- |
| 7.3.1 | Jobbsøker - Åpne treffsiden etter endring      | Ser oppdaterte detaljer (tittel, tidspunkt, sted, svarfrist) |      |       |
| 7.3.2 | Jobbsøker - Sjekk aktivitetskort etter endring | Aktivitetskort har oppdaterte detaljer                       |      |       |
| 7.3.3 | Veileder - Sjekk aktivitetskort etter endring  | Ser oppdaterte detaljer                                      |      |       |

### Endring og synkronisering (ROS 28065)

Helhetlig test av endringsflyt: varseldialog, feltvalg, mottakere, MinSide og aktivitetskortsynkronisering.

| #     | Test                                                                                                         | Forventet resultat                                                                                                                                             | ✅❌ | Notat |
| ----- | ------------------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 7.4.1 | **Endre alle felt:** Endre tittel, tidspunkt, sted og svarfrist. Velg alle switch-knapper.                   | Varsel (SMS/e-post) nevner alle felt. Aktivitetskort oppdateres med ny tittel, tidspunkt, sted og svarfrist. Jobbsøker uten KRR-kontakt får varsel på MinSide. |      |       |
| 7.4.2 | **Endre ingen felt:** Gjør endring, men velg "Ikke send varsel" i dialogen.                                  | Endring lagres. Aktivitetskort oppdateres. Ingen varsel sendes (SMS, e-post, MinSide).                                                                         |      |       |
| 7.4.3 | **Endre noen felt:** Endre kun tidspunkt og sted. Velg kun disse i dialogen.                                 | Varsel nevner kun tidspunkt og sted. Aktivitetskort oppdateres med nye verdier. Tittel og svarfrist er uendret i aktivitetskort.                               |      |       |
| 7.4.4 | **Mottakere:** Endring med varsel. Sjekk at jobbsøker (svart ja) får varsel, invitert og svart nei får IKKE. | Svart ja: Mottar varsel. Invitert (ikke svart) og svart nei: Mottar IKKE varsel. Alle aktivitetskort oppdateres uavhengig av svar.                             |      |       |
| 7.4.5 | **MinSide-varsel:** Jobbsøker uten kontaktinfo i KRR. Endre felt og send varsel.                             | Jobbsøker ser varsel på MinSide med info om endring. Aktivitetskort oppdateres.                                                                                |      |       |

---

## 8. Avlyse treff

> **ROS:** 27487

Markedskontakt avlyser et treff. Kun jobbsøkere som har svart ja varsles.

**Hvor:**

- Markedskontakt: rekrutteringsbistand
- Jobbsøker: SMS/e-post, rekrutteringstreff-bruker, aktivitetsplan

**Hva skjer:**

1. Treffstatus endres til "Avlyst"
2. Varsel sendes KUN til de som svarte ja
3. Aktivitetskort for alle inviterte settes til "Avbrutt"
4. Jobbsøker ser avlysningsmelding i rekrutteringstreff-bruker

| #     | Test                                                      | Forventet resultat                               | ✅❌ | Notat |
| ----- | --------------------------------------------------------- | ------------------------------------------------ | ---- | ----- |
| 8.1.1 | Markedskontakt - Avlys treff                              | Status endres til "Avlyst"                       |      |       |
| 8.1.2 | Jobbsøker (svart ja) - Motta avlysningsvarsel             | SMS/e-post om at treffet er avlyst               |      |       |
| 8.1.3 | Jobbsøker (svart ja) - Sjekk aktivitetskort               | Status er "Avbrutt"                              |      |       |
| 8.1.4 | Jobbsøker (invitert) - Sjekk varsel                       | Skal IKKE motta varsel                           |      |       |
| 8.1.5 | Jobbsøker (invitert) - Sjekk aktivitetskort               | Status er "Avbrutt"                              |      |       |
| 8.1.6 | Jobbsøker (svart nei) - Sjekk varsel                      | Skal IKKE motta varsel                           |      |       |
| 8.1.7 | Jobbsøker (svart nei) - Sjekk aktivitetskort              | Status er "Avbrutt"                              |      |       |
| 8.1.8 | Jobbsøker - Åpne avlyst treff                             | Ser tydelig avlysningsmelding                    |      |       |
| 8.1.9 | Markedskontakt (eier) - Se jobbsøkerliste etter avlysning | Alle jobbsøkere vises fortsatt med sine statuser |      |       |

---

## 9. Treff gjennomføres og avsluttes

> **ROS:** 27487, 27484

Treffet passerer i tid. Aktivitetskort oppdateres automatisk basert på jobbsøkers svar.

**Hvor:**

- Jobbsøker: aktivitetsplan, rekrutteringstreff-bruker
- Veileder eller markedskontakt: aktivitetsplan (veiledervisning / nasjonal tilgang)

**Hva skjer:**

1. Når sluttidspunkt passerer, markeres treffet som gjennomført
2. Aktivitetskort for de som svarte ja → "Fullført"
3. Aktivitetskort for invitert/svart nei → "Avbrutt"
4. rekrutteringstreff-bruker viser "Treffet er over"

| #     | Test                                                    | Forventet resultat                      | ✅❌ | Notat |
| ----- | ------------------------------------------------------- | --------------------------------------- | ---- | ----- |
| 9.1.1 | Jobbsøker - Åpne treff som pågår                        | Ser "Treffet er i gang"                 |      |       |
| 9.1.2 | Jobbsøker - Åpne treff som er passert                   | Ser "Treffet er over"                   |      |       |
| 9.1.3 | Jobbsøker (svart ja) - Sjekk aktivitetskort             | Status er "Fullført"                    |      |       |
| 9.1.4 | Jobbsøker (invitert, ikke svart) - Sjekk aktivitetskort | Status er "Avbrutt"                     |      |       |
| 9.1.5 | Jobbsøker (svart nei) - Sjekk aktivitetskort            | Status er "Avbrutt"                     |      |       |
| 9.1.6 | Markedskontakt - Sjekk aktivitetskort (svart ja)        | Status er "Fullført"                    |      |       |
| 9.1.7 | Markedskontakt (eier) - Se jobbsøkerliste etter treff   | Alle jobbsøkere vises med sine statuser |      |       |

---

## 10. Innlegg på treff

Markedskontakt legger til et innlegg (introduksjonstekst) på treffet som jobbsøkere kan se. Det er kun étt innlegg per treff - å "legge til" betyr å redigere det ene innlegget.

**Hvor:**

- Markedskontakt: rekrutteringsbistand
- Jobbsøker: rekrutteringstreff-bruker

**Hva skjer:** Innlegget vises under "Siste aktivitet" i rekrutteringstreff-bruker. Ingen varsel sendes for innlegg.

| #      | Test                                                    | Forventet resultat                              | ✅❌ | Notat |
| ------ | ------------------------------------------------------- | ----------------------------------------------- | ---- | ----- |
| 10.1.1 | Markedskontakt - Legg til innlegg                       | Innlegg vises på treffet i rekrutteringsbistand |      |       |
| 10.1.2 | Jobbsøker - Se innlegg                                  | Innlegg vises under "Siste aktivitet"           |      |       |
| 10.1.3 | Markedskontakt - Rediger eksisterende innlegg           | Samme innlegg oppdateres, ikke nytt             |      |       |
| 10.1.4 | Markedskontakt - Sjekk at det ikke kan legges til flere | Ingen knapp for å legge til nytt innlegg        |      |       |
| 10.1.5 | Markedskontakt - Tøm innlegget                          | Innlegget fjernes fra visningen                 |      |       |

---

## 11. KI-tekstvalideringstjenesten

> **ROS:** 27216, 27219, 27321, 27547, 27546, 27867, 28415

Når markedskontakt skriver tittel eller innlegg, valideres teksten automatisk av KI for å sjekke om den er diskriminerende eller bryter retningslinjer. Med utviklertilgang kan man se KI-loggen.

**Hvor:**

- Markedskontakt: rekrutteringsbistand (ved skriving av tittel/innlegg)
- Utvikler: rekrutteringsbistand → KI-logg (krever utviklertilgang)

**Hva skjer:**

1. Teksten sendes til Azure OpenAI for validering
2. KI returnerer om teksten bryter retningslinjer + begrunnelse
3. Resultatet logges i databasen
4. Ved advarsel vises "Lagre likevel"-knapp som brukeren MÅ trykke for å fortsette
5. Backend validerer at "Lagre likevel" er aktivt valgt før diskriminerende tekst kan lagres
6. "lagret"-feltet i logg avhenger av modus:
   - **Før publisering (kladd):** Autolagring - men ved advarsel venter autolagring på brukervalg
   - **Etter publisering:** lagret=true kun når markedskontakt åpner endringsdialog og trykker "Lagre"

### KI-validering av tittel (ROS 27216)

Tittel valideres både ved autolagring (kladd) og ved endring etter publisering.

| #      | Test                                                          | Eksempeltekst                           | Forventet resultat                        | ✅❌ | Notat |
| ------ | ------------------------------------------------------------- | --------------------------------------- | ----------------------------------------- | ---- | ----- |
| 11.1.1 | Markedskontakt - Skriv nøytral tittel (kladd)                 | `Rekrutteringstreff for lagerarbeidere` | Ingen advarsel, tekst godkjennes          |      |       |
| 11.1.2 | Markedskontakt - Diskriminerende tittel (alder)               | `Kun for unge under 30 år`              | Advarsel vises, "Lagre likevel"-knapp     |      |       |
| 11.1.3 | Markedskontakt - Diskriminerende tittel (kjønn)               | `Søker kvinnelig deltaker`              | Advarsel vises, "Lagre likevel"-knapp     |      |       |
| 11.1.4 | Markedskontakt - Diskriminerende tittel (helse)               | `Må være frisk og ha god helse`         | Advarsel vises, "Lagre likevel"-knapp     |      |       |
| 11.1.5 | Markedskontakt - Diskriminerende tittel (etnisitet)           | `Norsk bakgrunn foretrukket`            | Advarsel vises, "Lagre likevel"-knapp     |      |       |
| 11.1.6 | Markedskontakt - Endre tittel (etter publisering)             | _(nøytral tekst)_                       | KI validerer ved "Lagre" i dialog         |      |       |
| 11.1.7 | Markedskontakt - Endre til diskriminerende tittel (publisert) | `Kun for unge under 30 år`              | Advarsel vises, kan ikke lagre uten knapp |      |       |

### KI-validering av innlegg (ROS 27216)

Innlegg valideres på samme måte som tittel.

| #      | Test                                                           | Eksempeltekst                                       | Forventet resultat                        | ✅❌ | Notat |
| ------ | -------------------------------------------------------------- | --------------------------------------------------- | ----------------------------------------- | ---- | ----- |
| 11.2.1 | Markedskontakt - Skriv nøytralt innlegg (kladd)                | `Vi inviterer til treff hos arbeidsgiver`           | Ingen advarsel, tekst godkjennes          |      |       |
| 11.2.2 | Markedskontakt - Diskriminerende innlegg (alder)               | `Vi ønsker primært yngre kandidater til stillingen` | Advarsel vises, "Lagre likevel"-knapp     |      |       |
| 11.2.3 | Markedskontakt - Diskriminerende innlegg (kjønn)               | `Stillingen passer best for menn`                   | Advarsel vises, "Lagre likevel"-knapp     |      |       |
| 11.2.4 | Markedskontakt - Diskriminerende innlegg (helse)               | `Krever god fysisk og psykisk helse`                | Advarsel vises, "Lagre likevel"-knapp     |      |       |
| 11.2.5 | Markedskontakt - Diskriminerende innlegg (etnisitet)           | `Foretrekker kandidater med norsk som morsmål`      | Advarsel vises, "Lagre likevel"-knapp     |      |       |
| 11.2.6 | Markedskontakt - Endre innlegg (etter publisering)             | _(nøytral tekst)_                                   | KI validerer ved "Lagre" i dialog         |      |       |
| 11.2.7 | Markedskontakt - Endre til diskriminerende innlegg (publisert) | `Vi ønsker primært yngre kandidater`                | Advarsel vises, kan ikke lagre uten knapp |      |       |

### "Lagre likevel"-funksjonalitet

Når KI gir advarsel, må bruker aktivt velge å lagre likevel.

| #      | Test                                                       | Forventet resultat                       | ✅❌ | Notat |
| ------ | ---------------------------------------------------------- | ---------------------------------------- | ---- | ----- |
| 11.3.1 | Markedskontakt - Advarsel vist, IKKE trykk "Lagre likevel" | Kan ikke publisere/lagre treffet         |      |       |
| 11.3.2 | Markedskontakt - Advarsel vist, trykk "Lagre likevel"      | Teksten lagres, kan fortsette            |      |       |
| 11.3.3 | Markedskontakt - Prøv å publisere uten "Lagre likevel"     | Publisering blokkert inntil valg er tatt |      |       |

### KI-logg (krever utviklertilgang)

> **Automatiske tester:** ROBs nøyaktighet måles av automatiske tester i `apps/rekrutteringstreff-api/src/test/kotlin/no/nav/toi/rekrutteringstreff/ki/KiTekstvalideringParameterisertTest.kt`. Nøyaktighet = (antall test-prompts - antall avvikende resultat) / antall test-prompts \* 100.

| #      | Test                                                         | Forventet resultat                                               | ✅❌ | Notat |
| ------ | ------------------------------------------------------------ | ---------------------------------------------------------------- | ---- | ----- |
| 11.4.1 | Utvikler - Åpne KI-logg                                      | Ser liste over alle KI-valideringer                              |      |       |
| 11.4.2 | Utvikler - Sjekk logg for kladd-treff                        | lagret=true for tekst som ble autolagret                         |      |       |
| 11.4.3 | Utvikler - Sjekk logg etter publisert endring                | lagret=true kun når bruker trykket "Lagre" i dialog              |      |       |
| 11.4.4 | Utvikler - Sjekk tekst som ble forkastet                     | lagret=false for tekst som ble endret før lagring                |      |       |
| 11.4.5 | Utvikler - Sjekk lagret-felt før publisering (autolagring)   | lagret=true settes når autolagring kjører i kladd-modus          |      |       |
| 11.4.6 | Utvikler - Sjekk lagret-felt etter publisering (bekreftelse) | lagret=true settes kun etter submit-knapp i bekreftelsesdialogen |      |       |
| 11.4.7 | Utvikler - Legg inn manuell vurdering                        | Kan registrere egen vurdering for kvalitetskontroll              |      |       |
| 11.4.8 | Utvikler - Filtrer på avvik                                  | Kan finne tilfeller der KI vurderte feil                         |      |       |

### UI-tekst og brukeransvar (ROS 27979, 27545, 27321)

Test at løsningen tydeliggjør at KI-sjekken kun er et verktøy og at brukeren har ansvar for innholdet.

| #      | Test                                                                         | Forventet resultat                                                   | ✅❌ | Notat |
| ------ | ---------------------------------------------------------------------------- | -------------------------------------------------------------------- | ---- | ----- |
| 11.5.1 | Markedskontakt - Sjekk info ved tittelfeltet                                 | Ser tekst om at KI-sjekken ikke garanterer korrekthet                |      |       |
| 11.5.2 | Markedskontakt - Sjekk info ved innleggsfeltet                               | Ser tekst om brukerens eget ansvar for innholdet                     |      |       |
| 11.5.3 | Markedskontakt - Sjekk at KI-sjekken IKKE viser grønn "ok"-tekst             | Ingen grønn bekreftelse - kun advarsler ved problemer                |      |       |
| 11.5.4 | Markedskontakt - Sjekk at advarsel viser om det gjelder tittel eller innlegg | Tydelig om advarselen gjelder tittel eller innlegg                   |      |       |
| 11.5.5 | Markedskontakt - Sjekk at det er tydelig hvilken tekst som sendes            | Bruker kan se hvilken tekst som blir analysert før den sendes til KI |      |       |

### Avvikshåndtering (ROS 27321)

Test at brukere kan rapportere feil og avvik i KI-sjekken.

| #      | Test                                             | Forventet resultat                                              | ✅❌ | Notat |
| ------ | ------------------------------------------------ | --------------------------------------------------------------- | ---- | ----- |
| 11.6.1 | Markedskontakt - Finn lenke til avvikshåndtering | Lenke til avviksskjema/rapportering er synlig ved KI-validering |      |       |
| 11.6.2 | Markedskontakt - Klikk på avvikslenke            | Kommer til riktig skjema for å rapportere KI-feil               |      |       |

### Robusthetstesting av KI (ROS 27546)

Test at KI-sjekken håndterer uvanlige tekster på en trygg måte.

> ⚠️ **Krever utviklerhjelp:** Noen av disse testene (11.7.1, 11.7.4) krever spesifikke testeksempler som utvikler gir deg. Dette er for å unngå at angrepsteknikker publiseres i offentlig dokumentasjon.

| #      | Test                                    | Eksempeltekst                                         | Forventet resultat                                 | Utviklerhjelp | ✅❌ | Notat |
| ------ | --------------------------------------- | ----------------------------------------------------- | -------------------------------------------------- | ------------- | ---- | ----- |
| 11.7.1 | Skriv tekst som prøver å "lure" KI      | _(Utvikler gir eksempeltekst)_                        | KI håndterer teksten trygt                         | Ja            |      |       |
| 11.7.2 | Skriv subtil diskriminerende tekst      | `Passer best for unge, friske personer med god helse` | KI gir advarsel om mulig aldersdiskriminering      | Nei           |      |       |
| 11.7.3 | Skriv tekst på et annet språk           | `We are looking for young, healthy workers only`      | KI gir advarsel eller håndterer det uten å krasje  | Nei           |      |       |
| 11.7.4 | Skriv tekst med spesialtegn og symboler | _(Utvikler gir eksempeltekst)_                        | Systemet krasjer ikke                              | Ja            |      |       |
| 11.7.5 | Skriv veldig lang tekst                 | (Lim inn en hel artikkel eller 1000+ tegn)            | Systemet håndterer lang tekst, ev. med feilmelding | Nei           |      |       |

> **Tips:** Hvis KI-sjekken IKKE gir advarsel på 11.7.2-11.7.3, noter dette som et avvik. Det betyr ikke at testen feilet - det betyr at vi har funnet en svakhet som bør undersøkes.

### Backend-validering og bypass-sikkerhet (ROS 27547, 27321, 27867)

> ⚠️ **Utviklertester:** Disse testene verifiserer at validering ikke kan omgås, og krever utviklertilgang og teknisk innsikt.

Test at tittel og innlegg ikke kan lagres med diskriminerende innhold uten at KI-sjekken har kjørt og "Lagre likevel" er aktivt valgt. Backend skal returnere feilkode hvis validering forsøkes omgått.

| #      | Test                                                                                                                         | Forventet resultat                                                                                             | Utviklerhjelp | ✅❌ | Notat |
| ------ | ---------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------- | ------------- | ---- | ----- |
| 11.8.1 | **Autolagring med diskriminerende tekst** - Skriv diskriminerende tittel mens autolagring kjører                             | Autolagring venter på KI-validering før lagring. Advarsel vises. Feltet lagres IKKE automatisk uten brukervalg | Ja            |      |       |
| 11.8.2 | **API-kall uten validering** - Send lagre-request direkte til backend uten å ha kjørt KI-sjekk                               | Backend returnerer feilkode (f.eks. 400/422). Feltet lagres IKKE                                               | Ja            |      |       |
| 11.8.3 | **API-kall med diskriminerende tekst uten "Lagre likevel"** - Send lagre-request med flaggAdvarsel=true men uten bekreftelse | Backend returnerer feilkode. Diskriminerende felt lagres IKKE                                                  | Ja            |      |       |
| 11.8.4 | **Verifiser at backend krever valideringsresultat** - Prøv å lagre tittel/innlegg uten tilhørende KI-valideringsresultat     | Backend avviser forespørselen. Logging viser forsøk på å omgå validering                                       | Ja            |      |       |
| 11.8.5 | **Race condition ved rask redigering** - Endre tekst raskt flere ganger mens KI-sjekk pågår                                  | Kun siste tekst lagres. Tidligere valideringer kanselleres eller ignoreres. Ingen inkonsistent tilstand        | Ja            |      |       |

> **Implementasjonsnotat:** Backend skal:
>
> 1. Returnere feilkode til frontend hvis diskriminerende tekst forsøkes lagret uten at "Lagre likevel" er trykket
> 2. Sikre at autolagring ikke lagrer diskriminerende felt uten brukerbekreftet overstyring
> 3. Logge forsøk på å omgå validering for sikkerhetsanalyse

### Persondata-filtrering (ROS 27219)

Test at tall med 4 siffer eller mer fjernes før tekst sendes til Azure OpenAI. Verifiseres i KI-logg ved å sammenligne "originalTekst" og "sendtTekst".

> **Merk:** Systemet gir **ikke** feilmelding til bruker - tallene fjernes automatisk. Testen verifiseres ved å sjekke KI-logg at tallene er borte fra "sendtTekst".

| #      | Test                                                | Eksempeltekst                     | Forventet i KI-logg                          | ✅❌ | Notat |
| ------ | --------------------------------------------------- | --------------------------------- | -------------------------------------------- | ---- | ----- |
| 11.9.1 | Skriv tekst med 4-sifret tall                       | `Ring meg på 1234 for mer info`   | "sendtTekst" inneholder ikke "1234"          |      |       |
| 11.9.2 | Skriv tekst med fødselsnummer (11 siffer)           | `Kandidat 12345678901 er aktuell` | "sendtTekst" inneholder ikke fødselsnummeret |      |       |
| 11.9.3 | Skriv tekst med telefonnummer (8 siffer)            | `Ta kontakt på 98765432`          | "sendtTekst" inneholder ikke telefonnummeret |      |       |
| 11.9.4 | Skriv tekst med D-nummer                            | `Jobbsøker med D-nr 41234567890`  | "sendtTekst" inneholder ikke D-nummeret      |      |       |
| 11.9.5 | Skriv tekst med kontonummer                         | `Utbetaling til 1234.56.78901`    | "sendtTekst" inneholder ikke kontonummeret   |      |       |
| 11.9.6 | Skriv tekst med organisasjonsnummer                 | `Arbeidsgiver org.nr 912345678`   | "sendtTekst" inneholder ikke org.nummeret    |      |       |
| 11.9.7 | Skriv tekst med 3-sifret tall (skal IKKE filtreres) | `Treffet varer i 120 minutter`    | "sendtTekst" inneholder fortsatt "120"       |      |       |
| 11.9.8 | Skriv tekst med e-postadresse                       | `Send til ola.nordmann@nav.no`    | "sendtTekst" inneholder ikke e-postadressen  |      |       |

> **Verifisering:** Åpne KI-logg (11.4.1), finn valideringen, og sammenlign feltene for å bekrefte at filtrering skjedde.

### KI-valideringsknapp (ROS 27545)

Test at tekst i KI-felt ikke sendes til OpenAI uten at arrangør aktivt trykker "Sjekk og lagre" / "Sjekk og bruk".

| #       | Test                                                                     | Forventet resultat                                               | ✅❌ | Notat |
| ------- | ------------------------------------------------------------------------ | ---------------------------------------------------------------- | ---- | ----- |
| 11.10.1 | Markedskontakt - Skriv tittel i kladd uten å trykke "Sjekk og lagre"     | Tekst sendes IKKE til OpenAI, feltet lagres IKKE                 |      |       |
| 11.10.2 | Markedskontakt - Skriv tittel og trykk "Sjekk og lagre"                  | Tekst valideres av KI og lagres ved godkjenning                  |      |       |
| 11.10.3 | Markedskontakt - Forlat KI-felt uten å trykke knappen                    | Påminnelse vises: "Teksten må sjekkes før du kan gå videre"      |      |       |
| 11.10.4 | Markedskontakt - Endre publisert treff, trykk "Sjekk og bruk" på innlegg | Tekst valideres av KI, endring kan brukes i bekreftelsesdialogen |      |       |

---

## 12. Søke etter rekrutteringstreff

Veiledere og markedskontakter kan finne publiserte rekrutteringstreff for å legge til sine jobbsøkere.

**Hvor:** rekrutteringsbistand (forsiden/oversikt)

**Hva skjer:** Publiserte treff vises i oversikten. Brukeren kan åpne treff for å se detaljer og eventuelt legge til jobbsøkere.

| #      | Test                                               | Forventet resultat                            | ✅❌ | Notat |
| ------ | -------------------------------------------------- | --------------------------------------------- | ---- | ----- |
| 12.1.1 | Veileder - Åpne rekrutteringstreff-oversikten      | Ser liste over publiserte treff               |      |       |
| 12.1.2 | Markedskontakt - Åpne oversikten                   | Ser publiserte treff + egne upubliserte treff |      |       |
| 12.1.3 | Veileder - Klikk på et treff                       | Åpner treffet i lesemodus                     |      |       |
| 12.1.4 | Markedskontakt (ikke eier) - Klikk på andres treff | Åpner treffet i lesemodus                     |      |       |

---

## 13. Finn jobbsøkere (kandidatsøk)

> **ROS:** 27227

Markedskontakt eller veileder kan søke etter kandidater i CV-databasen for å legge dem til på treffet.

**Hvor:** rekrutteringsbistand → Treff → Jobbsøkere-fanen → "Finn jobbsøkere"

**Hva skjer:** Åpner kandidatsøk med filter. Brukeren kan søke, filtrere og legge til kandidater på treffet.

| #      | Test                                            | Forventet resultat                                                       | ✅❌ | Notat |
| ------ | ----------------------------------------------- | ------------------------------------------------------------------------ | ---- | ----- |
| 13.1.1 | Markedskontakt (eier) - Klikk "Finn jobbsøkere" | Åpner kandidatsøk med filter                                             |      |       |
| 13.1.2 | Markedskontakt - Søk med kompetansefilter       | Kandidater som matcher vises                                             |      |       |
| 13.1.3 | Markedskontakt - Legg til kandidat fra søk      | Kandidat legges til på treffet                                           |      |       |
| 13.1.4 | Markedskontakt - Legg til flere kandidater      | Alle legges til på treffet                                               |      |       |
| 13.1.5 | Veileder (ikke eier) - Klikk "Finn jobbsøkere"  | Åpner kandidatsøk                                                        |      |       |
| 13.1.6 | Veileder - Sjekk kandidatsøk                    | Ser kun "Mine kandidater" og "Mitt kontor" – IKKE andre kontor/nasjonalt |      |       |
| 13.1.7 | Veileder - Legg til kandidat                    | Kandidat legges til på treffet                                           |      |       |

---

## 14. Hendelseslogg

Markedskontakt (eier) kan se en logg over alle hendelser som har skjedd på treffet.

**Hvor:** rekrutteringsbistand → Treff → Hendelser-fanen

**Hva skjer:** Viser kronologisk liste over alle hendelser: opprettelse, publisering, jobbsøkere lagt til/invitert, arbeidsgivere lagt til, etc.

> **Tips:** Test dette på et treff der du allerede har gjort mange andre tester, slik at det finnes data for alle hendelsestyper. Hvis noen hendelsestyper mangler, utfør de relevante handlingene først (legg til/fjern jobbsøker, inviter, endre treff, etc.).

| #      | Test                                         | Forventet resultat                            | ✅❌ | Notat |
| ------ | -------------------------------------------- | --------------------------------------------- | ---- | ----- |
| 14.1.1 | Markedskontakt (eier) - Åpne Hendelser-fanen | Ser liste over alle hendelser på treffet      |      |       |
| 14.1.2 | Sjekk at opprettelse vises                   | "Opprettet" med tidspunkt og utført av        |      |       |
| 14.1.3 | Sjekk at publisering vises                   | "Publisert" med tidspunkt                     |      |       |
| 14.1.4 | Sjekk at jobbsøker-hendelser vises           | "Lagt til", "Invitert", "Svart ja/nei" etc.   |      |       |
| 14.1.5 | Sjekk at arbeidsgiver-hendelser vises        | "Lagt til", "Fjernet" etc.                    |      |       |
| 14.1.6 | Sjekk at endringshendelser vises             | "Endret" med info om hva som ble endret       |      |       |
| 14.1.7 | Sjekk kronologisk rekkefølge                 | Nyeste hendelser øverst eller tydelig sortert |      |       |
| 14.1.8 | Veileder (ikke eier) - Prøv Hendelser-fanen  | Fanen er ikke tilgjengelig                    |      |       |

---

## 15. Tilgangsstyring og roller

> **ROS:** 27217, 27215, 27220, 27225

Løsningen har tre roller med ulike tilganger. Test at hver rolle kun kan gjøre det de skal.

**Roller:**

- **Jobbsøkerrettet (veileder)**: Kan se treff og legge til jobbsøkere, men IKKE invitere eller opprette treff
- **Arbeidsgiverrettet (markedskontakt)**: Kan opprette og administrere egne treff, invitere jobbsøkere
- **Utvikler**: Full tilgang til alt, inkludert KI-logg og uavhengig av pilotkontor

### Veileder (jobbsøkerrettet)

Veileder skal kunne se publiserte treff og legge til egne jobbsøkere, men IKKE se andre jobbsøkere, invitere eller redigere treffet.

| #      | Test                                     | Forventet resultat                                                       | ✅❌ | Notat |
| ------ | ---------------------------------------- | ------------------------------------------------------------------------ | ---- | ----- |
| 15.1.1 | Veileder - Åpne publisert treff          | Ser treffdetaljer i lesemodus                                            |      |       |
| 15.1.2 | Veileder - Prøv å redigere treffdetaljer | Ingen redigeringsknapper synlige                                         |      |       |
| 15.1.3 | Veileder - Se jobbsøkerlisten            | Ser IKKE andre veilederes jobbsøkere                                     |      |       |
| 15.1.4 | Veileder - Legg til jobbsøker            | Kan legge til jobbsøker på treffet                                       |      |       |
| 15.1.5 | Veileder - Se egen jobbsøker             | Ser jobbsøkeren man selv la til                                          |      |       |
| 15.1.6 | Veileder - Prøv å invitere jobbsøker     | Inviter-knapp er IKKE synlig for veileder                                |      |       |
| 15.1.7 | Veileder - Prøv å se Hendelser-fanen     | Fanen er ikke synlig/tilgjengelig                                        |      |       |
| 15.1.8 | Veileder - Prøv å opprette nytt treff    | Knapp for opprett treff ikke synlig                                      |      |       |
| 15.1.9 | Veileder - Sjekk synlige faner på treff  | Ser kun "Om treffet", "Jobbsøkere" og "Arbeidsgivere" – IKKE "Hendelser" |      |       |

### Markedskontakt (arbeidsgiverrettet) - ikke eier

Markedskontakt som ikke er eier av treffet kan legge til jobbsøkere og opprette egne treff, men ikke redigere andres treff.

| #      | Test                                                | Forventet resultat                | ✅❌ | Notat |
| ------ | --------------------------------------------------- | --------------------------------- | ---- | ----- |
| 15.2.1 | Markedskontakt - Åpne andres publiserte treff       | Ser treffdetaljer i lesemodus     |      |       |
| 15.2.2 | Markedskontakt - Prøv å redigere andres treff       | Ingen redigeringsknapper synlige  |      |       |
| 15.2.3 | Markedskontakt - Se jobbsøkerlisten på andres treff | Ser IKKE andres jobbsøkere        |      |       |
| 15.2.4 | Markedskontakt - Legg til jobbsøker på andres treff | Kan legge til jobbsøker           |      |       |
| 15.2.5 | Markedskontakt - Opprette eget treff                | Knapp synlig, kan opprette        |      |       |
| 15.2.6 | Markedskontakt - Invitere jobbsøker på andres treff | Kan invitere jobbsøker man la til |      |       |

### Markedskontakt (arbeidsgiverrettet) - eier

Markedskontakt som er eier har full tilgang til eget treff.

| #      | Test                                                   | Forventet resultat                   | ✅❌ | Notat |
| ------ | ------------------------------------------------------ | ------------------------------------ | ---- | ----- |
| 15.3.1 | Markedskontakt (eier) - Åpne eget treff                | Ser alle faner inkl. Hendelser       |      |       |
| 15.3.2 | Markedskontakt (eier) - Redigere treffdetaljer         | Kan redigere tittel, tid, sted, etc. |      |       |
| 15.3.3 | Markedskontakt (eier) - Se alle jobbsøkere             | Ser alle jobbsøkere på treffet       |      |       |
| 15.3.4 | Markedskontakt (eier) - Invitere alle jobbsøkere       | Kan invitere alle, ikke bare egne    |      |       |
| 15.3.5 | Markedskontakt (eier) - Publisere treff                | Publiser-knapp synlig og fungerer    |      |       |
| 15.3.6 | Markedskontakt (eier) - Avlyse treff                   | Avlys-knapp synlig og fungerer       |      |       |
| 15.3.7 | Markedskontakt (eier) - Legge til/fjerne arbeidsgivere | Kan administrere arbeidsgiverlisten  |      |       |

### Utvikler

Utviklere har full tilgang til alt, uavhengig av kontor og eierskap.

| #      | Test                                | Forventet resultat                  | ✅❌ | Notat |
| ------ | ----------------------------------- | ----------------------------------- | ---- | ----- |
| 15.4.1 | Utvikler - Åpne KI-logg             | Ser liste over alle KI-valideringer |      |       |
| 15.4.2 | Utvikler - Se alle treff            | Kan se alle treff inkludert kladder |      |       |
| 15.4.3 | Utvikler - Tilgang uten pilotkontor | Får tilgang uansett kontor          |      |       |
| 15.4.4 | Utvikler - Se andres hendelseslogg  | Kan se hendelser på alle treff      |      |       |

### Pilotkontor-tilgang

I pilotperioden må brukeren være innlogget på et pilotkontor for å få tilgang.

| #      | Test                                                         | Forventet resultat                                       | ✅❌ | Notat |
| ------ | ------------------------------------------------------------ | -------------------------------------------------------- | ---- | ----- |
| 15.5.1 | Veileder på pilotkontor - Åpne rekrutteringstreff            | Får tilgang til rekrutteringstreff                       |      |       |
| 15.5.2 | Veileder på ikke-pilotkontor - Åpne rekrutteringstreff       | Ser melding "Du har ikke tilgang til rekrutteringstreff" |      |       |
| 15.5.3 | Markedskontakt på pilotkontor - Åpne rekrutteringstreff      | Får tilgang til rekrutteringstreff                       |      |       |
| 15.5.4 | Markedskontakt på ikke-pilotkontor - Åpne rekrutteringstreff | Ser melding "Du har ikke tilgang til rekrutteringstreff" |      |       |
| 15.5.5 | Markedskontakt på pilotkontor - Opprette treff               | Kan opprette treff                                       |      |       |
| 15.5.6 | Markedskontakt på ikke-pilotkontor - Opprette treff          | Kan IKKE opprette treff                                  |      |       |
| 15.5.7 | Utvikler - Åpne uansett kontor                               | Får alltid tilgang                                       |      |       |

### Bytte kontor (modiacontextholder)

NAV-ansatte kan bytte aktivt kontor. Tilgangen oppdateres basert på valgt kontor.

| #      | Test                                                      | Forventet resultat                                | ✅❌ | Notat |
| ------ | --------------------------------------------------------- | ------------------------------------------------- | ---- | ----- |
| 15.6.1 | Veileder - Bytt fra pilotkontor til ikke-pilotkontor      | Mister tilgang, ser melding om manglende tilgang  |      |       |
| 15.6.2 | Veileder - Bytt fra ikke-pilotkontor til pilotkontor      | Får tilgang etter kontorbytte                     |      |       |
| 15.6.3 | Markedskontakt - Bytt kontor midt i arbeidsøkt            | Ny tilgang gjelder umiddelbart                    |      |       |
| 15.6.4 | Markedskontakt - Opprett treff, bytt til ikke-pilotkontor | Kan fortsatt se eget treff, men ikke opprette nye |      |       |

### Produksjonsmiljø-indikator (ROS 29337)

Test at det er tydelig når man jobber i produksjonsmiljø.

| #      | Test                                                  | Forventet resultat                                           | ✅❌ | Notat |
| ------ | ----------------------------------------------------- | ------------------------------------------------------------ | ---- | ----- |
| 15.7.1 | Utvikler - Åpne løsningen i prod                      | Ser tydelig banner/indikator om at man er i produksjonsmiljø |      |       |
| 15.7.2 | Utvikler - Åpne løsningen i dev                       | Ingen prod-banner, men ev. dev-indikator                     |      |       |
| 15.7.3 | Utvikler - Sjekk at prod-banner er synlig ved KI-logg | Banneret er synlig når man jobber med sensitive logger       |      |       |

### KI-logg og robusthetstesting (ROS 27546)

Verifiser at logger fra robusthetstesting er tilgjengelige for analyse.

| #      | Test                                         | Forventet resultat                                                | ✅❌ | Notat |
| ------ | -------------------------------------------- | ----------------------------------------------------------------- | ---- | ----- |
| 15.8.1 | Utvikler - Sjekk logg etter robusthetstester | Logger fra test 11.7.1-11.7.5 er synlige for analyse av svakheter |      |       |

### KI-infrastruktur (ROS 29025, 29023, 29263, 29330)

Verifiser at Azure OpenAI-konfigurasjonen følger kravene.

> ⚠️ **Produksjonsmiljø:** Disse testene må utføres i produksjonsmiljøet for å verifisere at konfigurasjonen er korrekt der.

| #      | Test                                              | Forventet resultat                              | ✅❌ | Notat |
| ------ | ------------------------------------------------- | ----------------------------------------------- | ---- | ----- |
| 15.9.1 | Utvikler - Verifiser deployment-type i Azure      | Deployment er "Standard" i EU/EØS (ikke Global) |      |       |
| 15.9.2 | Utvikler - Verifiser abuse monitoring er aktivert | Content filtering er aktivert med høyeste nivå  |      |       |
| 15.9.3 | Utvikler - Sjekk at logger slettes automatisk     | Logger eldre enn definert retensjon finnes ikke |      |       |
| 15.9.4 | Utvikler - Verifiser modellversjon                | Modellen er dokumentert og har ikke utgått      |      |       |

---

## Relaterte dokumenter

- ROS-tiltak for KI-sjekken - se _Tryggnok: ROS Rekrutteringstreff-KI_
- ROS-tiltak for Rekrutteringstreff - se _Tryggnok: ROS Rekrutteringstreff_
- [Tilgangsstyring](../3-sikkerhet/tilgangsstyring.md) - Teknisk dokumentasjon roller og tilgang
- [Synlighet](../3-sikkerhet/synlighet.md) - Teknisk dokumentasjon synlighetsfiltrering
- [Invitasjon](../4-integrasjoner/invitasjon.md) - Teknisk flyt for invitasjon
- [Varsling](../4-integrasjoner/varsling.md) - Varslingsmekanismer og maler
- [Aktivitetskort](../4-integrasjoner/aktivitetskort.md) - Aktivitetskort-synkronisering
- [MinSide-flyt](../4-integrasjoner/minside-flyt.md) - Jobbsøkerflyt og rekrutteringstreff-bruker
- [KI-tekstvalidering](../5-ki/ki-tekstvalidering.md) - Teknisk dokumentasjon KI-validering og logging
- [KI-rutiner for utviklere](../8-utviklerrutiner/ki-rutiner.md) - Utviklerrutiner for KI-sjekken
