# Alternativ 2: PostgreSQL med søketabell (rekrutteringstreffsøk)

## Bakgrunn og mål

Dette dokumentet beskriver et alternativ til OpenSearch der søk, filtrering, sortering og aggregeringer for rekrutteringstreffsøk løses i PostgreSQL (17+).

Målet er å flytte dagens klientside-filtrering til backend uten å innføre en separat søkeinfrastruktur. Alternativet er mest aktuelt dersom:

- datamengden er moderat
- søk hovedsakelig er filterdrevet
- vi ønsker færre bevegelige deler i drift
- vi kan akseptere at relevans og typo-toleranse blir svakere enn i en dedikert søkemotor

Dette er ikke et forsøk på å gjøre PostgreSQL til en full erstatning for OpenSearch på alle områder. Det er en pragmatisk plan for å dekke det konkrete behovet i rekrutteringstreff.

### Forventet volum

- **Totalt historisk:** opp til ~100 000 treff over tid
- **Aktive treff** (i planlegging eller utførelse): sjelden over 1 000, selv i et sterkt suksessscenario
- **Samtidige brukere:** Nav-veiledere og markedskontakter, ikke publikumstrafikk

Dette er et lite datasett. 100 000 rader med denormaliserte søkedokumenter er trivielt for PostgreSQL — både for filtrering, fulltekstsøk og aggregeringer.

---

## Kort vurdering

Alt som trengs for denne løsningen finnes i PostgreSQL 14+. Vi bruker i dag PostgreSQL 17, som er mer enn tilstrekkelig. En oppgradering til 18 gir marginale ytelsesforbedringer (asynkron I/O, parallell GIN-bygging), men ingen funksjonelle forskjeller for dette brukstilfellet. Løsningen bygger på etablerte PostgreSQL-mekanismer:

- `tsvector` og fulltekstsøk for stemming og fritekst
- `pg_trgm` for fuzzy søk og feilstavinger
- vanlige B-tree-indekser for filtre og sortering
- eventuelt `GIN` og `BRIN` der det er naturlig
- en denormalisert søketabell for raskere lesing og enklere queries

For dette domenet er hovedgevinsten mindre arkitektur og enklere drift. Hovedulempen er svakere søkekvalitet og dårligere skaleringshistorie enn en dedikert søkemotor.

---

## Arkitekturoversikt

```text
┌──────────────┐       ┌──────────────────────────┐       ┌──────────────────────┐
│   Frontend   │──────▶│ rekrutteringstreff-api   │──────▶│ PostgreSQL           │
│  (Next.js)   │ POST  │ /api/rekrutteringstreff/ │ SQL   │ søketabell + indekser│
│              │◀──────│ sok                      │◀──────│                      │
└──────────────┘       └──────────────────────────┘       └──────────────────────┘
```

| Komponent                | Ansvar                                                                   |
| ------------------------ | ------------------------------------------------------------------------ |
| Frontend                 | Sender hele søketilstanden, viser resultater og paginering               |
| `rekrutteringstreff-api` | Søke-endepunkt, bygger SQL-spørringer, autentisering og tilgangskontroll |
| PostgreSQL               | Primær database og søkeindeks i samme system                             |

Det introduseres ingen egen søke-app og ingen egen indekser-app i første versjon.

---

## Anbefalt modell

### Egen søketabell

Det anbefales å bygge søk på en egen denormalisert tabell i stedet for å spørre direkte mot normaliserte domene-tabeller for hver request.

Forslag:

```sql
create table rekrutteringstreff_sok (
    treff_id uuid primary key,
    tittel text not null,
    beskrivelse text,
    status text not null,
    fra_tid timestamptz,
    til_tid timestamptz,
    svarfrist timestamptz,
    gateadresse text,
    postnummer text,
    poststed text,
    kommunenummer text,
    kommune text,
    fylkesnummer text,
    fylke text,
    opprettet_av_tidspunkt timestamptz not null,
    sist_endret timestamptz not null,
    antall_arbeidsgivere integer not null,
    antall_jobbsokere integer not null,
    eiere text[] not null,
    kontorer text[] not null,
    arbeidsgiver_orgnr text[] not null,
    arbeidsgiver_navn text[] not null,
    innlegg_titler text[] not null,
    soketekst text not null,
    soke_vektor tsvector not null
);
```

`soketekst` er et sammenslått tekstfelt som brukes av `pg_trgm`.

`soke_vektor` er en ferdigbygd `tsvector` som brukes av fulltekstsøk.

### Hvorfor egen søketabell

