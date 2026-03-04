# Plan: Administrere eiere på rekrutteringstreff

## Bakgrunn og status

Grunnstrukturen for eierskap er allerede på plass:

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

## Regler for eierskap

- Minst én eier til enhver tid (allerede håndhevet i `slettEier`)
- Maks **3 eiere** (ny regel)
- Oppretteren blir automatisk første eier (allerede slik)
- Kun **arbeidsgiverrettet-** eller **utviklerbruker** kan bli eier
- Eier kan:
  - Redigere treffet
  - Invitere jobbsøkere og arbeidsgivere
  - Legge til og fjerne eiere (inntil maks 3 / minst 1)
  - Se hvem andre eiere er
- Jobbsøkerrettet-rolle har lesetilgang, men kan ikke endre eiere

### Åpent designvalg: hvem kan legge til seg selv?

Forslag: Alle med **arbeidsgiverrettet-rolle** (og eventuelle pilotkontortilgang) kan legge til seg selv – uten krav om samme kontor som treffet. Dette er enklest å implementere og gir fleksibilitet.

Alternativ: Kun brukere på **samme kontor** (`opprettet_av_kontor_enhetid`) kan legge seg til. Krever at frontend sender kontoret i requesten og at backend sammenligner. Gir tettere kontroll, men begrenser mer.

**Anbefaling: Start uten kontorkrav – kan strammes til senere.**

---

## 1. Backend

### 1.1 Maks 3 eiere – validering i EierController

`PUT /api/rekrutteringstreff/{id}/eiere` må validere at de nye eierne ikke medfører mer enn 3 totalt.

```kotlin
// I leggTil()-handlerne:
val eksisterendeEiere = eierRepository.hent(id)?.tilNavIdenter() ?: emptyList()
val combined = (eksisterendeEiere + eiere).distinct()
if (combined.size > 3) {
    throw BadRequestResponse("Maks 3 eiere er tillatt per rekrutteringstreff")
}
```

Ny feilkode for frontend: `MAKS_EIERE_NÅDD` (HTTP 400).

### 1.2 Nytt endepunkt: legg til deg selv

```
PUT /api/rekrutteringstreff/{id}/eiere/meg
```

- Ingen request body
- Autentisert bruker legger sin egen navIdent til eierlisten
- Krav: rolle `ARBEIDSGIVER_RETTET` (eller `UTVIKLER`)
- Krav: maks 3 eiere ikke overskredet
- Idempotent: allerede eier → 200 OK uten feil

```kotlin
private fun leggTilMeg(): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val id = TreffId(ctx.pathParam("id"))
    val navIdent = ctx.authenticatedUser().extractNavIdent()

    val eiere = eierRepository.hent(id)?.tilNavIdenter()
        ?: throw NotFoundResponse("Rekrutteringstreff ikke funnet")

    if (eiere.contains(navIdent)) {
        ctx.status(200)
        return@let
    }
    if (eiere.size >= 3) {
        throw BadRequestResponse("Maks 3 eiere er tillatt")
    }

    eierRepository.leggTil(id, listOf(navIdent))
    // logg hendelse
    ctx.status(201)
}
```

### 1.3 Hendelseslogging

Alle eierskapsoperasjoner logges i `rekrutteringstreff_hendelse` med nye hendelsestyper:

| Hendelsestype   | Beskrivelse            | `hendelse_data`                    |
| --------------- | ---------------------- | ---------------------------------- |
| `EIER_LAGT_TIL` | En ny eier er lagt til | `{ "navIdentLagtTil": "A123456" }` |
| `EIER_FJERNET`  | En eier er fjernet     | `{ "navIdentFjernet": "A123456" }` |

`aktøridentifikasjon` = navIdent til den som utfører operasjonen (ikke den som legges/fjernes).

Logging skjer i `EierService` (ikke `EierRepository`) og injiseres via eksisterende hendelsesmekanisme.

### 1.4 Tilgangskontrollsjekk – eksisterende endepunkter

Gjennomgå at alle endepunkter som krever eier-tilgang kaller `eierService.erEierEllerUtvikler()`:

- `PUT /eiere` – ✅ allerede sjekket
- `DELETE /eiere/{navIdent}` – ✅ allerede sjekket
- `PUT /eiere/meg` – ny, legges til
- Rediger treff, invitasjon m.m. – verifiser at disse bruker `erEierEllerUtvikler`

---

## 2. Frontend – GUI

### 2.1 Plassering

Eier-seksjonen plasseres i `OmTreffetForEier.tsx`, under "Om treffet"-boksen, som et eget `InfoBoks`-kort.

### 2.2 Visning av eiere (`EiereSeksjon`)

```
┌──────────────────────────────────────────────────┐
│  Eiere                                           │
│                                                  │
│  A123456 – Ola Nordmann     [Fjern]              │
│  Z999999 – Kari Hansen      [Fjern]              │
│                                                  │
│  [+ Legg til meg som eier]                       │
│                                                  │
│  (Maks 3 eiere. 2 av 3 brukt.)                  │
└──────────────────────────────────────────────────┘
```

**Felter og knapper:**

