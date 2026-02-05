# Utviklerrutiner for KI-sjekken (ROB)

Dette dokumentet beskriver rutiner utviklere må følge ved vedlikehold og utvikling av KI-sjekken.

> **Viktig:** Dette dokumentet er referert til i ROS-analysen og skal holdes oppdatert ved endringer i rutiner eller tiltak.

## Innhold

- [Deployment av ny modell i Azure OpenAI](#deployment-av-ny-modell-i-azure-openai)
- [Evaluering og oppgradering av språkmodell](#evaluering-og-oppgradering-av-språkmodell)
- [Endring av systemprompt](#endring-av-systemprompt)
- [Overvåking av Azure OpenAI retningslinjer](#overvåking-av-azure-openai-retningslinjer)
- [Periodisk sjekk av oppsett](#periodisk-sjekk-av-oppsett)
- [Oppdatering av kunnskapsgrunnlag](#oppdatering-av-kunnskapsgrunnlag)
- [Verifisering av hallusinering](#verifisering-av-hallusinering)
- [Feilhåndtering ved loggsletting](#feilhåndtering-ved-loggsletting)
- [Tilgangsstyring og rollebruk](#tilgangsstyring-og-rollebruk)
- [Automatiske tester og benchmarking](#automatiske-tester-og-benchmarking)

---

## Nøkkelinformasjon

| Parameter           | Verdi     | Merknad                           |
| ------------------- | --------- | --------------------------------- |
| Gjeldende modell    | gpt-4.1   | Standard deployment i EU/EØS      |
| Modellversjon utgår | Mars 2026 | Må byttes før denne dato          |
| Benchmark-mål       | 90%       | Minimum treffprosent på testcases |
| Loggretensjon       | 6 måneder | Hardsletting av fritekstfelt      |
| Content filter      | DefaultV2 | Abuse monitoring aktivert         |

---

---

## Deployment av ny modell i Azure OpenAI

**ROS-referanse:** 29023, 29025, 29263, 28415, 27868

**Når:** Ved opprettelse av ny deployment eller bytte av modell i Azure OpenAI.

### Krav til deployment

| Krav                | Beskrivelse                           | Hvorfor                             |
| ------------------- | ------------------------------------- | ----------------------------------- |
| Standard deployment | Velg **Standard** (ikke Global)       | Sikrer at data behandles i EU/EØS   |
| Region              | Norway East eller Sweden Central      | EU/EØS-krav for personvern          |
| Content filter      | DefaultV2 eller sterkere              | Abuse monitoring påkrevd            |
| Navngivning         | Prefiks `toi-` (f.eks. `toi-gpt-4.1`) | Identifisering av teamets ressurser |

### Før deployment – sjekkliste

1. **Logg inn i Azure Portal:** [https://oai.azure.com/resource/deployments](https://oai.azure.com/resource/deployments)
2. **Bekreft riktig miljø:**
   - Verifiser at du er i riktig ressurs (`arbeidsmarked-dev` eller `arbeidsmarked-prod`)
   - Ha et bevisst forhold til om du jobber i **dev** eller **prod** – dobbeltsjekk før du gjør endringer
3. **Verifiser region:**
   - Ressursen må være lokalisert i **Norway East** eller **Sweden Central** (EU/EØS-krav)
4. **Velg Standard deployment:**
   - Velg **Standard** deployment type (ikke Global)
   - ⚠️ **Global deployment overfører data til tredjeland** og er ikke tillatt
5. **Navngi modellen korrekt:**
   - Prefiks modellnavnet med `toi-` (f.eks. `toi-gpt-4.1`)
   - Inkrementer versjonsnummer i kodebasen når modell byttes
6. **Sjekk tokens per minutt (TPM):**
   - Før du sletter gammel ressurs/deployment: noter hvor mange tokens per minutt (TPM) den bruker
   - Sett ny deployment til samme eller høyere TPM-grense
7. **Aktiver Content Filter:**
   - Velg **DefaultV2** content filter eller sterkere (Abuse monitoring)
   - ⚠️ **Abuse monitoring skal IKKE deaktiveres** - Nav kan bli ansvarliggjort for misbruk

### Benchmarking og testing (før produksjon)

1. Kjør full benchmark-suite mot eksisterende testcases (se [Automatiske tester](#automatiske-tester-og-benchmarking))
2. Verifiser at ny modell scorer **minimum 90%** på testsuiten
3. Sammenlign treffprosent med nåværende modell – ny modell bør score like bra eller bedre
4. Undersøk og dokumenter eventuelle testcases som feiler
5. Test manuelt med representative eksempler i dev
6. Test med testcases som systemprompten ikke er trent på (fra domeneekspert)

### Etter deployment

1. Kopier API-nøkkel (ved første gangs oppsett) og legg inn i NAIS secret
2. Oppdater modellnavn/versjon i kodebasen (inkrementer versjon)
3. Oppdater eventuelle miljøvariabler/konfigurasjon i appen
4. Test at integrasjonen fungerer i dev før produksjon
5. **Oppdater ROS-dokumentasjon** i tryggnok med ny modellversjon og begrunnelse
6. Dokumenter endringen i teamets changelog/backlog

---

## Endring av systemprompt

**ROS-referanse:** 27852, 27853

**Når:** Ved behov for å justere KI-sjekken oppførsel.

### Prinsipper for systemprompt

- **Klarspråk:** Alle formuleringer skal være klare og forståelige
- **Unngå overtilpasning:** Prompten skal ikke være for rigid eller detaljert
- **Tematisk oppdeling:** Del opp prompten i temaer for enklere testing
- **Heller for streng:** Prompten skal heller være for streng enn for mild

### Rutine

1. Dokumenter formål med endring i PR-beskrivelse
2. Følg klarspråk-prinsipper (se [ki-tekstvalideringstjeneste.md](../5-ki/ki-tekstvalideringstjeneste.md))
3. **Inkrementer versjonsnummer** i systemprompten manuelt
4. **Oppdater tidspunkt** i systemprompten
5. **Generer ny hash** ved å kjøre `main` i systemprompt-filen
6. Kjør automatiske tester (se [Automatiske tester](#automatiske-tester-og-benchmarking))
7. Verifiser mot benchmarks – minimum 90% treffprosent
8. Test manuelt i dev med representative eksempler
9. Test manuelt i Azure Chat Playground
10. Merge via normal code review

### Rollback

Prompten er versjonskontrollert og kan enkelt reverteres via git. Ved akutt behov:

1. Revert commit i git
2. Deploy til prod
3. Dokumenter årsak i teamets backlog

---

## Overvåking av Azure OpenAI retningslinjer

**ROS-referanse:** 29262, 29023

**Når:** Løpende, minimum kvartalsvis.

### Viktige lenker

| Ressurs                    | URL                                                                                                                               |
| -------------------------- | --------------------------------------------------------------------------------------------------------------------------------- |
| Azure OpenAI dokumentasjon | [learn.microsoft.com/azure/ai-services/openai](https://learn.microsoft.com/en-us/azure/ai-services/openai/)                       |
| Model retirements          | [learn.microsoft.com/.../model-retirements](https://learn.microsoft.com/en-gb/azure/ai-foundry/openai/concepts/model-retirements) |
| Abuse monitoring           | [learn.microsoft.com/.../abuse-monitoring](https://learn.microsoft.com/en-us/azure/ai-foundry/openai/concepts/abuse-monitoring)   |
| Nav GKI-veileder           | [data.nav.no/fortelling/ki](https://data.nav.no/fortelling/ki/index.html)                                                         |

### Rutine

1. Sjekk [Azure OpenAI Service documentation](https://learn.microsoft.com/en-us/azure/ai-services/openai/) for oppdateringer
2. Sjekk [Model retirements](https://learn.microsoft.com/en-gb/azure/ai-foundry/openai/concepts/model-retirements) for å se når modeller utgår
   - ⚠️ **gpt-4.1 (standard deployment) utgår mars 2026** - ny versjon må være på plass før denne dato
3. Les gjennom eventuelle nye retningslinjer
4. Følg [Nav veileder for generativ kunstig intelligens](https://data.nav.no/fortelling/ki/index.html)
5. Vurder om vår bruk er i tråd med retningslinjene
6. Dokumenter avvik og tiltak i teamets backlog

---

## Periodisk sjekk av oppsett

**ROS-referanse:** 29263, 29025

**Når:** Kvartalsvis eller ved endring av infrastruktur.

**Rutine:**

1. Verifiser i Azure Portal at Content Filter er aktivert ("Abuse monitoring")
2. Verifiser at deployment fortsatt er "Standard" (ikke Global)
3. Verifiser at modellen kjører i EU/EØS-region (Norway East / Sweden Central)

---

## Oppdatering av kunnskapsgrunnlag

**ROS-referanse:** 27544

**Når:** Ved endringer i lovverk, diskrimineringsgrunnlag, eller NAV-retningslinjer.

**Rutine:**

1. Identifiser hvilke deler av prompten som påvirkes
2. Oppdater prompt med ny informasjon
3. Oppdater benchmarks/testcases om nødvendig
4. Følg rutine for [Endring av systemprompt](#endring-av-systemprompt)

---

## Evaluering og oppgradering av språkmodell

**ROS-referanse:** 27868, 29023, 29025

**Når:** Ved valg av ny modell, oppgradering av eksisterende, eller når Microsoft varsler at modellversjon utgår.

### Evaluering (før beslutning)

1. Dokumenter hvilken modell som vurderes og hvorfor
2. Verifiser at modellen støtter **standard deployment i EU/EØS** (ikke Global)
3. Les Microsofts release notes for ny versjon
4. Sjekk [Model retirements](https://learn.microsoft.com/en-gb/azure/ai-foundry/openai/concepts/model-retirements) for utløpsdatoer

### Testing (før produksjonssetting)

1. Oppdater modellversjon i dev/test først
2. Kjør full benchmark-suite mot eksisterende testcases (se [ki-tekstvalideringstjeneste.md](../5-ki/ki-tekstvalideringstjeneste.md))
3. Sammenlign treffprosent med nåværende modell
4. Verifiser at **90%-målet** for testcases opprettholdes
5. Dokumenter eventuelle risikoer eller endringer i oppførsel
6. Få godkjenning fra teamet før produksjonssetting

### Gjennomføring

1. Følg rutine for [Deployment av ny modell i Azure OpenAI](#deployment-av-ny-modell-i-azure-openai)
2. **Oppdater ROS-dokumentasjon** i tryggnok med ny modellversjon
3. Oppdater dokumentasjon med ny modellversjon
4. Deploy til prod med toggle klar for rask rollback

---

## Verifisering av hallusinering

**ROS-referanse:** 27854

**Når:** Ved oppgradering av modell, endring av prompt, eller ved mistanke om feil.

**Rutine:**

1. Kjør testcases som verifiserer faktisk innhold i responser
2. Sjekk at KI-sjekken ikke genererer informasjon utover det som etterspørres
3. Verifiser at vurderingene er basert på faktisk innhold i teksten
4. Dokumenter eventuelle avvik og korriger prompt om nødvendig

---

## Feilhåndtering ved loggsletting

**ROS-referanse:** 29330

**Når:** Dersom automatisk sletting av logger feiler.

### Loggpolicy

| Type data    | Retensjon   | Handling             |
| ------------ | ----------- | -------------------- |
| Fritekstfelt | 6 måneder   | Hardsletting         |
| Metadata     | Kan bevares | For statistikkformål |

### Rutine ved feil

1. Motta varsel om feilet slettejobb (via monitorering)
2. Undersøk årsak til feil
3. Kjør manuell sletting om nødvendig
4. Verifiser at logger eldre enn retensjon er slettet
5. Dokumenter hendelsen og ev. tiltak

### Forebyggende tiltak

- Overvåk slettejobber via dashboards
- Sett opp varsler ved feil
- Test slettejobber regelmessig i dev

---

## Tilgangsstyring og rollebruk

**ROS-referanse:** 29337

**Når:** Ved tildeling eller fjerning av utviklertilgang.

### Roller

| Rolle       | Tilgang                                      | Hvem               |
| ----------- | -------------------------------------------- | ------------------ |
| Utvikler    | Full tilgang inkl. KI-logg, produksjonsmiljø | Teamets utviklere  |
| Admin (Toi) | Tilgang til løsningen, ikke infrastruktur    | Vurderes ved behov |

### Rutine for tildeling

1. Verifiser at personen har tjenestlig behov
2. Gjennomfør opplæring i testmiljø før tilgang til prod
3. Gå gjennom dette dokumentet (KI-rutiner for utviklere)
4. Gå gjennom rollebeskrivelse i teamets Loop-dokument
5. Dokumenter tildeling

### Rutine for fjerning

1. Fjern utviklertilgang når den ikke lenger er nødvendig
2. Dokumenter fjerning
3. Vurder behov jevnlig (f.eks. ved prosjektslutt)

### Bruk av produksjonsmiljø

- ⚠️ **Banner i løsningen** viser at man er i produksjonsmiljø
- Vær bevisst på at du jobber med reelle data
- Følg beste praksis beskrevet i dette dokumentet

---

## Automatiske tester og benchmarking

**ROS-referanse:** 28415, 27867, 27852, 27868

**Når:** Ved endring av systemprompt, modell, eller før produksjonssetting.

### Testfil

Testene ligger i:

```
apps/rekrutteringstreff-api/src/test/kotlin/no/nav/toi/rekrutteringstreff/ki/KiTekstvalideringParameterisertTest.kt
```

### Slik fungerer testene

1. I toppen av test-filen ligger en liste med test-prompts og forventet vurdering:
   - `false` = godta (prompten bryter ikke med retningslinjer)
   - `true` = ikke godta (prompten bryter med retningslinjer)
2. Testen sender hver tekst til Azure OpenAI med den definerte systemprompten
3. Testen sjekker om vurderingen stemmer med forventet resultat
4. Etter alle tester skrives en liste over avvik med begrunnelse

### Beregning av nøyaktighet

```
ROBs nøyaktighet = (antall test-prompts - antall avvikende resultat) / antall test-prompts * 100
```

**Krav:** Minimum **90%** nøyaktighet for produksjonssetting.

### Rutine

1. Kjør testene før hver endring i systemprompt eller modell
2. Undersøk alle testcases som feiler
3. Dokumenter hvorfor de feilet og vurder om det er akseptabelt
4. Test også med testcases som systemprompten ikke er trent på (fra domeneekspert)
5. Sammenlign treffprosent før og etter endring
6. Bruk offisielle benchmarks for å vurdere modellvalg

### Manuell testing

I tillegg til automatiske tester:

1. Test i Azure Chat Playground med representative eksempler
2. Test grensetilfeller som er vanskelige å automatisere
3. Test manipulasjonsforsøk (jf. ROS 27546)
4. Dokumenter resultater i testscript i Loop

---

## Se også

- [ki-tekstvalideringstjeneste.md](../5-ki/ki-tekstvalideringstjeneste.md) - Teknisk dokumentasjon av KI-tekstvalideringstjenesten
- [tilgangsstyring.md](../3-sikkerhet/tilgangsstyring.md) - Tilgangskontroll og roller
- [akseptansetester.md](../7-akseptansetest-og-ros/akseptansetester.md) - Akseptansetester inkl. KI-tester
