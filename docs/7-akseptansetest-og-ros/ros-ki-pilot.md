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

| ROS-ID | Risiko                                                  | S   | K   | Tiltak | Manuell rutine        | Referanse                                |
| ------ | ------------------------------------------------------- | --- | --- | ------ | --------------------- | ---------------------------------------- |
| 29337  | Utviklertilgang til logger tildeles for bredt           | 1   | 4   | ✅     | Rutine dokumentert    | AT 15.33-15.35, 15.37-15.39              |
| 29330  | Logger lagres for lenge/for mye                         | 2   | 2   | ✅     | -                     | AT 15.39, 15.43, rutine: ki-rutiner      |
| 29263  | Abuse monitoring skrus av                               | 2   | 2   | ✅     | -                     | AT 15.38, 15.42, rutine: ki-rutiner      |
| 29262  | Ikke følger retningslinjer for Azure OpenAI             | -   | -   | ✅     | -                     | rutine: ki-rutiner                       |
| 29025  | Feil deployment av modell                               | 4   | 2   | ✅     | -                     | AT 15.37, 15.41, rutine: ki-rutiner      |
| 29023  | Modellversjon utgår (gpt-4o utgår mars 2026)            | 1   | 3   | ✅     | -                     | AT 15.44, rutine: ki-rutiner             |
| 28415  | KI-sjekken treffer ikke bra nok på testcases            | 3   | 3   | ✅     | -                     | AT 11.1-11.17                            |
| 27979  | KI-sjekken gir falsk trygghet                           | 3   | 3   | ✅     | Retningslinjer i loop | AT 11.24-11.28                           |
| 27868  | Mangelfull evaluering av språkmodell                    | 2   | 3   | ✅     | -                     | AT 15.37, 15.41, rutine: ki-rutiner      |
| 27867  | Mangelfull eller utilstrekkelig testing                 | 5   | 2   | ✅     | -                     | AT 11.1-11.28, 11.44-11.48               |
| 27854  | Hallusinering av fakta                                  | 4   | 2   | ✅     | -                     | rutine: ki-rutiner                       |
| 27853  | Kompleksitet i systemprompt (overtilpasning)            | 3   | 2   | ✅     | -                     | rutine: ki-rutiner                       |
| 27852  | Feil ved oppdatering av prompten                        | 3   | 3   | ✅     | -                     | rutine: ki-rutiner                       |
| 27547  | KI identifiserer ikke diskriminerende/personopplysning  | 2   | 4   | ✅     | Feedback via Skyra    | AT 11.12-11.17, 11.22-11.23, 11.44-11.48 |
| 27546  | KI-sjekken manipuleres                                  | 2   | 4   | ✅     | Logging for kontroll  | AT 11.31-11.35, 15.40                    |
| 27545  | Arrangør gjør ikke selvstendig vurdering                | 1   | 3   | ✅     | Retningslinjer i loop | AT 11.24-11.28                           |
| 27544  | Mangelfull oppdatering av kunnskapsgrunnlag             | 2   | 1   | ✅     | -                     | rutine: ki-rutiner                       |
| 27542  | Feil/dårlig veiledning pga manglende kontekstforståelse | 3   | 2   | ✅     | -                     | AT 11.31-11.35                           |
| 27321  | Personopplysninger av særlig kategori i tekst           | 2   | 4   | ✅     | -                     | AT 11.29-11.30, 11.36-11.43, 11.44-11.48 |

### Oppsummering manuelle rutiner

Følgende risikoer har manuelle rutiner eller dokumentasjon som ligger i Loop:

| ROS-ID | Hva er dokumentert                                      | Hvor              |
| ------ | ------------------------------------------------------- | ----------------- |
| 29337  | Tilgangsrutiner, opplæring, rollebeskrivelse            | Loop-dokument     |
| 27979  | Retningslinjer for bruk av KI-sjekken (for Nav-ansatte) | Loop-dokument     |
| 27547  | Brukerrutiner, feedback-innhenting fra brukere          | Skyra / Loop      |
| 27545  | Retningslinjer for ansvarlig bruk av KI-sjekken         | Informasjonspakke |

## Detaljert gjennomgang

