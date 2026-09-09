# Manuelle akseptansetester – WorkOp og treffgjennomføring

Testscenarier for domeneeksperter før pilot og produksjonssetting. Testene følger arbeidet før, under og etter et WorkOp, og dekker også forskjellene fra vanlige rekrutteringstreff.
## Testmiljø

| System | URL (dev) | Brukes av | Også kalt |
| --- | --- | --- | --- |
| rekrutteringsbistand | rekrutteringsbistand.intern.dev.nav.no | Veileder, Markedskontakt, Utvikler | Intern løsning |
| rekrutteringstreff-bruker | rekrutteringstreff.ekstern.dev.nav.no | Jobbsøker | Treffsiden |
| Aktivitetsplan (veileder) | veilarbpersonflate.intern.dev.nav.no | Veileder | Aktivitetskort og dialog |
| Aktivitetsplan (bruker) | aktivitetsplan.ekstern.dev.nav.no | Jobbsøker | Aktivitetskort og dialog |
| MinSide | min-side.dev.nav.no | Jobbsøker | MinSide-varsel |

## Slik gjennomføres testene

Test slik det er beskrevet. Fyll inn ✅ eller ❌ og noter avvik. Hvis en test ikke kan gjennomføres, skriv «Ikke kjørt» og årsaken i Notat.

## Testdata og begreper

**Roller:** Bruk en markedskontakt som eier treffet, en medeier, en markedskontakt uten eierskap, en veileder uten eierskap og en utvikler. Bruk separate nettleserprofiler for eier og medeier i samtidighetstestene.

**Hovedtreff:** Opprett et WorkOp med fem arbeidsgivere og 30 synlige jobbsøkere. I gjennomføringsdelen registreres 25 som møtt; invitasjons- og svartestene kjøres først. Ha personer som bare er lagt til, inviterte uten svar, personer som har svart ja og personer som har svart nei. Fordel kontaktopplysningene slik at SMS, e-post og MinSide uten ekstern kontaktinformasjon kan prøves. Bruk et vanlig rekrutteringstreff som kontroll.

**Egne grensetreff:** Klargjør treff uten deltakere, uten arbeidsgivere, med én arbeidsgiver og med fire/seks arbeidsgivere. Bruk små, separate treff til tester av svarfrist, start, slutt, avlysning og fullføring.

**Deltakernummer** identifiserer en fremmøtt innenfor ett WorkOp. **Interesse** er jobbsøkerens ønske om å møte en arbeidsgiver. **Intervjufordeling** er rekkefølgen og utvalget til speedintervju. **Vurdering**, **avtalt intervju**, **jobbtilbud** og **formidling** er ulike opplysninger; ett av dem skal ikke uten videre tolkes som et annet.

**Omfang:** Testene bygger på kildekoden i `rekrutteringsbistand-frontend`, `rekrutteringstreff-backend`, `rekrutteringstreff-bruker` og `rekrutteringsbistand-kandidatvarsel-api`.

---

## 1. Tilgang, trefftype og miljø

**Hvor:** rekrutteringsbistand → treffoversikten og et WorkOp

**Hva skjer:** WorkOp er en egen kategori. Intern treffoversikt viser WorkOp til eiere, ikke automatisk til alle på samme kontor. Utviklerens direkte tilgang er ikke det samme som synlighet i søket.

### Oversikt, eiere og direkte lenker

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 1.1.1 | Eier – Søk etter eget WorkOp i kladd, publisert, avlyst og fullført status. Bruk passende dato-/statusfilter. | Eget WorkOp finnes i de relevante filtrene og er tydelig merket «WorkOp». Slettede treff vises ikke. | | |
| 1.1.2 | Markedskontakt og veileder uten eierskap – Gjenta søket, også med «Alle», eget kontor og valgte kontorer. | WorkOp vises ikke, selv om personen tilhører treffets kontor. Vanlige tilgjengelige rekrutteringstreff kan fortsatt finnes. | | |
| 1.1.3 | Markedskontakt uten eierskap – Åpne direkte lenke til et WorkOp. | Treffet er ikke tilgjengelig. Det vises ikke gjennomføringsdata eller redigeringshandlinger. | | |
| 1.1.4 | Medeier med arbeidsgiverrettet rolle – Åpne et WorkOp som medeier (evt. åpne et og bli medeier først). | Medeier kan lese og redigere gjennomføringen som eier. Ingen særskilt hovedansvarlig må velges. | | |
| 1.1.5 | Utvikler uten eierskap – Søk etter WorkOp, og åpne deretter en kjent direkte lenke. | WorkOp filtreres bort i søket uten eierskap, men direkte tilgang tillates av utviklerrollen. | | |
| 1.1.7 | Eier – Fjern en medeier uten utviklerrolle, og la den tidligere medeieren åpne og endre gjennomføringen på nytt. | Den tidligere medeieren mister tilgang. En gammel åpen fane gir ikke fortsatt skrivetilgang. | | |
| 1.1.9 | Eier – Åpne et slettet testtreff via gammel lenke. | Slettet-melding vises uten faner eller redigeringshandlinger. | | |

> **Viktig:** Direkte tilgang til underressurser og selvinnmelding som medeier har egne avvikstester i seksjon 16.1. At hovedsiden er skjult, er ikke tilstrekkelig tilgangskontroll.

### Miljø og forskjellen fra vanlige treff

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 1.2.1 | Eier – Åpne publisert WorkOp i dev. | Gjennomføringsfanen vises med seks steg når API-et gir tilgang. | | |
| 1.2.2 | Eier – Åpne et vanlig rekrutteringstreff i dev. | Gjennomføringsfanen er skjult. WorkOp-funksjonene gjøres ikke tilgjengelige ved bare å endre faneparameter i URL-en. | | |
| 1.2.5 | Utvikler – Kall interesse- og vurderings-API for vanlig treff i dev, og for begge kategorier med produksjonskonfigurasjon. | Lagring avvises. Bare lokal utvikling tillater disse registreringene på vanlige treff; produksjon avviser også WorkOp. | | |
| 1.2.6 | Utvikler – Kall møteoppsett, romflytting og intervjufordeling på vanlig treff, også lokalt. | Kallene avvises fordi disse operasjonene krever kategorien WORKOP. | | |

---

## 2. Opprette, klargjøre og publisere WorkOp

**Hvor:** rekrutteringsbistand → «Opprett» → «WorkOp» → redigering

**Hva skjer:** Valget oppretter en kladd med WorkOp-kategori og åpner redigeringen. Fem arbeidsgivere og 25 jobbsøkere er planleggingsinformasjon, ikke harde kapasitetsgrenser i dagens løsning.

### Opprettelse, autolagring og kategori

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 2.1.1 | Markedskontakt – Velg «Opprett» → «WorkOp» i dev. | Kladd med «WorkOp uten navn» opprettes, du blir eier og redigeringssiden åpnes. Treffet har kategori WORKOP. | | |
| 2.1.2 | Veileder uten arbeidsgiverrettet rolle – Åpne opprettingsmenyen. | WorkOp-valget er ikke tilgjengelig. | | |
| 2.1.3 | Eier – Fyll inn navn, tid, svarfrist, sted og introduksjon. Vent på lagring, lukk og åpne kladden igjen. | Verdiene er bevart. Oppfylt sjekkliste og lagringsstatus gjenspeiler de lagrede opplysningene. | | |
| 2.1.4 | Eier – Prøv å bytte WorkOp til vanlig rekrutteringstreff under redigering og etter publisering. | Det finnes ikke et fritt kategoribytte. Vanlig oppdatering endrer ikke kategorien. | | |
| 2.1.5 | Eier – Opprett et vanlig rekrutteringstreff som kontroll. | Treffet får vanlig kategori, ikke WorkOp-merking eller WorkOp-regler ved en feil. | | |
| 2.1.6 | Eier – Åpne sletting av en egen kladd uten jobbsøkere. Avbryt først, og bekreft deretter sletting. | Avbryt beholder kladden. Bekreftelse fjerner den fra oversikten; gammel lenke gir slettet-visning. | | |

### Arbeidsgivere, antallsinformasjon og innhold

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 2.2.1 | Eier – Åpne arbeidsgiverdelen med null og deretter fire arbeidsgivere. | WorkOp-informasjonen sier «Det skal planlegges for 5 arbeidsgivere i et WorkOp møte.» | | |
| 2.2.2 | Eier – Legg til arbeidsgiver nummer fem og deretter nummer seks. | Informasjonsboksen for færre enn fem forsvinner. Nummer seks kan legges til; fem er ikke en maksimumsgrense. | | |
| 2.2.3 | Eier – Fjern en arbeidsgiver slik at antallet går fra fem til fire. | Informasjonsboksen vises igjen. Bare den valgte arbeidsgiveren fjernes. | | |
| 2.2.4 | Eier – Søk opp testarbeidsgiver med navn og organisasjonsnummer, og legg til både før og etter publisering. | Riktig virksomhet knyttes til treffet. Den er tilgjengelig som intern arbeidsgiver i gjennomføringen, men skal ikke vises på jobbsøkerens treffside. | | |
| 2.2.5 | Eier – Åpne jobbsøkerfanen med færre enn, nøyaktig og flere enn 25 personer. | WorkOp-informasjonen om å planlegge for 25 jobbsøkere vises uansett antall. Det er mulig å legge til flere enn 25. | | |
| 2.2.6 | Eier – Åpne samme arbeidsgiver-/jobbsøkervisninger på et vanlig treff. | De WorkOp-spesifikke infoboksene vises ikke. | | |

