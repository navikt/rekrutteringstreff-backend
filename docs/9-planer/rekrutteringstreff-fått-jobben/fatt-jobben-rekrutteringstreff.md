# Plan: Registrering av «fått jobben» i rekrutteringstreff (alternativ 2A)

Implementasjonsplan for løsningen som er valgt i [vurdering-fatt-jobben-statistikk-rekrutteringstreff.md](./vurdering-fatt-jobben-statistikk-rekrutteringstreff.md), alternativ 2A: markedskontakt registrerer «fått jobben» direkte fra jobbsøkerlisten i et rekrutteringstreff. `rekrutteringstreff-api` eier hele løpet og publiserer en Rapids-melding som dagens `statistikk-api` lytter på.

## Kjernebeslutninger

| Tema                       | Beslutning                                                                                                                                                         | Implementasjonskonsekvens                                                                                                                               |
| -------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Kategori i Avro            | Gjenbruke `stillingskategori = FORMIDLING` i v1. Egen `REKRUTTERINGSTREFF`-verdi vurderes senere.                                                                  | Ingen endring hos `datavarehus-statistikk` ved utrulling. Treff er ikke skillbar fra etterregistrering eksternt før migrering.                          |
| Synlighet av treff internt | `statistikk-api` skal kunne skille treff fra ordinær etterregistrering i sine egne tabeller og aggregater.                                                         | To alternativer — se «Endringer i `statistikk-api`» nedenfor. Ikke avgjort ennå.                                                                        |
| Kandidatliste/stilling     | Det opprettes ikke noen formidlingsstilling. `stillingsId` og `kandidatlisteId` er ikke med i den nye meldingen og settes ikke i `kandidatutfall` for treff-rader. | Stilling-api og kandidat-api berøres ikke i v1. Kolonner i `kandidatutfall` for `stillingsId`/`kandidatlisteId` er allerede nullable.                   |
| Angring                    | Støttet i v1. Markedskontakt kan fjerne en feilregistrert «fått jobben».                                                                                          | Ny hendelse `FATT_JOBBEN_FJERNET` (event-sourcing, ingen sletting). Eget endepunkt + egen Rapids-melding `kandidat_v2.RekrutteringstreffFåttJobbenFjernet`. Lytter i `statistikk-api` markerer raden som angret. |
| Idempotens                 | En jobbsøker kan ha maks én aktiv `FATT_JOBBEN`-hendelse per rekrutteringstreff.                                                                                   | Unik constraint i `rekrutteringstreff-api` + sjekk i service før hendelse skrives. Endepunktet returnerer `409` ved duplikat.                           |
| Utsending til statistikk   | Egen scheduler i `rekrutteringstreff-api` plukker uutsendte hendelser og publiserer på Rapids. Retries med eksponentiell backoff.                                  | Ny tabell `jobbsoker_fatt_jobben_utsending` og ny scheduler. Sender via eksisterende Rapids-tilkobling.                                                 |

## Hendelser

`JobbsøkerHendelsestype` utvides med `FATT_JOBBEN` og `FATT_JOBBEN_FJERNET`. Eksisterende hendelsestyper er uendret.

| Hendelsestype          | Trigger                                                                            | `hendelse_data`                                                                                                                              |
| ---------------------- | ---------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------- |
| `FATT_JOBBEN`          | Markedskontakt bekrefter «Registrer fått jobben» fra jobbsøkerlisten i treffet     | JSON-snapshot: `registrertAvNavIdent`, `registrertAvNavKontor`, `tidspunkt`, `arbeidsgiverOrgnr`                                             |
| `FATT_JOBBEN_FJERNET`  | Markedskontakt klikker «Fjern fått jobben» på en jobbsøker som har `FATT_JOBBEN`     | JSON-snapshot: `fjernetAvNavIdent`, `fjernetAvNavKontor`, `tidspunkt`, `referanseFattJobbenHendelseId` (FK til opprinnelig `FATT_JOBBEN`)    |

Effektiv status på jobbsøkeren utledes ved å lese siste hendelse i tidsserien: hvis siste hendelse er `FATT_JOBBEN_FJERNET`, er jobbsøkeren *ikke* lenger i «fått jobben»-tilstand. Re-registrering tillates dermed (ny `FATT_JOBBEN`-hendelse).

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

