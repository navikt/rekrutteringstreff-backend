# ROS-tiltak og testdekning

Dette dokumentet viser hvilke risikoer fra ROS-analysen som er dekket av akseptansetester og systemdokumentasjon.

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

| ROS-ID | Risiko                                        | Tiltak | Manuell rutine                | Referanse                           |
| ------ | --------------------------------------------- | ------ | ----------------------------- | ----------------------------------- |
| 28065  | Jobbøker får ikke info om endringer           | ✅     | -                             | AT 6.1.1-6.2.2, 7.1.1-7.4.3         |
| 27487  | Kort flyttes ikke til avbrutt                 | ✅     | Prosedyre for sletting        | AT 6.1.4, 8.1.1-8.1.4, 9.1.1-9.1.2  |
| 27486  | Data forsvinner ved sletting                  | ✅     | Sletteregler                  | AT 1.3.1-1.3.3                      |
| 27485  | Deltakere forstår ikke invitasjon             | 🔄     | Intern komm., markedskontakt  | AT 5.5.1-5.5.3, 6.2.1               |
| 27484  | Treff arkiveres for tidlig                    | ➖     | -                             | Ikke relevant i pilot               |
| 27483  | Feil data sendes for arbeidsgiver             | ✅     | -                             | AT 2.4.1-2.4.2                      |
| 27482  | Feil arbeidsgiver legges til                  | 🔄     | -                             | AT 2.1.1-2.3.4 (varsel etter pilot) |
| 27433  | Arbeidsgiver uten reelt rekrutteringsbehov    | ⚠️     | Vurdering av behov            | -                                   |
| 27390  | Arrangør kvalitetssikrer ikke KI-tekst        | 🔄     | Opplæring, prosessbeskrivelse | AT 11.1.1-11.3.3                    |
| 27389  | ROB manipuleres til feilaktige vurderinger    | ✅     | -                             | AT 11.7.1-11.7.5                    |
| 27388  | Feilregistrering ved deltakelsesvalg          | ✅     | -                             | AT 6.1.1-6.2.2                      |
| 27386  | Aktivitetskort blir ikke opprettet            | ✅     | -                             | AT 5.1.5-5.1.7                      |
| 27385  | Nav-ansatte mangler info om frivillighet      | 🔄     | Info til fagpersoner          | AT 5.5.1-5.5.3                      |
| 27383  | Jobbøker får feil info i treffsiden           | ✅     | Manuell kontroll              | AT 7.3.1-7.3.3                      |
| 27381  | Deltaker mottar samme varsel gjentatte ganger | ✅     | -                             | AT 5.4.1                            |
| 27379  | Menneskelig feil - feil person får invitasjon | ⚠️     | Manuell kontroll              | -                                   |
| 27378  | Teknisk feil - feil person får invitasjon     | ✅     | -                             | AT 4.1.1-4.1.5, 4.7.1, 5.1.1-5.1.8  |
| 27275  | Usynlige kandidater ikke skjult               | ✅     | -                             | AT 4.2.1-4.8.4                      |
| 27273  | Jobbøker får feil/mangelfull info pga feil    | ✅     | Manuell kontroll              | AT 7.1.1-7.4.3, 5.5.1-5.5.3         |
| 27227  | Behandler flere opplysninger enn nødvendig    | ✅     | -                             | sysdok: arkitektur                  |
| 27225  | Ansatte får ikke tilgang                      | ✅     | -                             | AT 15.5.1-15.6.4                    |
| 27223  | Adressefelt brukt til andre formål            | ✅     | Manuell kontroll              | AT 1.4.1-1.4.3                      |
| 27222  | Feil arbeidsgiver/virksomhet registreres      | ✅     | -                             | AT 2.1.1-2.4.2                      |
| 27220  | Tilgang til kontor utenfor pilot              | ✅     | Manuell kontroll              | AT 15.5.1-15.6.4                    |
| 27219  | Særlige kategorier i tittel/beskrivelse       | ✅     | Manuell kontroll              | AT 11.9.1-11.9.8                    |
| 27217  | Tilgang til treff man ikke skulle hatt        | ✅     | -                             | AT 15.1.1-15.5.7                    |
| 27216  | KI identifiserer ikke diskriminerende tekst   | ✅     | Feedback fra brukere          | AT 11.1.2-11.2.7, 11.7.2-11.7.3     |
| 27215  | Brudd på informasjons-/tilgangskontroll       | ✅     | -                             | AT 15.1.1-15.5.7                    |

### Oppsummering manuelle rutiner

Følgende risikoer krever manuelle rutiner eller dokumentasjon som ikke er i systemdokumentasjonen:

| ROS-ID | Hva må dokumenteres                                              | Hvor finnes rutinen               |
| ------ | ---------------------------------------------------------------- | --------------------------------- |
| 27485  | Plan for intern kommunikasjon, retningslinjer for markedskontakt | Informasjonspakke i Loop          |
| 27433  | Rutine for vurdering av arbeidsgivers rekrutteringsbehov         | Informasjonspakke i Loop          |
| 27390  | Opplæring før pilot, prosessbeskrivelse for KI-verktøy           | Eget Loop-dokument for KI-verktøy |
| 27385  | Info til Nav-ansatte om frivillighet ved deltakelse              | Informasjonspakke i Loop          |
| 27379  | Brukerrutine - manuell kontroll av at riktig person inviteres    | Informasjonspakke i Loop          |

---

## Detaljert gjennomgang

### 28065 - Jobbsøker får ikke info om endringer i aktivitetskort

