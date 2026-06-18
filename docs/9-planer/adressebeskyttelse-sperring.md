# Plan: Sperring av jobbsøkere med adressebeskyttelse

**Status:** Vedtatt løsning, ikke implementert (kun plan)
**Omfang:** Backend (rekrutteringstreff-api) + delt plattform (toi-synlighetsmotor) + frontend (rekrutteringsbistand-frontend)

Denne planen beskriver hvordan jobbsøkere med adressebeskyttelse skal **sperres** i
rekrutteringstreff: de skal ikke kunne legges til i en ny formidling, og dersom de allerede er
formidlet (lagt til _før_ sperringen inntraff) skal både navn og fødselsnummer anonymiseres i
formidlingslisten.

**Avklart med produkteier:** Vi velger den enkleste løsningen. Kode **6**, **7** og evt. **19**
behandles likt — all adressebeskyttelse som toi-synlighetsmotor allerede fanger tolkes som
«sperret». Vi trenger derfor ikke skille kode 6 fra kode 7 i den delte plattformkomponenten.

---

## Begrepsavklaring

| Begrep               | Betydning                                                                                                                                                                                                                                                       |
| -------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Skjult / inaktiv** | `jobbsoker.er_synlig = false`. Personen er ute av kandidatsøket (manglende CV, ikke under oppfølging, adressebeskyttelse, død, KVP osv.). I formidlingslisten nulles **kun** fødselsnummer; navn vises fortsatt, og UI viser «Inaktiv kandidat».                |
| **Sperret**          | Strengere håndtering av adressebeskyttelse. Personen kan **ikke** legges til i ny formidling, og i formidlingslisten anonymiseres **både navn og fødselsnummer**. UI viser «Skjermet». En sperret person er alltid også skjult (`er_synlig = false`, se under). |

### Nøkkelinnsikt: sperret er en delmengde av skjult

En person med adressebeskyttelse er **allerede** `er_synlig = false` i toi-synlighetsmotor, fordi
`erSynlig()` krever `harIkkeAdressebeskyttelse == true`. Konsekvensen:

- Sperrede er **allerede skjult** fra jobbsøkerlisten i treffene (`hentJobbsøkere(...)` filtrerer
  `er_synlig = TRUE`). Ingen ny logikk trengs for å skjule dem der.
- Sperrede **telles allerede** som skjulte i `antallSkjulte`
  (`status != SLETTET AND er_synlig = FALSE`). **Tellingen er felles** — vi innfører ikke en egen
  `antallSperret`-kategori. I jobbsøkerkonteksten er det ikke viktig å skille sperret fra skjult.

Det nye `sperret`-feltet trengs derfor **kun i formidlingskonteksten**, der det har betydning:
anonymisere navn og blokkere ny formidling.

---

## Datakilde og dataflyt

### Dagens synlighetsflyt i rekrutteringstreff-api

Synlighet settes i dag via to Kafka-veier, begge i pakken
`no.nav.toi.jobbsoker.synlighet`:

1. **Event-strøm** — `SynlighetsLytter.kt` lytter på `synlighet.erSynlig` (boolean) +
   `synlighet.ferdigBeregnet=true`. Kaller `JobbsøkerService.oppdaterSynlighetFraEvent(...)`.
2. **Need/behov** — `SynlighetsBehovScheduler.kt` sender `@behov: ["synlighetRekrutteringstreff"]`
   for jobbsøkere uten evaluert synlighet (`synlighet_sist_oppdatert IS NULL`).
   `SynlighetsBehovLytter.kt` mottar svaret `synlighetRekrutteringstreff.erSynlig` /
   `.ferdigBeregnet`. Kaller `JobbsøkerService.oppdaterSynlighetFraNeed(...)`.

`JobbsøkerRepository` har to UPDATE-metoder:

- `oppdaterSynlighetFraEvent(...)` — setter `er_synlig`, `synlighet_sist_oppdatert`,
  `synlighet_kilde = 'EVENT'`. Skriver hvis `synlighet_sist_oppdatert IS NULL` **eller**
  `synlighet_kilde = 'NEED'` **eller** eldre tidsstempel (event vinner alltid).
- `oppdaterSynlighetFraNeed(...)` — setter `synlighet_kilde = 'NEED'`. Skriver **kun** hvis
  `synlighet_sist_oppdatert IS NULL` (event har prioritet).

