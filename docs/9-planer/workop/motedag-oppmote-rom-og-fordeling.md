# Plan: Møtedag for WorkOp – oppmøte, romfordeling og speedintervju

Forslag til flyt og elementer for de tre oppgavene i
[behov-og-prioriteringer.md](../../../../behov-og-prioriteringer.md) (kapittelet «Oppgaver
som må utredes og utvikles»):

1. **Registrere oppmøte** (behov nr. 6, oppgave 1)
2. **Fordele jobbsøkere i grupperom** (behov nr. 7, oppgave 2)
3. **Fordele jobbsøkere til arbeidsgivere for speedintervju** (behov nr. 8, oppgave 3)
4. **Følge opp resultatet per arbeidsgiver** (behov nr. 9, oppgave 4)

Dette er et **design-, flyt- og statusdokument**. Fase A–D er implementert i
frontend (`rekrutteringsbistand-frontend`) med stateful MSW. Backend-delene er
fortsatt en kontraktskisse for `rekrutteringstreff-api`.

---

## Beslutninger (avklart)

| Tema                   | Valg                                                                                                                                                                                                                                                                  |
| ---------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Omfang                 | Kun **WorkOp-treff** (`kategori === WORKOP`). Rom-rotasjon og speedintervju er WorkOp-metodikk.                                                                                                                                                                       |
| Feature toggle         | Samme mønster som Formidlinger-fanen: `getMiljø() !== Miljø.ProdGcp` (vises i lokalt/dev/test, skjult i prod), gated i både `TabsNav.tsx` og `TabsPanels.tsx`. I tillegg gate på `kategori === WORKOP`.                                                               |
| Inngang                | To innganger: (a) **burgermeny** på jobbsøkerkortet for å registrere oppmøte, og (b) en **egen «WorkOp gjennomføring»-fane**.                                                                                                                                         |
| Stegnavigasjon         | Aksel **Stepper** med seks steg. Brukeren kan gå tilbake til steg der forutsetningene er oppfylt. Ved stegbytte flyttes visningen til starten av WorkOp-innholdet.                                                                                                     |
| Aksel-prinsipp         | Bruk Aksel layout-primitives (`VStack`, `HStack`, `HGrid`, `Box`) med spacing tokens. Nye lokale meldinger bruker `LocalAlert` der det passer.                                                                                                                        |
| Persistering           | Én komplett målkontrakt og stateful MSW-handlere dekker alle seks steg. Backend implementerer den samme kontrakten uten å endre frontendtypene.                                                                                                                        |
| Antall rom             | **Avledet: ett rom per arbeidsgiver.** Det er alltid nok rom tilgjengelig, så antallet oppgis ikke manuelt og vises ikke i steg 1. Rotasjonslogikken håndterer fortsatt ubalanse, men den oppstår ikke i praksis.                                     |
| Romfordeling           | **Automatisk** første gang via «Opprett møteplan». I steg 2 kan jobbsøkere flyttes manuelt med dra-og-slipp eller direkte romvalg. «Fordel på nytt» erstatter alle manuelle plasseringer med ny round-robin-fordeling etter bekreftelse.                           |
| Oppmøte-omfang         | Første versjon dekker **kun selve WorkOp-dagen**. Formøte er utenfor omfanget.                                                                                                                                                                                        |
| Oppmøte-lagring        | Oppmøte utledes fra hendelsene `MØTT_OPP`/`ANGRE_MØTT_OPP`. Egen `JobbsøkerStatus` er utenfor omfanget fordi den også krever oppdatering av aktivitetsplanen.                                                                                                         |
| Hvem kan markeres møtt | **Alle** jobbsøkere på lista (ikke begrenset til svarstatus).                                                                                                                                                                                                         |
| Redigerbarhet          | Steg er redigerbare når forutsetningene finnes. Møteoppsettet i steg 1 kan endres også etter opprettelse – tidene styrer bare timeplanen, ikke hvem som sitter hvor – og romplasseringene kan endres i steg 2. Første versjon har ingen egen låse- eller gjenåpningsmekanisme.                                               |
| Oppmøte etter oppsett  | Endret oppmøte skal ikke stille om alle rom i det skjulte. Eksisterende romplasseringer beholdes, ny deltaker legges i rommet med færrest personer, og fjerning berører bare den personen. Brukeren kan deretter flytte manuelt eller velge «Fordel på nytt».       |
| Møteoppsett            | **Starttidspunkt** og **varighet per møte** settes i steg 1. Standardverdier er `10:00` og `10`. Siste minutt av hvert møte brukes til forflytning, så det finnes ingen egen pause. Antall rom er avledet fra antall arbeidsgivere. |
| Rotasjonsplan          | Vises som sammendrag og full matrise i steg 2. To separate utskrifter: **én til arbeidsgiverne** (hvilket rom de skal til, per klokkeslett) og **én til jobbsøkerne** (hvem som kommer til rommet, per klokkeslett). Én mottaker per side.                            |
| Steg 3 (ønsker)        | Registrer **jobbsøkers ønske** om hvilke arbeidsgivere hen vil møte. Kun fremmøtte jobbsøkere inngår.                                                                                                                                                                 |
| Steg 4 (fordeling)     | Arrangør lager intervjurekkefølge per arbeidsgiver. Jobbsøkere kan flyttes over og under sperrelinjen. Rekkefølgen lagres, men ikke konkrete tidspunkter.                                                                                                             |
| Steg 5 (registrering)  | **Registrering av status** per jobbsøker × arbeidsgiver: oppsummering av ønske og speedintervju, vurdering (**Aktuell / Kanskje / Ikke aktuell**), **2. intervju**, **Jobbtilbud** og skrivebeskyttet **Formidlet** fra Formidlinger.                                 |
| Steg 6 (oppsummering)  | **Oppsummering** av hele treffet: nøkkeltall for aktuelle kandidater, andre intervju, øvrige statuser og formidling, samt en tabell per arbeidsgiver. Hver kandidat telles én gang, med den mest positive vurderinga hen har fått.                                    |
| Tilgang                | **Avklart:** samme eier-regel som resten av API-et. Eier eller utvikler har tilgang, kontortilgang alene gir ikke tilgang. Egen hovedansvarlig-modell er forkastet som unødvendig kompleksitet.                                          |

---

## Overordnet flyt

```text
  JOBBSØKER-FANE
      │
      │   Burgermeny på jobbsøkerkort: «Registrer oppmøte»
      │   Handlingsrad: «Marker som møtt (N)» og «Fjern oppmøte (N)»
      ▼
  WORKOP GJENNOMFØRING-FANE  —  Aksel Stepper med 6 steg
  ───────────────────────────────────────────────────
      │
      ▼
  ┌────────────────────┐
  │ Steg 1             │
  │ Oppmøte og oppsett │
  └────────────────────┘
      │   «Opprett møteplan» (auto-fordeler rom og rotasjon første gang)
      ▼
  ┌────────────────────┐
  │ Steg 2             │
  │ Rom og rotasjon    │
  └────────────────────┘
      │   «Neste»
      ▼
  ┌────────────────────┐
  │ Steg 3             │
  │ Ønsker             │
  └────────────────────┘
      │   «Neste»
      ▼
  ┌────────────────────┐
  │ Steg 4             │
  │ Intervjufordeling  │
  └────────────────────┘
      │   «Neste»
      ▼
  ┌────────────────────┐
  │ Steg 5             │
  │ Registrering av    │
  │ status             │
  └────────────────────┘
      │   «Neste»
      ▼
  ┌────────────────────┐
  │ Steg 6             │
  │ Oppsummering       │
  └────────────────────┘

  Tilbake: via Stepper kan man når som helst gå til et fullført steg
```

WorkOp gjennomføring-fanen er en **Aksel Stepper** med seks logiske steg. Innholdet
for det aktive steget rendres under stegindikatoren. Fullførte steg kan besøkes
på nytt (les/rediger), og et lite sammendrag øverst («23 møtt · 5 rom · 5
arbeidsgivere») gir kontekst på tvers av steg.

Steg 2–5 viser samme autolagringsstatus på en fast plass i steghodet:
**Lagret**, **Lagrer …** eller **Lagringsfeil**. Statusendringer skal ikke skyve
matriser, arbeidsgiverkort eller jobbsøkerrader. I steg 2 er Stepper og lokale
navigasjonsknapper ikke interaktive mens en romendring lagres, slik at en
lagringsfeil ikke kan skjules ved at komponenten navigeres bort.

> **Hvorfor Stepper?** Aksel anbefaler Stepper til å «navigere eller vise
> brukerens progresjon mellom steg», og komponenten er interaktiv slik at man kan
> hoppe tilbake til fullførte steg. `Process` er for statiske, ikke-styrbare forløp,
> og `Tabs`/`Accordion` er alternativer hvis vi heller vil vise alle steg samtidig.
> Stepper treffer best på «de forrige stegene må kunne ses». Vi beholder likevel
> **Neste/Tilbake-knapper** i tillegg (Stepper skal ikke være eneste navigasjon).

Stepper skal implementeres som knapp (`Stepper.Step as="button"`) i denne SPA-flyten,
med `aria-labelledby` på selve stepperen. Den vises horisontalt på brede flater og
vertikalt under `md`. Stegene skal bare inneholde stegtittel – interaktivt innhold
rendres under stepperen. Steg uten nødvendige data settes
`interactive={false}` til forutsetningene finnes.

## Gjennomgang mot Aksel/Nav best practice

Planen er i hovedsak i tråd med Aksel og dagens Rekbis-mønstre, men implementasjonen
bør styres av disse kravene:

| Område            | Vurdering                                                                                             | Krav i implementasjon                                                                                                                                                                             |
| ----------------- | ----------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Stepper           | Riktig komponent når bruker kan navigere mellom steg.                                                 | Bruk `as="button"`, `aria-labelledby`, `completed` bare for reelt fullførte steg og `interactive={false}` for steg uten forutsetninger.                                                           |
| Layout            | Riktig å bruke Aksel primitives for layout.                                                           | Bruk `VStack`/`HStack`/`HGrid`/`Box` med `space-*` tokens for spacing og kolonner.                                                                                                                |
| Tabeller/matriser | `Table` er riktig for enkel tabulær data. `DataGrid` er fortsatt preview og bør ikke være førstevalg. | Bruk `Table` med `caption`, `HeaderCell scope="row"/"col"`, maks ett interaktivt element per celle og tydelig `aria-labelledby` for skjulte checkbox-labels.                                      |
| Lokale meldinger  | Nye lokale infomeldinger bør bruke dagens Aksel-komponenter.                                          | Bruk `LocalAlert` for lokale info-/warning-meldinger der kodebasen tillater det, og unngå å introdusere nye `Alert`-flater uten grunn.                                                            |
| Personvern        | Planen har riktig retning med fiktive mockdata.                                                       | Vis fødselsnummer kun der det trengs (forenklet jobbsøkerliste i steg 1) og logg det aldri. Ikke legg inn notatfelt i v1. Mockdata skal være åpenbart syntetisk (ingen realistiske fødselsnumre). |
| Tilgang           | Frontend-gating er nødvendig, men ikke tilstrekkelig.                                                 | Backend håndhever `kategori === WORKOP`, eierskap og rolle server-side. Kontortilgang alene er ikke nok. Frontend bruker det samme autoritative tilgangsresultatet.              |

