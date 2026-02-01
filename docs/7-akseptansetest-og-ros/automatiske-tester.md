# Plan for automatiske backend-tester

Dette dokumentet beskriver hvilke automatiske tester som bør implementeres basert på akseptansetestene i [akseptansetester.md](akseptansetester.md).

> **Målgruppe:** Utviklere som skal implementere backend-tester for Rekrutteringstreff.

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

### Testmønster

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@WireMockTest
class EksempelTest {
    private val authServer = MockOAuth2Server()
    private val db = TestDatabase()

    @BeforeAll
    fun setUp() { authServer.start(); app.start() }

    @AfterEach
    fun reset() { db.slettAlt() }
}
```

---

## Prioritert testplan

### 🔴 Prioritet 1: Sikkerhetskritiske tester

Disse testene må implementeres først da de dekker sikkerhetskrav fra ROS-analysen.

#### 11.8 KI bypass-sikkerhet (ROS 27547, 27321, 27867)

| AT-ref | Testcase                                                                                 | Testfil                    | Status     |
| ------ | ---------------------------------------------------------------------------------------- | -------------------------- | ---------- |
| 11.8.1 | Autolagring med diskriminerende tekst - verifiser at autolagring venter på KI-validering | `KiBypassSikkerhetTest.kt` | ❌ Mangler |
| 11.8.2 | API-kall uten validering - send lagre-request uten KI-sjekk, forvent 400/422             | `KiBypassSikkerhetTest.kt` | ❌ Mangler |
| 11.8.3 | API-kall med diskriminerende tekst uten "Lagre likevel" - forvent avvisning              | `KiBypassSikkerhetTest.kt` | ❌ Mangler |
| 11.8.4 | Verifiser at backend krever valideringsresultat                                          | `KiBypassSikkerhetTest.kt` | ❌ Mangler |
| 11.8.5 | Race condition ved rask redigering                                                       | `KiBypassSikkerhetTest.kt` | ❌ Mangler |

**Implementasjonsnotat:**

```kotlin
// Eksempel på test for 11.8.2
@Test
fun `lagre tittel uten KI-validering skal gi 400`() {
    val treffId = db.opprettRekrutteringstreffIDatabase()

    val response = fuel.patch("/api/rekrutteringstreff/$treffId")
        .header("Authorization", authServer.lagToken())
        .body("""{"tittel": "Kun for unge under 30 år"}""")
        .response()

    assertThat(response.statusCode).isEqualTo(400)
}
```

#### 5.4 Dobbel invitasjon

| AT-ref | Testcase                                                      | Testfil                           | Status     |
| ------ | ------------------------------------------------------------- | --------------------------------- | ---------- |
| 5.4.1  | Trykk inviter to ganger raskt - kun én invitasjon registreres | `InvitasjonFeilhåndteringTest.kt` | ❌ Mangler |
| 5.4.2  | Inviter jobbsøker som blir ikke-synlig                        | `InvitasjonFeilhåndteringTest.kt` | ❌ Mangler |

---

### 🟠 Prioritet 2: Kjerneforretningslogikk

#### 3.1 Publisering av treff

| AT-ref | Testcase                                       | Testfil                  | Status               |
| ------ | ---------------------------------------------- | ------------------------ | -------------------- |
| 3.1.1  | Publiser treff - status endres til "Publisert" | `TreffLivssyklusTest.kt` | ❌ Mangler           |
| 3.1.2  | Søk etter publisert treff - treffet dukker opp | `TreffLivssyklusTest.kt` | ✅ Delvis (hentAlle) |
| 3.1.3  | Åpne publisert treff - kan se detaljer         | `TreffLivssyklusTest.kt` | ✅ Eksisterer        |

#### 6.1 Jobbsøker svarer

| AT-ref | Testcase                       | Testfil                | Status     |
| ------ | ------------------------------ | ---------------------- | ---------- |
| 6.1.1  | Svar "Ja" - status oppdateres  | `JobbsøkerSvarTest.kt` | ❌ Mangler |
| 6.1.5  | Svar "Nei" - status oppdateres | `JobbsøkerSvarTest.kt` | ❌ Mangler |
| 6.1.8  | Endre svar fra ja til nei      | `JobbsøkerSvarTest.kt` | ❌ Mangler |
| 6.1.9  | Endre svar fra nei til ja      | `JobbsøkerSvarTest.kt` | ❌ Mangler |

#### 8.1 Avlyse treff

| AT-ref | Testcase                                 | Testfil            | Status     |
| ------ | ---------------------------------------- | ------------------ | ---------- |
| 8.1.1  | Avlys treff - status endres til "Avlyst" | `AvlysningTest.kt` | ❌ Mangler |
| 8.1.2  | Svart ja får avlysningsvarsel            | `AvlysningTest.kt` | ❌ Mangler |
| 8.1.4  | Invitert (ikke svart) får IKKE varsel    | `AvlysningTest.kt` | ❌ Mangler |
| 8.1.6  | Svart nei får IKKE varsel                | `AvlysningTest.kt` | ❌ Mangler |

#### 7.2 Varselmottakere ved endring

| AT-ref | Testcase                                    | Testfil                | Status     |
| ------ | ------------------------------------------- | ---------------------- | ---------- |
| 7.2.1  | Invitert (ikke svart) mottar endringsvarsel | `EndringVarselTest.kt` | ❌ Mangler |
| 7.2.2  | Svart ja mottar endringsvarsel              | `EndringVarselTest.kt` | ❌ Mangler |
| 7.2.3  | Svart nei skal IKKE motta varsel            | `EndringVarselTest.kt` | ❌ Mangler |

---

### 🟡 Prioritet 3: Validering og feilhåndtering

#### 1.1 Opprettelse med validering

| AT-ref | Testcase                        | Testfil                                  | Status        |
| ------ | ------------------------------- | ---------------------------------------- | ------------- |
| 1.1.1  | Opprett med påkrevde felter     | `RekrutteringstreffTest.kt`              | ✅ Eksisterer |
| 1.1.2  | Opprett med alle felter         | `RekrutteringstreffTest.kt`              | ✅ Delvis     |
| 1.1.3  | Ugyldig data - valideringsfeil  | `RekrutteringstreffValideringTest.kt`    | ❌ Mangler    |
| 1.1.4  | Andre ser ikke upublisert treff | `RekrutteringstreffAutorisasjonsTest.kt` | ❌ Mangler    |

#### 1.3 Sletting av kladd

| AT-ref | Testcase                           | Testfil                             | Status     |
| ------ | ---------------------------------- | ----------------------------------- | ---------- |
| 1.3.1  | Slett kladd-treff                  | `RekrutteringstreffSlettingTest.kt` | ❌ Mangler |
| 1.3.2  | Bekreft sletting - treffet fjernes | `RekrutteringstreffSlettingTest.kt` | ❌ Mangler |

#### 2.4 Feilhåndtering arbeidsgiver

| AT-ref | Testcase                             | Testfil                         | Status     |
| ------ | ------------------------------------ | ------------------------------- | ---------- |
| 2.4.1  | Ugyldig orgnummer - feilmelding      | `ArbeidsgiverValideringTest.kt` | ❌ Mangler |
| 2.4.2  | Nettverksfeil ved oppslag (WireMock) | `ArbeidsgiverValideringTest.kt` | ❌ Mangler |

#### 6.2-6.3 Tilstander og feil

| AT-ref | Testcase                                     | Testfil                | Status     |
| ------ | -------------------------------------------- | ---------------------- | ---------- |
| 6.2.2  | Åpne etter svarfrist utløpt - kan ikke svare | `JobbsøkerSvarTest.kt` | ❌ Mangler |
| 6.3.1  | Ugyldig treff-ID - 404                       | `JobbsøkerSvarTest.kt` | ❌ Mangler |
| 6.3.2  | Trykk svar to ganger - kun ett svar          | `JobbsøkerSvarTest.kt` | ❌ Mangler |

---

### 🟢 Prioritet 4: Allerede dekket

Disse testene eksisterer allerede med god dekning:

| Område                        | Testfil(er)                              | Dekning          |
| ----------------------------- | ---------------------------------------- | ---------------- |
| 4.2-4.8 Synlighetsregler      | `SynlighetsmotorTest.kt`                 | ✅ 20+ testcases |
| 15.1-15.3 Roller/autorisasjon | `*AutorisasjonsTest.kt`                  | ✅ Omfattende    |
| 15.5-15.6 Pilotkontor         | `PilotkontorTest.kt`                     | ✅ Dekket        |
| 11.1-11.2 KI diskriminering   | `KiTekstvalideringParameterisertTest.kt` | ✅ 40+ prompts   |
| 11.9 Persondata-filtrering    | `PersondataFilterTest.kt`                | ✅ Dekket        |
| 11.4 KI-logg                  | `KiLoggRepositoryTest.kt`                | ✅ Dekket        |

---

## Nye testfiler å opprette

```
rekrutteringstreff-api/src/test/kotlin/no/nav/toi/
├── ki/
│   └── KiBypassSikkerhetTest.kt          # Prioritet 1: 11.8.x
├── jobbsoker/
│   ├── JobbsøkerSvarTest.kt              # Prioritet 2: 6.1.x, 6.2.x, 6.3.x
│   └── InvitasjonFeilhåndteringTest.kt   # Prioritet 1: 5.4.x
├── rekrutteringstreff/
│   ├── TreffLivssyklusTest.kt            # Prioritet 2: 3.1.x
│   ├── AvlysningTest.kt                  # Prioritet 2: 8.1.x
│   ├── RekrutteringstreffValideringTest.kt   # Prioritet 3: 1.1.3
│   └── RekrutteringstreffSlettingTest.kt     # Prioritet 3: 1.3.x
├── arbeidsgiver/
│   └── ArbeidsgiverValideringTest.kt     # Prioritet 3: 2.4.x
└── varsel/
    └── EndringVarselTest.kt              # Prioritet 2: 7.2.x