I dag bæres kun ett boolsk `erSynlig` over Kafka. For å vite at noen er **sperret** må vi føre ett
ekstra flagg helt frem — på samme to veier som `er_synlig`.

### Avledet sperret-flagg i toi-synlighetsmotor

Synlighetsmotor vet allerede at noen har adressebeskyttelse, gjennom `erIkkeKode6eller7` (fra
Arena-diskresjonskode i `Kandidat.kt`) og `harIkkeAdressebeskyttelse` (fra PDL-gradering). Fordi vi
behandler kode 6, 7 og 19 likt, avleder vi ett flagg uten å måtte skille kodene:

```kotlin
val sperret = !erIkkeKode6eller7 || !harIkkeAdressebeskyttelse
```

Den rå diskresjonskoden (6 vs 7) kastes etter lagring i synlighetsmotor-db, men det spiller ingen
rolle her — vi trenger den ikke. Ingen ny kolonne eller migrasjon i synlighetsmotor-db kreves; kun
å sende det avledede flagget videre i event og need-svar.

### Relevante filer

**toi-synlighetsmotor** (`toi-rapids-and-rivers/apps/toi-synlighetsmotor/...`):

- `kotlin/.../Kandidat.kt` — `erIkkeKode6EllerKode7(...)`, `harIkkeAdressebeskyttelse(...)`
- `kotlin/.../Evaluering.kt` — `Evaluering`, `Synlighet`, `somSynlighet()`
- `kotlin/.../Repository.kt` — `databaseMap`, `evalueringFraDB`, kolonnenavn
- `kotlin/.../rekrutteringstreff/SynlighetRekrutteringstreffLytter.kt` — need-svar (har gradering)
- `kotlin/.../SynlighetsgrunnlagLytter.kt` — event-publisering (`packet["synlighet"] = ...somSynlighet()`)

**rekrutteringstreff-api** (`apps/rekrutteringstreff-api/...`):

- `kotlin/.../jobbsoker/synlighet/SynlighetsLytter.kt` — event-lytter
- `kotlin/.../jobbsoker/synlighet/SynlighetsBehovLytter.kt` — need-lytter
- `kotlin/.../jobbsoker/synlighet/SynlighetsBehovScheduler.kt` — sender need
- `kotlin/.../jobbsoker/JobbsøkerService.kt` — `oppdaterSynlighetFraEvent/FraNeed`
- `kotlin/.../jobbsoker/JobbsøkerRepository.kt` — UPDATE-metodene + `er_synlig`-filtre
- `kotlin/.../formidling/FormidlingRepository.kt` — `hentMedWhere(...)`, liste-query med
  `CASE WHEN j.er_synlig THEN j.fodselsnummer ELSE NULL END`
- `kotlin/.../formidling/dto/FormidlingDto.kt` — liste-DTO `FormidlingDto` (`fødselsnummer`,
  `fornavn`, `etternavn` nullable)
- `kotlin/.../formidling/FormidlingService.kt` — `opprettFormidling(...)` (blokkeringspunkt)
- Telling for jobbsøkere: `JobbsøkerSokRepository.hentTellinger()` (`antallSkjulte`/`antallSlettede`) /
  `jobbsoker_sok_view` (filtrerer `er_synlig`/`status`) — sperrede dekkes allerede av `antallSkjulte`

**frontend** (`rekrutteringsbistand-frontend/...`):

- `app/api/rekrutteringstreff/[...slug]/formidling/useFormidlinger.ts` — `FormidlingSchema`, mocks
- `app/rekrutteringstreff/[rekrutteringstreffId]/_ui/formidling/FormidlingRad.tsx` — visning av navn/fnr
- Opprett-modal (velg jobbsøker-steg) under `_ui/header/actions/`

---

## Endringer

### toi-synlighetsmotor (delt plattform)

1. `Evaluering.kt`: legg til avledet `fun sperret(): Boolean = !erIkkeKode6eller7 || !harIkkeAdressebeskyttelse`,
   og ta feltet med i `Synlighet` (event) og i need-svaret.
2. `SynlighetsgrunnlagLytter.kt`: `packet["synlighet"]` (fra `somSynlighet()`) får feltet `sperret`.
3. `SynlighetRekrutteringstreffLytter.kt`: `packet[synlighetRekrutteringstreffBehov]` får `sperret`
   ved siden av `erSynlig` / `ferdigBeregnet`.

### rekrutteringstreff-api

