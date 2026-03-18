# Sammenligning av søke-API-er

Sammenligning av rekrutteringstreff-søk, stillingssøk og kandidatsøk for å identifisere inkonsistenser og muligheter for å gjøre rekrutteringstreff-søk mer konsekvent med de etablerte mønstrene.

---

## Oversikt

| Egenskap              | Rekrutteringstreff-søk         | Stillingssøk                       | Kandidatsøk                          |
| --------------------- | ------------------------------ | ---------------------------------- | ------------------------------------ |
| **HTTP-metode**       | `GET`                          | `POST`                             | `POST`                               |
| **Backend-URL**       | `/api/rekrutteringstreff/sok`  | `/stilling/_search` (Elasticsearch)| `/api/kandidatsok/{portefølje}`      |
| **Frontend proxy**    | `/api/rekrutteringstreff/sok`  | `/api/stillings-sok`               | `/api/kandidat-sok/{portefølje}`     |
| **Parameterplassering** | Query params                 | Request body (ES-query)            | Query params + request body          |
| **SWR-hook**          | `useSWRGet`                    | `useSWRPost`                       | `useSWRPost`                         |
| **Backend-teknologi** | PostgreSQL                     | Elasticsearch/OpenSearch           | OpenSearch                           |

---

## Paginering

| Egenskap          | Rekrutteringstreff-søk  | Stillingssøk            | Kandidatsøk              |
| ----------------- | ----------------------- | ----------------------- | ------------------------ |
| **Sideparam**     | `side` (query param)    | `side` (browser-URL)    | `side` (query param)     |
| **Indeksering**   | 0-indeksert             | 1-indeksert             | 1-indeksert              |
| **Param til API** | `side=0`                | `from=0` (beregnet)     | `side=1`                 |
| **Antall per side** | `antallPerSide=25`    | `size=20` (body)        | 25 (hardkodet i backend) |
| **Param-navn**    | `antallPerSide`         | `size` (ES-felt)        | Ikke konfigurerbart      |
| **Total-respons** | `totaltAntall`          | ES `hits.total`         | `antallTotalt`           |

### Observasjoner paginering

- **Sideindeksering:** Rekrutteringstreff bruker 0-indeksert `side`, mens kandidatsøk og stillingssøk bruker 1-indeksert. 1-indeksert er mer intuitivt for frontend.
- **Antall per side:** Rekrutteringstreff og kandidatsøk bruker 25, stillingssøk bruker 20. Rekrutteringstreff er eneste som lar frontend styre dette via `antallPerSide`.
- **Totalfelt:** Rekrutteringstreff bruker `totaltAntall`, kandidatsøk bruker `antallTotalt`. Bør velge ett navn.

---

## Sortering

| Egenskap            | Rekrutteringstreff-søk                            | Stillingssøk                    | Kandidatsøk                   |
| ------------------- | ------------------------------------------------- | ------------------------------- | ----------------------------- |
| **Param-navn**      | `sortering`                                       | `sortering`                     | `sortering`                   |
| **Verdier**         | `SIST_OPPDATERTE`, `NYESTE`, `ELDSTE`             | `publiseringsdato` m.fl.        | `nyeste`, `score`             |
| **Casing**          | UPPER_SNAKE_CASE                                  | camelCase                       | lowercase                     |
| **Default**         | `SIST_OPPDATERTE`                                 | `publiseringsdato`              | `nyeste`                      |

### Observasjoner sortering

- **Param-navnet `sortering`** er konsekvent på tvers — bra!
- **Casing:** Rekrutteringstreff bruker `UPPER_SNAKE_CASE`, kandidatsøk bruker `lowercase`, stillingssøk bruker `camelCase`. Ingen konsistens.
- **Innhold:** Kandidatsøk bruker beskrivende verdier (`nyeste`, `score`). Stillingssøk bruker feltnavn (`publiseringsdato`). Rekrutteringstreff bruker beskrivende oppslagsord (`NYESTE`, `ELDSTE`, `SIST_OPPDATERTE`).

---

## Portefølje / visning

Alle tre søkene har konseptet «vis mine / mitt kontor / alle», men løser det ulikt:

| Egenskap              | Rekrutteringstreff-søk                                  | Stillingssøk                                          | Kandidatsøk                                                         |
| --------------------- | ------------------------------------------------------- | ----------------------------------------------------- | ------------------------------------------------------------------- |
| **Param-navn**        | `visning`                                               | `portefolje`                                          | Del av URL-path (`/minebrukere`, `/mittkontor`, `/alle` osv.)       |
| **Plassering**        | Query param                                             | Browser-URL param → ES-query                          | URL-path-segment                                                    |
| **Verdier**           | `ALLE`, `MINE`, `MITT_KONTOR`, `VALGTE_KONTORER`       | `intern`, `visMine`, `mittKontor`, `arbeidsplassen`   | `minebrukere`, `mittkontor`, `minekontorer`, `valgtekontorer`, `alle` |
| **Casing**            | UPPER_SNAKE_CASE                                        | camelCase                                             | lowercase (URL-path)                                                |

### Observasjoner portefølje

- Kandidatsøk bruker separate URL-paths per portefølje — dette er mest RESTful, men gjør det til fem separate endepunkter.
- Stillingssøk og rekrutteringstreff bruker param, men med ulikt navn (`portefolje` vs `visning`).
- Kontorfilter: Rekrutteringstreff bruker `kontorer` query param, kandidatsøk sender `valgtKontor` i body, stillingssøk bygger det inn i ES-query.

---

## Statusfiltrering

