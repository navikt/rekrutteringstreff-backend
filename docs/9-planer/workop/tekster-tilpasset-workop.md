# Plan: Tekster tilpasset WorkOp

**Status:** Analyse/utkast — ikke påbegynt.
**Omfang:** rekrutteringstreff-api, rekrutteringsbistand-aktivitetskort,
rekrutteringsbistand-kandidatvarsel-api, rekrutteringsbistand-frontend
(forhåndsvisning), ev. rekrutteringstreff-bruker.
**Kilde:** Trello-kort «Tekster tilpasset WorkOp» (fem ferdige tekstutkast).

**Repo-oppsett:** `rekrutteringstreff-api` og `rekrutteringsbistand-aktivitetskort`
ligger i **samme monorepo** (`apps/`), mens `rekrutteringsbistand-kandidatvarsel-api`,
`rekrutteringsbistand-frontend` og `rekrutteringstreff-bruker` er egne repoer.
Produsent- og aktivitetskort-endringene kan derfor gjøres i én PR, men de
deployes fortsatt som separate apper — se «Utrulling og rollback».

Målet er at automatiske tekster som sendes til jobbsøker (aktivitetskort i
aktivitetsplanen, SMS/e-post/MinSide-varsel) skal ha egne WorkOp-varianter når
treffet har `kategori = WORKOP`. I tillegg inneholder oppgaven tekster for løp
som **ikke finnes i systemet i dag** (påminnelser) — disse krever nye triggere
og bør skilles ut som eget arbeid.

---

## Begreper

- **T**: Kalenderdatoen WorkOp starter (`fraTid`), tolket i tidssonen
  `Europe/Oslo`.
- **T-2**: Kalenderdagen to dager før T. Dette betyr ikke nødvendigvis
  nøyaktig 48 timer før start.
- **T-1**: Kalenderdagen én dag før T. Dette betyr ikke nødvendigvis
  nøyaktig 24 timer før start.

Planen bruker T-2 og T-1 som kortformer for formuleringene «2 dager før
WorkOp» og «dagen før WorkOp» i Trello-kortet. Klokkeslett for utsending er
ikke oppgitt og må avklares før en eventuell scheduler implementeres.

---

## Dagens løsning: hvor tekstene ligger

| Tekst | System | Fil | Merknad |
| ----- | ------ | --- | ------- |
| Aktivitetskort-beskrivelse (invitasjon) | rekrutteringsbistand-aktivitetskort | `RekrutteringstreffInvitasjonLytter.kt` | Hardkodet streng ved kall til `repository.opprettRekrutteringstreffInvitasjon(...)` |
| Aktivitetskort-handling («Sjekk ut treffet») | rekrutteringsbistand-aktivitetskort | `Repository.kt` | Hardkodet `AktivitetskortHandling` |
| Aktivitetskort-detaljer (Tid, Sted) | rekrutteringsbistand-aktivitetskort | `Repository.kt`, `utils.kt` | Genereres fra treffdata; `formaterTidsperiode(...)` |
| SMS/e-post/MinSide invitasjon (`KANDIDAT_INVITERT_TREFF`) | rekrutteringsbistand-kandidatvarsel-api | `minside/Mal.kt` | Fast tekst, ingen flettedata |
| SMS/e-post/MinSide endring (`KANDIDAT_INVITERT_TREFF_ENDRET`) | rekrutteringsbistand-kandidatvarsel-api | `minside/Mal.kt` | Flettedata: valgte endrede felter |
| SMS/e-post/MinSide avlysning (`KANDIDAT_INVITERT_TREFF_AVLYST`) | rekrutteringsbistand-kandidatvarsel-api | `minside/Mal.kt` | Fast tekst |
| Forhåndsvisning av SMS i InviterModal / republisering | rekrutteringsbistand-frontend | `hentMeldingsmaler.ts`, `MeldingsmalVisning.tsx` | Henter maler fra kandidatvarsel-api (`MeldingsmalApi`) |
| Landingsside for jobbsøker (svarboks, info) | rekrutteringstreff-bruker | `Svarboks.tsx`, `InfoSide.tsx` | Generiske «treff»-tekster; appen bruker ikke `kategori` i dag |

### Nøkkelfunn

1. **Kafka-eventene har ikke kategori.** Verken `rekrutteringstreffinvitasjon`,
   `rekrutteringstreffoppdatering` eller `rekrutteringstreffSvarOgStatus`
   inneholder `treffkategori`. Konsumentene (kandidatvarsel-api,
   aktivitetskort) kan derfor ikke skille WorkOp fra vanlig treff i dag. Dette
   er den sentrale integrasjonsendringen.
2. **Produsentsiden er enkel å endre.** `JobbsøkerhendelserScheduler` henter
   hele treffet via `hentTreff(...)` før publisering, og `Rekrutteringstreff`
   har allerede `kategori: RekrutteringstreffKategori`. Å legge
   `treffkategori` på eventene er en liten endring i
   `Aktivitetskortinvitasjon`, `AktivitetskortOppdatering` og
   `RekrutteringstreffSvarOgStatus`.
3. **Ingen påminnelse-løp finnes.** Det finnes ingen scheduler som sender
   meldinger «2 dager før» eller «dagen før» et treff. Dagens løp er kun
   invitasjon, endring og avlysning.
4. **Ingen kontaktperson på treffet.** WorkOp-tekstene viser til
   «hovedansvarlige X» og direktenummer. Treffet har `eiere` (nav-identer),
   men verken navn eller telefonnummer til en kontaktperson er lagret.
   Flettedata for dette krever nytt felt eller at teksten skrives uten
   personlig kontaktinfo.