```

---

## Utvidelser til TestDatabase

For å forenkle testoppsett, legg til disse hjelpemetodene i `TestDatabase.kt`:

```kotlin
fun opprettPublisertTreff(
    eier: String = "A000001",
    tittel: String = "Test-treff"
): UUID {
    val treffId = opprettRekrutteringstreffIDatabase(eier, tittel)
    publiserTreff(treffId)
    return treffId
}

fun opprettTreffMedInviterteJobbsøkere(
    antall: Int = 3
): Pair<UUID, List<UUID>> {
    val treffId = opprettPublisertTreff()
    val personTreffIds = leggTilJobbsøkereMedHendelse(treffId, antall)
    inviterJobbsøkere(personTreffIds)
    return treffId to personTreffIds
}

fun settSvarfrist(treffId: UUID, svarfrist: LocalDateTime) {
    dataSource.connection.use { conn ->
        conn.prepareStatement(
            "UPDATE rekrutteringstreff SET svarfrist = ? WHERE id = ?"
        ).use { stmt ->
            stmt.setObject(1, svarfrist)
            stmt.setObject(2, treffId)
            stmt.executeUpdate()
        }
    }
}

fun simulerJobbsøkerSvar(personTreffId: UUID, svar: Svar) {
    // Oppdater status basert på svar
}
```

---

## Integrasjonstester mot Rapids

For varsler og aktivitetskort-synkronisering, bruk `TestRapid`:

```kotlin
class VarselRapidsTest {
    private val rapid = TestRapid()