| Element                                             | Synlighet                                                                                 | Handling                   |
| --------------------------------------------------- | ----------------------------------------------------------------------------------------- | -------------------------- |
| Navneliste med navIdent (og navn hvis tilgjengelig) | Alltid for eiere                                                                          | –                          |
| Fjern-knapp per eier                                | Kun om antall eiere > 1, og innlogget bruker er eier/utvikler                             | `DELETE /eiere/{navIdent}` |
| "Legg til meg som eier"-knapp                       | Vises om: bruker er ikke allerede eier + antall < 3 + rolle = arbeidsgiverrettet/utvikler | `PUT /eiere/meg`           |
| Kapasitetsindikator                                 | Alltid                                                                                    | "X av 3 eiere"             |

> **Navn på navidents:** Backend returnerer kun navIdenter i dag. Enten kan backend berike med navn (via norg2 eller modiacontextholder), eller frontend kan gjøre det via eksisterende kandidatnavn-oppslag. Enklest i første omgang: vis kun navIdent.

### 2.3 API-kall fra frontend

Bruk eksisterende mønster med SWR:

```typescript
// /app/api/rekrutteringstreff/[...slug]/eiere/useEiere.ts
export function useEiere(rekrutteringstreffId: string) {
  return useSWR<string[]>(
    `/api/rekrutteringstreff/${rekrutteringstreffId}/eiere`,
  );
}
```

```typescript
// Legg til meg:
async function leggTilMeg(rekrutteringstreffId: string) {
  await fetch(`/api/rekrutteringstreff/${rekrutteringstreffId}/eiere/meg`, {
    method: "PUT",
  });
}

// Fjern eier:
async function fjernEier(rekrutteringstreffId: string, navIdent: string) {
  await fetch(
    `/api/rekrutteringstreff/${rekrutteringstreffId}/eiere/${navIdent}`,
    {
      method: "DELETE",
    },
  );
}
```

### 2.4 Synlighet for ikke-eiere

Ikke-eiere (jobbsøkerrettet) ser treffets eiere som navIdenter i `OmTreffetForIkkeEier` – men uten mulighet til å legge til/fjerne. Dette er informasjonsvisning kun, og bør vurderes ut fra behov.

---

## 3. Datamodell – ingen DB-endringer nødvendig

`eiere text[]` er allerede i databasen. Det trengs ingen ny Flyway-migrasjon for selve eierskapsstrukturen.

Hva som _kan_ kreve migrasjon:

- Om man ønsker en separat `eier`-tabell for bedre normalisering og metadata (hvem la til hvem, when). Ikke nødvendig i v1.
- Dersom man ønsker å indeksere `eiere`-kolonnen for raske oppslag (lavt prioritet).

---

## 4. Testing

### Backend (komponenttester)

| Test                                  | Scenario                                  |
| ------------------------------------- | ----------------------------------------- |
| Legg til meg som eier                 | 201, idempotens (200 om allerede eier)    |
| Legg til meg – maks 3 nådd            | 400                                       |
| Legg til meg – mangler rolle          | 403                                       |
| Hent eiere                            | 200 med liste                             |
| Slett eier – siste eier               | 400                                       |
| Slett eier – ikke eier                | 403                                       |
| Hendelser logges ved legg til / slett | verifiser i `rekrutteringstreff_hendelse` |

Fila `RekrutteringstreffEierTest.kt` bør dekke disse scenariene (komponenttest med reell database).

### Frontend

- Eiere-seksjonen vises kun for eier-visning
- "Fjern"-knapp skjules om kun 1 eier
- "Legg til meg"-knapp skjules om innlogget bruker allerede er eier
- "Legg til meg"-knapp skjules om 3 eiere allerede er registrert

---

## 5. Arbeidsrekkefølge

```
Steg 1 – Backend: validering og nytt endepunkt
  [x] Maks 3 eiere-validering i leggTil()
  [x] PUT /eiere/meg
  [x] Hendelseslogging EIER_LAGT_TIL og EIER_FJERNET
  [x] Komponenttester

Steg 2 – Frontend: EiereSeksjon
  [x] useEiere() SWR-hook
  [x] EiereSeksjon-komponent (liste + knapper)
  [x] Integrasjon i OmTreffetForEier

Steg 3 – Gjennomgang
  [x] Verifiser alle controller-endepunkter bruker erEierEllerUtvikler()
  [x] Avtale om kontor-validering skal legges til
  [x] Playwright-test for eier-GUI
```

---

## 6. Åpne spørsmål

1. **Vise navn på eiere?** Kun navIdent i dag. Trenger vi navn? (Alternativ: oppslag via Axios mot norg2 eller navn fra token.)
2. **Kontorkrav for legg til meg?** Start uten – stram til etter behov.
3. **Kan eier legge til andre?** I dag brukes `PUT /eiere` som tar en liste. Bør dette fjernes til fordel for kun `PUT /eiere/meg`? Anbefaling: Behold begge – `PUT /eiere` kan brukes av utviklere/admin.
4. **Ikke-eier-visning av eiere?** Bestem om jobbsøkerrettet skal se eiere eller kun eiere selv.
