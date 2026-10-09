# Akseptansetester – WorkOp og treffgjennomføring, runde 2

Retest etter første testrunde. Testnumrene er de samme som i [akseptansetester-workop.md](akseptansetester-workop.md). Numre som mangler, trenger ikke retest.

Runde 2 tar med tester som er endret eller lagt til siden første runde, tester der kommentarene fra første runde er rettet, og tester som berøres av endringer gjort etter første runde. Funnene fra Trello står til slutt, under «T. Funn fra Trello». Testene i del 13.1.6–14 som ikke ble kjørt i første runde, tas i en egen runde.

Marker ✅ eller ❌ og noter avvik. Skriv «Ikke kjørt» ved tester som ikke er gjennomført.

## 1. Oversikt og tilgang

### 1.1. Søk, eiere og direkte lenker

| #     | Gjør dette                                                                                           | Forventet resultat                                                             | ✅❌ | Notat |
| ----- | ---------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------ | ---- | ----- |
| 1.1.1 | Eier – Søk etter eget WorkOp i kladd, publisert, avlyst og fullført status. Bruk dato-/statusfilter. | WorkOp vises i tilhørende filtre med WorkOp-merking. Slettede treff vises ikke og telles ikke i filtrene. |      |       |
| 1.1.3 | Markedskontakt uten eierskap – Åpne direkte lenke til WorkOp. Velg «Legg til meg som medeier» og bekreft. | Forhåndsvisning og selvinnmelding er tilgjengelig. «Finn og foreslå jobbsøkere» og «Legg til jobbsøkere» er skjult før selvinnmelding, også via direkte kandidatlenker og delte dialoger. Gjennomføring og eierredigering er utilgjengelig. Etter bekreftelse åpnes eierfunksjonene, inkludert tillegg av jobbsøkere, uten manuell omlasting. Eksisterende eiere beholdes. |      |       |
| 1.1.4 | Registrert medeier med arbeidsgiverrettet rolle – Åpne WorkOp via direkte lenke.                    | Gjennomføringen kan leses og redigeres. Medeier har samme tilgang som den som opprettet treffet. |      |       |
| 1.1.5 | Utvikler uten eierskap – Søk etter WorkOp og åpne direkte lenke.                                     | Vises i søket og kan åpnes via direkte lenke, med samme tilgang som eier.      |      |       |
| 1.1.6 | Markedskontakt uten eierskap – Åpne direkte lenke til WorkOp. La selvinnmeldingen feile. | En feilmelding vises. Brukeren forblir ikke-eier, med vanlig forhåndsvisning og mulighet til å prøve igjen. Ingen eierfunksjoner åpnes. |      |       |
| 1.1.7 | Markedskontakt/veileder uten eierskap – Åpne WorkOp via direkte lenke og prøv «Formidlinger» og «Opprett formidling», med og uten vanlig rolle-/kontortilgang. | Formidlinger følger samme rolle- og kontorregler som vanlige treff; WorkOp krever ikke eierskap. Med tilgang kan fanen og opprettingsdialogen åpnes. Manglende tilgang og 403 fra API-et respekteres. Tillegg og forslag av jobbsøkere forblir sperret. |      |       |

Bare eiere eller utviklere kan legge til eller foreslå jobbsøkere til WorkOp. Formidlinger omfattes ikke av dette eierskapskravet og følger vanlige rolle- og kontorregler. Eksisterende rollekrav gjelder fortsatt; utviklerrollen beholder tilgang uten medlemskap. Vanlige rekrutteringstreff er uendret. Eventuelle ytterligere begrensninger på innholdet i forhåndsvisningen avklares senere. Muligheten til å legge seg selv til som medeier skal beholdes dersom mer innhold skjules. WorkOp skal fortsatt være skjult i søk for ikke-eiere, som beskrevet i 1.1.2.

## 2. Opprette og publisere WorkOp

### 2.1. Opprettelse og kategori

| #     | Gjør dette                                                       | Forventet resultat                                                                   | ✅❌ | Notat |
| ----- | ---------------------------------------------------------------- | ------------------------------------------------------------------------------------ | ---- | ----- |
| 2.1.1 | Markedskontakt – Velg «Opprett» → «WorkOp» i dev.                | Kladden «WorkOp uten navn» åpnes for redigering med kategori WORKOP og deg som eier. |      |       |
| 2.1.3 | Eier – Åpne redigering før og etter publisering. Utvikler – Send `PUT` med annen kategori. | Redigering har ikke noe kategorifelt. API-et ignorerer kategori ved oppdatering. WorkOp kan ikke byttes til vanlig rekrutteringstreff. |      |       |