### Publisering og forhåndsvisning

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 2.3.1 | Eier – Opprett et WorkOp og sammenlign forhåndsvisning med jobbsøkerens faktiske treffside. | Navn, tider, sted og introduksjon samsvarer. Arbeidsgiverlisten skal ikke avsløres på den faktiske WorkOp-treffsiden; varslenes forhåndsvisning har egen avvikstest i seksjon 16.3. | | |

---

## 3. Jobbsøkerliste, invitasjoner og aktivitetskort

**Hvor:** rekrutteringsbistand → «Jobbsøkere», SMS/e-post, MinSide og aktivitetsplan

**Hva skjer:** Å legge til noen er ikke det samme som å invitere dem. Invitasjonen utløser varsel og aktivitetskort. Leveringsstatus for et varsel er ikke jobbsøkerens ja-/nei-svar.

### Invitasjon og varselkanaler
*Inviter noen jobbsøkere som eier før de neste testene.*

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 3.2.3 | Jobbsøker med SMS som varselkanal – Motta WorkOp-invitasjonen, logg inn på Nav som meldingen ber om, og åpne invitasjonen. | SMS omtaler WorkOp og ber om ja-/nei-svar. Invitasjonen på MinSide leder til riktig treff. SMS-malen trenger ikke inneholde en direkte trefflenke og inneholder ikke arbeidsgivernavn, oppmøte, vurderinger eller notater. | | |
| 3.2.4 | Jobbsøker med e-post som varselkanal – Motta invitasjonen, logg inn på Nav og åpne kortet som meldingen viser til. | Emnet er «Invitasjon til å treffe arbeidsgivere», mens innholdet omtaler WorkOp. Kortet leder til riktig treff bak innlogging; intern gjennomføringsinformasjon er ikke med i e-posten. | | |
| 3.2.5 | Jobbsøker uten ekstern kontaktinformasjon – Logg inn på MinSide. | WorkOp-invitasjonen finnes på MinSide og lenker til riktig treff. Manglende SMS/e-post er ikke det samme som at invitasjonen mangler. | | |
| 3.2.6 | Eier – Følg varselstatus etter ferdig behandlet invitasjon via SMS, e-post og bare MinSide. | Kanal/leveringsstatus samsvarer med faktisk behandling. Personen har fortsatt ikke svart ja eller nei bare fordi varslet er levert. | | |
| 3.2.7 | Eier/Jobbsøker – Inviter en kontrollperson til vanlig rekrutteringstreff. | Vanlig treff bruker trefftekst, ikke WorkOp-tekst. Lenke og invitasjonsflyt virker fortsatt. | | |

### Aktivitetskort og påminnelse

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 3.3.1 | Jobbsøker og veileder – Åpne aktivitetsplanen etter WorkOp-invitasjon. | Ett kort finnes i «Forslag», med riktig tittel, tid og sted, WorkOp-beskrivelse og «Sjekk ut WorkOp-en». Begge visninger gjelder samme aktivitet. | | |
| 3.3.2 | Jobbsøker – Følg «Sjekk ut WorkOp-en» fra aktivitetskortet. | Riktig treffside åpnes etter eventuell innlogging, med invitasjonen til denne personen. | | |
| 3.3.3 | Utvikler – Kontroller den utgående aktivitetskortmeldingen for WorkOp og kontrolltreffet. | Korttypene er henholdsvis WORKOP og REKRUTTERINGSTREFF. WorkOp bruker type og beskrivelse, ikke en ekstra etikett i `etiketter`-listen, som er tom. | | |
| 3.3.4 | Eier – Registrer oppmøte, romplassering, interesse, vurdering, «2. intervju» og jobbtilbud. | Disse handlingene sender ikke hver for seg nye SMS-er, e-poster eller aktivitetskort. Interne notater og vurderinger overføres ikke til aktivitetskortet. | | |

---

## 4. Jobbsøkeren leser og svarer

**Hvor:** treffsiden, åpnet fra MinSide eller aktivitetskort etter eventuell varsling på SMS/e-post

**Hva skjer:** Jobbsøkeren får felles treffinformasjon og kan svare på sin egen invitasjon. WorkOp skjuler arbeidsgiverlisten. Flere overskrifter og svardialogen bruker fortsatt generelle trefftekster.

### Innhold og skjuling av arbeidsgivere

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 4.1.1 | Jobbsøker – Åpne lenken uten å være innlogget, og logg deretter inn som invitert testperson. | Innlogging kreves før personlig svarstatus vises. Etter innlogging åpnes riktig treff. | | |
| 4.1.2 | Jobbsøker – Les et WorkOp med fem registrerte arbeidsgivere. | Treffnavn, tid, sted og introduksjon vises. Arbeidsgiverlisten er skjult; interne vurderinger, interesser og romfordeling vises ikke. | | |
| 4.1.4 | Jobbsøker – Åpne et vanlig rekrutteringstreff som kontroll. | De registrerte arbeidsgiverne vises etter vanlig trefflogikk. WorkOp-skjulingen rammer ikke kontrolltreffet. | | |
| 4.1.6 | Jobbsøker – Åpne en gyldig trefflenke som en testperson uten invitasjon. | Personen får ikke en annen persons svarstatus. Det vises informasjon om manglende invitasjon og kontakt med veileder, ikke et svarskjema for den inviterte. | | |
| 4.1.7 | Jobbsøker – Åpne en ukjent treff-ID. | En forståelig ikke-funnet-/feilmelding vises, ikke et tomt treff som kan besvares. | | |

## 5. Åpne gjennomføringen og navigere mellom steg

**Hvor:** rekrutteringsbistand → WorkOp → «Treffgjennomføring og oppfølging»

**Hva skjer:** Stegene viser oppmøte, rom og rotasjon, interesse, intervjufordeling, vurdering og oppfølging, og oppsummering. Forutsetningene bestemmer hvilke steg som kan åpnes. Å gå til oppsummeringen er ikke det samme som å fullføre treffet.

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 5.1.1 | Eier – Åpne gjennomføringen på et nytt, publisert WorkOp. | «Oppmøte» vises først. Alle seks stegnavn vises, men steg uten nødvendig grunnlag er utilgjengelige. | | |
| 5.1.2 | Eier – Prøv neste-knappen i oppmøtesteget uten fremmøtte, og deretter uten arbeidsgivere. | Knappen er deaktivert til minst én jobbsøker har møtt og minst én arbeidsgiver finnes. Å åpne romsteget via stegnavigasjonen omgår ikke kravene for å opprette møteplanen. | | |
| 5.1.3 | Eier – Registrer oppmøte, men ikke opprett møteplan. Prøv å åpne «Interesse». | WorkOp krever møteplan/romfordeling før interessesteget kan åpnes. | | |
| 5.1.4 | Eier – Opprett møteplan uten interesser. Prøv å åpne intervjufordeling og vurdering. | Stegene som krever interesse, er fortsatt utilgjengelige. | | |
| 5.1.5 | Eier – Gå frem og tilbake mellom tilgjengelige steg. Last siden på nytt. | Lagrede opplysninger er bevart. Du kan besøke tidligere steg uten å nullstille senere registreringer. | | |
| 5.1.6 | Eier – Kopier lenken til et tilgjengelig steg og åpne den i en ny fane. | Samme steg og et komplett, oppdatert datagrunnlag vises. Det kreves ikke at oppmøtesidene først er besøkt. | | |
| 5.1.7 | Eier – Sett `visSteg` til tekst som ikke er et tall, og prøv deretter et steg som ennå ikke er nådd og mangler forutsetninger. | Ikke-tall gir oppmøtesteg. Et utilgjengelig steg korrigeres til et tilgjengelig steg; du får ikke tom eller redigerbar visning uten grunnlag. | | |
| 5.1.8 | Eier – Start lagring og prøv straks «Neste», «Tilbake» og stegnavigasjonen. | Stegbytte er sperret mens lagringen pågår. Etter bekreftet lagring kan du navigere igjen. | | |
| 5.1.9 | Eier – Gå til oppsummeringen, gå tilbake og korriger en tidligere registrering. | Korrigering er mulig når feltets egne forutsetninger er oppfylt. Oppsummeringen låser ikke gjennomføringen eller setter treffstatus til «Fullført». | | |
| 5.1.10 | Eier – Nå et senere steg, rydd deretter interesser/vurderinger og gå tilbake til det nådde steget. | Allerede nådde steg er fortsatt tilgjengelige. Lagret progresjon går ikke automatisk bakover ved retting; et tomt steg skal ikke vise slettede registreringer. | | |

---

## 6. Registrere og korrigere oppmøte

**Hvor:** gjennomføringen → «Oppmøte»

**Hva skjer:** Oppmøte registreres individuelt, uavhengig av om personen har svart på invitasjonen. WorkOp gir fremmøtte et deltakernummer. Oppmøte må ikke forveksles med svar på invitasjonen eller formidling.

