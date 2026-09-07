# Plan: Flytte eiere og kontorer ut i egen tabell

**Status:** Klar til implementering — modellvalg besluttet (seksjon 6 og 7), én åpen avklaring (seksjon 8)
**Omfang:** Datamodell og migrering i `rekrutteringstreff-api`

**Mål:** Bevare sammenhengen mellom eier og kontor. I dag er `rekrutteringstreff.eiere text[]` og
`rekrutteringstreff.kontorer text[]` to uavhengige arrays — koblingen «eier X kom fra kontor Y» går tapt.

**Valgt semantikk (bekreftet med utvikler):** Kontor er *avledet* fra eiere. Når siste eier fra et kontor
fjernes, forsvinner kontoret fra treffet (og dermed kontorbasert tilgang). Derfor holder det med **én** ny
tabell.

---

## 1. Datamodell

```sql
CREATE TABLE rekrutteringstreff_eier (
    rekrutteringstreff_eier_id bigserial PRIMARY KEY,
    rekrutteringstreff_id      bigint NOT NULL REFERENCES rekrutteringstreff (rekrutteringstreff_id),
    nav_ident                  text   NOT NULL,
    eier_navn                  text,          -- visningsnavn, NULL for migrerte rader
    kontor_enhetid             text,          -- mål: NOT NULL, se seksjon 1
    lagt_til_tidspunkt         timestamptz NOT NULL DEFAULT now(),
    lagt_til_av                text,
    CONSTRAINT rekrutteringstreff_eier_unik UNIQUE (rekrutteringstreff_id, nav_ident)
);

CREATE INDEX idx_rekrutteringstreff_eier_treff  ON rekrutteringstreff_eier (rekrutteringstreff_id);
CREATE INDEX idx_rekrutteringstreff_eier_ident  ON rekrutteringstreff_eier (nav_ident);
CREATE INDEX idx_rekrutteringstreff_eier_kontor ON rekrutteringstreff_eier (kontor_enhetid);
```

**Arkitektoniske valg og tradeoffs**

| Valg | Hvorfor | Tradeoff |
| --- | --- | --- |
| Én tabell, kontor som kolonne | Kontor er avledet av eierskap — ingen selvstendig livssyklus | Kan ikke ha kontor uten eier (f.eks. kontor lagt til manuelt) |
| `kontor_enhetid` — mål: `NOT NULL` | `EierController` avviser nå manglende kontor. Av backfillens 70 hull kan 50 utledes entydig; 20 må avklares — se seksjon 1 | Krever at de 20 løses før constrainten kan settes |
| Oppdaterer ikke eksisterende eierrader | `leggTilEierMedKontor` returnerer tidlig hvis brukeren alt er eier | Kontor-/navnendringer fanges ikke opp — vurder endring i fase 2 |
| `eier_navn` nullable, denormalisert | Ingen server-side navIdent→navn-oppslag finnes i appen. Migrerte rader har ingen navnekilde | Navn kan bli utdatert; må tåle NULL i visning |
| Unik `(treff, nav_ident)` | Én eier kan bare være eier én gang | Eier som bytter kontor må oppdateres (UPDATE, ikke ny rad) |
| Ingen `ON DELETE CASCADE` | Treff slettes aldri fysisk (status `SLETTET`) | — |
| Hard delete av eierrader (ingen `slettet_tidspunkt`) | Historikken ligger i `rekrutteringstreff_hendelse` (`EIER_FJERNET`); soft delete ville lagt en ekstra betingelse å glemme i tilgangskritisk aggregering | Krever at hendelsesloggen er komplett — se `KONTOR_FJERNET` |
| Ingen `kontor_navn`-kolonne | Kontornavn slås opp fra JSON-fil ut fra `kontor_enhetid`; å lagre det ville duplisert en kilde som allerede finnes | Oppslaget må gjøres ved visning |

🔴 **Rød sone:** `kontor_enhetid` styrer tilgangskontroll (`harTilgangViaTreffkontor` i `EierService`).
Feil i migrering eller aggregering gir enten tapt tilgang eller *for bred* tilgang. Denne delen bør
gjennomgås manuelt og dekkes av tester før produksjonssetting.

### Når er `kontor_enhetid` NULL?

Målet er `NOT NULL`. Nye rader oppfyller det allerede — `EierController.leggTilMeg` avviser manglende
kontor:

