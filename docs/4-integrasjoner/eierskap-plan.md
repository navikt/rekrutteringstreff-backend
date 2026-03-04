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

| Hendelsestype   | `hendelse_data`                     | `aktøridentifikasjon`       |
| --------------- | ----------------------------------- | --------------------------- |
| `EIER_LAGT_TIL` | `{ "navIdentLagtTil": "A123456" }`  | Den som utfører operasjonen |
| `EIER_FJERNET`  | `{ "navIdentFjernet": "A123456" }`  | Den som utfører operasjonen |

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

| Element                      | Synlighet                                                          | Kall                       |
| ---------------------------- | ------------------------------------------------------------------ | -------------------------- |
| Liste over eiere (navIdent)  | Alltid for eiere                                                   | –                          |
| Fjern-knapp per eier         | Antall > 1 og innlogget bruker er eier/utvikler                    | `DELETE /eiere/{navIdent}` |
| "Legg til meg som eier"      | Bruker er ikke eier + antall < 3 + rolle arbeidsgiverrettet/utvikler | `PUT /eiere/meg`         |
| Kapasitetsindikator          | Alltid                                                             | –                          |

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

## 6. Åpne spørsmål

1. **Vise navn på eiere?** Kun navIdent i dag – hent via norg2 ved behov.
2. **Kontorkrav for legg-til-meg?** Ikke nå – kan strammes til.
3. **`PUT /eiere` (liste) vs. kun `PUT /eiere/meg`?** Behold begge – list-endepunktet er nyttig for admin/utvikler.
4. **Jobbsøkerrettet – skal de se eiere?** Avklar behov.

---

## 7. Fremtidig avklaring: eierkontor

I dag har treffet kun ett kontor: `opprettet_av_kontor_enhetid` (oppretterens kontor). Når flere kan eie et treff oppstår spørsmålet om **hvilke(t) kontor som skal knyttes til treffet**.

Uavklarte problemstillinger:

- Skal alle eiernes kontorer legges til automatisk?
- Hva om en eier tilhører flere kontorer – alle, eller kun det de er innlogget med?
- Hva hvis man blir tildelt eierskap av noen andre mens man er utlogget – hvilket kontor gjelder da?
- Skal eiere heller kunne **velge kontor manuelt**, uavhengig av eget tilhørende kontor?

Et mulig løsning er å, i likhet med `eiere`, ha et felt `kontorer text[]` i backend som kan administreres separat.

> **Foreløpig antagelse:** Vi beholder kun `opprettet_av_kontor_enhetid` som eneste kontor inntil videre. Dette er et avklaringsspørsmål som bør tas opp etter pilot.
