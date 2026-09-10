# Akseptansetester – WorkOp og treffgjennomføring

Marker ✅ eller ❌ og noter avvik. Skriv «Ikke kjørt» ved tester som ikke er gjennomført.

## 1. Oversikt og tilgang

### 1.1. Søk, eiere og direkte lenker

| #     | Gjør dette                                                                                           | Forventet resultat                                                             | ✅❌ | Notat |
| ----- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | ---- | ----- |
| 1.1.1 | Eier – Søk etter eget WorkOp i kladd, publisert, avlyst og fullført status. Bruk dato-/statusfilter. | WorkOp vises i tilhørende filtre med WorkOp-merking. Slettede treff er skjult. |      |       |
| 1.1.2 | Markedskontakt/veileder uten eierskap – Søk med «Alle», eget kontor og valgte kontorer.              | WorkOp er skjult; vanlige tilgjengelige treff vises.                           |      |       |
| 1.1.3 | Markedskontakt uten eierskap – Åpne direkte lenke til WorkOp.                                        | Ingen tilgang til treff, gjennomføringsdata eller redigering.                  |      |       |
| 1.1.4 | Medeier med arbeidsgiverrettet rolle – Åpne WorkOp.                                                  | Gjennomføringen kan leses og redigeres uten å velge hovedansvarlig.            |      |       |
| 1.1.5 | Utvikler uten eierskap – Søk etter WorkOp og åpne direkte lenke.                                     | Skjult i søket, tilgjengelig via direkte lenke.                                |      |       |

## 2. Opprette og publisere WorkOp

### 2.1. Opprettelse og kategori

| #     | Gjør dette                                                       | Forventet resultat                                                                   | ✅❌ | Notat |
| ----- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------ | ---- | ----- |
| 2.1.1 | Markedskontakt – Velg «Opprett» → «WorkOp» i dev.                | Kladden «WorkOp uten navn» åpnes for redigering med kategori WORKOP og deg som eier. |      |       |
| 2.1.2 | Veileder uten arbeidsgiverrettet rolle – Åpne opprettingsmenyen. | WorkOp-valget er skjult.                                                             |      |       |
| 2.1.3 | Eier – Prøv kategoribytte før og etter publisering.              | WorkOp kan ikke byttes til vanlig rekrutteringstreff.                                |      |       |
| 2.1.4 | Eier – Opprett et vanlig rekrutteringstreff.                     | Vanlig kategori, uten WorkOp-merking eller WorkOp-regler.                            |      |       |

### 2.2. Antall arbeidsgivere og jobbsøkere

| #     | Gjør dette                                                          | Forventet resultat                                                             | ✅❌ | Notat |
| ----- | ------------------------------------------------------------------- | ------------------------------------------------------------------------------ | ---- | ----- |
| 2.2.1 | Eier – Åpne arbeidsgiverdelen med null og fire arbeidsgivere.       | «Det skal planlegges for 5 arbeidsgivere i et WorkOp møte.» vises.             |      |       |
| 2.2.2 | Eier – Legg til arbeidsgiver nummer fem og seks.                    | Infoboksen forsvinner ved fem. Begge arbeidsgiverne kan legges til.            |      |       |
| 2.2.3 | Eier – Fjern én av fem arbeidsgivere.                               | Fire gjenstår; infoboksen vises igjen.                                         |      |       |
| 2.2.4 | Eier – Åpne jobbsøkerfanen med under, nøyaktig og over 25 personer. | Informasjonen om 25 jobbsøkere vises i alle tilfellene. Flere enn 25 tillates. |      |       |
| 2.2.5 | Eier – Åpne arbeidsgiver- og jobbsøkerfanene på et vanlig treff.    | Ingen WorkOp-infobokser vises.                                                 |      |       |

### 2.3. Standardtekst og forhåndsvisning

| #     | Gjør dette                                                                                                      | Forventet resultat                                                                                                              | ✅❌ | Notat |
| ----- | --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 2.3.1 | Eier – Publiser standardteksten. Sammenlign forhåndsvisningen med jobbsøkersiden.                               | Navn, tid, sted, introduksjon og eventuell WorkOp-merking samsvarer. Arbeidsgiverlisten er skjult i begge.                      |      |       |
| 2.3.2 | Jobbsøker – Les standardteksten og svarboksen på stor og liten skjerm. Prøv lenkene.                            | Lesbare overskrifter, avsnitt og lister, fungerende lenker, ingen plassholdere eller rå HTML. Det fremgår hva du skal svare på. |      |       |
| 2.3.3 | Eier/fagansvarlig – Sammenlign tidene i teksten og svarvisningen. Endre WorkOp-tidspunktet og sammenlign igjen. | Formøte, WorkOp-dag og svarfrist er tydelig atskilt, uten motstridende tider.                                                   |      |       |

## 3. Jobbsøkerliste, invitasjoner og aktivitetskort

### 3.1. Fra jobbsøkerlisten til oppmøtelisten

| #     | Gjør dette                                                                                                                               | Forventet resultat                                                                                                                                                                                       | ✅❌ | Notat |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 3.1.1 | Eier – Etter manuell romfordeling: Åpne «Oppmøte», legg til en synlig, uinvitert person i «Jobbsøkere» og besøk steg 1–6 uten sideoppdatering. Gjenta etter intervjufordeling/vurdering. | Personen vises én gang, ikke møtt. Totalen øker med én; fremmøtetallet er uendret. Ingen automatisk rom, interesse, intervju eller vurdering. Andres data og deltakernumre beholdes. | | |
| 3.1.2 | Eier – Registrer personen møtt. Registrer deretter interesse hos en arbeidsgiver med intervjufordeling.                                  | Fremmøtetallet øker med én. Personen får nummer og romplass, uten forhåndsvalgte interesser; intervju først etter interesse. Minste rom velges; andres plasseringer, rekkefølge og vurderinger beholdes. |      |       |
| 3.1.3 | Eier – Legg til samme person igjen etter oppmøte og registreringer.                                                                      | Ingen duplikat eller endring i antall, nummer, rom, interesser, intervjuer eller vurderinger.                                                                                                            |      |       |

### 3.2. Invitasjon og varselkanaler

| #     | Gjør dette                                                                       | Forventet resultat                                                                                                                          | ✅❌ | Notat |
| ----- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 3.2.1 | Jobbsøker – Les SMS-invitasjonen. Logg inn på Nav og åpne invitasjonen.          | SMS omtaler WorkOp og ja/nei-svar, uten arbeidsgivere eller interne data. MinSide åpner riktig WorkOp; SMS trenger ikke direkte trefflenke. |      |       |
| 3.2.2 | Jobbsøker – Les e-postinvitasjonen. Logg inn på Nav og åpne kortet.              | Emnet er «Invitasjon til å treffe arbeidsgivere»; innholdet omtaler WorkOp uten arbeidsgivernavn/interne data. Kortet åpner riktig treff.   |      |       |
| 3.2.3 | Jobbsøker uten ekstern kontaktinformasjon – Åpne invitasjonen på MinSide.        | WorkOp-invitasjon med riktig lenke vises uten SMS/e-post. Ingen arbeidsgivernavn/interne data.                                              |      |       |
| 3.2.4 | Eier/jobbsøker – Inviter en person til vanlig rekrutteringstreff og følg lenken. | Vanlig trefftekst, ikke WorkOp-tekst. Riktig treff åpnes.                                                                                   |      |       |

