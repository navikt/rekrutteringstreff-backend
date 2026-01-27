# ROS-tiltak for KI-sjekken (ROB)

Dette dokumentet gir oversikt over risikoer fra ROS-analysen som er spesifikke for KI-sjekken (ROB), og status på tiltak.

**Statusforklaring (Tiltak):**

- ✅ = Tiltak definert (akseptansetester, systemdok, eller utviklerrutiner)
- 🔄 = Delvis definert (noen tiltak gjenstår)
- ⚠️ = Kun manuell rutine (ingen teknisk test)
- ➖ = Ikke relevant for pilot

**Referanseforklaring:**

- AT = Akseptansetest (må kjøres for å verifisere)
- sysdok: = Systemdokumentasjon
- rutine: = Utviklerrutine

## Oversikt over risikoer

| ROS-ID | Risiko                                                  | Tiltak | Manuell rutine           | Referanse                           |
| ------ | ------------------------------------------------------- | ------ | ------------------------ | ----------------------------------- |
| 29337  | Utviklertilgang til logger tildeles for bredt           | 🔄     | Manuell rutine           | AT 15.33-15.35                      |
| 29330  | Logger lagres for lenge/for mye                         | ✅     | -                        | AT 15.39, rutine: ki-rutiner        |
| 29263  | Abuse monitoring skrus av                               | ✅     | -                        | AT 15.38, rutine: ki-rutiner        |
| 29262  | Ikke følger retningslinjer for Azure OpenAI             | ✅     | -                        | rutine: ki-rutiner                  |
| 29025  | Feil deployment av modell                               | ✅     | -                        | AT 15.37, 15.40, rutine: ki-rutiner |
| 29023  | Modellversjon utgår                                     | ✅     | -                        | AT 15.40, rutine: ki-rutiner        |
| 28415  | KI-sjekken treffer ikke bra nok på testcases            | ✅     | -                        | AT 11.1-11.17                       |
| 27979  | KI-sjekken gir falsk trygghet                           | 🔄     | Retningslinjer for bruk  | AT 11.24-11.28                      |
| 27868  | Mangelfull evaluering av språkmodell                    | ✅     | -                        | AT 15.37, 15.40, rutine: ki-rutiner |
| 27867  | Mangelfull eller utilstrekkelig testing                 | 🔄     | -                        | AT 11.1-11.28                       |
| 27854  | Hallusinering av fakta                                  | ✅     | -                        | rutine: ki-rutiner                  |
| 27853  | Kompleksitet i systemprompt (overtilpasning)            | ✅     | -                        | rutine: ki-rutiner                  |
| 27852  | Feil ved oppdatering av prompten                        | ✅     | -                        | rutine: ki-rutiner                  |
| 27547  | KI identifiserer ikke diskriminerende/personopplysning  | 🔄     | Feedback fra brukere     | AT 11.12-11.17, 11.22-11.23         |
| 27546  | KI-sjekken manipuleres                                  | ✅     | Logging for kontroll     | AT 11.31-11.35, 15.36               |
| 27545  | Arrangør gjør ikke selvstendig vurdering                | 🔄     | Opplæring ansvarlig bruk | AT 11.24-11.28                      |
| 27544  | Mangelfull oppdatering av kunnskapsgrunnlag             | ✅     | -                        | rutine: ki-rutiner                  |
| 27542  | Feil/dårlig veiledning pga manglende kontekstforståelse | ✅     | -                        | AT 11.31-11.35                      |
| 27321  | Personopplysninger av særlig kategori i tekst           | ✅     | -                        | AT 11.29-11.30, 11.36-11.43         |

### Oppsummering manuelle rutiner

Følgende risikoer krever manuelle rutiner eller dokumentasjon som ikke er i systemdokumentasjonen:

| ROS-ID | Hva må dokumenteres                                     |
| ------ | ------------------------------------------------------- |
| 29337  | Tilgangsrutiner (opplæring, bruk av roller, fjerning)   |
| 27979  | Retningslinjer for bruk av KI-sjekken (for Nav-ansatte) |
| 27547  | Brukerrutiner, feedback-innhenting fra brukere          |
| 27545  | Retningslinjer for ansvarlig bruk av KI-sjekken         |

## Detaljert gjennomgang

### 29337 - Utviklertilgang til logger tildeles for bredt

**Risiko:** Utviklertilgang gir større tilgang enn kun til loggene. Hvis denne tilgangen gis til andre enn teamet (f.eks. domeneekspert som trenger tilgang til loggene, men ikke andre tilganger som ligger i utviklerrollen).

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Legge inn banner i løsning at man er i prod | ✅ Implementert | AT 15.37-15.39 |
| Opplæring i test før utviklertilgang tildeles | ⚠️ Mangler | Må dokumenteres |
| Lage rutine for bruk av rollene (hva er lov/ikke lov) | ⚠️ Mangler | Må dokumenteres |
| Lage egen administrasjonstilgang (Toi) som kun gir tilgang til løsningen | ✅ Implementert | Egen admin-rolle |
| Fjerne tilgang når den ikke er nødvendig lenger | ⚠️ Mangler | Må dokumenteres |

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
| Dokumentere hva vi får lov til å lage (for utviklere) | ✅ Rutine | rutine: ki-rutiner |
| Velge sterkeste, moderne filter i henhold til krav | ✅ Implementert | |

