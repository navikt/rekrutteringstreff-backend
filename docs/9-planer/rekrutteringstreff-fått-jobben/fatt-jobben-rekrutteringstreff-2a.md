# Plan: Registrering av «fått jobben» i rekrutteringstreff (alternativ 2A)

Implementasjonsplan for løsningen som er valgt i [vurdering-fatt-jobben-statistikk-rekrutteringstreff.md](./vurdering-fatt-jobben-statistikk-rekrutteringstreff.md), alternativ 2A: markedskontakt registrerer «fått jobben» direkte fra jobbsøkerlisten i et rekrutteringstreff. `rekrutteringstreff-api` eier hele løpet og publiserer en Rapids-melding som dagens `statistikk-api` lytter på.

## Kjernebeslutninger

| Tema                       | Beslutning                                                                                                                                                         | Implementasjonskonsekvens                                                                                                                               |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Kategori i Avro            | Gjenbruke `stillingskategori = FORMIDLING` i v1. Egen `REKRUTTERINGSTREFF`-verdi vurderes senere.                                                                  | Ingen endring hos `datavarehus-statistikk` ved utrulling. Treff er ikke skillbar fra etterregistrering eksternt før migrering.                          |
| Synlighet av treff internt | `statistikk-api` skal kunne skille treff fra ordinær etterregistrering i sine egne tabeller og aggregater.                                                         | Ny kolonne `rekrutteringstreff_id` på `kandidatutfall`. Feltet propageres fra Rapids-meldingen og lagres ved siden av eksisterende felt.                |
| Kandidatliste/stilling     | Det opprettes ikke noen formidlingsstilling. `stillingsId` og `kandidatlisteId` er ikke med i den nye meldingen og settes ikke i `kandidatutfall` for treff-rader. | Stilling-api og kandidat-api berøres ikke i v1. Kolonner i `kandidatutfall` for `stillingsId`/`kandidatlisteId` er allerede nullable.                   |
| Angring                    | Ikke støttet i v1.                                                                                                                                                 | Ingen `FATT_JOBBEN_FJERNET`-hendelse i denne iterasjonen. Datamodellen designes likevel slik at det kan legges til uten migrering av eksisterende data. |
| Idempotens                 | En jobbsøker kan ha maks én aktiv `FATT_JOBBEN`-hendelse per rekrutteringstreff.                                                                                   | Unik constraint i `rekrutteringstreff-api` + sjekk i service før hendelse skrives. Endepunktet returnerer `409` ved duplikat.                           |
| Utsending til statistikk   | Egen scheduler i `rekrutteringstreff-api` plukker uutsendte hendelser og publiserer på Rapids. Retries med eksponentiell backoff.                                  | Ny tabell `jobbsoker_fatt_jobben_utsending` og ny scheduler. Sender via eksisterende Rapids-tilkobling.                                                 |

## Hendelser

`JobbsøkerHendelsestype` utvides med `FATT_JOBBEN`. Eksisterende hendelsestyper er uendret.

| Hendelsestype | Trigger                                                                        | `hendelse_data`                                                                                  |
| ------------- | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------ |
| `FATT_JOBBEN` | Markedskontakt bekrefter «Registrer fått jobben» fra jobbsøkerlisten i treffet | JSON-snapshot: `registrertAvNavIdent`, `registrertAvNavKontor`, `tidspunkt`, `arbeidsgiverOrgnr` |

## Database

### Endringer i `rekrutteringstreff-api`

```mermaid
erDiagram
    jobbsoker ||--o{ jobbsoker_hendelse : logger
    jobbsoker_hendelse ||--o| jobbsoker_fatt_jobben_utsending : "0..1"
    jobbsoker_hendelse {
        bigserial jobbsoker_hendelse_id PK
        bigint jobbsoker_id FK
        text hendelsestype "... | FATT_JOBBEN"
        jsonb hendelse_data
        timestamptz tidspunkt
    }
    jobbsoker_fatt_jobben_utsending {
        bigserial id PK
        bigint jobbsoker_hendelse_id FK "unik"
        timestamptz sendt_tidspunkt
    }
```

#### Flyway-migrasjon (treff-api)

Samme mønster som `aktivitetskort_polling`: én rad per hendelse, `sendt_tidspunkt NULL` betyr usendt. Rader som mangler i tabellen er ikke plukket opp ennå; rader med `sendt_tidspunkt IS NOT NULL` er ferdig sendt.

```sql
CREATE TABLE jobbsoker_fatt_jobben_utsending (
    id                    bigserial PRIMARY KEY,
    jobbsoker_hendelse_id bigint                   NOT NULL REFERENCES jobbsoker_hendelse(jobbsoker_hendelse_id),
    sendt_tidspunkt       timestamp with time zone,
    CONSTRAINT jobbsoker_fatt_jobben_utsending_hendelse_fk
        FOREIGN KEY (jobbsoker_hendelse_id) REFERENCES jobbsoker_hendelse(jobbsoker_hendelse_id)
);

CREATE UNIQUE INDEX idx_jobbsoker_fatt_jobben_utsending_hendelse
    ON jobbsoker_fatt_jobben_utsending(jobbsoker_hendelse_id);
```

