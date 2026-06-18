# Plan: Sperring av jobbsøkere med adressebeskyttelse (kode 6 / kode 19)

**Status:** Ikke implementert (kun plan)
**Omfang:** Backend (rekrutteringstreff-api) + delt plattform (toi-synlighetsmotor) + frontend (rekrutteringsbistand-frontend)

Denne planen beskriver hvordan jobbsøkere med streng adressebeskyttelse skal **sperres** i
rekrutteringstreff: de skal ikke kunne legges til i en ny formidling, og dersom de allerede er
formidlet (lagt til _før_ sperringen inntraff) skal både navn og fødselsnummer anonymiseres i
formidlingslisten.

Planen er skrevet slik at den kan implementeres uten ny analyse. Les hele «Bakgrunn og
kodegrunnlag» før du begynner — der ligger nøkkelfunnene som avgjør valget mellom de to
alternativene.

---

## Begrepsavklaring

| Begrep               | Betydning                                                                                                                                                                                                                                                                    |
| -------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Skjult / inaktiv** | Dagens oppførsel: `jobbsoker.er_synlig = false`. Personen er ute av kandidatsøket (manglende CV, ikke under oppfølging, kode 6/7, adressebeskyttelse, død, KVP osv.). I formidlingslisten nulles **kun** fødselsnummer; navn vises fortsatt, og UI viser «Inaktiv kandidat». |
| **Sperret**          | Ny, strengere håndtering: personen har streng adressebeskyttelse. Kan **ikke** legges til i ny formidling, og i listen anonymiseres **både navn og fødselsnummer**. UI viser «Skjermet».                                                                                     |

**Avklart scope (fra produkteier):**

- **Sperret** = kun diskresjonskode **6** (`STRENGT_FORTROLIG`) og **19** (`STRENGT_FORTROLIG_UTLAND`).
- **Kode 7** (`FORTROLIG`) og alle andre årsaker = vanlig **skjuling** (eksisterende `er_synlig = false`-oppførsel). Ingen sperring.

---

## Bakgrunn og kodegrunnlag (viktig — les før implementering)

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

**Konklusjon:** I dag bæres kun ett boolsk `erSynlig` over Kafka. Verken event eller need-svar
inneholder _årsaken_ til at noen er usynlig. For å vite at noen er **sperret** må vi føre mer
informasjon helt frem.

### Hvordan adressebeskyttelse beregnes i toi-synlighetsmotor

Det finnes **to uavhengige kilder** til adressebeskyttelse, og det er dette som avgjør hvor
vanskelig det er å skille kode 6 fra kode 7:

1. **Diskresjonskode (Arena, via oppfølgingsinformasjon)** — `Kandidat.kt`:

   ```kotlin
   private fun erIkkeKode6EllerKode7(oppfølgingsinformasjon: Oppfølgingsinformasjon): Boolean =
       (oppfølgingsinformasjon.diskresjonskode == null
               || oppfølgingsinformasjon.diskresjonskode !in listOf("6", "7"))
   ```

   Den rå diskresjonskoden («6» / «7») **kastes** her og kollapses til én boolean
   `erIkkeKode6eller7`. Denne lagres som kolonnen `er_ikke_kode6_eller_kode7` i
   synlighetsmotor-databasen (`Repository.kt`, `databaseMap`/`evalueringFraDB`). **Etter lagring er
   6 og 7 ikke lenger mulig å skille** — informasjonen finnes ikke i databasen.

2. **PDL-adressebeskyttelse (gradering)** — need-flyten for rekrutteringstreff
   (`SynlighetRekrutteringstreffLytter.kt`) henter et `adressebeskyttelse`-felt med graderingsstreng:
   `UKJENT`, `UGRADERT`, `FORTROLIG`, `STRENGT_FORTROLIG`, `STRENGT_FORTROLIG_UTLAND`.
   Her _kan_ man skille 6/19 fra 7. Men: graderingen brukes i dag kun til å sette boolean
   `harIkkeAdressebeskyttelse` (`adressebeskyttelse == "UKJENT" || == "UGRADERT"`), og selve strengen
   sendes **ikke** videre i need-svaret (`packet[synlighetRekrutteringstreffBehov] = mapOf("erSynlig" ..., "ferdigBeregnet" ...)`).

**Dette er kjernen i problemet:** Den nøyaktige diskresjonskoden 6 vs 7 fra Arena er allerede
borte etter at synlighetsmotor har lagret evalueringen. PDL-graderingen finnes i need-flyten, men
overlapper ikke nødvendigvis med Arena-diskresjonskoden (en person kan være kode 6 i Arena uten at
PDL-gradering er hentet, og omvendt).

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
- Statusaggregering for jobbsøkere: `RekrutteringstreffSokRepository.statusaggregering()` /
  `jobbsoker_sok_view` (teller `er_synlig`/`status`)

