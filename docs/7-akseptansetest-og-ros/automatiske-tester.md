# Automatiske backend-tester

Dette dokumentet gir oversikt over teststatus og definerer oppgaver for manglende tester.

### ✅ Implementerte tester

| Område                                 | Testfil(er)                                                          | Dekning                                                                                        |
| -------------------------------------- | -------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| **Jobbsøker svar ja/nei**              | `JobbsøkerInnloggetBorgerTest.kt`                                    | ✅ `svar ja til invitasjon`, `svar nei til invitasjon`                                         |
| **Endre svar**                         | `JobbsøkerInnloggetBorgerTest.kt`                                    | ✅ `kan endre svar fra ja til nei`, `kan endre svar fra nei til ja`                            |
| **Avlysning med hendelser**            | `RekrutteringstreffTest.kt`                                          | ✅ `avlys oppretter hendelse for rekrutteringstreff og alle jobbsøkere med aktivt svar ja`     |
| **Avlysning uten svar ja**             | `RekrutteringstreffTest.kt`                                          | ✅ `avlys oppretter kun rekrutteringstreff-hendelse når ingen jobbsøkere har aktivt svar ja`   |
| **Fullføring**                         | `RekrutteringstreffTest.kt`                                          | ✅ `fullfor oppretter hendelse...` (flere varianter)                                           |
| **Endringsvarsel til inviterte**       | `RekrutteringstreffTest.kt`                                          | ✅ `registrer endring oppretter hendelser for publisert treff med inviterte jobbsøkere`        |
| **Endringsvarsel til svart ja**        | `RekrutteringstreffTest.kt`                                          | ✅ `registrer endring oppretter hendelser for publisert treff med jobbsøkere som har svart ja` |
| **Endringsvarsel IKKE til svart nei**  | `RekrutteringstreffTest.kt`                                          | ✅ `registrer endring varsler ikke jobbsøkere som har svart nei`                               |
| **Sletting av treff**                  | `RekrutteringstreffTest.kt`                                          | ✅ `slettRekrutteringstreffMedUpublisertedata`, `slett rekrutteringstreff feiler (409)...`     |
| **Svar-service logikk**                | `JobbsøkerServiceTest.kt`                                            | ✅ `svarJaTilInvitasjon...`, `svarNeiTilInvitasjon...`, `finnJobbsøkereMedAktivtSvarJa...`     |
| **Minside-varsel lytter**              | `MinsideVarselSvarLytterTest.kt`                                     | ✅ Omfattende                                                                                  |
| **KI tekstvalidering**                 | `KiTekstvalideringTest.kt`                                           | ✅ Mange testcases                                                                             |
| **Persondata-filtrering**              | `PersondataFilterTest.kt`                                            | ✅ Dekket                                                                                      |
| **Synlighet**                          | `SynlighetsKomponentTest.kt`, `SynlighetsLytterTest.kt` m.fl.        | ✅ Omfattende                                                                                  |
| **Autorisasjon**                       | `*AutorisasjonsTest.kt` (flere filer)                                | ✅ Omfattende                                                                                  |
| **Pilotkontor**                        | `PilotkontorTest.kt`                                                 | ✅ Dekket                                                                                      |
| **Duplikat-håndtering**                | `EierRepositoryTest.kt`, `AktivitetskortTest.kt`                     | ✅ `leggTil legger ikke til duplikater`, duplikat-meldinger                                    |
| **Dobbel invitasjon (race condition)** | `InvitasjonFeilhåndteringTest.kt`                                    | ✅ Idempotens implementert med radlås (SELECT FOR UPDATE)                                      |
| **Svarfrist-validering**               | `JobbsøkerInnloggetBorgerTest.kt`                                    | ✅ `svar ja etter svarfrist avvises`, `svar nei etter svarfrist avvises`                       |
| **Ugyldig treff-ID**                   | `JobbsøkerInnloggetBorgerTest.kt`                                    | ✅ GET/POST til ukjent treff-ID gir feilkode                                                   |
| **Dobbelt svar (idempotens)**          | `JobbsøkerInnloggetBorgerTest.kt`                                    | ✅ To svar-ja kall håndteres konsistent, samtidige kall                                        |
| **Veileder kan ikke invitere**         | `JobbsokerControllerAutorisasjonsTest.kt`                            | ✅ AT 5.1.8 - Jobbsøkerrettet får HTTP_FORBIDDEN på inviter-endepunkt                          |
| **Jobbsøker-synlighet per rolle**      | `JobbsokerControllerAutorisasjonsTest.kt`                            | ✅ AT 15.1.3, 15.2.3 - Kun eier/utvikler kan hente jobbsøkerliste                              |
| **Slett jobbsøker**                    | `JobbsokerControllerAutorisasjonsTest.kt`, `JobbsøkerServiceTest.kt` | ✅ AT 4.1.4 - markerSlettet med autorisasjon                                                   |
| **Re-aktivering av slettet jobbsøker** | `JobbsøkerServiceTest.kt`                                            | ✅ AT 4.1.5 - Slettet jobbsøker kan legges til igjen                                           |
| **Avlysning: kun svart ja varsles**    | `RekrutteringstreffTest.kt`                                          | ✅ AT 8.1.4-8.1.7 - Kun SVART_JA får SVART_JA_TREFF_AVLYST hendelse                            |
| **Opprett treff validering**           | `RekrutteringstreffTest.kt`                                          | ✅ AT 1.1.3 - Ugyldig data/JSON gir 400 feilkode                                               |
| **Jobbsøker uten tilgang til treff**   | `JobbsøkerInnloggetBorgerTest.kt`                                    | ✅ AT 6.2.3 - Jobbsøker som ikke er lagt til får 404                                           |
| **Publiser treff**                     | `RekrutteringstreffTest.kt`                                          | ✅ AT 3.1.1 - Publisering endrer status fra UTKAST til PUBLISERT                               |