---

### 29262 - Ikke følger retningslinjer for Azure OpenAI/KI-forordningen

**Risiko:** Teamet følger ikke retningslinjer for bruk av Azure OpenAI utformet av Nav eller juridiske krav (KI-forordningen).

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Følge etablerte retningslinjer for ansvarlig KI i Nav | ✅ Rutine | rutine: ki-rutiner |
| Etablere retningslinjer i seksjonen/teamet for ny instans av Azure OpenAI | ✅ Rutine | rutine: ki-rutiner |

---

### 29025 - Feil deployment i strid med Navs retningslinjer

**Risiko:** Feil type deployment velges (f.eks. global deployment som overfører data til tredjeland). Det er mulig å velge riktig modell men feil deployment.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Dokumentere godkjente deployment-typer (for utviklere) | ✅ Rutine | rutine: ki-rutiner |
| Risikovurdere nye modeller/versjoner før bruk | ✅ Implementert | sysdok: ki-tekstvalideringstjeneste |
| Velge riktig deployment i prod i tillegg til dev | ✅ Implementert | |
| Kun språkmodeller med standard deployment i EU/EØS | ✅ Implementert | |

---

### 29023 - Modellversjon utgår

**Risiko:** En versjon av Azure OpenAI-modellen utgår og tilgjengelige versjoner oppfyller ikke Nav-kravene. F.eks. GPT-4.1 (standard deployment), og ny versjon må vurderes før den utgår.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Dokumentere godkjente deployment-typer (for utviklere) | ✅ Rutine | rutine: ki-rutiner |
| Mulighet for å deaktivere AI hvis ikke trygg modell | ✅ Implementert | Toggle finnes |
| Sjekke jevnlig og merke utløpsdato for modellen | ✅ Rutine | rutine: ki-rutiner |
| Teste før oppgradering av modell | ✅ Implementert | sysdok: ki-tekstvalideringstjeneste |

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
| KI-sjekken viser ikke grønn "ok" tekst | ✅ Implementert | AT 11.26 |
| Gjennomføre tester fortløpende | ✅ Implementert | |
| Lage retningslinjer for bruk av KI-sjekken i fritekst | ⚠️ N/A | Manuell rutine |
| Legge inn tekst ved fritekstfelt som beskriver hva KI-sjekken gjør/ikke gjør | ✅ Implementert | AT 11.24-11.25 |

---

### 27868 - Mangelfull evaluering av språkmodell

**Risiko:** Ved valg eller oppgradering av KI-modell gjøres dette uten tilstrekkelig analyse, dokumentasjon og kontroll. Risikoen er særlig relevant ved fremtidige oppdateringer eller justeringer av modellen. Konsekvenser: svekket ytelse, nye feil, uforutsette responsmønstre, tap av kompatibilitet eller økt risiko for misbruk.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Sikre at behandling av data er i henhold til databehandleravtale med Azure | ✅ Implementert | |
| Følge veileder for generativ kunstig intelligens fra Nav | ✅ Rutine | rutine: ki-rutiner |
| Manuell testing før bytte | ✅ Implementert | rutine: ki-rutiner |
| Evaluering av Azure OpenAI til Rekrutteringstreff | ✅ Dokumentert | sysdok: ki-tekstvalideringstjeneste |
| Gjøre undersøkelser for å sjekke om bytte/oppgradering gir bedre resultater | ✅ Rutine | rutine: ki-rutiner |
| Kjøre automatiske tester før bytte | ✅ Implementert | rutine: ki-rutiner |

---

### 27867 - Mangelfull eller utilstrekkelig testing

**Risiko:** KI-modell eller systemprompt tas i bruk uten grundig testing av funksjonalitet, sikkerhet og etiske implikasjoner.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Logging for etterprøving i prod | ✅ Implementert | AT 11.18-11.23 |
| Manuell testing | ✅ Implementert | AT 11.1-11.17 |
| Ansvarliggjøre brukere med tydelig info i løsningen | 🔄 Planlagt | AT 11.24-11.28 |
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
| Utarbeide og implementere rutiner for brukere | ⚠️ Brukerrettet | Opplæringsmateriell |
| Administrasjonskontroll for å registrere fornøydhet med ROB | ✅ Implementert | |
| Innhente feedback fra brukere, måle gevinst/effektivitet | ⚠️ Brukerrettet | Prosess for Nav-ansatte |
| Modellkontroll gjennom tester/stikkprøver | ✅ Implementert | |

---

### 27546 - KI-sjekken manipuleres