### 3.3. Aktivitetskort og lenker

| #     | Gjør dette                                                                        | Forventet resultat                                                                                    | ✅❌ | Notat |
| ----- | --------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | ---- | ----- |
| 3.3.1 | Jobbsøker/veileder – Åpne aktivitetsplanen etter invitasjon.                      | Ett felles kort i «Forslag» med riktig tittel, tid, sted, WorkOp-beskrivelse og «Sjekk ut WorkOp-en». |      |       |
| 3.3.2 | Jobbsøker – Følg kortlenken med aktiv innlogging og etter ny innlogging.          | Riktig WorkOp og personlig invitasjon åpnes. Arbeidsgiverlisten er skjult.                            |      |       |
| 3.3.3 | Eier – Registrer oppmøte, rom, interesse, vurdering, «2. intervju» og jobbtilbud. | Ingen nye SMS-er, e-poster eller aktivitetskort. Interne notater/vurderinger vises ikke på kortet.    |      |       |

### 3.4. Endring av publisert WorkOp

| #     | Gjør dette                                                                        | Forventet resultat                                                                                              | ✅❌ | Notat |
| ----- | --------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 3.4.1 | Eier/jobbsøker – Endre tid og sted, varsle ja-svarere uten oppmøte og les SMS-en. | WorkOp, tidspunkt og sted omtales; ingen uvalgte felt, plassholdere, arbeidsgivernavn eller interne data.       |      |       |
| 3.4.2 | Jobbsøker – Les e-posten om endringen.                                            | Emne/innhold omtaler WorkOp. Endrede felt og beskjed om innlogging vises, uten arbeidsgivernavn/interne data.   |      |       |
| 3.4.3 | Jobbsøker – Les endringsvarselet på MinSide og følg lenken.                       | WorkOp og valgte felt omtales, uten arbeidsgivernavn/interne data. Riktig WorkOp åpnes med ny tid og nytt sted. |      |       |
| 3.4.4 | Jobbsøker/veileder – Åpne aktivitetskortet. Følg lenken som jobbsøker.            | Samme kort og WorkOp-lenke, med ny tid og nytt sted. WorkOp-beskrivelse og lenketekst beholdes.                 |      |       |

### 3.5. Avlysning

| #     | Gjør dette                                                               | Forventet resultat                                                                                                        | ✅❌ | Notat |
| ----- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 3.5.1 | Eier/jobbsøker – Avlys WorkOp med ja-svarere uten oppmøte og les SMS-en. | WorkOp er avlyst; mer informasjon etter innlogging. Ingen arbeidsgivernavn/interne data.                                  |      |       |
| 3.5.2 | Jobbsøker – Les e-posten om avlysningen.                                 | Emne/innhold sier at WorkOp er avlyst, uten vanlig trefftekst, plassholdere, arbeidsgivernavn eller interne data.         |      |       |
| 3.5.3 | Jobbsøker – Les avlysningsvarselet på MinSide og følg lenken.            | WorkOp omtales som avlyst, uten arbeidsgivernavn/interne data. Riktig treff åpnes med avlysningsmelding, uten svarskjema. |      |       |
| 3.5.4 | Jobbsøker/veileder – Åpne aktivitetskortet. Følg lenken som jobbsøker.   | Samme kort er «Avbrutt», med WorkOp-beskrivelse og lenke til det avlyste treffet. Ingen nytt kort.                        |      |       |

## 4. Jobbsøkerens WorkOp-visning

### 4.1. Innhold og skjulte arbeidsgivere

| #     | Gjør dette                                     | Forventet resultat                                                                                       | ✅❌ | Notat |
| ----- | ---------------------------------------------- | -------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 4.1.1 | Jobbsøker – Åpne WorkOp med fem arbeidsgivere. | Navn, tid, sted og introduksjon vises. Arbeidsgivere, vurderinger, interesser og romfordeling er skjult. |      |       |
| 4.1.2 | Jobbsøker – Åpne et vanlig rekrutteringstreff. | Arbeidsgiverlisten vises.                                                                                |      |       |

## 5. Navigasjon og endringer underveis

### 5.1. Steg og lagrede registreringer

| #      | Gjør dette                                                                                      | Forventet resultat                                                                                         | ✅❌ | Notat |
| ------ | ----------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 5.1.1  | Eier – Åpne gjennomføringen på et nytt, publisert WorkOp.                                       | «Oppmøte» åpnes. Alle seks stegnavn vises.                                                                 |      |       |
| 5.1.2  | Eier – Prøv «Neste» uten fremmøtte, deretter uten arbeidsgivere. Prøv romsteget direkte.        | Møteplan kan ikke opprettes uten minst én fremmøtt og én arbeidsgiver. «Neste» er sperret.                 |      |       |
| 5.1.3  | Eier – Registrer oppmøte og prøv «Interesse» uten møteplan.                                     | Interessesteget er sperret til møteplan er opprettet.                                                      |      |       |
| 5.1.4  | Eier – Opprett møteplan uten interesser. Prøv intervjufordeling og vurdering.                   | Begge stegene er sperret.                                                                                  |      |       |
| 5.1.5  | Eier – Åpne lenken til et tilgjengelig steg i ny fane.                                          | Samme steg åpnes med oppdaterte data, uten besøk i tidligere steg.                                         |      |       |
| 5.1.6  | Eier – Sett `visSteg` til tekst, deretter til et ikke-nådd steg uten nødvendige registreringer. | Tekst åpner oppmøte; utilgjengelig steg erstattes av et tilgjengelig steg. Ingen redigering uten grunnlag. |      |       |
| 5.1.7  | Eier – Åpne oppsummeringen, gå tilbake og rett en registrering.                                 | Retting tillates når feltsperrene er oppfylt. Treffet blir ikke fullført eller låst av oppsummeringen.     |      |       |
| 5.1.8  | Eier – Nå et senere steg, fjern interesser/vurderinger og åpne steget igjen.                    | Steget er fortsatt tilgjengelig, uten de fjernede registreringene.                                         |      |       |

### 5.2. Person- og arbeidsgiverendringer underveis

