# Sammenligning av søke-API-er

Sammenligning av rekrutteringstreff-søk, stillingssøk og kandidatsøk — hva er likt, hva er ulikt, og hva som eventuelt kan tilpasses.

---

## Oversikt

| Egenskap                | Rekrutteringstreff-søk        | Stillingssøk                        | Kandidatsøk                      |
| ----------------------- | ----------------------------- | ----------------------------------- | -------------------------------- |
| **HTTP-metode**         | `GET`                         | `POST`                              | `POST`                           |
| **Backend-URL**         | `/api/rekrutteringstreff/sok` | `/stilling/_search` (Elasticsearch) | `/api/kandidatsok/{portefølje}`  |
| **Frontend proxy**      | `/api/rekrutteringstreff/sok` | `/api/stillings-sok`                | `/api/kandidat-sok/{portefølje}` |
| **Parameterplassering** | Query params                  | Request body (ES-query)             | Query params + request body      |
| **SWR-hook**            | `useSWRGet`                   | `useSWRPost`                        | `useSWRPost`                     |
| **Backend-teknologi**   | PostgreSQL                    | Elasticsearch/OpenSearch            | OpenSearch                       |

---

## Paginering

| Egenskap            | Rekrutteringstreff-søk | Stillingssøk         | Kandidatsøk              |
| ------------------- | ---------------------- | -------------------- | ------------------------ |
| **Sideparam**       | `side` (query param)   | `side` (browser-URL) | `side` (query param)     |
| **Indeksering**     | 1-indeksert            | 1-indeksert          | 1-indeksert              |
| **Antall per side** | `antallPerSide=20`     | `size=20` (body)     | 25 (hardkodet i backend) |
| **Param-navn**      | `antallPerSide`        | `size` (ES-felt)     | Ikke konfigurerbart      |
| **Total-respons**   | `antallTotalt`         | ES `hits.total`      | `antallTotalt`           |

### Observasjoner

- Sideindeksering er konsekvent (1-indeksert) på tvers av alle tre.
- Rekrutteringstreff og kandidatsøk bruker begge `antallTotalt` i respons.
- Rekrutteringstreff er eneste som lar frontend styre antall per side via `antallPerSide`.

---

## Sortering

| Egenskap       | Rekrutteringstreff-søk                | Stillingssøk             | Kandidatsøk       |
| -------------- | ------------------------------------- | ------------------------ | ----------------- |
| **Param-navn** | `sortering`                           | `sortering`              | `sortering`       |
| **Verdier**    | `sist_oppdaterte`, `nyeste`, `eldste` | `publiseringsdato` m.fl. | `nyeste`, `score` |
| **Casing**     | lowercase                             | camelCase                | lowercase         |
| **Default**    | `sist_oppdaterte`                     | `publiseringsdato`       | `nyeste`          |

### Observasjoner

- Param-navnet `sortering` er konsekvent på tvers.
- Rekrutteringstreff og kandidatsøk bruker begge lowercase verdier. Stillingssøk bruker camelCase.

---

## Portefølje / visning

| Egenskap       | Rekrutteringstreff-søk                           | Stillingssøk                                        | Kandidatsøk                                                           |
| -------------- | ------------------------------------------------ | --------------------------------------------------- | --------------------------------------------------------------------- |
| **Param-navn** | `visning`                                        | `portefolje`                                        | Del av URL-path (`/minebrukere`, `/mittkontor`, `/alle` osv.)         |
| **Plassering** | Query param                                      | Browser-URL param → ES-query                        | URL-path-segment                                                      |
| **Verdier**    | `alle`, `mine`, `mitt_kontor`, `valgte_kontorer` | `intern`, `visMine`, `mittKontor`, `arbeidsplassen` | `minebrukere`, `mittkontor`, `minekontorer`, `valgtekontorer`, `alle` |
| **Casing**     | lowercase                                        | camelCase                                           | lowercase (URL-path)                                                  |

### Observasjoner

- Kandidatsøk bruker separate URL-paths per portefølje — mest RESTful, men fem separate endepunkter.
- Stillingssøk og rekrutteringstreff bruker param, men med ulikt navn (`portefolje` vs `visning`).
- Rekrutteringstreff og kandidatsøk bruker begge lowercase verdier.
- Kontorfilter: rekrutteringstreff bruker `kontorer` (query param), kandidatsøk bruker `valgtKontor` (body), stillingssøk bygger det inn i ES-query.

---

## Statusfiltrering

