# ROS for WorkOp og treffgjennomføring

Dette dokumentet beskriver risikoer som kommer i tillegg til
[`ros-pilot.md`](ros-pilot.md) når WorkOp og fanen «Treffgjennomføring og
oppfølging» tas i bruk.

WorkOp er et rekrutteringstreff for jobbsøkere under 30 år. Jobbsøkerne
roterer mellom arbeidsgivere, interesse kartlegges og aktuelle speedintervjuer
fordeles. Jobbsøkerne ser ikke hvilke arbeidsgivere som deltar.

ROS-en er avgrenset til risikoer som består etter at ordinær sikker utvikling,
kodegjennomgang og testing er gjennomført. Vanlige implementasjonsfeil og
midlertidige feature toggles er derfor ikke egne risikopunkter.

Risikoene er registrert i ROS-verktøyet. Kolonnen «ROS» i tabellen viser
ID-en der. Vurderinger og beslutninger føres i ROS-verktøyet. Dette dokumentet
har bakgrunnen, status i koden og forslag til tekst. Forslagene til endringer i
ROS-verktøyet står samlet i
[Foreslåtte endringer og tillegg](#foreslåtte-endringer-og-tillegg-i-ros-verktøyet).

## Endringer 01.10.26

- Ny risiko WO-14: usynlige jobbsøkere vises med navn i treffgjennomføringen,
  vurderingen og «Hendelser», og kan registreres som møtt. Usynlige som er
  slettet, vises ikke, og personer med adressebeskyttelse vises uten navn.
  Reglene er avklart med produkteier og gjelder bare WorkOp. Vanlige treff er
  uendret.

## Endringer 30.09.26

- Risikoene er koblet til ID-ene i ROS-verktøyet (31566–31576).
- Nye risikoer: WO-11 (formøte og WorkOp-dag), WO-12 (selvinnmelding som
  medeier) og WO-13 (frivillighet i tekstene).
- WO-03 ble avklart 22.09.26: WorkOp-navnet beholdes i SMS og e-post.
- WO-08 gjelder nå all nedetid under arrangementet, ikke bare nettverksbrudd.
- Hver risiko har fått status i koden per 30.09.26.
- Sluttseksjonen har forslag til beskrivelser for alle tiltak i
  ROS-verktøyet, og for de tre risikoene som mangler beskrivelse.

## Omfang

Vurderingen omfatter:

- intern løsning i `rekrutteringsbistand-frontend`
- innbyggerflate i `rekrutteringstreff-bruker`
- varsling i `rekrutteringsbistand-kandidatvarsel-api`
- API, database, aktivitetskort og Kafka-hendelser i
  `rekrutteringstreff-backend`
- den fysiske gjennomføringen, inkludert utskrifter og ustabilt nettverk

Følgende nye opplysninger inngår i treffgjennomføringen:

- registrert oppmøte og deltakernummer
- rom- og intervjufordeling
- interesse mellom jobbsøker og arbeidsgiver
- arbeidsgivers og jobbsøkerens vurderinger
- vurderingsnotater, avtalt intervju og registrert jobbtilbud
- hendelser som viser hvem som registrerte eller endret opplysninger

#kanskje # Dataflyt og tillitsgrenser

```text
[Nav-ansatt]
    |
    | Azure AD / Wonderwall
    v
(rekrutteringsbistand-frontend)
    |
    | REST med brukerkontekst
    v
(rekrutteringstreff-api) ---> {PostgreSQL: treff og gjennomføringsdata}
    |
    | Kafka-hendelser med kategori og hendelses-id
    +----------------------------+
    |                            |
    v                            v
(aktivitetskort)          (kandidatvarsel)
    |                            |
    v                            v
{Aktivitetsplan}           {MinSide, SMS og e-post}

[Jobbsøker]
    |
    | ID-porten / Wonderwall
    v
(rekrutteringstreff-bruker)
    |
    | TokenX
    v
(rekrutteringstreff-minside-api)
    |
    | TokenX
    v
(rekrutteringstreff-api)

[Treffgjennomføring] ---> {utskrifter med deltakernummer og initialer}
```

## Risikovurdering

Skalaen er 1–4, der 1 er lavest og 4 er høyest. Sannsynlighet og konsekvens er
foreløpige vurderinger som må godkjennes av risikoeier. S og K for WO-11, WO-12
og WO-13 er forslag.

| ID    | ROS   | Risiko                                                                                      |   S |   K | Viktigste tiltak                                                                                | Relatert pilot-ROS  |
| ----- | ----- | ------------------------------------------------------------------------------------------- | --: | --: | ----------------------------------------------------------------------------------------------- | ------------------- |
| WO-01 | –     | Jobbsøkeren får ikke tilstrekkelig beslutningsgrunnlag når arbeidsgiverne skjules           |   3 |   3 | Forklare format, bransjer og forventninger uten å røpe arbeidsgiverne                           | 27485, 27385, 27273 |
| WO-02 | –     | Arbeidsgivere avsløres mens de skal være skjult                                             |   2 |   3 | Skjule i backend på alle innbyggerendepunkter og hindre navn i brukerrettet fritekst og varsler | 27383, 27215        |
| WO-03 | 31566 | WorkOp-navnet i SMS eller e-post røper arbeidsrettet oppfølging                             |   2 |   3 | Avklart 22.09.26: navnet beholdes, varslet har bare nødvendig informasjon                       | 27215, 27383        |
| WO-04 | 31567 | Vurderinger og notater kan inneholde helseopplysninger eller gi grunnlag for diskriminering |   3 |   4 | Faglig og personvernfaglig godkjent kodeverk, ingen fritekst og ingen sekundærbruk              | 27219, 27216, 27227 |
| WO-05 | 31568 | Gjennomføringsdata lagres lenger enn nødvendig eller kan ikke korrigeres og slettes riktig  |   3 |   4 | Fastsette behandlingsgrunnlag, slettefrist og rettingsløp for både tilstand og hendelser        | 27486, 27227        |
| WO-06 | 31574 | Personer uten tjenstlig behov får tilgang til WorkOp- og gjennomføringsdata                 |   2 |   4 | Ressursbasert tilgang i backend, pilotavgrensning, auditlogg og tilgangsrevisjon                | 27217, 27215        |
| WO-07 | 31569 | Feil eller samtidige registreringer gir uriktig oppmøte, interesse eller vurdering          |   3 |   3 | Individuell registrering, låsing, tydelig lagringsstatus og sporbart rettingsløp                | 27388, 27486        |
| WO-08 | 31570 | Nedetid under arrangementet gir stans, manglende eller usikre registreringer                |   2 |   3 | Forhåndssjekk, utskrift som reserve, synlige feil og etterfølgende avstemming                   | 27386, 28065        |
| WO-09 | 31571 | Feil kategori eller blanding av vanlig treff og WorkOp gir feil prosess og funksjoner       |   3 |   3 | Tydelige kriterier, merking, låst kategori, backendregler og kontraktstester                    | 27381, 27383, 27386 |
| WO-10 | 31572 | Utskrifter og deltakernummer gjør jobbsøkere identifiserbare i lokalet                      |   2 |   3 | Dataminimerte utskrifter, kontrollert utdeling, tidspunkt, innsamling og makulering             | 27215, 27227        |
| WO-11 | 31573 | Jobbsøkeren forveksler formøtet og WorkOp-dagen                                             |   2 |   2 | Standardtekst med begge avtalene og svarside som sier at svaret gjelder WorkOp-dagen            | –                   |
| WO-12 | 31575 | Markedskontakt som ikke skal være medeier, legger seg selv til som eier                     |   2 |   3 | Tydelig bekreftelse, sporbarhet, beskjed til eierne og retningslinjer for medeierskap           | 27217               |
| WO-13 | 31576 | Tekst i løsningen bryter med prinsippet om frivillig deltakelse                             |   2 |   3 | Gjennomgåtte faste tekster, retningslinjer og opplæring for arrangørene                         | 27485               |
| WO-14 | –     | Usynlige jobbsøkere vises og kan registreres i treffgjennomføringen                         |   2 |   3 | Bare WorkOp og eiere, aldri usynlige som er slettet, adressebeskyttede uten navn, ikke i jobbsøkerlisten | –                   |

WO-01 og WO-02 har ingen ID i utskriften fra ROS-verktøyet 30.09.26. WO-06
er bare delvis dekket av 31574, som gjelder søket.

## WO-01 – utilstrekkelig beslutningsgrunnlag

**Risiko:** Når arbeidsgiverne skjules, kan jobbsøkeren mangle informasjon som
er nødvendig for å vurdere om WorkOp er relevant. Invitasjonen kan oppfattes
som lite transparent.

**Tiltak:**

- Beskriv formålet, gjennomføringen, aktuelle bransjer og hvilke typer
  jobbmuligheter som finnes, uten å oppgi arbeidsgivernavn.
- Oppgi kontaktpunkt for spørsmål før svarfristen.
- Send praktisk informasjon og informasjon om eventuelt formøte i tide (se
  WO-11).
- Bruk pilotintervjuer til å kontrollere om informasjonen faktisk blir forstått.
- Frivillighet er skilt ut i WO-13.

**Status 30.09.26:** Innbyggerflaten skjuler arbeidsgiverne. Varselet sier at
jobbsøkeren kan møte arbeidsgivere. Format, bransjer og forventninger står bare
i arrangørens fritekst. Løsningen har ingen mal for den (se WO-11).

**Restrisiko:** Jobbsøkeren kan ikke velge ut fra konkrete arbeidsgivere på
forhånd. Dette er en tilsiktet del av WorkOp-konseptet og må aksepteres av
produkteier etter pilot.

**Ansvar:** Produkteier og fagansvarlig.  
**Ny vurdering:** Etter produksjonspiloten og før bred utrulling.

## WO-02 – arbeidsgivere avsløres

**Risiko:** Arbeidsgiverne kan bli synlige gjennom et alternativt API,
nettverkssvar, brukergrensesnitt, fritekst, aktivitetskort eller varsel selv om
den ordinære visningen skjuler dem.

**Tiltak:**

- La backend bestemme skjulingen. Frontend er bare et ekstra lag.
- Bruk samme regel på sammensatt treffrespons og alle separate
  arbeidsgiverendepunkter.
- Kontroller at tittel, beskrivelse, innlegg, aktivitetskort og varsler ikke
  inneholder arbeidsgivernavn.
- Test direkte API-kall og innbyggerreisen, ikke bare visuell skjuling.
- Dokumenter om og når skjulingen skal opphøre.

**Status 30.09.26:** `rekrutteringstreff-minside-api` fjerner arbeidsgiverne
fra både treffresponsen og arbeidsgiverendepunktet når treffet er en WorkOp.
`rekrutteringstreff-bruker` skjuler i tillegg arbeidsgiverseksjonen.
WorkOp-varslene nevner ikke arbeidsgivere. Arbeidsgiverne er alltid skjult i
innbyggerflatene. Tittel, beskrivelse og innlegg er fritekst og kontrolleres
ikke for arbeidsgivernavn.

**Restrisiko:** En arrangør kan skrive inn et arbeidsgivernavn i et fritekstfelt.
Retningslinjer og opplæring må redusere denne risikoen (se WO-13).

**Ansvar:** Team Toi og produkteier.  
**Ny vurdering:** Før pilot og ved endring av brukerrettede felter eller API-er.

## WO-03 – WorkOp-navnet røper kontekst

**Risiko:** SMS og e-post kan vises på en låst skjerm eller leses av andre.
Ordet «WorkOp» kan knytte mottakeren til arbeidsrettet oppfølging.

**Vurdering 22.09.26:** Risikoen vurderes som akseptabel. «WorkOp» sier lite
om mottakerens forhold til Nav, og meldingen må være spesifikk nok til at
mottakeren skjønner hva den gjelder. Teksten beholdes.

**Status 30.09.26:** Invitasjonen på SMS lyder: «Hei! Du er invitert til en
WorkOp der du kan møte arbeidsgivere. Logg inn på Nav for å svare JA eller NEI
på om du planlegger å delta. Vennlig hilsen Nav». Endringsvarselet nevner
hvilke felt som er endret, for eksempel tidspunkt eller sted, men ikke de nye
verdiene. Varslene inneholder ikke arbeidsgivere, vurderinger, oppmøte eller
svarstatus.

**Restrisiko:** Andre som ser skjermen, kan se at mottakeren er invitert til en
WorkOp.

**Ansvar:** Produkteier og personvernressurs.  
**Ny vurdering:** Ved endring av varseltekstene.

## WO-04 – sensitive eller diskriminerende vurderinger

**Risiko:** Treffgjennomføringen lagrer vurderinger om enkeltpersoner, blant
annet arbeidsgivers inntrykk, språk, kompetanse og jobbsøkerens begrunnelse.
Koden `JS_HELSE_KAPASITET` kan innebære behandling av helseopplysninger.
Opplysningene kan også bli oppfattet som objektive fakta eller brukes utenfor
formålet de ble samlet inn for.

**Tiltak:**

- Ikke produksjonssett `JS_HELSE_KAPASITET` før behov, behandlingsgrunnlag,
  tilgang og lagringstid er skriftlig avklart.
- Bruk bare et faglig og personvernfaglig godkjent, avgrenset kodeverk.
- Ikke tilby fritekst for vurderinger.
- Bevar tydelig hvem utsagnet kommer fra: arbeidsgiver eller jobbsøker.
- Ikke bruk registreringene til automatiserte avgjørelser, rangering eller
  andre formål uten en ny vurdering.
- Gi arrangørene opplæring i hva som skal og ikke skal registreres.

**Status 30.09.26:** Notatene er et fast kodeverk med 17 valg
(`Vurderingsnotat`). Løsningen har ikke fritekst for vurderinger. Hvert notat
er merket med om det kommer fra arbeidsgiveren eller jobbsøkeren. Valget
«Helse eller kapasitet» (`JS_HELSE_KAPASITET`) finnes fortsatt i både backend
og frontend.

**Restrisiko:** Strukturerte vurderinger vil fortsatt være subjektive og kan
påvirkes av bevisste eller ubevisste skjevheter.

**Ansvar:** Fagansvarlig, produkteier og personvernressurs.  
**Ny vurdering:** Før pilot, deretter etter pilotens faglige evaluering.

## WO-05 – lagring, retting og sletting

**Risiko:** Oppmøte, interesser, fordelinger, vurderinger, notater,
intervjuavtaler og jobbtilbud lagres både som gjeldende tilstand og delvis som
hendelser. Uriktige eller utdaterte opplysninger kan bli stående, og sletting
ett sted kan etterlate data i andre tabeller eller nedstrøms systemer.

**Tiltak:**

- Dokumenter formål og slettefrist for hver type gjennomføringsdata.
- Definer hva som skal skje ved fullføring, avlysning, fjerning av jobbsøker
  og sletting av treff.
- Lag et rettingsløp som både korrigerer gjeldende tilstand og etterlater et
  forståelig revisjonsspor.
- Kontroller relasjoner til hendelsestabeller, aktivitetskort,
  kandidatvarsler, logger og backup.
- Kontroller jevnlig at sletterutinene faktisk virker.

**Status 30.09.26:** Alle registreringer kan rettes. Oppmøtet kan ikke fjernes
før interesser, intervjufordeling og vurderinger hos jobbsøkeren er fjernet.
Retting av oppmøte, vurdering, notater, 2. intervju og jobbtilbud lagres som
hendelser med tidspunkt og Nav-ident. Interesse og intervjufordeling har bare
gjeldende tilstand. En jobbsøker kan bare slettes fra treffet med status «Lagt
til» og uten registreringer. Det finnes ingen slettefrist for
gjennomføringsdata. Opprydningsjobben i backend sletter bare KI-logger.

**Restrisiko:** Enkelte hendelser kan måtte beholdes av hensyn til
etterprøvbarhet. Omfang og lagringstid må begrenses og dokumenteres.

**Ansvar:** Behandlingsansvarlig/produkteier og Team Toi.  
**Ny vurdering:** Før pilot og når sletterutinen er fastsatt.

## WO-06 – tilgang uten tjenstlig behov

**Risiko:** WorkOp inneholder mer detaljerte personopplysninger enn den
generelle treffoversikten. For brede roller, søk, direkte oppslag eller
underendepunkter kan gi Nav-ansatte tilgang til treff de ikke arbeider med.
En jobbsøker skal bare få tilgang til sin egen invitasjon.

**Tiltak:**

- Håndhev eier- eller medeiertilgang på alle interne lese- og
  skriveoperasjoner, inkludert søk, direkte oppslag, hendelser og
  gjennomføringsendepunkter.
- Begrens utviklertilgang til nødvendig support, og auditlogg bruken.
- Kontroller jobbsøkerens tilknytning til treffet i backend. Kjennskap til en
  treff-id skal ikke være nok.
- Avgrens produksjonspiloten på serversiden til godkjente brukere eller
  kontorer, med en dokumentert stoppmekanisme.
- Gjennomgå tilganger etter piloten og jevnlig etter bred utrulling.

**Status 30.09.26:** Søket tar bare med WorkOp der den innloggede er eier.
Regelen ligger i backend og gjelder også antallene i filtrene. Utviklere ser
alle treff. Jobbsøkerlista og treffgjennomføringen krever eier eller utvikler.
Med direkte lenke ser en ikke-eier vanlig forhåndsvisning og kan legge seg til
som medeier (se WO-12).

**Status 09.10.26:** Jobbsøkerlista, jobbsøkersøket, tillegg av jobbsøkere,
arbeidsgiverbehovet og alle gjennomføringsendepunktene krever eier eller
utvikler i API-et. Ukjent treff, og person- eller arbeidsgiver-id fra et annet
treff, avvises uten endring (`TreffgjennomføringKomponentTest`). Utviklere ser
alle WorkOp, også i søket, og det er ønsket.

**Restrisiko:** Personer med legitim support- eller eiertilgang kan misbruke
tilgangen. Auditlogg og oppfølging reduserer, men fjerner ikke risikoen.

**Ansvar:** Produkteier, tilgangseier og Team Toi.  
**Ny vurdering:** Før pilot og før pilotbegrensningen fjernes.

## WO-07 – uriktige registreringer under gjennomføringen

**Risiko:** Under tidspress kan feil person markeres som møtt, interesse
registreres mot feil arbeidsgiver eller vurderinger overskrives. Flere
arrangører kan arbeide samtidig, og utdelte planer kan avvike fra lagret
tilstand.

**Tiltak:**

- Registrer oppmøte individuelt. Løsningen skal ikke ha «marker alle».
- Bruk deltakernummer og en egnet sekundær kontroll ved innsjekk uten å
  eksponere flere personopplysninger enn nødvendig.
- Serialiser konkurrerende skrivinger og hindre at eldre svar overskriver
  nyere data.
- Vis tydelig om en endring lagres, er lagret eller har feilet.
- Ved feil skal tilstanden forbli uendret, slik at brukeren må utføre
  handlingen på nytt.
- Sørg for at rettinger gir et forståelig hendelsesspor.

**Status 30.09.26:** Skrivinger på samme treff venter på hverandre (radlås i
databasen). Hver avkrysning lagres for seg, og status og feil vises per rad.
Ved feil står krysset uendret, og arrangøren må krysse på nytt. Arrangøren kan
ikke gå til neste steg mens lagringer pågår. Løsningen har ikke «marker alle».
Endrer to arrangører samme vurdering, vinner den siste lagringen uten at noen
får beskjed.

**Restrisiko:** Manuelle feil kan fortsatt skje og må kunne oppdages og rettes
mens treffet pågår.

**Ansvar:** Arrangør for arbeidsrutinen og Team Toi for systemkontrollene.  
**Ny vurdering:** Etter de første gjennomførte pilotene.

## WO-08 – nedetid under arrangementet

**Risiko:** WorkOp bruker løsningen under selve arrangementet, mens et vanlig
treff mest bruker den før og etter. Er løsningen, innloggingen eller nettet i
lokalet nede, stopper registreringen av oppmøte, rom og intervjuer mens
jobbsøkere og arbeidsgivere venter. Arrangører kan også tro at data er lagret
når de ikke er det.

**Tiltak:**

- Kontroller nettverk og nødvendig utstyr i lokalet før arrangementet.
- Skriv ut rom- og rotasjonsplanen og intervjufordelingen så snart de er klare.
- Vis lagringsfeil ved den aktuelle registreringen og samlet for steget.
- Ikke gå videre fra et steg før ventende lagringer er ferdige.
- Etabler en reserveprosedyre med minst mulig persondata på papir.
- Beskriv hvordan papirregistreringer skal avstemmes, etterregistreres og
  makuleres.
- Følg med på lagringsfeil og responstid under piloten uten personopplysninger
  i metrikker.

**Status 30.09.26:** Lagringsfeil vises per registrering og samlet for steget.
Rom- og rotasjonsplanen og intervjufordelingen kan skrives ut. Løsningen virker
ikke uten nett. Det finnes ingen beskrevet reserveprosedyre.

**Restrisiko:** Et lengre avbrudd kan forsinke gjennomføringen og kreve manuell
etterregistrering.

**Ansvar:** Arrangør og Team Toi.  
**Ny vurdering:** Etter hver pilot der reserveprosedyren tas i bruk.

## WO-09 – feil kategori og sammenblanding av trefftyper

**Risiko:** WorkOp følger en egen metode og gjelder bare jobbsøkere under 30
år. Et vanlig rekrutteringstreff kan ved en feil, eller bevisst for å få
tilgang til treffgjennomføringsfunksjonene, opprettes som WorkOp uten at
WorkOp-prosessen følges. Da kan arbeidsgivere skjules, egne varsler sendes og
flere personopplysninger registreres uten at det er nødvendig for treffet.

Det motsatte kan også skje: En reell WorkOp opprettes som vanlig
rekrutteringstreff. Da kan arbeidsgiverne bli vist til jobbsøkerne,
WorkOp-tekstene utebli og de særskilte kontrollene ikke bli brukt.

Selv med riktig kategori kan delvis utrulling eller ulik tolkning i
rekrutteringstreff-backend, aktivitetskort, kandidatvarsel og frontend gi en
blanding av vanlig treff- og WorkOp-oppførsel.

**Tiltak:**

- Definer konkrete kriterier for når et treff er en WorkOp, hvem som kan velge
  kategorien, og hvilke deler av WorkOp-prosessen som er obligatoriske.
- Begrens oppretting av WorkOp på serversiden til godkjente brukere eller
  kontorer i piloten. Vurder en egen tilgang også etter piloten dersom
  arbeidsgiverrettet rolle blir for vid.
- Forklar konsekvensene av kategorivalget og krev en eksplisitt bekreftelse
  ved oppretting. Merk WorkOp tydelig i alle interne visninger.
- Ikke tillat kategoribytte etter oppretting.
- Håndhev i backend at WorkOp-spesifikke endepunkter bare kan brukes for
  WorkOp, og at vanlige treff ikke får lagret de utvidede
  gjennomføringsopplysningene.
- Bruk en publiseringssjekkliste for å bekrefte at den obligatoriske
  WorkOp-prosessen er planlagt før invitasjoner sendes.
- Deploy konsumenter som forstår WorkOp før produsenten begynner å sende
  WorkOp-hendelser.
- Ha kontraktstester for invitasjon, oppdatering, svar, fullføring og avlysning
  på tvers av tjenestene.
- Bruk stabil hendelses-id og idempotent behandling ved gjenlevering.
- Mål antall produserte, mottatte, fullførte og feilede WorkOp-hendelser uten
  personopplysninger som metrikklapper.
- Avstem invitasjoner mot aktivitetskort og varsler, og varsle ved varige
  avvik eller konsumentlag.
- Følg med på antall WorkOp-er per kontor og eier, og undersøk uventet bruk
  eller treff som ikke følger den avtalte prosessen.
- Ved rollback: stopp opprettelse av nye WorkOp-er, men la konsumentene
  behandle allerede publiserte hendelser.

**Status 30.09.26:**

- WorkOp opprettes fra et eget valg i opprett-menyen, uten bekreftelse. Valget
  er skjult i prod, og backend avviser WorkOp i prod.
- Kategorien kan ikke endres etter oppretting.
- Merkelappen «WorkOp» vises i overskriften på treffsiden og på kortet i søket.
- Backend tillater rom, intervjufordeling og deltakernummer bare for WorkOp.
  Interesse og vurdering krever WorkOp i dev.
- Aktivitetskort og kandidatvarsel har egne WorkOp-hendelser og -maler.
- Svardialogen for jobbsøkeren spør «Kommer du på rekrutteringstreffet?», også
  for WorkOp.
- Løsningen hindrer ikke at jobbsøkere over 30 år legges til. Jobbsøkerlista
  kan filtreres på aldersgruppe.
- Kjent feil: Når et treff fullføres, blir aktivitetskortet ikke avsluttet for
  jobbsøkere med status «Møtt opp» eller «Fått jobb».

**Restrisiko:** En bruker med legitim tilgang kan velge feil kategori eller
bekrefte en sjekkliste uten å følge prosessen. Asynkron behandling gir også
alltid en periode med midlertidig ulikhet mellom systemene.

**Ansvar:** Produkteier, fagansvarlig, Team Toi og eiere av de berørte
integrasjonene.  
**Ny vurdering:** Under produksjonspiloten, før bred utrulling og ved endring
av WorkOp-prosessen eller hendelseskontrakten.

## WO-10 – identifisering via utskrifter

**Risiko:** Deltakernummer og initialer er mindre identifiserende enn navn, men
kan kobles til personer av andre som er til stede. Utdaterte eller gjenglemte
utskrifter kan gi feil møteplan eller spre opplysninger etter arrangementet.
Utskrifter kan også komme på avveie.

**Tiltak:**

- Bruk bare deltakernummer og initialer, aldri navn, fødselsnummer,
  vurderinger eller kontaktinformasjon.
- Skriv ut én mottakers nødvendige plan per side og begrens antall kopier.
- Merk utskriften med tidspunkt, slik at utdaterte planer kan trekkes tilbake.
- Ikke fotografer eller del listene digitalt.
- Samle inn og makuler alle utskrifter umiddelbart etter arrangementet.

**Status 30.09.26:**

- Utskriften til jobbsøkere viser per rom deltakernummer og initialer, og
  hvilke arbeidsgivere som kommer når.
- Utskriften til arbeidsgivere viser hvilket rom de skal til per klokkeslett.
- Utskriften av intervjufordelingen viser deltakernummer og initialer per
  arbeidsgiver.
- Ingen utskrift viser fullt navn.
- Deltakernummeret er fortløpende i den rekkefølgen oppmøtet registreres. Det
  er ikke tilfeldig.
- Utskriftene har ikke tidspunkt.

**Restrisiko:** Deltakere og arbeidsgivere i samme lokale kan fortsatt koble
nummer og initialer til en person.

**Ansvar:** Arrangør.  
**Ny vurdering:** Etter produksjonspiloten.

## WO-11 – formøtet og WorkOp-dagen forveksles

**Risiko:** WorkOp har et formøte på en annen dag enn selve WorkOp-dagen.
Jobbsøkeren svarer én gang i løsningen og kan tro at svaret gjelder formøtet,
eller møte opp feil dag. Da uteblir jobbsøkere, og svarstatus og oppmøte
stemmer ikke med det jobbsøkeren mente.

ROS-verktøyet spør om risikoen er relevant i dag. Den er det så lenge WorkOp
har formøte. Løsningen har ett tidspunkt og ett svar per treff, og formøtet
står bare i arrangørens fritekst.

**Tiltak:**

- Lag en standardtekst for WorkOp som beskriver formøtet og WorkOp-dagen hver
  for seg.
- Gjør standardteksten tilgjengelig for eierne i løsningen.
- La svarsiden si at svaret gjelder WorkOp-dagen.

**Status 30.09.26:** Løsningen har ingen standardtekst for WorkOp.
Svardialogen spør «Kommer du på rekrutteringstreffet?» for alle treff.

**Restrisiko:** Jobbsøkere som ikke leser beskrivelsen, kan fortsatt blande de
to avtalene.

**Ansvar:** Produkteier og fagansvarlig.  
**Ny vurdering:** Når standardteksten er tatt i bruk.

## WO-12 – uønsket selvinnmelding som medeier

**Risiko:** En markedskontakt kan legge seg selv til som medeier på et treff
hen ikke skal jobbe med, også en WorkOp. Som medeier får hen se jobbsøkerne,
svarstatus og treffgjennomføringen, og kan sende invitasjoner. Det omgår
tilgangsbegrensningen i WO-06.

**Tiltak:**

- Forklar ansvaret i bekreftelsesdialogen.
- Gjør selvinnmeldingen sporbar og synlig for eierne.
- Gi eierne beskjed når noen legger seg til.
- Beskriv i retningslinjene hvem som skal være medeier.
- Vurder å begrense hvem som kan legge seg til.

**Status 30.09.26:** Alle med arbeidsgiverrettet rolle og kontortilknytning
kan legge seg til som medeier på alle treff (`PUT /eiere/meg`), uavhengig av
kontor. Knappen «Legg meg til som medeier» vises for ikke-eiere som åpner
treffet med direkte lenke. Jobbsøkerrettet rolle kan ikke legge seg til.
Bekreftelsesdialogen sier hva medeier kan gjøre, men ikke hvem som bør bli
medeier. Selvinnmeldingen gir hendelsen «eier lagt til» på treffet, og
kontoret blir lagt til. Eierne får ikke beskjed. En eier kan fjerne andre
eiere.

**Restrisiko:** En ansatt som bevisst misbruker selvinnmeldingen, har tilgang
fram til noen oppdager det.

**Ansvar:** Produkteier og tilgangseier.  
**Ny vurdering:** Etter produksjonspiloten.

## WO-13 – tekst som bryter med frivillig deltakelse

**Risiko:** Tekster i invitasjonen, varselet, aktivitetskortet eller svarsiden
kan oppfattes som at jobbsøkeren må delta, eller at et nei får følger.
Invitasjonen kommer fra Nav og vises i aktivitetsplanen, der jobbsøkeren også
har aktiviteter avtalt med veilederen. Arrangørens egen tekst i tittel,
beskrivelse og innlegg kan også være formulert som et krav.

**Tiltak:**

- Gå gjennom de faste tekstene i varsler, aktivitetskort og svarside med tanke
  på frivillighet.
- Opplys tydelig at deltakelse er frivillig, hvordan man svarer nei, og at et
  avslag ikke får negative følger.
- Gi arrangørene retningslinjer og opplæring i hvordan de skriver om WorkOp.

**Status 30.09.26:** Svardialogen sier «Det er frivillig å delta.» og at
jobbsøkeren kan endre svaret fram til svarfristen. Varselet ber jobbsøkeren
«svare JA eller NEI på om du planlegger å delta». KI-sjekken av treffteksten
ser etter diskriminering, ikke etter krav om oppmøte.

**Restrisiko:** Jobbsøkere kan oppleve en invitasjon fra Nav som forpliktende
uansett ordlyd.

**Ansvar:** Produkteier og fagansvarlig.  
**Ny vurdering:** Etter pilotintervjuene.

## WO-14 – usynlige jobbsøkere vises i treffgjennomføringen

**Risiko:** En jobbsøker kan bli usynlig etter at hen er lagt til eller har
møtt opp, for eksempel fordi hen ikke lenger er under oppfølging, er i KVP
eller er død. Ellers skjules usynlige personer overalt i rekrutteringstreff.
For at eierne skal kunne fullføre og rette registreringene fra treffet, vises
usynlige med navn i treffgjennomføringen og vurderingen. Det gjør at
opplysninger om personer som ikke lenger skal være synlige for Nav-ansatte i
rekrutteringsløsningen, fortsatt vises og kan endres. Viser løsningen også
slettede personer eller personer med adressebeskyttelse, kan det avsløre
opplysninger som skal være skjermet.

**Regler, avklart med produkteier 01.10.26.** Reglene gjelder bare treff med
kategorien WorkOp. Vanlige treff er uendret, se «Vanlige treff» under.

- **Jobbsøkerlisten:** Usynlige vises ikke, bare som et samlet tall for
  skjulte, som før.
- **Treffgjennomføringen og vurderingen:** Usynlige vises med navn og
  deltakernummer, og kan registreres og rettes som andre. De merkes ikke som
  usynlige. Fødselsnummeret vises ikke. Der det ellers står, står «Ikke
  tilgjengelig».
- **Dataminimering:** Treffgjennomføringen henter fra et eget endepunkt med
  egen DTO, og får bare id, navn, status og fødselsnummer, også for synlige.
  Den får ikke alder, kontor, innsatsgruppe, hvem som la personen til, når
  personen ble lagt til, eller varseldata. Usynlige får heller ikke
  fødselsnummer. Endepunktet har ingen filtre på fødselsnummer, alder eller
  kontor, og ingen tellinger per alder og kontor.
- **Oppmøte:** Usynlige som ikke har møtt, kan registreres som møtt. Vi legger
  til grunn at de var synlige da de ble lagt til eller invitert, og at de har
  fått en status i ettertid som gjør dem usynlige.
- **Fanen «Hendelser»:** Hendelser for usynlige vises med navn, og med
  «Ikke tilgjengelig» i stedet for fødselsnummer. Har personen svart selv,
  står «Jobbsøker» under «Utført av», ikke fødselsnummeret.
- **Slettede:** Vises ikke i jobbsøkerlisten, treffgjennomføringen eller
  vurderingen. Usynlige som er slettet, vises heller ikke i «Hendelser».
  Synlige som er slettet, vises i «Hendelser» som på vanlige treff, slik at
  eierne kan se hvem som slettet personen.
- **Adressebeskyttelse:** Personen vises aldri med navn. Har personen
  gjennomføringsdata, vises hen som «Ukjent jobbsøker», både i gjennomføringen
  og i «Hendelser».
- **Varsler og aktivitetskort:** Sendes ikke til usynlige, som før.
- **Aktivitetskort ved fullføring:** Kjent feil for «Møtt opp» og «Fått jobb»
  (se «Før produksjonspilot») gjelder også usynlige. Den rettes ikke nå.

**Vanlige treff (ikke WorkOp):** Uendret. Usynlige filtreres bort overalt,
også i treffgjennomføringen, som er skrudd av med funksjonsbryter i
produksjon. «Hendelser» viser bare synlige, med fødselsnummer og navn som før,
og uten detaljer. Om usynlige skal vises i treffgjennomføringen for vanlige
treff, må vurderes i ROS-en før det endres.

**Tiltak:**

- Vis usynlige bare på WorkOp, bare for eiere og utviklere, og bare i
  treffgjennomføringen, vurderingen og «Hendelser».
- Hold slettede og personer med adressebeskyttelse utenfor på serversiden, ikke
  bare i skjermbildet.
- Dekk reglene med automatiske tester og akseptansetest 6.3.

**Status 01.10.26:** Reglene er på plass i koden, og serveren avgrenser dem til
WorkOp. Treffgjennomføringen henter jobbsøkere fra et eget endepunkt,
`POST …/treffgjennomforing-og-oppfolging/jobbsokere`, som krever eier eller
utvikler og har en egen DTO med bare de feltene som trengs. Endepunktet tar
med usynlige bare for WorkOp, og holder alltid slettede og personer med
adressebeskyttelse (`sperret`) utenfor. Endepunktet leser tabellene
`jobbsoker` og `rekrutteringstreff` direkte. Jobbsøkersøket bruker fortsatt
`jobbsoker_sok_view`, som er uendret og bare har synlige. Jobbsøkerlisten kan
derfor ikke få med usynlige, og et gammelt valg `inkluderSkjulte` i
forespørselen blir ignorert.
Hendelsesoversikten for et WorkOp-treff tar med usynlige, men ikke usynlige
som er slettet. Synlige som er slettet, vises som på vanlige treff. Detaljer
vises bare for hendelsene fra treffgjennomføringen, ikke for for eksempel
varsler, som kan inneholde fødselsnummer. Den viser fødselsnummer bare for
synlige, og navn ikke for personer med adressebeskyttelse. Det gjelder også
aktørfeltet: Når jobbsøkeren selv har svart, er aktøren fødselsnummeret, og
serveren skjermer det på samme måte. Backend sperrer ikke oppmøte for
usynlige. Tellingene i jobbsøkerlisten er uendret. Komponenttester dekker søket, hendelsene og
oppmøtet, og at vanlige treff er uendret. WorkOp kan ikke opprettes i
produksjon, så endringen gir ingen ny eksponering der før piloten.

**Utrulling:** Ingen databaseendring. Viewet for jobbsøkersøket er uendret,
så rekkefølgen på deploy av main og branch spiller ingen rolle.

**Restrisiko:** Eiere ser navn og registreringer for personer som ikke lenger
oppfyller kravene til synlighet, og kan registrere nye opplysninger om dem.
Dette er avgrenset til WorkOp-treff de selv eier.

For usynlige som er slettet, viser «Hendelser» ingenting, heller ikke hvem som
la til eller slettet personen. Det gjelder også vanlige treff, og er som før.
Hendelsene ligger i databasen, så utviklere kan finne dem ved behov. Sletting
er bare mulig før personen har registreringer, så det er ingen
gjennomføringsdata som forsvinner fra visningen.

**Ansvar:** Produkteier og Team Toi.  
**Ny vurdering:** Før pilot.


## Prioriterte avklaringer og tiltak

### Før produksjonspilot

1. Avklar behandlingsgrunnlag og lagringstid for alle gjennomføringsdata.
2. Fjern `JS_HELSE_KAPASITET`, eller dokumenter uttrykkelig hvorfor og hvordan
   opplysningen kan behandles.
3. Fastsett tilgangsmodell, kriterier for WorkOp og serversideavgrensning for
   hvem som kan opprette WorkOp i piloten.
4. Avklar hvem som skal kunne legge seg til som medeier (WO-12).
5. Verifiser hendelseskontraktene og deploy konsumentene før produsenten.
6. Rett at aktivitetskortet ikke avsluttes for jobbsøkere med status «Møtt
   opp» eller «Fått jobb» når treffet fullføres.
7. Lag standardtekst for WorkOp og gjør svarsiden tydelig på WorkOp-dagen
   (WO-11).
8. Etabler avstemming, overvåking og reserveprosedyre for arrangementsdagen.

### Før bred produksjonssetting

1. Evaluer forståelse, frivillighet, registreringsfeil og avvik fra piloten.
2. Verifiser sletting og retting med faktiske pilotdata.
3. Vurder om tilgangsmodellen og supporttilgangen kan snevres inn.
4. Kontroller at opprettede WorkOp-er faktisk har fulgt WorkOp-prosessen, og
   vurder om en egen opprettertilgang skal beholdes.
5. Dokumenter hvilke restrisikoer produkteier aksepterer.
6. Sett dato og ansvarlig for jevnlig revurdering av ROS-en.

## Forhold som ikke er egne risikopunkter

Følgende behandles som ordinære kvalitetskrav eller midlertidige
utrullingsoppgaver:

- tokenvalidering, inputvalidering, parameterisert SQL og vanlige
  sikkerhetsoppdateringer
- enhetstester, komponenttester og ende-til-ende-tester som normalt følger
  endringene
- midlertidige miljøsperrer og feature toggles før produksjonspiloten
- farger, etiketter og anbefalingstekster i brukergrensesnittet
- grensen på 100 jobbsøkere, så lenge WorkOp-konseptet har vesentlig færre
  deltakere. Risikoen må vurderes på nytt dersom konseptet skaleres.

## Rød sone – beslutninger som ikke kan tas av utviklingsteamet alene

- [ ] Behandlingsgrunnlag og lagringstid for oppmøte, interesser, vurderinger
      og hendelser
- [ ] Om helse-/kapasitetsnotatet kan brukes
- [ ] Hvem som har tjenstlig behov i pilot og ordinær produksjon
- [ ] Hvem som kan velge WorkOp-kategorien, og hvilke minstekrav WorkOp-prosessen
      skal oppfylle
- [ ] Hvem som kan legge seg selv til som medeier
- [x] Om WorkOp-navnet kan stå i SMS og e-post. Avklart 22.09.26: navnet
      beholdes.
- [ ] Når arbeidsgiveridentiteten eventuelt kan vises til jobbsøkeren

## Foreslåtte endringer og tillegg i ROS-verktøyet

Seksjonen sammenligner med utskriften fra ROS-verktøyet 30.09.26. For hvert
tiltak står et forslag til beskrivelse og status i koden. Status er en av
tre: på plass, delvis eller ikke startet.

### 31566 – WorkOp-navnet i SMS eller e-post (WO-03)

Vurderingen 22.09.26 bør registreres som akseptert restrisiko, med navn på den
som aksepterte. Nytt tiltak som dokumenterer det som faktisk reduserer
risikoen:

**Varselet inneholder bare nødvendig informasjon**  
SMS og e-post sier bare at mottakeren er invitert til en WorkOp, eller at en
WorkOp er endret eller avlyst. Tid, sted, arbeidsgivere og svarstatus står bare
etter innlogging. Endringsvarselet nevner hvilke felt som er endret, ikke de
nye verdiene. Nye maler vurderes mot dette før de tas i bruk.  
Status: på plass.

### 31567 – vurderinger og notater (WO-04)

**Ikke mulig å velge spesifikke helseopplysninger som tilbakemelding/notat**  
Notatlista har ingen valg for diagnose, funksjonsnivå eller annen konkret
helseinformasjon. Valget «Helse eller kapasitet» fjernes før produksjon, eller
beholdes bare hvis behandlingsgrunnlag, tilgang og slettefrist er dokumentert.  
Status: delvis. «Helse eller kapasitet» finnes fortsatt. Også et generelt valg
om helse er en helseopplysning, så beskrivelsen bør ikke si at løsningen
hindrer helseopplysninger så lenge valget finnes.

**Tilbakemeldinger (notater) er ikke fritekst**  
Arrangøren velger tilbakemeldinger fra en fast liste som fag og personvern har
godkjent. Løsningen har ingen fritekstfelt for vurderinger, så arrangøren kan
ikke skrive inn sensitive eller diskriminerende formuleringer. Hvert valg viser
om utsagnet kommer fra arbeidsgiveren eller jobbsøkeren. Endringer i lista
krever ny vurdering.  
Status: på plass.

### 31568 – lagring, retting og sletting (WO-05)

**Funksjonalitet for å korrigere feilklikk og -registreringer**  
Arrangøren kan angre hver registrering i treffgjennomføringen: oppmøte,
interesse, intervjufordeling, vurdering, notater, avtalt 2. intervju og
jobbtilbud. Oppmøtet kan ikke fjernes før registreringene som bygger på det er
fjernet, så det ikke blir liggende data uten grunnlag. Retting av oppmøte,
vurdering, notater, 2. intervju og jobbtilbud lagres som hendelser med
tidspunkt og Nav-ident.  
Status: på plass.

Nytt tiltak, fordi risikoen også gjelder lagringstid:

**Slettefrist for gjennomføringsdata**  
Oppmøte, deltakernummer, interesser, intervjufordeling, vurderinger og
tilhørende hendelser slettes automatisk en fastsatt tid etter at treffet er
fullført eller avlyst. Behandlingsansvarlig fastsetter fristen ut fra
formålet. En planlagt jobb sletter dataene, og teamet kontrollerer jevnlig at
jobben virker.  
Status: ikke startet. Fristen er en beslutning i rød sone.

### 31569 – samtidige eller hurtige registreringer (WO-07)

Beskrivelsen mangler. Forslag:

> Under WorkOp registrerer én eller flere arrangører oppmøte, interesse og
> vurderinger i høyt tempo, ofte på hver sin maskin. Et feilklikk kan markere
> feil person som møtt eller registrere interesse hos feil arbeidsgiver.
> Endrer to arrangører samme vurdering samtidig, vinner den siste lagringen, og
> den første endringen forsvinner uten varsel. Konsekvensen er uriktige
> opplysninger om jobbsøkeren, feil intervjufordeling og feil grunnlag for
> oppfølgingen.

Nye tiltak:

**Individuell oppmøteregistrering**  
Oppmøte registreres én person om gangen, med navnet synlig.
Løsningen har ikke «marker alle», så ingen blir registrert som møtt uten at
arrangøren har sett personen.  
Status: på plass.

**Tydelig lagringsstatus per registrering**  
Hver avkrysning lagres for seg og viser om den lagres, er lagret eller har
feilet. Ved feil står krysset uendret, og arrangøren må krysse på nytt.
Arrangøren kan ikke gå til neste steg mens lagringer pågår.  
Status: på plass.

**Samtidige endringer overskriver ikke hverandre uten varsel**  
Endrer to arrangører samme vurdering, får den som lagrer sist beskjed om at
vurderingen er endret av en annen, og ser den nye versjonen før hen lagrer på
nytt.  
Status: ikke startet. Backend lar skrivingene vente på hverandre, men den
siste lagringen vinner.

### 31570 – nedetid under WorkOp-arrangement (WO-08)

Beskrivelsen er «Hvorfor?». Forslag:

> WorkOp bruker løsningen under selve arrangementet. Arrangøren registrerer
> oppmøte og deltakernummer, fordeler rom og rotasjon og fordeler
> speedintervjuer mens jobbsøkere og arbeidsgivere venter. Et vanlig treff
> bruker løsningen mest før og etter. Er løsningen, innloggingen eller nettet i
> lokalet nede, stopper arbeidet i lokalet. Arrangøren kan også tro at noe er
> lagret når det ikke er det.

Nye tiltak:

**Forhåndssjekk av lokalet**  
Arrangøren tester nett, innlogging og skriver i lokalet før arrangementet og
har mobilt nett som reserve.  
Status: rutine, ikke i løsningen.

**Utskrift som reserve**  
Rom- og rotasjonsplanen og intervjufordelingen skrives ut så snart de er
klare. Da kan rotasjonen og intervjuene gå som planlagt selv om løsningen
faller ut.  
Status: utskriftene finnes. Rutinen er ikke beskrevet.

**Reserveprosedyre med etterregistrering**  
Er løsningen nede, fører arrangøren oppmøte på en papirliste med initialer.
Arrangøren registrerer i løsningen når den er oppe igjen, og makulerer
papiret samme dag. Prosedyren står i retningslinjene for WorkOp.  
Status: ikke startet.

### 31571 – forveksling mellom vanlig treff og WorkOp (WO-09)

**Tilgjengelige, tydelige retningslinjer for treff versus WorkOp**  
Retningslinjene beskriver når et treff skal være WorkOp: målgruppen
(jobbsøkere under 30 år), metoden med rotasjon og speedintervjuer, og hvem som
kan opprette det. De forklarer hva som er ulikt fra et vanlig treff, blant
annet at arbeidsgiverne er skjult for jobbsøkerne og at kategorien ikke kan
endres etterpå. Løsningen lenker til retningslinjene der WorkOp opprettes.  
Status: ikke lenket fra løsningen.

**Markere tydelig for eier som oppretter at de jobber med WorkOp under hele
prosessen**  
Merkelappen «WorkOp» vises i overskriften på treffsiden og på kortet i søket.
Kategorien kan ikke endres etter oppretting, så et treff kan ikke bytte type
ved en feil senere. Eieren bekrefter kategorivalget når treffet opprettes.  
Status: delvis. Merkingen og den låste kategorien finnes. Oppretting skjer
uten bekreftelse.

**Markere tydelig på svarsiden at det er WorkOp**  
Svarsiden sier at treffet er en WorkOp, forklarer kort hva det betyr, og spør
om jobbsøkeren kommer på WorkOp-en.  
Status: ikke startet. Arbeidsgiverne er skjult, men svardialogen har samme
tekst som for vanlige treff.

Nytt tiltak:

**Varsel når jobbsøkeren er over 30 år**  
Når eieren legger til en jobbsøker over 30 år på en WorkOp, viser løsningen et
varsel om at WorkOp er for jobbsøkere under 30 år. Eieren kan likevel legge
til personen.  
Status: ikke startet.

### 31572 – utskrifter og deltakernummer (WO-10)

**Ikke skrevet navn, kun tilfeldig deltakernummer og initialer**  
Foreslått ny tittel: «Utskrifter viser bare deltakernummer og initialer».
Utskriftene viser jobbsøkerne med deltakernummer og initialer, aldri fullt
navn, fødselsnummer, kontaktinformasjon eller vurderinger. Deltakernummeret
deles ut fortløpende når oppmøtet registreres.  
Status: på plass. Nummeret er ikke tilfeldig, så ordet «tilfeldig» bør ut av
tittelen.

Nye tiltak:

**Innsamling og makulering**  
Arrangøren samler inn alle utskrifter når arrangementet er ferdig, og
makulerer dem samme dag. Utskriftene tas ikke med ut av lokalet og deles ikke
digitalt.  
Status: rutine, ikke beskrevet.

**Tidspunkt på utskriften**  
Utskriften viser når den ble skrevet ut, så arrangøren ser om en plan er
utdatert.  
Status: ikke startet.

### 31573 – formøte og WorkOp-dag (WO-11)

Beskrivelsen spør om risikoen er relevant i dag. Forslag til beskrivelse:

> WorkOp har et formøte på en annen dag enn selve WorkOp-dagen. Jobbsøkeren
> svarer én gang i løsningen og kan tro at svaret gjelder formøtet, eller møte
> opp feil dag. Da uteblir jobbsøkere, og svarstatus og oppmøte stemmer ikke
> med det jobbsøkeren mente. Løsningen har ett tidspunkt og ett svar per treff,
> og formøtet står bare i arrangørens fritekst.

**Standardteksten som kopieres fra WorkOp-mal tydeliggjør at det er to
arrangement på ulike datoer**  
Standardteksten har ett avsnitt for formøtet og ett for WorkOp-dagen, hver med
dato, klokkeslett og sted. Den sier hva som skjer på hvert av dem, og at svaret
i løsningen gjelder WorkOp-dagen.  
Status: ikke i løsningen.

**Standardtekst-mal gjøres tilgjengelig i løsningen for eiere**  
Når eieren oppretter en WorkOp, er beskrivelsen forhåndsutfylt med
standardteksten. Eieren fyller inn tid og sted for formøtet og kan tilpasse
resten. En forhåndsutfylt tekst blir brukt oftere enn en tekst eieren må
kopiere selv.  
Status: ikke startet.

**Jobbsøkers svarside er tydelig på at man svarer for WorkOp-dagen og ikke
formøtet**  
Svardialogen spør om jobbsøkeren kommer på WorkOp-dagen og viser datoen. Har
treffet formøte, står det at formøtet er en egen avtale.  
Status: ikke startet. Svardialogen spør «Kommer du på rekrutteringstreffet?».

### 31574 – synlighet i søket (WO-06)

**Backend styrer synlighet i søket, slik at man ikke kan finne det fra
frontend dersom man er datakyndig**  
Backend fjerner WorkOp der den innloggede ikke er eier, før søkeresultatet
sendes til nettleseren. Det gjelder både treff-lista og antallene i filtrene.
En ikke-eier finner derfor ikke treffet, heller ikke ved å se på
nettverkstrafikken eller kalle API-et direkte. Utviklere ser alle treff for å
kunne gi support. Treffvelgeren i kandidatsøket bruker samme søk.  
Status: på plass.

Risikoen dekker bare søket. Direkte lenke og underressurser hører til WO-06 og
bør registreres, eller tas inn i beskrivelsen her.

### 31575 – markedskontakt legger seg selv til som eier (WO-12)

Risikoen har ingen beskrivelse eller tiltak. Forslag til beskrivelse:

> Alle med arbeidsgiverrettet rolle kan legge seg selv til som medeier på et
> treff, også en WorkOp, uten at eierne godkjenner det. Som medeier får de se
> jobbsøkerne, svarstatus og treffgjennomføringen, og kan sende invitasjoner.
> En markedskontakt som ikke skal jobbe med treffet, kan dermed få tilgang til
> personopplysninger uten tjenstlig behov.

Nye tiltak:

**Bekreftelse som forklarer ansvaret**  
Bekreftelsesdialogen sier at medeier får tilgang til personopplysninger om
jobbsøkerne, og at man bare skal bli medeier når man skal jobbe med treffet.  
Status: delvis. Dialogen nevner tilgangen, ikke ansvaret.

**Sporbar selvinnmelding**  
Hver selvinnmelding lagres som hendelsen «eier lagt til» med Nav-ident og
tidspunkt, og vises i hendelsene på treffet.  
Status: på plass.

**Eierne får beskjed**  
Eksisterende eiere får beskjed når noen legger seg til som medeier, så de kan
fjerne personen hvis det er feil.  
Status: ikke startet.

**Retningslinjer for medeierskap**  
Retningslinjene for WorkOp sier hvem som skal være medeier, og at eieren
fjerner medeiere som ikke skal ha tilgang.  
Status: ikke startet.

**Avgrense hvem som kan legge seg til**  
For eksempel bare ansatte på et kontor som alt er knyttet til treffet, eller
bare etter invitasjon fra en eier. Dette begrenser også den ønskede
selvinnmeldingen fra direkte lenke, og er derfor en beslutning i rød sone.  
Status: ikke avklart.

### 31576 – tekst som bryter med frivillig deltakelse (WO-13)

Forslag til beskrivelse:

> Tekster i invitasjonen, varselet, aktivitetskortet eller svarsiden kan
> oppfattes som at jobbsøkeren må delta, eller at et nei får følger.
> Invitasjonen kommer fra Nav og vises i aktivitetsplanen, der jobbsøkeren også
> har aktiviteter avtalt med veilederen. Arrangørens egen tekst kan også være
> formulert som et krav.

**Retningslinjer og opplæring for WorkOp**  
Arrangørene får skriftlige retningslinjer og opplæring før de oppretter sin
første WorkOp. Retningslinjene sier at deltakelse er frivillig og at et nei
ikke får negative følger. De gir eksempler på formuleringer som skal unngås,
som «du må møte» og «obligatorisk». De dekker også hva som ikke skal
registreres i vurderinger (WO-04), at arbeidsgivernavn ikke skal stå i
fritekst (WO-02), og hvem som skal være medeier (WO-12).  
Status: ikke i løsningen.

Nytt tiltak:

**Faste tekster er gjennomgått for frivillighet**  
Fag går gjennom SMS, e-post, aktivitetskort og svarside før produksjon og ved
hver endring. Svarsiden sier at deltakelse er frivillig.  
Status: delvis. Svardialogen sier «Det er frivillig å delta.». Tekstene er
ikke dokumentert gjennomgått.

### Risikoer som ikke finnes i utskriften

Disse risikoene står i dette dokumentet, men har ingen ID i utskriften:

- WO-01, utilstrekkelig beslutningsgrunnlag når arbeidsgiverne skjules.
  Registrer den, eller slå den sammen med 31576.
- WO-02, arbeidsgivere avsløres mens de skal være skjult. Registrer den. De
  viktigste tiltakene er på plass i koden (se WO-02).
- WO-06, tilgang uten tjenstlig behov utover søket. Registrer den, eller
  utvid 31574.
- WO-14, usynlige jobbsøkere vises og kan registreres i
  treffgjennomføringen. Registrer den. Forslag til beskrivelse:

  > Jobbsøkere som blir usynlige etter at de er lagt til, vises fortsatt med
  > navn i treffgjennomføringen, vurderingen og hendelsesoversikten, slik at
  > eierne kan fullføre og rette registreringene. De kan også registreres som
  > møtt. Usynlige som er slettet, vises aldri, og personer med
  > adressebeskyttelse vises uten navn. Jobbsøkerlisten viser usynlige bare som et samlet tall.
  > Gjelder bare WorkOp. Vanlige treff er uendret.

Dokumentet er gjennomgått mot implementasjonen i de berørte repoene
2026-09-30. Risikovurderingene må oppdateres når beslutningene i rød sone er
tatt, etter produksjonspiloten og før vesentlige endringer i
treffgjennomføringen.