- SQL-spørringene blir enklere og mer stabile
- vi kan indeksere nøyaktig det vi søker på
- vi slipper mange joins per request
- vi får et tydelig sted å håndtere søkespesifikk denormalisering

Dette er samme grunnidé som i OpenSearch-planen: et denormalisert snapshot. Forskjellen er at snapshotet lagres i PostgreSQL i stedet for i en separat søkeindeks.

---

## Oppdateringsstrategi

To strategier er realistiske.

### Alternativ A: Oppdater søketabellen synkront i samme transaksjon

Ved alle indekseringsrelevante endringer oppdateres `rekrutteringstreff_sok` i samme `executeInTransaction`-blokk som domeneendringen.

Fordeler:

- sterk konsistens
- enkel lesemodell
- ingen bakgrunnskø

Ulemper:

- mer skrivelast i transaksjonen
- flere steder som må holde snapshotet oppdatert

### Alternativ B: Behold outbox-køen, men skriv til Postgres-søketabell i worker

Dette følger mye av samme mønster som OpenSearch-planen, men worker bygger og upserter til `rekrutteringstreff_sok` i stedet for å publisere til OpenSearch.

Fordeler:

- mindre last i domene-transaksjoner
- robust ved mange relaterte oppdateringer
- enkelt å reindeksere

Ulemper:

- eventual consistency
- mer arkitektur enn alternativ A

Med forventet volum (maks ~1 000 aktive treff) er alternativ A klart anbefalt. Skrivelast og transaksjonskostnad er neglisjerbar. Alternativ B kan vurderes hvis vi senere trenger asynkron prosessering, men det er ikke nødvendig i dag.

---

## Request og respons

API-kontrakten kan være identisk med OpenSearch-varianten.

```kotlin
data class RekrutteringstreffSokRequest(
    val fritekst: String? = null,
    val visningsstatuser: List<Visningsstatus>? = null,
    val fylkesnummer: List<String>? = null,
    val kommunenummer: List<String>? = null,
    val kontorer: List<String>? = null,
    val visning: Visning = Visning.ALLE,
    val sortering: Sortering = Sortering.SIST_OPPDATERTE,
    val side: Int = 0,
    val antallPerSide: Int = 25,
)
```

Responsen kan også være den samme. Det er en fordel: frontend trenger ikke vite om backend bruker OpenSearch eller PostgreSQL.

---

## Fritekststrategi

### Fulltekstsøk

`soke_vektor` bygges av relevante felt, for eksempel:

- `tittel`
- `beskrivelse`
- `fylke`
- `kommune`
- `poststed`
- `gateadresse`
- arbeidsgivernavn
- innleggstitler

Eksempel:

```sql
setweight(to_tsvector('norwegian', coalesce(tittel, '')), 'A') ||
setweight(to_tsvector('norwegian', coalesce(beskrivelse, '')), 'B') ||
setweight(to_tsvector('norwegian', coalesce(fylke, '')), 'C') ||
setweight(to_tsvector('norwegian', coalesce(kommune, '')), 'C') ||
setweight(to_tsvector('norwegian', coalesce(poststed, '')), 'C') ||
setweight(to_tsvector('norwegian', coalesce(gateadresse, '')), 'D') ||
setweight(to_tsvector('norwegian', coalesce(array_to_string(arbeidsgiver_navn, ' '), '')), 'B') ||
setweight(to_tsvector('norwegian', coalesce(array_to_string(innlegg_titler, ' '), '')), 'B')
```

Dette gir:

- stemming for norsk, som hjelper på entall og flertall
- vekting av felter
- raskt oppslag via GIN

### Fuzzy søk med `pg_trgm`

Fulltekstsøk alene håndterer ikke skrivefeil godt. Derfor anbefales `pg_trgm` i tillegg.

Forslag:

- bruk fulltekstsøk som primærmekanisme
- fall tilbake til trigram-søk hvis fulltekstsøket gir få eller ingen treff
- eller kombiner begge med en enkel samlet score

Eksempel på trigram-felt:

```sql
soketekst = lower(concat_ws(' ',
    tittel,
    beskrivelse,
    fylke,
    kommune,
    poststed,
    gateadresse,
    array_to_string(arbeidsgiver_navn, ' '),
    array_to_string(innlegg_titler, ' ')
))
```

Dette gir:

- støtte for feilstavinger
- støtte for delvis tekst
- en rimelig tilnærming til søkemotoraktig oppførsel uten egen søkemotor

Det gir ikke samme kvalitet som en moden søkemotor, men dekker de vanligste feilene.

