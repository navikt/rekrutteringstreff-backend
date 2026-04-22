# Typeahead for samlede kvalifikasjoner og personlige egenskaper

## Bakgrunn

Arbeidsgiver beskriver sine behov i to felt:

- **Samlede kvalifikasjoner** (kombinert): yrkestittel, kompetanse, autorisasjon, fagdokumentasjon og førerkort.
- **Personlige egenskaper**: softskills (Janzz `KonseptType.SOFTSKILL`).

Begge realiseres i `pam-ontologi` som materialiserte typeahead-tabeller, helt på linje med eksisterende `typeahead_*`-tabeller.

### Eksisterende tabeller (uendret)

| Tabell                       | KonseptType | Filtrering                              |
| ---------------------------- | ----------- | --------------------------------------- |
| `typeahead_stilling`         | `S`         | `length(styrk08ssb) > 3`                |
| `typeahead_kompetanse`       | `K`         | Ingen                                   |
| `typeahead_autorisasjon`     | `A`         | Ingen                                   |
| `typeahead_godkjenninger`    | `K`         | Etterkommere av cid 567151              |
| `typeahead_fagdokumentasjon` | `U`         | Etterkommere av cid 534435/11777/407667 |
| `typeahead_geografi`         | `LO`        | Bruker `no_description` som kode        |

Alle har skjema `(id integer pk, konsept_id, verdi, verdi_lc, styrk08ssb, styrk08ssb_label, isco08, esco, esco_label, undertype)`, btree-indekser på `konsept_id` og `verdi_lc` og GIN trigram på `verdi_lc`.

## Valgt løsning

To nye tabeller med **samme skjema og samme indeksering** som de eksisterende, populert under Janzz-importen i samme staging-flyt.

### `typeahead_personlige_egenskaper`

Identisk skjema med `typeahead_kompetanse`. Populeres direkte fra `tmp_konsept` + `tmp_term` via samme `slettOgIndekserTypeahead(...)`-helper som de andre kategoriene, kalt med `KonseptType.SOFTSKILL`. Eget endepunkt med samme søkemønster som `/kompetanse`.

### `typeahead_samlede_kvalifikasjoner`

Skjemaet er identisk med de andre `typeahead_*`-tabellene, **pluss** en `kategori varchar(30) not null`-kolonne. Primærnøkkel `id integer` (term-id, samme som i de andre tabellene). Btree-indekser på `konsept_id`, `verdi_lc`, `kategori`, samt GIN trigram på `verdi_lc`.

Populeres ved Janzz-import som **etterkommer** av de allerede genererte `tmp_typeahead_*`-tabellene:

1. `YRKESTITTEL` ← `tmp_typeahead_stilling`
2. `KOMPETANSE` ← `tmp_typeahead_kompetanse`.
3. `AUTORISASJON` ← `tmp_typeahead_autorisasjon`
4. `FAGDOKUMENTASJON` ← `tmp_typeahead_fagdokumentasjon`
5. `FORERKORT` ← **hardkodet liste** (speiler `gyldigeFørerkort` i CV-prosjektet). Førerkort hentes ikke fra Janzz fordi listen er stabil, kontrollert av Statens vegvesen-kategoriene og Janzz-data har vist seg å være upresise/inkonsistente. Hardkodede rader bruker syntetiske negative `id`-er (-1 .. -19) for å unngå kollisjon med term-id-er fra Janzz, og `konsept_id = NULL`.

Hver insert har `where not exists (select 1 from <prod-tabell> x where x.id = kilde.id)` for å eliminere duplikater. Konseptene under `typeahead_godkjenninger` utelates fordi godkjenninger er en delmengde av kompetanser i Janzz og ville duplisert KOMPETANSE-radene.

Vi bruker `FORERKORT` (uten æøå) som kategori-verdi i database, enum og JSON for å unngå encoding-fallgruver.

## API

To endepunkter, begge returnerer `ResponseEntity<List<...>>` på linje med eksisterende typeahead-endepunkter. Ingen problem details, ingen `Any`-respons.

### `GET /rest/typeahead/samlede_kvalifikasjoner?q=...`

Standard typeahead-mønster. Min 2 tegn → returnerer `List<SamletKvalifikasjonRespons>` filtrert via `verdi_lc like '%q%'`. Færre enn 2 tegn → `200 OK` med tom liste, **ikke** `400`. Bruker samme rangering (eksakt → prefiks → midt-i-ord) som `/kompetanse` osv.

### `GET /rest/typeahead/personlige_egenskaper?q=...`

Standard typeahead-mønster. Min 2 tegn → returnerer resultater. Færre enn 2 tegn → `200 OK` med tom liste, **ikke** `400`. Bruker eksisterende `finnTypeahead(...)`-metode i `JanzzRepository`, som støtter midten-av-ord-treff via `verdi_lc like '%q%'`.

### Respons-DTO

```kotlin
enum class SamletKvalifikasjonKategori {
    YRKESTITTEL, KOMPETANSE, AUTORISASJON, FAGDOKUMENTASJON, FORERKORT
}

data class SamletKvalifikasjonRespons(
    val konseptId: Long?,
    val styrk08: String,
    val styrk08Label: String,
    val esco: String,
    val escoLabel: String,
    val label: String,
    val undertype: String,
    val kategori: SamletKvalifikasjonKategori
)
```

`konseptId` er nullable fordi de hardkodede førerkortene ikke har et Janzz-konsept.

`/personlige_egenskaper` returnerer eksisterende `Typeahead`-DTO (samme som `/kompetanse`).

## Implementering