| Egenskap       | Rekrutteringstreff-søk                                              | Stillingssøk            | Kandidatsøk |
| -------------- | ------------------------------------------------------------------- | ----------------------- | ----------- |
| **Param-navn** | `statuser`                                                          | `statuser`              | N/A         |
| **Format**     | Kommaseparert query param                                           | Array i browser-URL     | —           |
| **Verdier**    | `utkast`, `publisert`, `soknadsfrist_passert`, `fullfort`, `avlyst` | `aktiv`, `utløpt` m.fl. | —           |

### Observasjoner

- Rekrutteringstreff og stillingssøk bruker begge `statuser` som param-navn.
- Rekrutteringstreff og stillingssøk bruker begge lowercase verdier.
- Rekrutteringstreff bruker `fullfort` (med o) i URL-verdien — mappet til enum `FULLFØRT` i backend.
- Kandidatsøk har ikke statusfiltrering.

---

## Fritekstsøk

| Egenskap       | Rekrutteringstreff-søk | Stillingssøk             | Kandidatsøk         |
| -------------- | ---------------------- | ------------------------ | ------------------- |
| **Støtte**     | Nei (ennå)             | Ja                       | Ja                  |
| **Param-navn** | —                      | `fritekst` (lokal state) | `fritekst` (i body) |
| **Plassering** | —                      | Lokal state → ES-query   | Request body        |

### Observasjon

- `fritekst` er konsekvent mellom stillingssøk og kandidatsøk. Bør gjenbrukes når rekrutteringstreff får fritekstsøk.

---

## Aggregeringer / statusopptelling

| Egenskap         | Rekrutteringstreff-søk       | Stillingssøk               | Kandidatsøk |
| ---------------- | ---------------------------- | -------------------------- | ----------- |
| **Respons-felt** | `statusaggregering` (array)  | ES `aggregations` (nested) | Nei         |
| **Format**       | `[{ verdi, antall }]`        | ES aggregation buckets     | —           |
| **Separat kall** | Nei, inkludert i søkerespons | Nei, kombinert i ES-query  | —           |

---

## HTTP-metode og parameterplassering

| Aspekt         | Rekrutteringstreff-søk | Stillingssøk          | Kandidatsøk  |
| -------------- | ---------------------- | --------------------- | ------------ |
| **Metode**     | `GET`                  | `POST`                | `POST`       |
| **Filtre**     | Query params           | Body (ES-query)       | Body (JSON)  |
| **Sortering**  | Query param            | Body (ES `sort`)      | Query param  |
| **Paginering** | Query params           | Body (`from`, `size`) | Query params |

### Observasjoner

- Stillingssøk bruker POST fordi den sender en Elasticsearch-query direkte.
- Kandidatsøk bruker POST for filtre i body, men paginering og sortering i query params — en hybrid.
- Rekrutteringstreff bruker GET med alt i query params. Naturlig og RESTful for PostgreSQL-basert søk med få filterdimensjoner.

---

## Oppsummering: konsistens og avvik

### Konsekvent på tvers

| Aspekt             | Verdi/mønster                                     |
| ------------------ | ------------------------------------------------- |
| Sideindeksering    | 1-indeksert                                       |
| Sideparam          | `side`                                            |
| Sorteringsparam    | `sortering`                                       |
| Sorteringscasing   | lowercase (rekrutteringstreff + kandidatsøk)      |
| Visningscasing     | lowercase (rekrutteringstreff + kandidatsøk)      |
| Statusfiltercasing | lowercase (rekrutteringstreff + stillingssøk)     |
| Totalfelt          | `antallTotalt` (rekrutteringstreff + kandidatsøk) |
| Statusfilterparam  | `statuser` (rekrutteringstreff + stillingssøk)    |
| Fritekstsøk (plan) | `fritekst` (stillingssøk + kandidatsøk)           |

### Gjenstående avvik

| #   | Hva                                       | Vurdering                                                                                                         |
| --- | ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| 1   | Porteføljenavn: `visning` vs `portefolje` | `visning` er mer dekkende for rekrutteringstreff. `portefolje` er etablert i stillingssøk. Begge fungerer.        |
| 2   | Kontorparam: `kontorer` vs `valgtKontor`  | `kontorer` (rekrutteringstreff) er klarere enn `valgtKontor` (kandidatsøk). Behold som det er.                    |
| 3   | Antall per side som param                 | Bare rekrutteringstreff eksponerer `antallPerSide`. Gir fleksibilitet.                                            |
| 4   | HTTP-metode (GET vs POST)                 | GET er naturlig for rekrutteringstreff. POST-mønsteret i de andre skyldes ES/OpenSearch og mange filterparametre. |