Et praktisk eksempel er søk på deler av arbeidsgivernavn. Hvis en arbeidsgiver heter `Norsk Hydro`, skal søk på `hydro` kunne treffe dette selv om brukeren ikke skriver hele navnet. Det samme søket skal også kunne treffe `hydro` i innleggstitler eller andre felter som inngår i fritekstsøket. Dette løses ved at både `arbeidsgiver_navn` og andre relevante tekstfelt inngår i både `soke_vektor` og `soketekst`.

Når brukeren samtidig avgrenser geografisk, for eksempel til ett eller flere fylker, kan PostgreSQL kombinere trigram- eller fulltekstindeksen med B-tree-indekser på geografifeltene. Forventet plan er da `Bitmap Index Scan` per aktivt filter og `BitmapAnd` mellom dem, ikke table scan, så lenge predikatene bygges indeksvennlig.

---

## Forslag til indekser

```sql
create extension if not exists pg_trgm;

create index rekrutteringstreff_sok_tsv_gin_idx
    on rekrutteringstreff_sok using gin (soke_vektor);

create index rekrutteringstreff_sok_soketekst_trgm_idx
    on rekrutteringstreff_sok using gin (soketekst gin_trgm_ops);

create index rekrutteringstreff_sok_status_idx
    on rekrutteringstreff_sok (status);

create index rekrutteringstreff_sok_sist_endret_idx
    on rekrutteringstreff_sok (sist_endret desc);

create index rekrutteringstreff_sok_opprettet_idx
    on rekrutteringstreff_sok (opprettet_av_tidspunkt desc);

create index rekrutteringstreff_sok_fra_tid_idx
    on rekrutteringstreff_sok (fra_tid asc);

create index rekrutteringstreff_sok_til_tid_idx
    on rekrutteringstreff_sok (til_tid desc);

create index rekrutteringstreff_sok_fylkesnummer_idx
    on rekrutteringstreff_sok (fylkesnummer);

create index rekrutteringstreff_sok_kommunenummer_idx
    on rekrutteringstreff_sok (kommunenummer);

create index rekrutteringstreff_sok_kontorer_gin_idx
    on rekrutteringstreff_sok using gin (kontorer);

create index rekrutteringstreff_sok_eiere_gin_idx
    on rekrutteringstreff_sok using gin (eiere);
```

Kommentarer:

- `GIN` på `tsvector` og trigram dekker fritekst
- `GIN` på arrays gjør `= any(...)` og overlap-søk mer effektivt
- vanlige filtre og sorteringsfelt får B-tree
- ikke lag komposittindekser for alle kombinasjoner; la planner bruke bitmap scans

Ved forventet volum (opp til 100k rader) er B-tree tilstrekkelig for alle sorteringsfelt. BRIN er ikke nødvendig.

## Regler for indeksvennlig dynamisk SQL

Indeksene over er nødvendige, men de er ikke alene nok til å sikre god query-plan. Dynamisk SQL fungerer godt i PostgreSQL så lenge predikatene bygges på en indeksvennlig måte.

Anbefalte regler:

- bygg ekte dynamisk SQL i Kotlin og utelat filtre som ikke er satt
- bind alle brukerverdier som parametre i prepared statements eller named parameters
- hold predikatene direkte, for eksempel `status in (...)`, `fylkesnummer in (...)`, `kontorer && ...` og `soke_vektor @@ ...`
- normaliser data ved lagring i stedet for å bruke funksjoner på kolonnen i `WHERE`
- bruk whitelistede SQL-fragmenter for sortering, ikke frie felt- eller `order by`-verdier fra klienten
- match datatypene nøyaktig mellom kolonne og parameter

Mønstre som bør unngås:

- `(:status is null or status = :status)`
- `lower(kolonne) = :verdi`
- `date(fra_tid) = :dato`
- `kommunenummer::text = :kommunenummer`
- store `OR`-uttrykk på tvers av ulike felt når det kan uttrykkes som `IN (...)` innenfor én kategori og `AND` mellom kategorier

Årsaken er at slike uttrykk ofte gjør queryene mindre selektive eller hindrer planner i å bruke indeksene effektivt.

Målet er ikke at PostgreSQL alltid skal velge indeks. Ved lite selektive filtre kan `Seq Scan` være riktig plan. Målet er at planner skal ha mulighet til å velge `Bitmap Index Scan`, `BitmapAnd` eller `Index Scan` når queryen tilsier det.

Dette må verifiseres med representative søkekombinasjoner og `EXPLAIN ANALYZE` før løsningen regnes som ferdig tunet.

---

## SQL-modell for visningsstatus