```kotlin
val kontorId = ctx.authenticatedUser().extractKontorId()
if (kontorId == null) {
    throw IllegalStateException("Innlogget bruker har ikke kontor-tilknytning, ...")
}
```

⚠️ **Backfillen er blokkeringen.** 70 av 269 eierrader får ingen kontor fra hendelsesloggen (se seksjon 6).
En `NOT NULL`-constraint ville fått migreringen til å feile.

**Slutningen som kan løse det:** de 70 oppstår fordi `leggTilKontor` returnerte `false` — kontoret eieren
brakte med seg lå allerede på treffet, og derfor ble ingen `KONTOR_LAGT_TIL` skrevet. Eierens kontor er
altså garantert ett av treffets egne kontorer. Har treffet nøyaktig ett kontor, er tilordningen entydig og
kan gjøres deterministisk:

```sql
UPDATE rekrutteringstreff_eier e
SET kontor_enhetid = k.enhetid
FROM rekrutteringstreff rt
CROSS JOIN LATERAL unnest(array_remove(rt.kontorer, NULL)) AS k(enhetid)
WHERE e.rekrutteringstreff_id = rt.rekrutteringstreff_id
  AND e.kontor_enhetid IS NULL
  AND array_length(array_remove(rt.kontorer, NULL), 1) = 1;
```

**Spørring 6 (kjørt mot prod):**

| Måltall | Verdi |
| --- | --- |
| `uten_kontor_totalt` | 70 |
| `uten_kontor_men_ett_entydig_kontor` | 50 |
| `uten_kontor_og_flere_kontorer` | 20 |
| `uten_kontor_og_treffet_har_ingen` | 0 |

50 av 70 kan altså fylles deterministisk. Ingen ligger på treff helt uten kontor, så alle 70 har *et* svar
— for 20 av dem er det bare ikke entydig hvilket av treffets kontorer det er.

### De 20 tvetydige — må avgjøres

| Alternativ | Vurdering |
| --- | --- |
| **Slå opp riktig kontor** (NOM, Modia eller spørre personene) og legg inn en eksplisitt mapping i migreringen | Eneste som gir korrekte data. 20 rader er håndterbart på dette volumet. **Anbefalt.** |
| **Gjett** — f.eks. treffets `opprettet_av_kontor_enhetid` | Utvider ikke tilgangen umiddelbart, siden kontoret allerede ligger på treffet. Men gir *forsinket* feil: fjernes den ekte eieren av kontoret senere, beholder kontoret tilgang via den feilattribuerte raden. Ikke gratis. |
| **Behold nullable** | Trygt, men beholder `FILTER (WHERE kontor_enhetid IS NOT NULL)` som en betingelse å glemme i rød sone — og de 20 blir aldri fylt, siden hullene ikke lukker seg selv. |

Bruk **spørring 7** for radene som må avklares, og **spørring 8** for én rad per ident — samme person kan
eie flere treff, så kontoret trenger bare slås opp én gang per person. Spørring 8 gir også
`kjent_kontor_fra_andre_treff`: står personen med et entydig kontor på et *annet* treff der koblingen
finnes, er det en sterk indikasjon på riktig svar.

**Når de 20 er avklart**, legges mappingen inn eksplisitt i `V14`:

```sql
-- 1. Fyll de 50 entydige
UPDATE rekrutteringstreff_eier e
SET kontor_enhetid = k.enhetid
FROM rekrutteringstreff rt
CROSS JOIN LATERAL unnest(array_remove(rt.kontorer, NULL)) AS k(enhetid)
WHERE e.rekrutteringstreff_id = rt.rekrutteringstreff_id
  AND e.kontor_enhetid IS NULL
  AND array_length(array_remove(rt.kontorer, NULL), 1) = 1;

-- 2. Fyll de 20 avklarte fra eksplisitt mapping
UPDATE rekrutteringstreff_eier e
SET kontor_enhetid = m.kontor_enhetid
FROM (VALUES
    ('A123456', '0315'),
    ('B654321', '1201')
    -- ... resten av de avklarte identene
) AS m(nav_ident, kontor_enhetid)
WHERE e.nav_ident = m.nav_ident
  AND e.kontor_enhetid IS NULL;

-- 3. Verifiser at ingen hull gjenstår før constrainten settes.
--    Feiler migreringen her, er mappingen ufullstendig.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM rekrutteringstreff_eier WHERE kontor_enhetid IS NULL) THEN
        RAISE EXCEPTION 'Eierrader uten kontor gjenstår — mappingen i steg 2 er ufullstendig';
    END IF;
END $$;

ALTER TABLE rekrutteringstreff_eier ALTER COLUMN kontor_enhetid SET NOT NULL;
```