1. **Flyway** — `V10__typeahead_samlede_kvalifikasjoner_og_personlige_egenskaper.sql`: oppretter `typeahead_samlede_kvalifikasjoner` (+ `tmp_*`) og `typeahead_personlige_egenskaper` (+ `tmp_*`) med samme skjema og indeksering som øvrige typeahead-tabeller.
2. **`JanzzRepository`**:
   - `slettOgIndekserTypeaheadPersonligeEgenskaper(prefix)` — delegerer til eksisterende `slettOgIndekserTypeahead(...)` med `KonseptType.SOFTSKILL`.
   - `slettOgIndekserTypeaheadSamledeKvalifikasjoner(prefix)` — kjører de fem `INSERT ... WHERE NOT EXISTS`-stegene over.
   - `overforTypeaheadSamledeKvalifikasjoner()` — variant av `overforTypeahead(...)` som inkluderer `kategori`.
   - `finnSamledeKvalifikasjoner(oppslagsord)` — speiler `finnTypeahead(...)` mot `typeahead_samlede_kvalifikasjoner` og inkluderer `kategori`-kolonnen.
   - `finnTypeaheadForPersonligeEgenskaper(oppslagsord)` — gjenbruker `finnTypeahead("typeahead_personlige_egenskaper", ...)`.
3. **`InnlastingService`**:
   - `lastInnTypeahead()` — kall `slettOgIndekserTypeaheadPersonligeEgenskaper("tmp_")` og deretter `slettOgIndekserTypeaheadSamledeKvalifikasjoner("tmp_")` (sistnevnte må kjøre **etter** alle andre typeahead-populeringer).
   - `overforNedlastetOntologi()` — truncate prod-tabellene og kall `overforTypeahead("typeahead_personlige_egenskaper")` + `overforTypeaheadSamledeKvalifikasjoner()`.
   - `trunkerArbeidstabeller()` — droppe `tmp_typeahead_personlige_egenskaper` og `tmp_typeahead_samlede_kvalifikasjoner`.
4. **`TypeaheadService`**:
   - `finnSamledeKvalifikasjoner(q)` — mapper repository-resultatet til `SamletKvalifikasjonRespons`.
   - `finnTypeaheadForPersonligeEgenskaper(q)` — speiler `finnTypeaheadForKompetanse`.
5. **`TypeaheadController`**:
   - `GET /rest/typeahead/samlede_kvalifikasjoner` (samme mønster som `/kompetanse`, 200+tom liste under 2 tegn).
   - `GET /rest/typeahead/personlige_egenskaper` (samme mønster som `/kompetanse`, 200+tom liste under 2 tegn).

## Testing

`JanzzRepositorySamledeKvalifikasjonerTest` (Testcontainers, samme oppsett som `rekrutteringstreff-api`):

- Populering fra de fire kategoriene gir riktig kategori per rad; godkjenninger gir ikke duplikater.
- Førerkort er naturlig utelatt fra de fire ontologi-kildene (autorisasjoner i Janzz har egne (e)-koder og inneholder ikke førerkort), så ingen ekstra filtrering er nødvendig — den hardkodede FORERKORT-listen kommer i tillegg uten risiko for duplikater.
- Den hardkodede FORERKORT-listen (19 oppføringer) populeres uten ontologi-input og har `konsept_id = null`.
- `finnSamledeKvalifikasjoner(q)` filtrerer på `verdi_lc` som de andre typeahead-metodene.
- Personlige egenskaper populeres kun for `type='SS'` med samme filtrering som de andre typeaheadene.
- `finnTypeaheadForPersonligeEgenskaper` treffer midt i ord, er case-insensitivt og håndterer norske tegn.
- Softskills lekker ikke inn i samlede kvalifikasjoner.

## Kjente sideobservasjoner (ikke blokkerende)

`slettOverflodingeGodkjenninger`, `slettOverflodigeFagdokumentasjoner`, `settUndertyperForTypeahead` og `settUndertyperForKonsepter` i `JanzzRepository` leser fra uprefiksert `konsept_relasjon` selv når de kjøres med `prefix="tmp_"`. Dette er ikke introdusert av denne planen, og er dekket av `JanzzRepositoryTypeaheadTest`.

## Tilbakemeldinger fra review (status)

- [x] Bruk fullt skjema (esco/styrk08/isco/undertype) i kombinert tabell, helt likt de andre typeahead-tabellene — ikke smalt returobjekt.
- [x] Bruk `verdi`/`verdi_lc` (ikke `label`/`label_lc`) i database, helt likt de andre tabellene.
- [x] Primærnøkkel `id integer` (term-id), helt likt de andre tabellene.
- [x] Indekseringsmetode helt lik de andre (btree på `konsept_id` og `verdi_lc`, GIN trgm på `verdi_lc`).
- [x] Unngå duplikater ved insert via `where not exists` (ett rad per term-id på tvers av kategoriene).
- [x] Bytt navn fra «softskills» til «personlige egenskaper». Egen tabell og eget typeahead-endepunkt.
- [x] Kombinert endepunkt bruker `q` på samme måte som de andre typeahead-endepunktene (`/kompetanse`, `/autorisasjon` …).
- [x] Fjern godkjenninger fra kombinert tabell (subset av kompetanser, ville gitt duplikater).
- [x] Navnebytte fra `behov` til `samlede_kvalifikasjoner`.
- [x] `/personlige_egenskaper` bruker eksisterende `finnTypeahead`-mønster (samme som `/kompetanse`, midten-av-ord-treff).
- [x] Typeahead-endepunktene returnerer `ResponseEntity<List<...>>` — ikke `Any`, ingen problem details.
- [x] Under 2 tegn → `200 OK` med tom liste, ikke `400` (samme mønster som `/kompetanse`, `/autorisasjon` osv.).