5. **rekrutteringstreff-bruker eksponerer allerede `kategori`.**
   rekrutteringstreff-minside-api returnerer `kategori` til bruker-appen
   (og skjuler arbeidsgiverlisten for WORKOP der). Appen kan derfor rendre
   WorkOp-tekster betinget uten nye API-endringer — men gjør ikke det i dag.
6. **`beskrivelse` finnes allerede i invitasjonseventet, men er `"TODO"`.**
   `Aktivitetskortinvitasjon` sender `"beskrivelse" to "TODO"`, mens
   `RekrutteringstreffInvitasjonLytter` ignorerer feltet og bruker sin egen
   hardkodede tekst. Feltet er altså allerede reservert for teksteierskap på
   produsentsiden — se «Designvalg» under. Uansett valg bør `"TODO"` ryddes
   opp i denne oppgaven.
7. **Beskrivelsen på et aktivitetskort oppdateres aldri.**
   `oppdaterRekrutteringstreffAktivitetskort` bygger ny rad med
   `SELECT ... beskrivelse ... FROM aktivitetskort` — beskrivelsen kopieres
   uendret fra forrige versjon. Kun tittel, datoer og detaljer (tid/sted)
   endres. Konsekvens: **jobbsøkere som allerede er invitert får ikke ny
   tekst uten at oppdateringsveien bygges ut eksplisitt**. Som standard
   gjelder nye WorkOp-tekster kun invitasjoner sendt etter utrulling.
8. **Mal-navn er koblet i tre lag.** Innfører vi nye mal-navn må alle tre
   oppdateres samtidig, ellers får vi stille feil:
   - `Maler.malerForVarselType(VarselType.REKRUTTERINGSTREFF)` i
     kandidatvarsel-api filtrerer databasespørringer på mal-navn.
   - `Maler.valueOf(...)` i `MinsideVarsel.RowMapper` **kaster
     `IllegalArgumentException`** for ukjente mal-navn ved lesing fra
     databasen. Dette er en reell rollback-risiko (se «Utrulling»).
   - `getMalTekst(...)` i frontend (`minsideStatusUtil.ts`) er en `switch` på
     mal-navn med `default -> null`. Ukjent mal gir tom statustekst i
     jobbsøkerlisten.
9. **Dagens modell har ett aktivitetskort per jobbsøker per treff.**
   Tabellen `rekrutteringstreff` i aktivitetskort-appen har
   `UNIQUE (rekrutteringstreff_id, fnr)`, og alle svar- og statusendringer
   slår opp ett `aktivitetskort_id` via denne koblingen. Et nytt kort på T-2
   passer derfor ikke i dagens modell. Det vil enten avvises av databasen
   eller kreve en ny én-til-mange-modell som gjør det uklart hvilket kort som
   skal få status `GJENNOMFORES`, `FULLFORT` eller `AVBRUTT`.
10. **T-2-meldingen kan ikke uten videre være første invitasjon.**
    Systemet bruker invitasjonen til å opprette aktivitetskortet og samle
    svar. T-2-meldingen er formulert til deltakere («vi gleder oss til å møte
    dere») og bør kun gå til dem som har svart ja. Hvis første invitasjon
    sendes T-2, finnes det ikke et tidligere svargrunnlag, og jobbsøkeren får
    svært kort tid til å svare og forberede seg. Dette kolliderer også med
    workshop/formøte før WorkOp. Det kan bare velges hvis WorkOp bekrefter at
    deltakelse avklares utenfor løsningen og at svarfristen kan ligge så sent.
    Det er ikke funnet et teknisk krav om et bestemt antall dager mellom
    invitasjon og treff; backend krever bare at svarfrist finnes, mens
    frontend hindrer at den settes etter treffstart. Begrensningen er derfor
    primært funksjonell, ikke teknisk.
11. **Treffet har allerede et rikt «innlegg» på jobbsøkersiden.**
    Innlegget kan inneholde programmet og vises via lenken fra
    aktivitetskortet. Feltet `sendesTilJobbsokerTidspunkt` finnes, men brukes
    ikke til å filtrere eller planlegge visning i dagens kode; frontend setter
    det til nåtid, og minside-api henter alle innlegg. Det kan derfor brukes
    til å vise informasjon fra første publisering, men ikke som en fungerende
    T-2-planlegger uten ny utvikling.
12. **Trello-tekstene samsvarer ikke fullt ut med dagens brukerflyt.**
    Første tekst sier «Svar her i dialogen», men dagens aktivitetskort lenker
    til rekrutteringstreff-bruker, der jobbsøkeren svarer på invitasjonen.
    Samme tekst omtaler workshop med dato og sted, selv om støtte for
    formøte/workshop er utsatt. Tekstene må derfor funksjonelt tilpasses før
    språkvask; de kan ikke kopieres direkte inn.

---

## Anbefalt enkleste tilpasning

Skill mellom **innhold** og **varslingstidspunkt**. Trello-kortet blander en
første invitasjon, informasjon før møtet og påminnelser. De trenger ikke
løses med samme mekanisme.

### Minste leveranse

1. Inviter jobbsøkere tidlig nok til at de kan svare og forberede seg, slik
   dagens flyt er laget for.
2. Legg `treffkategori` kun på `rekrutteringstreffinvitasjon`.
3. La aktivitetskort-appen velge en egen WorkOp-beskrivelse ved opprettelse av
   det **ene eksisterende aktivitetskortet**.
