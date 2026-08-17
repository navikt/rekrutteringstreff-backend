# Plan: Tekster tilpasset WorkOp

**Status:** Analyse/utkast — ikke påbegynt.
**Omfang:** rekrutteringstreff-api, rekrutteringsbistand-aktivitetskort,
rekrutteringsbistand-kandidatvarsel-api, rekrutteringsbistand-frontend
(forhåndsvisning), ev. rekrutteringstreff-bruker.
**Kilde:** Trello-kort «Tekster tilpasset WorkOp» (fem ferdige tekstutkast).

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

---

## Kartlegging av Trello-tekster

| # | Trello-tekst | Dekkes av dagens løp? | Forslag |
| - | ------------ | --------------------- | ------- |
| 1 | Første melding i aktivitetsplanen («ER DU KLAR FOR NYE JOBBMULIGHETER?») | ✅ Ja — invitasjonsløpet | Ny WorkOp-beskrivelse på aktivitetskortet. Verifiser makslengde på beskrivelse i aktivitetsplanen (dab) — teksten er lang. Avklar om invitasjons-SMS/e-post også skal ha WorkOp-variant (Trello har ingen egen invitasjons-SMS) |
| 2 | SMS om kontaktforsøk («Jeg har forsøkt å ringe deg …») | ❌ Nei — ingen trigger | Sendes manuelt i dag. Avklar: skal dette automatiseres (ny handling i frontend → nytt endepunkt + ny mal), eller forblir den manuell? Anbefaler å holde utenfor første leveranse |
| 3 | Workshop-SMS (dagen før workshop) | ⛔ Blokkert | Workshop = formøte, som er utsatt («vi venter med støtte for formøte»). Tas når formøte-støtte landes |
| 4 | Melding i aktivitetsplan 2 dager før WorkOp (program for dagen) | ❌ Nei — nytt løp | Ny scheduler-hendelse (f.eks. `PÅMINNELSE_2_DAGER_FØR`) → nytt aktivitetskort eller oppdatering av eksisterende kort, med WorkOp-program. Kun til SVART_JA |
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

- Legg `treffkategori` (string: `REKRUTTERINGSTREFF` / `WORKOP`) på alle tre
  Rapids-eventer: `rekrutteringstreffinvitasjon`, `rekrutteringstreffoppdatering`,
  `rekrutteringstreffSvarOgStatus`. Verdien hentes fra treffet som allerede er
  lastet i `JobbsøkerhendelserScheduler`.
- Bakoverkompatibilitet: feltet er nytt og konsumentene må tåle at det mangler
  (tolkes som `REKRUTTERINGSTREFF`) til produsenten er rullet ut.

### 2. rekrutteringsbistand-kandidatvarsel-api

- Nye WorkOp-maler i `Mal.kt`, enten som egne mal-objekter
  (f.eks. `KandidatInvitertTreffWorkop`) eller som kategori-parameter på
  eksisterende maler. Egne mal-objekter gir enklest sporing i
  `minsideVarselSvar` og databasen.
- Lytterne (`KandidatInvitertLytter`, `KandidatInvitertTreffEndretLytter`,
  `KandidatTreffAvlystLytter`) velger mal basert på `treffkategori` i eventet,
  med fallbakke til dagens maler når feltet mangler.
- `MeldingsmalApi` utvides med WorkOp-variantene slik at frontend kan vise
  riktig forhåndsvisning.
- SMS-lengde: WorkOp-tekstene bør holdes korte nok til å unngå mange
  SMS-segmenter; den lange program-teksten hører hjemme i aktivitetsplanen,
  ikke i SMS.

### 3. rekrutteringsbistand-aktivitetskort

- `RekrutteringstreffInvitasjonLytter`: les `treffkategori` som valgfri nøkkel
  (ikke `requireKey` — ellers knekker lytteren under utrulling), og velg
  WorkOp-beskrivelse/-handling når kategorien er `WORKOP`.
- Vurder WorkOp-tilpasset handlingstekst (i dag «Sjekk ut treffet» /
  «Sjekk ut treffet og svar»).
- Teksten må ikke nevne konkrete arbeidsgivere — WorkOp skjuler deltakerlisten
  for inviterte. Trello-teksten nevner bare «inntil fem arbeidsgivere»
  generelt, som er i tråd med dette.

### 4. rekrutteringsbistand-frontend

- Forhåndsvisning i `InviterModal`/`MeldingsmalVisning` må vise WorkOp-malen
  når treffet er WorkOp. Frontend kjenner kategorien fra treff-objektet, men
  `hentMeldingsmaler` må kunne returnere WorkOp-variantene (se punkt 2).

### 5. rekrutteringstreff-bruker (vurderes)

- Landingssiden bruker generiske «treff»-tekster. `kategori` er allerede
  tilgjengelig fra minside-api, så WorkOp-tekster kan rendres betinget uten
  nye API-endringer. Avklar med WorkOp om landingssidens tekster inngår i
  denne oppgaven, eller om aktivitetsplan + SMS er tilstrekkelig.

### 6. Nye påminnelsesløp (tekst 4 og 5) — skilles ut

Krever ny infrastruktur og bør være egen leveranse:

- Ny hendelsestype (eller egen tabell/scheduler) i rekrutteringstreff-api som
  finner WorkOp-treff som nærmer seg og oppretter hendelser for
  SVART_JA-jobbsøkere (T-2 dager for aktivitetsplan-melding, T-1 dag for SMS).
- Nye maler i kandidatvarsel-api og ny/oppdatert aktivitetskort-melding.
- Idempotens må sikres (samme mønster som dagens `hendelseId`/`varselId`).

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
6. Skal endring- og avlysning-meldingene få WorkOp-varianter?

---

## Foreslått leveranseplan

| Del | Innhold | Avhengigheter |
| --- | ------- | ------------- |
| A | `treffkategori` på alle tre Rapids-eventer (rekrutteringstreff-api) | Ingen |
| B | WorkOp-maler for invitasjon (SMS/e-post/MinSide) + aktivitetskort-beskrivelse (tekst 1) | Del A |
| C | WorkOp-varianter av endring- og avlyst-maler (hvis ønsket) | Del A |
| D | Forhåndsvisning av WorkOp-mal i frontend | Del B |
| E | Påminnelsesløp T-2 og T-1 (tekst 4 og 5) | Ny scheduler, egen oppgave |
| F | Workshop-SMS (tekst 3) | Formøte-støtte (utsatt) |
| G | Kontaktforsøk-SMS (tekst 2) | Avklaring, evt. nytt manuelt varsel-endepunkt |

## Definition of done (forslag)

- WorkOp-treff (`kategori = WORKOP`) gir WorkOp-tekster i aktivitetsplanen og
  i SMS/e-post/MinSide ved invitasjon; vanlige treff er uendret.
- Konsumenter tåler eventer med og uten `treffkategori` (bakoverkompatibelt).
- Komponenttester dekker begge kategoriene for invitasjonsløpet.
- Avklaringer over er besvart og tekster er kvalitetssikret av WorkOp før
  produksjonssetting.
