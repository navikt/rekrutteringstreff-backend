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

| ROS-ID | Risiko                                        | Tiltak | Manuell rutine                | Referanse                       |
| ------ | --------------------------------------------- | ------ | ----------------------------- | ------------------------------- |
| 28065  | Jobbsøker får ikke info om endringer          | ✅     | -                             | AT 6.1-6.9, 7.1-7.18            |
| 27487  | Kort flyttes ikke til avbrutt                 | ✅     | Prosedyre for sletting        | AT 6.7, 8.3-8.7, 9.4-9.5        |
| 27486  | Data forsvinner ved sletting                  | ✅     | Sletteregler                  | AT 1.11-1.13                    |
| 27485  | Deltakere forstår ikke invitasjon             | 🔄     | Intern komm., markedskontakt  | AT 5.14-5.15, 6.5               |
| 27484  | Treff arkiveres for tidlig                    | ➖     | -                             | Ikke relevant i pilot           |
| 27483  | Feil data sendes for arbeidsgiver             | ✅     | -                             | AT 2.8-2.9                      |
| 27482  | Feil arbeidsgiver legges til                  | 🔄     | -                             | AT 2.1-2.7 (varsel etter pilot) |
| 27433  | Arbeidsgiver uten reelt rekrutteringsbehov    | ⚠️     | Vurdering av behov            | -                               |
| 27390  | Arrangør kvalitetssikrer ikke KI-tekst        | 🔄     | Opplæring, prosessbeskrivelse | AT 11.1-11.17                   |
| 27389  | ROB manipuleres til feilaktige vurderinger    | ✅     | -                             | AT 11.31-11.35                  |
| 27388  | Feilregistrering ved deltakelsesvalg          | ✅     | -                             | AT 6.1-6.9                      |
| 27386  | Aktivitetskort blir ikke opprettet            | ✅     | -                             | AT 5.5-5.7                      |
| 27385  | Nav-ansatte mangler info om frivillighet      | 🔄     | Info til fagpersoner          | AT 5.14-5.15                    |
| 27383  | Jobbsøker får feil info i treffsiden          | ✅     | Manuell kontroll              | AT 7.11-7.13                    |
| 27381  | Deltaker mottar samme varsel gjentatte ganger | ✅     | -                             | AT 5.12                         |
| 27379  | Menneskelig feil - feil person får invitasjon | ⚠️     | Manuell kontroll              | -                               |
| 27378  | Teknisk feil - feil person får invitasjon     | ✅     | -                             | AT 4.1-4.5, 5.1-5.7             |
| 27275  | Usynlige kandidater ikke skjult               | ✅     | -                             | AT 4.5-4.29                     |
| 27273  | Jobbsøker får feil/mangelfull info pga feil   | ✅     | Manuell kontroll              | AT 7.1-7.18, 5.14-5.15          |
| 27227  | Behandler flere opplysninger enn nødvendig    | ✅     | -                             | sysdok: arkitektur              |
| 27225  | Ansatte får ikke tilgang                      | ✅     | -                             | AT 15.26-15.32                  |
| 27223  | Adressefelt brukt til andre formål            | ✅     | Manuell kontroll              | AT 1.14-1.16                    |
| 27222  | Feil arbeidsgiver/virksomhet registreres      | ✅     | -                             | AT 2.1-2.7                      |
| 27220  | Tilgang til kontor utenfor pilot              | ✅     | Manuell kontroll              | AT 15.26-15.32                  |
| 27219  | Særlige kategorier i tittel/beskrivelse       | ✅     | Manuell kontroll              | AT 11.36-11.43                  |
| 27217  | Tilgang til treff man ikke skulle hatt        | ✅     | -                             | AT 15.1-15.32                   |
| 27216  | KI identifiserer ikke diskriminerende tekst   | ✅     | Feedback fra brukere          | AT 11.2-11.14, 11.32-11.33      |
| 27215  | Brudd på informasjons-/tilgangskontroll       | ✅     | -                             | AT 15.1-15.32                   |

### Oppsummering manuelle rutiner

Følgende risikoer krever manuelle rutiner eller dokumentasjon som ikke er i systemdokumentasjonen:

