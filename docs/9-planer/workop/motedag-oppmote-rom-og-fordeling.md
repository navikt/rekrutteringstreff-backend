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
| Møteoppsett            | **Starttidspunkt**, **varighet per møte**, og **pause mellom møter** settes i steg 1. Standardverdier er `10:00`, `10` og `5`. Antall rom er avledet fra antall arbeidsgivere.                                                                                                    |
| Rotasjonsplan          | Vises som sammendrag og full matrise i steg 2. To separate utskrifter: **én til arbeidsgiverne** (hvilket rom de skal til, per klokkeslett) og **én til jobbsøkerne** (hvem som kommer til rommet, per klokkeslett). Én mottaker per side.                            |
| Steg 3 (ønsker)        | Registrer **jobbsøkers ønske** om hvilke arbeidsgivere hen vil møte. Kun fremmøtte jobbsøkere inngår.                                                                                                                                                                 |
| Steg 4 (fordeling)     | Arrangør lager intervjurekkefølge per arbeidsgiver. Jobbsøkere kan flyttes over og under sperrelinjen. Rekkefølgen lagres, men ikke konkrete tidspunkter.                                                                                                             |
| Steg 5 (registrering)  | **Registrering av status** per jobbsøker × arbeidsgiver: oppsummering av ønske og speedintervju, vurdering (**Aktuell / Kanskje / Ikke aktuell**), **2. intervju**, **Jobbtilbud** og skrivebeskyttet **Formidlet** fra Formidlinger.                                 |
| Steg 6 (oppsummering)  | **Oppsummering** av hele treffet: nøkkeltall for aktuelle kandidater, andre intervju, øvrige statuser og formidling, samt en tabell per arbeidsgiver. Hver kandidat telles én gang, med den mest positive vurderinga hen har fått.                                    |
| Tilgang                | Kun de to eksplisitt registrerte hovedansvarlige, én markedskontakt og én veileder, har tilgang. Utvikler kan ha bypass. Kontortilgang alene gir ikke tilgang.                                                                                                        |

---

## Overordnet flyt