### 2.3. Standardtekst og forhåndsvisning

| #     | Gjør dette                                                                                                      | Forventet resultat                                                                                                              | ✅❌ | Notat |
| ----- | --------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 2.3.1 | Eier – Publiser standardteksten. Sammenlign forhåndsvisningen med jobbsøkers invitasjonsside.                               | Navn, tid, sted, introduksjon og eventuell WorkOp-merking samsvarer. Arbeidsgiverlisten er skjult i begge.                      |      |       |

## 3. Jobbsøkerliste, invitasjoner og aktivitetskort

### 3.2. Invitasjon og varselkanaler

| #     | Gjør dette                                                                       | Forventet resultat                                                                                                                          | ✅❌ | Notat |
| ----- | -------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 3.2.2 | Jobbsøker – Les e-postinvitasjonen. Logg inn på Nav og åpne kortet.              | Emnet er «Invitasjon til å treffe arbeidsgivere»; innholdet omtaler WorkOp uten arbeidsgivernavn/interne data. Kortet åpner riktig treff.   |      |       |

### 3.3. Aktivitetskort og lenker

| #     | Gjør dette                                                                        | Forventet resultat                                                                                    | ✅❌ | Notat |
| ----- | --------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------- | ---- | ----- |
| 3.3.1 | Jobbsøker/veileder – Åpne aktivitetsplanen etter invitasjon.                      | Ett felles kort i «Forslag» med riktig tittel, tid, sted, WorkOp-beskrivelse og «Sjekk ut WorkOp-en». |      |       |
| 3.3.3 | Eier – Registrer oppmøte, rom, interesse, vurdering, «2. intervju» og jobbtilbud. | Ingen nye SMS-er, e-poster eller aktivitetskort. Interne notater/vurderinger vises ikke på kortet.    |      |       |

### 3.4. Endring av publisert WorkOp

| #     | Gjør dette                                                                        | Forventet resultat                                                                                              | ✅❌ | Notat |
| ----- | --------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 3.4.1 | Jobbsøker – Les e-posten om endringen.                                            | Emne/innhold omtaler WorkOp. Endrede felt og beskjed om innlogging vises, uten arbeidsgivernavn/interne data.   |      |       |

### 3.5. Avlysning

| #     | Gjør dette                                                               | Forventet resultat                                                                                                        | ✅❌ | Notat |
| ----- | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 3.5.1 | Eier/jobbsøker – Avlys WorkOp med ja-svarere uten oppmøte og les SMS-en. | WorkOp er avlyst; mer informasjon etter innlogging. Ingen arbeidsgivernavn/interne data.                                  |      |       |
| 3.5.2 | Jobbsøker – Les e-posten om avlysningen.                                 | Emne/innhold sier at WorkOp er avlyst, uten vanlig trefftekst, plassholdere, arbeidsgivernavn eller interne data.         |      |       |
| 3.5.3 | Jobbsøker – Les avlysningsvarselet på MinSide og følg lenken.            | WorkOp omtales som avlyst, uten arbeidsgivernavn/interne data. Riktig treff åpnes med avlysningsmelding, uten svarskjema. |      |       |
| 3.5.4 | Jobbsøker/veileder – Åpne aktivitetskortet. Følg lenken som jobbsøker.   | Samme kort er «Avbrutt», med WorkOp-beskrivelse og lenke til det avlyste treffet. Ingen nytt kort.                        |      |       |

## 5. Navigasjon og endringer underveis

### 5.1. Steg og lagrede registreringer

| #      | Gjør dette                                                                                      | Forventet resultat                                                                                         | ✅❌ | Notat |
| ------ | ----------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 5.1.6  | Eier – Sett `visSteg` til tekst, deretter til et ikke-nådd steg. Fjern så registreringene for et steg som allerede er nådd, og åpne det via stegindikatoren og «Neste». | Tekst åpner oppmøte. Et ikke-nådd steg erstattes av nærmeste tilgjengelige steg. Et steg som er nådd én gang, forblir låst opp, både i stegindikatoren og via «Neste». |      |       |

### 5.2. Person- og arbeidsgiverendringer underveis

