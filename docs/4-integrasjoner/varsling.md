# Varsling for Rekrutteringstreff

## Overordnet

Løsningen sender automatiske varsler til jobbsøkere i følgende situasjoner:

1. **Invitasjon** - Når en jobbsøker inviteres til et rekrutteringstreff
2. **Endring** - Når et publisert treff med inviterte jobbsøkere endres
3. **Avlysning** - Når et treff avlyses

Varsling skjer via **SMS** eller **e-post**, avhengig av hvilken kontaktinformasjon som finnes i Kontakt- og reservasjonsregisteret (KRR). Dersom jobbsøker ikke har registrert kontaktinformasjon, lagres varselet på **MinSide** der jobbsøkeren kan se det ved pålogging.

---

## Løp 1: Invitasjon til treff

Når veileder inviterer en jobbsøker til et treff, sendes det automatisk et varsel.

### Innhold i meldingen

Varselet inneholder en kort tekst som ber jobbsøker om å logge inn på Nav for å se detaljer og svare på invitasjonen.

### Mal som brukes

**KANDIDAT_INVITERT_TREFF**

**SMS-tekst:**

```
Hei! Du er invitert til et treff der du kan møte arbeidsgivere som har ledige jobber.
Logg inn på Nav for å se detaljer og svare på invitasjonen. Vennlig hilsen Nav
```

### Flyt: Invitasjon

```mermaid
sequenceDiagram
    participant FE as rekrutteringsbistand-frontend
    participant API as rekrutteringstreff-api<br/>(JobbsøkerController)
    participant DB as Database<br/>(jobbsoker_hendelse)
    participant Scheduler as AktivitetskortJobbsøkerScheduler
    participant KV as kandidatvarsel-api<br/>(RekrutteringstreffInvitasjonLytter)
    participant MS as MinSide<br/>(SMS/Epost/MinSide)

    FE->>API: POST /jobbsokere/inviter
    API->>DB: Lagre INVITERT-hendelse

    loop Hvert 10. sekund
        Scheduler->>DB: Hent usendte INVITERT-hendelser
        DB-->>Scheduler: Liste med hendelser
        Scheduler-->>KV: Publiser "rekrutteringstreffinvitasjon"
        Scheduler->>DB: Marker som sendt
    end

    KV->>KV: Opprett varsel med mal<br/>KANDIDAT_INVITERT_TREFF
    KV-->>MS: Send varselbestilling via Kafka

    MS->>MS: Hent kontaktinfo fra KRR
    alt Telefon finnes
        MS-->>Jobbsøker: Send SMS
    else Epost finnes
        MS-->>Jobbsøker: Send e-post
    else Ingen kontaktinfo
        MS-->>MS: Lagre på MinSide
    end

    MS-->>KV: Publiser varselstatus via Kafka
    KV->>KV: Filter: Kun SENDT eller FEILET publiseres

    alt Status er SENDT eller FEILET
        KV-->>API: Publiser "minsideVarselSvar"
        API->>DB: Lagre MOTTATT_SVAR_FRA_MINSIDE<br/>(MinsideVarselSvarLytter)
    else Status er FERDIGSTILT, VENTER, etc.
        KV->>KV: Ikke publisert til rapids
    end

    loop Polling hvert 10. sekund
        FE->>API: GET /jobbsokere (hent liste)
        API->>DB: Hent jobbsøkere med hendelser
        DB-->>API: Jobbsøkerdata inkl. hendelser
        API-->>FE: Liste med jobbsøkere + varselstatus
        FE->>FE: Vis varselstatus i jobbsøkerkort
    end
```

> **Tegnforklaring:**
>
> - Hel linje (`->>`): Synkron/direkte kommunikasjon
> - Stiplet linje (`-->>`): Asynkron kommunikasjon via Kafka (Rapids & Rivers)

---

## Løp 2: Melding om endring

Når et publisert treff med inviterte jobbsøkere endres, kan markedskontakt velge om det skal sendes melding om endringen.

### Valgmuligheter for markedskontakt

Markedskontakt velger:

1. **Om** melding skal sendes (ja/nei per endret felt)
2. **Hvilke felter** som skal nevnes i meldingen:
   - Navn
   - Tidspunkt
   - Svarfrist
   - Sted
   - Introduksjon

### Innhold i meldingen

Varselet inneholder:

- Informasjon om hvilke felter som er endret
- Lenke til oppdatert informasjon

### Mal som brukes

**KANDIDAT_INVITERT_TREFF_ENDRET**

**SMS-tekst:**

```
Det er endringer i et treff du er invitert til: [endringer]. Logg inn på Nav for å se detaljer.
```

`[endringer]` erstattes med valgte felter, f.eks. "tidspunkt og sted" eller "navn, tidspunkt og sted".