### 29337 - Utviklertilgang til logger tildeles for bredt

**Risiko:** Utviklertilgang til logger på administrasjonssiden gis til flere utover de i teamet som har tjenestlig behov. Utviklertilgangen gir mer omfattende rettigheter enn kun innsyn i logger. Dersom denne tilgangen gis til andre i teamet, feks domenekspert som trenger tilgang til loggene, men ikke andre tilganger som ligger i utviklerrollen.

**Konsekvenser:**

- Uautorisert eller utilsiktet tilgang til funksjoner, data eller konfigurasjon som ikke er relevant for rollen
- Økt risiko for feilbruk, feilkonfigurasjon eller utilsiktede endringer i løsningen
- Redusert tillit

**Sannsynlighet:** 1, **Konsekvens:** 4

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Legge inn banner i løsning at man er i prod | ✅ Implementert | AT 15.37-15.39 |
| Opplæring i test før utviklertilgang tildeles | ✅ Rutine | Beskrevet i teamets rutiner |
| Lage rutine for bruk av rollene (hva er lov/ikke lov) | ✅ Rutine | Best practice (KI-rutine for utviklere) og rollebeskrivelse ligger i teamets loop-dokument |
| Lage egen administrasjonstilgang (Toi) som kun gir tilgang til løsningen | 🔄 Vurderes | Behovet må undersøkes nærmere |
| Fjerne utviklertilgang når den ikke er nødvendig lenger | ✅ Rutine | Tildeling av utviklertilgang i teamet beror på tillit |

---

### 29330 - Logger lagres for lenge eller i for stort omfang

**Risiko:** Det er en risiko for at interne logger lagres utover det som er nødvendig for formålet, enten ved at de lagres over lengre tid enn påkrevd eller ved at det samles inn og lagres flere opplysninger enn det som er nødvendig.

**Konsekvenser:**

- Økt eksponering av sensitive eller personopplysninger
- Høyere sannsynlighet for uautorisert innsyn/tilgang eller misbruk
- Brudd på gjeldende etterlevelseskrav i personvernlovgivningen

**Sannsynlighet:** 2, **Konsekvens:** 2

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lage rutiner for tilfeller der sletting kan feile (for utviklere) | ✅ Rutine | Rutine beskrives i KI-rutine for utviklere |
| Automatisk sletting - logger slettes etter 6 mnd | ✅ Implementert | Hardsletting av fritekstfelt, metadata kan lagres for statistikkformål |

---

### 29263 - Abuse monitoring skrus av

**Risiko:** Risiko for å skru av abuse monitoring som kan føre til at Nav blir ansvarliggjort for eventuelle misbruk av OpenAI. Hendelsen oppstår dersom abuse monitoring skrus av teamet. Det kan medføre at Nav ikke oppdager eller håndterer misbruk av OpenAI-tjenester.

**Referanser:**