| #     | Gjør dette                                                                                                                                                                  | Forventet resultat                                                                                                                            | ✅❌ | Notat |
| ----- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 5.2.4 | Eier – Gi en uinvitert fremmøtt interesse hos to arbeidsgivere, intervju/vurdering hos den nye. Prøv å fjerne arbeidsgiveren og oppmøtet.                                   | Oppmøtet sperres. Arbeidsgiveren kan ikke fjernes så lenge den har interesser, intervjufordelinger eller vurderinger, eller personer i startrommet. Registreringene går ikke tapt. |      |       |
| 5.2.7 | Eier/utvikler – Fordel intervjuer. La en fremmøtt med intervju/vurdering bli usynlig. Legg til en ny arbeidsgiver, registrer interesse hos den og endre en annens interesse. | Den usynlige vises fortsatt med navn (se 6.3). Interessen hos den nye arbeidsgiveren kommer med i intervjufordelingen uten «Fordel på nytt». Usynlighet sletter ikke data. |      |       |

## 6. Oppmøte, retting og sletting

### 6.1. Oppmøte, deltakernummer og statuser

| #     | Gjør dette                                                                      | Forventet resultat                                                                                  | ✅❌ | Notat |
| ----- | ------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------- | ---- | ----- |
| 6.1.1 | Eier – Registrer en uinvitert person, en ja-svarer og en nei-svarer møtt. Last siden på nytt. La ja- og nei-svareren åpne treffet på Min side og prøv å svare på nytt. | Alle er møtt med unike numre. Fremmøtetallet øker med tre; ingen invitasjoner eller varsler sendes. Jobbsøkerne ser fortsatt sitt opprinnelige svar, ikke svarskjemaet. Et nytt svar lagres som hendelse, men oppmøtet beholdes. |      |       |
| 6.1.2 | Eier – Fjern oppmøte uten øvrige registreringer. Registrer personen møtt igjen. | Tidligere svarstatus gjenopprettes og rom fjernes. Ved nytt oppmøte beholdes nummeret.              |      |       |
| 6.1.3 | Eier – Endre oppmøte. Kontroller sortering, tellinger og «Møtt opp»-filteret.   | Navnesorteringen beholdes; status, tellinger og filtrerte personer samsvarer.                       |      |       |
| 6.1.4 | Eier – Prøv «Endre svar» for en fremmøtt.                                       | Sperret med beskjed om å fjerne oppmøtet først.                                                     |      |       |

### 6.3. Usynlige jobbsøkere

| #     | Gjør dette                                                                                                                                | Forventet resultat                                                                                                     | ✅❌ | Notat |
| ----- | ----------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 6.3.1 | Eier/utvikler – La en person med gjennomføringsdata forsvinne fra søket og bli bekreftet usynlig. La en annen invitert person bli usynlig før oppmøte. Kontroller jobbsøkerlisten, oppmøte og rom. | Jobbsøkerlisten viser ingen av dem, bare det samlede tallet for skjulte. I treffgjennomføringen vises begge med navn, uten merking. Den som ikke har møtt, kan registreres som møtt og får deltakernummer. Der fødselsnummeret ellers står, står «Ikke tilgjengelig». Ingen varsler sendes. |      |       |
| 6.3.2 | Eier/utvikler – Kontroller interesse, intervjufordeling og vurdering for den usynlige personen. Gjør en endring i hvert steg. | Personen vises med navn i alle steg og i vurderingen. Lagrede registreringer beholdes, og endringer lagres. |      |       |
| 6.3.3 | Eier – Kontroller oppsummering og nye rom-/intervjuutskrifter.                                                                            | Personen telles og vises i oppsummering og utskrifter på samme måte som i gjennomføringen. |      |       |
| 6.3.4 | Eier/utvikler – Gjør personen synlig igjen og åpne gjennomføringen på nytt.                                                               | Samme lagrede data og deltakernummer, uten duplikater eller varsler.                                                   |      |       |
| 6.3.5 | Eier/utvikler – La én person være både usynlig og slettet, en annen synlig og slettet, og en tredje usynlig med adressebeskyttelse. Kontroller jobbsøkerlisten, alle steg og fanen «Hendelser». | De slettede vises ikke i jobbsøkerlisten eller i stegene. I «Hendelser» vises den synlige slettede med navn og fødselsnummer, også hvem som slettet hen. Den usynlige slettede vises ikke der. Personen med adressebeskyttelse vises aldri med navn; har hen gjennomføringsdata, vises hen som «Ukjent jobbsøker», også i «Hendelser». |      |       |
| 6.3.6 | Eier – La en person svare selv, bli usynlig og få registreringer. Åpne fanen «Hendelser». | Hendelsene vises med navn og detaljer, og med «Ikke tilgjengelig» i stedet for fødselsnummer. Under «Utført av» står «Jobbsøker» for svaret, ikke fødselsnummeret. |      |       |
| 6.3.7 | Utvikler – Skru på treffgjennomføring for et vanlig treff (ikke WorkOp) i dev, og la en invitert person bli usynlig. Kontroller jobbsøkerlisten, treffgjennomføringen og «Hendelser». | Personen vises ikke noe sted, bare i det samlede tallet for skjulte. Hendelsene for personen vises ikke, og ingen hendelser har detaljer. Vanlige treff er uendret til regelen er avklart i ROS-en. |      |       |

