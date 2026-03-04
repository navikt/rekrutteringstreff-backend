# Plan: Administrere eiere på rekrutteringstreff

## Status

| Komponent                                              | Status          |
| ------------------------------------------------------ | --------------- |
| `eiere text[]` i DB                                    | ✅ Implementert |
| `GET /eiere`, `PUT /eiere`, `DELETE /eiere/{navIdent}` | ✅ Implementert |
| `EierService.erEierEllerUtvikler()`                    | ✅ Implementert |
| `RekrutteringstreffDto.eiere`                          | ✅ Implementert |
| `useErTreffEier()` i frontend                          | ✅ Implementert |
| Maks 3 eiere (validering)                              | ❌ Mangler      |
| Legg til meg selv som eier                             | ❌ Mangler      |
| Hendelseslogging ved eierskap-endringer                | ❌ Mangler      |
| GUI for eiere                                          | ❌ Mangler      |

---

## Regler

- Minst 1 eier, maks **3 eiere**
- Oppretteren settes automatisk som første eier
- Kun **arbeidsgiverrettet** eller **utvikler** kan bli eier
- Eier kan: redigere treff, invitere jobbsøkere/arbeidsgivere, administrere eiere
- Jobbsøkerrettet har kun lesetilgang

> **Kontorkrav:** Anbefaling er å starte uten – alle med arbeidsgiverrettet-rolle kan legge til seg selv. Kan strammes til ved behov.

---

## 1. Backend

### 1.1 Maks 3 eiere

Valideringssjekk i `EierController.leggTil()`: eksisterende + nye eiere (distinct) må ikke overstige 3. HTTP 400 ved brudd.

### 1.2 Nytt endepunkt: `PUT /api/rekrutteringstreff/{id}/eiere/meg`

- Ingen request body – bruker sin egen navIdent
- Krav: rolle `ARBEIDSGIVER_RETTET` eller `UTVIKLER`
- Idempotent: allerede eier → 200, ny eier → 201, maks nådd → 400

### 1.3 Hendelseslogging

Logges i `rekrutteringstreff_hendelse` fra `EierService`:

| Hendelsestype   | `hendelse_data`                    | `aktøridentifikasjon`       |
| --------------- | ---------------------------------- | --------------------------- |
| `EIER_LAGT_TIL` | `{ "navIdentLagtTil": "A123456" }` | Den som utfører operasjonen |
| `EIER_FJERNET`  | `{ "navIdentFjernet": "A123456" }` | Den som utfører operasjonen |

### 1.4 Tilgangskontroll – gjennomgang

Sjekk at alle operasjoner som krever eierskap bruker `eierService.erEierEllerUtvikler()`: rediger treff, invitasjon, eier-endringer.

---

## 2. Frontend – GUI

### 2.1 EiereSeksjon

Plasseres i `OmTreffetForEier.tsx` under "Om treffet"-boksen som eget `InfoBoks`-kort.

```
┌──────────────────────────────────────────┐
│  Eiere                    2 av 3         │
│                                          │
│  A123456                  [Fjern]        │
│  Z999999                  [Fjern]        │
│                                          │
│  [+ Legg til meg som eier]               │
└──────────────────────────────────────────┘
```

| Element                     | Synlighet                                                            | Kall                       |
| --------------------------- | -------------------------------------------------------------------- | -------------------------- |
| Liste over eiere (navIdent) | Alltid for eiere                                                     | –                          |
| Fjern-knapp per eier        | Antall > 1 og innlogget bruker er eier/utvikler                      | `DELETE /eiere/{navIdent}` |
| "Legg til meg som eier"     | Bruker er ikke eier + antall < 3 + rolle arbeidsgiverrettet/utvikler | `PUT /eiere/meg`           |
| Kapasitetsindikator         | Alltid                                                               | –                          |

> Frontend bruker SWR-hook `useEiere()` etter eksisterende mønster. Kun navIdent vises i første omgang.

### 2.2 Synlighet for ikke-eiere

Eiere-listen vises i `OmTreffetForIkkeEier` som informasjon, uten endringsmuligheter.

### 2.3 Hendelsesvisning

For at `EIER_LAGT_TIL` og `EIER_FJERNET` vises i hendelsesloggen (`Hendelser.tsx`) må disse stedene oppdateres:

- **`constants.ts`** – legg til i `RekrutteringstreffHendelsestype` og tilhørende labels (`'eier lagt til'` / `'eier fjernet'`)
- **`HentHendelseIkon.tsx`** – ikon per type (`PersonPlusIcon` / `PersonMinusIcon` fra `@navikt/aksel-icons`)
- **allehendelser-API** – berik eier-hendelser med navIdent fra `hendelse_data` slik at det vises i "Gjelder"-kolonnen

---

## 3. Datamodell

Ingen DB-endringer nødvendig – `eiere text[]` er allerede på plass.

---

## 4. Testing

**Backend** (`RekrutteringstreffEierTest.kt`, komponenttester):

- Legg til meg: 201, idempotens (200), maks nådd (400), mangler rolle (403)
- Slett eier: siste eier (400), ikke eier (403)
- Hendelser logges i `rekrutteringstreff_hendelse`

**Frontend:**

- Fjern-knapp skjult om kun 1 eier
- "Legg til meg" skjult om allerede eier eller maks nådd

---

## 5. Arbeidsrekkefølge

1. Backend: maks-validering + `PUT /eiere/meg` + hendelseslogging + tester
2. Frontend: `useEiere()` + `EiereSeksjon` + integrasjon i `OmTreffetForEier`
3. Frontend: hendelsestyper i `constants.ts`, ikon, allehendelser-berikelse
4. Gjennomgang: tilgangskontroll + Playwright-test

---

## 6. Avklaringer

- **Maks eiere:** Hvor mange eiere skalman max kunne ha, er et bra tall?
- **Hvem kan legge til seg selv:** Alle med arbeidsgiverrettet-rolle på samme kontor som treffet. Eler alle som har rollen uansett kontor?
- **Eier legger til andre:** Bør eiere kunne legge til navIdenten til en annen person direkte, eller kun via "legg til meg selv"?
- **Navn på eiere:** Viser kun navIdent i dag – skal vi hente og vise fullt navn?
  **Jobbsøkerrettet – skal de se eiere?** Skal jobbsøkerrettet-brukere se hvem som er eiere av et treff?
  **Eierkontor:** Kontor settes til innlogget kontor ved opprettelse. Bør eiere kunne overføre treffet til annet kontor? Eventuelt kun kontor som personen som overfører har tilgang til?