4. Behold dagens generiske invitasjons-SMS/e-post
   (`KANDIDAT_INVITERT_TREFF`) inntil WorkOp eksplisitt ber om en egen tekst
   også der. Trello-tekst 1 gjelder aktivitetsplanen, ikke invitasjons-SMS.
5. Legg program og praktisk informasjon i eksisterende `innlegg` på
   jobbsøkersiden. Informasjonen kan vises fra første publisering hvis kravet
   om nøyaktig T-2-tidspunkt kan slippes.

WorkOp-beskrivelsen bør være kort og stabil. Dato, klokkeslett og sted finnes
allerede som strukturerte detaljer på aktivitetskortet og bør ikke
dupliseres i friteksten. Beskrivelsen må be jobbsøkeren åpne lenken og svare
der — ikke «svare i dialogen». Workshop-avsnittet tas ut inntil
formøte/workshop er støttet.

Dette krever endringer i rekrutteringstreff-api og aktivitetskort-appen, men
**ingen endring i kandidatvarsel-api, ingen ny scheduler og ingen nye
aktivitetskort**.

### Hvis T-2-tidspunktet er et absolutt krav

Oppdater det eksisterende aktivitetskortet; ikke opprett et nytt. Da må vi:

- innføre en idempotent T-2-hendelse/scheduler for jobbsøkere med
  `SVART_JA`,
- utvide aktivitetskortets oppdateringsvei slik at den kan endre
  `beskrivelse`,
- avklare om T-2-teksten skal **erstatte** eller **legges til** den opprinnelige
  invitasjonsteksten,
- avklare om en aktivitetskort-oppdatering faktisk varsler jobbsøkeren. Hvis
  ikke, må oppdateringen kombineres med et MinSide-varsel/SMS.

### Alternativer vurdert

| Alternativ | Fordeler | Ulemper | Vurdering |
| ---------- | -------- | -------- | --------- |
| A: Én tidlig invitasjon, WorkOp-kort + program på jobbsøkersiden | Minst utvikling, bruker dagens svar- og statusflyt | Programmet kommer ikke som ny melding T-2 | **Anbefalt hvis tidspunktet kan avklares bort** |
| B: Tidlig invitasjon + oppdater samme kort T-2 | Oppfyller tidspunkt og unngår duplikat | Ny scheduler, oppdateringskontrakt og avklaringer om varsling | Velg kun hvis T-2 er et absolutt krav |
| C: Første invitasjon T-2 | Ingen egen påminnelsesflyt | For kort svartid; ingen tidligere SVART_JA; kolliderer med workshop | Ikke anbefalt uten eksplisitt prosessavklaring |
| D: Nytt aktivitetskort T-2 | Kan holde tekstene separat | Bryter én-kort-modellen og gir uklar statusflyt | Forkastes |

---

## Mulig liten, generell utvidelse: formøte

Formøte kan avgrenses fra hovedmodellen med en valgfri én-til-én-relasjon.
Modellen bør være generell for alle rekrutteringstreff, selv om WorkOp er
første bruker. Det gir et godt grunnlag for workshop-avsnittet i WorkOp-
invitasjonen og kan senere brukes av vanlige treff uten ny datamodell.

### Anbefalt datamodell

Bruk en generell tabell med navnet `rekrutteringstreff_formote`:

```text
rekrutteringstreff_formote
├── rekrutteringstreff_id  bigint, PK + FK → rekrutteringstreff.rekrutteringstreff_id
├── fra_tid                timestamptz, NOT NULL
├── til_tid                timestamptz, NULL
├── gateadresse            text, NOT NULL
├── postnummer             text, NOT NULL
├── poststed               text, NOT NULL
├── sist_endret            timestamptz, NOT NULL
└── sist_endret_av         text, NOT NULL
```

Kardinaliteten er `rekrutteringstreff 1 — 0..1 rekrutteringstreff_formote`:

- Ingen rad betyr at treffet ikke har formøte.
- Én rad betyr at formøte er konfigurert.
- Primærnøkkelen på `rekrutteringstreff_id` hindrer flere formøter for samme
  treff.
- Tabellen er uavhengig av `kategori`. WorkOp bruker funksjonen først, men
  et vanlig rekrutteringstreff kan bruke samme modell senere.

En ren dato er ikke nok til tekstene i Trello. Invitasjonen og
SMS-påminnelsen trenger minst dato, klokkeslett og sted. `fra_tid` bør derfor
være et tidspunkt i stedet for et `date`-felt. `til_tid` kan være valgfritt
hvis sluttid ikke skal vises.

### Regler

- `fra_tid` må være før treffets `fraTid`.
- Hvis `til_tid` finnes, må den være etter formøtets `fra_tid` og før eller
  lik treffets `fraTid`.
- Alle tidspunkt tolkes og vises i `Europe/Oslo`, men lagres som
  `timestamptz`.
- Første versjon bør ikke lagre navn eller telefon til hovedansvarlig. Bruk
  heller en generell kontakttekst inntil behov og personvern er avklart.

Valideringen gjøres i `FormøteService`; databasen håndhever
én-til-én-relasjonen og obligatoriske felt. Kategorien trenger ikke
valideres fordi modellen er generell.

### API og tilgang

En egen vertikal flyt holder formøtefeltene ute av hoved-DTO-en:

```text
GET    /api/rekrutteringstreff/{id}/formote
PUT    /api/rekrutteringstreff/{id}/formote
DELETE /api/rekrutteringstreff/{id}/formote
```

Foreslåtte klasser:

- `FormøteController`
- `FormøteService`
- `FormøteRepository`