To alternativer. Begge bruker den samme `RekrutteringstreffFåttJobbenLytter` og endrer ikke eksisterende lyttere.

#### Alt A — Ny kolonne på `kandidatutfall`

```sql
ALTER TABLE kandidatutfall ADD COLUMN rekrutteringstreff_id uuid;
ALTER TABLE kandidatutfall ADD COLUMN angret_tidspunkt      timestamptz;
CREATE INDEX idx_kandidatutfall_rekrutteringstreff ON kandidatutfall(rekrutteringstreff_id)
    WHERE rekrutteringstreff_id IS NOT NULL;
```

Lytteren skriver én rad til `kandidatutfall` med `rekrutteringstreff_id` satt og `stilling_id`/`kandidatliste_id` som `NULL`. Ved angring (`kandidat_v2.RekrutteringstreffFåttJobbenFjernet`) oppdateres raden med `angret_tidspunkt`. Eksisterende `DatavarehusKafkaProducer`-scheduler plukker raden opp uten endring og sender Avro videre til `datavarehus-statistikk` ved første registrering; ved angring sendes ingen ny Avro-melding i v1 (datavarehus-statistikk håndterer ikke fjerning ennå — se åpne spørsmål). Aggregater internt filtrerer på `angret_tidspunkt IS NULL`.

| Fordel                                              | Ulempe                                                                                           |
| --------------------------------------------------- | ------------------------------------------------------------------------------------------------ |
| Ingen endring i producer eller datavarehus-pipeline | Treff-rader bærer nullable-felt som er meningsløse for treff (`stilling_id`, `kandidatliste_id`) |
| Eksisterende repo-kode og tester dekker nesten alt  | Samler to ulike datakontekster i én tabell                                                       |
| Minst ny kode                                       | Partial index nødvendig for effektive treff-spesifikke spørringer                                |

#### Alt B — Dedikert tabell `rekrutteringstreff_utfall`

```sql
CREATE TABLE rekrutteringstreff_utfall (
    id                    bigserial PRIMARY KEY,
    rekrutteringstreff_id uuid        NOT NULL,
    aktor_id              text        NOT NULL,
    nav_ident             text        NOT NULL,
    nav_kontor            text        NOT NULL,
    organisasjonsnummer   text        NOT NULL,
    tidspunkt             timestamptz NOT NULL,
    angret_tidspunkt      timestamptz,
    UNIQUE (rekrutteringstreff_id, aktor_id)
);

CREATE INDEX idx_rekrutteringstreff_utfall_treff
    ON rekrutteringstreff_utfall(rekrutteringstreff_id);
```

Lytteren skriver kun til `rekrutteringstreff_utfall`. Siden det er nøyaktig én rad per jobbsøker per treff, reflekterer den unike constrainten `(rekrutteringstreff_id, aktor_id)` forretningsmeningen direkte. Ved angring oppdateres `angret_tidspunkt`; raden slettes ikke. Aktive utfall hentes med `WHERE angret_tidspunkt IS NULL`. Uttrekk internt er trivielt: `SELECT * FROM rekrutteringstreff_utfall WHERE rekrutteringstreff_id = ? AND angret_tidspunkt IS NULL` — ingen partial index, ingen nullable-støy.

For at Avro fortsatt skal sendes til `datavarehus-statistikk` i v1 uten å endre den tjenesten, må én av to løsninger velges:

- **B1**: Lytteren skriver også én rad til `kandidatutfall` (med `NULL` for stilling-felt) slik at eksisterende producer plukker det opp — to skriv i én transaksjon.
- **B2**: Ny scheduler leser fra `rekrutteringstreff_utfall` og sender Avro separat, uavhengig av `DatavarehusKafkaProducer`.