Samme visningsstatus som i OpenSearch-planen kan brukes.

`SOKNADSFRIST_PASSERT` er fortsatt avledet og ikke en domenestatus.

Forslag:

```sql
case
    when status = 'UTKAST' then 'UTKAST'
    when status = 'AVLYST' then 'AVLYST'
    when status = 'FULLFORT' then 'FULLFORT'
    when status = 'PUBLISERT' and svarfrist is not null and svarfrist < now() then 'SOKNADSFRIST_PASSERT'
    when status = 'PUBLISERT' then 'PUBLISERT'
end
```

Ved filtrering brukes eksplisitte `WHERE`-clauses i stedet for et beregnet felt dersom det gir enklere indekshjelp.

---

## SQL-modell for visning

### `ALLE`

Ingen ekstra filter.

### `MINE`

Innlogget navident må finnes i `eiere`.

Eksempel:

```sql
where lower(:navident) = any(eiere)
```

For dette bør `eiere` normaliseres til lowercase ved lagring.

### `MITT_KONTOR`

Innlogget kontor må finnes i `kontorer`.

Eksempel:

```sql
where :kontor = any(kontorer)
```

---

## Sorteringsmodell

Samme sorteringsvalg kan støttes.

### `RELEVANS`

Brukes bare når `fritekst` er satt.

En enkel variant er:

```sql
ts_rank_cd(soke_vektor, websearch_to_tsquery('norwegian', :fritekst))
```

Eventuelt kombinert med trigram:

```sql
(0.8 * ts_rank_cd(...)) + (0.2 * similarity(soketekst, lower(:fritekst)))
```

Dette må valideres med reelle data. Relevans i Postgres krever mer tuning enn i dedikerte søkemotorer.

### `SIST_OPPDATERTE`

```sql
order by sist_endret desc
```

### `NYESTE`

```sql
order by opprettet_av_tidspunkt desc
```

### `ELDSTE`

```sql
order by opprettet_av_tidspunkt asc
```

### `AKTIVE`

```sql
where status = 'PUBLISERT'
  and (til_tid is null or til_tid > now())
order by fra_tid asc nulls last
```

### `FULLFØRTE`

```sql
where status = 'FULLFORT'
order by til_tid desc nulls last
```

---

## Aggregeringer

Aggregeringer brukes til å vise antall treff per filtervalg ved siden av hvert filter i søkepanelet. Tallet skal oppdateres dynamisk og representere nøyaktig hvor mange treff brukeren ville fått dersom de klikker det aktuelle filteret.

### Multi-select facets — ekskluder egen dimensjon

Dette er et avgjørende mønster: antallet for hvert filter i dimensjon X beregnes uten å anvende det aktive filteret i dimensjon X, men alle andre aktive filtre gjelder fortsatt.

Eksempel:

- Brukeren har valgt fylke = "Oslo" og status = "PUBLISERT"
- Antall per statusverdi beregnes med alle filtre unntatt statusfilteret (dvs. bare fylkefilteret gjelder)
- Antall per fylke beregnes med alle filtre unntatt fylkefilteret (dvs. bare statusfilteret gjelder)

Dette gjør at tallene alltid speiler konsekvensen av å klikke, ikke av hva som allerede er valgt. Samme semantikk som Elasticsearch bruker med `post_filter` og global aggregeringer.

### Én query per facetdimensjon

Strategien er å kjøre separate `COUNT`-queryer for hver facetdimensjon i samme HTTP-request. Med tre facets (status, fylke, kontor) og én treffliste blir det fire queryer totalt:

1. Treffliste med paginering
2. Aggregering for visningsstatus (uten statusfilter, med øvrige filtre)
3. Aggregering for fylke (uten fylkesfilter, med øvrige filtre)
4. Aggregering for kontor (uten kontorfilter, med øvrige filtre)

Eksempel på statusaggregering der brukeren har valgt fylke og visning, men ikke status:

```sql
select
    case
        when status = 'UTKAST' then 'UTKAST'
        when status = 'AVLYST' then 'AVLYST'
        when status = 'FULLFORT' then 'FULLFORT'
        when status = 'PUBLISERT' and svarfrist is not null and svarfrist < now() then 'SOKNADSFRIST_PASSERT'
        when status = 'PUBLISERT' then 'PUBLISERT'
    end as visningsstatus,
    count(*) as antall
from rekrutteringstreff_sok
where fylkesnummer = any(:fylkesnummer)   -- øvrige filtre gjelder
  -- statusfilteret er bevisst utelatt
group by 1
order by 1;
```

Eksempel på fylkesaggregering der brukeren har valgt status, men ikke fylke:

