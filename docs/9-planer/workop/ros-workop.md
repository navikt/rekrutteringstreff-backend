# ROS for WorkOp og treffgjennomføring

Dette dokumentet beskriver risikoer som kommer i tillegg til
[`ros-pilot.md`](ros-pilot.md) når WorkOp og fanen «Treffgjennomføring og
oppfølging» tas i bruk.

WorkOp er et rekrutteringstreff der jobbsøkere roterer mellom arbeidsgivere,
interesse kartlegges og aktuelle speedintervjuer fordeles. Arbeidsgivernes
identitet skjules for jobbsøkerne i den perioden produktet har bestemt at de
skal være skjult.

ROS-en er avgrenset til risikoer som består etter at ordinær sikker utvikling,
kodegjennomgang og testing er gjennomført. Vanlige implementasjonsfeil og
midlertidige feature toggles er derfor ikke egne risikopunkter.

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

## Dataflyt og tillitsgrenser

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
foreløpige vurderinger som må godkjennes av risikoeier.

| ID | Risiko | S | K | Viktigste tiltak | Relatert pilot-ROS |
| --- | --- | ---: | ---: | --- | --- |
| WO-01 | Jobbsøkeren får ikke tilstrekkelig beslutningsgrunnlag når arbeidsgiverne skjules | 3 | 3 | Forklare format, bransjer, forventninger og frivillighet uten å røpe arbeidsgiverne | 27485, 27385, 27273 |
| WO-02 | Arbeidsgivere avsløres mens de skal være skjult | 2 | 3 | Skjule i backend på alle innbyggerendepunkter og hindre navn i brukerrettet fritekst og varsler | 27383, 27215 |
| WO-03 | WorkOp-navnet i SMS eller e-post røper arbeidsrettet oppfølging | 2 | 3 | Personvernvurdere ordlyden og begrense varslet til nødvendig informasjon | 27215, 27383 |
| WO-04 | Vurderinger og notater kan inneholde helseopplysninger eller gi grunnlag for diskriminering | 3 | 4 | Faglig og personvernfaglig godkjent kodeverk, ingen fritekst og ingen sekundærbruk | 27219, 27216, 27227 |
| WO-05 | Gjennomføringsdata lagres lenger enn nødvendig eller kan ikke korrigeres og slettes riktig | 3 | 4 | Fastsette behandlingsgrunnlag, slettefrist og rettingsløp for både tilstand og hendelser | 27486, 27227 |
| WO-06 | Personer uten tjenstlig behov får tilgang til WorkOp- og gjennomføringsdata | 2 | 4 | Ressursbasert tilgang i backend, pilotavgrensning, auditlogg og tilgangsrevisjon | 27217, 27215 |
| WO-07 | Feil eller samtidige registreringer gir uriktig oppmøte, interesse eller vurdering | 3 | 3 | Individuell registrering, låsing, tydelig lagringsstatus og sporbart rettingsløp | 27388, 27486 |
| WO-08 | Nettverksbrudd under arrangementet gir manglende eller usikre registreringer | 2 | 3 | Forhåndssjekk, synlige feil, kontrollert reserveprosedyre og etterfølgende avstemming | 27386, 28065 |
| WO-09 | WorkOp-kategori eller hendelser blir inkonsistente mellom tjenestene | 2 | 3 | Kontraktstester, konsument-før-produsent-utrulling, idempotens, målinger og avstemming | 27381, 27383, 27386 |
| WO-10 | Utskrifter og deltakernummer gjør jobbsøkere identifiserbare i lokalet | 2 | 3 | Dataminimerte utskrifter, kontrollert utdeling, versjonsmerking, innsamling og makulering | 27215, 27227 |

## WO-01 – utilstrekkelig beslutningsgrunnlag

**Risiko:** Når arbeidsgiverne skjules, kan jobbsøkeren mangle informasjon som
er nødvendig for å vurdere om WorkOp er relevant. Invitasjonen kan oppfattes
som lite transparent eller som noe man plikter å delta på.

**Tiltak:**

- Beskriv formålet, gjennomføringen, aktuelle bransjer og hvilke typer
  jobbmuligheter som finnes, uten å oppgi arbeidsgivernavn.
- Opplys tydelig at deltakelse er frivillig, hvordan man svarer nei, og at et
  avslag ikke får negative følger.
- Oppgi kontaktpunkt for spørsmål før svarfristen.
- Send praktisk informasjon og informasjon om eventuelt formøte i tide.
- Bruk pilotintervjuer til å kontrollere om informasjonen faktisk blir forstått.

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