### Oppmøte, deltakernummer og statuser

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 6.1.1 | Eier – Åpne oppmøte med 30 synlige jobbsøkere, hvor 25 er registrert møtt. | Listen viser alle 30, med oppmøte avkrysset på de 25. Telleren viser 25 møtt av 30 påmeldte; «påmeldte» er listetotalen, ikke bare de som svarte ja. | | |
| 6.1.2 | Eier – Registrer én person som bare er lagt til, én invitert uten svar, én med ja-svar og én med nei-svar som møtt. | Alle fire kan registreres møtt. Bare den aktuelle personens oppmøte endres for hvert valg. | | |
| 6.1.3 | Eier – Registrer de første personene møtt på et WorkOp uten tidligere oppmøte. | Hver får et eget deltakernummer. To personer får ikke samme nummer på treffet. | | |
| 6.1.4 | Eier – Fjern oppmøte uten interesser eller vurderinger, og registrer samme person møtt igjen. | Oppmøtet fjernes og kan registreres igjen. Personen får tilbake sitt opprinnelige deltakernummer. | | |
| 6.1.5 | Eier – Fjern oppmøtet til deltaker 1, og registrer deretter en ny person møtt. | Nummer 1 gjenbrukes ikke på den nye personen. Andre personers numre endres ikke. | | |
| 6.1.6 | Eier – Registrer oppmøte på flere personer i forskjellig navnerekkefølge. | Oppmøtelisten forblir sortert på navn. Avkrysning flytter ikke personen til en annen plass eller side. | | |
| 6.1.7 | Eier – Fjern oppmøtet til en person uten videre registreringer. | Personen blir stående i oppmøtelisten med tom avkrysning, men tas ut av fremmøttegrunnlaget og romfordelingen. Andre personer beholdes. | | |
| 6.1.8 | Eier – Bytt til «Jobbsøkere» etter en oppmøteendring, og bruk filteret «Møtt opp». | Statusmerke, filter og tellinger gjenspeiler oppmøtet. Jobbsøkerfanen har ikke egne handlinger for å registrere/fjerne oppmøte. | | |
| 6.1.9 | Eier – Prøv «Endre svar» for en fremmøtt person i jobbsøkerfanen. | Handlingen er sperret med forklaring om å fjerne oppmøtet i gjennomføringen først. | | |
| 6.1.10 | Eier – Se etter «Marker alle møtt» i oppmøtesteget. | Ingen masseavkrysning for oppmøte tilbys. Vanlig kandidatmarkering for invitasjoner er en annen handling. | | |
| 6.1.11 | Eier – Åpne senere steg med en fremmøtt person som har status «Fått jobb». | Personen inngår i gjennomføringsgrunnlaget sammen med øvrige fremmøtte, uten duplikat. Formidling er ikke det samme som avkrysningen «Jobbtilbud». | | |

### Sperrer og riktig rekkefølge ved retting

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 6.2.1 | Eier – Registrer interesse og gå tilbake for å fjerne personens oppmøte. | Oppmøteavkrysningen er låst. Forklaringen opplyser at interesse må fjernes først. | | |
| 6.2.2 | Eier – Prøv å fjerne oppmøte når personen har vurdering, notat, avtalt intervju eller jobbtilbud. Prøv opplysningstypene hver for seg. | Oppmøtet er låst også når bare én slik registrering finnes. Løsningen forklarer at registreringene må nullstilles først. | | |
| 6.2.3 | Eier – Nullstill alle vurderingsfelter hos alle berørte arbeidsgivere, fjern interessene og fjern deretter oppmøtet. | Rettingen kan fullføres i denne rekkefølgen. Personen forsvinner fra videre gjennomføringsgrunnlag; andre personers registreringer består. | | |
| 6.2.4 | Eier – Nullstill vurderingen hos én arbeidsgiver, men behold den hos en annen. Prøv å fjerne oppmøtet. | Oppmøtet er fortsatt låst. Alle sperrende registreringer for personen må være ryddet, ikke bare den sist åpnete arbeidsgiveren. | | |
| 6.2.5 | Eier – Registrer oppmøte og fjern det igjen før øvrige registreringer, først for en som svarte ja og deretter for en som svarte nei. | Opprinnelig svarstatus vises igjen etter fjerning: ja for den første, nei for den andre. Det registreres ikke et nytt ja-/nei-svar av oppmøtehandlingen. | | |

---
## 7. WorkOp – møteoppsett, rom og rotasjon

**Hvor:** gjennomføringen → «Rom og rotasjon»

**Hva skjer:** Det opprettes ett rom per arbeidsgiver. Jobbsøkerne fordeles på rom, mens arbeidsgiverne roterer. Møtetidene styrer rotasjonsplanen, ikke en grense for hvor mange intervjuer som kan settes opp.

### Opprette og endre møteplan

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 7.1.1 | Eier – Åpne møteoppsettet første gang. | «Starttidspunkt» er 10:00 og «Varighet per møte (min)» er 10. Antall rom avledes fra arbeidsgiverne, ikke fra et manuelt antallsfelt. | | |
| 7.1.2 | Eier – Opprett møteplan med fem arbeidsgivere og 25 fremmøtte. | Fem rom opprettes med fem jobbsøkere i hvert. Hver fremmøtt er plassert én gang. | | |
| 7.1.3 | Eier – Opprett møteplan med tre arbeidsgivere og sju fremmøtte. | Fordelingen er jevn: romstørrelsene er 3, 2 og 2. Ingen personer mangler eller finnes i flere rom. | | |
| 7.1.4 | Eier – Opprett møteplan med én arbeidsgiver og flere fremmøtte. | Ett rom opprettes med alle fremmøtte og én rotasjonsrunde. | | |
| 7.1.5 | Eier – Prøv tomt starttidspunkt, 24:00 og ugyldige minutter. | Ugyldig klokkeslett kan ikke lagres som møteoppsett. En gyldig verdi skal ha formatet HH:mm. | | |
| 7.1.6 | Eier – Prøv varighet 0, negativ verdi og desimaltall; prøv deretter 1 minutt. | Bare positive, hele minutter godtas. Ett minutt kan lagres. | | |
| 7.1.7 | Eier – Flytt personer manuelt, velg «Rediger møteoppsett», endre til 09:00 og 15 minutter og lagre. | Nye tider vises i rotasjonsplanen. Manuelle romplasseringer, interesser, intervjufordeling og vurderinger består. | | |
| 7.1.8 | Eier – Endre møteoppsettet, men velg «Avbryt». | Lagrede tider og romplasseringer er uendret. | | |
| 7.1.9 | Eier – Bruk fem arbeidsgivere, start 10:00 og varighet 10 minutter. Les hele rotasjonsplanen. | Fem runder går fra 10:00–10:10 til 10:40–10:50. Hver arbeidsgiver besøker hvert rom én gang, uten to arbeidsgivere i samme rom samtidig. | | |
| 7.1.10 | Eier – Sammenlign møteoppsettet med treffets ordinære start- og sluttid. | Møteoppsettet har egne tider. Endring av møtelengde flytter ikke selve treffets tidspunkt og sender ikke endringsvarsel til inviterte. | | |

### Flytting, omfordeling og endret oppmøte

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 7.2.1 | Eier – Flytt én person til et annet rom med romvalget ved personen. | Bare denne personen bytter rom. Endringen vises som lagret når serveren har bekreftet den. | | |
| 7.2.2 | Eier – Flytt en annen person med dra-og-slipp. Last siden på nytt. | Flyttingen er bevart og gir samme resultat som romvalget. Ingen personer dupliseres. | | |
| 7.2.3 | Eier – Flytt en person til et rom som har deltakere med både lavere og høyere deltakernummer. | Rommet vises i serverens rekkefølge med stigende deltakernummer, også etter ny åpning; flyttet person skal ikke nødvendigvis stå sist. | | |
| 7.2.4 | Eier – Registrer en ny fremmøtt etter at romplanen er opprettet og personer er flyttet manuelt. | Den nye personen plasseres i et rom med færrest personer. Eksisterende manuelle plasseringer beholdes. | | |
| 7.2.5 | Eier – Flytt den nye fremmøtte direkte til et annet rom. | Personen kan flyttes selv om første plassering var automatisk beregnet. Andre personers plasseringer består. | | |
| 7.2.6 | Eier – Fjern oppmøtet til en person som ikke har sperrende registreringer. | Bare personens plassering fjernes. Resten av romfordelingen stilles ikke om i det skjulte. | | |
| 7.2.7 | Eier – Fjern alt oppmøte på et eget treff uten interesser/vurderinger. Registrer deretter én person møtt igjen. | Møteoppsett og tomme rom beholdes. Den nye fremmøtte får romplassering, og møteplanen trenger ikke opprettes på nytt. | | |
| 7.2.8 | Eier – Flytt flere personer manuelt og velg «Fordel på nytt». Avbryt dialogen. | Dialogen forklarer at manuelle plasseringer erstattes. Avbryt bevarer dem. | | |
| 7.2.9 | Eier – Bekreft «Fordel på nytt» etter manuelle flyttinger. | Alle fremmøtte fordeles jevnt på nytt. Interesser, intervjufordeling og vurderinger slettes ikke av romomfordelingen. | | |
| 7.2.10 | Eier – Legg til en arbeidsgiver etter opprettet møteplan, og åpne gjennomføringen på nytt. | Ny arbeidsgiver og ett ekstra rom inngår i planen og rotasjonen. Eksisterende deltakere blir ikke duplisert. | | |
| 7.2.11 | Eier – Fjern en arbeidsgiver på et eget testtreff med møteplan og åpne den på nytt. | Arbeidsgiveren tas ut. Rom og rotasjon tilpasses gjenværende arbeidsgivere; deltakere fra rom som bortfaller, får gyldig plassering. | | |

---

## 8. Registrere og fjerne interesse

**Hvor:** gjennomføringen → «Interesse»