| Fordel                                                        | Ulempe                                                            |
| ------------------------------------------------------------- | ----------------------------------------------------------------- |
| Ren modell — kun feltene som er relevante for treff           | Krever ekstra beslutning for datavarehus-pipelinen (B1 eller B2)  |
| Unik constraint på `(treff, aktor)` er naturlig og eksplisitt | Mer ny kode: nytt repo, ny scheduler (B2) eller dobbeltskriv (B1) |
| Enkel og rask intern spørring uten partial index              | Eksisterende `KandidatutfallRepositoryTest` dekker ikke ny tabell |

#### Sammenligning

|                                | Alt A                        | Alt B                                  |
| ------------------------------ | ---------------------------- | -------------------------------------- |
| Endring i datavarehus-pipeline | Ingen                        | B1: dobbeltskriv · B2: ny scheduler    |
| Intern spørring                | Partial index, nullable felt | Trivielt, ren tabell                   |
| Ny kode i statistikk-api       | Lite (1 kolonne + lytter)    | Mer (ny tabell + repo + ev. scheduler) |
| Modellryddighet                | Middels                      | God                                    |

**Ikke avgjort.** Alt A er enklest å shippe; Alt B er renere om vi forventer mer intern rapportering på tvers av treff.

## API i `rekrutteringstreff-api`

| Endepunkt                                                                        | Beskrivelse                                                                                                                                                                                                                              |
| -------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `POST /api/rekrutteringstreff/{id}/jobbsoker/{personTreffId}/fatt-jobben`        | Registrerer at jobbsøkeren har fått jobben. Krever rolle `ARBEIDSGIVER_RETTET` + eier/utvikler. Returnerer `201`. `409` ved duplikat. `409` hvis treffet ikke er i `FULLFORT`-status. `409` hvis jobbsøkeren allerede har aktiv `FATT_JOBBEN`. |
| `DELETE /api/rekrutteringstreff/{id}/jobbsoker/{personTreffId}/fatt-jobben`      | Angrer en tidligere registrering. Krever samme rolle som POST. Returnerer `204`. `409` hvis jobbsøkeren ikke har en aktiv `FATT_JOBBEN`. Skriver `FATT_JOBBEN_FJERNET`-hendelse og køer en angre-melding for utsending.                     |

Body (forslag):

```kotlin
data class FattJobbenInputDto(
    val arbeidsgiverOrgnr: String
)
```

`personTreffId` er i URL-stien. `aktørId` (og eventuelt fødselsnummer ved behov) slås opp server-side fra `personTreffId` — ingen personidentifikator i request body. `registrertAvNavIdent`/`registrertAvNavKontor` hentes fra token, `tidspunkt` settes server-side.

### Status etter `FATT_JOBBEN`

Når `FATT_JOBBEN`-hendelsen er aktiv (siste relevante hendelse), låses jobbsøkeren delvis i treffet:

- Jobbsøkeren kan **ikke** slettes fra treffet.
- Ingen andre statushendelser kan registreres.
- Et nytt `POST /…/fatt-jobben` avvises med `409`.
- Eneste tillatte muterende handling er `DELETE /…/fatt-jobben` (angring).

Etter angring (`FATT_JOBBEN_FJERNET` er siste hendelse) kan jobbsøkeren igjen få nye statushendelser, inkludert ny `FATT_JOBBEN` om markedskontakt vil registrere på nytt.

Service-laget leser hendelseshistorikken for `personTreffId` og avgjør effektiv status før enhver muterende operasjon. Feilrespons inkluderer forklarende `feil`-felt.

## Rapids-meldinger

To dedikerte events som kun `rekrutteringstreff-api` publiserer og `statistikk-api` lytter på. Ingen gjenbruk av eksisterende lyttere — ingen syntetiske ID-er, ingen `stillingsinfo`-wrapper.

### `kandidat_v2.RekrutteringstreffFåttJobben`

| Felt                    | Verdi                              |
| ----------------------- | ---------------------------------- |
| `@event_name`           | `kandidat_v2.RekrutteringstreffFåttJobben` |
| `aktørId`               | Jobbsøkerens aktør-ID                |
| `rekrutteringstreffId`  | Treffets UUID                      |
| `organisasjonsnummer`   | `arbeidsgiverOrgnr` fra hendelsen  |
| `tidspunkt`             | Hendelsens tidspunkt               |
| `utførtAvNavIdent`      | `registrertAvNavIdent`             |
| `utførtAvNavKontorKode` | `registrertAvNavKontor`            |

