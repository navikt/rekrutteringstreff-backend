# Automatiske backend-tester

Dette dokumentet gir oversikt over teststatus og definerer Trello-oppgaver for manglende tester.

> **Målgruppe:** Utviklere som skal implementere backend-tester for Rekrutteringstreff.

---

## Teststatus etter merge med main

Etter merge med `main` er mange tester nå implementert. Her er oppdatert status:

### ✅ Implementerte tester

| Område                                | Testfil(er)                                                   | Dekning                                                                                        |
| ------------------------------------- | ------------------------------------------------------------- | ---------------------------------------------------------------------------------------------- |
| **Jobbsøker svar ja/nei**             | `JobbsøkerInnloggetBorgerTest.kt`                             | ✅ `svar ja til invitasjon`, `svar nei til invitasjon`                                         |
| **Endre svar**                        | `JobbsøkerInnloggetBorgerTest.kt`                             | ✅ `kan endre svar fra ja til nei`, `kan endre svar fra nei til ja`                            |
| **Avlysning med hendelser**           | `RekrutteringstreffTest.kt`                                   | ✅ `avlys oppretter hendelse for rekrutteringstreff og alle jobbsøkere med aktivt svar ja`     |
| **Avlysning uten svar ja**            | `RekrutteringstreffTest.kt`                                   | ✅ `avlys oppretter kun rekrutteringstreff-hendelse når ingen jobbsøkere har aktivt svar ja`   |
| **Fullføring**                        | `RekrutteringstreffTest.kt`                                   | ✅ `fullfor oppretter hendelse...` (flere varianter)                                           |
| **Endringsvarsel til inviterte**      | `RekrutteringstreffTest.kt`                                   | ✅ `registrer endring oppretter hendelser for publisert treff med inviterte jobbsøkere`        |
| **Endringsvarsel til svart ja**       | `RekrutteringstreffTest.kt`                                   | ✅ `registrer endring oppretter hendelser for publisert treff med jobbsøkere som har svart ja` |
| **Endringsvarsel IKKE til svart nei** | `RekrutteringstreffTest.kt`                                   | ✅ `registrer endring varsler ikke jobbsøkere som har svart nei`                               |
| **Sletting av treff**                 | `RekrutteringstreffTest.kt`                                   | ✅ `slettRekrutteringstreffMedUpublisertedata`, `slett rekrutteringstreff feiler (409)...`     |
| **Svar-service logikk**               | `JobbsøkerServiceTest.kt`                                     | ✅ `svarJaTilInvitasjon...`, `svarNeiTilInvitasjon...`, `finnJobbsøkereMedAktivtSvarJa...`     |
| **Minside-varsel lytter**             | `MinsideVarselSvarLytterTest.kt`                              | ✅ Omfattende                                                                                  |
| **KI tekstvalidering**                | `KiTekstvalideringTest.kt`                                    | ✅ Mange testcases                                                                             |
| **Persondata-filtrering**             | `PersondataFilterTest.kt`                                     | ✅ Dekket                                                                                      |
| **Synlighet**                         | `SynlighetsKomponentTest.kt`, `SynlighetsLytterTest.kt` m.fl. | ✅ Omfattende                                                                                  |
| **Autorisasjon**                      | `*AutorisasjonsTest.kt` (flere filer)                         | ✅ Omfattende                                                                                  |
| **Pilotkontor**                       | `PilotkontorTest.kt`                                          | ✅ Dekket                                                                                      |
| **Duplikat-håndtering**               | `EierRepositoryTest.kt`, `AktivitetskortTest.kt`              | ✅ `leggTil legger ikke til duplikater`, duplikat-meldinger                                    |
| **Dobbel invitasjon (race condition)**| `InvitasjonFeilhåndteringTest.kt`                             | ⏳ Tester skrevet - venter på idempotens-implementasjon                                        |
| **Svarfrist-validering**              | `JobbsøkerInnloggetBorgerTest.kt`                             | ⏳ Tester skrevet - venter på svarfrist-validering i backend                                   |
| **Ugyldig treff-ID**                  | `JobbsøkerInnloggetBorgerTest.kt`                             | ✅ GET/POST til ukjent treff-ID gir feilkode                                                   |
| **Dobbelt svar (idempotens)**         | `JobbsøkerInnloggetBorgerTest.kt`                             | ✅ To svar-ja kall håndteres konsistent, samtidige kall                                        |