**Hva skjer:** Matrisen kobler fremmøtte jobbsøkere til arbeidsgiverne de ønsker å møte. Flere kryss betyr flere interesser, ikke flere jobbsøkere.

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 8.1.1 | Eier – Åpne interessesteget med både fremmøtte og ikke-fremmøtte. | Bare fremmøtte inngår som rader. Aktive arbeidsgivere vises som kolonner. | | |
| 8.1.2 | Eier – Kryss av én arbeidsgiver for én person. Last siden på nytt. | Riktig person–arbeidsgiver-par er avkrysset og lagret. Radens totaltall er 1. | | |
| 8.1.3 | Eier – Kryss av tre arbeidsgivere for samme person og én for en annen. | Radene viser henholdsvis 3 og 1 interesser. De andre cellene er uendret. | | |
| 8.1.4 | Eier – Fjern en interesse uten vurdering hos arbeidsgiveren. | Krysset og interessen fjernes. Personen tas også ut av denne arbeidsgiverens intervju-/ekskluderingsliste, men ikke fra andre arbeidsgivere. | | |
| 8.1.5 | Eier – Fjern alle interesser og prøv «Neste». | Du kan ikke gå videre uten minst én interesse. | | |
| 8.1.6 | Eier – Registrer interesser og gå videre på WorkOp første gang. | Intervjufordelingen klargjøres fra interessene. Du kommer til intervjufordeling når lagringen/fordelingen er ferdig. | | |
| 8.1.7 | Eier – Lag manuell intervjurekkefølge, gå tilbake til interesse og deretter frem igjen uten endringer. | Eksisterende rekkefølge blir ikke automatisk fordelt på nytt ved stegnavigasjon. | | |
| 8.1.8 | Eier – Prøv å fjerne interesse når jobbsøker-arbeidsgiver-paret har vurdering, notat, avtalt intervju eller jobbtilbud. Prøv feltene hver for seg. | Krysset er låst med forklaring om å nullstille registreringen i steg 5 først. | | |
| 8.1.9 | Eier – Sett vurderingen til «Ingen vurdering», men behold et notat eller jobbtilbud. | Interessen er fortsatt låst. Å tømme bare vurderingsvalget nullstiller ikke hele registreringen. | | |
| 8.1.10 | Eier – Nullstill alle vurderingsfelter for paret og fjern interessen. | Interessen kan fjernes. Vurderinger hos andre arbeidsgivere blir stående. | | |
| 8.1.11 | Eier – Kryss raskt av, av og på samme celle, og kryss av en annen celle mens det lagres. | Siste valg per celle består etter at køen er ferdig og siden er lastet på nytt. Et eldre svar overskriver ikke et nyere valg. | | |

---

## 9. WorkOp – intervjufordeling og speedintervjuer

**Hvor:** gjennomføringen → «Intervjufordeling»

**Hva skjer:** Hver arbeidsgiver har en intervjurekkefølge og en liste under sperrelinjen for dem som ikke gjennomfører speedintervju. Plasskonflikter er varsler om rekkefølge.

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 9.1.1 | Eier – Åpne første fordeling etter å ha registrert interesser. | Interesserte jobbsøkere fordeles hos riktig arbeidsgiver. En person kan stå hos flere arbeidsgivere, men bare én gang hos hver. | | |
| 9.1.2 | Eier – Endre rekkefølgen med flytteknappene. Last siden på nytt. | Den manuelt valgte intervjurekkefølgen beholdes. Den sorteres ikke automatisk tilbake etter navn eller deltakernummer. | | |
| 9.1.3 | Eier – Endre rekkefølgen med dra-og-slipp. | Resultatet lagres som ved flytteknappene. Fokus og muligheten for videre tastaturbetjening beholdes. | | |
| 9.1.4 | Eier – Flytt en person under sperrelinjen. | Personen vises under «Ikke gjennomført speedintervju» hos den aktuelle arbeidsgiveren. Interessen består, men personen teller ikke som inkludert intervju der. | | |
| 9.1.5 | Eier – Flytt samme person tilbake over sperrelinjen. | Personen inkluderes igjen i valgt rekkefølge. Andre arbeidsgiveres fordelinger er uendret. | | |
| 9.1.6 | Eier – Flytt alle under sperrelinjen og prøv «Neste». | «Neste» er deaktivert når ingen intervjuer er inkludert. | | |
| 9.1.7 | Eier – Inkluder minst én person igjen. | «Neste» kan brukes når endringen er lagret. | | |
| 9.1.8 | Eier – Sett samme person på samme plassnummer hos to arbeidsgivere. | «Plasskonflikt» synliggjør kollisjonen. Løsningen må ikke gi inntrykk av at to samtidige plasseringer er konfliktfrie. | | |
| 9.1.9 | Eier – Flytt personen til en annen plass slik at konflikten forsvinner. | Konfliktmarkeringen oppdateres og forsvinner når det ikke lenger er kollisjon. | | |
| 9.1.10 | Eier – Velg «Fordel på nytt», les dialogen og avbryt. | Dialogen forklarer at manuell rekkefølge erstattes. Avbryt beholder både rekkefølge og ekskluderinger. | | |
| 9.1.11 | Eier – Bekreft «Fordel på nytt» med noen personer under sperrelinjen. | Ny rekkefølge beregnes. De eksplisitt ekskluderte forblir under sperrelinjen så lenge interessen består. Fordelingen forsøker å redusere plasskonflikter. | | |
| 9.1.12 | Eier – Legg til en ny interesse etter at fordelingen finnes. | Personen tas med hos den nye arbeidsgiveren uten at tidligere ekskluderinger for andre par eller hele rekkefølgen nullstilles. | | |
| 9.1.13 | Eier – Fjern en interesse for en ekskludert person uten vurdering. | Personen forsvinner fra både intervju- og ekskluderingslisten hos denne arbeidsgiveren. | | |
| 9.1.14 | Eier – Flytt en allerede vurdert person under sperrelinjen. Gå til vurdering og oppfølging. | Registrert vurdering består og kan fortsatt leses. Ekskludering fra speedintervju sletter ikke oppfølgingen. | | |
| 9.1.15 | Eier – Registrer mange interesser hos én arbeidsgiver og opprett fordeling. | Fordelingen har ingen automatisk kvote beregnet fra møtelengden. Arrangøren må selv håndtere utvalg og rekkefølge; løsningen oppretter ikke en egen venteliste eller kalenderavtaler. | | |

---

## 10. Vurdering og oppfølging

**Hvor:** gjennomføringen → «Vurdering og oppfølging»

**Hva skjer:** Opplysninger registreres per jobbsøker og arbeidsgiver. Notater er forhåndsdefinerte valg, ikke fritekst. «Formidlet» kommer fra formidlingsflyten og er skrivebeskyttet her. Avtalt videre intervju heter «2. intervju» i skjermbildet, med feltet «Dato for 2. intervju».

### Vurdering og notater

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 10.1.1 | Eier – Åpne steget med interesser, inkluderte/ekskluderte intervjuer og eksisterende vurderinger. | Riktig person vises under riktig arbeidsgiver. Interesse, intervju og vurdering fremstår som forskjellige opplysninger. | | |
| 10.1.2 | Eier – Velg «Aktuell», «Kanskje» og «Ikke aktuell» på tre ulike par. Last siden på nytt. | Alle vurderinger er lagret på riktig par. Det ene valget påvirker ikke en annen arbeidsgivers vurdering av samme person. | | |
| 10.1.3 | Eier – Bytt vurdering fra «Aktuell» til «Ikke aktuell», og deretter «Ingen vurdering». | Siste verdi lagres. Notater, avtalt intervju og jobbtilbud fjernes ikke bare fordi vurderingsvalget endres. | | |
| 10.1.4 | Eier – Åpne notatvalgene og registrer ett utsagn fra arbeidsgiver og ett fra jobbsøker. | Valgene grupperes tydelig etter hvem som uttaler seg. Det finnes ikke et fritt tekstfelt for vurderingsnotater. | | |
| 10.1.5 | Eier – Velg flere notater for samme par, last siden på nytt og fjern deretter bare ett. | Alle valgte notater er bevart etter ny åpning. Bare det valgte notatet slettes fra gjeldende vurdering. | | |
| 10.1.6 | Eier – Sammenlign arbeidsgiverens og jobbsøkerens notater. | Parten fremgår av tekst, ikke bare farge. Arbeidsgiverutsagn vises ikke som en rød systemfeil eller faremelding. | | |
| 10.1.7 | Eier – Registrer et notat uten å velge «Aktuell», «Kanskje» eller «Ikke aktuell». | Notatet lagres selvstendig og sperrer fjerning av interesse/oppmøte frem til det er fjernet. | | |
| 10.1.8 | Eier – Gjør flere raske endringer i samme vurderingsrad og en annen rad. | Siste komplette valg på hver rad er bevart etter lagring. Nyere notater eller avkrysninger forsvinner ikke når et eldre svar kommer tilbake. | | |
| 10.1.9 | Eier – Prøv hvert forhåndsdefinerte notatvalg på syntetiske data i dev: velg, last siden på nytt og fjern igjen. | Hvert valg beholder riktig tekst og part og kan fjernes uten å endre andre notater. At et valg fungerer i dev, innebærer ikke at det er godkjent for produksjon; se 16.3.3. | | |