```sql
select fylkesnummer as verdi,
       fylke        as label,
       count(*)     as antall
from rekrutteringstreff_sok
where status = any(:statuser)   -- øvrige filtre gjelder
  -- fylkefilteret er bevisst utelatt
group by fylkesnummer, fylke
order by label asc;
```

Samme mønster brukes for kontor.

### Ytelse

Med søketabell og indekser er disse queryene raske:

- Statusaggregering: `GROUP BY` på en indeksert kolonne med lavt kardinalitet — noen mikrosekunder
- Fylkesaggregering: `GROUP BY` på `fylkesnummer` med B-tree og typisk ~20 distinkte verdier — under 1 ms
- Kontoraggregering: `GROUP BY` på `kontorer`-array via GIN — noe tyngre, men fortsatt godt under 10 ms ved 100k rader

Det er viktig at filterpredikatene i aggregeringsqueryene er de samme strukturelt sett som i trefflisten, slik at planner kan bruke bitmap scans på tvers av dimensjonene.

### Sammenligning med Elasticsearch

Elasticsearch løser dette i ett kall via `aggs` + `post_filter`. PostgreSQL krever N+1 queryer (én per facetdimensjon pluss én for trefflisten). For dette domenet er det ingen praktisk ulempe: queryene er lette, de kan kjøres parallelt med JDBC-connection pool, og total svartid vil ligge i samme størrelsesorden. Ved 100k rader er `GROUP BY` trivielt raskt.

### Respons

Responsen beholder samme struktur som OpenSearch-varianten:

```kotlin
data class RekrutteringstreffSokRespons(
    val treff: List<RekrutteringstreffSokTreff>,
    val totaltAntall: Long,
    val side: Int,
    val antallPerSide: Int,
    val aggregeringer: RekrutteringstreffAggregeringer,
)

data class RekrutteringstreffAggregeringer(
    val visningsstatuser: List<FilterValg>,
    val fylkesnummer: List<FilterValg>,
    val kontorer: List<FilterValg>,
)

data class FilterValg(
    val verdi: String,
    val label: String,
    val antall: Long,
)
```

Frontend merker ingen forskjell på om aggregeringene kommer fra Elasticsearch eller PostgreSQL.

---

## Paginering

Offset/limit er sannsynligvis godt nok i første versjon.

```sql
limit :antallPerSide
offset (:side * :antallPerSide)
```

Ved forventet volum (opp til 100k rader totalt, typisk langt færre etter filtrering) er offset/limit uproblematisk.

---

## Støtte for ordvarianter og “slop”

### Entall og flertall

Dette dekkes i stor grad av stemming i PostgreSQL fulltekstsøk.

### Synonymer

Kan løses med:

- synonym- eller thesaurus-dictionary
- `ts_rewrite` med regler i database

Dette gir en brukbar modell for domeneord og alternative skrivemåter.

### Frase og nærhet

PostgreSQL støtter frase- og nærhetssøk via `tsquery`-operatorer og `tsquery_phrase(query1, query2, distance)`.

Det gir en form for “slop”, men ikke samme ergonomi og bredde som i OpenSearch.

### Feilstavinger

Dette dekkes ikke godt av `tsvector` alene. `pg_trgm` må brukes i tillegg.

---

## Hva PostgreSQL-alternativet ikke gjør like godt

Dette bør være eksplisitt før vi velger retning.

### Relevans

Postgres kan gi brukbar relevans, men det er mer manuelt arbeid å få godt resultat. Standard fulltekstrangering er svakere enn moden BM25-basert søkemotoradferd. For dette domenet er likevel de fleste søk filterdrevne, ikke friteksttunge. Relevans er viktigere for Google-aktige søk enn for «vis meg alle treff i Oslo med status Publisert».

### Typo-toleranse

`pg_trgm` fungerer, men er mindre elegant enn en søkemotor med innebygd typo-modell. Ved 100k rader er trigram-søk raskt nok til å brukes som fallback.

### Multisearch

Kan løses med flere SQL-spørringer i samme kall, men finnes ikke som en egen søkeprimitive. Ikke et reelt behov i dette domenet.

### Skalering under høy søkelast

Søk og transaksjoner konkurrerer om samme database. Med maks ~1 000 aktive treff og moderat antall samtidige søk er dette en ikke-risiko. Hvis det likevel blir et problem, kan en read replica settes opp.

### Faceting på store datamengder

Mulig, men dyrere enn i søkemotorer som er bygget for dette. Ved 100k rader er `GROUP BY` med indeks så raskt at forskjellen ikke er merkbar.

