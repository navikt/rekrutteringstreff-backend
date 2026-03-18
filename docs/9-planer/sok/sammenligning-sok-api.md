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

| Egenskap            | Rekrutteringstreff-søk     | Stillingssøk         | Kandidatsøk              |
| ------------------- | -------------------------- | -------------------- | ------------------------ |
| **Sideparam**       | `side` (query param)       | `side` (browser-URL) | `side` (query param)     |
| **Indeksering**     | 1-indeksert                | 1-indeksert          | 1-indeksert              |
| **Antall per side** | Default `antallPerSide=20` | `size=20` (body)     | 25 (hardkodet i backend) |
| **Param-navn**      | `antallPerSide` (API)      | `size` (ES-felt)     | Ikke konfigurerbart      |
| **Total-respons**   | `antallTotalt`             | ES `hits.total`      | `antallTotalt`           |

### Observasjoner

- Sideindeksering er konsekvent (1-indeksert) på tvers av alle tre.
- Rekrutteringstreff og kandidatsøk bruker begge `antallTotalt` i respons.
- Rekrutteringstreff-API-et støtter `antallPerSide`, men dagens frontend sender ikke denne parameteren og bruker derfor default på 20.

---

## Sortering

| Egenskap       | Rekrutteringstreff-søk                | Stillingssøk                                                                       | Kandidatsøk       |
| -------------- | ------------------------------------- | ---------------------------------------------------------------------------------- | ----------------- |
| **Param-navn** | `sortering`                           | `sortering`                                                                        | `sortering`       |
| **Verdier**    | `sist_oppdaterte`, `nyeste`, `eldste` | `publiseringsdato`, `utløpsdato` (og `relevans` i querybygger)                     | `nyeste`, `score` |
| **Casing**     | lowercase                             | lowercase                                                                          | lowercase         |
| **Default**    | `sist_oppdaterte`                     | `publiseringsdato` i frontend-state, som gir publiseringsdato-sortering i ES-query | `nyeste`          |

### Observasjoner

- Param-navnet `sortering` er konsekvent på tvers.
- Alle tre bruker lowercase sorteringsverdier, men stillingssøk har samtidig camelCase i porteføljeverdier (`visMine`, `mittKontor`).

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
- Kontorfilter: rekrutteringstreff bruker `kontorer` (query param), kandidatsøk bruker `valgtKontor` i request body, stillingssøk bygger det inn i ES-query.
- Rekrutteringstreff støtter `valgte_kontorer` i API-et, og frontend har en egen `Velg kontor`-visning for dette. Den er rollebegrenset til arbeidsgiverrettet rolle.

---

## Statusfiltrering

| Egenskap       | Rekrutteringstreff-søk                                              | Stillingssøk                                                                                       | Kandidatsøk |
| -------------- | ------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------- | ----------- |
| **Param-navn** | `statuser`                                                          | `statuser`                                                                                         | N/A         |
| **Format**     | Kommaseparert query param                                           | Array i browser-URL                                                                                | —           |
| **Verdier**    | `utkast`, `publisert`, `soknadsfrist_passert`, `fullfort`, `avlyst` | `Åpen for søkere`, `Stengt for søkere`, `Utløpt - Stengt for søkere`, `Fullført`, `Ikke publisert` | —           |

### Observasjoner

- Rekrutteringstreff og stillingssøk bruker begge `statuser` som param-navn.
- Rekrutteringstreff bruker lowercase URL-verdier, mens stillingssøk i dag lagrer visningsnavn i browser-state/querystring.
- Rekrutteringstreff bruker `fullfort` (med o) i URL-verdien. I søk-API-et er dette visningsstatusen `FULLFORT`, som avledes fra domenestatusen `FULLFØRT` i søke-viewet.
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

### Presiseringer

- Rekrutteringstreff ignorerer `kontorer` når `visning=mitt_kontor`.
- Rekrutteringstreff med `visning=valgte_kontorer` og uten `kontorer`-param returnerer i dag alle treff.
- Kandidatsøk styres primært av path-segment for portefølje, men frontend holder også `portefolje` som browser-state og sender `portefølje` i request body.

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
| Totalfelt          | `antallTotalt` (rekrutteringstreff + kandidatsøk) |
| Statusfilterparam  | `statuser` (rekrutteringstreff + stillingssøk)    |
| Fritekstsøk (plan) | `fritekst` (stillingssøk + kandidatsøk)           |

### Gjenstående avvik

| #   | Hva                                       | Vurdering                                                                                                         |
| --- | ----------------------------------------- | ----------------------------------------------------------------------------------------------------------------- |
| 1   | Porteføljenavn: `visning` vs `portefolje` | `visning` er mer dekkende for rekrutteringstreff. `portefolje` er etablert i stillingssøk. Begge fungerer.        |
| 2   | Kontorparam: `kontorer` vs `valgtKontor`  | `kontorer` (rekrutteringstreff) er klarere enn `valgtKontor` (kandidatsøk). Behold som det er.                    |
| 3   | Antall per side som param                 | Bare rekrutteringstreff-API-et eksponerer `antallPerSide`. Dagens frontend bruker likevel fast default på 20.     |
| 4   | HTTP-metode (GET vs POST)                 | GET er naturlig for rekrutteringstreff. POST-mønsteret i de andre skyldes ES/OpenSearch og mange filterparametre. |
