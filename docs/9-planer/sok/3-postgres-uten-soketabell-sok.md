# Alternativ 3: PostgreSQL uten søketabell (rekrutteringstreffsøk)

## Idé

Søk direkte mot `rekrutteringstreff`-tabellen slik den er i dag, med joins der vi trenger mer data. Ingen ny søketabell, ingen synkroniseringslogikk, ingen ny infrastruktur.

---

## Hvorfor dette alternativet

- `eiere` og `kontorer` er allerede arrays på `rekrutteringstreff`-tabellen
- `status` er allerede en kolonne
- Alle filtre som trengs i en minimal versjon finnes allerede i eksisterende tabellstruktur
- Kan implementeres med minimale kodeendringer i eksisterende `rekrutteringstreff-api`

---

## Minimal førsteversjon: eier + status

Den enkleste versjonen som gir reell verdi er kun to dimensjoner:

### Eierfilter (tabs)

| Tab         | SQL-clause                      |
| ----------- | ------------------------------- |
| Alle        | Ingen ekstra filter             |
| Mine        | `lower(:navident) = ANY(eiere)` |
| Mitt kontor | `:kontor = ANY(kontorer)`       |

### Statusfilter

| Visningsstatus       | SQL-clause                                                           |
| -------------------- | -------------------------------------------------------------------- |
| UTKAST               | `status = 'UTKAST'`                                                  |
| PUBLISERT            | `status = 'PUBLISERT' AND (svarfrist IS NULL OR svarfrist >= now())` |
| SOKNADSFRIST_PASSERT | `status = 'PUBLISERT' AND svarfrist < now()`                         |
| FULLFORT             | `status = 'FULLFØRT'`                                                |
| AVLYST               | `status = 'AVLYST'`                                                  |

`SLETTET` filtreres alltid bort.

### Eksempel-query for minimal versjon

```sql
SELECT id, tittel, status, fra_tid, til_tid, svarfrist,
       gateadresse, postnummer, poststed,
       kommune, fylke,
       opprettet_av_tidspunkt, sist_endret,
       eiere, kontorer
FROM rekrutteringstreff
WHERE status != 'SLETTET'
  -- eierfilter (dynamisk, utelates for "Alle")
  AND lower(:navident) = ANY(eiere)
  -- statusfilter (dynamisk, utelates om ingen valgt)
  AND status = ANY(:statuser)
ORDER BY sist_endret DESC
LIMIT :antallPerSide OFFSET :side * :antallPerSide
```

### Statusaggregering

I den minimale versjonen er statusfilter den eneste filterdimensjonen med aggregeringer. Queryen er en enkel `COUNT GROUP BY` mot samme tabell — ingen joins.

Viktig: aggregeringen for status kjøres **uten** statusfilteret, men **med** eierfilteret. Det betyr at tallene viser hvor mange treff brukeren ville fått i hver status dersom de velger den.

```sql
SELECT
    CASE
        WHEN status = 'UTKAST' THEN 'UTKAST'
        WHEN status = 'AVLYST' THEN 'AVLYST'
        WHEN status = 'FULLFØRT' THEN 'FULLFORT'
        WHEN status = 'PUBLISERT' AND svarfrist IS NOT NULL AND svarfrist < now() THEN 'SOKNADSFRIST_PASSERT'
        WHEN status = 'PUBLISERT' THEN 'PUBLISERT'
    END AS visningsstatus,
    count(*) AS antall
FROM rekrutteringstreff
WHERE status != 'SLETTET'
  -- eierfilter gjelder
  AND lower(:navident) = ANY(eiere)
  -- statusfilter er bevisst utelatt
GROUP BY 1
ORDER BY 1;
```

Dette gir f.eks. `Publisert (42)`, `Utkast (3)` i filterpanelet.

### Hva dette gir

- Brukerne kan bytte mellom «Alle», «Mine» og «Mitt kontor»
- Brukerne kan filtrere på status
- Antall per statusverdi i filterpanelet
- Default sortering på sist endret
- Paginering (25 per side)
- Alt bygger på eksisterende kolonner — ingen migrasjoner, ingen joins

### Hva dette ikke gir

- Fritekst
- Geografifiltrering
- Kontorfiltrering (utover «Mitt kontor»-tab)
- Aggregeringer for geografi og kontor
- Sorteringsvalg

### Implementering

```
RekrutteringstreffSokController     (nytt endepunkt)
    ↓
RekrutteringstreffSokService        (bygger dynamisk query)
    ↓
RekrutteringstreffSokRepository     (ren SQL)
    ↓
rekrutteringstreff-tabellen         (eksisterende)
```

Alt bor i eksisterende `rekrutteringstreff-api`. Endepunktet er `POST /api/rekrutteringstreff/sok`.

---

## API-kontrakt

Kontrakten utvides stegvis. Strukturen er kompatibel med alternativ 1 og 2, så frontend kan byttes uten endringer når vi eventuelt oppgraderer.

### Request

```kotlin
data class RekrutteringstreffSokRequest(
    val visningsstatuser: List<Visningsstatus>? = null,
    val fylkesnummer: List<String>? = null,
    val kommunenummer: List<String>? = null,
    val visning: Visning = Visning.ALLE,
    val side: Int = 0,
    val antallPerSide: Int = 25,
)
```

### Respons

```kotlin
data class RekrutteringstreffSokRespons(
    val treff: List<RekrutteringstreffSokTreff>,
    val totaltAntall: Long,
    val side: Int,
    val antallPerSide: Int,
    val statusaggregering: List<FilterValg>,
    val fylkesaggregering: List<FilterValg>,
    val kommuneaggregering: List<FilterValg>,
)
```