**Risiko:** Jobbsøker får ikke informasjon i aktivitetskortet (navn, dato, tidspunkt, sted, svarfrist) hvis det skjer endringer.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Jobbsøker kan velge status | 6.1.1-6.1.9 | ✅ Testet | Funksjonalitet som gjør at jobbsøker kan velge status (Ja/Nei), eller mulighet til å se om jobbsøker har vært inne og sett kortet. |
| Varsler ved endringer | 7.2.1-7.2.4 | ✅ Testet | Ser nærmere på det i endelig løsning. |
| Eiere kan informere deltakere | 7.1.1-7.1.6 | ✅ Testet | Noe som sjekkes under piloten. Spørsmål om arrangør kan sjekke aktivitetsplanen, evt. ta kontakt med arbeidsgiver. |
| Aktivitetskort synkroniseres ved endring | 7.4.1-7.4.5 | ✅ Testet | - |
| MinSide-varsel for jobbsøkere uten KRR (endring) | 7.4.5 | ✅ Testet | - |
| MinSide-varsel for jobbsøkere uten KRR (invitasjon) | 5.3.1-5.3.3 | ✅ Testet | Jobbsøkere uten KRR-kontaktinfo ser varsel på MinSide og kan klikke seg til treffsiden. |

**Relaterte tester:** [5.3.1-5.3.3](akseptansetester.md#minside-varsel-for-jobbsøkere-uten-krr), [6.1.1-6.1.9](akseptansetester.md#6-jobbsøker-svarer-på-invitasjon), [7.1.1-7.4.5](akseptansetester.md#7-endre-publisert-treff)

---

### 27487 - Kort flyttes ikke til avbrutt-kolonnen

**Risiko:** Aktivitetskort flyttes ikke til avbrutt når jobbsøker sier nei eller treffet avlyses.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Prosedyre for sletting | - | 📝 Rutine, ikke teknisk test | Veileder/markedskontakt sjekker at kortet er satt til riktig status. |
| Manuelt sjekke at kort flyttes | 6.1.7, 8.1.3-8.1.7, 9.1.4-9.1.5 | ✅ Testet | Gjennomføres ved testing. |

**Relaterte tester:** [6.1.7](akseptansetester.md#6-jobbsøker-svarer-på-invitasjon), [8.1.3-8.1.7](akseptansetester.md#8-avlyse-treff), [9.1.4-9.1.5](akseptansetester.md#9-treff-gjennomføres-og-avsluttes)

---

### 27486 - Data forsvinner ved sletting

**Risiko:** Noen sletter treff som ikke skulle vært slettet, og data går tapt.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Fjerne cascade fra database | - | 🔧 Teknisk implementasjon | I dag har utviklere tilgang til å slette innhold i treff. Fjerning vil redusere risiko for utilsiktet datatap. |
| Bekreftelsesknapp før sletting | 1.3.1-1.3.3 | ✅ Testet | - |
| Sletteregler for spesifikke situasjoner | - | 📝 Rutine | Feks. eier bør kunne slette tomme treff/feilopprettede treff. |
| "Myk" sletting (skjule, ikke slette) | - | 🔧 Teknisk implementasjon | - |

**Relaterte tester:** [1.3.1-1.3.3](akseptansetester.md#sletting-av-kladd-ros-27486)

---

### 27485 - Deltakere forstår ikke hvorfor de inviteres

**Risiko:** Jobbsøkere forstår ikke at rekrutteringstreff er frivillig, og mister tillit til NAV.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Lett å svare nei | 6.5 | ✅ Testet | Ved å gjøre det enkelt for jobbsøkeren å avslå en invitasjon til et treff, blir det tydelig at deltakelse er helt frivillig. |
| Info om frivillighet på MinSide | - | 📝 Avklares | - |
| Plan for intern kommunikasjon i Nav | - | 📝 Rutine | Klare kanaler og rutiner forebygger feilinformasjon og usikkerhet internt i Nav. |
| Invitasjon tydeliggjør frivillighet | 5.5.1-5.5.3 | ✅ Testet | Det er tydelig i løsningen at treffene skal være frivillig å delta på. Vi har skrevet inn tydelig at deltagelse er frivillig når deltaker svarer på invitasjonen. Hele tiden mulig å endre svar i forkant av treff. Også dokumentert i manuell rutine (Loop). |
| Retningslinjer for markedskontakt | - | 📝 Rutine | Lage retningslinjer tilpasset Rekrutteringstreff. Informasjonspakke i Loop som skal deles og gjennomgås av eier før gjennomføring av et treff. |

**Relaterte tester:** [5.5.1-5.5.3](akseptansetester.md#invitasjonsspråk-og-frivillighet-ros-27485), [6.2.1](akseptansetester.md#6-jobbøker-svarer-på-invitasjon)

---

### 27484 - Treff arkiveres for tidlig

**Risiko:** Automatisk arkivering slår inn for tidlig og treff forsvinner.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Ikke ha automatisk arkivering i pilot | - | ✅ Ikke relevant for pilot | Arkiverer manuelt når teamet vurderer at det er nødvendig. |

---

### 27483 - Sender/henter feil data for arbeidsgiver

**Risiko:** Bruker velger riktig arbeidsgiver, men systemet sender feil orgnummer pga. cache/state-mismatch.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Vise feilmelding hvis arbeidsgiver ikke legges til | 2.4.1-2.4.2 | ✅ Testet | Gjelder alle eksterne APIer. Vi henter fra enhetsregisteret. |

**Relaterte tester:** [2.3.1-2.3.2](akseptansetester.md#feilhåndtering-ros-27483)

---

### 27482 - Feil arbeidsgiver legges til på treffet

**Risiko:** Markedskontakt velger feil organisasjon (f.eks. Tentative AS i stedet for Tentativ AS).

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Forhåndsvisning av arbeidsgiverinfo | 2.1.1-2.1.3 | ✅ Testet (implisitt) | Forhåndsvise informasjon om arbeidsgiver (navn, organisasjonsnummer, adresse) før de legges til av markedskontakter. Tiltak for å unngå at man foreslår feil arbeidsgiver. Eier får en påminnelse om å kontrollere opplysningene til arbeidsgiver før de legges til. 1) Søk opp arbeidsgiver i systemet, 2) Velg riktig arbeidsgiver fra søkeresultatet, 3) Lagre valget. |
| Arbeidsgiversøk (pam-search) med orgnr/navn | 2.2.1-2.2.5 | ✅ Testet | Søk på firmanavn, organisasjonsnummer, delvise søkeord. Velg fra søkeliste med orgnr, navn og adresse. |
| Jobbsøker varsles ved arbeidsgiver-endring | - | 📝 Utenfor pilot | Jobbsøkere får beskjed hvis det skjer endringer fra arrangør. Forutsetter at eier oppdager feilen i forkant og kan gi beskjed. Ikke hvis vi endrer eller sletter arbeidsgiver. |
| Mulig å endre arbeidsgiver | 2.3.3 | ✅ Testet | Gjøre det mulig å endre arbeidsgiver fortløpende fordi vi ikke har invitasjon som går ut til arbeidsgiver. Vi gjør det enkelt for eier av treffet til å gjøre endringer dersom det oppdages feil. |
| Hente mer info fra Brønnøysund | - | 🔧 Teknisk implementasjon | Vi henter mer informasjon fra Brønnøysundregisteret. Dersom vi henter ut mer informasjon fra Brønnøysundregisteret reduserer det risikoen for at feil arbeidsgiver legges til. |

**Merknad:** Varsel til jobbøker ved endring av arbeidsgiver er utenfor scope for pilot. Tester 7.2.1-7.2.4 dekker varsel ved andre endringer (tidspunkt, sted, etc.), men ikke arbeidsgiver-endringer.

**Relaterte tester:** [2.1.1-2.4.2](akseptansetester.md#2-legge-til-arbeidsgiver)

---

### 27433 - Arbeidsgiver uten konkret rekrutteringsbehov

**Risiko:** Arbeidsgiver deltar kun for markedsføring, uten reelt rekrutteringsbehov.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Manuell kontroll ved valg av arbeidsgivere | - | 📝 Rutine | Vi lager rutiner for minimumskrav til arbeidsgivere for å sikre at arbeidsgivere som deltar på treffet har et konkret rekrutteringsbehov. Kontrollen må gjennomføres i forkant av treffene, og det er teamet som har ansvar for at det blir gjort. Rutinene er beskrevet i informasjonspakke i Loop. |
| Markedskontakt vurderer og dokumenterer behov | - | 📝 Rutine | Markedskontakten har vurdert og dokumentert arbeidsgivers rekrutteringsbehov før treffet publiseres. Dette sikrer ansvarliggjøring og etterprøvbarhet i prosessen. Markedskontakten skriver ned rekrutteringsbehovet i en etterregistrering/eget dokument. Det er arbeidsgiver eller markedskontakten (eier) som beskriver arbeidsgivers rekrutteringsbehov. I første omgang er det ikke et krav at dette er synlig for brukere i løsningene, men det må dokumenteres og lagres for å sikre etterprøvbarhet ift. behandlingsgrunnlaget. Testes/verifiseres manuelt i piloten. Finnes rutine for dette i Loop. |
| Retningslinjer for vurdering av behov | - | 📝 Rutine | Etablere retningslinjer for hvordan markedskontakten kan vurdere arbeidsgivers rekrutteringsbehov. Dette sikrer ansvarliggjøring og etterprøvbarhet i prosessen. Fremgår av rutiner/prosessbeskrivelse. I pilot er det tilstrekkelig å ha det dokumentert i en rutine, men signeringsforbehold kan vurderes. |
| Manuell innføring før pilottest | - | 📝 Rutine | Gi pilotbrukere manuell innføring/gjennomgang før pilottest. Rutine beskrevet i eget dokument (informasjonspakke i Loop). |

**Merknad:** Dette er en operasjonell kontroll som ikke kan automatiseres. Bør dokumenteres i rutinebeskrivelse.

---

### 27390 - Arrangør kvalitetssikrer ikke KI-tekst

**Risiko:** Innholdet blir unøyaktig eller mindre relevant fordi arrangør ikke kvalitetssikrer teksten på treff-siden. Bruker kontrollerer ikke innholdet, eller velger å se bort fra KI-vurderingen.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Manuell innføring/gjennomgang før pilottest | - | 📝 Rutine | Gi pilotbrukere manuell innføring/gjennomgang før pilottest. Rutine beskrevet i eget dokument (informasjonspakke i Loop). |
| KI-sjekken ROB kvalitetssikrer treffet | 11.1.1-11.3.3 | ✅ Testet | Funksjonalitetene til ROB som KI-verktøy er å analysere og kvalitetssikre. |
| Retningslinjer tilpasset Rekrutteringstreff | - | 📝 Dokumentert | Informasjonspakke i Loop som skal deles og gjennomgås av eier før gjennomføring av et treff. |
| Prosessbeskrivelse for KI-verktøy | - | 📝 Dokumentert | Utarbeide og implementere rutiner rettet mot brukere for bruk av KI-verktøyet. Finnes i eget Loop-dokument. Dette sikrer enhetlig praksis, reduserer feilrisiko og legger til rette for oppfølging. |

**Relaterte tester:** [11.1.1-11.3.3](akseptansetester.md#11-ki-moderering)

---

### 27389 - ROB manipuleres til feilaktige vurderinger

**Risiko:** ROB manipuleres til å gi feilaktige eller utilsiktede vurderinger ved at brukere utnytter svakheter i systemets treningsdata, logikk eller prompt.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Manipulasjonstesting/penetrasjonstesting av ROB | 11.7.1-11.7.5 | ✅ Testet | Målet er å finne svakheter som kan utnyttes av en angriper eller som kan føre til utilsiktet feilopppørsel. Tester hvordan systemet reagerer når data eller brukerhandlinger manipuleres på uventede måter. Gjennomføres manuelt. |
| Logging av svar for administrasjonskontroll | 11.4.1-11.4.8 | ✅ Testet | Vi logger svarene som genereres for å avdekke eventuelle manipulasjoner gjennom administrasjonskontrollen i løsningen. Vi overvåker og kontrollerer hvordan KI-en reagerer kontinuerlig. Forenkler prosessen med å gjøre kontroll på vurderinger og sikrer sporbarhet i løsningen. (Kun tilgjengelig for adminbrukere/utviklertilgang) |
| Bruker kan overstyre KI-sjekken | 11.3.1-11.3.3 | ✅ Testet | Vi lar bruker overstyre KI-sjekken for å ivareta menneskelig kontroll. Vi overstyrer ikke at noen skriver feil. Trenger ikke å manipulere ROB for å endre teksten, men vi kan overvåke når folk gjør feil gjennom logging. |
| Retningslinjer om at ROB er et verktøy | - | 📝 Dokumentert | Formidle gjennom retningslinjer i løsningen at ROB kun er et verktøy som bruker må kontrollere. Tiltak for å sikre ansvarliggjøring av bruker - menneskelig kontroll. Utarbeide og implementere rutiner rettet mot brukere for bruk av KI-verktøyet. Dette sikrer enhetlig praksis, reduserer feilrisiko og legger til rette for oppfølging. |

**Relaterte tester:** [11.7.1-11.7.5](akseptansetester.md#robusthetstesting-av-ki-ros-27546)

---

### 27388 - Feilregistrering ved deltakelsesvalg

**Risiko:** Det skjer feilregistrering ved deltakelsesvalg - jobbsøker svarer "ja", men det blir registrert som "nei".

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Verifisere hele løpet gjennom tester | 6.1.1-6.1.9 | ✅ Testet | Gjennomfører manuelle tester. Fagressurs/domeneekspert verifiserer at det fungerer. |

**Relaterte tester:** [6.1.1-6.1.9](akseptansetester.md#6-jobbøker-svarer-på-invitasjon)

---

### 27386 - Aktivitetskort blir ikke opprettet

**Risiko:** Aktivitetskortet blir ikke opprettet, noe som fører til at jobbsøker ikke får invitasjon til treffet.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Automatiserte tester | - | 🔧 Teknisk implementasjon |
| Manuell testing før pilot | 5.1.5-5.1.7 | ✅ Testet |

**Relaterte tester:** [5.1.5-5.1.7](akseptansetester.md#5-invitere-jobbøker)

---

### 27385 - Nav-ansatte mangler info om frivillighet

**Risiko:** Ansatte i andre deler av Nav mangler nødvendig informasjon om at Rekrutteringstreff ikke skal påvirke andre ytelser jobbsøker mottar.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Dele informasjon med fagpersoner | - | 📝 Rutine | Dele informasjon med fagpersoner i seksjon for arbeidsgivertjenester. Sikre gode kommunikasjonskanaler slik at ansatte som trenger informasjon får det. |
| Tydelig i løsningen at treff er frivillig | 5.5.1-5.5.3 | ✅ Testet | Det er tydelig i løsningen at treffene skal være frivillig å delta på. Vi har skrevet inn tydelig at deltagelse er frivillig når deltaker svarer på invitasjonen. Hele tiden mulig å endre svar i forkant av treff. Også dokumentert i manuell rutine (Loop). |

**Relaterte tester:** [5.5.1-5.5.3](akseptansetester.md#invitasjonsspråk-og-frivillighet-ros-27485)

---

### 27383 - Jobbsøker får feil info i treffsiden

**Risiko:** Jobbsøker får feil informasjon på treffsiden (rekrutteringstreff-bruker) - f.eks. navn på treffet, dato, tidspunkt, sted og svarfrist.

> **Merk:** Treffsiden = rekrutteringstreff-bruker. SMS/e-post inneholder kun lenke til treffsiden og en oppsummering av endringer, men jobbsøker må åpne treffsiden for å se all oppdatert info.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Treffsiden viser oppdaterte detaljer etter endring | 7.11 | ✅ Testet | - |
| Mulig for markedskontakt å redigere info | 7.1.1-7.1.6 | ✅ Testet | Legge inn mulighet for å endre treff i ettertid. Gjør det mulig å korrigere feil. |
| Aktivitetskort oppdateres | 7.3.2-7.3.3 | ✅ Testet | - |
| Rutiner for å informere ved feil | - | 📝 Rutine | Lage rutiner for å informere om hva/hvordan vi skal kommunisere hvis det skjer feil. Rutine som beskriver hva/hvordan vi skal kommunisere hvis det skjer feil, kommer an på hvilken feil. Feil kan være alt fra ubetydelig til svært alvorlig. Rutine for feil bruk av ROB implementeres inne i løsningen. Kommunikasjon som gjelder alvorlige feil (feks personvernbrudd) finnes allerede på generelt grunnlag. For mindre alvorlige feil kan kommunikasjonen ta utgangspunkt i sunn fornuft og best practice. Rutiner er i informasjonspakke i Loop. |
| Manuell kontroll av info til jobbsøker | - | 📝 Rutine | Manuelt kontrollere informasjonen som blir sendt til jobbsøker. Testes manuelt. |
| Forhåndsvisning av treffet | - | 🔧 Teknisk (se treff-side) | For å gjøre det mulig å dobbeltsjekke informasjonen. |
| Sikre triangulering av felter (design) | - | 🔧 Design | Beskrivelse inneholder korrekt informasjon, men adressefeltet er feil. Korrekt informasjon ett sted kan rette feil et annet sted. Reduserer sannsynligheten for feil. |

**Relaterte tester:** [7.3.1-7.3.3](akseptansetester.md#oppdatering-i-treffsiden-og-aktivitetskort-ros-27383)

---

### 27381 - Deltaker mottar samme varsel gjentatte ganger

**Risiko:** En deltaker mottar samme varsel gjentatte ganger som følge av teknisk eller menneskelig svikt (spam).

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Test for dobbel invitasjon | 5.4.1 | ✅ Testet | Vi legger opp en logikk hvor bruker kun kan sende invitasjon i henhold til logikken. Noe må skje teknisk for å kunne sende SMS. Hvis jobbøker har sagt nei, så får hen ikke flere varsler. |
| Lytte til kandidatvarselet | - | 🔧 Teknisk implementasjon | Vi lytter til kandidatvarselet (Varsel API / Min side API). Kandidatvarsel er når vi sender SMS til personbrukere. |

**Relaterte tester:** [5.4.1](akseptansetester.md#feilsituasjoner)

---

### 27379 - Menneskelig feil - feil person får invitasjon

**Risiko:** En menneskelig feil fører til at innbyggere mottar en invitasjon som ikke er ment for dem. Nav-ansatt legger til feil person til treffet.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Manuell kontroll av treffene | - | 📝 Rutine | Gi pilotbrukere manuell innføring/gjennomgang før pilottest. Rutine beskrevet i eget dokument (informasjonspakke i Loop). |

**Merknad:** Menneskelig feil kan ikke forhindres teknisk, men begrenses ved rutiner og opplæring.

---

### 27378 - Teknisk feil - feil person får invitasjon

**Risiko:** En teknisk feil fører til at innbyggere mottar invitasjon som ikke er ment for dem, f.eks. feil i API, feilkoblinger av IDer.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Tester for å verifisere at systemet ikke feiler | 4.1.1-4.1.5 | ✅ Testet | Gjennomføre tester for å verifisere at systemet ikke feiler ved sending av data. For å sikre at Aktivitetsplanen fungerer som den skal. Vi får kun testet at de som skal ha informasjon får det, og de som er lagt til på listen, men ikke mottatt informasjon. |
| Håndtere endring av fnr (f.eks. kjønnsskifte) | 4.7.1 | ✅ Testet | - |
| Verifisere hele løpet gjennom tester | 5.1.1-5.1.8 | ✅ Testet | Gjennomfører manuelle tester. Fagressurs/domeneekspert verifiserer at det fungerer. |

**Relaterte tester:** [4.1.1-4.1.5](akseptansetester.md#4-legge-til-jobbøker), [4.7.1](akseptansetester.md#endring-av-fødselsnummer), [5.1.1-5.1.8](akseptansetester.md#5-invitere-jobbøker)

---

### 27275 - Usynlige kandidater ikke skjult

**Risiko:** Vi skjuler ikke CV-en til deltakere som skal være usynlige kandidater. Personen var synlig, men synlighetsregler slår inn og personen skal være "usynlig".

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Synlighetsreglene skjuler CV | 4.2.1-4.8.4 | ✅ Testet | Ivaretatt gjennom kandidatindeksen. Det er dette som fjerner/filtrerer vekk usynlige kandidater. |

**Relaterte tester:** [4.2.1-4.8.4](akseptansetester.md#synlighet)

---

### 27273 - Jobbsøker får feil/mangelfull info pga feil på treff-siden

**Risiko:** Jobbsøker mottar feil eller mangelfull informasjon som følge av svakheter i løsningen. Kan føre til manglende oppmøte.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Manuell kontroll av treffene | - | 📝 Rutine | - |
| Melding ved oppdatering av treff | 7.2.1-7.2.4 | ✅ Testet | Sende ut en melding til de som skal på treffet dersom treffet blir oppdatert, feks. dialogmelding i Modia. Oppdatering: Skal ikke gjennomføres i pilot. Det løses med varsler om endringer i treff. |
| Mulighet for å endre treff i ettertid | 7.1.1-7.1.6 | ✅ Testet | Legge inn mulighet for å endre treff i ettertid. Gjør det mulig å korrigere feil. |
| Forhåndsvisning av treffet | - | 🔧 Teknisk (se treff-side) | For å gjøre det mulig å dobbeltsjekke informasjonen. |
| Sikre triangulering av felter (design) | - | 🔧 Design | Beskrivelse inneholder korrekt informasjon, men adressefeltet er feil. Korrekt informasjon ett sted kan rette feil et annet sted. Reduserer sannsynligheten for feil. |
| Tydelig at deltakelse er frivillig | 5.5.1-5.5.3 | ✅ Testet | Det er tydelig i løsningen at treffene skal være frivillig å delta på. Vi har skrevet inn tydelig at deltagelse er frivillig når deltaker svarer på invitasjonen. |

**Relaterte tester:** [7.1.1-7.4.5](akseptansetester.md#7-endre-publisert-treff), [5.5.1-5.5.3](akseptansetester.md#invitasjonspråk-og-frivillighet-ros-27485)

---

### 27227 - Behandler flere opplysninger enn nødvendig

**Risiko:** Rekrutteringstreff behandler flere opplysninger enn nødvendig fordi jobbsøkere legges til via kandidatsøket.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Skiller mellom persontreffID og kandidatnummer | - | 🔧 Arkitekturbeslutning | En arbeidssøker kan ha ulike IDer utifra om vedkommende har tilknytning til Rekrutteringsbistand eller Rekrutteringstreff. Ved at arbeidssøker får egne ID-nøkler i hvert system gjør det enklere å holde dataene atskilt. Vi slår opp i PDL med fødselsnummeret for å hente riktige data om arbeidssøker. Vi gjør også oppslag for å se om personen har CV, slik at vi kan vise dette. |
| Forenlighetsvurdering gjennomført i Støtte til Etterlevelse | - | ✅ Gjennomført | Forenlighetsvurdering handler om å vurdere om en viderebehandling av personopplysninger er forenlig med det opprinnelige formålet. I Rekrutteringstreff behandles de samme personopplysninger som Rekrutteringsbistand. Det overordnede formålet er det samme, og har forankring i det samme behandlingsgrunnlaget. Forskjellen er selve gjennomføringen. Det er vurdert at koblingen mellom formålene er svært nær, konteksten opplysningene ble samlet inn for ligger innenfor registrertes rimelige forventninger - det er å komme i arbeid. Det er også færre personopplysninger som deles, blant annet fordi CV-opplysninger ikke blir delt med arbeidsgiver. Arbeidsgiver har heller ikke innsyn i deltakerlisten. |

**Merknad:** Dekket gjennom arkitekturbeslutning og juridisk vurdering.

---

### 27225 - Ansatte får ikke nødvendig tilgang

**Risiko:** Ansatte med tjenestlig behov får ikke tilgang til løsningen pga. feil ved tildeling.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Manuell kontroll av treff | - | 📝 Rutine | - |
| Tilgang til spesifikke kontorer | 15.5.1-15.5.7 | ✅ Testet | Tilgang gis til spesifikke kontorer. Vi vurderer at "alle" på kontoret har tjenestlig behov. Tiltaket gjør at flere kan få tilgang på ett kontor. Vi kan styre tilgangene som deles ut, dvs. at vi kan bestemme at tilgangene på ett kontor er begrenset til tilgangsstyringen som følger av rollene "jobbøkerrettet", "arbeidsgiverrettet". |
| Tilgang oppdateres ved bytte av kontor | 15.6.1-15.6.4 | ✅ Testet | NAV-ansatte kan bytte aktivt kontor via modiacontextholder. Tilgangen oppdateres umiddelbart basert på valgt kontor. |
| Tilgang til spesifikke personer (NavID) | - | 📝 Avklares | Det er teamet som styrer hvem som har tjenestelig behov. Hvis vi får forespørsel om tilgang vurderer vi det konkret for den ansatte det gjelder. Ikke hardkode Nav-IDenter, kan medføre risiko for å eksponere IDer for utenforstående. |

**Relaterte tester:** [15.5.1-15.6.4](akseptansetester.md#pilotkontor-tilgang)

---

### 27223 - Adressefelt brukt til andre formål

**Risiko:** Arrangør legger inn feil adresse eller misvisende informasjon i adressefeltet.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Begrensning på antall tegn | 1.4.1 | ✅ Testet | Vi legger inn en begrensning på antall tegn i adressefeltet. Gjelder felt for adresse og kontaktinformasjon. Vi legger begrensningen på frontend ettersom det er her den største risikoen ligger. Begrensningen som er lagt inn er 100 tegn. |
| Manuell kontroll av treff | - | 📝 Rutine | - |
| Adressesøk med forslag | 1.4.2-1.4.3 | ✅ Testet | Vi legger inn adressesøk slik at man må velge ett av alternativene som dukker opp. Bruker Postens API (adresser). Blir en avhengighet til Posten. Ikke aktuelt for pilottestingen, men vi vurderer tiltaket i endelig løsningen. |

**Relaterte tester:** [1.4.1-1.4.3](akseptansetester.md#adressefelt-ros-27223)

---

### 27222 - Feil arbeidsgiver/virksomhet registreres

**Risiko:** Feil arbeidsgiver registreres pga. like navn, utdatert info i Brønnøysund, eller feil orgnummer.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Kan fjerne feil arbeidsgiver | 2.1.3, 2.3.3 | ✅ Testet | Vi kan fjerne arbeidsgivere dersom det legges til feil arbeidsgiver på treff. Tiltaket gjennomføres manuelt ved at teamet fjerner arbeidsgivere som ikke skulle vært lagt til på treffet. |
| Bekreftelse før invitasjon til arbeidsgiver | - | ✅ N/A (ingen invitasjon i pilot) | Legge på bekreftelse på om man vil sende ut invitasjon til arbeidsgiver. Det er en dobbeltsjekk - ikke aktuelt i pilot. I pilot tar vi det for gitt at de arbeidsgiverne som blir valgt skal være med. |
| Forhåndsvisning av arbeidsgiverinfo | 2.1.1-2.1.3 | ✅ Testet | Forhåndsvise informasjon om arbeidsgiver før de legges til (navn, orgnummer) av markedskontakter. For å unngå at man velger feil arbeidsgiver: 1) Søk opp arbeidsgiver i systemet, 2) Velg riktig arbeidsgiver fra søkeresultatet, 3) Lagre valget. |
| Arbeidsgiversøk (pam-search) med orgnr/navn | 2.2.1-2.2.5 | ✅ Testet | Søk på firmanavn, organisasjonsnummer, delvise søkeord. Velg fra søkeliste med orgnr, navn og adresse. |

**Merknad:** Ligner på 27482. Dekket av samme tester.

**Relaterte tester:** [2.1.1-2.4.2](akseptansetester.md#2-legge-til-arbeidsgiver)

---

### 27220 - Tilgang til kontor utenfor pilot

**Risiko:** Et kontor som ikke skal være med i piloten får likevel tilgang.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Manuell kontroll av treff | - | 📝 Rutine | - |
| Tilgangsstyring i backend på bestemte kontorer | 15.5.1-15.5.7 | ✅ Testet | Implementere tilgangsstyring i backend på bestemte kontorer til treff. Tiltaket gjør det sikkert i løsningen. I praksis skjuler vi Rekrutteringstreff for alle andre, og åpner opp for andre - en form for feature flagging. |
| Tilgang oppdateres ved bytte av kontor (modiacontextholder) | 15.6.1-15.6.4 | ✅ Testet | NAV-ansatte kan bytte aktivt kontor. Tilgangen evalueres på nytt basert på valgt kontor. |

**Relaterte tester:** [15.5.1-15.6.4](akseptansetester.md#pilotkontor-tilgang)

---

### 27219 - Særlige kategorier personopplysninger i tittel/beskrivelse

**Risiko:** Tittel eller beskrivelse inneholder personopplysninger av særlig kategori (GDPR art. 9) eller sensitiv informasjon om Nav-tiltak.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Manuell kontroll av pilotkontor | - | 📝 Rutine | - |
| Ikke lagre personopplysninger i KI-logger | - | 🔧 Implementert | Ikke lagre personopplysninger i logger som genereres av KI-verktøyet. Må vurdere behovet for å lagre personopplysninger før filtrering. Kanskje ikke gjennomføre tiltaket i pilot, men når løsningen lanseres. |
| Tall (4+ siffer) fjernes før Azure OpenAI | 11.9.1-11.9.7 | ✅ Testet (persondata-filter) | Enkel sjekk på fnr og epost før vi sender data til Azure OpenAI. Vi ønsker å sende minst mulig til Azure OpenAI, derfor gjør vi en enkel sjekk på fnr og epost før data sendes til Azure OpenAI. Se Loop-dokument: Bruk av OpenAI i Rekrutteringstreff. |
| E-postadresser fjernes før Azure OpenAI | 11.9.8 | ✅ Testet (persondata-filter) | Se ovenfor. |

**Merknad:** Systemet gir ikke feilmelding til bruker - tall og e-post fjernes automatisk før innsending. Verifiseres i KI-logg ved å sammenligne "originalTekst" og "sendtTekst".

**Relaterte tester:** [11.9.1-11.9.8](akseptansetester.md#persondata-filtrering-ros-27219)

---

### 27217 - Tilgang til treff man ikke skulle hatt

**Risiko:** Nav-ansatte får utilsiktet tilgang til treff i piloten.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Testscript for tilgang | 15.1.1-15.6.4 | ✅ Testet | Lage og implementere testscript for tilgang, slik at riktig funksjon knyttes til riktig tilgang. Gjennomføres manuelt. Testscript er laget og finnes i Loop (Team Toi), men må implementeres og testes spesifikt for Rekrutteringstreff. |
| Tilgangskontroll for hvem som kan finne/se/legge til deltakere | 15.1.1-15.3.7 | ✅ Testet | Det skilles mellom tilgangene "jobbsøkerrettet", "arbeidsgiverrettet" og "utvikler". Jobbsøkertilgang = Populasjon begrenset til eget kontor, og begrenset funksjonalitet. Arbeidsgivertilgang = alle funksjonaliteter og populasjon i hele landet (skjule tilganger til andre kandidatlister - kun egne treff). Utviklertilgang = Full tilgang til alle (Jobbsøker + Arbeidsgivertilgang). Tiltak for tilgangsrollene er de samme som for Rekrutteringsbistand fordi rollene er tilsvarende. Se ROS for Tilgangsstyring for Rekrutteringsbistand ID: 1571. |
| Tilgangskontroll for pilotkontor | 15.5.1-15.5.7 | ✅ Testet | Legge på tilgangskontroll for å forhindre at andre enn de kontorene som er med i piloten skal få tilgang. I piloten skal ikke alle ha tilgang til treffene. Kun pilotkontor. Vi tar sikte på at piloten blir begrenset til 2-3 kontorer med denne tilgangen. |

**Relaterte tester:** [15.1.1-15.5.7](akseptansetester.md#15-tilgangsstyring-og-roller)

---

### 27216 - KI identifiserer ikke diskriminerende tekst

**Risiko:** KI-verktøyet (ROB) identifiserer ikke diskriminerende tekst eller personopplysninger i tittel/beskrivelse.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Test diskriminering på alder (tittel) | 11.1.2 | ✅ Testet | - |
| Test diskriminering på kjønn (tittel) | 11.1.3 | ✅ Testet | - |
| Test diskriminering på helse (tittel) | 11.1.4 | ✅ Testet | - |
| Test diskriminering på etnisitet (tittel) | 11.1.5 | ✅ Testet | - |
| Test diskriminering på alder (innlegg) | 11.2.2 | ✅ Testet | - |
| Test diskriminering på kjønn (innlegg) | 11.2.3 | ✅ Testet | - |
| Test diskriminering på helse (innlegg) | 11.2.4 | ✅ Testet | - |
| Test diskriminering på etnisitet (innlegg) | 11.2.5 | ✅ Testet | - |
| Test subtil diskriminering | 11.7.2 | ✅ Testet | - |
| Test diskriminering på annet språk | 11.7.3 | ✅ Testet | - |
| Modellkontroll gjennom KI-logg | 11.4.1-11.4.8 | ✅ Testet | Vi gjennomfører modellkontroll gjennom tester/stikkprøver av versjoner av Azure OpenAI. Gjennomføres manuelt og automatisk. |
| Retningslinjer for fritekstfelt | - | 📝 Dokumentert | Vi viser retningslinjer for hva fritekstfeltet skal brukes til. Bruker får informasjon om hvilke opplysninger som ikke skal legges inn i fritekstfeltet, at brukeren selv er ansvarlig for innholdet som registreres, og at kunstig intelligens (KI) benyttes i løsningen. |
| Rutiner for bruk av KI-verktøy | - | 📝 Rutine | Utarbeide og implementere rutiner rettet mot brukere for bruk av KI-verktøyet. Finnes i eget Loop-dokument. Dette sikrer enhetlig praksis, reduserer feilrisiko og legger til rette for oppfølging. |
| Feedback fra brukere | - | 📝 Brukertest | - |
| Toggle for å overskride KI-vurdering | 11.3.1-11.3.3 | ✅ Testet | Vi lar bruker overstyre KI-sjekken for å ivareta menneskelig kontroll. |

**Relaterte tester:** [11.1.2-11.2.7](akseptansetester.md#ki-validering-av-tittel-ros-27216), [11.7.2-11.7.3](akseptansetester.md#robusthetstesting-av-ki-ros-27546)

---

### 27215 - Brudd på informasjons- og tilgangskontroll

**Risiko:** Informasjon om personbrukere eller intern informasjon deles med uvedkommende pga. hacking, svindel eller illojale handlinger.

**Tiltak:**
| Tiltak | Test-ID | Status | Beskrivelse |
|--------|---------|--------|-------------|
| Tilgangskontroll for pilotkontor | 15.5.1-15.5.7 | ✅ Testet | Legge på tilgangskontroll for å forhindre at andre enn de kontorene som er med i piloten skal få tilgang. I piloten skal ikke alle ha tilgang til treffene. Kun pilotkontor. Vi tar sikte på at piloten blir begrenset til 2-3 kontorer med denne tilgangen. |
| Tilgangskontroll for hvem som kan finne/se/legge til deltakere | 15.1.1-15.3.7 | ✅ Testet | Det skilles mellom tilgangene "jobbsøkerrettet", "arbeidsgiverrettet" og "utvikler". Jobbsøkertilgang = Populasjon begrenset til eget kontor, og begrenset funksjonalitet. Arbeidsgivertilgang = alle funksjonaliteter og populasjon i hele landet (skjule tilganger til andre kandidatlister - kun egne treff). Utviklertilgang = Full tilgang til alle. Se ROS for Tilgangsstyring for Rekrutteringsbistand ID: 1571. |

**Relaterte tester:** [15.1.1-15.5.7](akseptansetester.md#15-tilgangsstyring-og-roller)

---

## Oppsummering av gap

### ✅ Nylig lagt til tester

Følgende gap er nå dekket med tester:

| ROS-ID | Risiko                                 | Nye tester  |
| ------ | -------------------------------------- | ----------- |
| 27486  | Bekreftelsesdialog ved sletting        | 1.3.1-1.3.3 |
| 27485  | Frivillighetsinfo i varsel             | 5.5.1-5.5.3 |
| 27483  | Feilhåndtering ved arbeidsgiveroppslag | 2.4.1-2.4.2 |
| 27223  | Adressefeltvalidering og -søk          | 1.4.1-1.4.3 |
| 28065  | Endring og synkronisering              | 7.4.1-7.4.5 |

### Manuell rutine (ikke testet i løsning)

| ROS-ID | Risiko                                     | Vurdering                   |
| ------ | ------------------------------------------ | --------------------------- |
| 27433  | Arbeidsgiver uten reelt rekrutteringsbehov | Operasjonell kontroll       |
| 27379  | Menneskelig feil - feil person invitert    | Kan ikke forhindres teknisk |
| 27385  | Nav-ansatte mangler info om frivillighet   | Kommunikasjon + rutine      |

### Bør vurderes

| Gap                          | ROS-ID | Vurdering                           |
| ---------------------------- | ------ | ----------------------------------- |
| MinSide-info om frivillighet | 27485  | Avklar hvor denne infoen skal ligge |
| Penetrasjonstesting av ROB   | 27389  | Bør gjennomføres før prod           |

---

## Vurdering: Utviklerrutiner-mappe

ROS-analysen nevner ikke eksplisitt rutiner for å deploye eller ta i bruk nye KI-modeller hos OpenAI. Imidlertid finnes det allerede dokumentasjon om dette i [KI-tekstvalidering](../5-ki/ki-tekstvalidering.md) under seksjonen "Prosess for evaluering av ny systemprompt eller modell".

**Anbefaling:** Det er ikke nødvendig å opprette en egen `utviklerrutiner`-mappe per nå, da:

1. KI-modellbytte er allerede dokumentert i `5-ki/ki-tekstvalidering.md`
2. Deploy-rutiner er typisk beskrevet i `README.md` eller `nais/`-konfigurasjon
3. ROS-tiltakene handler primært om funksjonelle kontroller, ikke deploy-rutiner

**Dersom det oppstår behov for slike rutiner**, kan følgende struktur vurderes:

```
docs/
├── 8-utviklerrutiner/
│   ├── bytte-ki-modell.md      # Sjekkliste for å ta i bruk ny modell
│   ├── deploy-til-prod.md      # Sjekkliste for produksjonsdeploy
│   └── feilsøking.md           # Vanlige feil og løsninger
```

---

## Relaterte dokumenter

- [Akseptansetester](akseptansetester.md) - Fullstendige testscenarier
- [ROS-tiltak for KI](ros-ki-pilot.md) - ROS-tiltak spesifikke for KI-sjekken (ROB)
- [KI-tekstvalidering](../5-ki/ki-tekstvalidering.md) - KI-validering og logging (inkl. modellbytte-prosess)
- [Tilgangsstyring](../3-sikkerhet/tilgangsstyring.md) - Roller og tilgang
