# ROS-tiltak for KI-sjekken (ROB)

Dette dokumentet gir oversikt over risikoer fra ROS-analysen som er spesifikke for KI-sjekken (ROB), og status på tiltak. Dokumentet oppdateres manuelt og er ikke automatisk koblet til akseptansetester.

**Statusforklaring:**

- ✅ Impl. = Teknisk implementert i løsningen
- 🔄 Delvis = Delvis implementert eller planlagt (teknisk)
- ⚠️ N/A = Manuell rutine utenfor systemdok (brukerrutiner, opplæring, etc.)

## Oversikt over risikoer

| ROS-ID | Risiko                                                  | Status    | Hovedtiltak                                          |
| ------ | ------------------------------------------------------- | --------- | ---------------------------------------------------- |
| 29337  | Utviklertilgang til logger tildeles for bredt           | 🔄 Delvis | Banner i prod, egen admin-rolle (+ manuelle rutiner) |
| 29330  | Logger lagres for lenge/for mye                         | ✅ Impl.  | Automatisk sletting, rutiner ved feil                |
| 29263  | Abuse monitoring skrus av                               | ✅ Impl.  | Beholder abuse monitoring, sterkeste filter          |
| 29262  | Ikke følger retningslinjer for Azure OpenAI             | ⚠️ N/A    | Manuell rutine                                       |
| 29025  | Feil deployment av modell                               | ✅ Impl.  | Kun standard deployment i EU/EØS                     |
| 29023  | Modellversjon utgår                                     | ✅ Impl.  | Toggle for deaktivering, teste før oppgradering      |
| 28415  | KI-sjekken treffer ikke bra nok på testcases            | ✅ Impl.  | Benchmarks, 90% mål, undersøke feil                  |
| 27979  | KI-sjekken gir falsk trygghet                           | 🔄 Delvis | Ingen grønn "ok", tekst i løsning                    |
| 27868  | Mangelfull evaluering av språkmodell                    | ✅ Impl.  | Dokumentert i ki-moderering.md                       |
| 27867  | Mangelfull eller utilstrekkelig testing                 | ✅ Impl.  | Logging, manuell test, automatiske tester            |
| 27854  | Hallusinering av fakta                                  | ✅ Impl.  | Risikovurdert modell, jevnlige tester                |
| 27853  | Kompleksitet i systemprompt (overtilpasning)            | ✅ Impl.  | Klarspråk, veiledning, tematisk oppdeling            |
| 27852  | Feil ved oppdatering av prompten                        | ✅ Impl.  | Versjonskontroll, reverserbar, auto-tester           |
| 27547  | KI identifiserer ikke diskriminerende/personopplysning  | ✅ Impl.  | Admin-kontroll, tester (+ manuelle rutiner)          |
| 27546  | KI-sjekken manipuleres                                  | 🔄 Delvis | Overstyre, logging, penetrasjonstesting              |
| 27545  | Arrangør gjør ikke selvstendig vurdering                | 🔄 Delvis | Informasjonstekst, design                            |
| 27544  | Mangelfull oppdatering av kunnskapsgrunnlag             | ⚠️ N/A    | Manuell rutine                                       |
| 27542  | Feil/dårlig veiledning pga manglende kontekstforståelse | ✅ Impl.  | Testing av grensetilfeller                           |
| 27321  | Personopplysninger av særlig kategori i tekst           | 🔄 Delvis | Avvikslenke, validering, testing                     |

---

## Detaljert gjennomgang

### 29337 - Utviklertilgang til logger tildeles for bredt

**Risiko:** Utviklertilgang gir større tilgang enn kun til loggene. Hvis denne tilgangen gis til andre enn teamet (f.eks. domeneekspert som trenger tilgang til loggene, men ikke andre tilganger som ligger i utviklerrollen).

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Legge inn banner i løsning at man er i prod | 🔄 Planlagt | Teknisk |
| Opplæring i test før utviklertilgang tildeles | ⚠️ N/A | Manuell rutine |
| Lage rutine for bruk av rollene (hva er lov/ikke lov) | ⚠️ N/A | Manuell rutine |
| Lage egen administrasjonstilgang (Toi) som kun gir tilgang til løsningen | 🔄 Planlagt | Teknisk |
| Fjerne tilgang når den ikke er nødvendig lenger | ⚠️ N/A | Manuell rutine |

---

### 29330 - Logger lagres for lenge eller i for stort omfang

