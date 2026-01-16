# GitHub Copilot Arbeidsflyt

> En prosess for å bruke GitHub Copilot effektivt på kompliserte oppgaver

## Innholdsfortegnelse

- [Kom i gang med Copilot](#kom-i-gang-med-copilot)
- [Valg av modell](#valg-av-modell)
- [Agent Mode](#agent-mode)
- [Planleggingsfasen](#planleggingsfasen)
- [Implementering](#implementering)
- [Kodegjennomgang](#kodegjennomgang)
- [Pull Request](#pull-request)

---

## Kom i gang med Copilot

### 1. Få tilgang via NAV

1. Gå til NAV sin **Self Service**-portal
2. Be om tilgang til godkjent Copilot-oppkobling
3. Vent på godkjenning

### 2. Sett opp Copilot i IntelliJ

1. Åpne IntelliJ IDEA
2. Gå til **Settings** → **Plugins**
3. Søk etter "GitHub Copilot" og installer
4. Restart IntelliJ
5. Logg inn med din GitHub-konto som er koblet til NAV-tilgang

---

## Valg av modell

For kompliserte oppgaver i public repos, velg en modell som håndterer stor kontekst og tyngre oppgaver:

| Modell                            | Beskrivelse                             |
| --------------------------------- | --------------------------------------- |
| **Claude Opus 4.5** eller nyere   | God på store kodebaser                  |
| **GPT 5.2** eller nyere           | Solid alternativ for komplekse oppgaver |
| **GPT 5.1 Codex Max** eller nyere | Spesialisert for kode                   |
| **Gemini 3 Pro** eller nyere      | Godt alternativ med stort kontekstvindu |

> **Tips:** Sjekk gjerne oppdaterte benchmarks(for eksempel https://lmarena.ai/leaderboard) før du velger – modellene forbedres kontinuerlig.

---

## Agent Mode

### Hva er Agent Mode?

Agent Mode gir Copilot tilgang til å navigere mellom filer i hele prosjektet. Dette er essensielt for større oppgaver som krever endringer på tvers av filer.

### Aktivering

1. Velg **Agent Mode** ved siden av modellvalget
2. Husk at den da får tilgang til filer i hele prosjektet som er åpent i IntelliJ

### Workspace-oppsett for multi-repo

Det er mulig å ha en rot med flere prosjekter som snakker sammen:

```
workspace/
├── rekrutteringsbistand-frontend/    # Frontend
├── rekrutteringstreff-backend/       # Backend
└── annen-app/                        # App som hovedappen snakker med
```

> Sjekk ut alle repos på ønsket nivå før du starter – dette gir Copilot full kontekst. Og åpne Intellij på roten i steden for ett av prosjektene. Du kan ha en annen intellj instans for et av underprosjektene om du ønsker det, men copilot må brukes på intellij instans som er på roten for de prosjektene du sjekket ut spesifikt for oppgaven.

---

## Planleggingsfasen

### Steg 1: Be om en plan først

Skriv i prompten at du **først bare vil ha en plan, ikke kode ennå**. Be om:

- Hvilke filer som bør endres
- Hvilke nye filer som bør lages
- Hvilke tester som bør være med
- Overordnet arkitektur/tilnærming

**Eksempel-prompt:**

```
Jeg vil implementere [feature X].

Gi meg først en plan uten kode. Inkluder:
- Hvilke eksisterende filer som må endres
- Hvilke nye filer som bør lages
- Hvilke tester som trengs
- Eventuell rekkefølge på implementering

Ikke skriv mye kode ennå, fokuser på selve planen.
```

### Steg 2: Iterer på planen

1. Gå gjennom planen nøye
2. Kom med innspill og justeringer
3. Gjenta med nye prompter til planen ser bra ut

### Steg 3: Del med teamet

1. **Presenter planen** til andre i teamet
2. Ta imot innspill
3. Juster planen basert på feedback
4. Gå tilbake til Copilot med forbedringene

---

## Implementering

Når du er fornøyd med planen, har du to valg:

### Alternativ A: Manuell implementering

Implementer selv basert på planen. Bruk Copilot for forslag underveis, men styr prosessen manuelt.

### Alternativ B: La Copilot utføre

#### Forberedelser

1. **Lag en branch:**

2. **Kontroller at det ikke er uinnsjekket kode i branchen du er på**

#### Sikkerhetshensyn

| Gjør                               | Ikke gjør                                                                   |
| ---------------------------------- | --------------------------------------------------------------------------- |
| ✅ Følg med på hva den gjør        | ❌ Ikke slå på "brave mode" om det er mulig                                 |
| ✅ Les bekreftelser nøye           | ❌ Ikke godkjenn for eksempel `rm`-kommandoer blindt                        |
| ✅ Si nei til tilgang utenfor repo | ❌ Ikke gi tilgang til filsystemet utenfor prosjekt du jobber på i oppgaven |

#### Under generering

1. Vær forsiktig når den spør om tilganger
2. Du bør bli spurt om bekreftelser
3. **Si nei til tilgang utenfor repoet du er inne på**

---

## Kodegjennomgang

### Etter generering

1. Trykk **"Keep"** på alle endringer (hvis du startet med ren branch)
2. Åpne **diff mode** i IntelliJ
3. Gå gjennom koden som du pleier når du har skrevet den selv

### Justeringer

- Si fra om hva du vil ha endret
- Ha gjerne flere commits på branchen dersom det blir flere prompter
- Iterer til koden er som du vil ha den

---

## Pull Request

### Steg 1: Opprett PR

```bash
git push -u origin feature/min-feature
```

Opprett PR på GitHub som vanlig.

### Steg 2: Copilot som reviewer

1. Velg **Copilot som reviewer** på PR-en
2. Gå gjennom forslagene
3. Vurder om anbefalingene bør implementeres
4. Gjør eventuelle endringer

### Steg 3: Team-gjennomgang

1. Be om review fra teammedlemmer
2. Vanlig PR-prosess med diskusjon
3. Merge når godkjent

---

## Sjekkliste

- [ ] Tilgang til Copilot via NAV Self Service
- [ ] Copilot-plugin installert i IntelliJ
- [ ] Riktig modell valgt for oppgaven
- [ ] Agent Mode aktivert for multi-fil oppgaver
- [ ] Plan laget og reviewet før implementering
- [ ] Ren branch før Copilot-generering
- [ ] Kode gjennomgått i diff mode
- [ ] Copilot-review på PR
- [ ] Team-review på PR

---

Husk at dette bare er forslag til en prosess, det er mange andre måter å gjøre dette på.