### Avtalt intervju, dato og jobbtilbud

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 10.2.1 | Eier – Kryss av «2. intervju» uten å fylle inn dato. | Avtalen lagres; dato er valgfri. «Dato for 2. intervju» blir tilgjengelig. | | |
| 10.2.2 | Eier – Velg en gyldig dato i kalenderen eller skriv den i feltet, og forlat feltet. | Datoen lagres og vises likt ved ny åpning. | | |
| 10.2.3 | Eier – Skriv en umulig dato, for eksempel 31.02.2027, og forlat feltet. | Valideringsfeil vises. Ugyldig dato erstatter ikke en tidligere lagret dato. | | |
| 10.2.4 | Eier – Tøm datoen, men behold avkrysningen for avtalt intervju. | Avtalen består uten dato. | | |
| 10.2.5 | Eier – Fjern avkrysningen «2. intervju» etter at dato er lagret. | Både avtalen og datoen fjernes fra gjeldende registrering. | | |
| 10.2.6 | Eier – Kryss av «Jobbtilbud» og last siden på nytt. Fjern deretter avkrysningen. | Jobbtilbudet kan lagres og angres. Det oppretter ikke automatisk formidling, stilling eller svar på invitasjonen. | | |
| 10.2.7 | Eier – Registrer avtalt intervju og jobbtilbud hos to arbeidsgivere for samme person. | Opplysningene holdes atskilt per arbeidsgiver. Begge kan være registrert uten at den ene overskriver den andre. | | |
| 10.2.8 | Eier – Skriv en intervjudato med tosifret år, og prøv deretter samme dato med firesifret år. | Tosifret år godtas ikke. Gyldig dato på formen dd.mm.åååå kan lagres. | | |

### Formidling og nullstilling

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 10.3.1 | Eier – Fullfør den ordinære formidlingsflyten for et aktuelt person–arbeidsgiver-par, og åpne oppfølgingen på nytt. | Paret merkes «Formidlet», og «Vis formidling» peker til riktig formidling. Dette kan ikke krysses av manuelt i vurderingssteget. | | |
| 10.3.2 | Eier – Kontroller samme jobbsøker hos en annen arbeidsgiver etter formidlingen. | Den andre arbeidsgiveren merkes ikke automatisk som mottaker av formidlingen. | | |
| 10.3.3 | Eier – Velg «Ingen vurdering», fjern alle notater, avtalt intervju/dato og jobbtilbud for et par uten formidling. | Registreringen er helt tom. Interessen kan deretter fjernes. Tidligere hendelser erstattes ikke av en uriktig beskrivelse av nåtilstanden. | | |
| 10.3.4 | Utvikler/Eier – La henting av formidlinger feile mens øvrige gjennomføringsdata er tilgjengelige. | Feilen varsles. Vanlig vurdering kan fortsatt brukes; manglende formidlingsdata presenteres ikke som sikkert fravær av formidling. | | |
| 10.3.5 | Eier – Angre personens eneste formidling i formidlingsflyten, og åpne oppfølging og oppsummering på nytt. | «Formidlet»-merket og formidlingstallet oppdateres. Andre vurderingsfelter og personens tidligere registrerte oppmøte forsvinner ikke fordi formidlingen angres. | | |

> **Se også:** Den komplette opprettelsesflyten for formidling er beskrevet i [akseptansetester-formidling.md](akseptansetester-formidling.md). Her testes koblingen til WorkOp og gjennomføring, ikke alle stillingsfeltene på nytt.

---

## 11. Oppsummering og tellinger

**Hvor:** gjennomføringen → «Oppsummering»

**Hva skjer:** Hovedtall dedupliserer jobbsøkere, mens intervjuer teller inkluderte person–arbeidsgiver-par. Samme person kan derfor gi flere intervjuer, men bare én person i et hovedtall.

**Kontrollgrunnlag:** Bruk et eget WorkOp med fem deltakere, to arbeidsgivere og tre fremmøtte A, B og C. A og B er inkludert hos arbeidsgiver 1; A og C hos arbeidsgiver 2. A er «Aktuell» og har avtalt videre intervju hos arbeidsgiver 1, og «Ikke aktuell» samt formidlet hos arbeidsgiver 2. B er «Kanskje» hos arbeidsgiver 1. C har ingen vurdering. De to siste deltakerne er ikke fremmøtte. Bruk bare syntetiske personer.

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 11.1.1 | Eier – Åpne oppsummeringen med kontrollgrunnlaget. | Grunnlaget viser 3 møtt av totalt 5, og 2 arbeidsgivere. Ikke-fremmøtte telles ikke som møtt. | | |
| 11.1.2 | Eier – Kontroller antall intervjuer. | Tallet er 4: A–arbeidsgiver 1, B–arbeidsgiver 1, A–arbeidsgiver 2 og C–arbeidsgiver 2. Det er ikke 3 selv om bare tre personer er involvert. | | |
| 11.1.3 | Eier – Kontroller samlede vurderingstall. | Det er 1 aktuell kandidat, 1 kanskje, 0 ikke aktuelle og 1 ikke vurdert. A telles én gang som aktuell, ikke også som ikke aktuell. | | |
| 11.1.4 | Eier – Kontroller videre intervju og formidling. | Begge hovedtallene er 1, siden A er den eneste personen med disse registreringene. | | |
| 11.1.5 | Eier – Kontroller arbeidsgiver 1 i tabellen. | Arbeidsgiveren har 2 vurderte, 1 aktuell, 1 avtalt videre intervju og 0 formidlede. | | |
| 11.1.6 | Eier – Kontroller arbeidsgiver 2 i tabellen. | Arbeidsgiveren har 1 vurdert, 0 aktuelle, 0 avtalte videre intervjuer og 1 formidlet. C uten vurdering øker ikke antall vurderte. | | |
| 11.1.7 | Eier – Endre A hos arbeidsgiver 1 fra «Aktuell» til «Kanskje». | Hovedtallet aktuelle blir 0 og kanskje blir 2. Prioriteten er Aktuell foran Kanskje foran Ikke aktuell foran Ingen vurdering. | | |
| 11.1.8 | Eier – Registrer avtalt videre intervju for A også hos arbeidsgiver 2. | Samlet antall personer med avtalt intervju er fortsatt 1. Begge arbeidsgivernes egne tall viser avtalen. | | |
| 11.1.9 | Eier – Flytt C under sperrelinjen hos arbeidsgiver 2 uten å endre oppmøtet. | Antall inkluderte intervjuer går fra 4 til 3. Fremmøtetallet forblir 3. | | |
| 11.1.10 | Eier – Endre en vurdering, et intervju eller et jobbtilbud, og gå tilbake til oppsummeringen. | Oppsummeringen bruker bekreftet, oppdatert tilstand og viser ikke tall fra før endringen. | | |
| 11.1.11 | Utvikler/Eier – La formidlingshentingen feile. | Det vises advarsel om at tallet for formidlede kan være for lavt. Øvrige tall vises uten at formidlingsfeilen skjules. | | |
| 11.1.12 | Eier – Sammenlign oppsummeringen med vurderingssteget. | Oppsummeringen viser aggregerte tall; opplysninger om enkeltpersoner kan leses i vurderingssteget. Det finnes ikke en egen eksport av personvurderinger i oppsummeringen. | | |

---

## 12. Utskrifter og bruk uten mus

**Hvor:** «Rom og rotasjon» og «Intervjufordeling»

**Hva skjer:** Utskrifter bruker deltakernummer og initialer fremfor fulle jobbsøkernavn. Arbeidsgivernavn vises i planene som brukes under selve arrangementet; dette er noe annet enn skjuling på den innloggete treffsiden for jobbsøkere.

### Utskriftsinnhold

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 12.1.1 | Eier – Velg «Utskrift til arbeidsgivere» med fem arbeidsgivere. | Fem arbeidsgiverseksjoner vises, med arbeidsgivernavn, klokkeslett og rom for hver runde. Jobbsøkeres navn, fødselsnummer og vurderinger er ikke med. | | |
| 12.1.2 | Eier – Velg «Utskrift til jobbsøkere» med fem rom. | Fem romseksjoner vises. Hver viser romnummer, rommets deltakernumre/initialer, klokkeslett og arbeidsgiverne som kommer dit. | | |
| 12.1.3 | Eier – Kontroller et tomt rom i utskriftsvisningen. | Rommet og planen finnes fortsatt, med «Ingen jobbsøkere». Det dukker ikke opp personer fra et annet rom. | | |
| 12.1.4 | Eier – Velg «Vis utskrift» i intervjufordelingen. | Bare inkluderte intervjuer vises, i lagret rekkefølge per arbeidsgiver, med deltakernummer og initialer. Personer under sperrelinjen og arbeidsgivere uten inkluderte intervjuer utelates. | | |
| 12.1.5 | Eier – Sammenlign alle utskriftsvariantene med navn og personopplysninger i skjermvisningen. | Ingen fulle jobbsøkernavn, fødselsnummer, kontaktopplysninger, notater eller vurderinger tas med i selve utskriftsinnholdet. | | |
| 12.1.6 | Eier – Åpne nettleserens utskriftsforhåndsvisning for alle variantene. | Rom-/rotasjonsplan er stående, intervjufordeling liggende. Seksjonene skilles med sideskift; tekst og tabeller er lesbare uten avkuttede kolonner. Lange seksjoner kan kreve flere sider. | | |
| 12.1.7 | Eier – Endre rom, tid og intervjurekkefølge, vent på lagring og åpne utskriftene igjen. | Ny utskrift viser siste lagrede plan, ikke en tidligere kopi. Utdaterte papirkopier må erstattes manuelt. | | |
| 12.1.8 | Eier – Avbryt utskrift og lukk dialogen med lukkeknapp eller Escape. | Du kommer tilbake til samme steg uten endrede data eller mistet navigasjon. | | |
| 12.1.9 | Eier – Skriv ut planen med en testperson som har flere fornavn eller bindestreksnavn, for eksempel «Ada-Marie Test Hansen». | Initialene er AMTH og vises sammen med personens deltakernummer. Fullt navn erstatter ikke initialene i utskriften. | | |