**Risiko:** Interne logger lagres utover det som er nødvendig for formålet, enten over lengre tid eller med flere opplysninger enn nødvendig. Konsekvenser: økt eksponering av sensitive opplysninger, høyere sannsynlighet for uautorisert innsyn, brudd på personvernlovgivning.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lage rutiner for hvis slettingen feiler (utvikler) | ✅ Implementert | |
| Automatisk sletting | ✅ Implementert | |

---

### 29263 - Abuse monitoring skrus av

**Risiko:** Nav kan bli ansvarliggjort for misbruk av OpenAI dersom abuse monitoring deaktiveres. Konsekvenser: rettslige konsekvenser, omdømmetap, økonomisk tap.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lage side på Loop med hva vi får lov til å lage (for utviklere) | ⚠️ N/A | Ekstern dokumentasjon |
| Velge sterkeste, moderne filter i henhold til krav | ✅ Implementert | |

---

### 29262 - Ikke følger retningslinjer for Azure OpenAI/KI-forordningen

**Risiko:** Teamet følger ikke retningslinjer for bruk av Azure OpenAI utformet av Nav eller juridiske krav (KI-forordningen).

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Følge etablerte retningslinjer for ansvarlig KI i Nav | ⚠️ N/A | Ekstern rutine |
| Etablere retningslinjer i seksjonen/teamet for ny instans av Azure OpenAI | ⚠️ N/A | Manuell rutine |

---

### 29025 - Feil deployment i strid med Navs retningslinjer

**Risiko:** Feil type deployment velges (f.eks. global deployment som overfører data til tredjeland). Det er mulig å velge riktig modell men feil deployment.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lage side på Loop med hva vi får lov til å lage (for utviklere) | ⚠️ N/A | Ekstern dokumentasjon |
| Risikovurdere nye modeller/versjoner før bruk | ✅ Implementert | Dokumentert i ki-moderering.md |
| Velge riktig deployment i prod i tillegg til dev | ✅ Implementert | |
| Kun språkmodeller med standard deployment i EU/EØS | ✅ Implementert | |

---

### 29023 - Modellversjon utgår

**Risiko:** En versjon av Azure OpenAI-modellen utgår og tilgjengelige versjoner oppfyller ikke Nav-kravene. F.eks. gpt-4o (standard deployment) utgår mars 2026, og ny versjon må vurderes før den tid.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lage side på Loop med hva vi får lov til å lage (for utviklere) | ⚠️ N/A | Ekstern dokumentasjon |
| Mulighet for å deaktivere AI hvis ikke trygg modell | ✅ Implementert | Toggle finnes |
| Sjekke jevnlig og merke utløpsdato for modellen | ⚠️ N/A | Manuell rutine |
| Teste før oppgradering av modell | ✅ Implementert | Dokumentert i ki-moderering.md |

---

### 28415 - KI-sjekken treffer ikke bra nok på testcases

**Risiko:** KI-sjekken treffer ikke korrekt på tilstrekkelig stor andel av testcases. Feil eller mangelfulle analyser kan bli godkjent i strid med retningslinjer.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lage prompten slik at den heller er for streng enn ikke | ✅ Implementert | |
| Gjennomføre tester med testcases prompten ikke er trent på | ✅ Implementert | |
| Sammenligne treffprosent mellom modeller | ✅ Implementert | |
| 90% målsetting, undersøke de som feiler | ✅ Implementert | |
| Lage benchmarks som viser treffprosent | ✅ Implementert | |

---

### 27979 - KI-sjekken gir falsk trygghet

**Risiko:** Bruker stoler for mye på KI-sjekken og oppretter treff til andre formål enn rekruttering fordi KI-sjekken ikke reagerer.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| KI-sjekken viser ikke grønn "ok" tekst | 🔄 Planlagt | Teknisk - UI-design |
| Gjennomføre tester fortløpende | ✅ Implementert | |
| Lage retningslinjer for bruk av KI-sjekken i fritekst | ⚠️ N/A | Manuell rutine |
| Legge inn tekst ved fritekstfelt som beskriver hva KI-sjekken gjør/ikke gjør | 🔄 Planlagt | Teknisk - UI-tekst |

---

### 27868 - Mangelfull evaluering av språkmodell

**Risiko:** Ved valg eller oppgradering av KI-modell gjøres dette uten tilstrekkelig analyse, dokumentasjon og kontroll. Konsekvenser: svekket ytelse, nye feil, uforutsette responsmønstre.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Evaluering ved valg/oppgradering av modell | 🔄 Delvis | Dokumentert i ki-moderering.md |

---

### 27867 - Mangelfull eller utilstrekkelig testing