Endepunktene bruker eksisterende Azure AD-autentisering. Skrivetilgang bør
kreve arbeidsgiverrettet rolle **og eierskap til treffet**, på samme måte som
andre administrative treff-funksjoner. Formøtedata inneholder i
utgangspunktet ikke personopplysninger, men tidspunkt og sted skal bare
vises til relevante brukere.

### Hvordan invitasjonen bygges

Når en jobbsøker inviteres:

1. `JobbsøkerhendelserScheduler` henter treffet og valgfritt formøte.
2. `rekrutteringstreffinvitasjon` får `treffkategori` og et valgfritt,
   strukturert `formote`-objekt med tidspunkt og sted.
3. Aktivitetskort-appen velger grunntekst ut fra treffkategori.
4. Hvis `formote` finnes, legges et formøteavsnitt til uavhengig av kategori.
   WorkOp kan omtale det som workshop i sin tekstvariant.

Bruk strukturerte felter i eventet, ikke en ferdig flettet tekst. Da kan
aktivitetskort-appen formatere tidspunkt konsistent og teksten kan endres
uten å endre datakontrakten.

### Viktigste konsekvens

Tabellen er enkel; **endringer etter at noen er invitert er den vanskelige
delen**. Dagens aktivitetskort beholder den opprinnelige beskrivelsen.

### Anbefaling: gjenbruk dagens endringsflyt

Formøte bør behandles som andre viktige endringer på et publisert treff:

1. Legg `FORMØTE` til `Endringsfelttype` i backend og frontend.
2. Vis «Nytt formøte» i dagens republiseringsdialog, med valg om varsling.
3. La `registrerEndring(...)` gjenbruke dagens mottakerregler:
   - Alle inviterte får hendelsen
     `TREFF_ENDRET_ETTER_PUBLISERING`, slik at aktivitetskortet oppdateres.
   - Bare jobbsøkere med aktivt svar ja får
     `TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON` og MinSide-varsel.
4. Utvid `rekrutteringstreffoppdatering` med et strukturert `formote`-objekt.
   Ved sletting må eventet uttrykkelig inneholde `formote: null`, slik at
   konsumenten kan skille sletting fra en eldre produsent som ikke sender
   feltet.
5. Aktivitetskort-appen bygger beskrivelsen på nytt med eller uten
   formøteavsnitt og oppdaterer kortet.
6. Legg `FORMØTE("formøte")` til `EndringFlettedata` i kandidatvarsel-api.
   Dagens generiske endringsmelding kan da gjenbrukes.

Dette er **moderat ekstraarbeid**, ikke en ny varslingsarkitektur. Det meste
av mottakerutvalg, hendelser, idempotens og MinSide-varsling finnes allerede.
Ekstraarbeidet ligger primært i å føre formøtedata gjennom oppdateringseventet
og bygge aktivitetskortbeskrivelsen på nytt.

Hvis dette vurderes som for stort i første versjon, er et trygt minimum å
kreve at formøte er ferdig konfigurert før første invitasjon og blokkere
endring/sletting etterpå. Ikke tillat stille endringer som gjør at
aktivitetskortene viser gammel informasjon.

### Omfang

Grunnfunksjonen er en liten til middels utvidelse:

- én Flyway-migrasjon,
- én liten controller/service/repository-flyt,
- en valgfri formøteseksjon (vises først i WorkOp-skjemaet),
- utvidelse av invitasjonseventet,
- betinget tekst i aktivitetskort-appen,
- komponenttester for treff med og uten formøte.

Støtte for endring etter invitasjon gjør omfanget middels og legger til:

- `FORMØTE` i eksisterende endringsfelt og republiseringsdialog,
- formøtedata i `rekrutteringstreffoppdatering`,
- oppdatering av aktivitetskortbeskrivelse,
- `FORMØTE` som flettedata i dagens generiske endringsvarsel.

Det krever ingen ny app, Kafka-topic, Nais-ressurs eller auth-mekanisme.
Workshop-SMS dagen før krever fortsatt en egen scheduler og varselmal, men
kan bygges senere på dataene i `rekrutteringstreff_formote`.

---

## Designvalg: hvordan skille WorkOp-tekst fra vanlig tekst

Dette designvalget er bare relevant dersom **SMS/e-post/MinSide-varselet**
også skal få WorkOp-tekst. Det trengs ikke for minste leveranse over.

**Viktig premiss:** meldingsteksten genereres **ikke** når lytteren mottar
eventet, men når varselet sendes. `VarselScheduler` leser raden fra
`minside_varsel`, rekonstruerer malen med `Maler.valueOf(mal)` og kaller
`mal.smsTekst()` i `MinsideClient`. Alt som skal påvirke teksten må derfor
være **persistert på varselet** — i dag er det bare `mal` og `flettedata`.

### Alternativ A — egne mal-objekter per kategori

Nye maler, f.eks. `KANDIDAT_INVITERT_WORKOP`. Lytterne velger mal ut fra
`treffkategori` i eventet, og mal-navnet lagres som i dag.

- ✅ Ingen databaseendring — `mal` er `text` uten constraint.
- ✅ Ren sporing: mal-navnet i `minside_varsel` og i `minsideVarselSvar` viser
  direkte hvilken tekst som ble sendt.
- ✅ Følger eksisterende arkitektur: én mal = én tekst.
- ✅ Tekstene kan utvikle seg uavhengig (WorkOp kan få egne flettedata uten at
  vanlig treff påvirkes).