- La backend være autoritativ for skjulingen; frontend er kun et ekstra lag.
- Bruk samme regel på sammensatt treffrespons og alle separate
  arbeidsgiverendepunkter.
- Kontroller at tittel, beskrivelse, innlegg, aktivitetskort og varsler ikke
  inneholder arbeidsgivernavn i skjulingsperioden.
- Test direkte API-kall og innbyggerreisen, ikke bare visuell skjuling.
- Dokumenter når skjulingen opphører. Inntil dette er besluttet skal
  arbeidsgiverne forbli skjult i innbyggerflatene.

**Restrisiko:** En arrangør kan skrive inn et arbeidsgivernavn i et fritekstfelt.
Publiseringskontroll og tydelig veiledning må redusere denne risikoen.

**Ansvar:** Team Toi og produkteier.  
**Ny vurdering:** Før pilot og ved endring av brukerrettede felter eller API-er.

## WO-03 – WorkOp-navnet røper kontekst

**Risiko:** SMS og e-post kan vises på en låst skjerm eller leses av andre.
Ordet «WorkOp» kan knytte mottakeren til arbeidsrettet oppfølging.

Det er et produktønske at WorkOp skal omtales som WorkOp i SMS og e-post.
Dette må derfor behandles som en bevisst personvernavveiing, ikke som en
utilsiktet teknisk lekkasje.

**Tiltak:**

- Få ordlyden godkjent av fag og personvern før produksjonspiloten.
- Ikke ta med arbeidsgivere, vurderinger, oppmøte, svarstatus eller annen
  detaljert informasjon i varslet.
- Lenke til innlogget flate for øvrig informasjon.
- Dokumenter hvorfor nytten av å bruke WorkOp-navnet veier opp for
  eksponeringsrisikoen.

**Restrisiko:** Andre kan se at mottakeren er invitert til WorkOp. Restrisikoen
må aksepteres eksplisitt dersom navnet beholdes.

**Ansvar:** Produkteier og personvernressurs.  
**Ny vurdering:** Før produksjonspiloten.

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
  kandidatvarsler, logger og sikkerhetskopier.
- Gjennomfør periodisk kontroll av at sletterutinene faktisk virker.

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
- Kontroller jobbsøkerens tilknytning til treffet i backend; kjennskap til en
  treff-id skal ikke være tilstrekkelig.
- Avgrens produksjonspiloten på serversiden til godkjente brukere eller
  kontorer, med en dokumentert stoppmekanisme.
- Gjennomgå tilganger etter piloten og periodisk etter bred utrulling.

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

- Registrer oppmøte individuelt; ikke tilby «marker alle».
- Bruk deltakernummer og en egnet sekundær kontroll ved innsjekk uten å
  eksponere flere personopplysninger enn nødvendig.
- Serialiser konkurrerende skrivinger og hindre at eldre svar overskriver
  nyere data.
- Vis tydelig om en endring lagres, er lagret eller har feilet.
- Ved feil skal tilstanden forbli uendret, slik at brukeren må utføre
  handlingen på nytt.
- Krev bekreftelse eller gjenåpning før gjennomførte steg endres.
- Sørg for at rettinger gir et forståelig hendelsesspor.

**Restrisiko:** Manuelle feil kan fortsatt skje og må kunne oppdages og rettes
mens treffet pågår.

**Ansvar:** Arrangør for arbeidsrutinen og Team Toi for systemkontrollene.  
**Ny vurdering:** Etter de første gjennomførte pilotene.

## WO-08 – nettverksbrudd under arrangementet

**Risiko:** Treffgjennomføringen brukes i et fysisk lokale der nettverket kan
være ustabilt. Arrangører kan tro at data er lagret, miste oversikten eller
måtte stoppe rotasjon og intervjuplanlegging.

**Tiltak:**

- Kontroller nettverk og nødvendig utstyr i lokalet før arrangementet.
- Vis lagringsfeil ved den aktuelle registreringen og samlet for steget.
- Ikke gå videre fra et steg før ventende lagringer er ferdige.
- Etabler en reserveprosedyre med minst mulig persondata på papir.
- Beskriv hvordan papirregistreringer skal avstemmes, etterregistreres og
  makuleres.
- Følg med på lagringsfeil og responstid under piloten uten personopplysninger
  i metrikker.

**Restrisiko:** Et lengre avbrudd kan forsinke gjennomføringen og kreve manuell
etterregistrering.

**Ansvar:** Arrangør og Team Toi.  
**Ny vurdering:** Etter hver pilot der reserveprosedyren tas i bruk.