| #     | Gjør dette                                                                                                                                                                  | Forventet resultat                                                                                                                            | ✅❌ | Notat |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 5.2.1 | Eier – Under romfordelingen, legg til en uinvitert person og en arbeidsgiver. Registrer personen møtt.                                                                      | Person-, fremmøte- og arbeidsgivertall øker med én. Nytt rom og rotasjon vises; gamle plasseringer og numre beholdes.                         |      |       |
| 5.2.2 | Eier – Flytt personen til høyeste romnummer. Fjern den nye arbeidsgiveren uten registreringer.                                                                              | Ett færre rom, gyldig plassering for personen og oppdatert rotasjon/utskrift uten kollisjoner. Ingen personer fjernes.                        |      |       |
| 5.2.3 | Eier – Legg til en annen arbeidsgiver. Fjern oppmøtet og slett den nye personen.                                                                                            | Person- og fremmøtetall synker med én; arbeidsgivertallet øker med én. Andres plasseringer og numre beholdes.                                 |      |       |
| 5.2.4 | Eier – Gi en uinvitert fremmøtt interesse hos to arbeidsgivere, intervju/vurdering hos den nye. Prøv å fjerne arbeidsgiveren og oppmøtet.                                   | Oppmøtet sperres. Arbeidsgiverfjerning følger avklart sperreregel; registreringene går ikke tapt.                                             |      |       |
| 5.2.5 | Eier – Rydd den nye arbeidsgiverens registreringer og fjern arbeidsgiveren. Prøv oppmøtefjerning. Rydd siste interesse, fjern oppmøte og slett personen.                    | Oppmøtet sperres til siste interesse er fjernet. Deretter tillates sletting. Tellingene oppdateres; andres data beholdes.                     |      |       |
| 5.2.6 | Eier – Legg personen og arbeidsgiveren tilbake. Registrer personen møtt.                                                                                                    | Samme deltakernummer, ingen duplikater. Ryddede interesser, intervjuer og vurderinger forblir fjernet.                                        |      |       |
| 5.2.7 | Eier/utvikler – La en fremmøtt med intervju/vurdering forsvinne fra kandidatsøket og bli bekreftet usynlig. Legg samtidig til en arbeidsgiver og endre en annens interesse. | Visning og handlinger følger 6.3. Arbeidsgiver- og interesseendringene lagres; usynlighet sletter ikke data.                                  |      |       |
| 5.2.8 | Eier – Prøv å fjerne arbeidsgiveren med den usynliges registreringer. Prøv oppmøtefjerning og personsletting fra gammel fane.                                               | Avklarte sperrer gjelder også skjulte registreringer og gamle faner. Andres data er uendret.                                                  |      |       |
| 5.2.9 | Eier/utvikler – Gjør personen synlig igjen. Gjenta for en slettet person.                                                                                                   | Data og nummer kommer tilbake for den første; slettet person forblir slettet. Arbeidsgiverendringer beholdes. Ingen duplikater eller varsler. |      |       |

## 6. Oppmøte, retting og sletting

### 6.1. Oppmøte, deltakernummer og statuser

| #     | Gjør dette                                                                      | Forventet resultat                                                                                  | ✅❌ | Notat |
| ----- | ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | ---- | ----- |
| 6.1.1 | Eier – Registrer en uinvitert person og en nei-svarer møtt. Last siden på nytt. | Begge er møtt med unike numre. Fremmøtetallet øker med to; ingen invitasjoner eller varsler sendes. |      |       |
| 6.1.2 | Eier – Fjern oppmøte uten øvrige registreringer. Registrer personen møtt igjen. | Tidligere svarstatus gjenopprettes og rom fjernes. Ved nytt oppmøte beholdes nummeret.              |      |       |
| 6.1.3 | Eier – Endre oppmøte. Kontroller sortering, tellinger og «Møtt opp»-filteret.   | Navnesorteringen beholdes; status, tellinger og filtrerte personer samsvarer.                       |      |       |
| 6.1.4 | Eier – Prøv «Endre svar» for en fremmøtt.                                       | Sperret med beskjed om å fjerne oppmøtet først.                                                     |      |       |

### 6.2. Sperrer ved retting

| #     | Gjør dette                                                                                                 | Forventet resultat                                                                                                   | ✅❌ | Notat |
| ----- | ---------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 6.2.1 | Eier – Fjern to interesser én om gangen, uten vurderingsfelter. Prøv oppmøtefjerning etter hver.           | Oppmøtefjerning tillates først etter siste interesse. Personen fjernes fra tilhørende intervju-/ekskluderingslister. |      |       |
| 6.2.2 | Eier – Sett «Ingen vurdering», behold notat eller «2. intervju». Prøv interesse- og oppmøtefjerning.       | Begge sperres, med beskjed om gjenstående registreringer.                                                            |      |       |
| 6.2.3 | Eier – Rydd vurderinger og interesser hos to arbeidsgivere, én om gangen. Prøv oppmøtefjerning etter hver. | Oppmøtet kan fjernes når begge er ryddet. Andres data beholdes.                                                      |      |       |
| 6.2.4 | Eier – Flytt personen under sperrelinjen hos alle arbeidsgiverne. Prøv oppmøtefjerning.                    | Fortsatt sperret av interessene.                                                                                     |      |       |

### 6.3. Usynlige jobbsøkere

| #     | Gjør dette                                                                                                                                | Forventet resultat                                                                                                     | ✅❌ | Notat |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 6.3.1 | Eier/utvikler – La en person med gjennomføringsdata forsvinne fra søket og bli bekreftet usynlig. Kontroller personliste, oppmøte og rom. | Personen skjules/anonymiseres etter avtalt regel. Identitet og oppmøte-/flyttehandlinger skjules uten godkjent unntak. |      |       |
| 6.3.2 | Eier/utvikler – Kontroller interesse, intervjufordeling og vurdering. Prøv endringer i steg 1–5 fra gammel fane og API.                   | Nye registreringer sperres. Avtalt retting håndheves likt i skjermbildet og API-et. Andres data beholdes.              |      |       |
| 6.3.3 | Eier – Kontroller oppsummering og nye rom-/intervjuutskrifter.                                                                            | Tellingene følger avtalt regel; utskrifter røper ikke personen.                                                        |      |       |
| 6.3.4 | Eier/utvikler – Gjør personen synlig igjen og åpne gjennomføringen på nytt.                                                               | Samme lagrede data og deltakernummer, uten duplikater eller varsler.                                                   |      |       |

### 6.4. Sletting og gjeninnlegging

| #     | Gjør dette                                                                                             | Forventet resultat                                                                                                                | ✅❌ | Notat |
| ----- | ------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 6.4.1 | Eier – Prøv å slette en aldri invitert fremmøtt. Rydd vurderinger, interesser og oppmøte; slett igjen. | Sperret til «Lagt til». Personen fjernes fra aktive lister/utskrifter. Fremmøte og total synker én gang hver; historikk beholdes. |      |       |
| 6.4.2 | Eier – Rydd registreringer, oppmøte og svar for en tidligere invitert person. Prøv sletting.           | Status er «Invitert»; sletting sperres.                                                                                           |      |       |
| 6.4.3 | Eier – Rydd og slett siste person på et eget WorkOp, uten tidligere invitasjon.                        | Tomme lister, tellinger null. Møteplan og rom beholdes.                                                                           |      |       |
| 6.4.4 | Eier – Legg til en ny person, deretter personen fra 6.4.1. Registrer begge møtt.                       | Ny person får nytt nummer; gjeninnlagt person beholder sitt. Ingen duplikater eller gjenopprettede interesser/vurderinger.        |      |       |

## 7. Møteplan, rom og rotasjon

### 7.1. Opprette og endre møteplan