- ⚠️ Krever samtidig endring i alle tre lagene i nøkkelfunn 8.
- ⚠️ Rollback: rader med nytt mal-navn kan ikke leses av gammel kode
  (`Maler.valueOf` kaster). Se avbøtende tiltak under.

### Alternativ B — samme mal, kategori-styrt tekst

Behold `KANDIDAT_INVITERT_TREFF` som mal-navn, og la malen velge tekstvariant
ut fra en persistert kategori.

- ✅ Ingen endring i mal-filtrering eller frontendens `getMalTekst`.
- ✅ Trygg rollback: mal-navnene er uendret.
- ⚠️ **Krever at kategorien lagres på varselet** (se premisset over) — enten
  ny kolonne (Flyway-migrasjon i kandidatvarsel-api) eller ved å legge
  kategorien i `flettedata`-kolonnen, som er ment for endringsfelter. Det
  siste er en snarvei som gjør `flettedata` tvetydig.
- ⚠️ Mal-objektene blir mer komplekse, og `MeldingsmalApi` må uansett
  eksponere begge tekstvariantene til frontend.

### Anbefaling

**Alternativ A**, men uten en generell fallback for ukjente mal-navn. En
fallback kan skjule kontraktsfeil og sende feil tekst. Gjør i stedet
utrullingen i to bakoverkompatible steg:

1. Deploy støtte for de nye WorkOp-malnavnene i `Maler.valueOf`,
   `malerForVarselType`, `MeldingsmalApi` og frontend, uten at lytterne tar
   dem i bruk.
2. Deploy deretter lytterne som velger WorkOp-mal når
   `treffkategori = WORKOP`.

Da kan steg 2 rulles tilbake til en versjon som allerede kjenner mal-navnene.
A gir best sporbarhet, unngår databaseendring og passer arkitekturen som
allerede finnes. Velges B likevel, bør kategorien få egen kolonne — ikke
legges i `flettedata`.


---

## Kartlegging av Trello-tekster

| # | Trello-tekst | Dekkes av dagens løp? | Forslag |
| - | ------------ | --------------------- | ------- |
| 1 | Første melding i aktivitetsplanen («ER DU KLAR FOR NYE JOBBMULIGHETER?») | ✅ Ja — invitasjonsløpet | Ny, kort WorkOp-beskrivelse på aktivitetskortet. Bruk kortets strukturerte tid/sted, endre «svar i dialogen» til svar via lenken, og ta ut workshop-avsnittet inntil formøte støttes. Avklar om invitasjons-SMS/e-post også skal ha WorkOp-variant |
| 2 | SMS om kontaktforsøk («Jeg har forsøkt å ringe deg …») | ❌ Nei — ingen trigger | Sendes manuelt i dag. Avklar: skal dette automatiseres (ny handling i frontend → nytt endepunkt + ny mal), eller forblir den manuell? Anbefaler å holde utenfor første leveranse |
| 3 | Workshop-SMS (dagen før workshop) | ⏸️ Avhengig | Kan bygges når `rekrutteringstreff_formote` finnes, men krever fortsatt egen scheduler og varselmal. Uten formøte-støtte forblir den utsatt |
| 4 | Melding i aktivitetsplan 2 dager før WorkOp (program for dagen) | ❌ Nei — nytt løp | Avklar først om programmet kan vises fra første invitasjon på jobbsøkersiden. Hvis T-2 er absolutt: oppdater eksisterende kort for SVART_JA; ikke opprett et nytt kort (nøkkelfunn 9) |
| 5 | SMS-påminnelse (dagen før WorkOp) | ❌ Nei — nytt løp | Ny scheduler-hendelse (`PÅMINNELSE_1_DAG_FØR`) → ny SMS-mal i kandidatvarsel-api. Kun til SVART_JA |

I tillegg bør **endring**- og **avlysning**-malene vurderes for WorkOp-varianter
for konsistens (DoD: «Endret / lagt til tekstene for WorkOp-treff»), selv om
Trello-kortet ikke nevner dem eksplisitt.

### Flettedatabehov

WorkOp-tekstene inneholder flere variabler enn dagens løp støtter:

- Dato, klokkeslett, sted — finnes på treffet ✅
- Kontaktperson/navn og direktenummer — **finnes ikke** ⚠️ (se nøkkelfunn 4)
- Workshop-dato/-sted — finnes ikke før formøte-støtte ⛔

Dagens invitasjonsmal bruker ingen flettedata; påminnelsesløpene vil trenge
flettedata på samme måte som endringsløpet (`hendelse_data` → Rapids).

---

## Foreslåtte endringer per system

### 1. rekrutteringstreff-api (produsent)

- Legg `treffkategori` (string: `REKRUTTERINGSTREFF` / `WORKOP`) på de
  Rapids-eventene som faktisk trenger det. Verdien hentes fra treffet som
  allerede er lastet i `JobbsøkerhendelserScheduler`.

  | Event | Trenger kategori? | Begrunnelse |
  | ----- | ----------------- | ----------- |
  | `rekrutteringstreffinvitasjon` | ✅ Ja | Styrer både aktivitetskort-beskrivelse og invitasjonsvarsel |
  | `rekrutteringstreffoppdatering` | ⚠️ Kun hvis endringsteksten skal tilpasses | Aktivitetskortet endrer ikke tekst ved oppdatering (nøkkelfunn 7) |
  | `rekrutteringstreffSvarOgStatus` | ⚠️ Kun hvis avlysningsteksten skal tilpasses | Ellers brukes eventet bare til statusbytte |

  Start med invitasjonseventet; ta de to andre når avklaring 6 er besvart.