**frontend** (`rekrutteringsbistand-frontend/...`):

- `app/api/rekrutteringstreff/[...slug]/formidling/useFormidlinger.ts` — `FormidlingSchema`, mocks
- `app/rekrutteringstreff/[rekrutteringstreffId]/_ui/formidling/FormidlingRad.tsx` — visning av navn/fnr
- Opprett-modal (velg jobbsøker-steg) under `_ui/header/actions/`

---

## Alternativ 1 — Behandle kode 6 og 7 likt (begge = sperret)

**Idé:** Tolk _all_ adressebeskyttelse som synlighetsmotor allerede fanger (kode 6 **og** 7 +
PDL-gradering) som «sperret». Dette unngår å måtte skille 6 fra 7, og krever ingen endringer i den
delte plattformkomponenten utover å sende ett ekstra flagg.

### Datakilde

Synlighetsmotor vet allerede at noen er kode 6/7 eller har PDL-adressebeskyttelse, gjennom
`erIkkeKode6eller7` og `harIkkeAdressebeskyttelse`. Innfør et avledet flagg, f.eks.
`harAdressebeskyttelse = !erIkkeKode6eller7 || !harIkkeAdressebeskyttelse`.

### Endringer

**toi-synlighetsmotor:**

1. `Evaluering.kt`: legg til avledet `fun harAdressebeskyttelse(): Boolean` og ta med i `Synlighet`
   (event) og i need-svaret.
2. `SynlighetsgrunnlagLytter.kt`: `packet["synlighet"]` får feltet `sperret` (event-strøm).
3. `SynlighetRekrutteringstreffLytter.kt`: `packet[synlighetRekrutteringstreffBehov]` får `sperret`.

**rekrutteringstreff-api:** 4. **V11-migrasjon:** `ALTER TABLE jobbsoker ADD COLUMN sperret boolean NOT NULL DEFAULT false;` 5. `SynlighetsLytter` / `SynlighetsBehovLytter`: les `sperret` og send videre. 6. `JobbsøkerService` + `JobbsøkerRepository`: utvid `oppdaterSynlighetFraEvent/FraNeed` med
`sperret`-parameter; UPDATE setter `sperret = ?` ved siden av `er_synlig`. 7. `FormidlingRepository.hentMedWhere(...)`: null ut navn **og** fnr ved sperret:

```sql
CASE WHEN j.sperret THEN NULL WHEN j.er_synlig THEN j.fodselsnummer ELSE NULL END AS fodselsnummer,
CASE WHEN j.sperret THEN NULL ELSE j.fornavn END AS fornavn,
CASE WHEN j.sperret THEN NULL ELSE j.etternavn END AS etternavn,
j.sperret AS sperret
```

8. `FormidlingDto`: legg til `val sperret: Boolean`.
9. `FormidlingService.opprettFormidling(...)`: valider at ingen valgte personer er `sperret`;
   returner `409`/`422` med `feil` + `hint`.
10. Statusaggregering: tell `sperret` på samme måte som `er_synlig`/`status`.

**frontend:** 11. `FormidlingSchema` får `sperret: z.boolean()`. 12. `FormidlingRad.tsx`: når `sperret` → vis «Skjermet» (ingen navn, ingen fnr); ellers dagens logikk. 13. Opprett-modal: filtrer bort / deaktiver sperrede jobbsøkere i velg-steget med forklaring.

### Fordeler / ulemper

- ✅ Minst kodeendring i plattform — ingen ny diskresjonskode-kolonne i synlighetsmotor-db.
- ✅ Trenger ikke skille 6 fra 7 (informasjonen som mangler i db er irrelevant her).
- ❌ Kode 7 (`FORTROLIG`) blir behandlet strengere enn produkteier strengt tatt har bedt om
  (de ville ha kode 7 som «skjult», ikke «sperret»). Avklar om dette er akseptabelt.

---

## Alternativ 2 — Skille ut kun kode 6 og 19 (anbefalt mot avklart scope)

**Idé:** Kun `STRENGT_FORTROLIG` (6) og `STRENGT_FORTROLIG_UTLAND` (19) = sperret. Kode 7 og resten
faller inn under eksisterende `er_synlig = false` (skjuling), akkurat som manglende CV. Da trenger vi
**ikke** sperret-felt/-logikk før denne planen faktisk utføres — skjuling dekker kode 7 i dag.

### Hovedutfordring

Diskresjonskode 6 vs 7 fra Arena er **ikke tilgjengelig** etter lagring i synlighetsmotor (se
«Bakgrunn»). For å skille dem må synlighetsmotor begynne å ta vare på enten den rå diskresjonskoden
eller et avledet «streng»-flagg. Dette er det som gjør alternativ 2 dyrere.

### To undervarianter for datakilde