**Risiko:** KI-modell eller systemprompt tas i bruk uten grundig testing av funksjonalitet, sikkerhet og etiske implikasjoner.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Logging for etterprøving i prod | ✅ Implementert | |
| Manuell testing | ✅ Implementert | |
| Ansvarliggjøre brukere med tydelig info i løsningen | 🔄 Planlagt | Teknisk - UI-tekst |
| Etablere automatiske tester basert på godkjente manuell-tester | ✅ Implementert | |

---

### 27854 - Hallusinering av fakta

**Risiko:** KI-sjekken genererer informasjon som ikke finnes i treningsdata eller ikke har grunnlag i virkeligheten. Brukere kan ta beslutninger basert på oppdiktet innhold.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Bruke risikovurdert modell som er vurdert trygg nok | ✅ Implementert | |
| Kartlagt hva som kan gå feil og tatt høyde for det | ✅ Implementert | Dokumentert i ROS |
| Jevnlige tester for å sjekke og forbedre | ✅ Implementert | |

---

### 27853 - Kompleksitet i systemprompt (overtilpasning)

**Risiko:** Systemprompten er for rigid eller detaljert, noe som gjør at modellen mister fleksibilitet og kan føre til diskriminerende tekster.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Jevnlige tester for å sjekke og forbedre | ✅ Implementert | |
| Klart og forståelig språk i prompten (klarspråk) | ✅ Implementert | |
| Laget veiledning med prinsipper for prompt | ✅ Implementert | |
| Dele opp prompten i temaer, deretter sette sammen | ✅ Implementert | |

---

### 27852 - Feil ved oppdatering av prompten

**Risiko:** Endringer i systemprompten er ikke tilstrekkelig testet, eller systemet blir for avhengig av én spesifikk prompt.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Loggføre og ha oversikt over versjon av systemprompt | ✅ Implementert | |
| Enkelt å reversere til tidligere systemprompt | ✅ Implementert | |
| Etablere automatiske tester | ✅ Implementert | |
| Lage og føre oversikt over manuell test | ✅ Implementert | |

---

### 27547 - KI identifiserer ikke diskriminerende tekst eller personopplysninger

**Risiko:** Bias i prompt eller datakilde fører til systematisk skjevhet. KI-sjekken har manglende kontekstforståelse, misforstår intensjon, bruker utdaterte kilder, eller fanger ikke opp grensetilfeller.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Utarbeide og implementere rutiner for brukere | ⚠️ N/A | Manuell rutine |
| Administrasjonskontroll for å registrere fornøydhet med ROB | ✅ Implementert | |
| Innhente feedback fra brukere, måle gevinst/effektivitet | ⚠️ N/A | Manuell rutine |
| Modellkontroll gjennom tester/stikkprøver | ✅ Implementert | |

---

### 27546 - KI-sjekken manipuleres

**Risiko:** Brukere, utviklere eller andre utnytter svakheter i treningsdata, logikk eller prompt til å få feilaktige vurderinger. Bruker kan bevisst forsøke å "lure" modellen.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Retningslinjer i løsningen om at ROB kun er et verktøy | 🔄 Planlagt | Teknisk - UI-tekst |
| Manipulasjonstesting/penetrasjonstesting av ROB | 🔄 Planlagt | Teknisk test |
| La bruker overstyre ROB (menneskelig kontroll) | ✅ Implementert | |
| Logging av svar for å avdekke manipulasjoner | ✅ Implementert | |

---

### 27545 - Arrangør gjør ikke selvstendig vurdering

**Risiko:** Arrangør kontrollerer ikke innholdet eller ser bort fra KI-vurderingen. Konsekvenser: brudd på personvern/diskriminering, tap av tillit, dårligere kvalitet.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Tydelig informasjonstekst om brukerens eget ansvar | 🔄 Planlagt | Teknisk - UI-tekst |
| Brukervennlig design/flyt som viser hvilke felt som analyseres | 🔄 Delvis | Teknisk - UI-design |
| Tydelige retningslinjer for ansvarlig bruk av KI-sjekken | ⚠️ N/A | Manuell rutine |

---

### 27544 - Mangelfull oppdatering av kunnskapsgrunnlag

**Risiko:** Endringer i personvernlovgivning eller likestillings-/diskrimineringsloven påvirker vurderingene KI-sjekken gir, men kunnskapsgrunnlaget er utdatert.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Sikre kunnskap om endringer i relevant lovverk | ⚠️ N/A | Manuell rutine |
| Vurdere behov for oppdatering ved ny versjon av modellen | ⚠️ N/A | Manuell rutine |
| Rutiner for stikkprøver for å teste at modellen er oppdatert | ⚠️ N/A | Manuell rutine |