Regel (se WO-14 i ROS-en): Usynlige personer vises ikke i jobbsøkerlisten, bare i det samlede tallet for skjulte. I treffgjennomføringen, vurderingen og fanen «Hendelser» vises de med navn, uten merking. Fødselsnummeret deres vises ikke, verken i treffgjennomføringen eller i «Hendelser». Der står «Ikke tilgjengelig». API-et gir bare id, navn og status for dem. Usynlige som ikke har møtt, kan registreres som møtt. Slettede vises ikke i jobbsøkerlisten eller i stegene. I «Hendelser» vises synlige som er slettet, som på vanlige treff, men ikke usynlige som er slettet. Personer med adressebeskyttelse vises som «Ukjent jobbsøker». Regelen gjelder bare WorkOp. På vanlige treff er usynlige skjult også i treffgjennomføringen og «Hendelser», som før.

### 6.4. Sletting og gjeninnlegging

| #     | Gjør dette                                                                                             | Forventet resultat                                                                                                                | ✅❌ | Notat |
| ----- | ------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 6.4.3 | Eier – Rydd og slett siste person på et eget WorkOp, uten tidligere invitasjon.                        | Tomme lister, tellinger null. Møteplan og rom beholdes. Steg som allerede er nådd, kan fortsatt åpnes via stegindikatoren og «Neste». |      |       |
| 6.4.4 | Eier – Legg til en ny person, deretter personen fra 6.4.1. Registrer begge møtt.                       | Ny person får nytt nummer; gjeninnlagt person beholder sitt. Ingen duplikater eller gjenopprettede interesser/vurderinger.        |      |       |

## 7. Møteplan, rom og rotasjon

### 7.1. Opprette og endre møteplan

| #      | Gjør dette                                                                               | Forventet resultat                                                                                         | ✅❌ | Notat |
| ------ | ---------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 7.1.1  | Eier – Åpne møteoppsettet første gang.                                                   | Start 10:00, varighet 10 minutter. Antall rom settes automatisk lik antall arbeidsgivere; det finnes ikke noe felt for antall rom. |      |       |
| 7.1.3  | Eier – Prøv tom starttid, 24:00 og ugyldige minutter.                                    | Feltet godtar bare gyldige klokkeslett (HH:mm); nettleseren hindrer eller retter ugyldige verdier. Tom starttid gir feilmelding. |      |       |

### 7.2. Flytting og endret oppmøte

| #     | Gjør dette                                                                            | Forventet resultat                                                         | ✅❌ | Notat |
| ----- | ------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- | ---- | ----- |
| 7.2.1 | Eier – Flytt en person med romvalget.                                                 | Bare personen flyttes. Statusen viser «Lagrer …» mens lagringen pågår, og «Lagret» først når serveren har bekreftet. |      |       |

### 7.3. Arbeidsgiverendringer etter romfordeling

| #     | Gjør dette                                                                                                              | Forventet resultat                                                                                                          | ✅❌ | Notat |
| ----- | ----------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 7.3.2 | Eier – Bruk separate WorkOp med fem arbeidsgivere og manuell romfordeling. Prøv å fjerne midterste og siste arbeidsgiver i rotasjonen. Flytt så personene ut av startrommet, fjern eventuelle registreringer og fjern arbeidsgiveren igjen. | Fjerningen sperres så lenge startrommet har personer eller arbeidsgiveren har registreringer. Etterpå: fire arbeidsgivere/rom, uten kollisjoner. Øvrige plasseringer, oppmøte og numre beholdes. | | |
| 7.3.3 | Eier – Flytt en person etter fjerningen. Last siden på nytt og åpne rom-/rotasjonsutskriftene. | Flyttingen beholdes. Hver fremmøtt finnes én gang i gyldig rom; fjernet arbeidsgiver er utelatt.                            |      |       |