- Rydd opp i `"beskrivelse" to "TODO"` i `Aktivitetskortinvitasjon`: enten
  fyll feltet med reell tekst (hvis teksteierskapet flyttes hit) eller fjern
  det.
- Bakoverkompatibilitet: feltet er nytt og konsumentene må tåle at det mangler
  (tolkes som `REKRUTTERINGSTREFF`) til produsenten er rullet ut.

### 2. rekrutteringsbistand-kandidatvarsel-api

- **Ingen endring i minste leveranse** hvis dagens generiske
  invitasjons-SMS/e-post beholdes.
- WorkOp-tekster i `Mal.kt` etter valgt alternativ (se «Designvalg»).
  Anbefalingen er alternativ A: egne mal-objekter, innført i to
  bakoverkompatible deployer.
- Lytterne (`KandidatInvitertLytter`, `KandidatInvitertTreffEndretLytter`,
  `KandidatTreffAvlystLytter`) leser `treffkategori` med `interestedIn(...)`,
  ikke `requireKey(...)` — ellers slutter lytteren å plukke opp meldinger fra
  en produsent som ennå ikke er rullet ut. Manglende felt tolkes som
  `REKRUTTERINGSTREFF`.
- Sjekkliste ved nye mal-navn (nøkkelfunn 8), alt i samme leveranse:
  `Maler.valueOf`, `Maler.malerForVarselType`, DTO-ene i `MeldingsmalApi` og
  `getMalTekst` i frontend.
- `MeldingsmalApi` (`/meldingsmal/rekrutteringstreff`) må returnere
  WorkOp-varianten slik at frontend kan forhåndsvise riktig tekst.
- SMS-lengde: WorkOp-tekstene bør holdes korte nok til å unngå mange
  SMS-segmenter; den lange program-teksten hører hjemme i aktivitetsplanen,
  ikke i SMS.

### 3. rekrutteringsbistand-aktivitetskort

- `RekrutteringstreffInvitasjonLytter`: les `treffkategori` som valgfri nøkkel
  (`interestedIn`, ikke `requireKey` — ellers knekker lytteren under
  utrulling), og velg WorkOp-beskrivelse/-handling når kategorien er `WORKOP`.
  Merk at lytteren har `forbid("aktørId")` i precondition; nye felt påvirker
  ikke den.
- Alternativt kan teksten flyttes til produsenten via det eksisterende
  `beskrivelse`-feltet (nøkkelfunn 6). Det samler teksteierskapet i
  rekrutteringstreff-api, men sprer det bort fra der de øvrige
  aktivitetskort-tekstene bor i dag. Velg ett sted, ikke begge.
- Vurder WorkOp-tilpasset handlingstekst (i dag «Sjekk ut treffet» /
  «Sjekk ut treffet og svar»).
- Teksten må ikke nevne konkrete arbeidsgivere — WorkOp skjuler deltakerlisten
  for inviterte. Trello-teksten nevner bare «inntil fem arbeidsgivere»
  generelt, som er i tråd med dette.
- Sjekk makslengde på `beskrivelse` mot aktivitetsplanen (dab). Kolonnen er
  `TEXT` hos oss, så begrensningen ligger eventuelt hos konsumenten.
  Trello-teksten er vesentlig lengre enn dagens.

### 4. rekrutteringsbistand-frontend

- Forhåndsvisning i `InviterModal`/`MeldingsmalVisning` må vise WorkOp-malen
  når treffet er WorkOp. Frontend kjenner kategorien fra treff-objektet, men
  `hentMeldingsmaler` må kunne returnere WorkOp-variantene (se punkt 2).
- Ved alternativ A (anbefalt): utvid `minsideStatusUtil.getMalTekst` med de
  nye mal-navnene, ellers blir statusteksten i jobbsøkerlisten tom.

### 5. rekrutteringstreff-bruker (vurderes)

- Landingssiden bruker generiske «treff»-tekster (`Svarboks.tsx`,
  `InfoSide.tsx`). `kategori` er allerede tilgjengelig fra minside-api, så
  WorkOp-tekster kan rendres betinget uten nye API-endringer. Avklar med
  WorkOp om landingssidens tekster inngår i denne oppgaven, eller om
  aktivitetsplan + SMS er tilstrekkelig.
- Programmet kan allerede legges i treffets `innlegg` og vises på denne
  siden. Dette er enklere enn en ny aktivitetskortmelding dersom innholdet
  kan være synlig fra første publisering. `sendesTilJobbsokerTidspunkt` må
  ikke omtales som planlegging før feltet faktisk håndheves ved uthenting.

### 6. Nye påminnelsesløp (tekst 4 og 5) — skilles ut

Krever ny infrastruktur og bør være egen leveranse:

- Ny hendelsestype (eller egen tabell/scheduler) i rekrutteringstreff-api som
  finner WorkOp-treff som nærmer seg og oppretter hendelser for
  SVART_JA-jobbsøkere (T-2 dager for aktivitetsplan-melding, T-1 dag for SMS).
  Eksisterende `DefaultScheduler` + leader election kan gjenbrukes.
- T-2 skal oppdatere det eksisterende aktivitetskortet. Et nytt kort er ikke
  kompatibelt med dagens én-kort-modell.
- T-1 krever ny mal i kandidatvarsel-api. T-2 krever utvidet
  aktivitetskort-oppdatering, og eventuelt et eget varsel dersom en
  kortoppdatering ikke varsler jobbsøkeren.
