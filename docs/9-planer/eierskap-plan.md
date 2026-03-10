# Plan: Administrere eiere på rekrutteringstreff

## Status

| Komponent                                              | Status                           |
| ------------------------------------------------------ | -------------------------------- |
| `eiere text[]` i DB                                    | ✅ Implementert                  |
| `GET /eiere`, `DELETE /eiere/{navIdent}`               | ✅ Implementert                  |
| `EierService.erEierEllerUtvikler()`                    | ✅ Implementert                  |
| `RekrutteringstreffDto.eiere`                          | ✅ Implementert                  |
| `useErTreffEier()` i frontend                          | ✅ Implementert                  |
| Legg til meg selv som eier (`PUT /eiere/meg`)          | ✅ Implementert                  |
| Hendelseslogging ved eierskap-endringer                | ✅ Implementert                  |
| GUI for eiere (knapp + bekreftelsesdialog)             | ✅ Implementert                  |
| `EIER_LAGT_TIL` i frontend constants og ikon           | ✅ Implementert                  |
| `KONTOR_LAGT_TIL` hendelseslogging                     | ✅ Implementert                  |
| `KONTOR_LAGT_TIL` i frontend constants og ikon         | ✅ Implementert                  |
| Feilhåndtering (visVarsel) i frontend mutations        | ✅ Implementert                  |
| Race condition-sikring (`leggTilMegSomEier`)           | ✅ Implementert                  |
| Hendelseslogging for slett eier (`DELETE /eiere`)      | ✅ Implementert                  |
| Min 1 eier-guard (SQL-nivå) ved slett                  | ✅ Implementert                  |
| Komponenttester for `/eiere/meg`                       | ✅ Implementert                  |
| `kontorer text[]` DB-migrasjon (V2)                    | ✅ Implementert                  |
| Støtte for flere kontorer på treff (`kontorer text[]`) | ✅ Implementert                  |
| Tilgangskontroll-gjennomgang                           | ✅ Implementert                  |
| Playwright-test for eierskap-knapp                     | ✅ Implementert                  |
| Slett `opprettet_av_kontor_enhetid` (V3-migrasjon)     | ❌ Gjenstår (verifiser V2 først) |

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
- Idempotent: gir alltid 200 (OK) enten brukeren allerede var eier eller ble lagt til ny.

**Begrunnelse for metodevalg (PUT) og idempotens:**
Vi velger `PUT` fordi endepunktet representerer en operasjon for å "sikre en tilstand" (brukeren _skal_ være eier). Ved `POST` ville forventningen gjerne vært å opprette en ny, unik ressurs hver gang, noe som ofte gir en "Conflict" (409) feilmelding om ressursen/knytningen finnes fra før. Ved å gjøre operasjonen idempotent med `PUT`, gjør vi klientkoden mer robust, for eksempel i møte med nettverksproblemer der klienten forsøker operasjonen på nytt. Returkoden er alltid 200 OK, uansett om brukeren allerede var eier eller ble lagt til som ny eier, siden tilstanden på server er sikret i begge tilfeller.

### 1.2 Hendelseslogging

Logges i `rekrutteringstreff_hendelse` fra `EierService`:

| Hendelsestype     | `subjekt_id` / `subjekt_navn` | `aktøridentifikasjon`       |
| ----------------- | ----------------------------- | --------------------------- |
| `EIER_LAGT_TIL`   | navIdent (f.eks `A123456`)    | Den som utfører operasjonen |
| `KONTOR_LAGT_TIL` | kontorEnhetId (f.eks `0318`)  | Den som utfører operasjonen |

### 1.3 Tilgangskontroll – gjennomgang

Sjekk at alle operasjoner som krever eierskap bruker `eierService.erEierEllerUtvikler()`: rediger treff, invitasjon, eier-endringer.

---

## 2. Frontend – GUI

### 2.1 Eierskap-knapp

Knappen for å bli eier plasseres i visningen for de som ikke er eier (`OmTreffetForIkkeEier.tsx`), uten å ligge inne i noen egen rammekomponent/infoboks på dette stadiet.