---

## Testinfrastruktur

### Eksisterende oppsett

| Komponent              | Rammeverk                | Database            | Eksempel                   |
| ---------------------- | ------------------------ | ------------------- | -------------------------- |
| rekrutteringstreff-api | JUnit 5 + Testcontainers | PostgreSQL (Docker) | `TestDatabase.kt`          |
| toi-synlighetsmotor    | JUnit 5                  | H2 in-memory        | `SynlighetsmotorTest.kt`   |
| kandidatvarsel-api     | JUnit 5 + TestRapid      | PostgreSQL (Docker) | `RapidsIntegrasjonTest.kt` |

### Nøkkelkomponenter

- **Testcontainers PostgreSQL** - Docker-basert database som starter automatisk
- **MockOAuth2Server** - Mocking av autentisering og tokens
- **WireMock** - Mocking av eksterne HTTP-tjenester
- **TestRapid** - Testing av Kafka/Rapids-meldinger

---

## 📋 Trello-oppgaver

Kopier disse kortene til Trello. Hver oppgave er selvstendig og kan utføres av hvem som helst.

---

### 🔴 PRIORITET 1: Sikkerhetskritiske (ROS-tiltak)

#### TRELLO-1: KI bypass-sikkerhet tester

**Tittel:** Implementer KiBypassSikkerhetTest.kt

**Beskrivelse:**
Opprett ny testfil i `rekrutteringstreff-api/.../ki/`-mappen som verifiserer at KI-valideringen ikke kan omgås.

**Tester å implementere:**

- [ ] **11.8.1** - Autolagring med diskriminerende tekst venter på KI-validering
- [ ] **11.8.2** - API-kall uten KI-validering gir feilkode (400/422)
- [ ] **11.8.3** - Diskriminerende tekst uten "Lagre likevel"-flagg avvises
- [ ] **11.8.4** - Backend krever valideringsresultat før lagring tillates
- [ ] **11.8.5** - Race condition ved rask redigering håndteres korrekt

**ROS-referanse:** ROS 27547, 27321, 27867

**Kobling:** Kan kobles til eksisterende Trello-oppgave for KI bypass-sikkerhet.

**Labels:** `backend`, `sikkerhet`, `ros-tiltak`, `prioritet-1`

---

#### ⏳ TRELLO-2: Dobbel invitasjon-beskyttelse (TESTER SKREVET - VENTER PÅ FUNKSJONALITET)

**Tittel:** Test for dobbel invitasjon (race condition)

**Beskrivelse:**
Legg til tester som verifiserer at systemet håndterer samtidige invitasjoner korrekt.

**Implementert i:** `InvitasjonFeilhåndteringTest.kt`

**Tester skrevet (men @Disabled inntil funksjonalitet er implementert):**

- [ ] **5.4.1** - To samtidige invitasjoner registrerer kun én invitasjon (idempotent)
- [ ] Re-invitasjon av allerede invitert jobbsøker håndteres idempotent

**Tester som passerer nå:**

- [x] **5.4.2** - Invitasjon av jobbsøker som nettopp ble ikke-synlig håndteres korrekt

**Plassering:** `InvitasjonFeilhåndteringTest.kt`

**Labels:** `backend`, `sikkerhet`, `concurrency`, `prioritet-1`

---

### 🟡 PRIORITET 2: Validering og edge cases