    @Test
    fun `avlysning sender varsel kun til svart ja`() {
        // Setup: Treff med 3 jobbsøkere (invitert, svart ja, svart nei)
        val treffId = setupTreffMedJobbsøkere()

        // Action: Avlys treffet
        avlysTreff(treffId)

        // Assert: Kun én melding sendt (til svart ja)
        val meldinger = rapid.inspektør.size
        assertThat(meldinger).isEqualTo(1)

        val melding = rapid.inspektør.message(0)
        assertThat(melding["@event_name"].asText()).isEqualTo("varsel.sendt")
    }
}
```

---

## Oppsummering

| Prioritet                  | Antall tester | Status               |
| -------------------------- | ------------- | -------------------- |
| 🔴 Kritisk (sikkerhet)     | 7             | ❌ 0/7 implementert  |
| 🟠 Høy (forretningslogikk) | 13            | ❌ 1/13 implementert |
| 🟡 Medium (validering)     | 10            | ❌ 2/10 implementert |
| 🟢 Lav (allerede dekket)   | ~80           | ✅ Eksisterer        |

**Neste steg:**

1. Implementer `KiBypassSikkerhetTest.kt` (kritisk for ROS-tiltak)
2. Utvid `TestDatabase.kt` med nye fixtures
3. Implementer livssyklus-tester (publisering, avlysning)
4. Legg til jobbsøker svar-flyt tester

---

## Relaterte dokumenter

- [akseptansetester.md](akseptansetester.md) - Manuelle akseptansetester
- [ros-pilot.md](ros-pilot.md) - ROS-tiltak og testdekning
- [ros-ki-pilot.md](ros-ki-pilot.md) - ROS-tiltak for KI-sjekken
- [../8-utviklerrutiner/ki-rutiner.md](../8-utviklerrutiner/ki-rutiner.md) - KI-rutiner for utviklere