---

## Foreslått appstruktur

Det er ikke behov for en ny app i første versjon. Løsningen kan bo i eksisterende `rekrutteringstreff-api`.

```text
RekrutteringstreffSokController
    ↓
RekrutteringstreffSokService
    ↓
RekrutteringstreffSokRepository
    ↓
PostgreSQL
```

Forslag til ansvar:

- Controller: auth, inputvalidering, request/response
- Service: visning, tilgangskontroll, sorteringsregler, orkestrering av liste og aggregeringer
- Repository: SQL-bygging og mapping

---

## Reindeksering og reparasjon

Hvis vi bruker en egen søketabell, trenger vi også en måte å bygge den opp på nytt.

Forslag:

- internt endepunkt eller kommandolinjejobb for full rebuild av `rekrutteringstreff_sok`
- truncate og rebuild i batcher
- logging av antall prosesserte treff og varighet

Eksempel på flyt:

1. Les alle treff porsjonsvis fra databasen.
2. Bygg snapshot per treff.
3. Upsert til `rekrutteringstreff_sok`.
4. Rebuild indekser kun hvis det viser seg nødvendig ved store migrasjoner.

Dette er betydelig enklere enn alias-swap og dual-write i OpenSearch-løsningen.

### Endring av søkeregler uten migrasjon

Søketabellen er en projeksjon av domenedata. Den inneholder ingen autoritativ informasjon. Det betyr at innholdsendringer — som å legge til nye felt i `soketekst`, endre vektingen i `soke_vektor`, eller endre hvilke kilder som inngår i fritekstfeltet — ikke krever en Flyway-migrasjon. Det holder å:

1. Oppdatere Kotlin-koden som bygger snapshoten.
2. Kjøre rebuild-jobben.

Ved 100 000 rader tar en full `TRUNCATE` + batch-upsert sekunder.

Flyway-migrasjoner trengs kun ved skjemaendringer — nye kolonner, endret datatype, nye indekser. Også da brukes rebuild-jobben for å fylle de nye feltene.

---

## Drift og operasjonelle konsekvenser

### Fordeler

- én teknologi mindre
- ingen ETL til ekstern søkeindeks
- enklere lokal utvikling og test
- færre deploybare komponenter

### Ulemper

- søkebelastning lander på primærdatabase eller read replica
- tuning av relevans og fuzzy søk må gjøres manuelt
- aggregeringer kan bli dyre når volumet vokser

Hvis denne løsningen velges, bør read-only søk helst kunne gå mot egen lesereplika når trafikken øker.

---

## Sammenligning: PostgreSQL vs. OpenSearch

| Dimensjon            | PostgreSQL                                                                                         | OpenSearch                                                          |
| -------------------- | -------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------- |
| **Nye apper**        | 0 — bor i `rekrutteringstreff-api`                                                                 | 2 — `rekrutteringstreff-indekser` + `rekrutteringstreff-sok`        |
| **Ny infrastruktur** | Ingen (bruker eksisterende database)                                                               | OpenSearch-kluster (Aiven), Kafka-hendelser, alias-håndtering       |
| **Indeksering**      | Synkront i transaksjon (alt. A) eller enkel outbox → Postgres-tabell                               | Outbox → scheduler → Rapids → Kafka-konsument → OpenSearch          |
| **Reindeksering**    | `truncate` + batch-upsert (sekunder)                                                               | Dual-write, alias-swap, env-var-styrt deploy                        |
| **Fritekst**         | `tsvector` + GIN (stemming, vekting)                                                               | `all_text_no` med norsk analyzer (stemming, BM25)                   |
| **Typo-toleranse**   | `pg_trgm` (brukbart, manuelt)                                                                      | Innebygd i analyzer-pipeline                                        |
| **Relevans**         | `ts_rank_cd`, eventuelt kombinert med trigram                                                      | BM25 med boost, multi-match                                         |
| **Filtre**           | B-tree + GIN-indekser, bitmap AND/OR                                                               | `term`/`terms`-filter, svært raskt                                  |
| **Aggregeringer**    | N+1 separate `COUNT GROUP BY`-queryer per request, én per facetdimensjon — trivielt ved 100k rader | `aggs` + `post_filter` i ett kall — mer elegant, men samme resultat |
| **Visningsstatus**   | `CASE`-uttrykk i SQL                                                                               | `bool`-query med `svarfrist < now`                                  |
| **Konsistens**       | Sterk (synkron) eller noen sekunder (outbox)                                                       | Eventual (~10–15 sek via scheduler + Kafka + refresh)               |
| **Drift**            | Ingenting nytt                                                                                     | Ny app-deploy, alerts, OpenSearch Dashboard, Kafka-monitorering     |
| **Testbarhet**       | Testcontainers med Postgres (allerede brukt)                                                       | Testcontainers med OpenSearch (nytt oppsett)                        |
| **Kodeomfang**       | Controller + Service + Repository i eksisterende app                                               | To nye apper, OpenSearch-klient, mapping, settings, lyttere         |
| **Risiko ved feil**  | Standard database-debugging                                                                        | Indeks ut av synk, poison pill, CrashLoopBackOff, alias-feil        |
| **Skaleringstak**    | Godt nok til millioner av rader med riktige indekser                                               | Nesten ubegrenset horisontalt                                       |