### Hvem får meldingen?

Kun jobbsøkere som:

- Er invitert, eller
- Har svart ja til invitasjonen

Jobbsøkere som har svart nei får **ikke** melding om endringer.

### Flyt: Endring

```mermaid
sequenceDiagram
    participant FE as rekrutteringsbistand-frontend<br/>(RepubliserRekrutteringstreffButton)
    participant API as rekrutteringstreff-api<br/>(RekrutteringstreffController)
    participant DB as Database<br/>(jobbsoker_hendelse)
    participant Scheduler as AktivitetskortJobbsøkerScheduler
    participant KV as kandidatvarsel-api<br/>(RekrutteringstreffOppdateringLytter)
    participant MS as MinSide<br/>(SMS/Epost/MinSide)

    FE->>FE: Markedskontakt endrer treff<br/>og velger hvilke felter skal varsles
    FE->>API: PUT /rekrutteringstreff/:id<br/>(med endringer + flettedata)
    API->>DB: Lagre TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON<br/>for hver invitert/ja jobbsøker

    loop Hvert 10. sekund
        Scheduler->>DB: Hent usendte TREFF_ENDRET-hendelser
        DB-->>Scheduler: Liste med hendelser
        Scheduler-->>KV: Publiser "rekrutteringstreffoppdatering"<br/>(inkl. flettedata: ["tidspunkt", "sted"])
        Scheduler->>DB: Marker som sendt
    end

    KV->>KV: Opprett varsel med mal<br/>KANDIDAT_INVITERT_TREFF_ENDRET<br/>(erstatt placeholder med flettedata)
    KV-->>MS: Send varselbestilling via Kafka

    MS->>MS: Hent kontaktinfo fra KRR
    alt Telefon finnes
        MS-->>Jobbsøker: Send SMS med endringer
    else Epost finnes
        MS-->>Jobbsøker: Send e-post med endringer
    else Ingen kontaktinfo
        MS-->>MS: Lagre på MinSide
    end

    MS-->>KV: Publiser varselstatus via Kafka
    KV->>KV: Filter: Kun SENDT eller FEILET publiseres

    alt Status er SENDT eller FEILET
        KV-->>API: Publiser "minsideVarselSvar"<br/>(inkl. flettedata og mal)
        API->>DB: Lagre MOTTATT_SVAR_FRA_MINSIDE<br/>(MinsideVarselSvarLytter)
    else Status er FERDIGSTILT, VENTER, etc.
        KV->>KV: Ikke publisert til rapids
    end

    loop Polling hvert 10. sekund
        FE->>API: GET /jobbsokere (hent liste)
        API->>DB: Hent jobbsøkere med hendelser
        DB-->>API: Jobbsøkerdata inkl. hendelser
        API-->>FE: Liste med jobbsøkere + varselstatus
        FE->>FE: Vis varselstatus i jobbsøkerkort
    end
```

> **Tegnforklaring:**
>
> - Hel linje (`->>`): Synkron/direkte kommunikasjon
> - Stiplet linje (`-->>`): Asynkron kommunikasjon via Kafka (Rapids & Rivers)

---

## Løp 3: Avlysning av treff

Når et publisert treff avlyses, sendes det automatisk varsel til jobbsøkere som har svart ja på invitasjonen.

### Hvem får varsel?

Kun jobbsøkere som:

- Har **svart ja** til invitasjonen

Jobbsøkere som kun er invitert (ikke svart) eller har svart nei, får **ikke** varsel om avlysning.

### Innhold i meldingen

Varselet inneholder:

- Informasjon om at treffet er avlyst
- Lenke til oppdatert informasjon på MinSide

### Mal som brukes

**KANDIDAT_INVITERT_TREFF_AVLYST** (ny mal)

**SMS-tekst:**

```
Hei! Treffet du hadde takket ja til er dessverre avlyst. Logg inn på Nav for mer informasjon. Vennlig hilsen Nav
```

### Flyt: Avlysning