Steg 3 er med hensikt en hard stopp: uten den ville `SET NOT NULL` feilet med en generisk melding som ikke
sier hva som mangler.

Gevinsten: `FILTER (WHERE kontor_enhetid IS NOT NULL)` i fase 3 faller bort — én betingelse mindre å glemme
i tilgangskritisk kode.

---

## 2. Migreringsstrategi: expand → migrate → contract

Arrayene brukes av `rekrutteringstreff_sok_view`, som igjen brukes av den fødererte BigQuery-spørringen
(`federated-queries/rekrutteringstreff-per-kontor-aggregert.sql`). Derfor **ikke** big-bang.

### Fase 1 — Expand (`V14__rekrutteringstreff_eier.sql`)

1. Opprett tabellen (over).
2. Backfill fra arrayene:

```sql
INSERT INTO rekrutteringstreff_eier (rekrutteringstreff_id, nav_ident, kontor_enhetid, lagt_til_tidspunkt, lagt_til_av)
SELECT
    rt.rekrutteringstreff_id,
    e.nav_ident,
    coalesce(
        -- 1. Kontoret eieren selv brakte inn, hentet fra hendelsesloggen.
        --    EierService skriver KONTOR_LAGT_TIL med aktøridentifikasjon = eierens
        --    navIdent og subjekt_id = kontorets enhetId.
        (SELECT h.subjekt_id
         FROM rekrutteringstreff_hendelse h
         WHERE h.rekrutteringstreff_id = rt.rekrutteringstreff_id
           AND h.hendelsestype = 'KONTOR_LAGT_TIL'
           AND h.aktøridentifikasjon = e.nav_ident
           AND h.subjekt_id IS NOT NULL
         ORDER BY h.tidspunkt DESC
         LIMIT 1),
        -- 2. Oppretteren: kontoret ligger direkte på treffraden.
        CASE WHEN e.nav_ident = rt.opprettet_av_person_navident
             THEN rt.opprettet_av_kontor_enhetid END
    ) AS kontor_enhetid,
    coalesce(
        (SELECT min(h.tidspunkt)
         FROM rekrutteringstreff_hendelse h
         WHERE h.rekrutteringstreff_id = rt.rekrutteringstreff_id
           AND h.hendelsestype = 'EIER_LAGT_TIL'
           AND h.subjekt_id = e.nav_ident),
        rt.opprettet_av_tidspunkt
    ) AS lagt_til_tidspunkt,
    'migrering-V14'
FROM rekrutteringstreff rt
CROSS JOIN LATERAL unnest(rt.eiere) AS e(nav_ident)
WHERE e.nav_ident IS NOT NULL
ON CONFLICT DO NOTHING;
```

**Hvorfor hendelsesloggen er hovedkilden:** `EierService.leggTilEierMedKontor` skriver `EIER_LAGT_TIL` og
`KONTOR_LAGT_TIL` i samme transaksjon, begge med `aktøridentifikasjon` satt til den innloggede brukerens
navIdent (endepunktet er `PUT /eiere/meg` — eier og aktør er samme person). `subjekt_id` og `subjekt_navn`
ble lagt til i `V2__kontorer.sql`, samme migrering som innførte `kontorer`-kolonnen, så det finnes ingen
`KONTOR_LAGT_TIL`-hendelser uten `subjekt_id`.

⚠️ **Restusikkerhet — to tilfeller gir fortsatt `kontor_enhetid = NULL`:**

1. **Eier nummer to fra samme kontor.** `leggTilKontor` returnerer `false` når kontoret allerede finnes på
   treffet, og `KONTOR_LAGT_TIL` skrives kun `if (nyttKontor)`. Eier B fra kontor X får derfor ingen
   hendelse hvis eier A alt har brakt X inn. Kontoret går *ikke* tapt (A holder det i live), men koblingen
   B→X mangler.