### Tastatur, zoom og liten skjerm

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 12.2.1 | Eier – Registrer oppmøte og interesse med bare Tab, Shift+Tab og mellomrom. | Riktig person/celle kan identifiseres og betjenes. Fokus er synlig og beholdes etter lagring. | | |
| 12.2.2 | Eier – Flytt personer mellom rom og i intervjurekkefølgen uten dra-og-slipp. | Romvalg og flytteknapper gir de samme mulighetene som musedragging. | | |
| 12.2.3 | Eier – Fokuser en låst oppmøte- eller interessekontroll med tastatur. | Årsaken til låsingen er tilgjengelig uten at musen må holdes over kontrollen. | | |
| 12.2.4 | Eier – Bruk 200 % zoom og smal nettleser gjennom alle seks steg og dialoger. | Felter, feilmeldinger og knapper er tilgjengelige. Brede matriser kan rulles uten at nødvendige handlinger blir utilgjengelige. | | |
| 12.2.5 | Eier – Observer siden mens lagringsstatus veksler mellom lagring, lagret og feil. | Det er tydelig hva som er lagret. Radene flytter seg ikke slik at et pågående klikk treffer en annen person. | | |

---

## 14. Hendelser, lagringsfeil og samtidige arrangører

**Hvor:** gjennomføringen, «Hendelser» og nettleserens utviklerverktøy

**Forutsetning:** Utvikler simulerer tregt nettverk, avviste skrivekall og tapte svar i dev/lokalt testoppsett. «Svaret gikk tapt» betyr at serveren kan ha lagret; det må ikke behandles som bevis på at ingenting skjedde.

### Hendelseshistorikk

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 14.1.1 | Eier – Registrer og fjern oppmøte, og åpne hendelsene. | Registrering og fjerning vises som to handlinger med hvem og når. WorkOp-registreringen viser deltakernummer. Angring sletter ikke det opprinnelige historikksporet. | | |
| 14.1.2 | Eier – Endre vurdering fra «Aktuell» til «Ikke aktuell», og åpne hendelsene. | Endringen har lesbar tekst og viser overgangen mellom vurderinger. | | |
| 14.1.3 | Eier – Legg til og fjern ett notat fra arbeidsgiver og ett fra jobbsøker. | Hvert tillegg og hver fjerning vises, med notattekst og riktig part. Ukjente tekniske enum-navn skal ikke erstatte de kjente tekstene. | | |
| 14.1.4 | Eier – Slå «2. intervju» og «Jobbtilbud» på og av. | Historikken viser både registrering og angring, på den aktuelle jobbsøkeren. | | |
| 14.1.5 | Eier – Opprett møteplan, endre møteoppsett og fordel intervjuer på nytt. | Treffhendelser viser henholdsvis opprettelse av gjennomføring, endret oppsett og ny intervjufordeling. | | |
| 14.1.6 | Eier – Endre bare interesse, romplassering og manuell intervjurekkefølge. | Nåtilstanden oppdateres. Det forventes ikke egne domenhendelser for disse handlingene; historikken er ikke et komplett historisk bilde av alle plasseringer. | | |
| 14.1.7 | Utvikler – Kontroller vurderingshendelsenes arbeidsgiverkobling. | Hendelsen skrives på jobbsøkeren med arbeidsgiver som kontekst, ikke som en identisk ekstra vurderingshendelse på arbeidsgiveren. | | |
| 14.1.8 | Utvikler – Gjenta identisk oppmøte, interesse og vurdering uten å endre verdi. | Ingen duplikatregistreringer oppstår; uendrede felt gir ikke nye endringshendelser. Dette er ikke et krav om at «Fordel på nytt» eller lagring av møteoppsett er hendelsesløst. | | |

### Treghet og usikkert lagringsutfall

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 14.2.1 | Utvikler/Eier – Forsink lagring av oppmøte, interesse og vurdering, og gjør flere raske valg. | Ventende/lagret status er tydelig. Siste valg på hver person eller hvert par beholdes; stegnavigasjon venter til lagringen er ferdig. | | |
| 14.2.2 | Utvikler/Eier – La én oppmøte-, interesse- eller vurderingslagring bli avvist uten lagring på serveren. | Feil vises på riktig rad/celle. Bekreftet tilstand hentes, og et ikke-lagret kryss/valg fremstår ikke som lagret. Brukeren må utføre ønsket endring på nytt. | | |
| 14.2.3 | Utvikler/Eier – Etter feil på rad A, lagre en endring på rad B. | Suksess på B fjerner ikke feilmarkeringen på A eller beskriver A som vellykket lagret. | | |
| 14.2.4 | Utvikler/Eier – La serveren lagre en romflytting, men mist svaret til nettleseren. | Frontend henter servertilstanden og viser den faktisk lagrede flyttingen med informasjon om usikkert utfall, ikke en påstått tilbakestilling. | | |
| 14.2.5 | Utvikler/Eier – Gjenta med endret intervjurekkefølge og «Fordel på nytt». | Bekreftet servertilstand hentes. Det sendes ikke automatisk en ny, potensielt overskrivende fordeling som «retry». | | |
| 14.2.6 | Utvikler/Eier – La både skrivesvaret og etterfølgende henting feile. | «Tilstanden er ubekreftet» og «Hent på nytt» vises. Endringer og stegnavigasjon er låst til en gyldig tilstand er hentet. | | |
| 14.2.7 | Utvikler/Eier – Trykk «Hent på nytt», forsink svaret og la det deretter lykkes. | Henting, ikke ny skriving, utføres. Kontroller er fortsatt låst under hentingen, og åpnes igjen ved bekreftet tilstand. | | |
| 14.2.8 | Utvikler/Eier – Forsink en romflytting og se på rommene før svaret kommer. | Personen flyttes ikke optimistisk før bekreftet svar. Lagringsstatus og sperrede kontroller viser at operasjonen pågår. | | |
| 14.2.9 | Utvikler/Eier – La første henting av arbeidsgivere eller gjennomføringen feile. | En forståelig datagrunnlagsfeil vises med mulighet for ny henting. Feilen tolkes ikke som at alle registreringer er slettet. | | |
| 14.2.10 | Utvikler/Eier – La automatisk intervjufordeling ved «Neste» fra interesse feile. | Du blir på interessesteget med feilmelding, og kan prøve stegovergangen igjen. Ingen tom fordeling presenteres som ferdig. | | |

### To eiere samtidig

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 14.3.1 | Eier og medeier – Registrer forskjellige personer møtt samtidig fra hver sin nettleser. | Begge registreringene består. Personene får forskjellige deltakernumre. | | |
| 14.3.2 | Eier og medeier – Åpne samme romfordeling. Eier flytter A; medeier flytter B fra sin eldre visning. | Begge flyttingene er bevart etter ny henting. Medeiers gamle visning overskriver ikke eiers flytting av A. | | |
| 14.3.3 | Eier og medeier – Flytt samme person til forskjellige rom etter hverandre. | Siste serverbehandlede flytting gjelder. Personen står fortsatt i nøyaktig ett rom. | | |
| 14.3.4 | Eier og medeier – Lagre vurderinger for forskjellige person–arbeidsgiver-par samtidig. | Begge registreringene består og kobles til riktig par. | | |
| 14.3.5 | Eier – Ha to ulike treff åpne. Endre oppmøte og rom i det ene. | Det andre treffets deltakere, numre, rom og vurderinger endres ikke. | | |

---

## 15. Tekniske kontroller av API og integrasjoner

**Hvor:** dev eller isolert lokalt oppsett, med utviklerhjelp

**Forutsetning:** Bruk egne syntetiske testdata og gyldige testidentiteter. API-testene skal ikke kjøres mot reelle personer. Relevante gjennomføringsruter ligger under `/api/rekrutteringstreff/{id}/treffgjennomforing`, vurderinger under `/{id}/oppfolging/vurderinger`, og aggregatet hentes fra `/{id}/treffgjennomforing-og-oppfolging`.