```text
  JOBBSØKER-FANE
      │
      │   Burgermeny på jobbsøkerkort: «Registrer oppmøte»
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
| Tilgang           | Frontend-gating er nødvendig, men ikke tilstrekkelig.                                                 | Backend håndhever `kategori === WORKOP`, eksplisitt hovedansvarlig og rolle server-side. Kontortilgang alene er ikke nok. Frontend bruker det samme autoritative tilgangsresultatet.              |

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

---

## Steg 1 – Oppmøte og oppsett

**Mål:** Registrere hvem som møtte, og sette opp rammene for møtene (tider)
før rotasjonen starter.

**Elementer:**

- **Forenklet jobbsøkerliste** – kun **fornavn, etternavn og fødselsnummer** (ikke
  full kort-stil). Hver rad har en «Fjern oppmøte»-knapp (speiler
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
  - **Pause mellom møter** i minutter (tid til å rotere), standard `5`.
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
- Flytting lagres optimistisk via `PUT /moetedag/romfordeling`. Ved feil rulles
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
`starttidspunkt` og varer `varighet per møte`; deretter legges `pause mellom
møter` til før neste runde.

### Rotasjonsmatrise og utskrifter

Rotasjonsmatrisen vises på skjermen i steg 2 som arrangørens oversikt. Eksempel
med start `10:00`, varighet `10 min` og pause `5 min`:

| Klokkeslett | Rom 1          | Rom 2          | Rom 3          | Rom 4          | Rom 5          |
| ----------- | -------------- | -------------- | -------------- | -------------- | -------------- |
| 10:00–10:10 | Arbeidsgiver A | Arbeidsgiver B | Arbeidsgiver C | Arbeidsgiver D | Arbeidsgiver E |
| 10:15–10:25 | Arbeidsgiver E | Arbeidsgiver A | Arbeidsgiver B | Arbeidsgiver C | Arbeidsgiver D |
| 10:30–10:40 | Arbeidsgiver D | Arbeidsgiver E | Arbeidsgiver A | Arbeidsgiver B | Arbeidsgiver C |
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

- Ett `ExpansionCard` per arbeidsgiver med en ordnet liste over jobbsøkere.
  Kortoverskriften viser antall med og ikke med. Første arbeidsgiver med ønsker
  er åpen som standard; de andre kortene er komprimerte til brukeren åpner dem.
- Arrangøren endrer rekkefølgen med dra-og-slipp eller piler. Jobbsøkere under
  sperrelinjen er ikke med på speedintervjuet.
- Startrekkefølgen fordeles uten plasseringskrasj mellom arbeidsgivere når det er
  mulig. Manuelle rekkefølger beholdes.
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
- Hver jobbsøkerrad viser relevante oppsummeringstagger:
  **Ønsket speedintervju**, **Satt opp til speedintervju** og eventuelt
  **Formidlet**.
- Arrangøren kan velge **Ingen vurdering / Aktuell / Kanskje / Ikke aktuell** og
  registrere de uavhengige statusene **2. intervju** og **Jobbtilbud**. Valget
  bruker Aksel `Select`.
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
  varighetPerMøteMinutter: number; // default 5
  pauseMellomMøterMinutter: number; // default 5
  oppmøte: string[]; // personTreffId som har møtt
  rom: RomDTO[];
  arbeidsgiverRekkefølge: ArbeidsgiverRotasjonDTO[];
  ønsker: ØnskeDTO[];
  intervjufordelinger: ArbeidsgiverIntervjufordelingDTO[];
  vurderinger: VurderingDTO[];
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
  andreIntervju: boolean;
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

### Backend-kontrakt

Følger dagens hybrid (current-state-tabeller + hendelser) og
Controller → Service → Repository:

- **Oppmøte (v1):** kun hendelse `MØTT_OPP` / `ANGRE_MØTT_OPP` i
  `jobbsoker_hendelse` – ingen ny kolonne. «Har møtt» utledes av hendelsene
  (se eget avsnitt under).
- **`møtedag`** (1:1 med treff): `rekrutteringstreff_id` (PK/FK), `fase`,
  `antall_rom`, `start_tidspunkt`, `varighet_min`, `pause_min`.
- **`workop_hovedansvarlig`:** `rekrutteringstreff_id`, `nav_ident`,
  `ansvarstype`; unik kombinasjon av treff + ansvarstype og treff + ident.
- **`rom_tildeling`:** `rekrutteringstreff_id`, `jobbsoker_id`, `romnummer`.
- **`arbeidsgiver_rotasjon`:** `arbeidsgiver_id`, `start_posisjon`.
- **`speedintervju_onske`:** `jobbsoker_id`, `arbeidsgiver_id`.
- **`speedintervju_fordeling`:** `jobbsoker_id`, `arbeidsgiver_id`, plassering
  og om jobbsøkeren er inkludert.
- **`speedintervju_vurdering`:** `jobbsoker_id`, `arbeidsgiver_id`,
  nullable `vurdering`, `andre_intervju` og `jobbtilbud`.

API-et bruker de offentlige domenenøklene `personTreffId` og
`arbeidsgiverTreffId`. Repository mapper disse til interne `jobbsoker_id` og
`arbeidsgiver_id`; interne database-ID-er skal ikke lekke ut i DTO-ene.

Foreslåtte endepunkter (under `/api/rekrutteringstreff/{id}/moetedag`, i tråd med
`/jobbsoker`- og `/formidling`-mønsteret):

| Metode | Sti                           | Funksjon                                                                                                 |
| ------ | ----------------------------- | -------------------------------------------------------------------------------------------------------- |
| GET    | `/moetedag`                   | Hent hele `MøtedagDTO`                                                                                   |
| PUT    | `/moetedag/oppmote`           | Registrer/fjern oppmøte (skriver `MØTT_OPP`/`ANGRE_MØTT_OPP`-hendelse)                                   |
| PUT    | `/moetedag/moteoppsett`       | Sett tider + 1–9 rom → opprett full round-robin-fordeling + rotasjon, fase = ROM                          |
| PUT    | `/moetedag/romfordeling`      | Erstatt komplett romfordeling etter manuell flytting eller eksplisitt «Fordel på nytt»                   |
| PUT    | `/moetedag/onsker`            | Sett/fjern ett ønskepar idempotent                                                                       |
| PUT    | `/moetedag/intervjufordeling` | Lagre rekkefølge over og under sperrelinjen for én arbeidsgiver                                          |
| PUT    | `/moetedag/vurderinger`       | Sett/fjern vurdering og oppfølging for ett par                                                           |

`PUT /romfordeling` tar alle rom med ordnede `personTreffId`-lister. Backend
validerer at romnumrene er unike innenfor 1–9, og at hver fremmøtt jobbsøker
finnes nøyaktig én gang uten ukjente personer.

`PUT /moteoppsett` oppretter bare den første planen. En identisk retry er
idempotent og returnerer eksisterende plan uten ny romfordeling; forsøk på å
endre et allerede opprettet møteoppsett avvises med `409 Conflict`. Det hindrer
at en gammel fane eller en annen arrangør overskriver manuelle romplasseringer.

Matriseendringer lagres per par (`personTreffId`, `arbeidsgiverTreffId`) i
stedet for å overskrive hele samlingen. Det reduserer faren for at de to
hovedansvarlige mister hverandres samtidige endringer. Møteoppsettet bør i
tillegg ha en versjon eller annen optimistisk lås dersom begge kan redigere det
samtidig.

Backend validerer at person og arbeidsgiver tilhører samme WorkOp-treff, og at
bare fremmøtte kan få ønsker og intervjufordeling. En vurdering kan bestå etter
at ønske og intervjufordeling fjernes. En vurderingsrad der vurdering er `null`
og begge boolean-feltene er `false`, slettes. Fjerning av oppmøte når det finnes
ønsker, intervjufordeling eller vurderinger skal kreve eksplisitt bekreftelse;
data må aldri bli hengende igjen inkonsistent.

Begge registrerte WorkOp-eiere må kunne lese alle relevante Formidlinger for
treffet. Dagens rollebaserte `alle`/`egne`-endepunkter dekker ikke nødvendigvis
det kravet og må avklares i backend. Frontendens midlertidige kobling med
fødselsnummer + organisasjonsnummer skal erstattes av en autoritativ kobling når
backendkontrakten utformes.

Tilgang: `verifiserAutorisasjon(ARBEIDSGIVER_RETTET)` + eksplisitt registrert
hovedansvarlig (utvikler kan ha bypass). Hovedansvar lagres med ansvarstype
`MARKEDSKONTAKT` eller `VEILEDER`, med maks én av hver per treff. Samme regel
håndheves for møtedag-endepunktene, direkte henting av WorkOp-treff via ID og
treff-søket. Formidlingsendepunktenes kontortilgang skal ikke gjenbrukes.

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
| Nr. 6 – Registrere oppmøte        | 1       | Steg 1 + burgermeny                                            |
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
  hovedansvarlig eller utvikler og i ikke-prod – skjult ellers. En 403 fra
  `/motedag` skjuler både fane og panel.
- **Stepper:** seks steg vises; fullførte steg er klikkbare, og steg uten
  forutsetninger er ikke-interaktive.
- **Steg 1 – oppmøte:** empty state når ingen er møtt; «Møtt»-tag og telleren
  «X av Y» oppdateres når oppmøte registreres fra burgermenyen.
- **Møteplan og rom:** «Opprett møteplan» fyller rommene, og «Gå til
  romfordeling» navigerer uten lagring. Test dra-og-slipp, direkte romvalg,
  innsetting sist, rollback ved lagringsfeil og full «Fordel på nytt» med
  bekreftelse. Rotasjonsplan-modalen viser klokkeslett, og «Skriv ut» finnes.
- **Steg 3 – ønsker:** matrisen viser kun fremmøtte jobbsøkere, og avkryssing
  oppdaterer telleren per rad.
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

Unngå assertions som bare speiler mock-data; verifiser at UI-et står i forventet
tilstand etter reelle brukerhandlinger.

---

## Avgrensninger for første versjon

- Møteoppsettet kan justeres etter opprettelse, men en endring av tidene
  fordeler ikke rommene på nytt. Det kan opprettes mellom 1 og 9 rom.
- Romfordelingen opprettes automatisk, kan endres manuelt og kan erstattes med en
  eksplisitt full round-robin-fordeling.
- Utskrift viser navn, rom og arbeidsgiver, men aldri fødselsnummer.
- Intervjufordelingen tar utgangspunkt i registrerte ønsker og lagrer rekkefølge
  over og under sperrelinjen, men ikke tidspunkt.
- Fjerning av oppmøte etter at ønsker, intervjufordeling eller vurderinger finnes,
  krever bekreftelse og rydder avhengige data atomisk.
- «2. intervju» og «Jobbtilbud» registreres i WorkOp. «Formidlet» registreres
  bare i Formidlinger og speiles skrivebeskyttet. Økonomidata inngår ikke.
- Egen `JobbsøkerStatus` for oppmøte krever en separat beslutning sammen med
  oppdatering av aktivitetsplan og aktivitetskort.

## Åpne spørsmål

- Hva gjør vi hvis møtene er gjennomført og noen endrer fordelingen på nytt?
  Diskuter **grad av låsing** av verdier i WorkOp gjennomføring-fanen.
- **Utskrift:** har romvertene egen notasjon for print, f.eks. bare initialer på
  jobbsøkere?
- Er dagens eier-/kontorregel streng nok for WorkOp, eller må «hovedansvarlige»
  modelleres eksplisitt før backend bygges?
- Hvilket backendendepunkt skal gi begge WorkOp-eierne komplett, autorisert
  lesetilgang til relevante Formidlinger?
- Hvilke autoritative domenenøkler skal erstatte frontendens midlertidige
  kobling med fødselsnummer + organisasjonsnummer?
- Når bør «møtt opp» løftes fra hendelse til egen `JobbsøkerStatus` – i takt med
  at oppmøte også oppdaterer aktivitetsplanen/aktivitetskortet?