2. **Eiere lagt til før V2.** Ingen `KONTOR_LAGT_TIL`-hendelser eksisterte da.

Begge gir NULL, ikke feil kontor.

⚠️ **Hullene lukker seg ikke av seg selv.** `EierService.leggTilEierMedKontor` returnerer tidlig hvis
brukeren allerede er eier (`if (eiere.contains(navIdent)) return`), så et nytt `PUT /eiere/meg` oppdaterer
verken kontor eller navn for en eksisterende eier. Manglende verdier må derfor fylles i migreringen — se
seksjon 1.

✅ **Målt mot prod: ingen kontorer går tapt.** `kontorkoblinger_som_forsvinner = 0` — hvert kontor i dagens
`kontorer[]` dekkes av minst én gjenværende eier. De 70 eierradene som får `NULL` er nettopp tilfelle 1 over:
andregangs-eiere fra et kontor en annen eier allerede har brakt inn. Se måleresultater i seksjon 6.

⚠️ **Kjør spørring 2 på nytt rett før migreringen.** Tallene er et øyeblikksbilde. Fjernes en eier i
mellomtiden, kan kontoret vedkommende brakte inn bli foreldreløst. Backfill-logikken er riktig uansett, men
`kontorkoblinger_som_forsvinner` bør bekreftes å være 0 også på migreringstidspunktet.

3. Behold `eiere`/`kontorer`-kolonnene urørt i denne fasen.

### Fase 2 — Dual write

Skriv til både arrayene og den nye tabellen i samme transaksjon. Berørte steder:

- `RekrutteringstreffRepository.opprett` — sett inn eierrad for oppretter med kontor
- `RekrutteringstreffRepository.leggTilKontor` — erstattes gradvis av oppdatering på eierraden
- `EierRepository.leggTil` / `slett`
- `EierService.leggTilEierMedKontor` / `slettEier`

Konsistenssjekk: kjør en jobb/spørring som verifiserer at `eiere`-arrayet og tabellen er like for alle treff.

### Fase 3 — Bytt lesing

- `EierRepository.hent` → `SELECT nav_ident, kontor_enhetid FROM rekrutteringstreff_eier ...`
  (behold `FOR UPDATE`-låsing; nå låser man eierradene, ikke treffraden)
- `RekrutteringstreffRepository.tilRekrutteringstreff` → hent eiere/kontorer via join eller `array_agg`
- `R__rekrutteringstreff_sok_view.sql` → erstatt `rt.eiere` / `rt.kontorer` med subqueries:
  ```sql
  (SELECT array_agg(DISTINCT e.nav_ident) FROM rekrutteringstreff_eier e
    WHERE e.rekrutteringstreff_id = rt.rekrutteringstreff_id) AS eiere,
  (SELECT array_agg(DISTINCT e.kontor_enhetid) FILTER (WHERE e.kontor_enhetid IS NOT NULL)
     FROM rekrutteringstreff_eier e
    WHERE e.rekrutteringstreff_id = rt.rekrutteringstreff_id) AS kontorer
  ```
  ⚠️ `array_agg` gir NULL (ikke `{}`) ved ingen rader — pakk inn i `COALESCE(..., '{}'::text[])`, ellers
  brekker `? = ANY(eiere)`-filtrene i `RekrutteringstreffSokRepository`.
- Verifiser ytelse: `RekrutteringstreffSokYtelsestest` finnes allerede. Med dagens datavolum (269 eierrader,
  se måleresultater i seksjon 6) er subqueries uproblematisk — materialisert view er ikke nødvendig.

### Fase 4 — Contract (`V15__dropp_eiere_kontorer_arrays.sql`)

- `ALTER TABLE rekrutteringstreff DROP COLUMN eiere, DROP COLUMN kontorer;`
- Kjøres **etter** at fase 3 er verifisert i prod (egen deploy, ikke samme release).
- Oppdater `federated-queries/rekrutteringstreff-per-kontor-aggregert.sql` — den bruker i dag
  `rt.opprettet_av_kontor_enhetid`; med ny modell kan den gruppere per kontor via eiertabellen og
  faktisk telle treff per *deltakende* kontor, ikke bare oppretterens.

---

## 3. Ny funksjonalitet som modellen muliggjør

Dette er gevinsten — vurder om noe skal med i første leveranse:

- `GET /api/rekrutteringstreff/{id}/eiere` kan returnere `[{navIdent, navn, kontorEnhetId}]` i stedet for
  bare identer — kontornavn slås opp fra JSON-fila ut fra `kontorEnhetId`. **Breaking change** for frontend
  — vurder nytt endepunkt eller versjonert respons.
- `KONTOR_FJERNET`-hendelse i `RekrutteringstreffHendelsestype` (`typer.kt`), utstedes når siste eier fra et
  kontor fjernes. Mangler i dag — se seksjon 7, dette er nå et *krav* før fase 4, ikke en mulighet.
- Statistikk per kontor basert på faktisk deltakelse.

---

## 4. Testdekning som må på plass

Eksisterende tester som må oppdateres: `EierRepositoryTest`, `RekrutteringstreffEierTest`,
`RekrutteringstreffEierAutorisasjonsTest`, `RekrutteringstreffSokRepositoryTest`,
`RekrutteringstreffSokKomponenttest`, `TestDatabase` (både i `rekrutteringstreff-api` og
`rekrutteringstreff-minside-api` — sistnevnte leser `kontorer` direkte i sin `TestDatabase.kt`).

Nye tester:

- Backfill-migrering: treff der eier har `KONTOR_LAGT_TIL`-hendelse → kontor gjenskapt fra hendelsen
- Backfill-migrering: eier uten hendelse som *er* oppretter → kontor fra `opprettet_av_kontor_enhetid`
- Backfill-migrering: eier uten hendelse som *ikke* er oppretter → `kontor_enhetid IS NULL`
- Backfill-migrering: kontor i `kontorer[]` uten gjenværende eier → forsvinner fra aggregatet
- Eier fjernet og lagt til igjen → fungerer (hard delete, ingen unikhetskonflikt)
- Siste eier fra kontor fjernet → `KONTOR_FJERNET`-hendelse utstedes med kontorets enhetId
- Nest siste eier fra kontor fjernet → *ingen* `KONTOR_FJERNET` (kontoret består)
- Samme eier lagt til to ganger → én rad, kontor oppdatert (idempotens)
- Siste eier fra kontor fjernet → kontoret forsvinner fra `kontorer`-aggregatet
- 🔴 Tilgang: `harTilgangViaTreffkontor` gir *ikke* tilgang etter at kontorets siste eier er fjernet
- 🔴 Tilgang: eier med `kontor_enhetid = NULL` gir ingen kontorbasert tilgang (kun relevant hvis kolonnen forblir nullable)
- `PUT /eiere/meg` uten kontor-tilknytning → avvises, ingen eierrad opprettes
- Eier uten kontor (`NULL`) påvirker ikke kontorfilter i søk

---

## 5. Rekkefølge og risiko

| Steg | Deploy | Risiko |
| --- | --- | --- |
| Fase 1 (tabell + backfill) | Egen | Lav — ingen lesere ennå |
| Fase 2 (dual write) | Egen | Lav — arrayene er fortsatt fasit |
| Fase 3 (bytt lesing + view) | Egen | 🔴 Høy — tilgangsstyring og søk |
| Fase 4 (drop kolonner) | Egen, etter verifisering | Middels — irreversibelt |

**Før migrering kjøres:** kjør spørring 2 på nytt og bekreft at `kontorkoblinger_som_forsvinner` fortsatt er
0. Målingen er et øyeblikksbilde; fjernes en eier i mellomtiden, kan et kontor bli foreldreløst.

**Krav om `KONTOR_FJERNET`:** hendelsen må innføres senest i fase 3 — se seksjon 7.

---

## 6. Besluttet: ren modell (`nav_ident NOT NULL`)

Beslutningen er tatt på grunnlag av måling mot prod: **ingen kontorer går tapt ved migrering**. Planen over
gjelder som beskrevet — `nav_ident NOT NULL`, kontor avledet av eierskap.

### Måleresultater (kjørt mot prod)

**Spørring 1 — gjenskaping av eier→kontor:**

| Måltall | Verdi |
| --- | --- |
| `eierrader_totalt` | 269 |
| `med_kontor` | 199 |
| `uten_kontor` | 70 |
| `prosent_med_kontor` | 74,0 % |

**Spørring 2 — kontorer som forsvinner:**