---

## Inngang og navigasjon

### 1. Burgermeny i Jobbsøker-fanen (registrere oppmøte)

Oppmøte registreres der man allerede jobber med deltakerne. Burgermenyen finnes
i dag i `JobbsokerKortValg.tsx` (Aksel `ActionMenu` med `MenuElipsisVerticalIcon`,
punktene «Endre svar» og «Slett»). Vi legger til:

- **«Registrer oppmøte»** / **«Fjern oppmøte»** (toggle) som et nytt
  `ActionMenyPunkt`.
- Punktet er feature-togglet som resten av WorkOp gjennomføring: vises kun for WorkOp-treff
  og i ikke-prod (`kategori === WORKOP` og `getMiljø() !== Miljø.ProdGcp`).
- Kortet får en synlig markør når personen er møtt (f.eks. en Aksel `Tag`
  «Møtt», på linje med `JobbsøkerStatusTag`).
- **«Marker som møtt (N)»** og **«Fjern oppmøte (N)»** i `JobbsøkerHandlingsrad`
  for å registrere oppmøte på mange samtidig, basert på avkrysningene i lista.
  Begge sender ett `PUT`-kall per person **sekvensielt** (samme endepunkt som
  burgermenyen) og tømmer valget når alle er registrert. Tellerne viser bare de
  valgte som faktisk endres, så «Marker som møtt» hopper over dem som allerede
  er møtt, og omvendt.
- Fjerning krever bekreftelse, siden den sletter ønsker, intervjuplasser og
  vurderinger. Dialogen er den samme `FjernOppmøteBekreftelse` som brukes for
  én person, men får **summen** av registreringene for alle de valgte, slik at
  konsekvensen vises samlet før noe kjøres.
- Avkrysningsboksen i `JobbsøkerKort` var låst til `status === LAGT_TIL` (den
  var laget for invitasjonsflyten). På WorkOp-treff åpnes den for alle
  statuser, siden oppmøte er ortogonalt til svarstatus. «Inviter»-knappen
  påvirkes ikke, fordi den allerede filtrerer på `LAGT_TIL`.

Oppmøte er **ortogonalt** til invitasjonsstatusen (`LAGT_TIL → INVITERT →
SVART_JA …`) – en person kan være «møtt» uansett svarstatus. I første versjon
registreres oppmøte **kun som en hendelse** (ikke som en ny `JobbsøkerStatus`).
Egen jobbsøkerstatus er utenfor omfanget – se «Oppmøte lagret som hendelse».

### 2. Ny «WorkOp gjennomføring»-fane

Ny verdi i `RekrutteringstreffTabs` (i
[Rekrutteringstreff.tsx](../../../../rekrutteringsbistand-frontend/app/rekrutteringstreff/%5BrekrutteringstreffId%5D/_ui/Rekrutteringstreff.tsx)),
plassert etter `ARBEIDSGIVERE`:

```
OM_TREFFET | JOBBSØKERE | ARBEIDSGIVERE | WORKOP_GJENNOMFØRING | (FORMIDLINGER) | HENDELSER
```

Synlighetsregel (speiler Formidlinger-fanen):

```ts
const erProd = getMiljø() === Miljø.ProdGcp;
const erWorkOp =
  rekrutteringstreff.kategori === RekrutteringstreffKategori.WORKOP;
const visWorkOpGjennomføring = !erProd && erWorkOp && harWorkOpTilgang;
```

Regelen legges i både `TabsNav.tsx` (fane-knappen) og `TabsPanels.tsx`
(fane-panelet). `harWorkOpTilgang` skal bygge på eksplisitt hovedansvar, ikke
kontortilhørighet.

### 3. Aktivt steg i URL-en

Stegvelgeren holder tilstanden i URL-en, på samme måte som fanevalget
(`visFane`). Under et treff sitter flere veiledere på hver sin skjerm, og et
steg må kunne deles i en melding uten å beskrive veien dit. En utilsiktet
oppfriskning skal heller ikke kaste deg tilbake til start.

| Parameter | Verdier | Merknad |
| --- | --- | --- |
| `visSteg` | `1`–`6` | Utelates på steg 1 (`clearOnDefault`), så adressen holdes ren |

Som resten av appen skriver den med nuqs' standard `history: 'replace'`.
Stegbytte lager altså ikke egne oppføringer i nettleserhistorikken – det
speiler fanebyttet, og gjør at tilbakeknappen fortsatt fører ut av treffet
framfor å vandre bakover gjennom seks steg.

**Klemming av ugyldige steg.** Verdien kan komme fra en delt lenke, et bokmerke
eller et håndredigert adressefelt, og kan peke på et steg treffet ikke har
kommet til. `nærmesteTilgjengeligeSteg` i
[workopSteg.ts](../../../../rekrutteringsbistand-frontend/app/rekrutteringstreff/%5BrekrutteringstreffId%5D/_ui/workop/workopSteg.ts)
går bakover til første steg som faktisk er tilgjengelig, framfor å vise en tom
side eller kaste brukeren helt til start. URL-en rettes deretter opp, slik at
adressen viser det man faktisk ser på. Ikke-numeriske verdier faller tilbake
til steg 1.

Tilgjengelighetsregelen (`erStegTilgjengelig`) bor samme sted og brukes både av
klemmingen og av `interactive`-flagget på stegvelgeren, så en lenke aldri kan
nå et steg man ikke kunne ha klikket seg til.

---

## Steg 1 – Oppmøte og oppsett

**Mål:** Registrere hvem som møtte, og sette opp rammene for møtene (tider)
før rotasjonen starter.

**Elementer:**

- **Forenklet jobbsøkerliste** – kun **deltakernummer, fornavn, etternavn og
  fødselsnummer** (ikke full kort-stil). Lista sorteres fortløpende på
  deltakernummer, slik at den leses som kortbunken som er delt ut. Hver rad har en «Fjern oppmøte»-knapp (speiler
  burgermeny-handlingen). **Alle** jobbsøkere kan markeres som møtt, uavhengig av
  svarstatus.
- **Teller:** «X møtt av Y påmeldte».
- **Skrolleindikator** under oppmøtelista når det finnes flere rader enn de som
  vises, med tilsvarende tekst for skjermlesere.
- **Liste over arbeidsgivere** – deltakende arbeidsgivere (typisk 5), gjenbruker
  `ArbeidsgiverListeItem`. Teller «Z arbeidsgivere».
- **Møteoppsett** – felter som styrer tidsplan og romoppsett for rotasjonen:
  - **Starttidspunkt** (gjenbruk eksisterende `TimeInput` hvis den passer,
    ellers Aksel `TextField`), standard `10:00`.
  - **Varighet per møte** i minutter (én runde / presentasjon), standard `10`.
    Siste minutt brukes til forflytning til neste rom.
  - **Antall rom** vises ikke i skjemaet. Det er alltid ett rom per
    arbeidsgiver, så antallet trenger verken oppgis eller bekreftes.
- Oppmøtelista har fast maks-høyde. Når det finnes flere jobbsøkere lenger ned,
  vises en **skrolleskygge** i bunnen i stedet for en hjelpetekst. Skyggen
  bruker Aksel-token og virker derfor i både lys og mørk modus. Skjermlesere får
  fortsatt den samme beskjeden via en `sr-only`-tekst.
- Før møteplanen finnes viser **«Opprett møteplan»**. Handlingen lagrer
  møteoppsettet, fordeler de fremmøtte automatisk og jevnt med round-robin,
  genererer rotasjonsplanen og går til steg 2.
- Når møteplanen finnes kan tidene fortsatt endres. Endringer lagres med
  **«Lagre endringer»** og oppdaterer bare timeplanen – romfordelingen beholdes
  som den er. Steget viser i tillegg **«Gå til romfordeling»**, som går til steg
  2 uten lagring eller ny beregning.
- Oppretting er deaktivert til minst én jobbsøker er registrert møtt og minst én
  arbeidsgiver finnes. Oppmøtet låses ikke.