```mermaid
sequenceDiagram
    participant FE as rekrutteringsbistand-frontend
    participant API as rekrutteringstreff-api<br/>(RekrutteringstreffController)
    participant DB as Database<br/>(jobbsoker_hendelse)
    participant Scheduler as AktivitetskortJobbsøkerScheduler
    participant KV as kandidatvarsel-api<br/>(RekrutteringstreffAvlysningLytter)
    participant MS as MinSide<br/>(SMS/Epost/MinSide)

    FE->>FE: Markedskontakt avlyser treff
    FE->>API: PUT /rekrutteringstreff/:id/avlys
    API->>DB: Lagre AVLYST-hendelse på treff
    API->>DB: Lagre SVART_JA_TREFF_AVLYST<br/>for jobbsøkere som har svart ja
    API->>DB: Lagre IKKE_SVART_TREFF_AVLYST<br/>for inviterte som ikke har svart

    Note over Scheduler,MS: Ny funksjonalitet: Varsling

    loop Hvert 10. sekund
        Scheduler->>DB: Hent usendte SVART_JA_TREFF_AVLYST-hendelser
        DB-->>Scheduler: Liste med hendelser
        Scheduler-->>KV: Publiser "rekrutteringstreffavlysning"
        Scheduler->>DB: Marker som sendt
    end

    KV->>KV: Opprett varsel med mal<br/>KANDIDAT_INVITERT_TREFF_AVLYST
    KV-->>MS: Send varselbestilling via Kafka

    MS->>MS: Hent kontaktinfo fra KRR
    alt Telefon finnes
        MS-->>Jobbsøker: Send SMS
    else Epost finnes
        MS-->>Jobbsøker: Send e-post
    else Ingen kontaktinfo
        MS-->>MS: Lagre på MinSide
    end

    MS-->>KV: Publiser varselstatus via Kafka
    KV->>KV: Filter: Kun SENDT eller FEILET publiseres

    alt Status er SENDT eller FEILET
        KV-->>API: Publiser "minsideVarselSvar"
        API->>DB: Lagre MOTTATT_SVAR_FRA_MINSIDE<br/>(MinsideVarselSvarLytter)
    end
```

> **Tegnforklaring:**
>
> - Hel linje (`->>`): Synkron/direkte kommunikasjon
> - Stiplet linje (`-->>`): Asynkron kommunikasjon via Kafka (Rapids & Rivers)

---

## Felles komponenter

### Scheduler (AktivitetskortJobbsøkerScheduler)

Kjører hvert 10. sekund og:

1. Henter usendte hendelser fra database (`INVITERT`, `TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON` og `SVART_JA_TREFF_AVLYST`)
2. Publiserer hendelser til Rapids & Rivers
3. Markerer hendelser som sendt

Kun synlige jobbsøkere (`er_synlig = TRUE`) får varsler.

### Flettedata og hendelse_data

Løsningen bruker to måter å overføre tilleggsinformasjon på, avhengig av behov:

| Løp         | Trenger dynamisk innhold? | Lagring                | Beskrivelse                                                   |
| ----------- | ------------------------- | ---------------------- | ------------------------------------------------------------- |
| Invitasjon  | Nei                       | Ingen ekstra data      | Fast SMS-tekst, ingen variabler                               |
| Endring     | Ja                        | `hendelse_data` (JSON) | Bruker velger hvilke felter som skal nevnes i meldingen       |
| Avlysning   | Nei                       | Ingen ekstra data      | Fast SMS-tekst, ingen variabler                               |

**Hvorfor flettedata kun for endring?**

Ved invitasjon og avlysning brukes en fast meldingstekst uten variabler. Ved endring må meldingen inneholde *hvilke* felter som er endret (f.eks. "tidspunkt og sted"), og dette velges av markedskontakt i frontend.

**Flyt for hendelse_data:**

```
Frontend → API → Database (hendelse_data JSON) → Scheduler → Rapids (flettedata) → kandidatvarsel-api → SMS
```

**Eksempel på hendelse_data i database:**

```json
{
  "flettedata": ["tidspunkt", "sted"]
}
```

Scheduleren leser denne JSON-en og inkluderer `flettedata` i Rapids-meldingen. kandidatvarsel-api bruker dette til å bygge SMS-teksten: *"Det er endringer i et treff du er invitert til: tidspunkt og sted."*

### Varselkanaler (fra MinSide)

MinSide velger kanal basert på kontaktinfo fra KRR:

| Kontaktinfo i KRR | Varselkanal | Ekstern kanal-verdi |
| ----------------- | ----------- | ------------------- |
| Telefonnummer     | SMS         | `SMS`               |
| E-postadresse     | E-post      | `EPOST`             |
| Ingen info        | MinSide     | `null`              |

**Prioritering:** SMS prioriteres hvis både telefon og e-post finnes.

### Varselstatus (i MOTTATT_SVAR_FRA_MINSIDE-hendelse)

**Statusfilter:**
Kun statuser `SENDT` og `FEILET` publiseres fra kandidatvarsel-api til rapids.

**Hvorfor ikke FERDIGSTILT?**
`FERDIGSTILT`-status kan ta lang tid å motta fra MinSide, selv om meldingen allerede ligger i MinSide og er sendt til jobbsøker. Ved å bruke `SENDT`-status får vi raskere tilbakemelding til markedskontakt om at varselet er levert, uten å vente på den endelige ferdigstillingen.