- Idempotens må sikres (samme mønster som dagens `hendelseId`/`varselId`).
- Avklar hva som skjer når treffet flyttes, avlyses eller jobbsøker trekker
  svaret sitt etter at påminnelsen er planlagt, men før den er sendt.

---

## Utrulling og rollback

Rekkefølgen er viktig fordi produsent og konsumenter deployes uavhengig:

1. **Konsumentene** — lytterne må tåle både med og uten `treffkategori`
   (`interestedIn`, ikke `requireKey`). For minste leveranse gjelder dette
   aktivitetskort-appen. Kandidatvarsel-api trenger først endring hvis det
   skal velge en egen WorkOp-mal. Deploy før produsenten sender feltet.
2. **Produsenten** — rekrutteringstreff-api begynner å sende `treffkategori`.
   Dette er nok for minste leveranse til aktivitetskortet.
3. **Kun ved nye varselmaler:** deploy først kode som kjenner de nye
   mal-navnene i kandidatvarsel-api og frontend, uten å bruke dem.
4. Deploy deretter lytteren som begynner å persistere WorkOp-malnavnet.

**Rollback:** Steg 2 kan rulles tilbake fordi manglende kategori tolkes som
vanlig treff. Steg 4 kan rulles tilbake til versjonen fra steg 3, som allerede
kjenner mal-navnene. Ikke bruk en generell fallback for ukjente maler; det
kan skjule feil og sende feil melding.

**Ingen etterfylling:** Allerede opprettede aktivitetskort beholder gammel
beskrivelse (nøkkelfunn 7). Hvis WorkOp trenger at også eksisterende
deltakere får ny tekst, må det løses som en egen jobb — det er ikke en
bieffekt av denne endringen.

## Observerbarhet

- Tell antall sendte varsler og opprettede aktivitetskort per kategori
  (`REKRUTTERINGSTREFF` / `WORKOP`), slik at man ser at WorkOp-varianten
  faktisk brukes etter utrulling.
- Logg valgt mal/tekstvariant på info-nivå ved utsending (uten persondata) —
  dagens lyttere logger allerede `rekrutteringstreffId`.
- Første WorkOp-treff etter utrulling bør verifiseres manuelt: sjekk at
  aktivitetskortet har WorkOp-tekst. Sjekk også SMS-en hvis del J leveres.

## Review fra fire perspektiver

| Perspektiv | Vurdering | Funn |
| ----------- | --------- | ---- |
| Arkitektur | ✅ for minste leveranse | Gjenbruk ett aktivitetskort og eksisterende jobbside. Unngå ny scheduler før tidspunktkravet er bekreftet |
| Sikkerhet/personvern | ✅ med avklaring | Ingen ny mottakergruppe eller auth. Ikke ta med konkrete arbeidsgivere eller unødvendig kontaktinformasjon |
| Plattform | ✅ | Minste leveranse krever ingen nye Nais-ressurser eller topics. T-2/T-1 krever schedulering, idempotens og metrikker |
| Endringssikkerhet | ⚠️ | Deploy konsument før produsent. Nye mal-navn må innføres i to steg. T-2-flyten er blokkert på spørsmål 9–13 |

**Konklusjon:** Godkjent med endringer. Gjennomfør minste leveranse A–C først.
Ikke bygg T-2/T-1-automatisering eller flere aktivitetskort før de åpne
produktspørsmålene er avklart.

---

## Personvern og mottakere

- Mottaker er jobbsøker (personbruker) via aktivitetsplanen, SMS, e-post eller
  MinSide — kanal velges av MinSide basert på KRR, som i dag.
- Meldingene skal ikke inneholde fødselsnummer eller andre unødige
  personopplysninger. Navn på hovedansvarlig/direktenummer i SMS må vurderes
  (bedre: «Kontakt veilederen din» eller et sentralt nummer).
- WorkOp-regelen om skjulte arbeidsgivere gjelder også i tekster: aldri nevn
  konkrete arbeidsgivere i meldinger til inviterte.

---

## Åpne spørsmål til WorkOp/produkteier

1. Skal invitasjons-SMS/e-post også få egen WorkOp-tekst, eller holder det at
   aktivitetskortet (første melding) tilpasses?
2. Kontaktperson i tekstene («hilsen X, direktenummer Y»): skal dette lagres
   på treffet (nytt felt), eller skrives tekstene om til å unngå personlig
   kontaktinfo?
3. Skal SMS om kontaktforsøk (tekst 2) automatiseres, eller forblir den
   manuell?
4. Er påminnelsesløpene (tekst 4 og 5) del av denne oppgaven, eller egen
   oppgave? (Anbefaling: egen oppgave.)
5. Skal landingssiden i rekrutteringstreff-bruker få WorkOp-tekster nå?
6. Skal endring- og avlysning-meldingene få WorkOp-varianter? (Avgjør om
   `treffkategori` også må på `rekrutteringstreffoppdatering` og
   `rekrutteringstreffSvarOgStatus`.)
7. Trenger allerede inviterte jobbsøkere å få oppdatert tekst, eller holder
   det at nye invitasjoner får WorkOp-tekst? (Se nøkkelfunn 7 — etterfylling
   er ikke gratis.)
8. Hvis invitasjons-SMS/e-post skal tilpasses: velger vi alternativ A eller B
   i «Designvalg»?
9. **Når inviteres jobbsøkerne i den reelle WorkOp-prosessen, og hvor lang
   svartid trenger de?** Hvis de skal inviteres før workshop/formøte, kan
   T-2-meldingen ikke være første invitasjon.