Felt som fritekst, kontorer, sortering og ytterligere aggregeringer legges til når de implementeres.

---

## Utvidet versjon: fritekst, geografi, aggregeringer

Når den minimale versjonen er i drift, kan det utvides stegvis.

### Fritekst

**ILIKE-tilnærming (enklest):**

```sql
AND (tittel ILIKE '%' || :fritekst || '%'
     OR beskrivelse ILIKE '%' || :fritekst || '%'
     OR poststed ILIKE '%' || :fritekst || '%')
```

Gir sequential scan, men er raskt nok med under ~10 000 rader. Ingen stemming, ingen typo-toleranse.

**tsvector-tilnærming (bedre kvalitet):**

Legge en `tsvector`-kolonne direkte på `rekrutteringstreff`:

```sql
ALTER TABLE rekrutteringstreff
ADD COLUMN soke_vektor tsvector
GENERATED ALWAYS AS (
    setweight(to_tsvector('norwegian', coalesce(tittel, '')), 'A') ||
    setweight(to_tsvector('norwegian', coalesce(beskrivelse, '')), 'B') ||
    setweight(to_tsvector('norwegian', coalesce(poststed, '')), 'C') ||
    setweight(to_tsvector('norwegian', coalesce(kommune, '')), 'C')
) STORED;

CREATE INDEX rekrutteringstreff_tsv_idx
ON rekrutteringstreff USING gin (soke_vektor);
```

Dette gir stemming for norsk uten egen søketabell. Ulempen er at felter fra andre tabeller (innleggstitler, arbeidsgivernavn) ikke kan inkluderes i en `GENERATED ALWAYS`-kolonne.

For å inkludere relaterte data i fritekst må enten:

- søketabellen innføres (→ alternativ 2)
- eller en join mot `innlegg` gjøres i queryen (fungerer med lavt volum)

### Geografi

Feltene `fylke`, `fylkesnummer`, `kommune` og `kommunenummer` finnes allerede på tabellen. Filtrering er en enkel `WHERE`-clause:

```sql
AND fylkesnummer = ANY(:fylkesnummer)
```

### Join mot innlegg

For fritekst som inkluderer innleggstitler:

```sql
LEFT JOIN innlegg i ON i.treff_id = rt.id
WHERE ... AND i.tittel ILIKE '%' || :fritekst || '%'
```

Fungerer med lavt volum. Legg indeks på `innlegg.treff_id` fra start.

### Aggregeringer

Separate `COUNT`-queryer per dimensjon, som beskrevet i [alternativ 2](2-postgres-med-soketabell-sok.md#aggregeringer). Med joins i stedet for søketabellen blir queryene noe tyngre, men fortsatt raske ved lavt volum.

---

## Styrker

- **Null oppsett.** Ingen migrasjoner, ingen ny tabell, ingen synkroniseringslogikk
- **Alltid konsistent.** Søker alltid mot kildedata — ingen risiko for utdaterte resultater
- **Raskest å komme i gang.** Minimal versjon kan implementeres på noen timer
- **Enkel å forstå.** Standard SQL mot eksisterende tabell

## Svakheter og begrensninger

- **Fritekst uten indeks:** ILIKE gir sequential scan. Raskt nok til ~10 000 rader, men et tydelig tak
- **Ingen stemming/typo-toleranse** med ILIKE-tilnærming
- **Joins ved fritekst** på relaterte tabeller (innlegg, arbeidsgivere) kan overraske ved skjev datafordeling
- **Aggregeringer med joins** er tyngre enn mot en flat tabell
- **Skaleringstak** rundt ~10 000 rader uten søkeindekser

## Migrasjonssti

API-kontrakten (`RekrutteringstreffSokRequest`/`RekrutteringstreffSokRespons`) kan være identisk med alternativ 1 og 2. Å bytte backend fra direkte queries til søketabell eller OpenSearch påvirker ikke frontend.

Stegvis utvidelse:

1. Start med eier + status (denne filen)
2. Legg til fritekst og geografi etter behov
3. Når joins og ILIKE begynner å skalere dårlig → innfør søketabell (alternativ 2) eller OpenSearch (alternativ 1)

---

## TODO: Minimal førsteversjon

### Oppgave 1: Backend

- [ ] Opprett `RekrutteringstreffSokController` med `POST /api/rekrutteringstreff/sok`
- [ ] Opprett `RekrutteringstreffSokService` og `RekrutteringstreffSokRepository`
- [ ] Implementer eierfilter (Alle/Mine/Mitt kontor) mot `eiere`- og `kontorer`-arrays
- [ ] Implementer statusfilter med visningsstatus-mapping (inkl. `SOKNADSFRIST_PASSERT`)
- [ ] Implementer statusaggregering (`COUNT GROUP BY` med visningsstatus-CASE, uten statusfilter)
- [ ] Implementer paginering (offset/limit, 25 per side)
- [ ] Default sortering: `sist_endret DESC`
- [ ] Filtrer alltid bort `SLETTET`
- [ ] Legg til autentisering og rollevalidering

### Oppgave 2: Frontend

- [ ] Nytt SWR-hook `useRekrutteringstreffSøk` som kaller `POST /api/rekrutteringstreff/sok`
- [ ] Tab-rad: Alle / Mine / Mitt kontor
- [ ] Statusfilter (checkboxes)
- [ ] Paginering
- [ ] Synk søkeparametre til URL

### Oppgave 3: Utvidelse (når behovet oppstår)

- [ ] Fritekst (ILIKE eller tsvector)
- [ ] Geografifilter
- [ ] Sorteringsvalg
- [ ] Aggregeringer