| #      | Gjør dette                                                                               | Forventet resultat                                                                                         | ✅❌ | Notat |
| ------ | ---------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 7.1.1  | Eier – Åpne møteoppsettet første gang.                                                   | Start 10:00, varighet 10 minutter. Romantallet følger arbeidsgiverantallet, uten eget antallsfelt.         |      |       |
| 7.1.2  | Eier – Opprett møteplan med fem arbeidsgivere og 25 fremmøtte.                           | Fem rom med fem personer i hvert; hver person finnes én gang.                                              |      |       |
| 7.1.3  | Eier – Prøv tom starttid, 24:00 og ugyldige minutter.                                    | Ugyldige tider avvises. Gyldig format er HH:mm.                                                            |      |       |
| 7.1.4  | Eier – Prøv varighet 0, negativ og desimaltall, deretter 1 minutt.                       | Bare positive heltall godtas; 1 minutt lagres.                                                             |      |       |
| 7.1.5  | Eier – Flytt personer manuelt. Rediger møteoppsettet til 09:00 og 15 minutter.           | Rotasjonstidene endres; romplasseringer, interesser, intervjuer og vurderinger beholdes.                   |      |       |
| 7.1.6  | Eier – Rediger møteoppsettet og avbryt.                                                  | Lagrede tider og romplasseringer beholdes.                                                                 |      |       |
| 7.1.7  | Eier – Endre møtelengden og sammenlign med treffets start-/sluttid.                      | Treffets tider er uendret; ingen endringsvarsel sendes.                                                    |      |       |

### 7.2. Flytting og endret oppmøte

| #     | Gjør dette                                                                            | Forventet resultat                                                         | ✅❌ | Notat |
| ----- | ------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- | ---- | ----- |
| 7.2.1 | Eier – Flytt en person med romvalget.                                                 | Bare personen flyttes. «Lagret» vises først etter serverbekreftelse.       |      |       |
| 7.2.2 | Eier – Flytt en annen person med dra-og-slipp. Last siden på nytt.                    | Flyttingen beholdes, uten duplikater.                                      |      |       |
| 7.2.3 | Eier – Flytt til et rom med både lavere og høyere deltakernumre. Åpne på nytt.        | Personene står i stigende nummerrekkefølge.                                |      |       |
| 7.2.4 | Eier – Flytt den automatisk plasserte personen til et annet rom.                      | Flyttingen lagres; andres plasseringer beholdes.                           |      |       |
| 7.2.5 | Eier – Fjern alt oppmøte uten interesser/vurderinger. Registrer én person møtt igjen. | Møteplan og tomme rom beholdes; personen får rom uten ny møteplan.         |      |       |
| 7.2.6 | Eier – Velg «Fordel på nytt» etter manuelle flyttinger. Avbryt.                       | Dialogen varsler erstatning av plasseringer; avbryt beholder dem.          |      |       |
| 7.2.7 | Eier – Bekreft «Fordel på nytt» etter manuelle flyttinger.                            | Jevn romfordeling. Interesser, intervjuer og vurderinger beholdes.         |      |       |

### 7.3. Arbeidsgiverendringer etter romfordeling

| #     | Gjør dette                                                                                                              | Forventet resultat                                                                                                          | ✅❌ | Notat |
| ----- | ----------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 7.3.1 | Eier – Legg til en sjette arbeidsgiver etter manuell romfordeling. Åpne romsteget og last siden på nytt.                | Sjette arbeidsgiver inngår i rotasjonen med et tomt rom. Gamle plasseringer beholdes; ingen duplikater eller omfordeling.   |      |       |
| 7.3.2 | Eier – Fjern midterste og siste arbeidsgiver i rotasjonen fra separate WorkOp med fem arbeidsgivere og manuell romfordeling. | Fire arbeidsgivere/rom, uten kollisjoner. Berørte personer får gyldige rom; øvrige plasseringer, oppmøte og numre beholdes. | | |
| 7.3.3 | Eier – Flytt en berørt person etter fjerningen. Last siden på nytt og åpne rom-/rotasjonsutskriftene.                   | Flyttingen beholdes. Hver fremmøtt finnes én gang i gyldig rom; fjernet arbeidsgiver er utelatt.                            |      |       |

### 7.4. Færre og flere enn fem arbeidsgivere

| #     | Gjør dette                                                                                                                         | Forventet resultat                                                                                              | ✅❌ | Notat |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 7.4.1 | Eier – Lag møteplan med 25 fremmøtte, fire arbeidsgivere, start 10:00 og 10 minutter per møte.                                     | Fire rom: 7, 6, 6, 6 personer. Fire runder til 10:40; hvert rom besøkes én gang per arbeidsgiver.               |      |       |
| 7.4.2 | Eier – Gjenta med 25 fremmøtte og seks arbeidsgivere, samme tider.                                                                 | Seks rom: 5, 4, 4, 4, 4, 4 personer. Seks runder til 11:00; hvert rom besøkes én gang per arbeidsgiver.         |      |       |
| 7.4.3 | Eier – Registrer interesse, intervjurekkefølge og vurdering hos hver arbeidsgiver med henholdsvis fire og seks arbeidsgivere.      | Alle arbeidsgivere kan brukes i alle tre steg; data lagres på riktig par.                                       |      |       |
| 7.4.4 | Eier – Åpne oppsummering og alle utskrifter for begge treffene.                                                                    | Fire/seks arbeidsgivere med tilhørende tall, rom og runder. Ingen manglende seksjoner eller avkuttede kolonner. |      |       |
| 7.4.5 | Eier – Prøv møteplan med fremmøtte uten arbeidsgivere. Legg til én arbeidsgiver og prøv igjen.                                     | Første forsøk avvises; andre gir ett rom og én runde.                                                           |      |       |
| 7.4.6 | Eier/utvikler – Prøv å fjerne siste arbeidsgiver fra møteplan med fremmøtte uten interesser/vurderinger, i skjermbildet og API-et. | Skjermbildet krever ny arbeidsgiver først. API-et håndhever avklart sperreregel.                                |      |       |

## 8. Interesse

### 8.1. Registrering og retting

| #      | Gjør dette                                                                                                     | Forventet resultat                                                                             | ✅❌ | Notat |
| ------ | -------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- | ---- | ----- |
| 8.1.1  | Eier – Åpne interesse med fremmøtte og ikke-fremmøtte.                                                         | Bare fremmøtte vises som rader; aktive arbeidsgivere som kolonner.                             |      |       |
| 8.1.2  | Eier – Velg tre arbeidsgivere for én person og én for en annen.                                                | Radtotaler 3 og 1; øvrige celler er uendret.                                                   |      |       |
| 8.1.3  | Eier – Fjern alle interesser og prøv «Neste».                                                                  | «Neste» er sperret.                                                                            |      |       |
| 8.1.4  | Eier – Registrer interesser og gå videre første gang.                                                          | Intervjufordeling opprettes fra interessene og åpnes etter ferdig lagring.                     |      |       |
| 8.1.5  | Eier – Lag manuell intervjurekkefølge. Gå tilbake til interesse og frem igjen uten endringer.                  | Rekkefølgen beholdes.                                                                          |      |       |
| 8.1.6  | Eier – Prøv interessefjerning med vurdering, notat, avtalt intervju eller jobbtilbud. Prøv hvert felt separat. | Sperret med beskjed om å nullstille registreringen i steg 5.                                   |      |       |