- [Deaktivere gjennomgang av data](https://customervoice.microsoft.com/Pages/ResponsePage.aspx?id=v4j5cvGGr0GRqy180BHbR7en2Ais5pxKtso_Pz4b1_xUOE9MUTFMUlpBNk5IQlZWWkcyUEpWWEhGOCQlQCN0PWcu)
- [Abuse Monitoring](https://learn.microsoft.com/en-us/azure/ai-foundry/openai/concepts/abuse-monitoring#components-of-abuse-monitoring)

**Konsekvenser:**

- Rettslige konsekvenser (potensielt brudd på avtale)
- Omdømmetap
- Økonomisk tap for Nav

**Sannsynlighet:** 2, **Konsekvens:** 2

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lage en KI-rutine for utviklere som beskriver krav og best practice | ✅ Rutine | rutine: ki-rutiner. Skal gjennomgås med utviklere og ligge lett tilgjengelig |
| Velge sterkeste, moderne filter som er tilgjengelig | ✅ Implementert | Velges ved deployment av KI-modellen i Azure |

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

**Risiko:** Risiko for at gpt-modellen bruker deployment i strid med Navs retningslinjer. Hendelsen oppstår hvis feil type deployment (utrulling) velges i strid med retningslinjer i Nav (se egen ROS ID1637). Eks: Hvis global deployment blir valgt kan det innebære at persondata deles, tilgjengeliggjøres, eller overføres til tredjeland. Det går an å velge riktig modell, men feil deployment.

**Konsekvenser:**

- Personopplysninger kan bli overført til eller gjort tilgjengelig fra tredjeland
- Brudd på krav til datalagring og geografisk behandling av persondata
- Manglende etterlevelse av personvernregelverk og interne retningslinjer
- Omdømmetap
- Redusert kontroll over hvor og hvordan data behandles

**Sannsynlighet:** 4, **Konsekvens:** 2

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lage en KI-rutine for utviklere som beskriver krav og best practice | ✅ Rutine | rutine: ki-rutiner |
| Dokumentere i ROS når vi oppgraderer modell/versjon | ✅ Rutine | Ved oppgradering dokumenteres hva det oppgraderes til og hvorfor |
| Velge riktig deployment både i testmiljø og produksjonsmiljø | ✅ Implementert | Beskrevet i KI-rutine for utviklere |
| Kun språkmodeller med standard deployment lokalisert i EU/EØS | ✅ Implementert | Standard deployment innebærer at modellen kjøres i spesifikk Azure-region |

---

### 29023 - Modellversjon utgår

**Risiko:** Risiko for at en versjon av språkmodellen utgår (Azure OpenAI-modell), og at tilgjengelige versjoner av Azure OpenAI ikke oppfyller kravene til Nav-interne retningslinjer. F.eks det er krav om å bruke gpt-4o fordi den har standard deployment (innenfor EU), mens oppdaterte modeller kun har global deployment. Gpt-4o (standard deployment) utgår mars 2026. Ny versjon må være på plass før den tid.

Vi har ingen roadmap for at det kommer en ny modell ihht. retningslinjer. Vi har ingen garanti for at modeller som utgår blir erstattet med like trygge modeller/godkjente deployments.

**Konsekvenser:**

- Manglende etterlevelse av krav
- Stans i teknisk drift
- Redusert tillit til løsningen

**Sannsynlighet:** 1, **Konsekvens:** 3

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lage en KI-rutine for utviklere som beskriver krav og best practice | ✅ Rutine | rutine: ki-rutiner |
| Mulighet for å stoppe redigering/deaktivere AI ved manglende trygg modell | ✅ Implementert | Ikke ønsket tiltak, men mulig dersom nødvendig |
| Sjekke jevnlig og merke utløpsdato for språkmodellen (versjon) | ✅ Rutine | Hvordan og hvor ofte fremgår av KI-rutine for utviklere |
| Teste før oppgradering av modell | ✅ Implementert | Følger etablert praksis, beskrevet i KI-rutine for utviklere |

---

### 28415 - KI-sjekken treffer ikke bra nok på testcases

**Risiko:** Risiko for at KI-sjekken har for lav treffsikkerhet målt opp i mot definerte testcases. Hendelsen kan oppstå hvis KI-sjekken ikke treffer riktig på nok av de definerte testcasene, enten på grunn av modellens begrensninger eller for lite testing. Feil eller mangelfulle analyser av input kan bli godkjent i strid med retningslinjer.

**Konsekvenser:**

- Brudd på retningslinjer
- Brudd på rettslige forpliktelser (personvern og likestilling)
- Redusert tillit til KI-sjekken
- Omdømmetap

**Sannsynlighet:** 3, **Konsekvens:** 3

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lage prompten slik at den heller er for streng enn ikke | ✅ Implementert | Tester viser at systemprompten vurderer noe strengere |
| Gjennomføre tester med testcases som systemprompten ikke er trent på | ✅ Implementert | Testsuite fra domeneekspert |
| Sammenligne treffprosent mellom modeller og vurdere resultatene | ✅ Rutine | Etablert rutine, beskrevet i KI-rutine for utviklere |
| 90 prosent er benchmark, undersøke tilfellene som feiler | ✅ Implementert | Vi undersøker spesielt tilfeller som feilet, og hvorfor |
| Bruke offisielle benchmarks for å finne beste modell for formålet | ✅ Implementert | Benchmarker mot utvalg av tilpassede tekster |
| Teste på ulike systemprompt og språkmodeller for best treffprosent | ✅ Implementert | |

---

### 27979 - KI-sjekken gir falsk trygghet

**Risiko:** Risiko for at KI-sjekken oppfattes som mer pålitelig enn den er, slik at den gir en falsk trygghet, og derfor blir treff opprettet til andre formål. Hendelsen kan oppstå hvis bruker lager et treff som ikke er et rekrutteringstreff fordi de stoler for mye på at KI-sjekken treffer riktig. Eks: ROB reagerer ikke på "arbeidstrening" derfor opprettes treff for arbeidstrening.

**Konsekvenser:**

- Brudd på personvernslovgivning og/eller regler i diskriminerings- og likestillingslovgivningen
- Tap av tillit til Nav
- Omdømmetap
- Skjev eller urettferdig vurderinger

**Sannsynlighet:** 3, **Konsekvens:** 3

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| KI-sjekken viser ikke grønn "ok" tekst når den godtar resultatet | ✅ Implementert | Reduserer sjansen for falsk trygghet. AT 11.26 |
| Gjennomføre tester fortløpende | ✅ Implementert | Tester ved endringer, manuelle stikkprøver, Marked involveres |
| Lage retningslinjer for bruk av KI-sjekken i fritekst | ✅ Dokumentert | Beskrevet i eget loop-dokument |
| Legge inn tekst ved fritekstfelt som beskriver hva KI-sjekken gjør/ikke gjør | ✅ Implementert | Tydelig beskrivelse med dropdown-funksjon. AT 11.24-11.25 |

---

### 27868 - Mangelfull evaluering av språkmodell

**Risiko:** Risiko for mangelfull evaluering av språkmodeller (Azure OpenAI). Hendelsen kan oppstå ved valg eller oppgradering av KI-modell, dersom dette gjøres uten tilstrekkelig analyse, dokumentasjon og kontroll. Risikoen er særlig relevant ved fremtidige oppdateringer eller justeringer av modellen.

**Konsekvenser:**

- Svekket ytelse
- Nye feil
- Uforutsette responsmønstre
- Tap av kompatibilitet med eksisterende systemer
- Økt risiko for misbruk

**Sannsynlighet:** 2, **Konsekvens:** 3

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Sikre at behandling av data er i henhold til databehandleravtale med Azure | ✅ Implementert | Etterlevelseskrav K190.2 - DBA og behandling i henhold til krav om datasenter |
| Følge veileder for generativ kunstig intelligens fra Nav | ✅ Rutine | [Nav GKI-veileder](https://data.nav.no/fortelling/ki/index.html) |
| Manuell testing | ✅ Implementert | Teste systemprompts, gpt-modeller og testcases i Azure Playground |
| Evaluering av Azure OpenAI til Rekrutteringstreff | ✅ Dokumentert | Loop-dokument |
| Gjøre undersøkelser for å sjekke om bytte/oppgradering gir bedre resultater | ✅ Rutine | Automatiske tester, manuelle tester, vilkår i OpenAI sine retningslinjer |
| Kjøre automatiske tester før bytte | ✅ Implementert | KiTekstvalideringParameterisertTest.kt - måler ROBs nøyaktighet |

---

### 27867 - Mangelfull eller utilstrekkelig testing

**Risiko:** Risiko for mangelfull eller utilstrekkelig testing. Hendelsen kan oppstå når en KI-modell eller systemprompt tas i bruk uten grundig testing av funksjonalitet, sikkerhet og etiske implikasjoner.

**Konsekvenser:**

- Feilaktige svar
- Sikkerhetsbrudd
- Skjulte bias eller uforutsette problemer som først avdekkes i produksjon
- Omdømmeskade

**Sannsynlighet:** 5, **Konsekvens:** 2

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Logging for etterprøving i prod | ✅ Implementert | AT 11.18-11.23 |
| Manuell testing | ✅ Implementert | AT 11.1-11.17 |
| Ansvarliggjøre brukere med tydelig info i løsningen | ✅ Implementert | Står tydelig ved fritekstfeltet og i egen informasjonspakke. AT 11.24-11.28 |
| Etablere automatiske tester basert på godkjente manuell-tester | ✅ Implementert | KiTekstvalideringParameterisertTest.kt - ROBs nøyaktighet = (antall test-prompts - antall avvik) / antall test-prompts \* 100 |

---

### 27854 - Hallusinering av fakta

**Risiko:** Risiko for hallusinering av fakta. Hendelsen kan oppstå når KI-sjekken genererer informasjon som ikke finnes i treningsdata eller som ikke har grunnlag i virkeligheten. KI-sjekken er trent på data fra internett.

**Konsekvenser:**

- Brukere tar beslutninger basert på feilaktig eller oppdiktet innhold
- Misinformasjon
- Tap av tillit
- Uønskede handlinger

**Sannsynlighet:** 4, **Konsekvens:** 2

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Bruke risikovurdert modell som er vurdert trygg nok for formålet | ✅ Implementert | Beskrives i KI-rutine for utviklere |
| Kartlagt hva som kan feile og tatt høyde for det | ✅ Implementert | Kvalitetssikres gjennom kontinuerlig testing. ROBs spesifikke oppgave gjør det enkelt å oppdage hallusinering |
| Jevnlige tester for å sjekke og forbedre | ✅ Implementert | Manuelle tester i Azure Chat Playground |

---

### 27853 - Kompleksitet i systemprompt (overtilpasning)

**Risiko:** Risiko for kompleksitet i systemprompt (overtilpasning av prompten). Hendelsen kan oppstå når systemprompten er for rigid eller detaljert.

**Eksempler:**

- Tilpasset testcases
- Mange regler

**Konsekvenser:**

- Modellen mister fleksibilitet, dynamikk og kreativitet
- Begrenser effektiviteten
- Kan føre til diskriminerende tekster

**Sannsynlighet:** 3, **Konsekvens:** 2

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Jevnlige tester for å sjekke og forbedre | ✅ Implementert | Manuelle tester i Azure Chat Playground |
| Klart og forståelig språk i prompten (klarspråk) | ✅ Implementert | Testet ulike formuleringer, brukt KI til å vaske språk for uklarheter |
| Laget veiledning med prinsipper for prompt | ✅ Implementert | "Veiledning for ansvarlig bruk språkmodeller for tilpassede KI-assistenter" basert på regjeringens guide |
| Dele opp prompten i temaer, deretter sette sammen | ✅ Implementert | Tester konkrete deler av prompten |

---

### 27852 - Feil ved oppdatering av prompten

**Risiko:** Risiko for feil ved oppdatering av prompten. Hendelsen kan oppstå når endringer eller oppdateringer i systemprompten ikke er tilstrekkelig testet. Hendelsen kan også oppstå når systemet blir for avhengig av én spesifikk systemprompt.

**Konsekvenser:**

- Redusert robusthet mot oppdateringer, endringer eller nye brukstilfeller
- Nye feil
- Uforutsette konsekvenser
- Svekket modell-ytelse

**Sannsynlighet:** 3, **Konsekvens:** 3

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Loggføre og ha oversikt over versjon av systemprompt | ✅ Implementert | Mulig å holde oversikt over endringer, sikre sporbarhet og dokumentere konfigurasjon |
| Enkelt å reversere til tidligere systemprompt | ✅ Implementert | |
| Etablere automatiske tester | ✅ Implementert | KiTekstvalideringParameterisertTest.kt - gjør det enkelt å sammenligne gamle og nye tester |
| Lage og føre oversikt over manuell test | ✅ Implementert | Testscript i Loop med oversikt over gjennomførte tester |

---

### 27547 - KI identifiserer ikke diskriminerende tekst eller personopplysninger

**Risiko:** Risiko for at KI-sjekken ikke klarer å identifisere diskriminerende tekst eller personopplysninger i tittel/beskrivelse til treffet (input). Kan oppstå hvis bias i prompten eller i datakilden fører til systematisk skjevhet, og ikke fanger opp at innholdet i teksten er diskriminerende. Hendelsen kan også inntreffe dersom en ansatt har skrevet inn personopplysninger i fritekstfeltet som KI-sjekken ikke identifiserer.

**Tekniske sårbarheter:**

- Manglende kontekstforståelse
- Misforstår intensjonen bak en tekst dersom den er tvetydig eller mangler kontekst
- Bruker utdaterte stillingsannonser som kilde
- Bruker/ansatte gir feilaktig/ufullstendig/misvisende informasjon i grensetilfeller

**Sannsynlighet:** 2, **Konsekvens:** 4

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Utarbeide og implementere rutiner rettet mot brukere | ✅ Dokumentert | Retningslinjer i egen informasjonspakke (loop) |
| Administrasjonskontroll for å registrere fornøydhet med ROB | ✅ Implementert | Forenkler kontroll på vurderinger og sikrer sporbarhet |
| Innhente feedback fra brukere, måle gevinst/effektivitet | ✅ Rutine | Manuelt eller gjennom Skyra |
| Modellkontroll gjennom tester/stikkprøver | ✅ Implementert | Manuelt og automatisk. Vurderer etterlevelse av retningslinjer før endring/oppgradering |

---

### 27546 - KI-sjekken manipuleres

**Risiko:** Risiko for at KI-sjekken manipuleres til å gi feilaktige eller utilsiktede vurderinger ved at bruker, utviklere eller andre med tilgang, utnytter svakheter i systemets treningsdata, logikk eller prompt. Hendelsen oppstår som følge av tilsiktet handling ved at brukeren bevisst forsøker å "lure" språkmodellen.

KI-sjekken kan være sårbar for ulike former for manipulasjon gjennom input, endringer av prompt eller ved å fremprovosere svakheter i kontekstforståelse.

**Konsekvenser:**

- Brudd på personvernslovgivning og/eller regler i diskriminerings- og likestillingslovgivningen
- Tap av tillit til Nav
- Omdømmetap
- Skjev eller urettferdig vurderinger

**Sannsynlighet:** 2, **Konsekvens:** 4

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Formidle gjennom retningslinjer at ROB kun er et verktøy som bruker må kontrollere | ✅ Implementert | Sikrer ansvarliggjøring av bruker. AT 11.24-11.25 |
| Manipulasjonstesting av ROB i Rekrutteringstreff | ✅ Implementert | Målet er å finne svakheter som kan utnyttes. Gjennomføres manuelt. AT 11.31-11.35 |
| La bruker overstyre ROB (menneskelig kontroll) | ✅ Implementert | Kan overvåke når folk gjør feil gjennom logging. AT 11.9-11.11 |
| Logging av svar for å avdekke forsøk på manipulasjon | ✅ Implementert | Administrasjonskontrollen i løsningen. Kun tilgjengelig for adminbrukere. AT 15.36 |

---

### 27545 - Arrangør gjør ikke selvstendig vurdering

**Risiko:** Risiko for at arrangør av ett treff ikke gjør en selvstendig vurdering av rettferdighet, kvalitet eller feil i innholdet til treffet. Hendelsen kan oppstå som følge av at vedkommende ikke kontrollerer innholdet i treffet, eller velger å se bort fra vurderingen gjort av KI-sjekken, og benytter innholdet uten en selvstendig vurdering av relevans, kvalitet eller eventuelle feil.

**Konsekvenser:**

- Brudd på personvernslovgivning og/eller regler i diskriminerings- og likestillingslovgivningen
- Tap av tillit til Nav og omdømmetap
- Ansattes tap av tillit til bruk av KI/løsningen
- Kvaliteten i treffet blir dårligere eller kan i verste fall oppleves som diskriminerende

**Sannsynlighet:** 1, **Konsekvens:** 3

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Tydelig informasjonstekst om brukerens eget ansvar | ✅ Implementert | Klargjør ansvarsforhold, forebygger misforståelser. AT 11.24-11.25 |
| Brukervennlig design/flyt som viser hvilke felt som analyseres | ✅ Implementert | Reduserer risiko for feilregistrering og misforståelser. AT 11.27-11.28 |
| Tydelige retningslinjer for ansvarlig bruk av KI-sjekken | ✅ Dokumentert | Egen informasjonspakke i loop som beskriver retningslinjene |

---

### 27544 - Mangelfull oppdatering av kunnskapsgrunnlag

**Risiko:** Risiko for mangelfull oppdatering av kunnskapsgrunnlag i språkmodellen fordi kunnskapsgrunnlaget er utdatert. Hendelsen kan oppstå hvis det skjer endringer i personvernslovgivningen, eller likestillings- og diskrimineringsloven som får konsekvenser for vurderingene KI-sjekken gir.

**Sannsynlighet:** 2, **Konsekvens:** 1

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Sikre kunnskap om endringer i relevant lovverk | ✅ Rutine | Juridisk kompetanse i teamet holder teamet oppdatert. Sjekker kunnskapsgrunnlaget ved ny versjon |
| Vurdere behov for oppdatering ved ny versjon av modellen | ✅ Rutine | Minst en gang i året, eller når ny modell blir tilgjengelig |
| Rutiner for stikkprøver for å teste at modellen er oppdatert | ✅ Implementert | Gjennom automatiske tester ved behov, samt manuelle tester ved endringer |

---

### 27542 - Feil/dårlig veiledning pga manglende kontekstforståelse

**Risiko:** Risiko for at ROB gir feil eller dårlig veiledning som følge av manglende kontekstforståelse. Hendelsen kan oppstå hvis språkmodellen "misforstår" prompten/teksten som bruker legger inn fordi det er tvetydig eller mangler tilstrekkelig kontekst. Hendelsen kan også oppstå hvis brukeren gir feilaktig, ufullstendig, misvisende informasjon eller informasjon som tilhørerer grensetilfellene.

**Eksempel:** "Vi ser etter unge, energiske menn som passer godt inn i vårt dynamiske team!" - Teksten er positiv og oppløftende, men ROB bør vurdere teksten som diskriminerende fordi den oppfordrer "unge menn" til å delta.

**Konsekvenser:**

- Feilinformasjon
- Svekket datakvalitet
- Svekket tillit til systemet
- Kvaliteten i treffet blir dårligere eller kan oppleves som diskriminerende
- Skjeve eller urettferdige treff
- Brudd på personvernslovgivningen og likestillings- og diskrimineringsloven

**Sannsynlighet:** 3, **Konsekvens:** 2

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Teste ROB opp mot retningslinjene | ✅ Implementert | I testsuiten |
| Retningslinjer for bruk av ROB tilgjengelig i applikasjonen og på Navet | ✅ Delvis | Informasjon i løsningen og informasjonspakke i loop. Tilgjengeliggjøres på Navet ved lansering |
| Retningslinjer for å hindre diskriminering gjenspeiles i systemprompten | ✅ Implementert | Sikrer transparens, sporbarhet og etterprøvbarhet |

---

### 27321 - Personopplysninger av særlig kategori i tittel/beskrivelse

**Risiko:** Risiko for at tittel/beskrivelsen i treffet inneholder personopplysninger av særlig kategori. Hendelsen kan oppstå på tilsvarende måte som beskrevet i ID27547, men denne risikohendelsen krever en separat vurdering av konsekvenser, ettersom både input (i tittel eller beskrivelse) og output kan inneholde personopplysninger av særlig kategori.

Med særlig kategori menes personopplysninger som fremgår av art. 9 i GDPR, men kan også inkludere informasjon som oppleves som sensitiv for personbruker. Feks informasjon om ytelser fra Nav.

**Konsekvenser:**

- Brudd på personvernslovgivning og/eller regler i diskriminerings- og likestillingslovgivningen
- Tap av tillit til Nav og omdømmetap
- Ansattes tap av tillit til bruk av KI/løsningen
- Serviceklager fra ansatte
- Personbruker føler skam, diskriminering eller sosial stigmatisering

**Sannsynlighet:** 2, **Konsekvens:** 4

**Tiltak:**
| Tiltak | Status | Kommentar |
|--------|--------|-----------|
| Lenke til avvikshåndtering i løsningen | ✅ Implementert | Linker til Nav sitt avvikssystem (ASYS). AT 11.29-11.30 |
| Funksjonalitet som tvinger bruker til å validere teksten | ✅ Implementert | Må avklare med design om hensiktsmessig tiltak. AT 11.9-11.11 |
| Teste systemet før implementering | ✅ Implementert | Plan for testing gjennomføres før KI-sjekken er i prod |
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