---

### 27542 - Feil/dårlig veiledning pga manglende kontekstforståelse

**Risiko:** Språkmodellen misforstår prompten/teksten fordi den er tvetydig eller mangler kontekst. Bruker gir feilaktig, ufullstendig eller misvisende informasjon.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Testing av grensetilfeller | ✅ Implementert | I testsuiten |

---

### 27321 - Personopplysninger av særlig kategori i tittel/beskrivelse

**Risiko:** Input eller output inneholder personopplysninger av særlig kategori (GDPR art. 9) eller sensitiv informasjon om brukergrupper/innsatsgrupper. Konsekvenser: brudd på personvern, skam, diskriminering, sosial stigmatisering.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lenke til avvikshåndtering i løsningen | 🔄 Planlagt | Teknisk - UI |
| Funksjonalitet som tvinger bruker til å validere teksten | 🔄 Planlagt | Teknisk - UI-flyt |
| Teste systemet før implementering | ✅ Implementert | |
| Jevnlige tester for å sjekke og forbedre | ✅ Implementert | |
| Synlig tekstlig beskrivelse om at KI kan feile | 🔄 Planlagt | Teknisk - UI-tekst |

---

## Oppsummering

### ✅ Implementerte tiltak (teknisk)

| Kategori                | Beskrivelse                                                         |
| ----------------------- | ------------------------------------------------------------------- |
| Logging                 | Logger for etterprøving i produksjon, versjonslogg for systemprompt |
| Automatisk sletting     | Logger slettes automatisk etter definert tid                        |
| Deployment              | Kun standard deployment i EU/EØS                                    |
| Abuse monitoring        | Aktivert med sterkeste filter                                       |
| Testing                 | Automatiske tester, benchmarks, 90% målsetting, grensetilfeller     |
| Overstyre KI            | Bruker kan overstyre ROB-vurdering                                  |
| Systemprompt            | Versjonskontroll, reverserbar, tematisk oppdelt                     |
| Administrasjonskontroll | Registrere fornøydhet med ROB i produksjon                          |
| Risikovurdering         | Kartlagt feilscenarier, dokumentert i ROS                           |

### 🔄 Planlagte tiltak (teknisk)

| Kategori             | Beskrivelse                                                     |
| -------------------- | --------------------------------------------------------------- |
| UI-tekst             | Tekst om at KI kan feile, ansvar for innhold, ROB er et verktøy |
| UI-design            | Ingen grønn "ok", tydelig hvilke felt som analyseres            |
| UI-flyt              | Funksjonalitet som tvinger validering av tekst                  |
| Tilgangsstyring      | Egen admin-rolle, banner i prod                                 |
| Manipulasjonstesting | Penetrasjonstesting av ROB                                      |
| Avvikslenke          | Lenke til avvikshåndtering i løsningen                          |

### ⚠️ Manuelle rutiner (utenfor systemdok)

| Kategori        | Beskrivelse                                    |
| --------------- | ---------------------------------------------- |
| Opplæring       | Opplæring før utviklertilgang                  |
| Ekstern dok     | Loop-side med hva utviklere får lov til å lage |
| Tilgangsstyring | Fjerne tilgang ved behov, rutine for rollebruk |
| Overvåking      | Sjekke utløpsdato for modell, overvåke lovverk |
| Brukerrutiner   | Retningslinjer for bruk, feedback fra brukere  |

### ⚠️ Høy risiko som bør prioriteres

| ROS-ID | Risiko                      | Manglende tiltak                    |
| ------ | --------------------------- | ----------------------------------- |
| 27867  | Mangelfull testing          | Ansvarliggjøre brukere i løsningen  |
| 29025  | Feil deployment             | Loop-dokumentasjon                  |
| 27854  | Hallusinering               | Kartlegging av feilscenarier        |
| 28415  | Treffprosent                | - (hovedsakelig implementert)       |
| 27979  | Falsk trygghet              | Ingen grønn "ok", informasjonstekst |
| 27852  | Feil ved prompt-oppdatering | - (hovedsakelig implementert)       |

---

## Relaterte dokumenter

- [ROS-tiltak (generelt)](ros.md) - Generelle ROS-tiltak for Rekrutteringstreff
- [KI-moderering](../5-ki/ki-moderering.md) - Teknisk dokumentasjon for KI-validering
- [Akseptansetester](akseptansetester.md) - Fullstendige testscenarier
