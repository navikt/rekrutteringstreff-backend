## Kort oppsummering av hva vi gjorde for å sette opp federated query for å flytte data fra Postgres til BigQuery

Vi fulgte nais-docen [her](https://docs.knada.io/dataprodukter/dele/dataoverf%C3%B8ring/#federated-query).

I punkt 1 opprettet vi en secret (rekrutteringstreff-api-bigquery). Den brukes av postgres-brukeren som ble opprettet med kommandoen `nais postgres users add <appnavn> <brukernavn> <passord>`.

I punkt 2 ble samme bruker/passord brukt for å sette opp en Cloud SQL Connection (rekrutteringstreff-api).

I punkt 5 opprettet vi en service bruker (bigquery-federated-queries) i toi-prod.

## Skedulerte spørringer

Hver SQL-fil i denne mappa lager sin egen tabell i datasettet `rekrutteringstreff` med `CREATE OR REPLACE TABLE`. Tabellnavnet står i SQL-fila, så `bq`-kommandoene trenger bare spørringen.

| SQL-fil | Tabell | Resource name (skedulert spørring) |
|---------|--------|------------------------------------|
| `rekrutteringstreff-tellinger-per-mnd.sql` | `tellinger_per_mnd` | `projects/565127870017/locations/europe-north1/transferConfigs/6b0b08d4-0000-2a25-981b-14c14ef260d4` |
| `rekrutteringstreff-per-kontor-aggregert.sql` | `per_kontor_aggregert` | `projects/565127870017/locations/europe-north1/transferConfigs/6a10cb02-0000-27ee-99ea-b8db38fb0eca` |

Kjører du en av filene manuelt i BigQuery-konsollen, overskriver den tabellen med ferske tall.

Kommandoene under krever `jq` (`brew install jq`) og kjøres fra rota i repoet.

### Opprette skedulert spørring

Eksempel for `rekrutteringstreff-tellinger-per-mnd.sql`. For en annen fil bytter du `--display_name`, `--schedule` og filnavn.

```bash
bq mk --transfer_config \
  --project_id=toi-prod-324e \
  --location=europe-north1 \
  --data_source=scheduled_query \
  --display_name="rekrutteringstreff-tellinger-per-mnd" \
  --schedule="every 1 hours from 05:00 to 17:00 on mon,tue,wed,thu,fri" \
  --service_account_name="bigquery-federated-queries@toi-prod-324e.iam.gserviceaccount.com" \
  --params="{\"query\":$(jq -Rs . < federated-queries/rekrutteringstreff-tellinger-per-mnd.sql)}"
```

- BigQuery tolker tidspunktene i `--schedule` som UTC og tar ikke hensyn til sommertid. 05:00–17:00 UTC er 06–18 norsk tid om vinteren og 07–19 om sommeren.
- `--service_account_name` er servicebrukeren fra punkt 5. Du må ha rollen Service Account User på den for å kunne bruke den.

### Oppdatere spørringen etter endring i SQL-fila

`rekrutteringstreff-tellinger-per-mnd.sql`:

```bash
bq update --transfer_config \
  --params="{\"query\":$(jq -Rs . < federated-queries/rekrutteringstreff-tellinger-per-mnd.sql)}" \
  projects/565127870017/locations/europe-north1/transferConfigs/6b0b08d4-0000-2a25-981b-14c14ef260d4
```

`rekrutteringstreff-per-kontor-aggregert.sql`:

```bash
bq update --transfer_config \
  --params="{\"query\":$(jq -Rs . < federated-queries/rekrutteringstreff-per-kontor-aggregert.sql)}" \
  projects/565127870017/locations/europe-north1/transferConfigs/6a10cb02-0000-27ee-99ea-b8db38fb0eca
```

`jq -Rs .` gjør hele SQL-fila om til én JSON-streng. `--params` erstatter alle parameterne til den skedulerte spørringen, men schedule og servicebruker endres ikke.

### Andre nyttige kommandoer

Liste skedulerte spørringer og finne `<resource-name>`:

```bash
bq ls --transfer_config --transfer_location=europe-north1 --project_id=toi-prod-324e
```

Se detaljer og neste kjøring:

```bash
bq show --format=prettyjson --transfer_config <resource-name>
```

Endre schedule:

```bash
bq update --transfer_config --schedule="<ny schedule>" <resource-name>
```