**2a — Bruk PDL-graderingen (minst plattformendring, men ufullstendig):**
Need-flyten (`SynlighetRekrutteringstreffLytter.kt`) har allerede graderingsstrengen. Sett
`sperret = adressebeskyttelse in {"STRENGT_FORTROLIG", "STRENGT_FORTROLIG_UTLAND"}` og send i
need-svaret. **Begrensning:** fanger ikke Arena-diskresjonskode 6 der PDL-gradering ikke er hentet,
og event-strømmen (`somSynlighet()`) har ikke graderingsstrengen — kun boolean
`harIkkeAdressebeskyttelse`. Krever derfor enten at man godtar at sperring kun gjelder PDL-graderte,
eller at event-flyten også beriges (se 2b).

**2b — Bevar diskresjonskode/streng-flagg gjennom synlighetsmotor (komplett, men størst endring):**

1. `Kandidat.kt`: ikke kast diskresjonskoden — avled `erStrengtFortrolig =
diskresjonskode in {"6"} || gradering in {STRENGT_FORTROLIG, STRENGT_FORTROLIG_UTLAND}`
   (kode 19 = strengt fortrolig utland; verifiser om Arena bruker «19» eller PDL-gradering for utland).
2. `Evaluering.kt`: nytt felt som bæres gjennom til `Synlighet`/need-svar.
3. `Repository.kt` + **ny Flyway-migrasjon i synlighetsmotor-db**: lagre flagget (ny kolonne, f.eks.
   `er_strengt_fortrolig`), oppdater `databaseMap` og `evalueringFraDB`.
4. `SynlighetsgrunnlagLytter.kt` (event) + `SynlighetRekrutteringstreffLytter.kt` (need): send `sperret`.

### Endringer i rekrutteringstreff-api og frontend

Identiske med alternativ 1, punkt 4–13 (V11-kolonne `sperret`, lyttere, UPDATE-metoder, liste-query
som nuller navn+fnr, `FormidlingDto.sperret`, blokkering i `opprettFormidling`, statusaggregering,
`FormidlingRad` «Skjermet», opprett-modal filtrering).

**Merk:** Hvis man velger 2a/2b og vil utsette sperret-feltet helt: kode 7 dekkes allerede av
`er_synlig = false` i dag, så ingen umiddelbar handling kreves for kode 7. Sperret-felt og
plattformendring innføres først når kode 6/19-sperring faktisk skal leveres.

### Fordeler / ulemper

- ✅ Treffer avklart scope nøyaktig (kun 6/19 sperres, 7 skjules).
- ✅ Kan utsettes — kode 7 håndteres riktig allerede uten ny kode.
- ❌ 2b krever endring i delt plattformkomponent **inkludert ny db-migrasjon** i synlighetsmotor,
  med stort nedslagsfelt og koordinering med synlighetsmotor-eier.
- ❌ 2a er enklere men ufullstendig (fanger ikke alle Arena-kode-6-tilfeller, og event-flyten mangler graderingen).

---

## Anbefaling

- Hvis produkteier kan akseptere at kode 7 også sperres: velg **Alternativ 1** (klart minst arbeid).
- Hvis skillet 6/19 vs 7 er et reelt krav: velg **Alternativ 2b** og koordiner db-/payload-endring
  med toi-synlighetsmotor-eier. **Alternativ 2a** kun hvis man bevisst godtar at sperring begrenses
  til PDL-graderte og at event-flyten ikke dekkes.

Uansett alternativ er endringene i rekrutteringstreff-api og frontend like (punkt 4–13). Det er kun
**datakilden for `sperret`-flagget** som skiller alternativene.

---

## Felles akseptansekriterier (uavhengig av alternativ)

1. En sperret jobbsøker kan **ikke** legges til i en ny formidling (opprett-endepunkt returnerer feil;
   opprett-modal viser dem ikke som valgbare).
2. En formidling lagt til **før** sperring vises fortsatt i formidlingslisten, men med **både navn og
   fødselsnummer anonymisert** og merket «Skjermet».
3. Skjult/inaktiv kandidat (kode 7, manglende CV osv.) oppfører seg som i dag: navn vises, fnr nulles,
   merket «Inaktiv kandidat».
4. Sperrede telles i statusaggregeringen på samme måte som synlige/slettede.

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

- Test at `sperret`/`er_strengt_fortrolig` settes korrekt for kode 6/19 (og ikke for kode 7 i
  alternativ 2), og at det sendes i både event (`SynlighetsgrunnlagLytter`) og need
  (`SynlighetRekrutteringstreffLytter`).

**frontend (Playwright `tests/rekrutteringstreff/formidlinger.spec.ts`):**

- Rad med `sperret` viser «Skjermet» (verken navn eller fnr).
- (Hvis modal-flyt med MSW) sperret kandidat er ikke valgbar i opprett-modal.