### 7.4. Færre og flere enn fem arbeidsgivere

| #     | Gjør dette                                                                                                                         | Forventet resultat                                                                                              | ✅❌ | Notat |
| ----- | ---------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 7.4.5 | Utvikler – Send møteoppsett via API for et WorkOp med fremmøtte, men uten arbeidsgivere. Legg til én arbeidsgiver og send igjen. Eier – Kontroller at «Opprett møteplan» er sperret uten arbeidsgiver. | Første forsøk avvises med 400; andre gir ett rom og én runde. Dekkes også av komponenttest. |      |       |
| 7.4.6 | Eier/utvikler – Prøv å fjerne siste arbeidsgiver fra møteplan med fremmøtte uten interesser/vurderinger, i skjermbildet og API-et. | Skjermbildet krever ny arbeidsgiver først. API-et svarer 409 «Treffet må alltid ha en arbeidsgiver som deltar. Legg til en ny arbeidsgiver først.», uten endring. Gjelder alle treff. |      |       |

## 8. Interesse

### 8.1. Registrering og retting

| #      | Gjør dette                                                                                                     | Forventet resultat                                                                             | ✅❌ | Notat |
| ------ | -------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- | ---- | ----- |
| 8.1.3  | Eier – Fjern alle interesser før intervjufordelingen er nådd, og prøv «Neste». Gjenta etter at intervjufordelingen er nådd. | Første gang er «Neste» sperret. Når intervjufordelingen allerede er nådd, er «Neste» åpen, som i stegindikatoren. |      |       |

## 9. Intervjufordeling

### 9.1. Rekkefølge, utvalg og konflikter

| #      | Gjør dette                                                          | Forventet resultat                                                                                       | ✅❌ | Notat |
| ------ | ------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 9.1.3  | Eier – Endre rekkefølge med dra-og-slipp.                           | Rekkefølgen lagres. Fokus settes på den flyttede personens flytteknapp, så tastaturbetjeningen kan fortsette derfra. |      |       |
| 9.1.15 | Eier – Fordel mange interesserte hos én arbeidsgiver.               | Alle interesserte vises. Utvalg og rekkefølge kan endres manuelt; det finnes ingen automatisk begrensning. |      |       |

### 9.2. Arbeidsgiverendringer etter intervjufordeling

| #     | Gjør dette                                                                                                                 | Forventet resultat                                                                                                                                                                              | ✅❌ | Notat |
| ----- | -------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 9.2.2 | Eier – Prøv å fjerne en arbeidsgiver med intervjuer/vurderinger. Fjern registreringene, tøm startrommet og fjern igjen. | Første forsøk sperres med beskjed om hva som må ryddes. Etter fjerning: ingen aktive rader/utskrifter for arbeidsgiveren, gyldige rom og uendret oppmøte/nummer. Ingen automatisk intervjuoverføring eller endring hos andre. |      |       |

## 10. Vurdering og oppfølging

### 10.2. Avtalt intervju og jobbtilbud

| #      | Gjør dette                                                                     | Forventet resultat                                                                       | ✅❌ | Notat |
| ------ | ------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------- | ---- | ----- |
| 10.2.3 | Eier – Skriv 31.02.2027 og forlat feltet. Velg deretter en dato tilbake i tid. | Ugyldig dato gir valideringsfeil, og tidligere lagret dato beholdes. Dato tilbake i tid er tillatt, slik at intervjuer kan etterregistreres. |      |       |

### 10.3. Formidling og nullstilling

| #      | Gjør dette                                                                            | Forventet resultat                                                                                | ✅❌ | Notat |
| ------ | ------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- | ---- | ----- |
| 10.3.5 | Eier – Formidle samme person til to arbeidsgivere. Angre den ene, deretter den andre. | Statusen er «Fått jobb» til begge er angret. Deretter gjelder oppmøtet eller det nyeste svaret.   |      |       |
| 10.3.6 | Eier – Formidle en person som ikke er registrert som møtt. Åpne oppmøtet.             | Personen har «Fått jobb», men står ikke som møtt. Registrert oppmøte står når personen får jobb.   |      |       |

## 12. Utskrift og tastaturbruk

### 12.2. Tastatur, zoom og liten skjerm

