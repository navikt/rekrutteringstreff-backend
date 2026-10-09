# Testresultater for WorkOp og treffgjennomføring

Gjennomgang 09.10.26 av første manuelle testrunde i [akseptansetester-workop.md](akseptansetester-workop.md) og Trello-kortet «Vurdering og oppfølging». Hvert avvik, spørsmål og kommentar fra testerne er sjekket mot koden på `main`:

| Repo | Commit |
| --- | --- |
| rekrutteringstreff-backend | `2dcab0a4` Fiks transaksjonslåsing (#231) |
| rekrutteringsbistand-frontend | `655f64fad` Fiks legg til på nytt tellingsfeil (#521) |
| rekrutteringstreff-bruker | `fc6d9f9` |
| rekrutteringsbistand-kandidatvarsel-api | `9a47a81` |

Feilene og testhullene fra gjennomgangen er fulgt opp 09.10.26 på grenen `workop-testresultater` i backend og frontend. Se «Feil fra testrunden» og «Ikke kjørt manuelt».

«OK ifølge implementasjonen» betyr at koden og de automatiske testene støtter forventet resultat. Det betyr ikke at punktet er retestet manuelt. Alle punkter merket «Retest» bør kjøres på nytt i dev.

## Status i korte trekk

- Feilen fra testrunden er rettet: arbeidsgivernavn i steg 2 tar ikke lenger tabulatorfokus ved 200 % zoom (G1).
- Det estetiske punktet om lange arbeidsgivernavn i romkortene er rettet (G2).
- Testteksten i 1.1.5 er rettet: utvikler skal se WorkOp i søket (G3).
- De uavklarte reglene er avklart 09.10.26. Ingen av dem krever kodeendring før pilot.
- Den eneste testen merket ❌ (5.2.7) er dekket av ny komponenttest og bør retestes.
- Alle øvrige avvik og spørsmål fra testrunden er rettet, besvart i testteksten eller avklart.
- Testene i 13.1.6–13.1.7, 13.2, 13.3 og 14 er ikke kjørt manuelt. Hullene i den automatiske dekningen er tettet. 13.2 er dekket av Playwright. 14.2.1 bør kjøres én gang ende til ende i dev, fordi flyten går gjennom flere apper.

## Feil fra testrunden

| # | Kilde | Funn | Status |
| --- | --- | --- | --- |
| G1 | Trello, steg 2 ved 200 % zoom | Etter «Utskrift til jobbsøkere» gikk tabulatoren til første arbeidsgiver i siste rom og deretter til samme arbeidsgiver i de andre rommene. Årsaken var at `AvkortetTekst` ga avkortede arbeidsgivernavn `tabIndex=0`, slik at tooltipen med fullt navn kunne åpnes med tastatur. | Rettet, retest. Rotasjonsmatrisen (`Rotasjonsmatrise.tsx`) bryter navnene over flere linjer i stedet for å avkorte dem. Cellene har ingen tabulatorstopp, og hele navnet er synlig. Ny test i `zoom-200.spec.ts`: `rotasjonsmatrisa viser hele arbeidsgivernavnet uten egne tabulatorstopp`. |
| G2 | Trello, estetisk | Lange arbeidsgivernavn brøt over to linjer i romkortet («Starter her: …», `Romkort.tsx`), slik at jobbsøkerlistene startet på ulik høyde. | Rettet, retest. Linjen har plass til to linjer i rutenettet, så listene starter på samme høyde. Navn over to linjer gir fortsatt ujevn høyde. Ny test i `zoom-200.spec.ts`: `romkortene viser startarbeidsgiveren uten å forskyve jobbsøkerlistene`. |
| G3 | Akseptansetest 1.1.5 | Testen forventet at WorkOp var skjult i søket for utvikler uten eierskap. Backend viser andres WorkOp til utvikler (`RekrutteringstreffSokRepository.byggWorkOpSynlighetCondition`). | Avklart 09.10.26: utvikler skal se alle WorkOp. Forventet resultat i 1.1.5 er endret til «Vises i søket og kan åpnes via direkte lenke». |

Frontend-testene for G1 og G2 er skrevet, men ikke kjørt ennå. Kjør `zoom-200.spec.ts` før merge.

## Avklarte regler

Avklart 09.10.26.

| # | Tema | Beslutning | Dagens oppførsel |
| --- | --- | --- | --- |
| U1 | Avlyst og fullført WorkOp | Ingen statussperre nå. | Gjennomføringen kan endres etter avlysning og fullføring. `Treffkontekst.kt` sjekker kategori og miljø, ikke treffstatus. |
| U2 | Jobbsøker som har svart nei og deretter møtt opp | Kortet skal fullføres når treffet fullføres, selv om det sto i «Avbrutt». Oppmøtet teller. | Slik virker det. `Jobbsøkerstatusregler.hendelseNårTreffetFullføres` gir `SVART_JA_TREFF_FULLFØRT` for inviterte med `MØTT_OPP` eller `FÅTT_JOBB`, uansett svar. Det samme gjelder «Ja → Møtt → Nei» og «Nei → Fjern svar → Møtt». |
| U3 | Samtidighet | Optimistisk låsing er en mulig senere oppgave. | #231 la inn transaksjoner og radlåser, men ikke versjonskontroll. En gammel fane kan overskrive samme vurdering eller intervjufordeling, så lenge dataene er gyldige. Siste skriving vinner. |
| U4 | Gjeninnlagt arbeidsgiver | Godtatt slik det er. | Arbeidsgiveren kan bare fjernes når startrommet er tomt og interesser, intervjufordeling og vurderinger er ryddet. Ved gjeninnlegging med behov reaktiveres samme rad med samme ID og hendelsen `REAKTIVERT`. Behovet fra skjemaet overskriver det gamle. Arbeidsgiveren kommer tilbake i rotasjonen med eget rom. Formidlinger sperrer ikke fjerning og blir synlige igjen. |
| U5 | Møtetider | Ingen grenser nå. | Backend krever `HH:mm` og minst ett minutt. |
| U6 | Standardtekst | Tidspunkter for formøte og hovedmøte i innlegget vurderes ikke nå. | Friteksten oppdateres ikke når de strukturerte feltene endres. |
| U7 | Notatvalget «Helse eller kapasitet» | Følges opp i egen oppgave. | `JS_HELSE_KAPASITET` finnes. Skriving til WorkOp-stegene er sperret i produksjon. |
| U8 | Selvinnmelding som medeier | I samsvar med WorkOp-reglene. | Ikke-eier med arbeidsgiverrettet rolle ser forhåndsvisningen via direkte lenke og kan legge seg til (`PUT /eiere/meg`). Jobbsøkerliste, jobbsøkersøk, tillegg av jobbsøkere, arbeidsgiverbehov og gjennomføring krever eierskap eller utviklerrolle. Etter selvinnmelding har personen samme tilgang som andre eiere. Restrisikoen i WO-12 gjelder fortsatt: eierne får ikke beskjed. |
| U9 | Invitasjon etter oppmøte | Utsatt til etter pilottreffene. | Den som er registrert møtt uten invitasjon, kan ikke inviteres. |

Mindre punkter som ikke krever beslutning før pilot:

- 10.2.3: Dato for «2. intervju» kan settes tilbake i tid. Testteksten sier nå at det er tillatt, slik at intervjuer kan etterregistreres. Treffdato som tidligste dato er et alternativ.
- 5.1.6 og 8.1.3: Stegindikatoren åpner steg 2 når noen har møtt opp. «Neste» fra steg 1 krever i tillegg minst én arbeidsgiver. Forskjellen er liten, fordi et publisert treff alltid har arbeidsgiver.
- 12.2.5: Lagringsstatusen viser «Lagret» også før første endring. Det betyr at ingen endringer venter.

## OK ifølge implementasjonen

### Rettet etter testrunden, bør retestes

Tidspunktet for testrunden er ikke registrert. Commit-kolonnen viser hvor oppførselen ble innført, ikke nødvendigvis at den kom etter testen.

| Test | Tilbakemelding | Commit | Hva koden gjør nå |
| --- | --- | --- | --- |
| 5.2.7 ❌ | Interesse hos ny arbeidsgiver kom ikke med i intervjufordelingen («0 med») uten «Fordel på nytt». | Backend #224 og #226 | `MatchingService` speiler nye interesser inn i arbeidsgiverens fordeling. En ny arbeidsgiver starter med tom fordeling. Bare denne arbeidsgiverens rader erstattes. Dekket av `interesse for arbeidsgiver lagt til etter fordelingen speiles inn i fordelingen` og `interesse registrert etter fordelingen speiles inn i eksisterende og ny fordeling`. |
| 5.1.6, 6.4.3, 8.1.3 | «Neste» var sperret selv om senere steg var besøkt. Stegindikatoren slapp gjennom. | Frontend #519 | Et steg som er nådd én gang, forblir åpent både i stegindikatoren og via «Neste». Ikke-nådde steg i `visSteg` erstattes av nærmeste tilgjengelige steg. Dekket av `navigasjon.spec.ts` og `stegtilgjengelighet.spec.ts`. |
| 6.1.1 | Nei-svarer som møtte opp, så ut som «ikke svart» på Min side. | Backend #229 | Svar og status utledes hver for seg. Min side får `harSvart=true` og `erPåmeldt=false`, og viser «Jeg blir ikke med». Dekket av `JobbsøkerstatusreglerTest`. |
| 9.1.3 | Fokus hoppet til toppen etter dra og slipp. | Frontend | Fokus settes på flytteknappen til personen som ble flyttet, både etter dra og slipp og etter flytteknappene (`useFokusEtterLagring`). Dekket av `intervjufordeling.spec.ts`. |
| 13.1.2 | Hendelsen het bare «Vurdert». | Backend #224, frontend #517 | Hendelsen viser arbeidsgiver og overgangen, for eksempel «aktuell → ikke aktuell». |
| 13.1.3 | Notater vistes som «Notat lagt til». | Backend #224, frontend #517 | Notatkoden oversettes til lesbar tekst med part, for eksempel «Arbeidsgiveren: godt inntrykk». |
| 13.1.5 | Ingen hendelse for opprettet møteplan. | Backend #224 | Treffhendelsene «Møteplan opprettet», «Møteoppsett endret» og «Intervjuer fordelt» skrives og vises. |
| 12.2.4 | Stegindikatoren forsvant ved 200 % zoom. | Frontend #516 | I smalt vindu åpner knappen «Steg X av 6» stegindikatoren. Dekket av `zoom-200.spec.ts`. |

### Trello-kortet

| Punkt | Status | Grunnlag |
| --- | --- | --- |
| Notatlisten i steg 5 lukket seg ved bruk av rullefeltet, og valg med mus virket ikke. | OK, retest | `Vurderingsnotatvelger.tsx` hindrer at popoveren lukkes ved klikk på rullefeltet. Alternativene er avkrysningsbokser som kan velges med mus (frontend `81f406b57`). |
| «Møtt opp» hindrer invitasjon. | Utsatt | Se U9. |
| WorkOp vises ikke for utvikler. | OK | Utvikler regnes som eier i frontend og ser WorkOp i søk og via lenke. Testteksten i 1.1.5 er rettet (G3). |
| Upresis hjelpetekst når deltaker ikke kan slettes. | OK, retest | `JobbsokerKortValg.tsx` viser «har møtt opp» for `MØTT_OPP` og ellers «er invitert». |
| Lange arbeidsgivernavn i romfordelingen. | Rettet, retest | Se G2. |
| Antall arbeidsgivere i trefflisten tok med slettede. | OK, retest | Søke-viewet teller bare arbeidsgivere med `status != 'SLETTET'` (`655bb179`). |
| Arbeidsgiverlisten ved 200 % zoom. | Rettet, retest | Rotasjonsmatrisen bryter navnene over flere linjer (G1), og romkortene har lik høyde på startlinjen (G2). |
| Tabulator ved 200 % zoom i steg 2. | Rettet, retest | Se G1. |
| Tabulator ved 200 % zoom i steg 3: navnet forsvant. | OK, retest | Navnekolonnen i interessematrisen er sticky, og `scroll-padding` holder fokusert avkrysning fri av den. Dekket av `zoom-200.spec.ts` (frontend #516). Retest på smal skjerm, slik det ble observert. |
| Skal usynlige jobbsøkere være med i romfordelingen? | Avklart | Ja for WorkOp. Usynlige kan registreres som møtt, fordeles på rom, telles og står med initialer i utskriften. Slettede og adressebeskyttede er ikke med (WO-14, [synlighet.md](../3-sikkerhet/synlighet.md)). |

### Spørsmål fra testerne som er besvart i testteksten

Testteksten er oppdatert for disse punktene. Ingen kodeendring var nødvendig.

| Test | Spørsmål | Svar |
| --- | --- | --- |
| 1.1.1 | Skal slettede treff telles i noen kategori? | Nei. Søke-viewet filtrerer bort `SLETTET`, og alle tellinger kommer fra viewet. |
| 1.1.4 | Finnes det en hovedansvarlig? | Nei. Medeier har samme tilgang som den som opprettet treffet. |
| 2.1.3 | Hvordan prøver man å bytte kategori? | Redigering har ikke kategorifelt. API-et ignorerer kategori ved `PUT`. |
| 6.3.2 | Sperres nye registreringer for usynlige? | Nei. Usynlige på WorkOp vises med navn og kan registreres og rettes som andre (WO-14). |
| 7.1.1 | Hva betyr «uten eget antallsfelt»? | Antall rom settes lik antall arbeidsgivere. Det finnes ikke noe felt for antall rom. |
| 7.1.3 | Ugyldige minutter ble rettet til 59. | Feltet er nettleserens klokkeslettfelt, som retter verdien. Skjemaet og API-et avviser alt annet enn `HH:mm`. |
| 7.2.1 | Vises «Lagret» før serveren har svart? | Nei. Romvalget venter på svaret, og statusen viser «Lagrer …» så lenge. |
| 7.3.2, 7.3.3 | Rommet må tømmes før arbeidsgiveren kan fjernes. | Testen beskriver nå sperren og oppryddingen før fjerning. |
| 7.4.5 | Lar dette seg teste? | Testen bruker nå API-et, og en komponenttest dekker det. |
| 7.4.6 | Siste arbeidsgiver kan ikke fjernes. | API-et svarer også 409. |
| 13.1.1 | Hendelsen viste navn og fødselsnummer, ikke deltakernummer. | Deltakernummeret er detalj på oppmøtehendelsen. Navn og fødselsnummer identifiserer personen under «Gjelder». Fødselsnummeret skjermes for usynlige (#226, #519). |
| 9.1.15 | Venteliste og kalenderavtale finnes ikke. | Testteksten nevner dem ikke lenger. |

### Bekreftet i testrunden

Øvrige tester i del 1–12 er merket ✅ uten avvik. 3.3.3, 3.4.1, 3.5.1 og 3.5.2 er kontrollert i malene, ikke ved å sende meldinger. Disse bør kjøres i dev før pilot. Ut over det trengs bare vanlig regresjonstest.

## Ikke kjørt manuelt

Disse testene er ikke kjørt manuelt. Tabellen viser hva de automatiske testene i backend og frontend dekker. Testene merket «ny» er lagt til 09.10.26.

| Tester | Automatisk dekning |
| --- | --- |
| 13.1.6 | `TreffgjennomføringKomponentTest`: `alle hendelser for treffet tar med detaljene for jobbsøkerhendelser` og ny `vurdering skriver hendelser bare på jobbsøkeren, ikke på arbeidsgiveren`. |
| 13.1.7 | `gjentatt registrering av samme oppmøte gir ingen ny hendelse`, `interesse er idempotent ved gjentakelse` og ny `identisk vurdering sendt på nytt gir ingen nye hendelser` i `TreffgjennomføringKomponentTest`. |
| 13.2.1–13.2.10 | Playwright i `tests/rekrutteringstreff/treffgjennomføring/e2e/` i frontend: `lagringsutfall`, `oppmøte`, `interesse`, `rom-og-rotasjon`, `vurdering-og-oppfølging`, `intervjufordeling` og `datagrunnlag`. Testene dekker ventende lagring, avvist lagring, tapt svar og «Hent på nytt». |
| 13.3.1 | `TransaksjonTest`: `oppmøte venter på trefflåsen og teller med deltakernummer som ble tildelt mens den ventet`. |
| 13.3.2–13.3.4 | `TreffgjennomføringTransaksjonTest`, ny: romflytting fra eldre visning, samme person til ulike rom og vurdering av ulike par. |
| 13.3.5 | `TreffgjennomføringKomponentTest`: operasjoner med id-er fra et annet treff endrer ingen av treffene (ny, se 14.1.2). |
| 13.3.6–13.3.8 | `TreffgjennomføringTransaksjonTest`, ny: begge rekkefølger for oppmøtefjerning mot interesse, arbeidsgiversletting mot interesse og jobbsøkersletting mot oppmøte. |
| 14.1.1 | `TreffgjennomføringAutorisasjonsTest` |
| 14.1.2 | `TreffgjennomføringKomponentTest`, ny: `ukjent treff avvises for alle skriveoperasjoner i gjennomføringen` og `person og arbeidsgiver fra et annet treff avvises uten endring i noen av treffene`. |
| 14.1.3–14.1.5 | `TreffgjennomføringKomponentTest`: flytting, interesse og vurdering uten oppmøte, intervjufordeling. |
| 14.1.6 | `TreffgjennomføringKomponentTest`, ny: `ugyldig vurdering avvises uten delvis lagring` med ukjent notatkode, ugyldig dato, feil datoformat og dato uten avtalt intervju. |
| 14.1.7–14.1.10 | `TreffgjennomføringKomponentTest` |
| 14.1.11 | `TreffgjennomføringTransaksjonTest`, ny: databasefeil når møteplanen opprettes og når oppmøtet registreres. Hele operasjonen rulles tilbake, og neste forsøk lykkes uten hull i deltakernumrene. |
| 14.1.12 | `JobbsøkerstatusPermutasjonKomponentTest` og sperretestene. |
| 14.1.13 | `TreffgjennomføringKomponentTest`, ny: `slettet jobbsøker kan ikke endres eller slettes på nytt via gamle id-er`. |
| 14.2.1–14.2.2 | API: `JobbsøkerhendelserSchedulerTest` sender `workopinvitasjon`, `workopoppdatering` og `workopSvarOgStatus`. Aktivitetskort: `RekrutteringstreffInvitasjonTest` og nye WorkOp-tester i `RekrutteringstreffSvarOgStatusLytterTest` og `RekrutteringstreffOppdateringTest` (kortet beholder WorkOp-type og -tekst). Kandidatvarsel-API har egne lyttere og maler med tester. Kjør én gang ende til ende i dev. |
| 14.2.3 | Kandidatvarsel-API `MainTest`: lyttere av i prod og ukjent miljø, på i dev og lokalt. |
| 14.2.4 | `RekrutteringstreffServiceTest`: `Skal ikke kunne opprette WorkOp i prod`. |

De nye backendtestene gikk grønt uten kodeendring. Reglene var altså på plass, men manglet test.

## Dokumentasjon

Oppdatert 09.10.26:

- [akseptansetester-workop.md](akseptansetester-workop.md): 1.1.5 er rettet. «Kjente feil og uavklarte regler» heter nå «Kjente begrensninger og avklarte regler». Tilgang, svarstatus og U1–U9 er oppdatert, og «Sletteforklaring» er fjernet.
- [ros-workop.md](../9-planer/workop/ros-workop.md), WO-06: ny status for tilgangskontrollen. WO-12 om selvinnmelding gjelder fortsatt.
- [transaksjoner.md](../2-arkitektur/transaksjoner.md): beskriver de nye samtidighets- og rollbacktestene i `TreffgjennomføringTransaksjonTest`. `TransaksjonTest` tester fortsatt bare låsemekanismen.

Gjenstår:

- [workop-gjenstående.md](../9-planer/workop/workop-gjenstående.md) er en eldre arbeidsliste. WorkOp-maler, aktivitetskortkategori og infobokser er på plass. Listen bør ryddes eller slettes av den som eier den.