```
[+ Legg til meg som eier]
```

| Element                 | Synlighet                                                        | Kall             |
| ----------------------- | ---------------------------------------------------------------- | ---------------- |
| "Legg til meg som eier" | Bruker er ikke allerede eier + arbeidsgiverrettet/utvikler-rolle | `PUT /eiere/meg` |

_(Visning av selve eier-listen holdes utenfor i første mvp for å unngå kompleksitet med overflow og uforholdsmessig lange lister)._

Klikk på "Legg til meg som eier" åpner en bekreftelsesdialog før kallet sendes:

> **Bli medeier av dette treffet?**
>
> Som eier får du tilgang til å jobbe med treffet. Det innebærer blant annet at du kan se påmeldte kandidater, sende invitasjoner og se svarstatus.
>
> [Avbryt] [Bekreft]

### 2.2 Synlighet og skjuling

Siden vi i første omgang ikke viser listen over alle eiere, vil knappen for å bli eier i praksis kun være relevant for de som _ikke_ er eiere ennå, men som har rettigheter til å bli det (`ARBEIDSGIVER_RETTET` eller `UTVIKLER`). Denne knappen legges i visningen `OmTreffetForIkkeEier`.
For brukere som allerede er eier, dukker ikke komponenten/knappen opp i det hele tatt.

### 2.3 Hendelsesvisning

For at eierskap-hendelser vises i hendelsesloggen (`Hendelser.tsx`) må disse stedene oppdateres:

- **`constants.ts`** – legg til `EIER_LAGT_TIL` og `KONTOR_LAGT_TIL` i `RekrutteringstreffHendelsestype` og tilhørende labels
- **`HentHendelseIkon.tsx`** – ikon for `EIER_LAGT_TIL` (`PersonPlusIcon`) og `KONTOR_LAGT_TIL` (`Buildings3Icon`)
- **allehendelser-API** – berik hendelser med data direkte fra kolonnene `subjekt_id` og `subjekt_navn` slik at det vises i "Gjelder"-kolonnen

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

1. ✅ DB-migrasjon: legg til `kontorer text[]` og kopier `opprettet_av_kontor_enhetid` (V2)
2. ✅ Backend: `PUT /eiere/meg` + hendelseslogging + tester
3. ✅ Frontend: Legge til "+ Legg til meg som eier"-knappen for ikke-eiere i `OmTreffetForIkkeEier`
4. ✅ Frontend: hendelsestyper i `constants.ts`, ikon, allehendelser-berikelse (via query-endring i repository)
5. ✅ Gjennomgang: tilgangskontroll – `InnleggController` manglet eier-sjekk på `POST`, `PUT`, `DELETE` – fikset
6. ✅ Playwright-test: ikke-eier ser knappen, klikker, bekrefter, og får suksessvarsel (`ikke-eier-visning.spec.ts`)
7. ❌ Verifiser V2-migrasjon i prod → lag V3-migrasjon som sletter `opprettet_av_kontor_enhetid`
8. ✅ Backend + frontend: `kontorer text[]` eksponert i DTO, `PUT /kontorer/mitt` (idempotent), kontor-filter oppdatert, UI viser kontorer og lar eiere legge til sitt kontor

---

## 6. Antagelser (ikke endelige beslutninger)

- **Maks eiere:** Ingen øvre grense foreløpig – dekker behovet uten ekstra kompleksitet.
- **Hvem kan legge til seg selv:** Alle med arbeidsgiverrettet-rolle kan legge til seg selv, uavhengig av kontor. Hendelsen logges, og en infoboks forklarer hva det innebærer.
- **Kontorer på treff:** Et treff kan ha flere kontorer. Alle på de registrerte kontorene kan se treffet ved filtrering på kontor. Ingen kontorbasert synlighetsstyring planlagt.
- **Fjerne eier / kontor / overføring:** Ikke planlagt nå – legges til ved behov.