### `kandidat_v2.RekrutteringstreffFåttJobbenFjernet`

| Felt                    | Verdi                                                                |
| ----------------------- | -------------------------------------------------------------------- |
| `@event_name`           | `kandidat_v2.RekrutteringstreffFåttJobbenFjernet`                    |
| `aktørId`               | Jobbsøkerens aktør-ID                                                  |
| `rekrutteringstreffId`  | Treffets UUID                                                        |
| `tidspunkt`             | Angretidspunkt                                                       |
| `utførtAvNavIdent`      | `fjernetAvNavIdent`                                                  |
| `utførtAvNavKontorKode` | `fjernetAvNavKontor`                                                 |

`stillingskategori` er ikke med i meldingene. Lytteren vet at kilden alltid er et rekrutteringstreff og hardkoder `FORMIDLING` (eller `REKRUTTERINGSTREFF` når det evt. innføres) ved lagring.

Ny lytter `RekrutteringstreffFåttJobbenLytter` håndterer begge events. Fått-jobben skriver en rad; angre markerer raden som angret — se «Endringer i `statistikk-api`» for hvordan dette gjøres i Alt A vs Alt B. Ingen endring i eksisterende lyttere eller validering.

## Endringer per system

| System                   | Endring                                                                                                                                                                                                                           |
| ------------------------ | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `frontend`               | Knapp i jobbsøkerlisten + bekreftelsesmodal («Er du sikker?» — kun her, ikke i etterregistrering). «Fjern fått jobben»-knapp på jobbsøker som er registrert. Visning av status på personkortet. Kall til POST/DELETE-endepunktene.                |
| `rekrutteringstreff-api` | To nye enum-verdier (`FATT_JOBBEN`, `FATT_JOBBEN_FJERNET`), POST- og DELETE-endepunkt, ny utsendingstabell, ny scheduler, to nye Rapids-events.                                                                                              |
| `statistikk-api`         | Ny lytter `RekrutteringstreffFåttJobbenLytter` som håndterer både fått-jobben og angre. **Alt A**: nye kolonner `rekrutteringstreff_id` og `angret_tidspunkt` på `kandidatutfall`. **Alt B**: dedikert tabell `rekrutteringstreff_utfall` med `angret_tidspunkt`. Eksisterende lyttere og validering er uendret uansett. |
| `stilling-api`           | Ingen endring i v1.                                                                                                                                                                                                               |
| `kandidat-api`           | Ingen endring i v1.                                                                                                                                                                                                               |
| `datavarehus-statistikk` | Ingen endring i v1.                                                                                                                                                                                                               |

## Spec — tester som må endres eller legges til

### `rekrutteringstreff-api`

