# Automatiske backend-tester

Dette dokumentet gir oversikt over teststatus og definerer Trello-oppgaver for manglende tester.

> **Målgruppe:** Utviklere som skal implementere backend-tester for Rekrutteringstreff.

---

## Teststatus etter merge med main

Etter merge med `main` er mange tester nå implementert. Her er oppdatert status:

### ✅ Implementerte tester

| Område                                 | Testfil(er)                                                   | Dekning                                                                                        |
| -------------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| **Jobbsøker svar ja/nei**              | `JobbsøkerInnloggetBorgerTest.kt`                             | ✅ `svar ja til invitasjon`, `svar nei til invitasjon`                                         |
| **Endre svar**                         | `JobbsøkerInnloggetBorgerTest.kt`                             | ✅ `kan endre svar fra ja til nei`, `kan endre svar fra nei til ja`                            |
| **Avlysning med hendelser**            | `RekrutteringstreffTest.kt`                                   | ✅ `avlys oppretter hendelse for rekrutteringstreff og alle jobbsøkere med aktivt svar ja`     |
| **Avlysning uten svar ja**             | `RekrutteringstreffTest.kt`                                   | ✅ `avlys oppretter kun rekrutteringstreff-hendelse når ingen jobbsøkere har aktivt svar ja`   |
| **Fullføring**                         | `RekrutteringstreffTest.kt`                                   | ✅ `fullfor oppretter hendelse...` (flere varianter)                                           |
| **Endringsvarsel til inviterte**       | `RekrutteringstreffTest.kt`                                   | ✅ `registrer endring oppretter hendelser for publisert treff med inviterte jobbsøkere`        |
| **Endringsvarsel til svart ja**        | `RekrutteringstreffTest.kt`                                   | ✅ `registrer endring oppretter hendelser for publisert treff med jobbsøkere som har svart ja` |
| **Endringsvarsel IKKE til svart nei**  | `RekrutteringstreffTest.kt`                                   | ✅ `registrer endring varsler ikke jobbsøkere som har svart nei`                               |
| **Sletting av treff**                  | `RekrutteringstreffTest.kt`                                   | ✅ `slettRekrutteringstreffMedUpublisertedata`, `slett rekrutteringstreff feiler (409)...`     |
| **Svar-service logikk**                | `JobbsøkerServiceTest.kt`                                     | ✅ `svarJaTilInvitasjon...`, `svarNeiTilInvitasjon...`, `finnJobbsøkereMedAktivtSvarJa...`     |
| **Minside-varsel lytter**              | `MinsideVarselSvarLytterTest.kt`                              | ✅ Omfattende                                                                                  |
| **KI tekstvalidering**                 | `KiTekstvalideringTest.kt`                                    | ✅ Mange testcases                                                                             |
| **Persondata-filtrering**              | `PersondataFilterTest.kt`                                     | ✅ Dekket                                                                                      |
| **Synlighet**                          | `SynlighetsKomponentTest.kt`, `SynlighetsLytterTest.kt` m.fl. | ✅ Omfattende                                                                                  |
| **Autorisasjon**                       | `*AutorisasjonsTest.kt` (flere filer)                         | ✅ Omfattende                                                                                  |
| **Pilotkontor**                        | `PilotkontorTest.kt`                                          | ✅ Dekket                                                                                      |
| **Duplikat-håndtering**                | `EierRepositoryTest.kt`, `AktivitetskortTest.kt`              | ✅ `leggTil legger ikke til duplikater`, duplikat-meldinger                                    |
| **Dobbel invitasjon (race condition)** | `InvitasjonFeilhåndteringTest.kt`                             | ✅ Idempotens implementert med radlås (SELECT FOR UPDATE)                                      |
| **Svarfrist-validering**               | `JobbsøkerInnloggetBorgerTest.kt`                             | ✅ `svar ja etter svarfrist avvises`, `svar nei etter svarfrist avvises`                       |
| **Ugyldig treff-ID**                   | `JobbsøkerInnloggetBorgerTest.kt`                             | ✅ GET/POST til ukjent treff-ID gir feilkode                                                   |
| **Dobbelt svar (idempotens)**          | `JobbsøkerInnloggetBorgerTest.kt`                             | ✅ To svar-ja kall håndteres konsistent, samtidige kall                                        |
| **Veileder kan ikke invitere**         | `JobbsokerControllerAutorisasjonsTest.kt`                     | ✅ AT 5.1.8 - Jobbsøkerrettet får HTTP_FORBIDDEN på inviter-endepunkt                          |
| **Jobbsøker-synlighet per rolle**      | `JobbsokerControllerAutorisasjonsTest.kt`                     | ✅ AT 15.1.3, 15.2.3 - Kun eier/utvikler kan hente jobbsøkerliste                              |
| **Slett jobbsøker**                    | `JobbsokerControllerAutorisasjonsTest.kt`, `JobbsøkerServiceTest.kt` | ✅ AT 4.1.4 - markerSlettet med autorisasjon                                              |
| **Re-aktivering av slettet jobbsøker** | `JobbsøkerServiceTest.kt`                                     | ✅ AT 4.1.5 - Slettet jobbsøker kan legges til igjen                                           |
| **Avlysning: kun svart ja varsles**    | `RekrutteringstreffTest.kt`                                   | ✅ AT 8.1.4-8.1.7 - Kun SVART_JA får SVART_JA_TREFF_AVLYST hendelse                            |
| **Opprett treff validering**           | `RekrutteringstreffTest.kt`                                   | ✅ AT 1.1.3 - Ugyldig data/JSON gir 400 feilkode                                               |
| **Jobbsøker uten tilgang til treff**   | `JobbsøkerInnloggetBorgerTest.kt`                             | ✅ AT 6.2.3 - Jobbsøker som ikke er lagt til får 404                                           |