### Hva OpenSearch gir som PostgreSQL ikke gir

- Bedre BM25-relevans ut av boksen
- Sterkere typo-toleranse uten ekstra arbeid
- Isolert søkeinfrastruktur som ikke påvirker OLTP
- Mer moden aggregeringsmodell under svært høy last
- `aggs` + `post_filter` — innebygd semantikk for multi-select facets, ikke noe som må implementeres manuelt og testes nøye
- **Mønstergjenbruk:** kandidat- og stillingsdomenet bruker allerede Elasticsearch med identisk filterpanel og multi-select facets. Søkekontrakt, aggregeringslogikk og frontend-integrasjon er etablerte mønstre i samme codebase — disse kan gjenbrukes direkte

### Hva PostgreSQL gir som OpenSearch ikke gir

- Null nye apper, null ny infrastruktur
- Sterk konsistens uten eventual-delay
- Dramatisk enklere reindeksering
- Enklere testing, debugging og lokal utvikling
- Mye mindre kode å skrive, vedlikeholde og forstå

---

## Anbefaling

**PostgreSQL anbefales for dette domenet.**

Begrunnelse:

1. **Volumet er lite.** Opp til 100 000 historiske treff og maks ~1 000 aktive er trivielt for PostgreSQL med riktige indekser. På dette volumet vil forskjellen i svartid mellom Postgres og OpenSearch ikke være merkbar for brukeren.

2. **Søket er filterdrevet, ikke friteksttungt.** Brukerne filtrerer primært på status, fylke, kontor og visning. Fritekst er et supplement. For filterdrevne søk er PostgreSQL like godt som OpenSearch — indekser og bitmap scans løser det effektivt.

3. **Arkitekturkostnaden ved OpenSearch er høy.** OpenSearch-planen krever to nye apper, Kafka-integrasjon, OpenSearch-kluster, mapping/settings, dual-write-reindeksering, alias-håndtering og nye alerts. Alt dette er velprøvd og godt designet i planen, men det er mye maskineri for et lite datasett.

4. **Konsistens er enklere.** Med synkron oppdatering av søketabellen er søkeresultatene alltid oppdaterte. Ingen delay mellom domeneendring og søkbar endring.

5. **Drift og feilhåndtering er enklere.** Ingen poison pills, ingen CrashLoopBackOff, ingen indeks-ut-av-synk-problematikk. Standard database-drift.

6. **Søkekvaliteten er god nok.** Stemming for norsk (entall/flertall), `pg_trgm` for skrivefeil, `ts_rank_cd` for relevans. Det er ikke Google-kvalitet, men det trenger det heller ikke å være for et internt fagverktøy.

7. **Migrasjonssti finnes.** Hvis volumet eller søkebehovet vokser vesentlig, kan API-kontrakten beholdes og backend byttes til OpenSearch senere. Frontend merker ingen forskjell.

8. **Planlagte utvidelser utfordrer ikke valget.**

### Motargument: gjenbruk av etablert Elasticsearch-mønster

Et reelt motargument er at kandidat- og stillingsdomenet allerede bruker Elasticsearch med identisk filterpanel og multi-select facets. Søkekontrakt, `aggs` + `post_filter`-mønster og frontend-integrasjon er etablerte mønstre som kan gjenbrukes direkte.

Det betyr at:

- Multi-select facets med korrekt semantikk er allerede løst og testet — ikke noe vi trenger å implementere og verifisere på nytt i PostgreSQL
- Felles søkeinfrastruktur på tvers av domenene er enklere å drifte og forstå for teamet
- Kompetansen og verktøyene (Kibana/OpenSearch Dashboard, alarmer, testoppsett) finnes allerede

