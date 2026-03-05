# Plan: Administrere eiere på rekrutteringstreff

## Status

| Komponent                                              | Status          |
| ------------------------------------------------------ | --------------- |
| `eiere text[]` i DB                                    | ✅ Implementert |
| `GET /eiere`, `PUT /eiere`, `DELETE /eiere/{navIdent}` | ✅ Implementert |
| `EierService.erEierEllerUtvikler()`                    | ✅ Implementert |
| `RekrutteringstreffDto.eiere`                          | ✅ Implementert |
| `useErTreffEier()` i frontend                          | ✅ Implementert |
| Legg til meg selv som eier                             | ❌ Mangler      |
| Hendelseslogging ved eierskap-endringer                | ❌ Mangler      |
| GUI for eiere                                          | ❌ Mangler      |
| Støtte for flere kontorer på treff (`kontorer text[]`) | ❌ Mangler      |

---

## Regler

- Minst 1 eier, ingen øvre grense
- Oppretteren settes automatisk som første eier
- Kun **arbeidsgiverrettet** eller **utvikler** kan bli eier
- Eier kan: redigere treff, invitere jobbsøkere/arbeidsgivere, administrere eiere
- **Lesetilgang er global:** Alle med gyldig rolle (inkludert jobbsøkerrettet) kan i API-et lese alle treff, uavhengig av kontor.
- **Kontor-filtrering:** Et treff kan ha flere kontorer. Dette feltet styrer _ikke_ tilgang, men brukes i frontend for at brukere enkelt skal kunne filtrere frem treff knyttet til sitt eget kontor.

---

## 1. Backend

### 1.1 Nytt endepunkt: `PUT /api/rekrutteringstreff/{id}/eiere/meg`

- Ingen request body – bruker sin egen navIdent
- Krav: rolle `ARBEIDSGIVER_RETTET` eller `UTVIKLER`
- Idempotent: allerede eier → 200, ny eier → 201

### 1.2 Hendelseslogging

Logges i `rekrutteringstreff_hendelse` fra `EierService`:

| Hendelsestype   | `hendelse_data`                    | `aktøridentifikasjon`       |
| --------------- | ---------------------------------- | --------------------------- |
| `EIER_LAGT_TIL` | `{ "navIdentLagtTil": "A123456" }` | Den som utfører operasjonen |

### 1.3 Tilgangskontroll – gjennomgang

Sjekk at alle operasjoner som krever eierskap bruker `eierService.erEierEllerUtvikler()`: rediger treff, invitasjon, eier-endringer.

---

## 2. Frontend – GUI

### 2.1 EiereSeksjon

Plasseres som et delt `InfoBoks`-kort som kan brukes i både `OmTreffetForEier.tsx` og `OmTreffetForIkkeEier.tsx`.

```
┌──────────────────────────────────────────┐
│  Eierskap                                │
│                                          │
│  [+ Legg til meg som eier]               │
└──────────────────────────────────────────┘
```

| Element                 | Synlighet                                                        | Kall             |
| ----------------------- | ---------------------------------------------------------------- | ---------------- |
| "Legg til meg som eier" | Bruker er ikke allerede eier + arbeidsgiverrettet/utvikler-rolle | `PUT /eiere/meg` |

_(Visning av selve eier-listen holdes utenfor i første mvp for å unngå kompleksitet med overflow og uforholdsmessig lange lister)._

Klikk på "Legg til meg som eier" åpner en bekreftelsesdialog før kallet sendes:

> **Bli eier av dette treffet?**
>
> Som eier får du tilgang til å jobbe med treffet. Det innebærer blant annet at du kan se påmeldte kandidater, sende invitasjoner og se svarstatus.
>
> [Avbryt] [Bekreft]

### 2.2 Synlighet og skjuling

Siden vi i første omgang ikke viser listen over alle eiere, vil infoboksen for "Eierskap" i praksis kun ha et formål for de som *ikke* er eiere ennå, men som har rettigheter til å bli det (`ARBEIDSGIVER_RETTET` eller `UTVIKLER`). 
For brukere som allerede er eier, kan man vurdere å skjule komponenten helt eller bare vise teksten "Du er eier av dette treffet", inntil en faktisk oversikt over alle eiere eventuelt implementeres.

### 2.3 Hendelsesvisning

For at eierskap-hendelser vises i hendelsesloggen (`Hendelser.tsx`) må disse stedene oppdateres:

- **`constants.ts`** – legg til `EIER_LAGT_TIL` i `RekrutteringstreffHendelsestype` og tilhørende label (`'eier lagt til'`)
- **`HentHendelseIkon.tsx`** – ikon for `EIER_LAGT_TIL` (`PersonPlusIcon` fra `@navikt/aksel-icons`)
- **allehendelser-API** – berik eier-hendelser med navIdent fra `hendelse_data` slik at det vises i "Gjelder"-kolonnen

---

## 3. Datamodell

`eiere text[]` er allerede på plass. I tillegg legges det til støtte for flere kontorer:

### Flyway-migrasjon: `kontorer text[]`

1. Legg til kolonne `kontorer text[]` på `rekrutteringstreff`
2. Kopier eksisterende `opprettet_av_kontor_enhetid` inn i den nye listen: `UPDATE rekrutteringstreff SET kontorer = ARRAY[opprettet_av_kontor_enhetid] WHERE opprettet_av_kontor_enhetid IS NOT NULL`
3. **Ikke slett `opprettet_av_kontor_enhetid`** i dette scriptet – verifiseres først, fjernes i eget script etterpå.

---

## 4. Testing

**Backend** (`RekrutteringstreffEierTest.kt`, komponenttester):

- Legg til meg: 201, idempotens (200), mangler rolle (403)
- Hendelse `EIER_LAGT_TIL` logges i `rekrutteringstreff_hendelse`
- Flyway: `kontorer` populeres korrekt fra `opprettet_av_kontor_enhetid`

**Frontend:**

- "Legg til meg" skjult om allerede eier

---

## 5. Arbeidsrekkefølge

1. DB-migrasjon: legg til `kontorer text[]` og kopier `opprettet_av_kontor_enhetid`
2. Backend: `PUT /eiere/meg` + hendelseslogging + tester
3. Frontend: Sette opp `Eierskap`-infoboks med knappen for ikke-eiere i `OmTreffetForIkkeEier`
4. Frontend: hendelsestyper i `constants.ts`, ikon, allehendelser-berikelse
5. Gjennomgang: tilgangskontroll + Playwright-test
6. Verifiser migrasjon → eget script for å slette `opprettet_av_kontor_enhetid`

---

## 6. Antagelser (ikke endelige beslutninger)

- **Maks eiere:** Ingen øvre grense foreløpig – dekker behovet uten ekstra kompleksitet.
- **Hvem kan legge til seg selv:** Alle med arbeidsgiverrettet-rolle kan legge til seg selv, uavhengig av kontor. Hendelsen logges, og en infoboks forklarer hva det innebærer.
- **Kontorer på treff:** Et treff kan ha flere kontorer. Alle på de registrerte kontorene kan se treffet ved filtrering på kontor. Ingen kontorbasert synlighetsstyring planlagt.
- **Fjerne eier / kontor / overføring:** Ikke planlagt nå – legges til ved behov.