| ROS-ID | Hva må dokumenteres                                              |
| ------ | ---------------------------------------------------------------- |
| 27485  | Plan for intern kommunikasjon, retningslinjer for markedskontakt |
| 27433  | Rutine for vurdering av arbeidsgivers rekrutteringsbehov         |
| 27390  | Opplæring før pilot, prosessbeskrivelse for KI-verktøy           |
| 27385  | Info til Nav-ansatte om frivillighet ved deltakelse              |
| 27379  | Brukerrutine - manuell kontroll av at riktig person inviteres    |

---

## Detaljert gjennomgang

### 28065 - Jobbsøker får ikke info om endringer i aktivitetskort

**Risiko:** Jobbsøker får ikke informasjon i aktivitetskortet (navn, dato, tidspunkt, sted, svarfrist) hvis det skjer endringer.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Jobbsøker kan velge status | 6.1-6.9 | ✅ Testet |
| Varsler ved endringer | 7.7-7.10 | ✅ Testet |
| Eiere kan informere deltakere | 7.1-7.6 | ✅ Testet |
| Aktivitetskort synkroniseres ved endring | 7.14-7.18 | ✅ Testet |
| MinSide-varsel for jobbsøkere uten KRR | 7.18 | ✅ Testet |

**Relaterte tester:** [6.1-6.9](akseptansetester.md#6-jobbsøker-svarer-på-invitasjon), [7.1-7.18](akseptansetester.md#7-endre-publisert-treff)

---

### 27487 - Kort flyttes ikke til avbrutt-kolonnen

**Risiko:** Aktivitetskort flyttes ikke til avbrutt når jobbsøker sier nei eller treffet avlyses.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Prosedyre for sletting | - | 📝 Rutine, ikke teknisk test |
| Manuelt sjekke at kort flyttes | 6.7, 8.3-8.7, 9.4-9.5 | ✅ Testet |

**Relaterte tester:** [6.7](akseptansetester.md#6-jobbsøker-svarer-på-invitasjon), [8.3-8.7](akseptansetester.md#8-avlyse-treff), [9.4-9.5](akseptansetester.md#9-treff-gjennomføres-og-avsluttes)

---

### 27486 - Data forsvinner ved sletting

**Risiko:** Noen sletter treff som ikke skulle vært slettet, og data går tapt.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Fjerne cascade fra database | - | 🔧 Teknisk implementasjon |
| Bekreftelsesknapp før sletting | 1.11-1.13 | ✅ Testet |
| Sletteregler for spesifikke situasjoner | - | 📝 Rutine |
| "Myk" sletting (skjule, ikke slette) | - | 🔧 Teknisk implementasjon |

**Relaterte tester:** [1.11-1.13](akseptansetester.md#sletting-av-kladd-ros-27486)

---

### 27485 - Deltakere forstår ikke hvorfor de inviteres

**Risiko:** Jobbsøkere forstår ikke at rekrutteringstreff er frivillig, og mister tillit til NAV.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Lett å svare nei | 6.5 | ✅ Testet |
| Info om frivillighet på MinSide | - | 📝 Avklares |
| Plan for intern kommunikasjon i Nav | - | 📝 Rutine |
| Invitasjon tydeliggjør frivillighet | 5.14-5.15 | ✅ Testet |
| Retningslinjer for markedskontakt | - | 📝 Rutine |

**Relaterte tester:** [5.14-5.15](akseptansetester.md#frivillighetsinfo-i-varsel-ros-27485), [6.5](akseptansetester.md#6-jobbsøker-svarer-på-invitasjon)

---

### 27484 - Treff arkiveres for tidlig

**Risiko:** Automatisk arkivering slår inn for tidlig og treff forsvinner.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Ikke ha automatisk arkivering i pilot | - | ✅ Ikke relevant for pilot |

---

### 27483 - Sender/henter feil data for arbeidsgiver

**Risiko:** Bruker velger riktig arbeidsgiver, men systemet sender feil orgnummer pga. cache/state-mismatch.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Vise feilmelding hvis arbeidsgiver ikke legges til | 2.8-2.9 | ✅ Testet |

**Relaterte tester:** [2.8-2.9](akseptansetester.md#feilhåndtering-ros-27483)

---

### 27482 - Feil arbeidsgiver legges til på treffet

**Risiko:** Markedskontakt velger feil organisasjon (f.eks. Tentative AS i stedet for Tentativ AS).

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Forhåndsvisning av arbeidsgiverinfo | 2.1-2.3 | ✅ Testet (implisitt) |
| Jobbsøker varsles ved arbeidsgiver-endring | - | 📝 Utenfor pilot |
| Mulig å endre arbeidsgiver | 2.6 | ✅ Testet |
| Hente mer info fra Brønnøysund | - | 🔧 Teknisk implementasjon |

**Merknad:** Varsel til jobbsøker ved endring av arbeidsgiver er utenfor scope for pilot. Tester 7.7-7.10 dekker varsel ved andre endringer (tidspunkt, sted, etc.), men ikke arbeidsgiver-endringer.

**Relaterte tester:** [2.1-2.7](akseptansetester.md#2-legge-til-arbeidsgiver)

---

### 27433 - Arbeidsgiver uten konkret rekrutteringsbehov

**Risiko:** Arbeidsgiver deltar kun for markedsføring, uten reelt rekrutteringsbehov.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Manuell kontroll ved valg av arbeidsgivere | - | 📝 Rutine |
| Markedskontakt vurderer og dokumenterer behov | - | 📝 Rutine |
| Retningslinjer for vurdering av behov | - | 📝 Rutine |

**Merknad:** Dette er en operasjonell kontroll som ikke kan automatiseres. Bør dokumenteres i rutinebeskrivelse.

---

### 27390 - Arrangør kvalitetssikrer ikke KI-tekst

**Risiko:** Innholdet blir unøyaktig eller mindre relevant fordi arrangør ikke kvalitetssikrer teksten på treff-siden. Bruker kontrollerer ikke innholdet, eller velger å se bort fra KI-vurderingen.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Manuell innføring/gjennomgang før pilottest | - | 📝 Rutine |
| KI-sjekken ROB kvalitetssikrer treffet | 11.1-11.17 | ✅ Testet |
| Retningslinjer tilpasset Rekrutteringstreff | - | 📝 Dokumentert |
| Prosessbeskrivelse for KI-verktøy | - | 📝 Dokumentert |

**Relaterte tester:** [11.1-11.17](akseptansetester.md#11-ki-moderering)

---

### 27389 - ROB manipuleres til feilaktige vurderinger

**Risiko:** ROB manipuleres til å gi feilaktige eller utilsiktede vurderinger ved at brukere utnytter svakheter i systemets treningsdata, logikk eller prompt.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Robusthetstesting av KI | 11.31-11.35 | ✅ Testet |
| Logging av svar for administrasjonskontroll | 11.18-11.23 | ✅ Testet |
| Bruker kan overstyre KI-sjekken | 11.15-11.17 | ✅ Testet |
| Retningslinjer om at ROB er et verktøy | - | 📝 Dokumentert |

**Relaterte tester:** [11.31-11.35](akseptansetester.md#robusthetstesting-av-ki-ros-27546)

---

### 27388 - Feilregistrering ved deltakelsesvalg

**Risiko:** Det skjer feilregistrering ved deltakelsesvalg - jobbsøker svarer "ja", men det blir registrert som "nei".

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Verifisere hele løpet gjennom tester | 6.1-6.9 | ✅ Testet |

**Relaterte tester:** [6.1-6.9](akseptansetester.md#6-jobbsøker-svarer-på-invitasjon)

---

### 27386 - Aktivitetskort blir ikke opprettet

**Risiko:** Aktivitetskortet blir ikke opprettet, noe som fører til at jobbsøker ikke får invitasjon til treffet.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Automatiserte tester | - | 🔧 Teknisk implementasjon |
| Manuell testing før pilot | 5.5-5.7 | ✅ Testet |

**Relaterte tester:** [5.5-5.7](akseptansetester.md#5-invitere-jobbsøker)

---

### 27385 - Nav-ansatte mangler info om frivillighet

**Risiko:** Ansatte i andre deler av Nav mangler nødvendig informasjon om at Rekrutteringstreff ikke skal påvirke andre ytelser jobbsøker mottar.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Dele informasjon med fagpersoner | - | 📝 Rutine |
| Tydelig i løsningen at treff er frivillig | 5.14-5.15 | ✅ Testet |

**Relaterte tester:** [5.14-5.15](akseptansetester.md#frivillighetsinfo-i-varsel-ros-27485)

---

### 27383 - Jobbsøker får feil info i treffsiden

**Risiko:** Jobbsøker får feil informasjon på treffsiden (rekrutteringstreff-bruker) - f.eks. navn på treffet, dato, tidspunkt, sted og svarfrist.

> **Merk:** Treffsiden = rekrutteringstreff-bruker. SMS/e-post inneholder kun lenke til treffsiden og en oppsummering av endringer, men jobbsøker må åpne treffsiden for å se all oppdatert info.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Treffsiden viser oppdaterte detaljer etter endring | 7.11 | ✅ Testet |
| Mulig for markedskontakt å redigere info | 7.1-7.6 | ✅ Testet |
| Aktivitetskort oppdateres | 7.12-7.13 | ✅ Testet |
| Rutiner for å informere ved feil | - | 📝 Rutine |
| Manuell kontroll av info til jobbsøker | - | 📝 Rutine |
| Begrense deltakere i pilot | - | 📝 Rutine |

**Relaterte tester:** [7.11-7.13](akseptansetester.md#oppdatering-i-treffsiden-og-aktivitetskort-ros-27383)

---

### 27381 - Deltaker mottar samme varsel gjentatte ganger

**Risiko:** En deltaker mottar samme varsel gjentatte ganger som følge av teknisk eller menneskelig svikt (spam).

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Test for dobbel invitasjon | 5.12 | ✅ Testet |

**Relaterte tester:** [5.12](akseptansetester.md#feilsituasjoner)

---

### 27379 - Menneskelig feil - feil person får invitasjon

**Risiko:** En menneskelig feil fører til at innbyggere mottar en invitasjon som ikke er ment for dem. Nav-ansatt legger til feil person til treffet.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Manuell kontroll av treffene | - | 📝 Rutine |

**Merknad:** Menneskelig feil kan ikke forhindres teknisk, men begrenses ved rutiner og opplæring.

---

### 27378 - Teknisk feil - feil person får invitasjon

**Risiko:** En teknisk feil fører til at innbyggere mottar invitasjon som ikke er ment for dem, f.eks. feil i API, feilkoblinger av IDer.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Tester for å verifisere at systemet ikke feiler | 4.1-4.5 | ✅ Testet |
| Verifisere hele løpet gjennom tester | 5.1-5.7 | ✅ Testet |

**Relaterte tester:** [4.1-4.5](akseptansetester.md#4-legge-til-jobbsøker), [5.1-5.7](akseptansetester.md#5-invitere-jobbsøker)

---

### 27275 - Usynlige kandidater ikke skjult

**Risiko:** Vi skjuler ikke CV-en til deltakere som skal være usynlige kandidater. Personen var synlig, men synlighetsregler slår inn og personen skal være "usynlig".

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Synlighetsregler skjuler CV | 4.5-4.29 | ✅ Testet |

**Relaterte tester:** [4.5-4.29](akseptansetester.md#synlighet)

---

### 27273 - Jobbsøker får feil/mangelfull info pga feil på treff-siden

**Risiko:** Jobbsøker mottar feil eller mangelfull informasjon som følge av svakheter i løsningen. Kan føre til manglende oppmøte.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Manuell kontroll av treffene | - | 📝 Rutine |
| Melding ved oppdatering av treff | 7.7-7.10 | ✅ Testet |
| Mulighet for å endre treff i ettertid | 7.1-7.6 | ✅ Testet |
| Forhåndsvisning av treffet | - | 🔧 Teknisk (se treff-side) |
| Sikre triangulering av felter (design) | - | 🔧 Design |
| Tydelig at deltakelse er frivillig | 5.14-5.15 | ✅ Testet |

**Relaterte tester:** [7.1-7.18](akseptansetester.md#7-endre-publisert-treff), [5.14-5.15](akseptansetester.md#frivillighetsinfo-i-varsel-ros-27485)

---

### 27227 - Behandler flere opplysninger enn nødvendig

**Risiko:** Rekrutteringstreff behandler flere opplysninger enn nødvendig fordi jobbsøkere legges til via kandidatsøket.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Skiller mellom persontreffID og kandidatnummer | - | 🔧 Arkitekturbeslutning |
| Forenlighetsvurdering gjennomført i Støtte til Etterlevelse | - | ✅ Gjennomført |

**Merknad:** Dekket gjennom arkitekturbeslutning og juridisk vurdering.

---

### 27225 - Ansatte får ikke nødvendig tilgang

**Risiko:** Ansatte med tjenestlig behov får ikke tilgang til løsningen pga. feil ved tildeling.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Manuell kontroll av treff | - | 📝 Rutine |
| Tilgang til spesifikke kontorer | 15.26-15.32 | ✅ Testet |
| Tilgang til spesifikke personer (NavID) | - | 📝 Avklares |

**Relaterte tester:** [15.26-15.32](akseptansetester.md#pilotkontor-tilgang)

---

### 27223 - Adressefelt brukt til andre formål

**Risiko:** Arrangør legger inn feil adresse eller misvisende informasjon i adressefeltet.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Begrensning på antall tegn | 1.14 | ✅ Testet |
| Manuell kontroll av treff | - | 📝 Rutine |
| Adressesøk med forslag | 1.15-1.16 | ✅ Testet |

**Relaterte tester:** [1.14-1.16](akseptansetester.md#adressefelt-ros-27223)

---

### 27222 - Feil arbeidsgiver/virksomhet registreres

**Risiko:** Feil arbeidsgiver registreres pga. like navn, utdatert info i Brønnøysund, eller feil orgnummer.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Kan fjerne feil arbeidsgiver | 2.3, 2.6 | ✅ Testet |
| Bekreftelse før invitasjon til arbeidsgiver | - | ✅ N/A (ingen invitasjon i pilot) |
| Forhåndsvisning av arbeidsgiverinfo | 2.1-2.3 | ✅ Testet |

**Merknad:** Ligner på 27482. Dekket av samme tester.

**Relaterte tester:** [2.1-2.7](akseptansetester.md#2-legge-til-arbeidsgiver)

---

### 27220 - Tilgang til kontor utenfor pilot

**Risiko:** Et kontor som ikke skal være med i piloten får likevel tilgang.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Manuell kontroll av treff | - | 📝 Rutine |
| Tilgangsstyring i backend på bestemte kontorer | 15.26-15.32 | ✅ Testet |

**Relaterte tester:** [15.26-15.32](akseptansetester.md#pilotkontor-tilgang)

---

### 27219 - Særlige kategorier personopplysninger i tittel/beskrivelse

**Risiko:** Tittel eller beskrivelse inneholder personopplysninger av særlig kategori (GDPR art. 9) eller sensitiv informasjon om Nav-tiltak.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Manuell kontroll av pilotkontor | - | 📝 Rutine |
| Ikke lagre personopplysninger i KI-logger | - | 🔧 Implementert |
| Tall (4+ siffer) fjernes før Azure OpenAI | 11.36-11.42 | ✅ Testet (persondata-filter) |
| E-postadresser fjernes før Azure OpenAI | 11.43 | ✅ Testet (persondata-filter) |

**Merknad:** Systemet gir ikke feilmelding til bruker - tall og e-post fjernes automatisk før innsending. Verifiseres i KI-logg ved å sammenligne "originalTekst" og "sendtTekst".

**Relaterte tester:** [11.36-11.43](akseptansetester.md#persondata-filtrering-ros-27219)

---

### 27217 - Tilgang til treff man ikke skulle hatt

**Risiko:** Nav-ansatte får utilsiktet tilgang til treff i piloten.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Testscript for tilgang | 15.1-15.32 | ✅ Testet |
| Tilgangskontroll for hvem som kan finne/se/legge til deltakere | 15.1-15.21 | ✅ Testet |
| Tilgangskontroll for pilotkontor | 15.26-15.32 | ✅ Testet |

**Relaterte tester:** [15.1-15.32](akseptansetester.md#15-tilgangsstyring-og-roller)

---

### 27216 - KI identifiserer ikke diskriminerende tekst

**Risiko:** KI-verktøyet (ROB) identifiserer ikke diskriminerende tekst eller personopplysninger i tittel/beskrivelse.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Test diskriminering på alder (tittel) | 11.2 | ✅ Testet |
| Test diskriminering på kjønn (tittel) | 11.3 | ✅ Testet |
| Test diskriminering på helse (tittel) | 11.4 | ✅ Testet |
| Test diskriminering på etnisitet (tittel) | 11.5 | ✅ Testet |
| Test diskriminering på alder (innlegg) | 11.9 | ✅ Testet |
| Test diskriminering på kjønn (innlegg) | 11.10 | ✅ Testet |
| Test diskriminering på helse (innlegg) | 11.11 | ✅ Testet |
| Test diskriminering på etnisitet (innlegg) | 11.12 | ✅ Testet |
| Test subtil diskriminering | 11.32 | ✅ Testet |
| Test diskriminering på annet språk | 11.33 | ✅ Testet |
| Modellkontroll gjennom KI-logg | 11.18-11.23 | ✅ Testet |
| Retningslinjer for fritekstfelt | - | 📝 Dokumentert |
| Rutiner for bruk av KI-verktøy | - | 📝 Rutine |
| Feedback fra brukere | - | 📝 Brukertest |
| Toggle for å overskride KI-vurdering | 11.15-11.17 | ✅ Testet |

**Relaterte tester:** [11.2-11.14](akseptansetester.md#ki-validering-av-tittel-ros-27216), [11.32-11.33](akseptansetester.md#robusthetstesting-av-ki-ros-27546)

---

### 27215 - Brudd på informasjons- og tilgangskontroll

**Risiko:** Informasjon om personbrukere eller intern informasjon deles med uvedkommende pga. hacking, svindel eller illojale handlinger.

**Tiltak:**
| Tiltak | Test-ID | Status |
|--------|---------|--------|
| Tilgangskontroll for pilotkontor | 15.26-15.32 | ✅ Testet |
| Tilgangskontroll for hvem som kan finne/se/legge til deltakere | 15.1-15.21 | ✅ Testet |

**Relaterte tester:** [15.1-15.32](akseptansetester.md#15-tilgangsstyring-og-roller)

---

## Oppsummering av gap

### ✅ Nylig lagt til tester

Følgende gap er nå dekket med tester:

| ROS-ID | Risiko                                 | Nye tester |
| ------ | -------------------------------------- | ---------- |
| 27486  | Bekreftelsesdialog ved sletting        | 1.11-1.13  |
| 27485  | Frivillighetsinfo i varsel             | 5.14-5.15  |
| 27483  | Feilhåndtering ved arbeidsgiveroppslag | 2.8-2.9    |
| 27223  | Adressefeltvalidering og -søk          | 1.14-1.16  |
| 28065  | Endring og synkronisering              | 7.14-7.18  |

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

ROS-analysen nevner ikke eksplisitt rutiner for å deploye eller ta i bruk nye KI-modeller hos OpenAI. Imidlertid finnes det allerede dokumentasjon om dette i [KI-tekstvalideringstjenesten](../5-ki/ki-tekstvalideringstjeneste.md) under seksjonen "Prosess for evaluering av ny systemprompt eller modell".

**Anbefaling:** Det er ikke nødvendig å opprette en egen `utviklerrutiner`-mappe per nå, da:

1. KI-modellbytte er allerede dokumentert i `5-ki/ki-tekstvalideringstjeneste.md`
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
- [KI-tekstvalideringstjenesten](../5-ki/ki-tekstvalideringstjeneste.md) - KI-validering og logging (inkl. modellbytte-prosess)
- [Tilgangsstyring](../3-sikkerhet/tilgangsstyring.md) - Roller og tilgang