## 9. Intervjufordeling

### 9.1. Rekkefølge, utvalg og konflikter

| #      | Gjør dette                                                          | Forventet resultat                                                                                       | ✅❌ | Notat |
| ------ | ------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 9.1.1  | Eier – Åpne første fordeling etter interesseregistrering.           | Interesserte vises hos tilhørende arbeidsgivere, høyst én gang hos hver.                                 |      |       |
| 9.1.2  | Eier – Endre rekkefølge med flytteknappene. Last siden på nytt.     | Manuell rekkefølge beholdes, uten automatisk navne-/nummersortering.                                     |      |       |
| 9.1.3  | Eier – Endre rekkefølge med dra-og-slipp.                           | Rekkefølgen lagres; fokus og videre tastaturbetjening beholdes.                                          |      |       |
| 9.1.4  | Eier – Flytt en person under sperrelinjen.                          | Personen vises under «Ikke gjennomført speedintervju» og telles ikke som inkludert. Interessen beholdes. |      |       |
| 9.1.5  | Eier – Flytt personen over sperrelinjen igjen.                      | Inkludert på valgt plass; andre arbeidsgiveres fordelinger beholdes.                                     |      |       |
| 9.1.6  | Eier – Flytt alle under sperrelinjen og prøv «Neste».               | «Neste» er sperret.                                                                                      |      |       |
| 9.1.7  | Eier – Inkluder én person igjen.                                    | «Neste» åpnes etter lagring.                                                                             |      |       |
| 9.1.8  | Eier – Sett samme person på samme plassnummer hos to arbeidsgivere. | Kollisjonen markeres som «Plasskonflikt».                                                                |      |       |
| 9.1.9  | Eier – Flytt personen slik at konflikten opphører.                  | Konfliktmarkeringen forsvinner.                                                                          |      |       |
| 9.1.10 | Eier – Velg «Fordel på nytt» og avbryt.                             | Dialogen varsler erstatning av rekkefølgen; avbryt beholder rekkefølge og ekskluderinger.                |      |       |
| 9.1.11 | Eier – Bekreft «Fordel på nytt» med ekskluderte personer.           | Ny rekkefølge forsøker å redusere konflikter; ekskluderinger med fortsatt interesse beholdes.            |      |       |
| 9.1.12 | Eier – Legg til en interesse etter intervjufordeling.               | Personen inkluderes hos arbeidsgiveren; eksisterende rekkefølge og andre ekskluderinger beholdes.        |      |       |
| 9.1.13 | Eier – Fjern interesse for en ekskludert person uten vurdering.     | Personen fjernes fra arbeidsgiverens intervju- og ekskluderingsliste.                                    |      |       |
| 9.1.14 | Eier – Flytt en vurdert person under sperrelinjen. Åpne vurdering.  | Vurderingen beholdes og kan leses.                                                                       |      |       |
| 9.1.15 | Eier – Fordel mange interesserte hos én arbeidsgiver.               | Ingen automatisk tidskvote, venteliste eller kalenderavtaler. Utvalg og rekkefølge kan endres manuelt.   |      |       |

### 9.2. Arbeidsgiverendringer etter intervjufordeling

| #     | Gjør dette                                                                                                                 | Forventet resultat                                                                                                                                                                              | ✅❌ | Notat |
| ----- | -------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 9.2.1 | Eier – Legg til en ny arbeidsgiver etter manuell intervjufordeling. Kontroller steg 2–5, registrer interesse og gå videre. | Nytt rom/rotasjon, ellers tomt til registrering. Interessen gir intervju hos den nye; andres rekkefølge, ekskluderinger og vurderinger beholdes.                                                |      |       |
| 9.2.2 | Eier – Prøv å fjerne en arbeidsgiver med intervjuer/vurderinger. Rydd etter avtalt regel og fjern igjen.                   | Avklart sperre håndheves. Etter fjerning: ingen aktive rader/utskrifter for arbeidsgiveren, gyldige rom og uendret oppmøte/nummer. Ingen automatisk intervjuoverføring eller endring hos andre. |      |       |
| 9.2.3 | Eier – Legg tilbake samme arbeidsgiver. Sammenlign interesser, intervjurekkefølge og vurderinger med før fjerningen.       | Tidligere data håndteres etter avtalt regel, uten duplikate arbeidsgivere eller person–arbeidsgiver-par.                                                                                        |      |       |

## 10. Vurdering og oppfølging

### 10.1. Vurdering og notater

| #      | Gjør dette                                                                                | Forventet resultat                                                                          | ✅❌ | Notat |
| ------ | ----------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------- | ---- | ----- |
| 10.1.1 | Eier – Åpne vurdering med interesser, inkluderte/ekskluderte intervjuer og vurderinger.   | Riktig person under riktig arbeidsgiver; interesse, intervju og vurdering er atskilt.       |      |       |
| 10.1.2 | Eier – Velg «Aktuell», «Kanskje» og «Ikke aktuell» på tre par. Last siden på nytt.        | Valgene beholdes på riktig par, uten påvirkning hos andre arbeidsgivere.                    |      |       |
| 10.1.3 | Eier – Bytt fra «Aktuell» til «Ikke aktuell» og «Ingen vurdering».                        | Siste valg lagres; notater, intervju og jobbtilbud beholdes.                                |      |       |
| 10.1.4 | Eier – Velg ett arbeidsgivernotat og ett jobbsøkernotat.                                  | Valgene er gruppert etter part. Ingen fritekstfelt vises.                                   |      |       |
| 10.1.5 | Eier – Velg flere notater, last siden på nytt og fjern ett.                               | Notatene beholdes ved ny åpning; bare valgt notat fjernes.                                  |      |       |
| 10.1.6 | Eier – Sammenlign arbeidsgiver- og jobbsøkernotater.                                      | Parten fremgår av tekst, ikke bare farge. Arbeidsgivernotater vises ikke som feilmeldinger. |      |       |
| 10.1.7 | Eier – Registrer notat uten vurderingsvalg.                                               | Notatet lagres og sperrer interesse-/oppmøtefjerning.                                       |      |       |

### 10.2. Avtalt intervju og jobbtilbud

| #      | Gjør dette                                                                     | Forventet resultat                                                                       | ✅❌ | Notat |
| ------ | ------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------- | ---- | ----- |
| 10.2.1 | Eier – Kryss av «2. intervju» uten dato.                                       | Avtalen lagres; valgfritt datofelt vises.                                                |      |       |
| 10.2.2 | Eier – Velg/skriv gyldig dato, forlat feltet og åpne på nytt.                  | Datoen beholdes.                                                                         |      |       |
| 10.2.3 | Eier – Skriv 31.02.2027 og forlat feltet.                                      | Valideringsfeil; tidligere lagret dato beholdes.                                         |      |       |
| 10.2.4 | Eier – Tøm datoen, behold «2. intervju».                                       | Avtalen beholdes uten dato.                                                              |      |       |
| 10.2.5 | Eier – Fjern «2. intervju» med lagret dato.                                    | Avtale og dato fjernes.                                                                  |      |       |
| 10.2.6 | Eier – Kryss av «Jobbtilbud», last siden på nytt og fjern krysset.             | Tilbudet lagres og fjernes; ingen automatisk formidling, stilling eller invitasjonssvar. |      |       |
| 10.2.7 | Eier – Registrer intervju og jobbtilbud hos to arbeidsgivere for samme person. | Begge registreringer beholdes, atskilt per arbeidsgiver.                                 |      |       |