| #      | Gjør dette                                                                    | Forventet resultat                                                                      | ✅❌ | Notat |
| ------ | ----------------------------------------------------------------------------- | --------------------------------------------------------------------------------------- | ---- | ----- |
| 12.2.4 | Eier – Bruk 200 % zoom og smalt vindu i alle seks steg/dialoger.              | Felter, feil og knapper er tilgjengelige; brede tabeller kan rulles. I smalt vindu åpnes stegindikatoren med knappen «Steg X av 6». |      |       |
| 12.2.5 | Eier – Endre data med treg eller blokkert lagring (se oppskriften i 13.2). | Statusen viser «Lagrer …», «Lagret» og «Lagringsfeil». Radene er sperret mens lagringen pågår, og flytter seg ikke under klikk. |      |       |

Oppskrift for 12.2.5 (fra 13.2): Åpne DevTools (F12) → «Network». Velg «Slow 3G» under throttling for treg lagring, eller «Offline» for å få lagringsfeil. For feil på bare én type lagring: høyreklikk forespørselen (for eksempel `…/treffgjennomforing/oppmote`) og velg «Block request URL». Fjern blokkeringen etterpå.

## 13. Hendelser, lagringsfeil og samtidighet

### 13.1. Hendelser

| #      | Gjør dette                                                               | Forventet resultat                                                                               | ✅❌ | Notat |
| ------ | ------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ | ---- | ----- |
| 13.1.1 | Eier – Registrer og fjern oppmøte. Åpne hendelsene på jobbsøkeren og i fanen «Hendelser». | To hendelser med hvem/når begge steder. Registreringen viser deltakernummer som detalj; i «Hendelser» identifiseres personen med navn og fødselsnummer under «Gjelder». Opprinnelig hendelse beholdes. |      |       |
| 13.1.2 | Eier – Endre «Aktuell» til «Ikke aktuell». Åpne hendelsene.              | Lesbar hendelse med arbeidsgiver, tidligere og ny vurdering, også i fanen «Hendelser». |      |       |
| 13.1.3 | Eier – Legg til og fjern arbeidsgiver- og jobbsøkernotat.                | Alle endringer vises med lesbar notattekst og riktig part, ikke tekniske koder, også i fanen «Hendelser». |      |       |
| 13.1.5 | Eier – Opprett møteplan, endre møteoppsett og fordel intervjuer på nytt. | Treffhendelsene «Møteplan opprettet», «Møteoppsett endret» og «Intervjuer fordelt». |      |       |

## T. Funn fra Trello

Funnene kom utenfor testskriptet og har derfor egne numre. Kolonnen «Gjør dette» viser hvilken test funnet hører til.

### T.1. Rettede funn

| #   | Gjør dette | Forventet resultat | ✅❌ | Notat |
| --- | ---------- | ------------------ | ---- | ----- |
| T.1 | Eier – Åpne notatvalget i steg 5 «Vurdering og oppfølging» med så mange valg at listen får rullefelt. Dra i rullefeltet med musen. Velg og fjern notater med mus og med tastatur. Se 10.1.4 og 10.1.5. | Listen holder seg åpen når du bruker rullefeltet. Notater kan velges og fjernes både med mus og tastatur. |      |       |
| T.2 | Eier – Fokuser eller hold musepekeren over «Slett» for en person som har møtt opp uten invitasjon, og for en invitert person. Se 6.4.1 og 6.4.2. | Hjelpeteksten er «Kan ikke slette jobbsøker som har møtt opp» for den fremmøtte og «Kan ikke slette jobbsøker som er invitert» for den inviterte. |      |       |
| T.3 | Eier – Legg til fem arbeidsgivere på et WorkOp og fjern to. Sammenlign antallet arbeidsgivere i trefflisten med antallet i treffet. Kontroller også treffet `0bffcc4b-a9d6-4c73-bc4a-c78e5fd3f6d5` fra første runde. | Begge steder viser tre. Fjernede arbeidsgivere telles ikke i trefflisten. |      |       |
| T.4 | Eier – Gi én arbeidsgiver et navn som går over to linjer. Åpne steg 2 «Rom og rotasjon» i bredt vindu. | Jobbsøkerlistene i romkortene starter på samme høyde. Navn som går over mer enn to linjer kan fortsatt gi ujevn høyde. |      |       |
| T.5 | Eier – Åpne steg 2 «Rom og rotasjon» med 200 % zoom og arbeidsgivere med lange navn. Kontroller romkortene og rotasjonstabellen. | Listen er lesbar. Arbeidsgivernavnene i rotasjonstabellen brytes over flere linjer og vises i sin helhet, uten «…». |      |       |
| T.6 | Eier – Bruk 200 % zoom i steg 2 «Rom og rotasjon». Flytt fokus med Tab fra «Utskrift til jobbsøkere». | Fokus går ikke til arbeidsgivernavnene i rotasjonstabellen, men videre til neste knapp eller lenke. Se 12.2.4. |      |       |
| T.7 | Eier – Bruk 200 % zoom i smalt vindu i steg 3 «Interesse». Flytt fokus med Tab og Shift+Tab mellom avkrysningsboksene, også mot høyre i tabellen. Se 12.2.1. | Navnet til jobbsøkeren står synlig ved siden av den fokuserte avkrysningsboksen, både for korte og lange navn. |      |       |

