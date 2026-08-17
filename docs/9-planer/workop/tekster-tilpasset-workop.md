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
   endres. Konsekvens: **jobbsøkere som allerede er invitert får aldri ny
   tekst**, uansett hvordan vi løser resten. Nye WorkOp-tekster gjelder kun
   invitasjoner sendt etter utrulling.
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

---

## Designvalg: hvordan skille WorkOp-tekst fra vanlig tekst

Dette er hovedbeslutningen i oppgaven, og den bør tas før implementering.

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

**Alternativ A**, forutsatt at rollback-risikoen fjernes først:

1. Gjør `Maler.valueOf(...)` tolerant for ukjente mal-navn (returner en
   fallback i stedet for å kaste `IllegalArgumentException`). Dette er en
   liten, isolert endring som kan deployes alene, og som fjerner alternativ
   A-ens eneste reelle ulempe. Den gjør også fremtidige mal-utvidelser
   trygge.
2. Deretter innføres WorkOp-malene, med nøkkelfunn 8-sjekklisten som krav i
   samme leveranse.

A gir best sporbarhet, unngår databaseendring og passer arkitekturen som
allerede finnes. Velges B likevel, bør kategorien få egen kolonne — ikke
legges i `flettedata`.


---

## Kartlegging av Trello-tekster

| # | Trello-tekst | Dekkes av dagens løp? | Forslag |
| - | ------------ | --------------------- | ------- |
| 1 | Første melding i aktivitetsplanen («ER DU KLAR FOR NYE JOBBMULIGHETER?») | ✅ Ja — invitasjonsløpet | Ny WorkOp-beskrivelse på aktivitetskortet. Avklar om invitasjons-SMS/e-post også skal ha WorkOp-variant (Trello har ingen egen invitasjons-SMS) |
| 2 | SMS om kontaktforsøk («Jeg har forsøkt å ringe deg …») | ❌ Nei — ingen trigger | Sendes manuelt i dag. Avklar: skal dette automatiseres (ny handling i frontend → nytt endepunkt + ny mal), eller forblir den manuell? Anbefaler å holde utenfor første leveranse |
| 3 | Workshop-SMS (dagen før workshop) | ⛔ Blokkert | Workshop = formøte, som er utsatt («vi venter med støtte for formøte»). Tas når formøte-støtte landes |
| 4 | Melding i aktivitetsplan 2 dager før WorkOp (program for dagen) | ❌ Nei — nytt løp | Ny scheduler-hendelse (f.eks. `PÅMINNELSE_2_DAGER_FØR`) for jobbsøkere med SVART_JA. Merk: dagens oppdateringsvei kan ikke endre beskrivelsen på et eksisterende kort (nøkkelfunn 7), så dette krever enten et eget kort eller en utvidelse av `oppdaterRekrutteringstreffAktivitetskort` |
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

- WorkOp-tekster i `Mal.kt` etter valgt alternativ (se «Designvalg»).
  Anbefalingen er alternativ A: egne mal-objekter, med `Maler.valueOf` gjort
  tolerant først.
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

### 6. Nye påminnelsesløp (tekst 4 og 5) — skilles ut

Krever ny infrastruktur og bør være egen leveranse:

- Ny hendelsestype (eller egen tabell/scheduler) i rekrutteringstreff-api som
  finner WorkOp-treff som nærmer seg og oppretter hendelser for
  SVART_JA-jobbsøkere (T-2 dager for aktivitetsplan-melding, T-1 dag for SMS).
  Eksisterende `DefaultScheduler` + leader election kan gjenbrukes.
- Nye maler i kandidatvarsel-api og ny/oppdatert aktivitetskort-melding.
- Idempotens må sikres (samme mønster som dagens `hendelseId`/`varselId`).
- Avklar hva som skjer når treffet flyttes, avlyses eller jobbsøker trekker
  svaret sitt etter at påminnelsen er planlagt, men før den er sendt.

---

## Utrulling og rollback

Rekkefølgen er viktig fordi produsent og konsumenter deployes uavhengig:

1. **Tolerant mal-lesing først** (ved alternativ A) — `Maler.valueOf` må tåle
   ukjente mal-navn før nye maler tas i bruk. Egen, liten deploy.
2. **Konsumentene** — lytterne må tåle både med og uten `treffkategori`
   (`interestedIn`, ikke `requireKey`). Deploy før produsenten sender feltet.
3. **Produsenten** — rekrutteringstreff-api begynner å sende `treffkategori`.
4. **Frontend** når malene er tilgjengelige fra `MeldingsmalApi`.

**Rollback:** Med steg 1 på plass er rollback trygt i begge alternativer.
Uten steg 1 vil rader skrevet med nye mal-navn gi
`IllegalArgumentException` hvis kandidatvarsel-api rulles tilbake, og da må
rollback kombineres med opprydding i `minside_varsel`.

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
  aktivitetskortet og SMS-en faktisk har WorkOp-tekst.

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
8. Teknisk, til teamet: velger vi alternativ A eller B i «Designvalg»?

---

## Foreslått leveranseplan

| Del | Innhold | Avhengigheter |
| --- | ------- | ------------- |
| A0 | Gjør `Maler.valueOf` tolerant for ukjente mal-navn (kandidatvarsel-api) | Ingen — kan deployes alene |
| A | Konsumentene tåler valgfri `treffkategori` (kandidatvarsel-api, aktivitetskort) | Ingen |
| B | `treffkategori` på `rekrutteringstreffinvitasjon` + opprydding av `beskrivelse: "TODO"` | Del A utrullet |
| C | WorkOp-tekst for invitasjon: aktivitetskort-beskrivelse + SMS/e-post/MinSide (tekst 1) | Del A0 + B |
| D | Forhåndsvisning av WorkOp-mal i frontend + `getMalTekst` | Del C |
| E | WorkOp-varianter av endring- og avlyst-tekster + `treffkategori` på de to øvrige eventene | Avklaring 6 |
| F | WorkOp-tekster på landingssiden i rekrutteringstreff-bruker | Avklaring 5 |
| G | Påminnelsesløp T-2 og T-1 (tekst 4 og 5) | Ny scheduler, egen oppgave |
| H | Workshop-SMS (tekst 3) | Formøte-støtte (utsatt) |
| I | Kontaktforsøk-SMS (tekst 2) | Avklaring 3, evt. nytt manuelt varsel-endepunkt |

Del A0–D er den minste leveransen som oppfyller Trello-kortets DoD for tekst 1.

## Definition of done (forslag)

- WorkOp-treff (`kategori = WORKOP`) gir WorkOp-tekster i aktivitetsplanen og
  i SMS/e-post/MinSide ved invitasjon; vanlige treff er uendret.
- Konsumenter tåler eventer både med og uten `treffkategori`, i begge
  rekkefølger av deploy (bakoverkompatibelt).
- Testdekning, med eksisterende testfiler som utgangspunkt:
  - `RekrutteringstreffInvitasjonTest.kt` (aktivitetskort) — én test per
    kategori, pluss én uten `treffkategori` i meldingen.
  - `RekrutteringstreffRapidTest.kt` / `MeldingsmalTest.kt`
    (kandidatvarsel-api) — riktig tekst per kategori, og at
    `MeldingsmalApi` returnerer WorkOp-varianten.
  - Ved alternativ A: test at `Maler.valueOf` kjenner de nye navnene og at
    `malerForVarselType` inkluderer dem.
- Tekstene er kvalitetssikret av WorkOp og språkvasket (klarspråk) før
  produksjonssetting.
- Åpne spørsmål over er besvart.