### 10.3. Formidling og nullstilling

| #      | Gjør dette                                                                            | Forventet resultat                                                                                | ✅❌ | Notat |
| ------ | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | ---- | ----- |
| 10.3.1 | Eier – Formidle personen til arbeidsgiveren og åpne oppfølging.                       | Paret merkes «Formidlet» med riktig lenke. Merket kan ikke settes manuelt i vurderingen.          |      |       |
| 10.3.2 | Eier – Kontroller samme person hos en annen arbeidsgiver.                             | Den andre arbeidsgiveren merkes ikke «Formidlet».                                                 |      |       |
| 10.3.3 | Eier – Tøm vurdering, notater, intervju/dato og jobbtilbud på et par uten formidling. | Registreringen er tom; interessen kan fjernes. Historikken beholdes.                              |      |       |
| 10.3.4 | Utvikler/eier – La formidlingshentingen feile.                                        | Feil varsles; vurdering kan brukes. Manglende data vises ikke som bekreftet fravær av formidling. |      |       |
| 10.3.5 | Eier – Angre personens eneste formidling. Åpne oppfølging og oppsummering.            | Merke og telling oppdateres; andre vurderingsfelter og tidligere oppmøte beholdes.                |      |       |

## 11. Oppsummering

### 11.1. Tellinger

| #       | Gjør dette                                                                                                          | Forventet resultat                                                                                  | ✅❌ | Notat |
| ------- | ------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | ---- | ----- |
| 11.1.1  | Eier – Åpne oppsummering med fem personer, tre fremmøtte og to arbeidsgivere.                                       | 3 møtt av 5 personer, 2 arbeidsgivere.                                                              |      |       |
| 11.1.2  | Eier – Inkluder fremmøtte testpersoner A/B hos arbeidsgiver 1 og A/C hos arbeidsgiver 2. Kontroller intervjutallet. | 4 intervjuer fordelt på 3 personer.                                                                 |      |       |
| 11.1.3  | Eier – Vurder A som «Aktuell» hos 1 og «Ikke aktuell» hos 2, B som «Kanskje» hos 1. La C være uvurdert.             | 1 aktuell, 1 kanskje, 0 ikke aktuelle og 1 ikke vurdert. A telles én gang.                          |      |       |
| 11.1.4  | Eier – Avtal videre intervju for A hos arbeidsgiver 1. Formidle A til arbeidsgiver 2.                               | Samlet 1 med videre intervju og 1 formidlet.                                                        |      |       |
| 11.1.5  | Eier – Kontroller arbeidsgiver 1 i oppsummeringen.                                                                  | 2 vurderte, 1 aktuell, 1 videre intervju, 0 formidlede.                                             |      |       |
| 11.1.6  | Eier – Kontroller arbeidsgiver 2.                                                                                   | 1 vurdert, 0 aktuelle, 0 videre intervjuer, 1 formidlet. C telles ikke som vurdert.                 |      |       |
| 11.1.7  | Eier – Endre A fra «Aktuell» til «Kanskje» hos arbeidsgiver 1.                                                      | Samlet 0 aktuelle og 2 kanskje.                                                                     |      |       |
| 11.1.8  | Eier – Registrer videre intervju for A også hos arbeidsgiver 2.                                                     | Samlet fortsatt 1 person; begge arbeidsgivere viser 1 avtalt intervju.                              |      |       |
| 11.1.9  | Eier – Flytt C under sperrelinjen hos arbeidsgiver 2. Behold oppmøtet.                                              | Intervjutallet synker fra 4 til 3; fremmøtetallet forblir 3.                                        |      |       |
| 11.1.10 | Utvikler/eier – La formidlingshentingen feile.                                                                      | Advarsel om mulig for lavt formidlingstall; øvrige tall vises.                                      |      |       |

## 12. Utskrift og tastaturbruk

### 12.1. Utskrifter

| #      | Gjør dette                                                                        | Forventet resultat                                                                                                                          | ✅❌ | Notat |
| ------ | --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 12.1.1 | Eier – Velg «Utskrift til arbeidsgivere» med fem arbeidsgivere.                   | Fem seksjoner med arbeidsgivernavn, tider og rom. Ingen jobbsøkernavn, fødselsnumre eller vurderinger.                                      |      |       |
| 12.1.2 | Eier – Velg «Utskrift til jobbsøkere» med fem rom.                                | Fem seksjoner med romnummer, deltakernumre/initialer, tider og besøkende arbeidsgivere.                                                     |      |       |
| 12.1.3 | Eier – Kontroller et tomt rom i utskriften.                                       | Romplan og «Ingen jobbsøkere» vises, uten personer fra andre rom.                                                                           |      |       |
| 12.1.4 | Eier – Velg «Vis utskrift» i intervjufordelingen.                                 | Inkluderte intervjuer i lagret rekkefølge, med nummer/initialer. Ekskluderte personer og arbeidsgivere uten inkluderte intervjuer utelates. |      |       |
| 12.1.5 | Eier – Kontroller personopplysninger i alle utskriftsvarianter.                   | Ingen fulle navn, fødselsnumre, kontaktopplysninger, notater eller vurderinger.                                                             |      |       |
| 12.1.6 | Eier – Åpne nettleserens forhåndsvisning for alle utskrifter.                     | Rom/rotasjon stående, intervjufordeling liggende. Sideskift mellom seksjoner; lesbar tekst uten avkuttede kolonner.                         |      |       |
| 12.1.7 | Eier – Endre rom, tid og intervjurekkefølge. Vent på lagring og åpne utskriftene. | Siste lagrede plan vises.                                                                                                                   |      |       |
| 12.1.8 | Eier – Avbryt utskrift. Lukk dialogen med lukkeknapp og Escape.                   | Samme steg åpnes, med uendrede data og fungerende navigasjon.                                                                               |      |       |
| 12.1.9 | Eier – Skriv ut planen for «Testperson-Alfa Eksempel Fiktiv».                     | Initialene TAEF og deltakernummer vises, ikke fullt navn.                                                                                   |      |       |

### 12.2. Tastatur, zoom og liten skjerm