### Validering og dataintegritet

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 15.1.1 | Utvikler – Les og skriv gjennomføring med testidentiteter som er ikke-eier uten utviklerrolle, borger eller bruker uten nødvendig rolle. | Tilgang avvises. Gjennomføringsdata kan ikke leses eller endres bare ved å kjenne treff-ID. | | |
| 15.1.2 | Utvikler – Bruk ukjent treff-ID og person-/arbeidsgiver-ID fra et annet treff i oppmøte, rom, interesse og vurdering. | Ugyldig treff/tilhørighet avvises. Ingen data i noen av treffene endres. | | |
| 15.1.3 | Utvikler – Flytt en ikke-fremmøtt person, flytt før møteplan er opprettet, og prøv rom 0 eller et rom over gjeldende antall. | Alle tre ugyldige romoperasjoner avvises uten delvis endring. | | |
| 15.1.4 | Utvikler – Registrer interesse eller en ikke-tom vurdering for en person som ikke er fremmøtt. | Kallet avvises. Manglende oppmøte omgås ikke ved direkte API-kall. | | |
| 15.1.5 | Utvikler – Fjern oppmøte med interesser/vurderinger, og fjern interesse med en eksisterende vurdering. | Konflikt avvises med 409. Registreringene bevares; oppmøteresponsen gir informasjon om sperrende registreringer. | | |
| 15.1.6 | Utvikler – Send intervjufordeling med duplikat innen én liste og samme person i både inkludert og ekskludert liste. | Begge tilfeller avvises. Tidligere gyldig fordeling består. | | |
| 15.1.7 | Utvikler – Send ukjent notatkode, ugyldig intervjudato og dato uten avkrysset intervju. | Ugyldige vurderingsdata avvises. Ingen del av den feilaktige vurderingen lagres. | | |
| 15.1.8 | Utvikler – Send helt tom vurdering og hent deretter registreringen på nytt. | Gjeldende vurderingsrad fjernes, slik at den ikke fortsetter å sperre interesse/oppmøte. Relevante fjernings-/endringshendelser består. | | |
| 15.1.9 | Utvikler – Hent nytt gjennomføringsaggregat flere ganger uten mutasjoner. | Standardverdier returneres uten å opprette gjennomføringsrader eller hendelser bare ved lesing. | | |
| 15.1.10 | Utvikler – Sett lagret steg fremover, deretter bakover og til samme verdi. | Lagret progresjon går ikke bakover. Dette er ikke en låsing av tidligere steg eller en garanti om at alle steg er gjennomført. | | |
| 15.1.11 | Utvikler – Gjenta en flytting til rommet personen allerede står i. | Personen finnes fortsatt én gang i samme rom, i returnert romrekkefølge. Ingen andre personer flyttes. | | |
| 15.1.12 | Utvikler – Fremprovoser en databasefeil under møteopprettelse eller annen sammensatt lagring. | Operasjonen rulles tilbake atomisk; det lagres ikke en halv møteplan, et duplikatnummer eller en hendelse uten tilsvarende endring. | | |

### WorkOp-kontrakter, gjenlevering og feil

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 15.2.1 | Utvikler – Følg invitasjon, endring og svar/status fra WorkOp gjennom backend og begge konsumenter. | WorkOp bruker `workopinvitasjon`, `workopoppdatering` og `workopSvarOgStatus`, riktig mal og riktig aktivitetskorttype. Meldingen behandles ikke i tillegg som vanlig treff. | | |
| 15.2.2 | Utvikler – Gjenta med vanlig rekrutteringstreff. | De tilsvarende ordinære treffhendelsene behandles fortsatt. Innføring av WorkOp gjør ikke at gamle invitasjoner eller kort stopper. | | |
| 15.2.3 | Utvikler – Gjenlever samme invitasjon med samme hendelses-ID etter ferdig behandling. | Det opprettes ikke nytt MinSide-varsel eller nytt aktivitetskort for samme invitasjon. Samme person på et annet treff får derimot eget kort. | | |
| 15.2.4 | Utvikler – Kjør aktivitetskortsenderen to ganger etter ferdig utsending. | Allerede sendte meldinger sendes ikke som nye kort. Eksisterende kort-ID består ved senere oppdateringer. | | |
| 15.2.5 | Utvikler – Send en oppdateringshendelse med tom endringsliste og deretter bare ukjente endringsfelt. | Varselkonsumenten oppretter ikke en generell, innholdsløs endringsbeskjed. Kortoppdatering er en egen behandling og kan fortsatt skje. | | |
| 15.2.6 | Utvikler – Send ett ukjent endringsfelt sammen med gyldig tidspunkt/sted. | Ukjent felt filtreres bort; bare kjente felter flettes inn i varslet. | | |
| 15.2.7 | Utvikler – Send varselhendelse uten påkrevd treff-ID, person eller hendelses-ID. | Ufullstendig hendelse gir ikke et varsel til feil mottaker eller et kort uten korrekt kobling. Feilen fremgår av eksisterende feilhåndtering. | | |
| 15.2.8 | Utvikler – Simuler MinSide-status opprettet, vellykket SMS, vellykket e-post og mislykket ekstern varsling. | Lagret leveringsstatus og intern visning følger hendelsen. Leveringsfeil blir synlig; `minsideVarselSvar` endrer ikke i seg selv personens ja-/nei-svar. | | |
| 15.2.9 | Utvikler – Simuler feil fra aktivitetskorttjenesten og kjør feilformidlingen. | Feilen knyttes til riktig person og treff som aktivitetskortfeil, ikke skjules som suksess. Samme behandlede feilmelding sendes ikke på nytt ved hver jobbkjøring. | | |
| 15.2.10 | Utvikler – Kontroller WorkOp-lyttere med lokal-, dev- og produksjonskonfigurasjon. | WorkOp-lytterne er aktive lokalt/dev og inaktive i produksjon/ukjent miljø. Vanlige trefflyttere fungerer fortsatt. | | |
| 15.2.11 | Utvikler – Forsøk direkte opprettelse av WorkOp med produksjonskonfigurasjon. | Backend avviser opprettelsen før lagring. Det er ikke nok at opprettingsknappen er skjult i frontend. | | |
| 15.2.12 | Utvikler – Sammenlign tid og dato i treffet, treffsiden og aktivitetskortet, også rundt et sommertidsbytte. | De representerer samme start og slutt i riktig lokal tid, uten utilsiktet dag- eller timeforskyvning. Møteoppsettets egne klokkeslett holdes adskilt. | | |

---

## 16. Kjente avvik og avklaringer før godkjenning

Denne seksjonen beskriver **akseptansekrav**, ikke en fasit der dagens avvik skal godkjennes. «Kjent avvik» er begrunnet i dagens kode; «Avklaring» betyr at forventet regel eller løsning må besluttes. Resultatene fylles først ut ved faktisk gjennomføring. Avvikene er ikke rettet som del av dette testscriptet.

### Tilgang utenfor hovedsiden

**Kjent avvik:** Hovedtreffet har WorkOp-eiersjekk, men flere underressurser mangler tilsvarende kontroll. `PUT /eiere/meg` lar en arbeidsgiverrettet bruker legge seg selv til som eier også på WorkOp. Dermed kan ikke en vellykket test av skjult oversikt alene brukes som godkjenning av tilgangsmodellen.

**Kilde:** [EierController.kt](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/rekrutteringstreff/eier/EierController.kt), sammenholdt med WorkOp-kontrollen i [RekrutteringstreffController.kt](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/rekrutteringstreff/RekrutteringstreffController.kt).

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 16.1.1 | Utvikler – Kjent avvik: Hent eiere, arbeidsgivere, arbeidsgiverbehov og innlegg direkte fra et WorkOp som en intern ikke-eier. | Samme WorkOp-tilgangsregel som for hovedtreffet håndheves. Underressurser gir ikke opplysninger som hovedsiden nekter brukeren å se. | | |
| 16.1.2 | Utvikler – Kjent avvik: Forsøk å legge til jobbsøker og hente formidlingsgrunnlag direkte på et WorkOp uten eierskap, også fra treffets kontor. | WorkOp-eierregelen kan ikke omgås via deltakerruter eller generell kontortilgang. Eventuelle særskilte unntak må være eksplisitt godkjent. | | |
| 16.1.3 | Utvikler – Kjent avvik: Kall `/eiere/meg` som ikke-eier på et WorkOp som er skjult i oversikten. | Brukeren kan ikke gi seg selv WorkOp-tilgang bare ved å kjenne treff-ID. Medeierskap krever et godkjent tilgangsløp. | | |

### Oppmøte, formidling og invitasjonssvar

**Kjent avvik:** Backend lagrer oppmøte og «Fått jobb» i samme statusfelt som invitasjonssvaret. Metoden for aktivt ja-svar sjekker bare SVART_JA. Oppmøte kan derfor gjøre at et tidligere ja-svar ikke gjenkjennes ved varsling/fullføring eller på treffsiden. «Fått jobb» regnes dessuten som fremmøte, og fjerning av oppmøte endrer ikke denne statusen.

**Kilde:** [Jobbsøker.kt](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/jobbsoker/Jobbsøker.kt), [JobbsøkerService.kt](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/jobbsoker/JobbsøkerService.kt) og [OppmøteRepository.kt](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/jobbsoker/oppmøte/OppmøteRepository.kt).

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 16.2.1 | Eier/Jobbsøker – Kjent avvik: Svar ja, registrer oppmøte, og åpne jobbsøkerens svarvisning på nytt før treffstart. | Tidligere ja-svar gjenkjennes fortsatt. Oppmøte skal ikke få jobbsøkeren til å fremstå som ubesvart. | | |
| 16.2.2 | Eier/Jobbsøker – Kjent avvik: Svar ja, registrer oppmøte og send et relevant endringsvarsel. | Personen omfattes fortsatt av regelen for ja-mottakere. Oppmøte alene skal ikke føre til at nødvendig informasjon uteblir. | | |
| 16.2.3 | Eier/Jobbsøker – Kjent avvik: Avlys et eget testtreff etter ja-svar og registrert oppmøte. | Personen mottar avlysning etter samme svarregel, og eksisterende aktivitetskort blir «Avbrutt». | | |
| 16.2.4 | Eier/Jobbsøker – Kjent avvik: Fullfør etter ja-svar og registrert oppmøte. Gjenta etter registrert formidling. | Kortet fullføres etter personens gyldige svarhistorikk; det blir ikke stående «Gjennomføres» fordi gjennomføringsstatusen har erstattet SVART_JA. | | |
| 16.2.5 | Utvikler – Kjent avvik: Send ja/nei direkte når personen allerede har oppmøte eller «Fått jobb», også gjennom eierens svar-API. | Svaret håndteres uten å slette eller skjule registrert oppmøte/formidling. Frontendens sperre mot «Endre svar» må ikke kunne omgås slik at data blir inkonsistente. | | |
| 16.2.6 | Utvikler/Eier – Kjent avvik: Fjern oppmøte for en person med «Fått jobb» uten andre sperrende registreringer. | Løsningen gir enten en tydelig, korrekt sperre eller en reell retting etter avtalt regel. Den skal ikke logge «oppmøte fjernet» mens personen fortsatt står som møtt. | | |
| 16.2.7 | Utvikler/Eier – Avklaring: Formidle en person som ikke er registrert møtt, og åpne gjennomføring og oppsummering. | Regelen for om formidling skal telle som fremmøte er besluttet og dokumentert. Telling, avkrysning og deltakernummer må følge samme regel; dagens automatiske inkludering må ikke forveksles med bekreftet fysisk oppmøte. | | |