Avklarte og utsatte funn fra Trello trenger ingen retest:

- «Møtt opp» hindrer invitasjon. Utsatt til etter pilottreffene. Se «Invitasjon etter oppmøte» under «Kjente begrensninger og avklarte regler».
- Usynlige jobbsøkere i romfordelingen. Avklart: de er med på WorkOp. Se 6.3.
- WorkOp vises ikke for utvikler. Rettet og dekket av 1.1.5.

## Hvorfor testene er med

| Test | Grunn |
| --- | --- |
| 1.1.1 | Endret etter spørsmålet om slettede treff. Slettede treff vises ikke og telles ikke. |
| 1.1.3 | Endret: forhåndsvisning og selvinnmelding som medeier via direkte lenke. |
| 1.1.4 | Endret etter spørsmålet om hovedansvarlig. Medeier har samme tilgang som den som opprettet treffet. |
| 1.1.5 | Endret: utvikler ser WorkOp i søket. |
| 1.1.6, 1.1.7 | Nye: selvinnmelding som feiler, og formidlinger uten eierskap. |
| 2.1.3 | Endret etter spørsmålet om hvordan kategoribytte skal prøves. |
| 2.1.1 | Regresjon: «Opprett» viser nå en dialog om formål. |
| 2.3.1, 3.2.2 | Regresjon: ny forhåndsvisning av e-post. |
| 3.3.1, 3.5.1–3.5.4, 6.1.2–6.1.4 | Regresjon: status og svar er skrevet om. Det påvirker svar, aktivitetskort og avlysning. |
| 3.3.3, 3.4.1 | Bare kontrollert i malene i runde 1. Bør kjøres med ekte meldinger før pilot. |
| 6.3.4 | Regresjon: reglene for usynlige er endret. |
| 6.4.4 | Regresjon: tellingen når en person legges til på nytt er rettet. |
| 5.1.6, 6.4.3, 8.1.3 | «Neste» var sperret selv om steget var besøkt før. Rettet. |
| 5.2.4, 7.3.2, 7.3.3, 9.2.2 | Endret: arbeidsgiveren kan først fjernes når startrommet er tomt og registreringene er fjernet. |
| 5.2.7 | ❌ i første runde: interesse hos ny arbeidsgiver kom ikke med i intervjufordelingen. Rettet. |
| 6.1.1 | Nei-svarer som møtte opp, så ut som «ikke svart» på Min side. Rettet. |
| 6.3.1–6.3.3 | Endret: usynlige vises med navn i treffgjennomføringen og kan registreres (WO-14). |
| 6.3.5–6.3.7 | Nye: usynlige og slettede i hendelser, og usynlige på vanlige treff. |
| 7.1.1, 7.1.3 | Endret etter spørsmålene om antall rom og ugyldige minutter. |
| 7.2.1 | Endret etter spørsmålet om når «Lagret» vises. |
| 7.4.5, 7.4.6 | Endret: møteplan uten arbeidsgiver testes via API, og siste arbeidsgiver kan ikke fjernes. |
| 9.1.3 | Fokus hoppet til toppen av siden etter flytting. Rettet. |
| 9.1.15 | Endret: venteliste og kalenderavtale er tatt ut av testen. |
| 10.2.3 | Endret: dato tilbake i tid er tillatt, slik at intervjuer kan etterregistreres. |
| 10.3.5, 10.3.6 | Nye: «Fått jobb» med flere formidlinger og uten oppmøte. |
| 12.2.4 | Stegindikatoren forsvant ved 200 % zoom. Rettet. |
| 12.2.5 | Endret: oppskrift for treg og blokkert lagring. |
| 13.1.1 | Endret: hvor deltakernummer, navn og fødselsnummer vises i hendelsene. |
| 13.1.2, 13.1.3, 13.1.5 | Hendelsene manglet detaljer, og møteplanen hadde ingen hendelse. Rettet. |

---