## WO-09 – inkonsistens mellom tjenester

**Risiko:** WorkOp-kategori og egne hendelsesnavn går fra
rekrutteringstreff-backend til aktivitetskort og kandidatvarsel. Ved delvis
utrulling, ukjent kategori, konsumentfeil eller manglende hendelse kan
jobbsøkeren få ordinær trefftekst, mangle varsling eller få usynkron status.

**Tiltak:**

- Deploy konsumenter som forstår WorkOp før produsenten begynner å sende
  WorkOp-hendelser.
- Ha kontraktstester for invitasjon, oppdatering, svar, fullføring og avlysning
  på tvers av tjenestene.
- Bruk stabil hendelses-id og idempotent behandling ved gjenlevering.
- Mål antall produserte, mottatte, fullførte og feilede WorkOp-hendelser uten
  personopplysninger som metrikklapper.
- Avstem invitasjoner mot aktivitetskort og varsler, og varsle ved varige
  avvik eller konsumentlag.
- Ved rollback: stopp opprettelse av nye WorkOp-er, men la konsumentene
  behandle allerede publiserte hendelser.

**Restrisiko:** Asynkron behandling gir alltid en periode med midlertidig
ulikhet mellom systemene.

**Ansvar:** Team Toi og eiere av de berørte integrasjonene.  
**Ny vurdering:** Etter produksjonspiloten og ved endring av hendelseskontrakten.

## WO-10 – identifisering via utskrifter

**Risiko:** Deltakernummer og initialer er mindre identifiserende enn navn, men
kan kobles til personer av andre som er til stede. Utdaterte eller gjenglemte
utskrifter kan i tillegg gi feil møteplan eller spre opplysninger etter
arrangementet.

**Tiltak:**

- Bruk bare deltakernummer og initialer; aldri navn, fødselsnummer,
  vurderinger eller kontaktinformasjon.
- Skriv ut én mottakers nødvendige plan per side og begrens antall kopier.
- Merk utskriften med tidspunkt eller versjon, slik at utdaterte planer kan
  trekkes tilbake.
- Ikke fotografer eller del listene digitalt.
- Samle inn og makuler alle utskrifter umiddelbart etter arrangementet.

**Restrisiko:** Deltakere og arbeidsgivere i samme lokale kan fortsatt koble
nummer og initialer til en person.

**Ansvar:** Arrangør.  
**Ny vurdering:** Etter produksjonspiloten.

## Prioriterte avklaringer og tiltak

### Før produksjonspilot

1. Avklar behandlingsgrunnlag og lagringstid for alle gjennomføringsdata.
2. Fjern `JS_HELSE_KAPASITET`, eller dokumenter uttrykkelig hvorfor og hvordan
   opplysningen kan behandles.
3. Godkjenn at WorkOp-navnet brukes i SMS og e-post, med dokumentert
   restrisiko.
4. Fastsett perioden arbeidsgivere skal være skjult, og kontroller alle
   brukerrettede kanaler.
5. Fastsett tilgangsmodell og serversideavgrensning for piloten.
6. Verifiser hendelseskontraktene og deploy konsumentene før produsenten.
7. Etabler avstemming, overvåking og reserveprosedyre for arrangementsdagen.

### Før bred produksjonssetting

1. Evaluer forståelse, frivillighet, registreringsfeil og avvik fra piloten.
2. Verifiser sletting og retting med faktiske pilotdata.
3. Vurder om tilgangsmodellen og supporttilgangen kan snevres inn.
4. Dokumenter hvilke restrisikoer produkteier aksepterer.
5. Sett dato og ansvarlig for periodisk revurdering av ROS-en.

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
  deltakere; risikoen må vurderes på nytt dersom konseptet skaleres

## Rød sone – beslutninger som ikke kan tas av utviklingsteamet alene

- [ ] Behandlingsgrunnlag og lagringstid for oppmøte, interesser, vurderinger
  og hendelser
- [ ] Om helse-/kapasitetsnotatet kan brukes
- [ ] Hvem som har tjenstlig behov i pilot og ordinær produksjon
- [ ] Om WorkOp-navnet kan stå i SMS og e-post
- [ ] Når arbeidsgiveridentiteten eventuelt kan vises til jobbsøkeren

Dokumentet er gjennomgått mot implementasjonen i de fire berørte repoene
2026-09-04. Risikovurderingene må oppdateres når beslutningene i rød sone er
tatt, etter produksjonspiloten og før vesentlige endringer i
treffgjennomføringen.