### Varseltekst, personvern og manglende integrasjonssperrer

**Kjent avvik:** Intern forhåndsvisning henter fortsatt ordinære treffmaler, mens kandidatvarsel har egne WorkOp-maler. Dagens notatliste inneholder også «Helse eller kapasitet». Godkjenning av slike opplysninger følger ikke automatisk av at et felt finnes i løsningen.

**Kilde:** [Frontendens meldingsmaler](../../../rekrutteringsbistand-frontend/app/api/kandidatvarsel/hentMeldingsmaler.ts) og [varselmalene](../../../rekrutteringsbistand-kandidatvarsel-api/src/main/kotlin/no/nav/toi/kandidatvarsel/minside/Mal.kt).

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 16.3.1 | Eier/Jobbsøker – Kjent avvik: Sammenlign SMS-/e-postforhåndsvisning i WorkOp-invitasjonen og endringsdialogen med faktisk utsendt melding. | Forhåndsvisningen viser WorkOp-malen som faktisk sendes, med samme betydning og valgte endringsfelter. | | |
| 16.3.2 | Eier/Fagansvarlig – Avklaring: Gå gjennom WorkOp-innhold og varsler før pilot, inkludert frivillighet, kontaktpunkt og skjulte arbeidsgivere. | Ordlyden er faglig/personvernfaglig godkjent. Jobbsøkeren får tilstrekkelig beslutningsgrunnlag uten at skjulte arbeidsgivere røpes i fritekst eller varsel. | | |
| 16.3.3 | Utvikler/Fagansvarlig – Avklaring: Gå gjennom tilgjengelige notatkoder, særlig «Helse eller kapasitet», og prøv direkte lagring med produksjonskonfigurasjon. | Produksjonslagring er sperret med dagens kode. Før sperren eventuelt åpnes, må godkjent kodeverk og behandlingsgrunnlag være avklart; sensitive valg skal ikke tas i bruk bare fordi de finnes lokalt/dev. | | |
| 16.3.4 | Utvikler – Avklaring: Gjenlever samme varselhendelse samtidig til to konsumentinstanser. | Samme logiske hendelse gir maksimalt ett varsel til mottakeren. Dagens oppslag-før-innsetting alene er ikke tilstrekkelig dokumentasjon på samtidighetssikker idempotens. | | |
| 16.3.5 | Eier/Jobbsøker – Avklaring: Avlys og gjenåpne et WorkOp og kontroller aktivitetskort/varsler etterpå. | Det er avklart hvordan mottakeren får vite om gjenåpning og hvilken kortstatus som skal gjenopprettes. Gjenåpning av treffstatus alene godkjenner ikke hele brukerreisen. | | |

### Grenser, skjulte personer og livsløp

**Kjent avvik:** Manuell intervjufordeling validerer trefftilhørighet, men ikke oppmøte/interesse. Borgerens svarmetoder mangler tids-/treffstatuskontroll. Gjennomføringskonteksten filtrerer slettede personer, men har ikke samme synlighetsfilter som den ordinære jobbsøkerlisten. Gjennomførings-API-et har heller ingen generell låsing ved avlysning.

**Kilde:** [MatchingService.kt](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/treffgjennomføring/matching/MatchingService.kt), [Treffkontekst.kt](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/treffgjennomføring/Treffkontekst.kt) og svarmetodene i [JobbsøkerService.kt](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/jobbsoker/JobbsøkerService.kt).

| # | Test | Forventet resultat | ✅❌ | Notat |
| --- | --- | --- | --- | --- |
| 16.4.1 | Utvikler – Kjent avvik: Sett opp en ikke-fremmøtt person eller en person uten interesse i manuell intervjufordeling via API. | Ugyldig grunnlag avvises etter samme regler som den vanlige brukerflyten. Det skal ikke oppstå skjulte eller uforklarlige intervjuplasseringer i oppsummeringen. | | |
| 16.4.2 | Utvikler – Kjent avvik: Send invitasjonssvar direkte etter treffstart, etter avlysning/fullføring og for en person som bare er lagt til, ikke invitert. | Backend håndhever avtalte svarregler, ikke bare skjulte knapper. Svar uten gyldig invitasjon eller etter stengt svarperiode avvises uten å endre status. | | |
| 16.4.3 | Utvikler/Eier – Kjent avvik: La en fremmøtt person med rom/interesse bli usynlig, og sammenlign alle steg, tellinger, utskrifter og API-svar. | Ingen personopplysninger eksponeres i strid med synlighetsreglene. Samlet fremmøtetall og videre grunnlag må ikke motsi listen fordi ulike filtre brukes. Eventuelle særskilte oppfølgingsunntak må være avklart. | | |
| 16.4.4 | Utvikler/Eier – Avklaring: Forsøk ny oppmøte-, rom-, interesse- og vurderingsregistrering på avlyst treff, både i skjermbildet og direkte API. | Det er besluttet hvilke rettinger som eventuelt er tillatt etter avlysning, og regelen håndheves likt i frontend/backend. Dagens fravær av statuskontroll godkjennes ikke som en beslutning. | | |
| 16.4.5 | Utvikler/Eier – Avklaring: To eiere endrer ulike felter på samme vurderingspar eller samme arbeidsgivers intervjufordeling fra gamle visninger. | Regelen for samtidige endringer er avklart. Endringer skal enten bevares eller en konflikt synliggjøres, ikke gå tapt uten at brukeren forstår det. Serverlås alene beskytter ikke et helt dokument sendt fra en gammel klientkopi. | | |
| 16.4.6 | Utvikler/Fagansvarlig – Avklaring: Fullfør/avlys treff og gjennomgå hva som fortsatt ligger i nåtilstand, hendelser og utskrifter. | Lagringstid, retting og sletting er dokumentert, inkludert hvilke historikkopplysninger som beholdes. Status «Fullført»/«Avlyst» og logisk sletting må ikke forveksles med fysisk sletting. | | |
| 16.4.7 | Eier/Fagansvarlig – Avklaring: Prøv et møteoppsett med svært lang varighet og en tidsplan som går utenfor selve treffet eller over midnatt. | Tillatte tidsgrenser og håndtering av døgnskifte er avklart. Dagens positive heltallsvalidering skal ikke alene tolkes som godkjenning av enhver møteplan. | | |

---

## Grunnlag og relaterte dokumenter

Kildekartleggingen omfatter følgende deler. Plandokumenter og presentasjonen er støtteinformasjon; implementasjonen og de eksplisitte akseptansekravene over må skilles fra eldre målbeskrivelser.

| Område | Kildegrunnlag |
| --- | --- |
| Intern brukerflyt | [Frontendens gjennomføringssteg](../../../rekrutteringsbistand-frontend/app/rekrutteringstreff/%5BrekrutteringstreffId%5D/_ui/treffgjennomføring), opprettingsmeny, arbeidsgiver-/jobbsøkerfaner, meldingsforhåndsvisning og tilhørende frontendtester |
| Domene og API | [Treffgjennomføring](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/treffgjennomføring), [oppmøte](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/jobbsoker/oppmøte), [oppfølging](../../apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/oppfølging), treff-/eier-/søkeregler, migrasjoner og komponenttester |
| Jobbsøkerens flate | [Brukerapplikasjonen](../../../rekrutteringstreff-bruker/app) og [MinSide-API](../../apps/rekrutteringstreff-minside-api/src/main/kotlin/no/nav/toi/minside), særlig svarstatus og skjuling av arbeidsgivere |
| Varsler | [Kandidatvarsel-API](../../../rekrutteringsbistand-kandidatvarsel-api/src/main/kotlin/no/nav/toi/kandidatvarsel), særlig WorkOp-lyttere, maler, MinSide-status og idempotens |
| Aktivitetskort | [Aktivitetskorttjenesten](../../apps/rekrutteringsbistand-aktivitetskort/src/main/kotlin/no/nav/toi), særlig WorkOp-type, oppdatering, svar/status, senderjobb og feilhåndtering |
| Presentasjon og historiske planer | [WorkOp-oversikt](../../../workop-oversikt/README.md) og [WorkOp-planer](../9-planer/workop), gjennomgått for omfang, men ikke brukt som bevis på ferdig funksjonalitet |

Generelle tester finnes i [akseptansetester.md](akseptansetester.md), formidlingsflyten i [akseptansetester-formidling.md](akseptansetester-formidling.md), og risikopunktene i [ros-workop.md](../9-planer/workop/ros-workop.md).
