# Typeahead for personlige egenskaper (softskills) i pam-ontologi

## Bakgrunn

Designet for «Legg til arbeidsgivere» i rekrutteringstreff har et eget felt **Personlige egenskaper (Valgfritt)** (Kundebehandler, Resepsjonist-type egenskaper, …). Vi trenger en typeahead for dette feltet på linje med den kombinerte typeaheaden for [arbeidsgivers behov](./kombinert-typeahead-i-pam-ontologi.md), men avgrenset til softskills/personlige egenskaper.

### Har vi data i pam-ontologi?

Ja. Janzz-ontologien har en egen branch for softskills, og importflyten gjenkjenner dem allerede:

- `InnlastingService.finnKonseptTypeForNode(...)` setter `KonseptType.SOFTSKILL` når `branch:softskill == True` på noden (se `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/service/InnlastingService.kt`, linjen `else if (node["branch:softskill"].equals("True", true)) KonseptType.SOFTSKILL`).
- `KonseptType.SOFTSKILL` har `dbVerdi = "SS"` i `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/repository/JanzzRepository.kt`.
- Relasjonen `HAS_SOFTSKILL` håndteres i `InnlastingService` (line ~66) — softskills er altså allerede lagret i `konsept`-tabellen med `type='SS'`, og relasjoner mellom stillinger/kompetanser og softskills finnes i `konsept_relasjon`.

Det finnes i dag **ingen** typeahead-tabell for softskills (`typeahead_stilling`, `_kompetanse`, `_autorisasjon`, `_godkjenninger`, `_fagdokumentasjon`, `_geografi` dekker ikke softskills), og `slettOgIndekserTypeahead*` kalles ikke for `KonseptType.SOFTSKILL`. Vi kan derfor gjenbruke det eksisterende mønsteret uten å endre på konsept-/term-importen — vi trenger kun en ny typeahead-tabell og et nytt endepunkt.

### Forholdet til kombinert typeahead

Personlige egenskaper er **ikke** inkludert i den kombinerte typeaheaden for arbeidsgivers behov. Grunnen er enkel: designet har dem som eget felt, og semantisk blander vi ikke en softskill som «Kundebehandler» inn blant yrkestitler og fagbrev — det vil forvirre både brukere og senere konsumenter.

Strukturen (DTO, JSONB-lagring i `arbeidsgiver_behov.personlige_egenskaper`) er bevisst identisk med kombinert typeahead, slik at frontend kan gjenbruke samme `BehovTag`-komponent og `{label, kategori, konseptId?}`-format.

---

## Valgt løsning: Egen typeahead-tabell for softskills

Ny tabell `typeahead_personlige_egenskaper` med samme form som den kombinerte. Den populeres under Janzz-import fra `${prefix}konsept` + `${prefix}term` på lik linje med de øvrige `typeahead_*`-tabellene, filtrert på `type='SS'`.

Kategori-verdien i DB og enum er `SOFTSKILL` (ASCII, samsvarer med `KonseptType.SOFTSKILL`). Frontend kan eksponere en penere visning («Personlig egenskap») uten at det påvirker intern modell.

### Database-skjema

```sql
CREATE TABLE typeahead_personlige_egenskaper (
    id         serial PRIMARY KEY,
    konsept_id integer NOT NULL,
    label      varchar(500) NOT NULL,
    label_lc   varchar(500) NOT NULL,
    kategori   varchar(30) NOT NULL DEFAULT 'SOFTSKILL'
);

CREATE TABLE tmp_typeahead_personlige_egenskaper (
    id         serial PRIMARY KEY,
    konsept_id integer NOT NULL,
    label      varchar(500) NOT NULL,
    label_lc   varchar(500) NOT NULL,
    kategori   varchar(30) NOT NULL DEFAULT 'SOFTSKILL'
);

CREATE INDEX trgm_idx_pe_label_lc ON typeahead_personlige_egenskaper USING gin (label_lc gin_trgm_ops);
```

`kategori`-kolonnen har en fast verdi i dag, men beholdes for å matche DTO-en (`BehovTag`) uten spesial-case i repository og for å åpne for underkategorier senere uten skjema-endring.

`tmp_*`-tabellen får **ikke** GIN-indeks (samme mønster som `tmp_typeahead_*`).

### Populering under import

Ny privat metode i `JanzzRepository`:

```kotlin
fun slettOgIndekserTypeaheadForPersonligeEgenskaper(prefix: String = "") {
    slettOgIndekserTypeahead("typeahead_personlige_egenskaper", prefix, filtrerStyrk = false, KonseptType.SOFTSKILL)
}
```

Denne gjenbruker den eksisterende private `slettOgIndekserTypeahead(tabellnavn, prefix, filtrerStyrk, konseptType)`-funksjonen i repositoryet, som allerede:

- truncater `${prefix}$tabellnavn`
- inserter `(id, konsept_id, verdi, verdi_lc, styrk08ssb, …)` fra `${prefix}konsept` + `${prefix}term` filtrert på `k2.type = konseptType.dbVerdi` (`'SS'`), `t2.tag in ('p','l')`, `lang='no'`, `no_label is not null`, `umbrella=0`.

Fordi `slettOgIndekserTypeahead` inserter et fast sett kolonner (inkl. styrk/esco) som ikke finnes i vår nye smale tabell, legger vi i stedet til en dedikert, smalere variant som bare inserter `(konsept_id, label, label_lc)` + setter `kategori='SOFTSKILL'`:

```kotlin
fun slettOgIndekserTypeaheadForPersonligeEgenskaper(prefix: String = "") {
    val tab = "${prefix}typeahead_personlige_egenskaper"
    namedJdbcTemplate.update("truncate table $tab", MapSqlParameterSource())
    namedJdbcTemplate.update(
        """
        insert into $tab (konsept_id, label, label_lc, kategori)
        select k.konsept_id, t.verdi, t.verdi_lc, 'SOFTSKILL'
        from ${prefix}konsept k, ${prefix}term t
        where t.konsept_id = k.konsept_id
          and k.type = '${KonseptType.SOFTSKILL.dbVerdi}'
          and t.tag in ('p','l')
          and t.lang = 'no'
          and k.no_label is not null
          and k.umbrella = 0
        """.trimIndent(),
        MapSqlParameterSource()
    )
}
```

Endringer i `InnlastingService`:

- `lastInnTypeahead()`: legg til `janzzRepository.slettOgIndekserTypeaheadForPersonligeEgenskaper("tmp_")` etter de eksisterende `slettOgIndekser*`-kallene (og før kombinert typeahead, slik at den kan leses senere hvis vi noen gang ønsker å inkludere softskills der).
- `overforNedlastetOntologi()`: truncate `typeahead_personlige_egenskaper` og kall `janzzRepository.overforTypeahead("typeahead_personlige_egenskaper")`.
- `trunkerArbeidstabeller()`: legg til `janzzRepository.slettTabell("tmp_typeahead_personlige_egenskaper")`.

Merk: `overforTypeahead(tabellnavn)` forventer i dag kolonnene `(id, konsept_id, verdi, verdi_lc, styrk08ssb, styrk08ssb_label, isco08, esco, esco_label, undertype)`. Det passer ikke vår smalere tabell. Vi legger derfor til en ny overload / en egen liten overføringsfunksjon:

```kotlin
fun overforTypeaheadSmal(tabellnavn: String) {
    val felter = "id, konsept_id, label, label_lc, kategori"
    namedJdbcTemplate.update(
        "insert into $tabellnavn($felter) select $felter from tmp_$tabellnavn",
        MapSqlParameterSource()
    )
}
```

Samme overload kan brukes av kombinert typeahead (`typeahead_arbeidsgivers_behov`), så vi slipper å duplisere denne logikken mellom planene.

### Søk og sortering

Identisk rangering som kombinert typeahead (se [kombinert-typeahead-i-pam-ontologi.md — alternativ 2](./kombinert-typeahead-i-pam-ontologi.md)): ordgrense-rangering + GIN trigram på `label_lc`, med n-token AND-logikk.

```sql
SELECT id, konsept_id, label, kategori
FROM typeahead_personlige_egenskaper
WHERE label_lc ILIKE :t1_llike
  AND label_lc ILIKE :t2_llike                -- osv. for alle tokens, dynamisk
ORDER BY
  CASE
    WHEN label_lc = :q                                 THEN 0
    WHEN label_lc LIKE :q_like                         THEN 1
    WHEN label_lc LIKE :t1_like                        THEN 2
    WHEN label_lc LIKE '% ' || :t1 || '%'              THEN 3
    ELSE                                                    4
  END,
  length(label_lc),
  label_lc
LIMIT 50;
```

Ingen `kategori`-filter siden tabellen bare inneholder softskills.

### API-endepunkt

```
GET /rest/typeahead/personlige-egenskaper?q=<søkeord>
```

- Minimum lengde på `q`: 2 tegn (samme som øvrige typeahead-endepunkter i `TypeaheadController`).
- Returnerer `List<ArbeidsgiversBehov>` (samme DTO som kombinert typeahead), alltid med `kategori = SOFTSKILL`. Gjenbruk av DTO gjør at frontend kan dele komponent og lagringsformat mellom kombinert typeahead og personlige egenskaper.

### Respons-DTO

Gjenbruk `ArbeidsgiversBehov` fra kombinert typeahead-planen. Legg til `SOFTSKILL` som verdi i `BehovKategori`-enumen:

```kotlin
enum class BehovKategori {
    YRKESTITTEL,
    KOMPETANSE,
    AUTORISASJON,
    GODKJENNING,
    FAGDOKUMENTASJON,
    FORERKORT,
    SOFTSKILL
}
```

### Konsistens mot `arbeidsgivers-behov`-plan

`arbeidsgiver_behov.personlige_egenskaper` er `jsonb` med array av `{label, kategori, konseptId?}` (se [arbeidsgivers-behov.md](./arbeidsgivers-behov.md)). Typeahead-APIet leverer nøyaktig denne strukturen, så frontend kan lagre forslaget direkte uten ekstra mapping.

---

## Implementeringsplan

Rekkefølge slik at hver commit kan kjøres og testes isolert. Kan gjøres etter eller parallelt med kombinert typeahead.

1. **Flyway-migrasjon** (neste ledige V-nummer etter kombinert typeahead, antatt `V11__typeahead_personlige_egenskaper.sql`):
   - `CREATE TABLE typeahead_personlige_egenskaper` + `tmp_typeahead_personlige_egenskaper`.
   - GIN trigram-indeks på `typeahead_personlige_egenskaper.label_lc`.

2. **Repository** — `pam-ontologi/.../repository/JanzzRepository.kt`:
   - `slettOgIndekserTypeaheadForPersonligeEgenskaper(prefix: String = "")` (SQL over).
   - `finnTypeaheadPersonligeEgenskaper(oppslagsord: String): List<ArbeidsgiversBehov>` med samme n-token-logikk som kombinert typeahead.
   - Utvid `overforTypeahead`-familien med `overforTypeaheadSmal(tabellnavn)` som flytter `(id, konsept_id, label, label_lc, kategori)`.

3. **InnlastingService** — `pam-ontologi/.../service/InnlastingService.kt`:
   - Legg til kall i `lastInnTypeahead()`, `overforNedlastetOntologi()` og `trunkerArbeidstabeller()` (se over).

4. **Service** — utvid `TypeaheadService`:
   - `finnPersonligeEgenskaper(q: String): List<ArbeidsgiversBehov>` som delegerer til repository.

5. **Controller** — `pam-ontologi/.../rest/TypeaheadController.kt`:
   - `GET /rest/typeahead/personlige-egenskaper` med query-param `q` (min 2 tegn). Returner `List<ArbeidsgiversBehov>`.

6. **Tester** — se [Testing](#testing) nedenfor.

## Relevante filer

- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/repository/JanzzRepository.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/service/TypeaheadService.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/rest/TypeaheadController.kt`
- `pam-ontologi/src/main/kotlin/no/nav/pam/ontologi/service/InnlastingService.kt`
- `pam-ontologi/src/main/resources/db/migration/`

## Testing

Bygger videre på testoppsettet som etableres i [kombinert-typeahead-i-pam-ontologi.md — Testing](./kombinert-typeahead-i-pam-ontologi.md#testing) (Testcontainers + Flyway-migrasjoner + minimal dataseeding). Samme `TestDatabase`-hjelper gjenbrukes.

### Konkrete tester

**Repository (`JanzzRepositoryTest`):**

- `slettOgIndekserTypeaheadForPersonligeEgenskaper("tmp_")` populerer `tmp_typeahead_personlige_egenskaper` kun med rader der `konsept.type = 'SS'`.
- Rader med `umbrella=1`, `no_label IS NULL` eller `term.lang != 'no'` ekskluderes (dekker samme filter som for øvrige typeaheads).
- `overforTypeaheadSmal("typeahead_personlige_egenskaper")` flytter rader fra `tmp_*` til prod-tabellen.
- `finnTypeaheadPersonligeEgenskaper` med én-token query returnerer riktig rangering (eksakt → prefix → ordgrense → substring).
- `finnTypeaheadPersonligeEgenskaper` med flere tokens bruker AND-logikk.
- Alle returnerte rader har `kategori = SOFTSKILL`.
- Resultatet er begrenset til 50 rader.

**Controller (`TypeaheadControllerTest`):**

- `GET /rest/typeahead/personlige-egenskaper?q=k` returnerer `400`.
- `GET /rest/typeahead/personlige-egenskaper?q=ku` returnerer `200` med JSON-array.

**Service (`TypeaheadServiceTest`):**

- Delegerer til repository og returnerer `ArbeidsgiversBehov`-DTO uten tap av felter.

### Avgrensning

- Ingen krav om at softskills har stabile `konsept_id`-er over tid — testdata seedes programmatisk i hver test.
- Ingen krav om test av konkrete softskill-labels fra Janzz (data kan endres ved nattlig import).
