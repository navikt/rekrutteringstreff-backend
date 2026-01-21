# MinSide-flyt for jobbsøkere

Jobbsøkere kan se rekrutteringstreff de er invitert til og svare på invitasjoner via MinSide.

## Arkitektur

```mermaid
sequenceDiagram
    participant Jobbsøker
    participant MinSide
    participant minside-api as rekrutteringstreff-minside-api
    participant api as rekrutteringstreff-api

    Note over Jobbsøker,api: Jobbsøker åpner invitasjonslenke

    Jobbsøker->>MinSide: Klikker på lenke i varsel
    MinSide->>minside-api: GET /api/rekrutteringstreff/{id}
    minside-api->>api: Videresender med TokenX
    api-->>minside-api: Treffdetaljer + arbeidsgivere + innlegg
    minside-api-->>MinSide: Filtrert respons for jobbsøker
    MinSide-->>Jobbsøker: Viser treffinfo

    Note over Jobbsøker,api: Jobbsøker svarer på invitasjon

    Jobbsøker->>MinSide: Svarer ja/nei
    MinSide->>minside-api: PUT /api/rekrutteringstreff/{id}/svar
    minside-api->>api: POST .../jobbsoker/borger/svar-ja eller svar-nei
    api-->>minside-api: Bekreftelse
    minside-api-->>MinSide: OK
    MinSide-->>Jobbsøker: Bekreftelse på svar
```

## Flytbeskrivelse

### Se rekrutteringstreff

1. Jobbsøker mottar varsel (SMS/e-post) med lenke til MinSide
2. Jobbsøker logger inn via ID-porten
3. MinSide henter treffdetaljer fra `rekrutteringstreff-minside-api`
4. Jobbsøker ser informasjon om treffet: tittel, beskrivelse, tid, sted, arbeidsgivere og innlegg

### Svare på invitasjon

1. Jobbsøker velger å svare "Ja" eller "Nei" på invitasjonen
2. Svaret sendes til `rekrutteringstreff-minside-api`
3. API-et videresender svaret til `rekrutteringstreff-api` som registrerer hendelsen
4. Veileder kan se jobbsøkerens svar i rekrutteringsbistand

### Aktivitetskort-oppdatering

Når jobbsøker svarer på invitasjonen, oppdateres aktivitetskortet i aktivitetsplanen automatisk:

- **Svarer ja** → Aktivitetskortet flyttes til status "Planlagt"
- **Svarer nei** → Aktivitetskortet flyttes til status "Avbrutt"

Se [aktivitetskort.md](aktivitetskort.md) for detaljer om aktivitetskort-integrasjonen.

## Autentisering

- Jobbsøker autentiseres via ID-porten
- `rekrutteringstreff-minside-api` bruker TokenX for å veksle token mot `rekrutteringstreff-api`
- Jobbsøker identifiseres med personnummer (`pid`-claim)
