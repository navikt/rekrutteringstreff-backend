## Kort oppsummering av hva vi gjorde for å sette opp federated query for å flytte data fra Postgres til BigQuery

Vi fulgte nais-docen [her](https://docs.knada.io/dataprodukter/dele/dataoverf%C3%B8ring/#federated-query).

I punkt 1 opprettet vi en secret (rekrutteringstreff-api-bigquery). Den brukes av postgres-brukeren som ble opprettet med kommandoen `nais postgres users add <appnavn> <brukernavn> <passord>`.

I punkt 2 ble samme bruker/passord brukt for å sette opp en Cloud SQL Connection (rekrutteringstreff-api).

I punkt 5 opprettet vi en service bruker (bigquery-federated-queries) i toi-prod.