#### ⏳ TRELLO-3: Svarfrist-validering (TESTER SKREVET - VENTER PÅ FUNKSJONALITET)

**Tittel:** Test at svar etter svarfrist avvises

**Beskrivelse:**
Verifiser at jobbsøkere ikke kan svare på invitasjoner etter at svarfristen har utløpt.

**Implementert i:** `JobbsøkerInnloggetBorgerTest.kt`

**Tester skrevet (men @Disabled inntil funksjonalitet er implementert):**

- [ ] **6.2.2** - Svar ja etter svarfrist avvises
- [ ] Svar nei etter svarfrist avvises

**Tester som passerer nå:**

- [x] Svar ja før svarfrist tillates (positiv test)

**Plassering:** `JobbsøkerInnloggetBorgerTest.kt`

**Labels:** `backend`, `validering`, `prioritet-2`

---

#### ✅ TRELLO-4: Ugyldig treff-ID håndtering (IMPLEMENTERT)

**Tittel:** Test 404 for ugyldig treff-ID

**Beskrivelse:**
Verifiser at API returnerer 404 for ikke-eksisterende treff-IDer.

**Implementert i:** `JobbsøkerInnloggetBorgerTest.kt`

**Tester implementert:**

- [x] **6.3.1** - GET til ukjent treff-ID gir 404
- [x] POST svar-ja til ukjent treff-ID gir feilkode
- [x] POST svar-nei til ukjent treff-ID gir feilkode

**Labels:** `backend`, `feilhåndtering`, `prioritet-2`

---

#### ✅ TRELLO-5: Dobbelt svar-håndtering (IMPLEMENTERT)

**Tittel:** Test at dobbelt svar kun registreres én gang

**Beskrivelse:**
Verifiser at systemet er idempotent ved gjentatte svar fra samme jobbsøker.

**Implementert i:** `JobbsøkerInnloggetBorgerTest.kt`

**Tester implementert:**

- [x] **6.3.2** - To raske "Svar ja"-kall registrerer kun én hendelse
- [x] Samtidige svar-ja kall håndteres konsistent

**Plassering:** `JobbsøkerInnloggetBorgerTest.kt`

**Labels:** `backend`, `idempotens`, `prioritet-2`

---

## Oppsummering

| Prioritet              | Oppgaver              | Status                          | Estimat   |
| ---------------------- | --------------------- | ------------------------------- | --------- |
| 🔴 Kritisk (sikkerhet) | TRELLO-1              | ⏳ Gjenstår                      | 1 dag     |
| 🔴 Kritisk (sikkerhet) | TRELLO-2              | ⏳ Tester skrevet, venter impl. | -         |
| 🟡 Medium (validering) | TRELLO-3              | ⏳ Tester skrevet, venter impl. | -         |
| 🟡 Medium (validering) | TRELLO-4, TRELLO-5    | ✅ Implementert og passerer     | -         |

---

## Utenfor scope for rekrutteringstreff-backend

Følgende fra akseptansetestene dekkes **ikke** av backend-tester her:

| AT-ref      | Område                  | Grunn                                                                                                                                                                                             |
| ----------- | ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 2.4.1-2.4.2 | Arbeidsgiver validering | Arbeidsgiversøk går via **pam-search** (ekstern tjeneste). Frontend kaller pam-search direkte, ikke via rekrutteringstreff-api. Se [enhetsregisteret.md](../4-integrasjoner/enhetsregisteret.md). |

---

## Relaterte dokumenter

- [akseptansetester.md](akseptansetester.md) - Manuelle akseptansetester
- ROS-tiltak for Rekrutteringstreff - se _Tryggnok: ROS Rekrutteringstreff_
- ROS-tiltak for KI-sjekken - se _Tryggnok: ROS Rekrutteringstreff-KI_
- [../8-utviklerrutiner/ki-rutiner.md](../8-utviklerrutiner/ki-rutiner.md) - KI-rutiner for utviklere