**MinsideStatus:**

- `UNDER_UTSENDING` - Varselet sendes
- `OPPRETTET` - MinSide har bekreftet opprettelse
- `SLETTET` - Varselet er slettet

**EksternStatus (kun SENDT og FEILET publiseres til rapids):**

- `SENDT` - ✅ Publiseres - Varsel sendt vellykket (SMS eller e-post)
- `FEILET` - ✅ Publiseres - Feil ved sending
- `FERDIGSTILT` - ❌ Publiseres ikke - Ferdig behandlet (tar ofte lang tid)
- `VENTER` - ❌ Publiseres ikke - Venter på utsending
- `KANSELLERT` - ❌ Publiseres ikke - Kansellert
- `UNDER_UTSENDING` - ❌ Publiseres ikke - Vi kan vurdere å legge det til senere dersom vi erfarer at meldinger tar lang tid.

**EksternFeilmelding:**

- `person_ikke_funnet` - Personen mangler kontaktinfo i KRR

---

## Teknisk oversikt

### Rapids-meldinger

De tre løpene bruker ulike Rapids-meldinger med ulikt innhold:

#### rekrutteringstreffinvitasjon (Løp 1)

Inneholder treffdetaljer for aktivitetskort. Ingen flettedata – fast SMS-tekst.

```json
{
  "@event_name": "rekrutteringstreffinvitasjon",
  "fnr": "12345678910",
  "rekrutteringstreffId": "uuid",
  "hendelseId": "uuid",
  "tittel": "Jobbtreff hos bedrift AS",
  "fraTid": "2025-02-01T10:00:00Z",
  "tilTid": "2025-02-01T14:00:00Z",
  "svarfrist": "2025-01-25T23:59:59Z",
  "gateadresse": "Eksempelgate 1",
  "postnummer": "0123",
  "poststed": "Oslo"
}
```

#### rekrutteringstreffoppdatering (Løp 2)

Inneholder `flettedata` – hvilke felter som er endret og skal nevnes i SMS.

```json
{
  "@event_name": "rekrutteringstreffoppdatering",
  "fnr": "12345678910",
  "rekrutteringstreffId": "uuid",
  "hendelseId": "uuid",
  "flettedata": ["tidspunkt", "sted"]
}
```

#### rekrutteringstreffavlysning (Løp 3)

Enkel melding uten flettedata – fast SMS-tekst.

```json
{
  "@event_name": "rekrutteringstreffavlysning",
  "fnr": "12345678910",
  "rekrutteringstreffId": "uuid",
  "hendelseId": "uuid",
  "tittel": "Jobbtreff hos bedrift AS"
}
```

#### minsideVarselSvar (tilbakemelding fra alle løp)

Returneres fra kandidatvarsel-api etter at MinSide har behandlet varselet. Inneholder `flettedata` og `mal` slik at vi kan spore hvilken type varsel det gjelder.

```json
{
  "@event_name": "minsideVarselSvar",
  "varselId": "uuid",
  "avsenderReferanseId": "rekrutteringstreffId",
  "fnr": "12345678910",
  "eksternStatus": "SENDT",
  "minsideStatus": "OPPRETTET",
  "eksternKanal": "SMS",
  "mal": "KANDIDAT_INVITERT_TREFF",
  "flettedata": ["tidspunkt", "sted"]
}
```

> **Merk:** `flettedata` i `minsideVarselSvar` er kun relevant for Løp 2 (endring). For andre løp er feltet tomt eller fraværende.
```

### Nøkkelklasser

**rekrutteringstreff-api:**

- `JobbsøkerController` - API-endepunkt for invitasjon
- `RekrutteringstreffController` - API-endepunkt for oppdatering av treff
- `JobbsøkerService` - Forretningslogikk
- `AktivitetskortJobbsøkerScheduler` - Poller og publiserer hendelser
- `MinsideVarselSvarLytter` - Lytter på varselstatus

**kandidatvarsel-api:**

- `RekrutteringstreffInvitasjonLytter` - Lytter på invitasjoner
- `RekrutteringstreffOppdateringLytter` - Lytter på endringer
- `MinsideClient` - Sender varsel til MinSide via Kafka

**rekrutteringsbistand-frontend:**

- `JobbsøkerKort.tsx` - Viser varselstatus i UI
- `RepubliserRekrutteringstreffButton.tsx` - UI for valg av varsling ved endringer
- Polling-logikk: Henter jobbsøkerliste hvert 10. sekund

---

## Relatert dokumentasjon

- [Aktivitetskort for Rekrutteringstreff](aktivitetskort.md)
- [MinSide dokumentasjon](https://navikt.github.io/tms-dokumentasjon/varsler/produsere)