4. **V11-migrasjon** (`V11__jobbsoker_sperret.sql`):
   `ALTER TABLE jobbsoker ADD COLUMN sperret boolean NOT NULL DEFAULT false;`
5. `SynlighetsLytter` / `SynlighetsBehovLytter`: les `sperret` fra pakken og send videre til service.
6. `JobbsøkerService` + `JobbsøkerRepository`: utvid `oppdaterSynlighetFraEvent/FraNeed` med
   `sperret`-parameter; UPDATE setter `sperret = ?` ved siden av `er_synlig`. Samme
   prioritetsregler som `er_synlig` (event vinner over need).
7. `FormidlingRepository.hentMedWhere(...)`: null ut navn **og** fnr ved sperret:

   ```sql
   CASE WHEN j.sperret THEN NULL WHEN j.er_synlig THEN j.fodselsnummer ELSE NULL END AS fodselsnummer,
   CASE WHEN j.sperret THEN NULL ELSE j.fornavn END AS fornavn,
   CASE WHEN j.sperret THEN NULL ELSE j.etternavn END AS etternavn,
   j.sperret AS sperret
   ```

8. `FormidlingDto`: legg til `val sperret: Boolean`.
9. `FormidlingService.opprettFormidling(...)`: i `validerOgHentArbeidsgivereOgJobbsøkere(...)`,
   før `lagreFormidlinger()`, valider at ingen valgte personer er `sperret`; returner `422` (eller
   `409`) med JSON `feil` + `hint`.

**Ikke nødvendig (allerede dekket av `er_synlig`):**

- Skjuling fra jobbsøkerlisten — `hentJobbsøkere(...)` filtrerer allerede `er_synlig = TRUE`.
- Telling — sperrede telles allerede i `antallSkjulte`
  (`JobbsøkerSokRepository.hentTellinger()`). Ingen endring i `hentTellinger`,
  `JobbsøkerSøkRespons`, `JobbsøkereOutboundDto` eller `jobbsoker_sok_view`.

### frontend (rekrutteringsbistand-frontend)

10. `FormidlingSchema` (`useFormidlinger.ts`): legg til `sperret: z.boolean()`.
11. `FormidlingRad.tsx`: når `sperret` → vis «Skjermet» (verken navn eller fnr); ellers dagens logikk
    («Inaktiv kandidat» når fnr er nullet pga. `er_synlig = false`).
12. Opprett-modal (velg jobbsøker-steg): sperrede er allerede utenfor jobbsøkerlisten
    (`er_synlig = false`), så de dukker ikke opp som valgbare. Backend-blokkeringen (punkt 9) er
    sikkerhetsnettet.

---

## Akseptansekriterier

1. En sperret jobbsøker kan **ikke** legges til i en ny formidling (opprett-endepunkt returnerer feil;
   opprett-modal viser dem ikke som valgbare).
2. En formidling lagt til **før** sperring vises fortsatt i formidlingslisten, men med **både navn og
   fødselsnummer anonymisert** og merket «Skjermet».
3. Skjult/inaktiv kandidat (manglende CV osv. uten adressebeskyttelse) oppfører seg som i dag: navn
   vises, fnr nulles, merket «Inaktiv kandidat».
4. Sperrede telles som skjulte i `antallSkjulte` (felles telling — ingen egen kategori) og er skjult
   fra jobbsøkerlisten i treffene.

## Tester som må skrives

**rekrutteringstreff-api:**

- `FormidlingRepositoryTest`: liste nuller **både** navn og fnr når `sperret = true` (skill fra
  `er_synlig = false`-testen som kun nuller fnr).
- `FormidlingerKomponentTest` (HTTP): sperret formidling returnerer anonymisert navn+fnr +
  `sperret = true`.
- `FormidlingServiceTest` / komponenttest: `opprettFormidling` blokkerer sperret person med riktig
  statuskode + `feil`/`hint`.
- Synlighet-lytter-tester: `sperret` fra event og need oppdaterer `jobbsoker.sperret`.
- `TestDatabase`: helper `settSperret(personTreffId, sperret)` analogt med `settSynlighet(...)`.

**toi-synlighetsmotor:**

- Test at `sperret` settes for kode 6/7/19 og PDL-graderte, og at det sendes i både event
  (`SynlighetsgrunnlagLytter`) og need (`SynlighetRekrutteringstreffLytter`).

**frontend (Playwright `tests/rekrutteringstreff/formidlinger.spec.ts`):**

- Rad med `sperret` viser «Skjermet» (verken navn eller fnr).