| Måltall | Verdi |
| --- | --- |
| `treff_totalt` | 173 |
| `treff_som_mister_alle_kontorer` | **0** |
| `treff_som_mister_minst_ett_kontor` | **0** |
| `kontorkoblinger_som_forsvinner` | **0** |

**Tolkning:** de to tallene henger sammen. De 70 eierradene uten kontor er andregangs-eiere fra et kontor en
annen eier allerede har brakt inn — raden får `NULL`, men kontoret består. Hendelsesbasert backfill
gjenskaper derfor hele kontormengden, og ingen mister kontorbasert tilgang.

74 % er altså ikke et tap på 26 %; det er andelen *eierrader* med presis kontorattribusjon. Dekningen på
kontornivå er 100 %.

### Følger for planen

- **Ingen nullable `nav_ident`.** Semantikken «kontor forsvinner når siste eier fjernes» holder fullt ut.
- **Ingen manuell opprydding eller varsling.** Ingen berørte kontorer.
- **Ytelsespunktet i fase 3 er strøket.** 269 eierrader gjør `array_agg`-subqueries uproblematisk.
- **Restansen på 70 rader må fylles i migreringen.** 50 kan utledes entydig, 20 krever avklaring — se
  seksjon 1. De lukker seg *ikke* av seg selv, siden `leggTilEierMedKontor` returnerer tidlig for
  eksisterende eiere.

### Vurderte og forkastede alternativer

**Nullable `nav_ident`** (eierløse kontorrader, `UNIQUE NULLS NOT DISTINCT`, PG15+). Skulle sikret null
tilgangstap, men er unødvendig når tapet allerede er målt til null. Kostnaden ville vært at
`EierRepository.hent` og hver framtidig spørring måtte håndtere eierløse rader permanent.

**«Behold koblingen kun ved én eier og ett kontor.»** Forenkler i praksis lite: i nesten alle treff med én
eier og ett kontor *er* eieren oppretteren, og da løser `opprettet_av_kontor_enhetid` det allerede.
Heuristikken tilfører kun noe når den ene eieren ikke er oppretteren — og der er den usikker, siden kontoret
like gjerne kan tilhøre en fjernet oppretter. Med `kontorkoblinger_som_forsvinner = 0` er den overflødig.

Merk at en feilgjetting her *ikke* utvider tilgangen: kontoret ligger allerede i `kontorer[]` og gir tilgang
i dag. Feil attribusjon påvirker bare når kontoret senere ryddes bort.

---

## 7. Besluttet: hard delete, ingen `slettet_tidspunkt`

Når en eier fjernes, slettes raden fysisk. Ingen `slettet_tidspunkt`-kolonne.

### Begrunnelse

**Historikken finnes allerede.** `rekrutteringstreff_hendelse` registrerer `EIER_FJERNET` med
`subjekt_id = navIdent` og `tidspunkt`. Et `slettet_tidspunkt` ville duplisert dette i en tabell som styrer
tilgangskontroll.

**Soft delete er en sikkerhetsfelle her.** Kontoraggregatet i fase 3 måtte da hatt *både*
`FILTER (WHERE kontor_enhetid IS NOT NULL)` og `WHERE slettet_tidspunkt IS NULL`. Glemmes den andre,
beholder fjernede eieres kontorer tilgangen — nøyaktig feilen `harTilgangViaTreffkontor` ikke tåler. Rød
sone bør ha færrest mulig betingelser å glemme.

**`UNIQUE (rekrutteringstreff_id, nav_ident)` ville brukket.** Med bevarte rader kan en tidligere eier ikke
legges til igjen; constrainten måtte blitt et partial index `WHERE slettet_tidspunkt IS NULL`.

**Konsistent med kodebasen.** `jobbsoker` og `rekrutteringstreff` er *entiteter* og soft-slettes via
`status = 'SLETTET'`. Eierskap er en *relasjon*, og fjernes i dag hardt med `array_remove`. Hard delete
viderefører eksisterende semantikk.

### Konsekvens: `KONTOR_FJERNET` blir et krav

I dag skrives `EIER_FJERNET` ved fjerning, men ingenting registrerer at kontoret forlot treffet. Så lenge
`kontorer[]` finnes, kan tilstanden leses der. Etter fase 4 er kolonnen borte, og med hard delete finnes da
*ingen* kilde til når et kontor mistet tilknytningen.