| Egenskap            | Rekrutteringstreff-søk                                              | Stillingssøk                  | Kandidatsøk   |
| ------------------- | ------------------------------------------------------------------- | ----------------------------- | ------------- |
| **Param-navn**      | `visningsstatuser`                                                  | `statuser`                    | N/A           |
| **Format**          | Kommaseparert query param                                           | Array i browser-URL           | —             |
| **Verdier**         | `UTKAST`, `PUBLISERT`, `SOKNADSFRIST_PASSERT`, `FULLFORT`, `AVLYST`| `aktiv`, `utløpt` m.fl.       | —             |

### Observasjoner statusfiltrering

- Stillingssøk bruker `statuser`, rekrutteringstreff bruker `visningsstatuser`. `statuser` er enklere og mer konsistent.
- Kandidatsøk har ikke statusfiltrering.

---

## Fritekstsøk

| Egenskap            | Rekrutteringstreff-søk  | Stillingssøk                | Kandidatsøk               |
| ------------------- | ----------------------- | --------------------------- | -------------------------- |
| **Støtte**          | Nei (ennå)              | Ja                          | Ja                         |
| **Param-navn**      | —                       | `fritekst` (lokal state)    | `fritekst` (i body)        |
| **Plassering**      | —                       | Lokal state → ES-query      | Request body               |

### Observasjon

- Param-navnet `fritekst` er konsekvent mellom stillingssøk og kandidatsøk. Bør gjenbrukes når rekrutteringstreff får fritekstsøk.

---

## Aggregeringer / statusopptelling

| Egenskap             | Rekrutteringstreff-søk                    | Stillingssøk                     | Kandidatsøk  |
| -------------------- | ----------------------------------------- | -------------------------------- | ------------ |
| **Respons-felt**     | `statusaggregering` (array)               | ES `aggregations` (nested)       | Nei          |
| **Format**           | `[{ verdi, antall }]`                     | ES aggregation buckets           | —            |
| **Separat kall**     | Nei, inkludert i søkerespons              | Nei, kombinert i ES-query        | —            |

---

## HTTP-metode og parameterplassering

| Aspekt                | Rekrutteringstreff-søk                | Stillingssøk                    | Kandidatsøk                    |
| --------------------- | ------------------------------------- | ------------------------------- | ------------------------------ |
| **Metode**            | `GET`                                 | `POST`                          | `POST`                         |
| **Filtre**            | Query params                          | Body (ES-query)                 | Body (JSON)                    |
| **Sortering**         | Query param                           | Body (ES `sort`)                | Query param                    |
| **Paginering**        | Query params                          | Body (`from`, `size`)           | Query params                   |

### Observasjoner HTTP-metode

- Stillingssøk bruker POST fordi den sender en Elasticsearch-query direkte — dette er spesielt for ES og ikke et mønster vi bør kopiere.
- Kandidatsøk bruker POST for filtre i body, men paginering og sortering i query params — en hybrid.
- Rekrutteringstreff bruker GET med alt i query params. For et PostgreSQL-basert søk med få filterdimensjoner er dette naturlig og RESTful. GET er riktig valg her.
- **Konklusjon:** GET er passende for rekrutteringstreff så lenge filterdimensjonene forblir enkle. Kandidatsøk bruker POST fordi antall filterparametre er stort. Stillingssøk bruker POST pga. ES. Ingen grunn til å endre rekrutteringstreff til POST.

---

## Oppsummering: mulige justeringer

### Bør vurderes (lav kostnad, høy konsistens)

| # | Hva                                           | Fra (nå)                     | Til (konsistent)      | Begrunnelse                                                       |
|---|-----------------------------------------------|------------------------------|-----------------------|-------------------------------------------------------------------|
| 1 | Sideindeksering                               | 0-indeksert                  | 1-indeksert           | Kandidatsøk og stillingssøk bruker 1-indeksert                    |
| 2 | Totalfelt i respons                           | `totaltAntall`               | `antallTotalt`        | Matcher kandidatsøk                                               |
| 3 | Sorteringsverdier casing                      | `SIST_OPPDATERTE` osv.       | `nyeste` osv.         | Kandidatsøk bruker lowercase, men dette er smakssak               |
| 4 | Statusfilterparam                             | `visningsstatuser`           | `statuser`            | Enklere, matcher stillingssøk                                     |
| 5 | Fritekstsøk (når det kommer)                  | —                            | `fritekst`            | Gjenbruk etablert paramternavn                                    |

### Kan vurderes (krever mer arbeid eller er smakssak)

| # | Hva                                           | Diskusjon                                                                 |
|---|-----------------------------------------------|---------------------------------------------------------------------------|
| 6 | Porteføljenavn: `visning` vs `portefolje`     | `visning` er mer dekkende for rekrutteringstreff, men `portefolje` er etablert i stillingssøk. Begge er OK. |
| 7 | Kontorparam: `kontorer` vs `valgtKontor`      | `kontorer` (rekrutteringstreff) er klarere enn `valgtKontor` (kandidatsøk). Behold som det er. |
| 8 | Antall per side som param                     | Bare rekrutteringstreff eksponerer dette. Kan beholdes — gir fleksibilitet. |

### Bør ikke endres

| # | Hva                         | Hvorfor                                                                     |
|---|-----------------------------|-----------------------------------------------------------------------------|
| 9 | HTTP-metode (GET)           | Naturlig for enkle filtre mot PostgreSQL. POST-mønsteret skyldes ES/OpenSearch. |
| 10| Parameterplassering         | Query params passer for GET. Ingen grunn til å flytte til body.             |