| Test                                                                            | Type          | Hva den skal verifisere                                                                                               |
| ------------------------------------------------------------------------------- | ------------- | --------------------------------------------------------------------------------------------------------------------- |
| `JobbsøkerFattJobbenKomponenttest.skalRegistrereFattJobben`                     | Komponenttest | `POST /…/fatt-jobben` lagrer `FATT_JOBBEN`-hendelse med riktig `hendelse_data` og oppretter rad i utsendingstabellen. |
| `JobbsøkerFattJobbenKomponenttest.skalReturnere409VedDuplikat`                  | Komponenttest | Andre kall for samme jobbsøker i samme treff returnerer `409` og oppretter ikke ny hendelse.                          |
| `JobbsøkerFattJobbenKomponenttest.skalKreveEierEllerUtvikler`                   | Komponenttest | Andre roller får `403`.                                                                                               |
| `JobbsøkerFattJobbenKomponenttest.skalAvviseUkjentArbeidsgiverOrgnr`            | Komponenttest | `arbeidsgiverOrgnr` må tilhøre treffet, ellers `400`.                                                                 |
| `JobbsøkerFattJobbenKomponenttest.skalKreveTreffErFullfort`                         | Komponenttest | Endepunktet returnerer `409` hvis treffet ikke er i `FULLFORT`-status.                                                                          |
| `JobbsøkerFattJobbenKomponenttest.skalBlokkereSlettingAvJobbsokerMedAktivFattJobben` | Komponenttest | `DELETE /…/jobbsoker/{personTreffId}` returnerer `409` hvis jobbsøkeren har aktiv `FATT_JOBBEN`. Ingen ny hendelse skrives.                    |
| `JobbsøkerFattJobbenAngreKomponenttest.skalAngreFattJobben`                          | Komponenttest | `DELETE /…/fatt-jobben` returnerer `204`, skriver `FATT_JOBBEN_FJERNET`-hendelse og oppretter angre-utsending.                                  |
| `JobbsøkerFattJobbenAngreKomponenttest.skalReturnere409VedAngreUtenAktivFattJobben`  | Komponenttest | Angre uten aktiv `FATT_JOBBEN` returnerer `409` og skriver ingen hendelse.                                                                       |
| `JobbsøkerFattJobbenAngreKomponenttest.skalKunneReregistrereEtterAngring`            | Komponenttest | Etter `FATT_JOBBEN_FJERNET` kan ny `POST /…/fatt-jobben` registreres på nytt med `201`.                                                          |
| `JobbsøkerFattJobbenAngreKomponenttest.skalKreveSammeRolleSomRegistrering`           | Komponenttest | Andre roller får `403` på DELETE.                                                                                                                |
| `FattJobbenStatistikkSchedulerTest.skalSendeUutsendteHendelser`                 | Komponenttest | Scheduler plukker rader med `sendt_til_statistikk_tidspunkt IS NULL`, publiserer på Rapids, oppdaterer tidspunkt.     |
| `FattJobbenStatistikkSchedulerTest.skalIkkeResendeAlleredeSendt`                | Komponenttest | Allerede sendte rader hoppes over.                                                                                    |
| `FattJobbenStatistikkSchedulerTest.skalLagreFeilOgØkeForsok`                    | Enhetstest    | Ved feil i publisering lagres `siste_feil` og `forsok` økes; `sendt_til_statistikk_tidspunkt` forblir `NULL`.         |
| `JobbsøkerHendelsestypeTest`                                                    | Enhetstest    | `FATT_JOBBEN` og `FATT_JOBBEN_FJERNET` finnes i enumet og er med i serialisering.                                     |

Eksisterende tester som må verifiseres mot ny enum-verdi:

- `JobbsøkerSokKomponenttest` — sortering/filter må håndtere ny hendelsestype uten å feile.
- `AktivitetskortRepositoryTest` — `FATT_JOBBEN` skal ikke trigge aktivitetskort-utsending.

### `statistikk-api`

| Test | Type | Hva den skal verifisere |
| ---- | ---- | ----------------------- |

**Alt A:**

| Test                                                                           | Type          | Hva den skal verifisere                                                                                                         |
| ------------------------------------------------------------------------------ | ------------- | ------------------------------------------------------------------------------------------------------------------------------- |
| `RekrutteringstreffFåttJobbenLytterTest.skalLagreUtfallOgRekrutteringstreffId` | Komponenttest | Rapids-melding lagrer rad i `kandidatutfall` med riktig `rekrutteringstreff_id`, og `stilling_id`/`kandidatliste_id` er `NULL`. |
| `RekrutteringstreffFåttJobbenLytterTest.skalIgnorereMeldingUtenPåkrevdeFelt`   | Komponenttest | Meldinger som mangler obligatoriske felt ignoreres.                                                                             |
| `RekrutteringstreffFåttJobbenLytterTest.skalMarkereRadenSomAngretVedFjernet`   | Komponenttest | `kandidat_v2.RekrutteringstreffFåttJobbenFjernet` setter `angret_tidspunkt` på raden uten å slette den.                          |
| `KandidatutfallRepositoryTest.skalLagreOgLeseRekrutteringstreffId`             | Enhetstest    | Repo-mapping mot nye kolonner (`rekrutteringstreff_id`, `angret_tidspunkt`) fungerer.                                            |
| `DatavarehusKafkaProducerTest`                                                 | Eksisterende  | Verifisere at `rekrutteringstreff_id` og `angret_tidspunkt` ikke lekker ut i `AvroKandidatutfall`.                              |