**Kjente begrensninger og avklarte regler**

Resultatene fra første testrunde står i [testresultater-workop.md](testresultater-workop.md).

- **Tilgang:** Avklart 09.10.26. Direkte lenke gir vanlig forhåndsvisning og mulighet for selvinnmelding via `/eiere/meg`, med gjeldende rollekrav og kontortilknytning. Jobbsøkerliste, jobbsøkersøk, tillegg av jobbsøkere, arbeidsgiverbehov, gjennomføring og eierredigering krever eierskap eller utviklerrolle i API-et. Utvikler ser alle WorkOp, også i søket. Eierne får ikke beskjed om selvinnmelding (WO-12).
- **Svarstatus:** Statusen og svaret utledes fra hendelsene på ett sted (`Jobbsøkerstatusregler`). Oppmøte og «Fått jobb» går foran ja/nei i statusfeltet, mens svaret alltid er det nyeste. Et svar etter oppmøte endrer svaret, men statusen er fortsatt «Møtt opp». Fjernes oppmøtet, blir statusen det nyeste svaret. Min side, `erPåmeldt`, varsel om endringer og avslutning av treffet bruker det nyeste svaret. Når treffet fullføres, får alle inviterte med «Møtt opp» eller «Fått jobb» «Fullført» på aktivitetskortet, uansett svar. Det gjelder også den som svarte nei og møtte likevel. Kortet går da fra «Avbrutt» til «Fullført», og det er ønsket (avklart 09.10.26). Den som er registrert møtt uten invitasjon, har ikke aktivitetskort.
- **Formidling og oppmøte:** Avklart: «Fått jobb» telles ikke som møtt uten oppmøteregistrering, fordi oppmøte ikke er obligatorisk. Fremmøtt følger siste oppmøtehendelse. «Fått jobb» står til siste aktive formidling er angret. Tellingene for «svart ja» endres ikke nå. Å fjerne et oppmøte som ikke er registrert, gjør ingenting. Se `docs/9-planer/jobbsoker-statuser.md`.
- **Invitasjon etter oppmøte:** Den som er registrert som møtt uten invitasjon, kan ikke inviteres etterpå. Det er bevisst, fordi invitasjonen sender SMS. Utsatt til etter pilottreffene (09.10.26).
- **Arbeidsgiverfjerning:** Skjermbildet og API-et sperrer fjerning når startrommet har personer, eller når arbeidsgiveren har interesser, intervjufordelinger eller vurderinger. Det gjelder også usynlige personer, som nå vises og kan ryddes. Siste arbeidsgiver kan ikke fjernes, heller ikke via API-et (409). Formidlinger sperrer ikke fjerning og beholdes.
- **Gjeninnlagt arbeidsgiver:** Avklart 09.10.26, godtatt slik det er. Arbeidsgiveren kan bare fjernes når interesser, intervjufordeling og vurderinger er ryddet. Ved gjeninnlegging med behov reaktiveres samme rad, og behovet fra skjemaet overskriver det gamle. Formidlinger blir synlige igjen.
- **Usynlige personer:** Avklart 01.10.26, se 6.3 og WO-14 i ROS-en. Aktivitetskortet avsluttes for usynlige på samme måte som for andre (se «Svarstatus»).
- **Samtidighet:** Alle skriveoperasjoner i gjennomføringen tar trefflåsen og bygger på lagret tilstand, og sletting bruker lagret oppmøtestatus (`TransaksjonTest` og `TreffgjennomføringTransaksjonTest`). En gammel fane kan likevel overskrive samme vurdering eller intervjufordeling, så lenge dataene er gyldige. Siste skriving vinner. Optimistisk låsing er en mulig senere oppgave (09.10.26).
- **Avlyst WorkOp:** Gjennomføringen har ingen statuskontroll og kan endres etter avlysning og fullføring. Ingen sperre nå (09.10.26).
- **Standardtekst:** Tidspunkter i fritekst oppdateres ikke med de strukturerte feltene. Tidspunkter for formøte og hovedmøte i innlegget vurderes ikke nå (09.10.26).
- **Notatvalg:** «Individuelle forutsetninger eller kapasitet» (tidligere «Helse eller kapasitet») krever avklart behandlingsgrunnlag og godkjent kodeverk før produksjonsbruk. Følges opp i egen oppgave.
- **Møtetider:** Backend krever `HH:mm` og minst ett minutt. Ingen grenser for lange møter, tider utenfor treffet eller døgnskifte nå (09.10.26).