### Endringer i `statistikk-api`

```sql
ALTER TABLE kandidatutfall ADD COLUMN rekrutteringstreff_id uuid;
CREATE INDEX idx_kandidatutfall_rekrutteringstreff ON kandidatutfall(rekrutteringstreff_id)
    WHERE rekrutteringstreff_id IS NOT NULL;
```

Feltet er `NULL` for alle eksisterende rader og for utfall som ikke kommer fra et treff. Det brukes kun internt — Avro-meldingen videre til `datavarehus-statistikk` påvirkes ikke.

## API i `rekrutteringstreff-api`

| Endepunkt                                                                 | Beskrivelse                                                                                                                                                                                                                              |
| ------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST /api/rekrutteringstreff/{id}/jobbsoker/{personTreffId}/fatt-jobben` | Registrerer at jobbsøkeren har fått jobben. Krever rolle `ARBEIDSGIVER_RETTET` + eier/utvikler. Returnerer `201`. `409` ved duplikat. `409` hvis treffet ikke er i `FULLFORT`-status. `409` hvis jobbsøkeren allerede har `FATT_JOBBEN`. |

Body (forslag):

```kotlin
data class FattJobbenInputDto(
    val arbeidsgiverOrgnr: String
)
```

`personTreffId` er i URL-stien. `aktørId` (og eventuelt fødselsnummer ved behov) slås opp server-side fra `personTreffId` — ingen personidentifikator i request body. `registrertAvNavIdent`/`registrertAvNavKontor` hentes fra token, `tidspunkt` settes server-side.

### Uediterbar status etter `FATT_JOBBEN`

Når `FATT_JOBBEN`-hendelsen er registrert, låses jobbsøkeren i treffet:

- Jobbsøkeren kan **ikke** slettes fra treffet.
- Ingen videre statushendelser kan registreres (verken fra frontend eller API).
- Scheduleren kan sende Rapids-meldingen på nytt ved feil, men endepunktet avviser et nytt `POST`-kall.

Service-laget sjekker om `FATT_JOBBEN` eksisterer for `personTreffId` i gjeldende treff før enhver muterende operasjon og returnerer `409` med forklarende `feil`-felt.

## Rapids-melding

Eget event `kandidat_v2.RekrutteringstreffFåttJobben` som kun `rekrutteringstreff-api` publiserer og `statistikk-api` lytter på. Ingen gjenbruk av eksisterende lytter — ingen syntetiske ID-er, ingen `stillingsinfo`-wrapper.

| Felt                    | Verdi                                                                     |
| ----------------------- | ------------------------------------------------------------------------- |
| `@event_name`           | `kandidat_v2.RekrutteringstreffFåttJobben`                                |
| `aktørId`               | Jobbsøkerens aktør-ID                                                     |
| `rekrutteringstreffId`  | Treffets UUID                                                             |
| `organisasjonsnummer`   | `arbeidsgiverOrgnr` fra hendelsen                                         |
| `tidspunkt`             | Hendelsens tidspunkt                                                      |
| `utførtAvNavIdent`      | `registrertAvNavIdent`                                                    |
| `utførtAvNavKontorKode` | `registrertAvNavKontor`                                                   |
| `stillingskategori`     | `FORMIDLING` (eller `REKRUTTERINGSTREFF` om kategori-beslutningen endres) |

Ny lytter `RekrutteringstreffFåttJobbenLytter` i `statistikk-api` leser disse feltene direkte og lagrer i `kandidatutfall` + den nye `rekrutteringstreff_id`-kolonnen. Ingen endring i eksisterende lyttere eller validering.

## Endringer per system

| System                   | Endring                                                                                                                            |
| ------------------------ | ---------------------------------------------------------------------------------------------------------------------------------- |
| `frontend`               | Knapp + bekreftelsesmodal i jobbsøkerlisten. Visning av status «fått jobben» på personkortet. Kall til nytt endepunkt.             |
| `rekrutteringstreff-api` | Ny enum-verdi, nytt endepunkt, ny tabell, ny scheduler, ny Rapids-publisering.                                                     |
| `statistikk-api`         | Ny kolonne `rekrutteringstreff_id`, ny lytter `RekrutteringstreffFåttJobbenLytter`. Eksisterende lyttere og validering er uendret. |
| `stilling-api`           | Ingen endring i v1.                                                                                                                |
| `kandidat-api`           | Ingen endring i v1.                                                                                                                |
| `datavarehus-statistikk` | Ingen endring i v1.                                                                                                                |

## Spec — tester som må endres eller legges til

### `rekrutteringstreff-api`

| Test                                                                            | Type          | Hva den skal verifisere                                                                                               |
| ------------------------------------------------------------------------------- | ------------- | --------------------------------------------------------------------------------------------------------------------- |
| `JobbsøkerFattJobbenKomponenttest.skalRegistrereFattJobben`                     | Komponenttest | `POST /…/fatt-jobben` lagrer `FATT_JOBBEN`-hendelse med riktig `hendelse_data` og oppretter rad i utsendingstabellen. |
| `JobbsøkerFattJobbenKomponenttest.skalReturnere409VedDuplikat`                  | Komponenttest | Andre kall for samme jobbsøker i samme treff returnerer `409` og oppretter ikke ny hendelse.                          |
| `JobbsøkerFattJobbenKomponenttest.skalKreveEierEllerUtvikler`                   | Komponenttest | Andre roller får `403`.                                                                                               |
| `JobbsøkerFattJobbenKomponenttest.skalAvviseUkjentArbeidsgiverOrgnr`            | Komponenttest | `arbeidsgiverOrgnr` må tilhøre treffet, ellers `400`.                                                                 |
| `JobbsøkerFattJobbenKomponenttest.skalKreveTreffErFullfort`                     | Komponenttest | Endepunktet returnerer `409` hvis treffet ikke er i `FULLFORT`-status.                                                |
| `JobbsøkerFattJobbenKomponenttest.skalBlokkereSlettingAvJobbsokerMedFattJobben` | Komponenttest | `DELETE /…/jobbsoker/{personTreffId}` returnerer `409` hvis jobbsøkeren har `FATT_JOBBEN`. Ingen ny hendelse skrives. |
| `FattJobbenStatistikkSchedulerTest.skalSendeUutsendteHendelser`                 | Komponenttest | Scheduler plukker rader med `sendt_til_statistikk_tidspunkt IS NULL`, publiserer på Rapids, oppdaterer tidspunkt.     |
| `FattJobbenStatistikkSchedulerTest.skalIkkeResendeAlleredeSendt`                | Komponenttest | Allerede sendte rader hoppes over.                                                                                    |
| `FattJobbenStatistikkSchedulerTest.skalLagreFeilOgØkeForsok`                    | Enhetstest    | Ved feil i publisering lagres `siste_feil` og `forsok` økes; `sendt_til_statistikk_tidspunkt` forblir `NULL`.         |
| `JobbsøkerHendelsestypeTest`                                                    | Enhetstest    | `FATT_JOBBEN` finnes i enumet og er med i serialisering.                                                              |

Eksisterende tester som må verifiseres mot ny enum-verdi:

- `JobbsøkerSokKomponenttest` — sortering/filter må håndtere ny hendelsestype uten å feile.
- `AktivitetskortRepositoryTest` — `FATT_JOBBEN` skal ikke trigge aktivitetskort-utsending.

### `statistikk-api`

| Test                                                                           | Type          | Hva den skal verifisere                                                                                                   |
| ------------------------------------------------------------------------------ | ------------- | ------------------------------------------------------------------------------------------------------------------------- |
| `RekrutteringstreffFåttJobbenLytterTest.skalLagreUtfallOgRekrutteringstreffId` | Komponenttest | Rapids-melding med `kandidat_v2.RekrutteringstreffFåttJobben` lagrer `kandidatutfall` med riktig `rekrutteringstreff_id`. |
| `RekrutteringstreffFåttJobbenLytterTest.skalIgnorereMeldingUtenPåkrevdeFelt`   | Komponenttest | Meldinger som mangler obligatoriske felt ignoreres.                                                                       |
| `KandidatutfallRepositoryTest.skalLagreOgLeseRekrutteringstreffId`             | Enhetstest    | Repo-mapping mot ny kolonne fungerer.                                                                                     |
| `DatavarehusKafkaProducerTest`                                                 | Eksisterende  | Verifisere at `rekrutteringstreff_id` ikke lekker ut i `AvroKandidatutfall`.                                              |

### `frontend`

| Test                                                  | Type        | Hva den skal verifisere                                             |
| ----------------------------------------------------- | ----------- | ------------------------------------------------------------------- |
| `RegistrerFattJobbenKnapp.test.tsx`                   | Enhet (RTL) | Knapp er kun synlig for eier/utvikler. Klikk åpner modal.           |
| `RegistrerFattJobbenModal.test.tsx`                   | Enhet (RTL) | Bekreftelse trigger POST. Lukking uten bekreftelse gjør ingen kall. |
| `JobbsokerListe.fatt-jobben.spec.ts`                  | Playwright  | Hele flyten: åpne liste, velg person, bekreft, se status oppdatert. |
| MSW-mock for `POST /…/fatt-jobben` med `409` og `201` | Mock        | Brukes av begge testene over og av lokal utvikling.                 |

## Åpne spørsmål

1. Skal `arbeidsgiverOrgnr` velges av markedskontakt i modalen, eller utledes hvis treffet kun har én arbeidsgiver?
2. Hvor lang retry-periode og maks antall forsøk skal scheduleren ha før den gir opp og varsler?
3. Når vi senere åpner for `REKRUTTERINGSTREFF`-kategori i Avro: skal eksisterende `FORMIDLING`-rader migreres, eller kun nye?