| #      | Gjør dette                                                                    | Forventet resultat                                                                      | ✅❌ | Notat |
| ------ | ----------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- | ---- | ----- |
| 12.2.1 | Eier – Registrer oppmøte/interesse med Tab, Shift+Tab og mellomrom.           | Riktig person/celle kan identifiseres og betjenes; synlig fokus beholdes etter lagring. |      |       |
| 12.2.2 | Eier – Flytt mellom rom og i intervjurekkefølgen uten dra-og-slipp.           | Romvalg/flytteknapper gir samme flyttemuligheter.                                       |      |       |
| 12.2.3 | Eier – Fokuser sperret oppmøte/interesse med tastatur.                        | Sperreårsaken er tilgjengelig uten mus.                                                 |      |       |
| 12.2.4 | Eier – Bruk 200 % zoom og smalt vindu i alle seks steg/dialoger.              | Felter, feil og knapper er tilgjengelige; brede tabeller kan rulles.                    |      |       |
| 12.2.5 | Eier – Endre data mens lagringsstatus veksler mellom lagring, lagret og feil. | Tydelig status; rader flytter seg ikke under pågående klikk.                            |      |       |

## 13. Hendelser, lagringsfeil og samtidighet

### 13.1. Hendelser

| #      | Gjør dette                                                               | Forventet resultat                                                                               | ✅❌ | Notat |
| ------ | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ | ---- | ----- |
| 13.1.1 | Eier – Registrer og fjern oppmøte. Åpne hendelsene.                      | To hendelser med hvem/når; registreringen har deltakernummer. Opprinnelig hendelse beholdes.     |      |       |
| 13.1.2 | Eier – Endre «Aktuell» til «Ikke aktuell». Åpne hendelsene.              | Lesbar hendelse med tidligere og ny vurdering.                                                   |      |       |
| 13.1.3 | Eier – Legg til og fjern arbeidsgiver- og jobbsøkernotat.                | Alle endringer vises med lesbar notattekst og riktig part, ikke tekniske koder.                  |      |       |
| 13.1.4 | Eier – Slå «2. intervju» og «Jobbtilbud» på og av.                       | Registrering og angring vises på riktig person.                                                  |      |       |
| 13.1.5 | Eier – Opprett møteplan, endre møteoppsett og fordel intervjuer på nytt. | Egne treffhendelser for opprettelse, endret møteoppsett og ny intervjufordeling.                 |      |       |
| 13.1.6 | Utvikler – Kontroller arbeidsgiverkoblingen i vurderingshendelser.       | Hendelsen ligger på personen med arbeidsgiverkontekst, uten identisk hendelse på arbeidsgiveren. |      |       |
| 13.1.7 | Utvikler – Send identisk oppmøte, interesse og vurdering på nytt.        | Ingen duplikater eller endringshendelser for uendrede felt.                                      |      |       |

### 13.2. Treghet og lagringsfeil

| #       | Gjør dette                                                                           | Forventet resultat                                                                                        | ✅❌ | Notat |
| ------- | ------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 13.2.1 | Utvikler/eier – Forsink lagring av oppmøte, interesse og vurdering. Endre samme/ulike rader raskt. Prøv «Neste», «Tilbake» og stegnavigasjon. Last siden på nytt etter lagring. | Tydelig lagringsstatus; stegbytte sperres til lagringen er bekreftet. Siste komplette valg per person/par beholdes etter omlasting. | | |
| 13.2.2  | Utvikler/eier – Avvis lagring av oppmøte, interesse og vurdering uten serverendring. | Feil på riktig rad/celle; bekreftet tilstand hentes. Ulagrede valg vises ikke som lagret.                 |      |       |
| 13.2.3  | Utvikler/eier – Fremprovoser feil på rad A, lagre deretter rad B.                    | Feilen på A beholdes; B vises som lagret.                                                                 |      |       |
| 13.2.4  | Utvikler/eier – Lagre romflytting på serveren, men mist svaret.                      | Servertilstanden hentes; lagret flytting vises med varsel om usikkert utfall, uten falsk tilbakestilling. |      |       |
| 13.2.5  | Utvikler/eier – Mist svaret etter lagret intervjurekkefølge og «Fordel på nytt».     | Bekreftet tilstand hentes; ingen automatisk ny fordeling sendes.                                          |      |       |
| 13.2.6  | Utvikler/eier – La skrivesvaret og etterfølgende henting feile.                      | «Tilstanden er ubekreftet» og «Hent på nytt» vises. Redigering og stegbytte sperres.                      |      |       |
| 13.2.7  | Utvikler/eier – Velg «Hent på nytt». Forsink svaret, la hentingen lykkes.            | Bare henting utføres. Kontrollene åpnes først ved bekreftet tilstand.                                     |      |       |
| 13.2.8  | Utvikler/eier – Forsink en romflytting. Kontroller før svaret kommer.                | Personen flyttes først etter bekreftelse; lagringsstatus vises og kontrollene sperres.                    |      |       |
| 13.2.9  | Utvikler/eier – La første henting av arbeidsgivere eller gjennomføring feile.        | Feilmelding med ny henting, ikke tomme lister som ved slettede data.                                      |      |       |
| 13.2.10 | Utvikler/eier – La automatisk intervjufordeling ved «Neste» feile.                   | Interessesteget beholdes med feil og mulighet for nytt forsøk. Ingen tom, ferdig fordeling.               |      |       |

### 13.3. Samtidige endringer

| #      | Gjør dette                                                                                                  | Forventet resultat                                                                                                                  | ✅❌ | Notat |
| ------ | ----------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 13.3.1 | Eier/medeier – Registrer ulike personer møtt samtidig fra hver sin nettleser.                               | Begge lagres med ulike deltakernumre.                                                                                               |      |       |
| 13.3.2 | Eier/medeier – Flytt A, deretter B fra den andre eierens eldre romvisning.                                  | Begge flyttinger beholdes etter ny henting.                                                                                         |      |       |
| 13.3.3 | Eier/medeier – Flytt samme person til ulike rom etter hverandre.                                            | Siste serverbehandlede flytting gjelder; personen finnes i ett rom.                                                                 |      |       |
| 13.3.4 | Eier/medeier – Vurder ulike person–arbeidsgiver-par samtidig.                                               | Begge vurderinger lagres på riktig par.                                                                                             |      |       |
| 13.3.5 | Eier – Åpne to treff. Endre oppmøte/rom i det ene.                                                          | Det andres personer, numre, rom og vurderinger er uendret.                                                                          |      |       |
| 13.3.6 | Eier/medeier – Fjern oppmøte samtidig med ny interesse for samme person. Prøv begge rekkefølger.            | Først lagrede handling vinner, den andre avvises. Ingen interesse uten oppmøte; begge faner viser bekreftet resultat etter henting. |      |       |
| 13.3.7 | Eier/medeier – Fjern en arbeidsgiver uten registreringer samtidig med ny interesse. Prøv begge rekkefølger. | Avklart sperreregel håndheves ved lagring; ingen nye interesser hos fjernet arbeidsgiver.                                           |      |       |
| 13.3.8 | Eier/medeier – Slett en person i «Lagt til» samtidig med oppmøteregistrering. Prøv begge rekkefølger.       | Først lagrede handling vinner, den andre avvises. Personen er enten slettet uten nytt oppmøte eller møtt og ikke slettet.           |      |       |

## 14. API og integrasjoner

### 14.1. Validering og dataintegritet

