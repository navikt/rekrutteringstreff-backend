# Søkestrategi for rekrutteringstreff

## Problemstilling

Frontend henter i dag alle rekrutteringstreff og gjør filtrering, søk og sortering i klienten. Dette skal flyttes til backend.

Spørsmålet er hvor mye infrastruktur vi trenger for å løse dette.

---

## Tre alternativer

| #   | Alternativ                                                      | Kort beskrivelse                                                     | Nye apper | Ny infrastruktur                    |
| --- | --------------------------------------------------------------- | -------------------------------------------------------------------- | --------- | ----------------------------------- |
| 1   | [OpenSearch](1-opensearch-sok.md)                               | Fullverdig søkemotor med indekser-app og søke-app                    | 2         | OpenSearch-kluster, Kafka-hendelser |
| 2   | [PostgreSQL med søketabell](2-postgres-med-soketabell-sok.md)   | Denormalisert søketabell i eksisterende database, optimert for søk   | 0         | Ingen                               |
| 2.5 | [PostgreSQL med view](2.5-postgres-med-view-sok.md)             | View over eksisterende tabeller, ingen synkronisering eller indekser | 0         | Ingen                               |
| 3   | [PostgreSQL uten søketabell](3-postgres-uten-soketabell-sok.md) | Søk direkte mot eksisterende tabeller med joins                      | 0         | Ingen                               |

---

## Kan vi starte med bare eier + status?

Uavhengig av alternativ er det verdt å vurdere om vi kan starte med en minimal versjon:

**Kun to filtre:**

- **Eierfilter** (tabs): Alle / Mine / Mitt kontor
- **Statusfilter**: Utkast, Publisert, Fullført, Avlyst

**Hva dette gir:**

- Brukerne kan finne «sine» treff og filtrere på status — som dekker det mest akutte behovet
- Antall per statusverdi i filterpanelet (f.eks. «Publisert (42)»)
- Paginering (25 per side)
- Kan implementeres på kort tid med alternativ 3

**Hva det ikke gir:**

- Fritekst (søk på tittel, arbeidsgivernavn osv.)
- Geografifiltrering (fylke, kommune)
- Kontorfiltrering
- Aggregeringer for geografi og kontor
- Sorteringsvalg utover default

Denne minimale versjonen kan bygges med alternativ 3 og utvides stegvis uten å låse oss til noen bestemt retning.

### Felles API-kontrakt

Alle tre alternativer bruker samme request/response-struktur (`RekrutteringstreffSokRequest`/`RekrutteringstreffSokRespons`) med paginering (25 per side). I den minimale versjonen ignorerer backend felt som ikke er støttet ennå (fritekst, geografi, kontorer), og aggregeringer returneres som tomme lister. Frontend kan dermed bygges én gang og fungerer uendret uavhengig av hvilket alternativ backend velger.

---

## Sammenligning

| Dimensjon                  | 1. OpenSearch                            | 2. Postgres med søketabell | 2.5 Postgres med view       | 3. Postgres uten søketabell  |
| -------------------------- | ---------------------------------------- | -------------------------- | --------------------------- | ---------------------------- |
| **Nye apper**              | 2 (indekser + søk)                       | 0                          | 0                           | 0                            |
| **Ny infrastruktur**       | OpenSearch-kluster, Kafka                | Ingen                      | Ingen                       | Ingen                        |
| **Tid til første versjon** | Høy                                      | Middels                    | Lav                         | Lav                          |
| **Fritekst**               | BM25(keyword search), norsk analyzer     | tsvector + pg_trgm         | Ikke støttet                | ILIKE (ok til ~10k rader)    |
| **Typo-toleranse**         | Innebygd                                 | pg_trgm (brukbart)         | Ingen                       | Ingen                        |
| **Relevans**               | Sterk (BM25 + boost)                     | Brukbar (ts_rank)          | Ingen                       | Ingen                        |
| **Filtre**                 | term/terms, svært raskt                  | B-tree + GIN, raskt        | Kildetabellens indekser     | Joins, raskt ved lavt volum  |
| **Aggregeringer**          | aggs + post_filter i ett kall            | N+1 COUNT GROUP BY         | N+1 COUNT GROUP BY via view | N+1 COUNT GROUP BY med joins |
| **Konsistens**             | Eventual consistence(~10–15 sek)         | Sterk (synkron)            | Sterk (alltid kildedata)    | Sterk (alltid kildedata)     |
| **Reindeksering**          | Dual-write, alias-swap, deploy           | truncate + batch-insert    | CREATE OR REPLACE VIEW      | Ikke nødvendig               |
| **Vedlikeholdskostnad**    | Høy (mapping, settings, lyttere, alerts) | Middels (søketabell-synk)  | Lav (ingen synk)            | Lav                          |
| **Skaleringstak**          | Nesten ubegrenset                        | 100k+ med riktige indekser | ~10k uten egne indekser     | ~10k uten søkeindekser       |
| **Mønstergjenbruk**        | Kandidat/stilling bruker samme           | Ingen eksisterende         | Ingen eksisterende          | Ingen eksisterende           |

---

## Stegvis tilnærming

```
Steg 1 (valgt)            Steg 2 (ved behov)           Steg 3 (om nødvendig)
─────────────────────     ──────────────────────────    ───────────────────────
Alt. 2.5: View            Alt. 2: Søketabell           Alt. 1: OpenSearch

Eier + status + geografi  + fritekst (tsvector/trgm)   Fullverdig søk
View over eksisterende    + egne indekser              med søkemotor
tabeller                  + sorteringsvalg
                          + kontorfilter
```

**Steg 1** — alternativ 2.5 — gir eier-, status- og geografifiltrering via et view. Ingen synkronisering, ingen indekser, ingen fritekst. Dekker det mest akutte behovet med minimal innsats.

**Steg 2** utvider med fritekst og flere filtre via alternativ 2 (søketabell). Naturlig overgang fra viewet: vi materialiserer den flate strukturen til en egen tabell og legger til tsvector, pg_trgm og indekser.

**Steg 3** er kun aktuelt hvis vi opplever at PostgreSQL ikke dekker behovet — enten på grunn av volum, søkekvalitet eller at mønstergjenbruk med kandidat/stilling veier tungt nok.

---

## Beslutning

**Alternativ 2.5 — PostgreSQL med view.**

Vi starter med et view over eksisterende tabeller som gir eier-, status- og geografifiltrering (fylke/kommune). Vi venter med fritekst.

Begrunnelse:

- Dekker de viktigste behovene: finne treff man er eier/medeier av, filtrere på status og område
- Ingen ny infrastruktur, ingen synkroniseringslogikk, ingen indekser å vedlikeholde
- View er alltid konsistent — leser direkte fra kildetabellene
- Naturlig migrasjonssti til alternativ 2 (søketabell) når vi trenger fritekst, og videre til alternativ 1 (OpenSearch) hvis PostgreSQL ikke dekker behovet
- API-kontrakten er den samme uavhengig av backend-valg, så frontend trenger ikke endres ved oppgradering