---

## ❌ Manglende tester – anbefalt å implementere

Følgende akseptansetester har backend-logikk som bør testes automatisk, men som ikke er dekket i dag:

### 1. Tilstandsoverganger (høy prioritet)

| AT-ref | Beskrivelse                                        | Anbefalt testfil            | Kompleksitet |
| ------ | -------------------------------------------------- | --------------------------- | ------------ |
| 9.1.x  | `fullfor` skal feile hvis `tilTid` ikke er passert | `RekrutteringstreffTest.kt` | Lav          |
| -      | `fullfor` skal feile for AVLYST treff              | `RekrutteringstreffTest.kt` | Lav          |
| -      | `avlys` skal feile for FULLFØRT treff              | `RekrutteringstreffTest.kt` | Lav          |
| -      | `gjenapn` skal kun fungere for AVLYST treff        | `RekrutteringstreffTest.kt` | Lav          |

### 2. Arbeidsgiver-validering (middels prioritet)

| AT-ref | Beskrivelse                                     | Anbefalt testfil      | Kompleksitet |
| ------ | ----------------------------------------------- | --------------------- | ------------ |
| 2.4.1  | Legg til ugyldig orgnummer → 400 Bad Request    | `ArbeidsgiverTest.kt` | Lav          |
| 2.4.1  | Legg til orgnummer med feil format gir feilkode | `ArbeidsgiverTest.kt` | Lav          |

### 3. Jobbsøker API-idempotens (middels prioritet)

| AT-ref | Beskrivelse                                                     | Anbefalt testfil   | Kompleksitet |
| ------ | --------------------------------------------------------------- | ------------------ | ------------ |
| 4.1.3  | Legg til samme jobbsøker to ganger via API → idempotent respons | `JobbsøkerTest.kt` | Lav          |

### 4. Hendelseslogg-autorisasjon (middels prioritet)

| AT-ref | Beskrivelse                                             | Anbefalt testfil       | Kompleksitet |
| ------ | ------------------------------------------------------- | ---------------------- | ------------ |
| 14.1.8 | Veileder kan IKKE hente hendelseslogg for andres treff  | `AutorisasjonsTest.kt` | Lav          |
| 14.1.8 | Markedskontakt (ikke eier) kan IKKE hente hendelseslogg | `AutorisasjonsTest.kt` | Lav          |

### 5. KI bypass-sikkerhet (høy prioritet – ROS 27547)

| AT-ref | Beskrivelse                                                             | Anbefalt testfil         | Kompleksitet |
| ------ | ----------------------------------------------------------------------- | ------------------------ | ------------ |
| 11.8.2 | API-kall uten KI-validering avvises                                     | `KiAutorisasjonsTest.kt` | Middels      |
| 11.8.3 | Lagring med `flaggAdvarsel=true` uten `lagreLikevel` gir feil           | `KiAutorisasjonsTest.kt` | Middels      |
| 11.8.4 | Backend krever gyldig KI-valideringsresultat for å lagre tittel/innlegg | `KiAutorisasjonsTest.kt` | Høy          |

### 6. Pilotkontor-bytte (lav prioritet)

| AT-ref | Beskrivelse                                                 | Anbefalt testfil     | Kompleksitet |
| ------ | ----------------------------------------------------------- | -------------------- | ------------ |
| 15.6.1 | Bytte fra pilotkontor til ikke-pilotkontor → mister tilgang | `PilotkontorTest.kt` | Middels      |
| 15.6.2 | Bytte fra ikke-pilotkontor til pilotkontor → får tilgang    | `PilotkontorTest.kt` | Middels      |

---

## Anbefalte neste steg

1. **Start med tilstandsoverganger** – enkle tester med høy verdi for å sikre at ugyldige operasjoner blokkeres
2. **KI bypass-sikkerhet** – kritisk for ROS, bør prioriteres
3. **Hendelseslogg-autorisasjon** – viktig for tilgangsstyring
4. **Arbeidsgiver-validering** – lav innsats, god dekning