| #       | Gjør dette                                                                                                                                            | Forventet resultat                                                                                                           | ✅❌ | Notat |
| ------- | ----------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 14.1.1  | Utvikler – Les/skriv gjennomføring som ikke-eier uten utviklerrolle, borger og bruker uten nødvendig rolle.                                           | Alle avvises uten tilgang til eller endring av data.                                                                         |      |       |
| 14.1.2  | Utvikler – Bruk ukjent treff-ID og person-/arbeidsgiver-ID fra annet treff i oppmøte, rom, interesse og vurdering.                                    | Avvises uten endring i noen av treffene.                                                                                     |      |       |
| 14.1.3  | Utvikler – Flytt uten oppmøte, uten møteplan, til rom 0 og til rom over gjeldende antall.                                                             | Alle avvises uten delvis endring.                                                                                            |      |       |
| 14.1.4  | Utvikler – Registrer interesse og ikke-tom vurdering uten oppmøte.                                                                                    | Begge avvises.                                                                                                               |      |       |
| 14.1.5  | Utvikler – Send intervjufordeling med duplikat i én liste, deretter samme person inkludert og ekskludert.                                             | Begge avvises; tidligere fordeling beholdes.                                                                                 |      |       |
| 14.1.6  | Utvikler – Send ukjent notatkode, ugyldig intervjudato og dato uten avtalt intervju.                                                                  | Alle avvises uten delvis lagring.                                                                                            |      |       |
| 14.1.7  | Utvikler – Send tom vurdering og hent på nytt.                                                                                                        | Vurderingsraden fjernes og sperrer ikke lenger retting. Fjernings-/endringshendelser beholdes.                               |      |       |
| 14.1.8  | Utvikler – Hent nytt gjennomføringsaggregat flere ganger uten endringer.                                                                              | Standardverdier returneres; ingen gjennomføringsrader eller hendelser opprettes.                                             |      |       |
| 14.1.9  | Utvikler – Sett lagret steg fremover, bakover og til samme verdi.                                                                                     | Lagret progresjon går ikke bakover; tidligere steg kan fortsatt åpnes.                                                       |      |       |
| 14.1.10 | Utvikler – Flytt personen til rommet vedkommende allerede står i.                                                                                     | Én forekomst i samme rom og returnert rekkefølge; andre flyttes ikke.                                                        |      |       |
| 14.1.11 | Utvikler – Fremprovoser databasefeil under møteopprettelse eller annen sammensatt lagring.                                                            | Hele operasjonen rulles tilbake; ingen delvis plan, duplikatnummer eller hendelse uten endring.                              |      |       |
| 14.1.12 | Utvikler – Gjenta 6.2 og 6.4 via API, inkludert sletting ved invitert, ja, nei, møtt og «Fått jobb». | Sperret interesse-/oppmøtefjerning gir 409; feil slettestatus gir 422. Oppmøteresponsen oppgir sperreårsak. Ingen dataendringer eller fjernings-/slettehendelser. | | |
| 14.1.13 | Utvikler/eier – Bruk gammel fane/ID etter personsletting. Prøv oppmøte, rom, interesse, intervjufordeling, vurdering og ny sletting. Hent data igjen. | Ingen endring/gjeninnlegging via gjennomføringen, ny slettehendelse eller aktive personrader i API/skjermbilde.              |      |       |

### 14.2. WorkOp-hendelser og miljøsperrer

| #      | Gjør dette                                                                                        | Forventet resultat                                                                                                                              | ✅❌ | Notat |
| ------ | ------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 14.2.1 | Utvikler – Følg WorkOp-invitasjon, endring og svar/status gjennom backend og begge konsumenter.   | `workopinvitasjon`, `workopoppdatering`, `workopSvarOgStatus`, WorkOp-maler og korttype WORKOP brukes, uten ekstra behandling som vanlig treff. |      |       |
| 14.2.2 | Utvikler – Gjenta med vanlig rekrutteringstreff.                                                  | Ordinære hendelser, invitasjoner og kort behandles fortsatt.                                                                                    |      |       |
| 14.2.3 | Utvikler – Kontroller WorkOp-lyttere lokalt, i dev og med produksjons-/ukjent miljøkonfigurasjon. | Aktive lokalt/dev, inaktive i produksjon/ukjent miljø. Vanlige trefflyttere fungerer.                                                           |      |       |
| 14.2.4 | Utvikler – Opprett WorkOp direkte via API med produksjonskonfigurasjon.                           | Avvises før lagring.                                                                                                                            |      |       |

---

**Kjente feil og uavklarte regler**

- **Tilgang:** Flere underressurser mangler WorkOp-eiersjekk, og `/eiere/meg` tillater selvinnmelding. Avklar eierkravet, kontortilgang og tillatte unntak.
- **Svarstatus:** Oppmøte og «Fått jobb» overskriver ja/nei. Tidligere svar må fortsatt styre svarvisning, endrings-/avlysningsvarsler og kortstatus ved avlysning/fullføring. Direkte svarendring må ikke ødelegge oppmøte/formidling.
- **Formidling og oppmøte:** «Fått jobb» telles som møtt, også uten oppmøteregistrering. Oppmøtefjerning kan logges uten faktisk endring. Avklar telling og retting; visning, nummer og historikk må samsvare.
- **Arbeidsgiverfjerning:** Skjulte interesser/vurderinger hos fjernet arbeidsgiver kan sperre oppmøtefjerning uten tilgjengelig retting. Forslaget er å sperre arbeidsgiverfjerning til avklart opprydding, også for usynlige personer/formidlinger. Siste-arbeidsgiver-sperren finnes bare i skjermbildet; API-regelen må avklares.
- **Gjeninnlagt arbeidsgiver:** Tidligere data kan bli synlige igjen. Avklar om de skal gjenopptas eller kreve egen opprydding.
- **Usynlige personer:** Stegene bruker ulike synlighetsfiltre. Avklar visning, registrering, retting, telling og utskrift i alle seks steg, inkludert oppfølgingsunntak.
- **Intervju-API:** Manuell fordeling mangler oppmøte-/interessevalidering. Personer uten gyldig grunnlag må avvises.
- **Samtidighet:** Gamle klientdata kan overskrive endringer på samme vurdering/intervjufordeling. Avklar konflikthåndtering; sletting må heller ikke bruke utdatert oppmøtestatus.
- **Avlyst WorkOp:** Gjennomføringen mangler generell statuskontroll. Avklar hvilke registreringer/rettinger som tillates, likt i skjermbildet og API-et.
- **Meldingsforhåndsvisning:** Bruker vanlige treffmaler. Skal vise WorkOp-meldingen som faktisk sendes.
- **Standardtekst:** Tidspunkter i fritekst oppdateres ikke nødvendigvis med strukturerte felt. Avklar dobbelvisning og ansvar for oppdatering.
- **Notatvalg:** «Helse eller kapasitet» krever avklart behandlingsgrunnlag og godkjent kodeverk før produksjonsbruk.
- **Møtetider:** Avklar grenser for svært lange møter, tider utenfor treffet og døgnskifte; positiv varighet alene begrenser ikke dette.
- **Sletteforklaring:** En aldri invitert fremmøtt kan få teksten «Kan ikke slette jobbsøker som er invitert». Meldingen må forklare oppmøtesperren.