- **Deltakernummer** tildeles når jobbsøkeren registreres møtt, starter på 1 og
  øker fortløpende. Nummeret svarer til det fysiske kortet som deles ut i døra,
  følger personen resten av dagen og gjenbrukes aldri av noen andre. Se
  [Deltakernummer](#deltakernummer) for regelen backend skal implementere.

**Empty state:** Hvis ingen er markert som møtt: informasjon om at oppmøte
registreres via burgermenyen i Jobbsøker-fanen (med lenke/knapp tilbake dit).

---

## Steg 2 – Rom og rotasjon

**Mål:** Tilpasse romfordelingen effektivt og vise arbeidsgivernes rotasjon mellom
rommene, som romvertene bruker under presentasjonene.

Romfordelingen opprettes automatisk i steg 1 basert på antall rom. Steget er en
redigerbar arbeidsflate:

- En jobbsøker kan dras til et annet rom. Gyldige målrom markeres visuelt ved
  hover, og jobbsøkeren legges alltid sist i målrommet.
- Tastatur- og klikkfallback er en Aksel `ActionMenu` kalt **«Flytt til rom»**.
  Brukeren velger målrom direkte i stedet for å måtte klikke gjennom naborom eller
  skrive og validere et romnummer.
- Flytting lagres optimistisk via `PUT /motedag/romfordeling`. Ved feil rulles
  plasseringen tilbake og en lokal feil vises.
- **«Fordel på nytt»** krever bekreftelse og erstatter alle manuelle plasseringer
  med en ny, full round-robin-fordeling. Ønsker, intervjufordeling og vurderinger
  beholdes.

**Elementer:**

- **Rom vist som kolonner/kort** (Aksel `HGrid`/`Box`/`VStack`), hvert rom lister
  sine jobbsøkere og fungerer som droppsone.
- **Arbeidsgiver-rotasjon:** startposisjon per arbeidsgiver (standard: arbeidsgiver
  _i_ → posisjon _i_). Systemet genererer en **rotasjonsplan** med klokkeslett
  basert på møteoppsettet fra steg 1.
- **Rotasjonsplan:** vises som et kort sammendrag i steget, med hele matrisen
  (klokkeslett per runde og rom) rett under. Matrisen er arrangørens oversikt og
  har ingen egen utskrift.
- **To utskrifter:** «Utskrift til arbeidsgivere» og «Utskrift til jobbsøkere»
  åpner hver sin Aksel `Modal` med **«Skriv ut»-knapp**.
- **Primærknapp «Neste»** → steg 3, i tillegg til sekundærhandlingen
  **«Fordel på nytt»**.

Ved endret oppmøte etter møteoppsett beholdes eksisterende romplasseringer. En ny
deltaker legges i rommet med færrest personer, og en fjernet deltaker tas bare ut
av sitt rom. Brukeren kan deretter flytte enkeltpersoner manuelt eller velge
«Fordel på nytt» hvis hele fordelingen skal beregnes på nytt.

### Rotasjonslogikk

La `R` = antall rom og `E` = antall arbeidsgivere. Rotasjonen skjer over
`P = maks(R, E)` posisjoner: posisjon `0 … R-1` er rommene, og eventuelle
posisjoner `R … E-1` er **venteplasser** (benk). Hver arbeidsgiver har en
`startPosisjon` (standard: arbeidsgiver på indeks `i` starter i posisjon `i`).

- Runde `t` (t = 0, 1, …, P-1): arbeidsgiverens posisjon = `(startPosisjon + t) mod P`.
  Er posisjonen et rom, presenterer arbeidsgiveren der; er den en venteplass,
  **venter** arbeidsgiveren den runden.
- Etter `P` runder har hver arbeidsgiver besøkt alle rom (møtt alle grupper).

Tre tilfeller:

- **`R = E`** (normalt): alle presenterer hver runde, ingen venter (`P = R = E`).
- **`R > E`** (flere rom enn arbeidsgivere): noen rom står tomme i enkelte
  runder (`P = R`).
- **`R < E`** (færre rom enn arbeidsgivere): de `E - R` overskytende
  arbeidsgiverne **venter** hver runde og roterer inn senere (`P = E`, dvs. flere
  runder).

**Klokkeslett per runde** beregnes fra møteoppsettet i steg 1: runde 1 starter på
`starttidspunkt` og varer `varighet per møte`; neste runde starter når den
forrige slutter. Siste minutt av møtet brukes til forflytning.

### Rotasjonsmatrise og utskrifter

Rotasjonsmatrisen vises på skjermen i steg 2 som arrangørens oversikt. Eksempel
med start `10:00` og varighet `10 min`:

| Klokkeslett | Rom 1          | Rom 2          | Rom 3          | Rom 4          | Rom 5          |
| ----------- | -------------- | -------------- | -------------- | -------------- | -------------- |
| 10:00–10:10 | Arbeidsgiver A | Arbeidsgiver B | Arbeidsgiver C | Arbeidsgiver D | Arbeidsgiver E |
| 10:10–10:20 | Arbeidsgiver E | Arbeidsgiver A | Arbeidsgiver B | Arbeidsgiver C | Arbeidsgiver D |
| 10:20–10:30 | Arbeidsgiver D | Arbeidsgiver E | Arbeidsgiver A | Arbeidsgiver B | Arbeidsgiver C |
| …           | …              | …              | …              | …              | …              |

Er det færre rom enn arbeidsgivere, får matrisen en **«Venter»-kolonne** som
viser hvem som sitter over hver runde.

Matrisen deles i to utskrifter som deles ut på papir, med **én mottaker per
side**:

- **Utskrift til arbeidsgivere:** én seksjon per arbeidsgiver med klokkeslett
  og hvilket rom hen skal til. Arbeidsgiveren trenger ikke navnene på
  jobbsøkerne, så de tas ikke med.
- **Utskrift til jobbsøkere:** én seksjon per rom med jobbsøkerne som sitter
  der – én per linje, slik at lista er lett å lese på papir – og hvilken
  arbeidsgiver som kommer til hvilket klokkeslett.

Begge bruker `useWorkOpUtskrift`, som legger stilene inline i utskrifta i stedet
for å vente på at kopierte `<link>`-stilark melder seg ferdig lastet.

> Kantcase: er `antallRom > antallArbeidsgivere` står noen rom tomme i enkelte
> runder. Er `antallRom < antallArbeidsgivere` **venter** de overskytende
> arbeidsgiverne den runden (benk) og roterer inn igjen senere – da blir det
> flere runder. I praksis er antall rom alltid lik antall arbeidsgivere, så
> ingen av kantcasene oppstår.

For layout brukes `HGrid` med én kolonne per rom (`columns={antallRom}` eller
`repeat(auto-fit, minmax(14rem, 1fr))`). Rotasjonsplanen i modal bør ha `caption`,
rad-/kolonneoverskrifter og utskriftsstil som skjuler omkringliggende app-krom.

---

## Steg 3 – Ønsker (jobbsøkers ønske)

**Mål:** Etter at alle har hørt alle arbeidsgiverne, registrerer arrangøren hvilke
arbeidsgivere hver jobbsøker **ønsker** speedintervju med.

**Elementer:**

- **Matrise** (Aksel `Table`): rader = jobbsøkere, kolonner = arbeidsgivere,
  celle = `Checkbox` («ønsker møte»). 25 × 5 = kompakt og effektivt.
- Tabellen skal ha `caption`, jobbsøker som `HeaderCell scope="row"` og arbeidsgiver
  som `HeaderCell scope="col"`. Checkbox-label kan skjules visuelt, men må knyttes
  til både rad og kolonne med `aria-labelledby` eller tilsvarende.
- Vis kun jobbsøkere som er registrert møtt – ingen filtrering/søk i v1.
- Per rad: teller «ønsker N arbeidsgivere».
- Matrisa har **faste kolonnebredder** (`table-fixed`), slik at avkryssingene
  står midtstilt rett under sin egen arbeidsgiver uansett navnelengde. Lange
  arbeidsgivernavn brytes over inntil to linjer, og alle kolonneoverskriftene
  bunnjusteres slik at «Jobbsøker» står rett over navnene uten luft imellom.
- Avkryssinger oppdateres optimistisk og legges i en serialisert lagringskø, slik
  at mange ønsker kan registreres raskt uten at matrisen låses.
- **Neste** venter på og tømmer lagringskøen før steg 4 åpnes. Ved lagringsfeil
  beholdes brukeren i steg 3, og bare den aktuelle avkryssingen tilbakestilles.
- **Primærknapp «Neste»** → steg 4.

Dette tilsvarer at jobbsøkeren «gir beskjed til arrangør om hvilke arbeidsgivere
de ønsker å gå på intervju med».

---

## Steg 4 – Intervjufordeling

**Mål:** Arrangøren fordeler de faktiske speedintervjuene etter at ønskene er
registrert. Dette er den andre halvdelen av behov nr. 8 og kan ikke utledes av
ønskene alene.

**Elementer:**

- Ett `ExpansionCard` per arbeidsgiver med en ordnet liste over jobbsøkere, i
  et rutenett som fyller bredden med **så mange kolonner det er plass til**
  – som romkortene i steg 2. Kolonnetallet styres av en
  minstebredde per kort (`repeat(auto-fit, minmax(21rem, 1fr))`), ikke av faste
  brekkpunkter: radene her er bredere enn i steg 2 (dragehåndtak, plassnummer,
  navn, varseltrekant og to pilknapper), så faste brekkpunkter ga for smale
  kolonner. I praksis gir det 2 kolonner rundt 1280px, 3 på 1440px og 4 på
  1920px.
  Kortoverskriften viser antall med og ikke med. Første arbeidsgiver med ønsker
  er åpen som standard; de andre er komprimerte til brukeren åpner dem.
  Kortene åpnes uavhengig av hverandre, også når de står på samme rad – et
  lukket kort strekkes ikke til høyden av et åpent nabokort.
- I hver rad ligger **pilknappene alltid til høyre**, på samme linje som navnet.
  Er navnet for langt for kortbredden, brytes navneteksten over flere linjer i
  stedet for at knappene skyves ned på en egen linje. Minstebredden på kortet
  (`21rem`) er valgt slik at vanlige navn får plass på én linje; å presse inn en
  kolonne til ville tvunget også korte navn til å brytes.
- Arrangøren endrer rekkefølgen med dra-og-slipp eller piler. Jobbsøkere under
  sperrelinjen, merket **«Ikke gjennomført speedintervju»**, er ikke med på
  speedintervjuet.
- **Plassen i rekkefølgen vises ikke som et eget tall.** Den er implisitt i
  rekkefølgen på lista. Navnet begynner allerede med deltakernummeret, og et
  plassnummer ved siden av ga to tall per rad som ble lest som samme slags
  nummer. Det gjelder også utskriften. Plasskonflikter omtaler fortsatt plassen
  i klartekst («Plass 3 også hos …»), der tallet er nødvendig for å forklare
  hva som kolliderer.
- Fordelingen skjer bare etter en **bevisst handling**, aldri ved navigering:
  første gang arrangøren går videre fra ønskesteget, og når hun trykker
  **«Fordel på nytt»** (som krever bekreftelse, siden den overskriver manuelle
  flyttinger). Går hun fram og tilbake mellom stegene, står fordelingen urørt.
- Backend eier algoritmen. Frontend kaller `POST /intervjufordeling/fordel` og
  viser svaret; den regner ikke ut rekkefølger selv.
- Jobbsøkere som er flyttet under sperrelinjen blir værende der også etter
  «Fordel på nytt». Skal de bli med igjen, må de flyttes opp manuelt først.
- Hvis samme jobbsøker har samme plass hos flere arbeidsgivere, vises en
  varseltrekant med forklaring i tooltip.
- Jobbsøkerne over sperrelinjen er **nummerert** med plassen sin, både på
  skjermen og i utskrifta, slik at rekkefølgen er tydelig.
- **«Vis utskrift»** åpner en Aksel `Modal` for alle arbeidsgivere som har minst
  ett planlagt intervju. Hver arbeidsgiver vises med den ordnede listen over
  inkluderte jobbsøkere, og **hver arbeidsgiver skrives ut på sin egen side**.
  Arbeidsgivere uten intervju og jobbsøkere under sperrelinjen tas ikke med.
- **Primærknapp «Neste»** → steg 5.

Fordelingen lagres som inkluderte og ekskluderte `personTreffId`-lister i
rekkefølge per arbeidsgiver. Konkret intervjutidspunkt er utenfor omfanget.

---

## Steg 5 – Registrering av status

**Mål:** Samle resultatet fra de tidligere stegene og registrere videre
oppfølging per jobbsøker × arbeidsgiver. Steget skal gi arrangøren en praktisk
arbeidsflate etter speedintervjuet, uten å duplisere Formidlinger som
sannhetskilde for hvem som har fått jobb.

**Layout og innhold:**

- Ett Aksel `ExpansionCard` per arbeidsgiver, også når arbeidsgiveren ikke har
  relevante jobbsøkere. **Alle kort med jobbsøkere er åpne som standard**; tomme
  kort er lukket og viser arbeidsgiver og antall jobbsøkere i en kompakt
  overskrift. Åpne tomme kort viser en kort tomtilstand.
- Hver jobbsøkerrad viser relevante oppsummeringstagger: **innsatsbehov**,
  **Ønsket speedintervju**, **Satt opp til speedintervju** og eventuelt
  **Formidlet**.
- **Innsatsbehov** er ren visning av jobbsøkerens gjeldende § 14 a-vurdering, og
  gjenbruker etikettene fra `alleInnsatsgrupper` i kandidatsøket. Det står først
  i taggrekka fordi det er egenskapen ved personen, ikke noe som skjedde på
  møtedagen. Feltet kommer fra jobbsøkersøket som `innsatsgruppe` og lagres
  **ikke** i møtedagen. Koder frontend ikke kjenner igjen vises ikke i det hele
  tatt, slik at en ny verdi fra backend ikke havner rå på skjermen.
- Arrangøren kan velge **Ingen vurdering / Aktuell / Kanskje / Ikke aktuell** og
  registrere de uavhengige statusene **2. intervju** og **Jobbtilbud**. Valget
  bruker Aksel `Select`.
- **Notater** er observasjoner fra møtet, valgt fra en fast liste og vist som
  etiketter. De er bevisst **uavhengige av vurderinga**: det som kom fram i
  samtalen er like sant enten paret ender som aktuelt eller ikke, og arrangøren
  skal kunne skrive det ned med en gang – før vurderinga er tatt. Notatene blir
  derfor stående når vurderinga endres.
- Et par kan ha **flere notater samtidig**, og de vises gruppert på **hvem som
  har sagt det** («Arbeidsgiveren» / «Jobbsøkeren»), med hver sin farge.
  Gruppering framfor å gjenta parten i hver etikett holder radene korte, slik at
  flere notater får plass i kortet. Parten ligger i verdien (prefiks `AG_` /
  `JS_`), så part og notat kan ikke komme i utakt. Én kilde til sannhet:
  `workop/notatvalg.ts`.
- Notatene velges i en `Popover` med én `CheckboxGroup` per part. Flervalg i en
  popover framfor et nedtrekk, fordi lista er lang og man ofte velger flere.
- **Dato for 2. intervju** vises bare når 2. intervju er huket av. Avkryssinga
  gjør altså feltet **tilgjengelig**, men åpner ikke kalenderen. Å åpne
  kalenderen er en egen handling, ved siden av å skrive datoen rett inn i
  feltet. Sprettet kalenderen opp automatisk, måtte den lukkes igjen i alle
  tilfellene der datoen ennå ikke er avtalt – og det er nettopp det vanlige rett
  etter at avtalen er gjort. Datoen er **valgfri**: avtalen kan stå uten at
  partene har landet en dag. Den kan endres og fjernes i ettertid uten at
  avkryssinga røres, men fjernes automatisk når avkryssinga fjernes, slik at den
  ikke blir liggende igjen som en usynlig rest.
- Både datofeltet og notatene ligger på **egne linjer** under kontrollraden.
  Det er et bevisst layoutvalg: legges datofeltet inn i samme rad som
  avkryssingene, flytter «Jobbtilbud» seg nedover i det øyeblikket man huker av
  «2. intervju», og man risikerer å klikke feil.

> **Må avklares med fag:** selve notatlista i `notatvalg.ts` er et forslag fra
> utviklingssida, ikke et fagvalidert vokabular. Ordlyden og utvalget bør
> gjennomgås før dette settes i produksjon, siden verdiene blir en enum i basen
> og senere endringer krever migrering.
- Lenken **«Vis formidling»** vises på samme kontrollinje etter 2. intervju og
  Jobbtilbud når paret finnes i Formidlinger.
- Endringer lagres automatisk per jobbsøker–arbeidsgiver-par. UI-et oppdateres
  optimistisk og ruller tilbake med lokal feilmelding hvis lagringen feiler.

En rad vises når minst ett av disse kriteriene er oppfylt:

1. Jobbsøkeren ønsker arbeidsgiveren.
2. Jobbsøkeren er inkludert i arbeidsgiverens speedintervjufordeling.
3. Paret har en lagret vurdering, 2. intervju eller jobbtilbud.
4. En aktiv Formidling viser at jobbsøkeren har fått jobb hos arbeidsgiveren.

Lagrede vurderinger og oppfølgingsstatuser beholdes når ønske eller
intervjufordeling senere fjernes. Raden forsvinner først når paret ikke lenger
oppfyller noen av kriteriene. Arbeidsgiverkortet beholdes.

### Skrivebeskyttet «Formidlet»

- «Formidlet» skrives og endres **kun i Formidlinger**. Feltet finnes ikke i
  `MøtedagDTO`, `VurderingDTO` eller WorkOp-mutasjoner.
- Frontend speiler aktive, ikke-sperrede formidlingsrader ved å koble
  fødselsnummer og organisasjonsnummer i minnet. Det matches aldri på navn, og
  fødselsnummer legges ikke i URL eller logger.
- Flere jobbsøkere kan være registrert i samme formidling og dele
  `stillingId`. Koblingen må derfor aldri anta 1:1 mellom et WorkOp-par og en
  formidling.
- Lenken «Vis formidling» åpner Formidlinger-fanen filtrert på
  arbeidsgiver via query-parameteren `formidlingArbeidsgivere`. Den lenker ikke
  direkte til jobbsøkeren.
- Formidlinger lastes separat. Ved feil er «Formidlet» **ukjent**, ikke
  «nei», og de redigerbare WorkOp-statusene virker fortsatt.

Fritekst/notat, yrkesønske, ledighetsmåneder, ytelse og økonomiberegninger fra
Excel er utenfor dette steget.

---

## Steg 6 – Oppsummering

**Mål:** Gi arrangøren et samlet bilde av resultatet for hele treffet etter at
statusene er registrert. Steget er skrivebeskyttet og har ingen utskrift.

**Elementer:**

- **Nøkkeltall** som kort: aktuelle kandidater, til andre intervju, kanskje,
  ikke aktuelle, ikke vurdert, formidlet, møtt av påmeldte og antall
  speedintervjuer.
- **Tabell per arbeidsgiver** med antall vurderte, aktuelle, andre intervju og
  formidlede. Tabellen har faste kolonnebredder, og tallene er midtstilt under
  sin egen overskrift.

Hver kandidat telles **én gang** i nøkkeltallene, med den mest positive
vurderinga hen har fått på tvers av arbeidsgivere (Aktuell > Kanskje > Ikke
aktuell > ikke vurdert). Tabellen per arbeidsgiver teller derimot rader, siden
samme kandidat kan være vurdert hos flere.

---

## Datamodell

### Frontend-typer (mock + framtidig API-form)

```ts
type MøtedagFase = "OPPMØTE" | "ROM" | "ØNSKER" | "FORDELING" | "VURDERING";
type SpeedintervjuVurdering = "AKTUELL" | "KANSKJE" | "IKKE_AKTUELL";

interface MøtedagDTO {
  rekrutteringstreffId: string;
  fase: MøtedagFase; // hvor langt man er kommet
  antallRom: number; // alltid lik antall arbeidsgivere
  starttidspunkt: string; // «HH:mm», f.eks. «09:00»
  varighetPerMøteMinutter: number; // default 10
  oppmøte: string[]; // personTreffId som har møtt
  deltakernummer: DeltakernummerDTO[]; // nummeret på det fysiske kortet
  rom: RomDTO[];
  arbeidsgiverRekkefølge: ArbeidsgiverRotasjonDTO[];
  ønsker: ØnskeDTO[];
  intervjufordelinger: ArbeidsgiverIntervjufordelingDTO[];
  vurderinger: VurderingDTO[];
}
interface DeltakernummerDTO {
  personTreffId: string;
  nummer: number; // 1, 2, 3 … i den rekkefølgen folk ble registrert møtt
}
interface RomDTO {
  romnummer: number;
  jobbsøkere: string[];
} // personTreffId
interface ArbeidsgiverRotasjonDTO {
  arbeidsgiverTreffId: string;
  startPosisjon: number;
} // 0..maks(R,E)-1; < R = rom, ellers venteplass
interface ØnskeDTO {
  personTreffId: string;
  arbeidsgiverTreffId: string;
}
interface ArbeidsgiverIntervjufordelingDTO {
  arbeidsgiverTreffId: string;
  inkludertePersonTreffIder: string[];
  ekskludertePersonTreffIder: string[];
}
interface VurderingDTO {
  personTreffId: string;
  arbeidsgiverTreffId: string;
  vurdering: SpeedintervjuVurdering | null;
  notater: string[]; // se notatvalg.ts
  andreIntervju: boolean;
  andreIntervjuDato: string | null; // yyyy-MM-dd
  jobbtilbud: boolean;
}
```

«Formidlet» er med vilje ikke del av møtedagskontrakten. Den avledes
skrivebeskyttet fra Formidlinger.

### MSW-mock (dynamisk for demo)

Legg en `møtedagStore = new Map<string, MøtedagDTO>()` i
[mswState.ts](../../../../rekrutteringsbistand-frontend/app/api/rekrutteringstreff/mswState.ts)
(samme mønster som `arbeidsgiverStore`/`innleggStore`). Handlerne bygger svar fra
samme store som leses, slik at oppmøte → romfordeling → ønsker → fordeling →
vurdering henger sammen gjennom en demo. Kontrakten og handlerne inkluderer alle
fem faser og samlinger. Seed for `id === 'workop'` bruker tydelig oppdiktede
navn/identer – ingen realistiske fødselsnumre.

Mocken implementerer også `POST /intervjufordeling/fordel`, men med en **bevisst
forenklet** algoritme: bare regel 1 og 2, uten «beste av to». Knappen «Fordel på
nytt» gjør dermed noe ekte lokalt og i tester, mens finjusteringene bare finnes i
backend der de kan måles ordentlig. Mocken er ikke fasit for
fordelingskvaliteten, og frontendtester skal ikke måle den.

### Backend-kontrakt

Frontend er bygd ferdig mot MSW-mocken, og kontrakten i
[useMøtedag.ts](../../../../rekrutteringsbistand-frontend/app/api/rekrutteringstreff/[...slug]/møtedag/useMøtedag.ts)
er fasiten. Backend skal treffe den uendret, slik at frontend kun trenger å skru
av mocken.

#### Prinsipper

- Samme lagdeling som resten av API-et: Controller → Service → Repository.
  `formidling/`-pakken er referansemønsteret.
- Ren SQL med JDBC, ingen ORM. All databasetilgang går gjennom service, som eier
  transaksjonen.
- Hele møtedagen leses og skrives som ett aggregat. Alle skriveoperasjoner
  returnerer hele møtedagen, slik frontend allerede forventer.

#### Ny pakke

`no.nav.toi.motedag` med `Motedag.kt` (domenemodell), `MotedagController.kt`,
`MotedagService.kt`, `MotedagRepository.kt` og `dto/MotedagDto.kt`.

#### DTO-er og enums

Speiler frontend-typene over 1:1. `MotedagDto` er svaret på samtlige endepunkter.

```kotlin
enum class MøtedagFase { OPPMØTE, ROM, ØNSKER, FORDELING, VURDERING }

enum class SpeedintervjuVurdering { AKTUELL, KANSKJE, IKKE_AKTUELL }

// Nye verdier i eksisterende JobbsøkerHendelsestype
MØTT_OPP, ANGRE_MØTT_OPP

data class MotedagDto(
    val rekrutteringstreffId: UUID,
    val fase: MøtedagFase,
    val antallRom: Int,
    val starttidspunkt: String,          // "HH:mm"
    val varighetPerMøteMinutter: Int,
    val oppmøte: List<UUID>,             // personTreffId
    val deltakernummer: List<DeltakernummerDto>,
    val rom: List<RomDto>,
    val arbeidsgiverRekkefølge: List<ArbeidsgiverRotasjonDto>,
    val ønsker: List<ØnskeDto>,
    val intervjufordelinger: List<ArbeidsgiverIntervjufordelingDto>,
    val vurderinger: List<VurderingDto>,
)

data class DeltakernummerDto(
    val personTreffId: UUID,
    val nummer: Int,                     // 1-basert, aldri gjenbrukt
)

data class RomDto(
    val romnummer: Int,                  // 1-basert
    val jobbsøkere: List<UUID>,
)

data class ArbeidsgiverRotasjonDto(
    val arbeidsgiverTreffId: UUID,
    val startPosisjon: Int,
)

data class ØnskeDto(
    val personTreffId: UUID,
    val arbeidsgiverTreffId: UUID,
)

data class ArbeidsgiverIntervjufordelingDto(
    val arbeidsgiverTreffId: UUID,
    val inkludertePersonTreffIder: List<UUID>,   // rekkefølgen er plassnummeret
    val ekskludertePersonTreffIder: List<UUID>,
)

data class VurderingDto(
    val personTreffId: UUID,
    val arbeidsgiverTreffId: UUID,
    val vurdering: SpeedintervjuVurdering?,
    val notater: List<Vurderingsnotat>,
    val andreIntervju: Boolean,
    val andreIntervjuDato: LocalDate?,
    val jobbtilbud: Boolean,
)
```

Frontend behandler en ukjent vurderingsverdi som «ingen vurdering» framfor å
feile. Den gamle `KLADD`-verdien er tatt bort og skal ikke innføres i backend.

`notater` valideres bevisst **ikke** mot en enum i frontend-skjemaet. Legger
backend til et nytt notat, skal det vises som ukjent verdi framfor å velte hele
møtedagen. Backend eier lista og bør validere den. Backend bør også avvise
`andreIntervjuDato` når `andreIntervju` er `false` — frontend rydder allerede,
så dette er et vern mot andre klienter.

En vurderingsrad regnes som **tom** først når verken vurdering, notater, 2.
intervju, dato eller jobbtilbud er satt. Tomme rader slettes. Regelen ligger
ett sted i frontend (`harRegistrertNoe`) fordi den brukes både til å avgjøre om
raden vises, om den lagres og om den slettes — kommer de i utakt, forsvinner
registreringer uten spor. Backend bør ha samme regel ett sted.

#### Endepunkter

Alle under `/api/rekrutteringstreff/{id}/motedag`, alle returnerer hele
møtedagen:

| Metode | Sti                  | Funksjon                                                                       |
| ------ | -------------------- | ------------------------------------------------------------------------------ |
| GET    | `/`                  | Hent møtedagen. Rent lesende — tomt aggregat hvis ingenting er lagret.         |
| PUT    | `/oppmote`           | Registrer eller angre oppmøte for én jobbsøker. Fjerning med registreringer krever `bekreftSlettRegistreringer`. |
| PUT    | `/moteoppsett`       | Sett tider og 1–9 rom. Første gang: opprett full round-robin-fordeling + rotasjon, fase = ROM. Senere: oppdater tider/rom uten å regenerere. |
| PUT    | `/romfordeling`      | Erstatt komplett romfordeling etter manuell flytting eller «Fordel på nytt».   |
| PUT    | `/onsker`            | Sett eller fjern ett ønskepar, idempotent. Nytt ønske legges bakerst blant de inkluderte i intervjufordelingen; trukket ønske fjernes fra begge lister. |
| PUT    | `/intervjufordeling` | Lagre rekkefølge over og under sperrelinjen for én arbeidsgiver. Brukes ved manuell dra-og-slipp. |
| POST   | `/intervjufordeling/fordel` | Fordel speedintervjuene på nytt. Tom body — alt som trengs er lagret. Erstatter hele fordelingen i én transaksjon. |
| PUT    | `/vurderinger`       | Sett eller fjern vurdering og oppfølging for ett par.                          |

Request-DTO-ene, som speiler `mutations.ts` i frontend. Merk at bare
romfordelingen er innpakket i et objekt — intervjufordeling og vurdering sendes
som selve DTO-en i rotnivå av bodyen:

```kotlin
data class OppmøteRequest(
    val personTreffId: UUID,
    val møtt: Boolean,
    val bekreftSlettRegistreringer: Boolean = false,
)

data class MøteoppsettRequest(
    val antallRom: Int,                  // 1-9
    val starttidspunkt: String,          // "HH:mm"
    val varighetPerMøteMinutter: Int,    // minst 1
)

data class RomfordelingRequest(val rom: List<RomDto>)

data class ØnskeRequest(
    val personTreffId: UUID,
    val arbeidsgiverTreffId: UUID,
    val ønsket: Boolean,
)

// PUT /intervjufordeling tar ArbeidsgiverIntervjufordelingDto direkte.
// PUT /vurderinger tar VurderingDto direkte.
```

#### Fordelingsalgoritmen (backend)

Frontend eide denne en periode, men den er flyttet hit fordi den er ren
forretningslogikk, leser hele møtedagen og må skrives atomisk. `POST
/intervjufordeling/fordel` og førstegangsopprettelsen skal kalle **samme**
servicefunksjon — det skal ikke finnes to kodeveier.

**Domenet:** posisjonen i den inkluderte lista er en *tidsluke*. Alle
arbeidsgivere kjører intervju 1 samtidig, så intervju 2. Står samme person på
samme plass hos to arbeidsgivere, kan hun ikke være begge steder — det er en
**plasskonflikt**.

Algoritmen er bevisst *ikke* en fullstendig løser. Den fjerner de fleste
konfliktene; resten vises som varseltrekant i frontend og rettes manuelt.

To regler:

1. **Arbeidsgivere med færrest personer fordeles først.** De har minst å gå på,
   så de får velge mens det ennå er ledige tidsluker.
2. **Hver person får den ledige plassen nærmest den hun står på nå**, blant
   plassene hun ikke allerede er opptatt i hos en annen arbeidsgiver.

I tillegg kjøres fordelingen **to ganger** med ulik kørekkefølge innenfor hver
arbeidsgiver — én gang i listerekkefølge, én gang med de mest etterspurte først
(flest arbeidsgivere som vil intervjue henne). Den av de to som gir færrest
plasskonflikter beholdes; ved uavgjort vinner listerekkefølgen. Ingen av
strategiene vinner alltid, og å kjøre begge er billigere enn å gjette.

Målt på 3000 tilfeldige treff ga dette 27 % færre konflikter enn regel 1 og 2
alene, og andelen helt konfliktfrie fordelinger gikk fra 43 % til 64 %.

**Invarianter:**

- Ekskluderte forblir ekskluderte. Bare rekkefølgen på de inkluderte regnes ut.
- Ønsker som ennå ikke er plassert regnes som inkluderte.
- Personer som ikke lenger er ønsket faller ut av begge lister.

Algoritmen er ren og bør ligge i en egen fil uten databasetilgang, testet med
vanlige enhetstester — ikke Testcontainers.

**Plasskonflikter beregnes ikke av backend.** Frontend må uansett regne dem på
nytt ved hver dra-og-slipp for å oppdatere varselet uten rundtur, og to
implementasjoner ville drifte fra hverandre.

#### Validering

Invarianter backend må håndheve, ikke bare stole på fra frontend:

- Person og arbeidsgiver tilhører samme WorkOp-treff.
- En jobbsøker forekommer bare én gang i `inkludertePersonTreffIder`, én gang i
  `ekskludertePersonTreffIder`, og aldri i begge hos samme arbeidsgiver.
- `starttidspunkt` er `HH:mm` i 24-timers format, `antallRom` minst 1 og
  `varighetPerMøteMinutter` minst 1.
- `PUT /romfordeling` tar alle rom med ordnede `personTreffId`-lister. Romnumre
  er unike innenfor 1–9, og hver fremmøtt jobbsøker finnes nøyaktig én gang uten
  ukjente personer.
- `PUT /moteoppsett` er en vanlig oppdatering, ikke en engangsoperasjon.
  Møtetidene kan endres når som helst uten `409`. Tidene styrer bare timeplanen,
  ikke hvem som sitter hvor, så en endring skal **ikke** regenerere romfordeling,
  ønsker, intervjufordeling eller vurderinger. Første kall — når det ennå ikke
  finnes rom — oppretter round-robin-fordelingen og rotasjonen og setter fase
  `ROM`. Senere kall oppdaterer bare de tre tidsfeltene.
  `antallRom` er utledet av antall arbeidsgivere på treffet og settes ikke
  manuelt i UI-et, så det skal ikke overstyre en eksisterende romfordeling. Se
  [Kjente gap](#kjente-gap-som-må-lukkes-i-backend) for hva som må skje når
  arbeidsgiverlista endrer seg etter at rommene er opprettet.
- Bare fremmøtte kan få ønsker og intervjufordeling. En vurdering kan bestå etter
  at ønske og intervjufordeling fjernes. En vurderingsrad der vurdering er `null`
  og begge boolean-feltene er `false`, slettes.
- Fjerning av oppmøte når det finnes ønsker, intervjufordeling eller vurderinger
  krever eksplisitt bekreftelse; data må aldri bli hengende igjen inkonsistent.
  Se [Bekreftet kaskadesletting](#bekreftet-kaskadesletting).

#### Bekreftet kaskadesletting

`PUT /motedag/oppmote` tar feltet `bekreftSlettRegistreringer: Boolean = false`.
Når oppmøte fjernes for en person som har ønsker, intervjufordeling eller
vurderinger, og feltet er `false`, svarer backend `409 Conflict` uten å endre
noe:

```json
{
  "feil": "Jobbsøkeren har registreringer som slettes hvis oppmøtet fjernes.",
  "hint": "Bekreft med bekreftSlettRegistreringer=true.",
  "registreringer": { "ønsker": 2, "intervjuplasser": 1, "vurderinger": 1 }
}
```

Frontend bruker tallene i `registreringer` til å beskrive konsekvensen i
bekreftelsesdialogen, og sender deretter samme kall med
`bekreftSlettRegistreringer: true`. Da slettes oppmøtet og de avhengige radene i
én transaksjon. MSW-mocken i frontend implementerer allerede nøyaktig denne
oppførselen og er referansen for backend.

#### Samtidighet

Ingen global versjonskolonne og ingen optimistisk låsing. Møtedagen skrives
gjennom små, atomiske delressurs-PUT-er (`/moteoppsett`, `/oppmote`,
`/romfordeling`, `/onsker`, `/intervjufordeling`, `/vurderinger`), der hver PUT
er transaksjonell for sin egen del og siste skriving vinner. Matriseendringer
lagres per par (`personTreffId`, `arbeidsgiverTreffId`) i stedet for å
overskrive hele samlingen, slik at to arrangører som jobber i hver sin del av
skjermbildet ikke overskriver hverandre.

Dette er et bevisst valg: møtedagen redigeres av et lite antall kjente personer
i samme rom, og kostnaden ved versjonskonflikt-UI er større enn gevinsten.
Skulle reelle konflikter vise seg i bruk, kan optimistisk låsing legges til
senere per delressurs uten å endre lesekontrakten.

#### Ingen sideeffekt ved lesing

`GET /motedag` er rent lesende. Finnes det ingen lagret møtedag, returneres
et tomt aggregat med `200`: fase `OPPMØTE`, standardtider og tomme lister — det
opprettes ingen rad. Lagret tilstand oppstår først ved første PUT. Dette gjør at
det å åpne fanen aldri skriver til databasen, og at en leser uten skrivehensikt
ikke kan «låse inn» et møteoppsett.

Frontend har ingen egen «ikke startet»-fase; `OPPMØTE` med tom `oppmøte`-liste
_er_ tomtilstanden. Standardverdiene backend returnerer for et tomt aggregat
skal speile `møtedagStartdata.ts` i frontend: `antallRom` = antall
arbeidsgivere på treffet (minst 1), `starttidspunkt` `"10:00"`,
`varighetPerMøteMinutter` 10.

#### Tilgang

Samme regel som resten av API-et, ingen egen WorkOp-mekanisme:
`verifiserAutorisasjon(ARBEIDSGIVER_RETTET)` +
`eierService.erEierEllerUtvikler(...)`, ellers 403.

Formidlingsendepunktenes kontortilgang gjenbrukes **ikke** – for møtedagen er
eierskap eneste vei inn. Det er en innstramming, ikke en oppmykning.

At WorkOp-treff ikke skal vises i søket håndteres som en egen oppgave.

#### Koblingsnøkler

`personTreffId` og `arbeidsgiverTreffId` brukes gjennomgående. Repository mapper
disse til interne `jobbsoker_id` og `arbeidsgiver_id`; interne database-ID-er
skal ikke lekke ut i DTO-ene. Fødselsnummer og organisasjonsnummer er kun
visningsdata og skal aldri brukes til å koble sammen data — heller ikke i
frontend.

Konsekvens for eksisterende kode: `FormidlingDto` utvides additivt med
`personTreffId` og `arbeidsgiverTreffId`. Begge finnes allerede som
fremmednøkler på `formidling`-tabellen, og lista joiner allerede både `jobbsoker`
og `arbeidsgiver`. Det er derfor to nye kolonner i eksisterende select, uten
migrasjon og uten å fjerne noe. Frontend er allerede lagt om til de nye feltene.

#### Database

Én ny migrasjon, `V14__motedag.sql`, med sju tabeller:

| Tabell                    | Innhold                                                                       |
| ------------------------- | ----------------------------------------------------------------------------- |
| `motedag`                 | 1:1 med treff: `rekrutteringstreff_id` (PK/FK), `fase`, `antall_rom`, `start_tidspunkt`, `varighet_min` |
| `deltakernummer`          | `rekrutteringstreff_id`, `jobbsoker_id`, `nummer` — unik på (treff, nummer) og (treff, jobbsoker) |
| `rom_tildeling`           | `rekrutteringstreff_id`, `jobbsoker_id`, `romnummer`                          |
| `arbeidsgiver_rotasjon`   | `arbeidsgiver_id`, `start_posisjon`                                           |
| `speedintervju_onske`     | `jobbsoker_id`, `arbeidsgiver_id`                                             |
| `speedintervju_fordeling` | `jobbsoker_id`, `arbeidsgiver_id`, plassering og om jobbsøkeren er inkludert  |
| `speedintervju_vurdering` | `jobbsoker_id`, `arbeidsgiver_id`, nullable `vurdering`, `andre_intervju`, nullable `andre_intervju_dato`, `jobbtilbud` |
| `speedintervju_notat`     | `vurdering_id`, `notat` — ett rad per notat, siden et par kan ha flere        |

Rekkefølge lagres eksplisitt som et heltall — den skal ikke utledes av
innsettingsrekkefølge. Migrasjonen er rene `CREATE TABLE` uten endringer på
eksisterende tabeller.

**Rollback:** Flyway ruller ikke tilbake automatisk. Fordi migrasjonen kun
oppretter nye tabeller, er den likevel trygg i praksis: eksisterende
funksjonalitet er upåvirket om WorkOp må skrus av, og fanen gates i frontend.
Skal tabellene faktisk fjernes, kreves en ny migrasjon med `DROP TABLE` — data
i dem går da tapt. Det er akseptabelt i v1 siden møtedagsdata er
gjennomføringsstøtte, ikke vedtaksgrunnlag, men det gjør migrasjonen
rød sone: den skal leses nøye av utvikler før den kjøres i produksjon.

Oppmøte får **ingen** ny kolonne, se [Oppmøte lagret som hendelse](#oppmøte-lagret-som-hendelse).

##### Deltakernummer

`deltakernummer` er en egen tabell og ikke en kolonne på oppmøtehendelsen. Det er
nettopp separasjonen som gir persistensen: nummeret skal overleve at oppmøtet
fjernes og settes på nytt.

Regelen backend skal følge, tildelt i samme transaksjon som oppmøtet
registreres:

```sql
INSERT INTO deltakernummer (rekrutteringstreff_id, jobbsoker_id, nummer)
SELECT :treffId, :jobbsokerId, COALESCE(MAX(nummer), 0) + 1
  FROM deltakernummer WHERE rekrutteringstreff_id = :treffId
ON CONFLICT (rekrutteringstreff_id, jobbsoker_id) DO NOTHING;
```

- **Aldri gjenbruk.** Nummeret regnes fra `MAX(nummer) + 1`, ikke fra antall
  rader. Fjernes oppmøtet, blir raden stående, og neste person i døra får et
  nytt nummer. Hull i rekka er derfor forventet og riktig: nummeret står på et
  fysisk kort som allerede er delt ut, og samme kortnummer skal aldri peke på to
  personer i løpet av dagen.
- **Gjenbruk til samme person.** `ON CONFLICT … DO NOTHING` gjør at en person som
  registreres møtt på nytt får tilbake sitt opprinnelige nummer.
- **Unik på (treff, nummer)** i databasen, ikke bare i koden. To samtidige
  oppmøteregistreringer kan ellers lese samme `MAX` og dele ut samme kortnummer.
  Ved konflikt skal kallet prøves på nytt framfor å feile mot brukeren.
- Tabellen tåler at `nummer` ikke finnes for en jobbsøker. Frontend viser da bare
  navnet, slik at møtedager fra før nummereringen fantes fortsatt kan åpnes.

Nummeret kobler skjermbildet til de **fysiske kortene** som deles ut i døra.
Under speedintervjuene noterer arbeidsgiverne nummeret framfor navnet, og
utskriftene bruker det samme nummeret. Derfor vises det sammen med navnet i alle
stegene, på formen `3. Fornavn Etternavn`, og lista i steg 1 sorteres
fortløpende på det slik at den leses som kortbunken.

Deltakernummeret er det **eneste** tallet som vises ved navnet. Plassen i
intervjurekkefølgen i steg 4 er implisitt i rekkefølgen på lista, nettopp for at
to tall ved siden av hverandre ikke skal forveksles.

#### Kjente gap som må lukkes i backend

Disse finnes i mocken i dag og må håndteres ordentlig når backend tar over:

1. **Stale møteoppsett.** Mocken fryser antall rom og arbeidsgiverrekkefølge ved
   første lagring. Legges en arbeidsgiver til etterpå, får den aldri rom eller
   plass i rotasjonen; fjernes en, blir den stående. Backend må utlede rom og
   rotasjon fra arbeidsgiverne som faktisk er på treffet på lesetidspunktet.
2. **Endret arbeidsgiverliste etter fordeling.** Fjernes en arbeidsgiver etter at
   ønsker og intervjufordeling er registrert, må backend rydde radene som peker
   på den — ikke la dem bli hengende som foreldreløse referanser. Mocken gjør
   ikke dette i dag.

#### Observability

- Teller for antall registrerte oppmøter per treff.
- Teller for lagringsfeil per endepunkt, slik at vi ser om autolagringen i
  frontend feiler systematisk.
- Ingen fødselsnumre i logger. Logg `personTreffId` og treff-id.

#### Testing

Komponenttester med Testcontainers, som ellers i API-et. Prioriter:

- Tilgang: eier får 200, ikke-eier får 403, kontortilgang alene gir 403.
- Oppmøte: registrer, angre, registrer igjen — utledet tilstand er riktig.
- Kaskadesletting: fjerning av oppmøte med registreringer gir 409 uten bekreftelse
  og uten sideeffekt, og sletter alt i én transaksjon med bekreftelse.
- Aggregatet: hver PUT returnerer hele møtedagen med de andre delene intakt.
- Fordeling: enhetstester på algoritmen (ekskluderte bevares, ingen mister
  plass, ønsker uten plassering blir inkludert), og én komponenttest på at
  `POST /fordel` erstatter hele fordelingen i én transaksjon.
- Lesing uten sideeffekt: `GET /motedag` på et treff uten lagret møtedag gir
  tomt aggregat, og et påfølgende GET viser fortsatt ingen lagret rad.
- Endret møteoppsett: tider kan endres etter opprettelse uten at romfordeling,
  ønsker eller vurderinger går tapt.
- Stale-tilfellet: arbeidsgiver lagt til etter at møteoppsettet ble lagret.

#### Rekkefølge

1. `V14__motedag.sql` og repository for lesing.
2. `GET /motedag` med tilgangssjekk. Frontend kan da lese ekte data.
3. `FormidlingDto`-utvidelsen — uavhengig av resten, kan tas først.
4. Oppmøtehendelsene, inkludert `bekreftSlettRegistreringer` og 409-svaret.
5. Resten av PUT-endepunktene, ett steg om gangen.
6. `POST /intervjufordeling/fordel` med fordelingsalgoritmen. Frontend kaller
   den allerede, og mocken har en forenklet variant som kan slås av her.
7. Skru av MSW i frontend, ett endepunkt om gangen etter hvert som backend er klar.
8. **Produksjonsaktivering** — se under. Dette er et eget, bevisst steg og skjer
   ikke automatisk når backend er ferdig.

#### Produksjonsaktivering

Fanen er gated på `getMiljø() !== Miljø.ProdGcp` i både `TabsNav.tsx` og
`TabsPanels.tsx` gjennom `useWorkOpMøtedag`. Fram til aktivering
kjører WorkOp bare i lokalt, dev og test.

Rekkefølge for å skru på i produksjon:

1. Alle backend-endepunkter er i drift i dev og test, og MSW er skrudd av for dem.
2. Verifisert i test med et ekte WorkOp-treff: oppmøte, romfordeling, ønsker,
   fordeling, status og utskrift fungerer ende-til-ende.
3. `V14__motedag.sql` er kjørt i produksjon, og tabellene er tomme.
4. Miljøsjekken fjernes fra `useWorkOpMøtedag`, slik at gatingen bare
   består av `kategori === WORKOP` og eier-/utviklerrolle.
5. Observability-tellerne følges i første reelle gjennomføring.

Punkt 4 er en egen, liten PR. Å holde den atskilt gjør det mulig å skru av
igjen ved å reversere én linje.

#### Rød sone

Skrives av utvikler selv, ikke generert:

- Tilgangssjekken i `MotedagController` — sikkerhetskritisk.
- `V14__motedag.sql` — irreversibel i produksjon.

### Oppmøte lagret som hendelse

Domenet er hybrid (current-state-tabeller + hendelsestabeller med
`hendelse_data jsonb`). For oppmøte har vi tre alternativer:

| Alternativ                 | Lagring                                                                                | Kommentar                                                                 |
| -------------------------- | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------- |
| **A – Kun hendelse**       | `MØTT_OPP` / `ANGRE_MØTT_OPP` i `jobbsoker_hendelse`; «har møtt» utledes av hendelsene | **Valgt for v1** – enkelt, ingen skjemaendring                            |
| B – Kun current-state      | Boolean `mott_opp` på `jobbsoker`                                                      | Ingen historikk; forkastet                                                |
| C – Egen `JobbsøkerStatus` | Ny verdi i jobbsøker-livssyklusen                                                      | Utenfor omfanget; forutsetter at oppmøte også oppdaterer aktivitetsplanen |

**Valg for v1 – alternativ A (kun hendelse):**

- Vi skriver `MØTT_OPP` / `ANGRE_MØTT_OPP` til `jobbsoker_hendelse` (bærer
  `tidspunkt`, `opprettet_av_aktortype` = `MARKEDSKONTAKT_ELLER_VEILEDER`,
  `aktøridentifikasjon`). **Ingen ny kolonne.**
- «Har møtt» **utledes** av hendelsene: den siste av `MØTT_OPP` /
  `ANGRE_MØTT_OPP` bestemmer tilstanden. Møtedag-lista, «Møtt»-taggen og telleren
  «X møtt av Y» leser fra hendelsene – samme måte som minside-/relevante hendelser
  allerede utledes i frontend.
- Ved like tidspunkt brukes `jobbsoker_hendelse_id` som deterministisk
  tie-breaker; sorter på tidspunkt synkende og hendelses-ID synkende.
- Backend implementerer `JobbsøkerHendelsestype.MØTT_OPP` og
  `ANGRE_MØTT_OPP`, inkludert serialisering og komponenttester.
- Hendelsene vises i Hendelser-fanen når typen legges til i frontend-konstantene
  (label + relevant-sett).

**Hvorfor ikke egen status:** En statusendring må også oppdatere aktivitetsplanen
og aktivitetskortet. Hendelsene er derfor eneste sannhetskilde for oppmøte i denne
leveransen og unngår en ufullstendig statusmodell.

---

## Kobling til Excel master

| Excel                                                       | Møtedag-steg                              |
| ----------------------------------------------------------- | ----------------------------------------- |
| Oppmøte (finnes ikke som egen kolonne i dagens ark)         | Steg 1 – Oppmøte                          |
| Grupperom/gruppeinndeling (håndteres manuelt i dag)         | Steg 1–2 (romoppsett + rotasjon)          |
| «Bedrift 1–6» – hvilke bedrifter kandidaten ønsker          | Steg 3 – Ønsker                           |
| Faktisk fordeling og rekkefølge til speedintervju           | Steg 4 – Intervjufordeling                |
| «Aktuell / Kanskje / Ikke aktuell» (Master + «Bedrift 1–6») | Steg 5 – Registrering av status            |
| «2. intervju hos» og «Jobbtilbud fra»                       | Steg 5 – Registrering av status            |
| «Fått jobben»                                               | Skrivebeskyttet speil fra Formidlinger    |
| Samlede tall for treffet                                    | Steg 6 – Oppsummering                     |
| Yrkesønske, ledighetsmåneder, ytelse og økonomi             | Utenfor scope – statistikk (behov nr. 13) |

---

## Kobling til behov-og-prioriteringer

| Behov                             | Oppgave | Dekkes av                                                      |
| --------------------------------- | ------- | -------------------------------------------------------------- |
| Nr. 6 – Registrere oppmøte        | 1       | Steg 1 + burgermeny + samlehandlinger i handlingsraden       |
| Nr. 7 – 5 grupper/grupperom       | 2       | Steg 1 (antall rom + auto-fordeling) + steg 2 (rom + rotasjon) |
| Nr. 8 – Fordele til speedintervju | 3       | Steg 3 (ønsker) + steg 4 (intervjufordeling)                   |
| Nr. 9 – Statusoversikt            | —       | Steg 5 – arbeidsgiverspesifikk status og oppfølging            |

---

## Gjenbruk av eksisterende mønstre

- **Faner/toggle:** `RekrutteringstreffTabs`, `Fanepanel`, `getMiljø()`-gating,
  og en felles `harWorkOpTilgang`-hook som håndterer 403 fra `/motedag`.
- **Lister/kort:** `ListeKort`, `JobbsøkerKort`-stil, `ArbeidsgiverListeItem`,
  `JobbsøkerStatusTag` (for «Møtt»-tag).
- **Burgermeny:** `ActionMenu` + `ActionMenyPunkt` i `JobbsokerKortValg.tsx`.
- **Data/lasting:** `SWRLaster`, `useRekrutteringstreffContext`, SWR + MSW-mock
  med in-memory store.
- **Aksel:** `Stepper`, `Table`, `TextField`, `CheckboxGroup`, `ToggleGroup`/
  `RadioGroup`, `Box`/`HStack`/`VStack`/`HGrid`, `Tag`, `Button`, `LocalAlert`.
- **Testing:** `tests/rekrutteringstreff/`, `gotoApp`/`ventTilKlar`, `storageState`
  per rolle, og MSW node-server via `instrumentation.ts` + `mocks/server.ts`.

### Lange navn

Navn som ikke får plass avkortes med ellipse i stedet for å brytes, og hele
navnet vises da i en tooltip. Felleskomponenten `components/AvkortetTekst.tsx`
gjør dette, og brukes på skjermvisninger av person- og arbeidsgivernavn i alle
stegene.

Avveiningen er bevisst: stabil radhøyde og layout er viktigere enn å alltid vise
hele navnet. Brytes navnet, skyves dragehåndtak, plassnummer og pilknapper ut av
stilling, og det rammer *alle* rader – ikke bare den med det lange navnet.

To unntak:

- **Utskrift** avkorter aldri. Papir har ingen hover, så hele navnet må stå.
- **Kolonneoverskriftene i ønskematrisen** brytes fortsatt over to linjer.
  Avkorting krever at navnet står på én linje, og med fem arbeidsgivere ville
  tabellen da blitt bredere enn skjermen. Der er det bredden, ikke radhøyden,
  som er knapp.

Tooltipen vises **bare** når teksten faktisk er kuttet, så man slipper en boble
som gjentar det man allerede kan lese. Det krever at bredden måles i nettleseren,
og komponenten måler både etter layout, ved endret størrelse og når nettfonten er
lastet.

Avkorting forutsetter at hele kjeden av foreldre får lov til å krympe. Aksels
`ExpansionCard` har en indre innpakning med `min-width: auto` som nekter å bli
smalere enn innholdet sitt. I steg 4 gjorde det at radene rant ut av kortet og
pilknappene ble klippet bort i smale kolonner, uten at de forsvant fra DOM-en.
Kortet i steg 4 overstyrer derfor `min-width` på den innpakningen.

---

## Gjennomføringsrekkefølge (frontend først)

1. **Fase A0 – Komplett kontrakt og mock-grunnmur:** etabler `MøtedagDTO` med
   alle fem faser, rom, rotasjon, ønsker, intervjufordelinger og vurderinger. Opprett
   stateful MSW-handlere for alle mutasjoner, syntetisk WorkOp-seed og testede
   hjelpefunksjoner for stabil romfordeling og rotasjon.
2. **Fase A1 – Navigasjon og steg 1:** opprett fane, tilgangsgating og Stepper med
   alle seks steg. Legg til oppmøte fra jobbsøkerkortet, oppmøteliste,
   arbeidsgiverliste, møteoppsett, «Opprett møteplan» og «Gå til romfordeling».
3. **Fase B – Steg 2 (Rom og rotasjon):** vis
   auto-fordelte rom, manuell flytting med dra-og-slipp og direkte romvalg,
   eksplisitt full omfordeling, rotasjonsmatrise med klokkeslett på skjermen og
   to utskrifter (arbeidsgivere og jobbsøkere).
4. **Fase C – Steg 3 og 4:** bygg ønske-matrise og intervjufordeling på
   den etablerte kontrakten.
5. **Fase D – Steg 5 og 6 (frontend implementert):** arbeidsgiverkort med
   oppsummering, Aktuell/Kanskje/Ikke aktuell, 2. intervju, Jobbtilbud og
   skrivebeskyttet Formidling-speil. Stateful MSW dekker lagring per par. Steg 6
   summerer resultatet for hele treffet.
6. **Fase E – Backend:** implementer den ønsker impsamme kontrakten med Flyway-migrasjoner,
   controller/service/repository og hendelser. Bytt datakilden fra MSW til API
   uten å endre komponentenes DTO-er eller flyt.

Hver fase avsluttes med Playwright-verifisering: bekreft tilstandene manuelt med
playwright-mcp, og dekk dem med nye tester i `tests/rekrutteringstreff/`.

---

## Validering og testing

Målet er å sikre at frontend vises i **riktige tilstander** gjennom hele flyten –
ikke å teste selve mock-laget.

### Verktøy under utvikling

- **playwright-mcp:** kjør en ekte nettleser mot dev-serveren og klikk gjennom
  flyten (oppmøte → «Opprett møteplan» → rom/rotasjon → ønsker → fordeling →
  vurdering) for å bekrefte at riktige tilstander vises. Bruk den til å utforske UI-et og finne
  stabile role-baserte selektorer før tester skrives.
- **next-devtools-mcp (valgfritt):** inspiser Next.js (App Router-ruter, server-/
  klientkomponenter, konsoll-/byggefeil) når noe ikke rendres som forventet.

### MSW med state (ikke stub-svar)

- `møtedagStore` (se «MSW-mock») **muteres** av PUT-handlerne og leses av
  GET-handleren, slik at oppmøte → romfordeling → ønsker → fordeling → vurdering henger sammen
  som ekte tilstandsoverganger.
- Testene skal drive flyten via UI-et og verifisere at tilstanden **utvikler seg
  riktig** (f.eks. at «Møtt»-tag dukker opp etter registrering, at rom fylles etter
  «Opprett møteplan»). Ikke skriv tester som bare sjekker at et endepunkt returnerer
  en fast verdi.
- Node-MSW startes i test-modus via `instrumentation.ts`
  (`NEXT_PUBLIC_PLAYWRIGHT_TEST_MODE=true`) + `mocks/server.ts`. Legg
  WorkOp-handlerne i `mocks/handlers.ts` og seed `id === 'workop'` med syntetiske
  data.

### Nye Playwright-tester

Plasseres i `tests/rekrutteringstreff/` (f.eks. `workop-gjennomforing.spec.ts`),
samme mønster som eksisterende tester: `storageState` for rolle
(arbeidsgiverrettet), `gotoApp(page, …)`, `ventTilKlar` og role-baserte selektorer
(`getByRole`). Fokuser på **tilstandene som vises**:

- **Fane-synlighet:** «WorkOp gjennomføring»-fanen vises kun for WorkOp-treff, for
  eier eller utvikler og i ikke-prod – skjult ellers. En 403 fra
  `/motedag` skjuler både fane og panel.
- **Stepper:** seks steg vises; fullførte steg er klikkbare, og steg uten
  forutsetninger er ikke-interaktive. Fra `xl` står stegnavnene på én linje.
  Aksel lar ellers hvert stegnavn krympe til sitt lengste ord, og det overstyres
  med Tailwind-klasser på `Stepper` i `WorkOpGjennomføring.tsx` – ikke i
  `globals.css`, slik at overstyringen står der den gjelder. Under `xl` brytes
  navnene som før.
- **Steg 1 – oppmøte:** empty state når ingen er møtt; «Møtt»-tag og telleren
  «X av Y» oppdateres når oppmøte registreres fra burgermenyen. Egen test for
  «Marker som møtt (N)»: kryss av to jobbsøkere i jobbsøkerfanen, registrer, og
  sjekk at begge får «Møtt»-tag, at valget tømmes, og at telleren i steg 1 øker.
  Tilsvarende for «Fjern oppmøte (N)»: bekreftelsesdialogen skal navngi antallet
  og listen skal miste «Møtt»-taggen først etter at fjerningen er bekreftet.
- **Møteplan og rom:** «Opprett møteplan» fyller rommene, og «Gå til
  romfordeling» navigerer uten lagring. Test dra-og-slipp, direkte romvalg,
  innsetting sist, rollback ved lagringsfeil og full «Fordel på nytt» med
  bekreftelse. Rotasjonsplan-modalen viser klokkeslett, og «Skriv ut» finnes.
- **Steg 3 – ønsker:** matrisen viser kun fremmøtte jobbsøkere, og avkryssing
  oppdaterer telleren per rad. Egen test for avkorting: et langt navn skal ha
  samme høyde som et kort, være kuttet, og vise hele navnet i tooltip ved hover –
  mens et navn som får plass ikke skal gi noen tooltip.
- **Steg 4 – intervjufordeling:** rekkefølgen kan endres med dra-og-slipp og
  piler, jobbsøkere kan flyttes over/under sperrelinjen, og plasskonflikter
  varsles. Den samlede utskriftsvisningen bevarer rekkefølgen og utelater
  arbeidsgivere uten planlagte intervju og jobbsøkere under sperrelinjen.
- **Steg 5 – registrering av status:** kortene viser unionen av ønsker,
  intervjufordeling, lagrede statuser og Formidlinger. Test lagring/nullstilling,
  utholdenhet etter fjernet ønske/fordeling, at alle ikke-tomme kort åpnes som
  standard, tomme arbeidsgiverkort, skrivebeskyttet «Formidlet»,
  «Vis formidling»-lenken, flere kandidater i samme formidling, filtrert
  navigasjon og lokal feiltilstand for begge datakildene.
- **Steg 6 – oppsummering:** nøkkeltallene teller hver kandidat én gang med
  beste vurdering, og tabellen per arbeidsgiver teller rader.
- **Deltakernummer** (`workop-deltakernummer.spec.ts`): nummeret tildeles ved
  oppmøteregistrering og fortsetter der forrige slapp; det beholdes når oppmøtet
  fjernes og gis tilbake til samme person ved ny registrering, mens neste person
  i køen får et nytt nummer; det vises sammen med navnet i alle stegene; og
  radene i steg 4 viser ett tall, ikke deltakernummer og plassnummer ved siden
  av hverandre. Testene er verifisert ved å sabotere hver regel i tur og se at
  riktig test feiler.
- **Innsatsbehov** vises i steg 5 for jobbsøkere med kjent innsatsgruppe, og
  vises ikke i det hele tatt for jobbsøkere uten.

Unngå assertions som bare speiler mock-data; verifiser at UI-et står i forventet
tilstand etter reelle brukerhandlinger.

---

## Avgrensninger for første versjon

- Møteoppsettet kan justeres etter opprettelse, men en endring av tidene
  fordeler ikke rommene på nytt. Det kan opprettes mellom 1 og 9 rom.
- Romfordelingen opprettes automatisk, kan endres manuelt og kan erstattes med en
  eksplisitt full round-robin-fordeling.
- Utskrift viser deltakernummer, navn, rom og arbeidsgiver, men aldri
  fødselsnummer.
- Deltakernummer deles ut per rekrutteringstreff og gjenbrukes aldri innenfor
  samme treff. Hull i rekka etter fjernet oppmøte er forventet.
- Intervjufordelingen tar utgangspunkt i registrerte ønsker og lagrer rekkefølge
  over og under sperrelinjen, men ikke tidspunkt.
- Fjerning av oppmøte etter at ønsker, intervjufordeling eller vurderinger finnes,
  krever bekreftelse og rydder avhengige data atomisk.
- «2. intervju» og «Jobbtilbud» registreres i WorkOp. «Formidlet» registreres
  bare i Formidlinger og speiles skrivebeskyttet. Økonomidata inngår ikke.
- Egen `JobbsøkerStatus` for oppmøte krever en separat beslutning sammen med
  oppdatering av aktivitetsplan og aktivitetskort.
- **Desktop-only.** Fanen er laget for arrangørens PC i møtelokalet. Matrisen,
  romfordelingen og utskriftsvisningen forutsetter bred skjerm og scroller
  horisontalt på smale vinduer. Egen mobiltilpasning er bevisst utelatt —
  det finnes ingen mobilklient for arrangørflaten.
- **Ingen optimistisk låsing.** Delressurs-PUT-ene er atomiske og siste skriving
  vinner. Se [Samtidighet](#samtidighet).
- **Maks 100 jobbsøkere vises.** `useJobbsøkere` henter én side à 100, og
  backend håndhever `antallPerSide` i intervallet 1–100. Har treffet flere enn
  100 jobbsøkere, faller de bakerste ut av lista WorkOp regner navn og status
  ut fra. Dette gjelder også utenfor WorkOp — for eksempel duplikatsjekken i
  «Finn kandidater» — og er derfor en eksisterende begrensning i frontend, ikke
  noe WorkOp innfører. Fikses separat, enten ved at frontend paginerer eller ved
  at backend tilbyr et «hent alle»-endepunkt. WorkOp-treff med speedintervju i
  maks 9 rom ligger i praksis godt under grensen.

## Åpne spørsmål

- Hva gjør vi hvis møtene er gjennomført og noen endrer fordelingen på nytt?
  Diskuter **grad av låsing** av verdier i WorkOp gjennomføring-fanen.
- **Utskrift:** har romvertene egen notasjon for print, f.eks. bare initialer på
  jobbsøkere?
- ~~Er dagens eier-/kontorregel streng nok for WorkOp?~~ **Avklart:** ja, vi
  bruker eier-regelen uten egen hovedansvarlig-modell.
- Hvilket backendendepunkt skal gi begge WorkOp-eierne komplett, autorisert
  lesetilgang til relevante Formidlinger?
- ~~Hvilke autoritative domenenøkler skal erstatte fødselsnummer +
  organisasjonsnummer?~~ **Avklart:** `personTreffId` og `arbeidsgiverTreffId`.
- Når bør «møtt opp» løftes fra hendelse til egen `JobbsøkerStatus` – i takt med
  at oppmøte også oppdaterer aktivitetsplanen/aktivitetskortet?