**Risiko:** Brukere, utviklere eller andre utnytter svakheter i treningsdata, logikk eller prompt til å få feilaktige vurderinger. Bruker kan bevisst forsøke å "lure" modellen.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Retningslinjer i løsningen om at ROB kun er et verktøy | ✅ Implementert | AT 11.24-11.25 |
| Robusthetstesting av KI-sjekken | ✅ Implementert | AT 11.31-11.35 |
| La bruker overstyre ROB (menneskelig kontroll) | ✅ Implementert | AT 11.9-11.11 |
| Logging av svar for å avdekke forsøk på manipulasjon | ✅ Implementert | AT 15.36 |

---

### 27545 - Arrangør gjør ikke selvstendig vurdering

**Risiko:** Arrangør kontrollerer ikke innholdet eller ser bort fra KI-vurderingen. Konsekvenser: brudd på personvern/diskriminering, tap av tillit, dårligere kvalitet.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Tydelig informasjonstekst om brukerens eget ansvar | ✅ Implementert | AT 11.24-11.25 |
| Brukervennlig design/flyt som viser hvilke felt som analyseres | ✅ Implementert | AT 11.27-11.28 |
| Tydelige retningslinjer for ansvarlig bruk av KI-sjekken | ⚠️ Brukerrettet | Opplæringsmateriell |

---

### 27544 - Mangelfull oppdatering av kunnskapsgrunnlag

**Risiko:** Endringer i personvernlovgivning eller likestillings-/diskrimineringsloven påvirker vurderingene KI-sjekken gir, men kunnskapsgrunnlaget er utdatert.

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Sikre kunnskap om endringer i relevant lovverk | ✅ Rutine | rutine: ki-rutiner |
| Vurdere behov for oppdatering ved ny versjon av modellen | ✅ Rutine | rutine: ki-rutiner |
| Rutiner for stikkprøver for å teste at modellen er oppdatert | ✅ Rutine | rutine: ki-rutiner |

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
| Lenke til avvikshåndtering i løsningen | ✅ Implementert | AT 11.29-11.30 |
| Funksjonalitet som tvinger bruker til å validere teksten | ✅ Implementert | AT 11.9-11.11 |
| Teste systemet før implementering | ✅ Implementert | |
| Jevnlige tester for å sjekke og forbedre | ✅ Implementert | |
| Synlig tekstlig beskrivelse om at KI kan feile | ✅ Implementert | AT 11.24-11.25 |

---

## Oppsummering

### ✅ Testede tiltak

| Kategori                | Beskrivelse                                                         | Akseptansetest |
| ----------------------- | ------------------------------------------------------------------- | -------------- |
| Logging                 | Logger for etterprøving i produksjon, versjonslogg for systemprompt | AT 11.12-11.17 |
| Automatisk sletting     | Logger slettes automatisk etter definert tid                        | -              |
| Deployment              | Kun standard deployment i EU/EØS                                    | -              |
| Abuse monitoring        | Aktivert med sterkeste filter                                       | -              |
| Testing                 | Automatiske tester, benchmarks, 90% målsetting, grensetilfeller     | AT 11.1-11.17  |
| Overstyre KI            | Bruker kan overstyre ROB-vurdering                                  | AT 11.9-11.11  |
| Systemprompt            | Versjonskontroll, reverserbar, tematisk oppdelt                     | -              |
| Administrasjonskontroll | Registrere fornøydhet med ROB i produksjon                          | AT 11.16-11.17 |
| Risikovurdering         | Kartlagt feilscenarier, dokumentert i ROS                           | -              |
| UI-tekst                | Tekst om at KI kan feile, ansvar for innhold, ROB er et verktøy     | AT 11.24-11.28 |
| UI-design               | Ingen grønn "ok", tydelig hvilke felt som analyseres                | AT 11.26-11.28 |
| UI-flyt                 | Funksjonalitet som tvinger validering av tekst                      | AT 11.9-11.11  |
| Tilgangsstyring         | Egen admin-rolle, banner i prod                                     | AT 15.33-15.35 |
| Robusthetstesting       | Testing av KI-sjekken med uvanlige tekster                          | AT 11.31-11.35 |
| Avvikslenke             | Lenke til avvikshåndtering i løsningen                              | AT 11.29-11.30 |

### ⚠️ Brukerrettet dokumentasjon (ikke i systemdok)

| Kategori      | Beskrivelse                                          |
| ------------- | ---------------------------------------------------- |
| Opplæring     | Retningslinjer for bruk av KI-sjekken (Nav-ansatte)  |
| Brukerrutiner | Prosessbeskrivelser, feedback-innhenting fra brukere |

---

## Relaterte dokumenter

- [ROS-tiltak (generelt)](ros-pilot.md) - Generelle ROS-tiltak for Rekrutteringstreff
- [KI-tekstvalideringstjenesten](../5-ki/ki-tekstvalideringstjeneste.md) - Teknisk dokumentasjon for KI-validering
- [Akseptansetester](akseptansetester.md) - Fullstendige testscenarier