Dette er det sterkeste argumentet for OpenSearch i dette domenet. Hvis teamet allerede eier og drifter Elasticsearch for kandidat og stilling, er den reelle merkostnaden ved å legge til rekrutteringstreff på samme kluster vesentlig lavere enn det sammenligningstabellen antyder — mye av infrastrukturen er allerede betalt.

**Konklusjonen endres likevel ikke** fordi arkitekturkostnaden ved to nye apper og Kafka-integrasjon er reell uavhengig av om klusteret finnes fra før. PostgreSQL anbefales fortsatt for dette domenet og volumet. Men dersom teamet vurderer at gjenbruk av Elasticsearch-mønsteret er viktigere enn enklere arkitektur, er OpenSearch et fullt forsvarlig valg.

8. **Planlagte utvidelser utfordrer ikke valget.** Utvidelsesmulighetene beskrevet i [utvidbarhet.md](../6-kvalitet/utvidbarhet.md) — uavhengighet fra CV-krav, brukere utenfor oppfølging, alternative innganger for påmelding, store arrangementer og Workops-møter — handler om å øke hvem som kan delta og hvordan man kommer inn. De endrer ikke hva man søker etter eller hvor mye data det er. Søketabellen har én rad per treff, og antall treff påvirkes ikke av flere brukergrupper eller deltakere. Enklere arkitektur gjør det også lettere å utvide uten å koordinere endringer på tvers av flere apper.

### Når bør vi revurdere?

- Hvis datamengden vokser langt utover 100 000 (f.eks. millioner)
- Hvis fritekstsøk med avansert relevans blir et hovedbehov
- Hvis søkelast fra mange samtidige brukere faktisk belaster databasen merkbart
- Hvis vi trenger søk som en plattformkapabilitet på tvers av flere domener

Ingen av disse er sannsynlige på kort sikt gitt dagens forventninger.

---

## Foreslått førsteversjon

Hvis vi vil teste denne retningen raskt, er en fornuftig førsteversjon:

1. Opprett `rekrutteringstreff_sok` som denormalisert tabell.
2. Bygg `soke_vektor` og `soketekst` i samme transaksjon som domeneendringer.
3. Implementer `POST /api/rekrutteringstreff/sok` i `rekrutteringstreff-api`.
4. Støtt filtre, visning, sortering og paginering.
5. Bruk `tsvector` som primært fritekstsøk.
6. Legg på `pg_trgm` som fallback eller supplement.
7. Implementer separate aggregat-queryer for status, fylke og kontor.
8. Mål svartider med realistiske data før det tas stilling til OpenSearch.

Dette gir et reelt sammenligningsgrunnlag før man innfører mer infrastruktur.

---

## TODO

### Oppgave 1: Søketabell

- [ ] Opprett Flyway-migrasjon for `rekrutteringstreff_sok`
- [ ] Definer hvilke felter som skal denormaliseres
- [ ] Legg til `soketekst`
- [ ] Legg til `soke_vektor`

### Oppgave 2: Oppdateringsflyt

- [ ] Velg mellom synkron oppdatering og outbox-basert oppdatering
- [ ] Sørg for at alle indekseringsrelevante domeneendringer oppdaterer søketabellen
- [ ] Sørg for at `sistEndret` oppdateres ved alle relevante endringer

### Oppgave 3: Indekser

- [ ] Aktiver `pg_trgm`
- [ ] Opprett GIN-indeks for `soke_vektor`
- [ ] Opprett trigram-indeks for `soketekst`
- [ ] Opprett nødvendige B-tree-indekser for filtre og sortering
- [ ] Valider query-planer med `EXPLAIN ANALYZE`

### Oppgave 4: API

- [ ] Implementer `POST /api/rekrutteringstreff/sok`
- [ ] Legg til autentisering og rollevalidering
- [ ] Implementer SQL for filtre, sortering og paginering
- [ ] Implementer separate `COUNT GROUP BY`-queryer for visningsstatus, fylke og kontor
- [ ] Bruk "ekskluder egen dimensjon"-mønster: statusaggregering kjøres uten aktivt statusfilter, fylkesaggregering uten aktivt fylkesfilter osv.
- [ ] Returner `aggregeringer` med `FilterValg(verdi, label, antall)` i responsen

### Oppgave 5: Rebuild og drift

- [ ] Implementer intern rebuild-jobb for søketabellen
- [ ] Legg til logging for query-varighet og aggregat-varighet
- [ ] Mål effekt på database under realistisk last

### Oppgave 6: Frontend

- [ ] Frontend kan bruke samme request/response-kontrakt som i OpenSearch-planen
- [ ] Bytt fra klientside-filtrering til server-side søk
- [ ] Behold URL-synk av søkeparametre