10. **Må programmet publiseres nøyaktig to dager før, eller kan det være
    synlig fra første invitasjon?** Hvis det kan vises tidlig, kan eksisterende
    `innlegg` brukes uten scheduler.
11. **Betyr «melding i aktivitetsplan» at selve aktivitetskortet skal
    oppdateres, eller er det tilstrekkelig at programmet ligger på den lenkede
    WorkOp-siden?**
12. **Skal T-2-hendelsen aktivt varsle jobbsøkeren, eller er det nok at
    informasjonen blir synlig?** En oppdatering av aktivitetskortet må ikke
    antas å utløse et varsel før dette er verifisert mot aktivitetsplanen.
13. Hvis eksisterende aktivitetskort oppdateres T-2: skal programmet erstatte
    invitasjonsteksten, eller legges til slik at opprinnelig informasjon
    beholdes?
14. Er bransjene i første tekst (produksjon, lager, service, butikk og kontor)
    faste for alle WorkOp, eller varierer de per treff? Hvis de varierer, skal
    de ligge i treffets redigerbare `innlegg`, ikke hardkodes i
    aktivitetskort-malen.
15. Hvilket klokkeslett skal T-2- og T-1-meldingene sendes? Planen tolker dem
    som kalenderdager i `Europe/Oslo`, ikke som nøyaktig 48/24 timer før
    `fraTid`.
16. Er det alltid maksimalt ett formøte per treff? Forslaget
    `rekrutteringstreff_formote` forutsetter `0..1`.
17. Er formøte valgfritt for WorkOp, eller skal WorkOp ikke kunne
    publiseres/invitere uten at formøte er konfigurert? Modellen er generell,
    men denne regelen kan fortsatt være WorkOp-spesifikk.
18. Kan formøte endres eller slettes etter at første jobbsøker er invitert?
    Hvis ja, skal eksisterende aktivitetskort oppdateres og mottakerne
    varsles?
19. Har formøtet alltid eget tidspunkt og sted, eller kan stedet arves fra
    WorkOp-dagen?
20. Skal formøtedetaljene også vises på den lenkede siden i
    rekrutteringstreff-bruker, eller er aktivitetskortet tilstrekkelig?
21. Er arbeidsgiverrettet rolle + eierskap riktig tilgang for å endre
    formøte? Forslaget legger dette til grunn.

---

## Foreslått leveranseplan

| Del | Innhold | Avhengigheter |
| --- | ------- | ------------- |
| A | Aktivitetskort-appen tåler valgfri `treffkategori` | Ingen |
| B | `treffkategori` på `rekrutteringstreffinvitasjon` + opprydding av `beskrivelse: "TODO"` | Del A utrullet |
| C | WorkOp-beskrivelse på aktivitetskortet ved første invitasjon (tekst 1) | Del B |
| D | Vis program/praktisk informasjon i eksisterende `innlegg` fra første publisering | Avklaring 10 og 11 |
| E | WorkOp-varianter av endring- og avlyst-tekster + `treffkategori` på de to øvrige eventene | Avklaring 6 |
| F | WorkOp-tekster på landingssiden i rekrutteringstreff-bruker | Avklaring 5 |
| G | T-2: oppdater eksisterende aktivitetskort, eventuelt med separat varsel | Avklaring 10–13; ny scheduler, egen oppgave |
| G2 | T-1: SMS-påminnelse (tekst 5) | Ny scheduler og varselmal, egen oppgave |
| H0 | Formøte: generell `rekrutteringstreff_formote`, eget API/skjema og formøte i invitasjon | Avklaring 16–21 |
| H1 | Endring av formøte etter invitasjon via dagens republiserings- og varslingsflyt | Del H0; kan erstattes av låsing i første versjon |
| H | Workshop-SMS (tekst 3) | Del H0 + ny scheduler og varselmal |
| I | Kontaktforsøk-SMS (tekst 2) | Avklaring 3, evt. nytt manuelt varsel-endepunkt |
| J | Egen WorkOp-invitasjons-SMS/e-post + forhåndsvisning | Avklaring 1; nye mal-navn deployes i to steg |

Del A–C er den minste tekniske leveransen for tekst 1. Del D bør tas med hvis
WorkOp godtar at programmet vises fra første publisering. G og G2 er bare
nødvendige dersom tidspunktkravene opprettholdes.

## Definition of done (forslag)

- WorkOp-treff (`kategori = WORKOP`) gir WorkOp-tekst på det eksisterende
  aktivitetskortet ved invitasjon; vanlige treff er uendret.
- Det opprettes ikke flere aktivitetskort for samme jobbsøker og treff.
- Aktivitetskort-appen tåler invitasjonseventer både med og uten
  `treffkategori` (bakoverkompatibelt).
- Testdekning, med eksisterende testfiler som utgangspunkt:
  - `RekrutteringstreffInvitasjonTest.kt` (aktivitetskort) — én test per
    kategori, pluss én uten `treffkategori` i meldingen.
  - Bare hvis invitasjonsvarselet også tilpasses:
    `RekrutteringstreffRapidTest.kt` / `MeldingsmalTest.kt`
    (kandidatvarsel-api) — riktig tekst per kategori, og at
    `MeldingsmalApi` returnerer WorkOp-varianten.
  - Ved nye mal-navn: test at `Maler.valueOf` kjenner dem og at
    `malerForVarselType` inkluderer dem.
- For T-2/T-1 er DoD ikke fastsatt før spørsmål 9–13 er besvart.
- Tekstene er kvalitetssikret av WorkOp og språkvasket (klarspråk) før
  produksjonssetting.
- Åpne spørsmål over er besvart.