`KONTOR_FJERNET` må derfor innføres senest i fase 3, utstedt fra `EierService.slettEier` når den fjernede
eieren var den siste fra sitt kontor. Hendelsestypen legges til i `RekrutteringstreffHendelsestype`
(`typer.kt`) med `subjektId`/`subjektNavn` satt til kontorets enhetId, i tråd med `KONTOR_LAGT_TIL`.

### Merk om dagens slettemulighet

`DELETE /api/rekrutteringstreff/{id}/eiere/{navIdent}` godtar innlogget brukers egen navIdent, så en eier
kan fjerne seg selv i dag — eneste sperre er at siste eier ikke kan fjernes (`EierService.slettEier`).

---

## 8. `eier_navn` — kilde må avklares

Kolonnen `eier_navn` lagrer visningsnavnet til eieren, slik at `GET /eiere` kan returnere navn uten oppslag
per forespørsel.

**Problemet:** appen har ingen server-side navIdent→navn-oppslag. Det finnes ingen NOM- eller
Graph-integrasjon, og ingen kode leser et `name`-claim fra tokenet i dag.

Dagens navnekilder i kodebasen henter alle navnet fra *utsiden*:

| Felt | Kilde |
| --- | --- |
| `jobbsoker.veileder_navn` | `KandidatsøkKlient` — gjelder jobbsøkerens veileder, ikke eiere |
| `formidling.opprettet_av_veileder_navn` | request-DTO (`opprettFormidling.opprettetAvNavn`) |
| `innlegg.opprettet_av_person_navn` | request-DTO |
| `rekrutteringstreff_hendelse.subjekt_navn` for `EIER_LAGT_TIL` | plassholder — settes til navIdent, ikke et reelt navn |

### Alternativer

**(a) `name`-claim fra tokenet.** Azure AD-tokens inneholder normalt et `name`-claim. Krever bare en
`extractNavn()` i `AuthenticatedUser`, ingen ny integrasjon. Passer godt fordi `PUT /eiere/meg` er
selvbetjening — brukeren som legges til *er* den innloggede.
⚠️ Må verifiseres mot et faktisk token i dev før det velges; ingen kode i repoet leser claimet i dag.

**(b) Frontend sender navnet.** Følger mønsteret fra `innlegg` og `formidling`. Men `PUT /eiere/meg` har i
dag ingen request-body, så API-kontrakten må utvides — og klientoppgitte visningsnavn er en svakere kilde.

**(c) Nytt oppslag mot NOM/Graph.** Mest korrekt og gir også navn ved backfill, men er en ny integrasjon
med tilhørende scope, feilhåndtering og driftsansvar.

**Anbefaling:** (a), med (c) som senere forbedring hvis navn trengs for historiske rader.

### Backfill

Ingen av alternativene gir navn for de 269 eksisterende radene — `eier_navn` blir `NULL`.

⚠️ Navnet fylles **ikke** inn av seg selv senere: `leggTilEierMedKontor` returnerer tidlig for eksisterende
eiere, så et nytt `PUT /eiere/meg` gjør ingenting. Skal historiske rader få navn, må enten migreringen
fylle dem (se delvis backfill under) eller `leggTilEierMedKontor` endres til å oppdatere navn og kontor på
eksisterende rader — se «Følger for øvrig».

Delvis backfill er mulig fra `innlegg`, som har både navident og navn for de som har skrevet innlegg:

```sql
UPDATE rekrutteringstreff_eier e
SET eier_navn = i.opprettet_av_person_navn
FROM innlegg i
WHERE i.opprettet_av_person_navident = e.nav_ident
  AND i.opprettet_av_person_navn IS NOT NULL
  AND e.eier_navn IS NULL;
```

Vurder om det er verdt kompleksiteten — dekningen er trolig lav.

### Følger for øvrig

- `Eier`-klassen (`eier/Eier.kt`) må utvides fra `Eier(navIdent)`. `tilJson()` og `tilNavIdenter()` brukes
  av `EierController` og `EierService`, så endringen berører responsformatet — se punktet om breaking
  change i seksjon 3.
- `subjektNavn` for `EIER_LAGT_TIL`/`EIER_FJERNET` kan endelig settes til et reelt navn i stedet for
  navIdent-plassholderen.