**Alt B:**

| Test                                                                                               | Type          | Hva den skal verifisere                                                                         |
| -------------------------------------------------------------------------------------------------- | ------------- | ----------------------------------------------------------------------------------------------- |
| `RekrutteringstreffFåttJobbenLytterTest.skalLagreIRekrutteringstreffUtfall`                        | Komponenttest | Rapids-melding lagrer rad i `rekrutteringstreff_utfall` med alle felt korrekt.                  |
| `RekrutteringstreffFåttJobbenLytterTest.skalHåndhevUnikConstraintPerTreffOgAktor`                  | Komponenttest | Duplikatmelding for samme `(rekrutteringstreff_id, aktor_id)` lagres ikke på nytt (idempotens). |
| `RekrutteringstreffFåttJobbenLytterTest.skalIgnorereMeldingUtenPåkrevdeFelt`                       | Komponenttest | Meldinger som mangler obligatoriske felt ignoreres.                                             |
| `RekrutteringstreffFåttJobbenLytterTest.skalMarkereAngretTidspunktVedFjernet`                      | Komponenttest | `kandidat_v2.RekrutteringstreffFåttJobbenFjernet` setter `angret_tidspunkt` på raden.            |
| `RekrutteringstreffUtfallRepositoryTest.skalLagreOgHenteUtfall`                                    | Enhetstest    | Repo-mapping mot ny tabell fungerer, inkludert `angret_tidspunkt`.                              |
| _(Alt B1)_ `RekrutteringstreffFåttJobbenLytterTest.skalOgsåSkriveKandidatutfallForAvro`            | Komponenttest | Lytteren skriver atomisk til begge tabeller; `DatavarehusKafkaProducer` plukker opp raden.      |
| _(Alt B2)_ `RekrutteringstreffAvroSchedulerTest.skalSendeAvroForUutsendteRekrutteringstreffUtfall` | Komponenttest | Dedikert scheduler sender Avro for rader uten `sendt_tidspunkt`.                                |

### `frontend`

| Test                                                                              | Type        | Hva den skal verifisere                                                                                  |
| --------------------------------------------------------------------------------- | ----------- | -------------------------------------------------------------------------------------------------------- |
| `RegistrerFattJobbenKnapp.test.tsx`                                               | Enhet (RTL) | Knapp er kun synlig for eier/utvikler. Klikk åpner bekreftelsesmodal (kun her, ikke i etterregistrering). |
| `RegistrerFattJobbenModal.test.tsx`                                               | Enhet (RTL) | Bekreftelse trigger POST. Lukking uten bekreftelse gjør ingen kall.                                      |
| `FjernFattJobbenKnapp.test.tsx`                                                   | Enhet (RTL) | Synlig kun når jobbsøkeren har aktiv `FATT_JOBBEN`. Klikk trigger DELETE.                                 |
| `JobbsokerListe.fatt-jobben.spec.ts`                                              | Playwright  | Hele flyten: åpne liste, velg person, bekreft, se status oppdatert.                                      |
| `JobbsokerListe.fatt-jobben-angre.spec.ts`                                        | Playwright  | Angre-flyt: registrer fått jobben, klikk fjern, se status fjernet, mulig å registrere på nytt.            |
| MSW-mock for `POST` og `DELETE /…/fatt-jobben` med `204`, `409` og `201`           | Mock        | Brukes av testene over og av lokal utvikling.                                                            |

## Åpne spørsmål

1. Skal `arbeidsgiverOrgnr` velges av markedskontakt i modalen, eller utledes hvis treffet kun har én arbeidsgiver?
2. Hvor lang retry-periode og maks antall forsøk skal scheduleren ha før den gir opp og varsler?
3. Når vi senere åpner for `REKRUTTERINGSTREFF`-kategori i Avro: skal eksisterende `FORMIDLING`-rader migreres, eller kun nye?
4. Skal angring propageres videre til `datavarehus-statistikk` i v1, eller er det nok at det håndteres internt i `statistikk-api`? (`AvroKandidatutfall` har i dag ingen «angret»-semantikk.)
