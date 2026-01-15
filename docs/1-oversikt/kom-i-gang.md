# Kom i gang

Denne guiden hjelper deg med å sette opp utviklingsmiljøet for rekrutteringstreff-backend.

## Forutsetninger

| Verktøy                     | Minimum versjon   | Formål                              |
| --------------------------- | ----------------- | ----------------------------------- |
| **JDK**                     | 21                | Kompilering og kjøring              |
| **Docker** eller **Colima** | -                 | Kjøre PostgreSQL via Testcontainers |
| **Gradle**                  | 8.x (via wrapper) | Bygging og testing                  |
| **IntelliJ IDEA**           | -                 | Utviklingsmiljø                     |

> **macOS:** Vi bruker [Colima](https://github.com/abiosoft/colima) som Docker-alternativ.

---

## Klone og bygge

```bash
# Klone repoet
git clone https://github.com/navikt/rekrutteringstreff-backend.git
cd rekrutteringstreff-backend

# Bygg hele prosjektet
./gradlew build

# Kjør alle tester
./gradlew test
```

---

## Utvikling

Vi kjører **ikke** applikasjonen lokalt. I stedet bruker vi:

1. **JUnit-tester lokalt** - For å verifisere kode under utvikling
2. **Dev-miljøet** - For integrasjonstesting og manuell testing

### Kjøre tester

Testene bruker Testcontainers og spinner opp en PostgreSQL-database automatisk:

```bash
# Kjør alle tester
./gradlew test

# Kjør kun komponenttester
./gradlew test --tests "*Komponenttest*"

# Kjør tester for en spesifikk app
./gradlew :apps:rekrutteringstreff-api:test
```

---

## API-dokumentasjon (Swagger)

Swagger UI er tilgjengelig i dev-miljøet:

**URL:** https://rekrutteringstreff-api.intern.dev.nav.no/swagger

### Autentisering

1. Åpne Swagger UI i nettleseren
2. Klikk på **Authorize**-knappen
3. Velg å generere et test-token via webløsningen
4. Gjør API-kall direkte i Swagger

---

## Prosjektstruktur

```
rekrutteringstreff-backend/
├── apps/
│   ├── rekrutteringstreff-api/              # Hoved-API for veiledere
│   ├── rekrutteringstreff-minside-api/      # API for jobbsøkere via MinSide
│   └── rekrutteringsbistand-aktivitetskort/ # Kafka-lytter for aktivitetskort
├── technical-libs/                           # Delte biblioteker
├── docs/                                     # Dokumentasjon (du er her!)
└── buildSrc/                                 # Gradle-konfigurasjon
```

---

## Vanlige oppgaver

### Legge til en ny Flyway-migrasjon

1. Opprett fil i `apps/rekrutteringstreff-api/src/main/resources/db/migration/`
2. Navngi filen `V<neste_nummer>__beskrivelse.sql`
3. Skriv SQL-migrasjon
4. Oppdater [database.md](../2-arkitektur/database.md) med beskrivelse

### Legge til et nytt API-endepunkt

1. Opprett eller utvid Controller i relevant modul
2. Legg til forretningslogikk i Service
3. Skriv komponenttest
4. Oppdater Swagger-dokumentasjon ved behov

### Publisere hendelser til Rapids & Rivers

Se [rapids-and-rivers.md](../4-integrasjoner/rapids-and-rivers.md) for mønster og eksempler.

---

## Feilsøking

### Tester feiler med "Could not connect to database"

Docker/Colima må kjøre for at Testcontainers skal fungere:

```bash
docker info    # Sjekk at Docker kjører
colima status  # Eller sjekk Colima-status på Mac
```

### IntelliJ finner ikke klasser

1. File → Invalidate Caches → Invalidate and Restart
2. Reimporter Gradle-prosjektet

---

## Neste steg

- Les [Oversikt](oversikt.md) for å forstå domenet
- Se [Arkitekturprinsipper](../2-arkitektur/prinsipper.md) for tekniske valg
- Utforsk [Tilgangsstyring](../3-sikkerhet/tilgangsstyring.md) for å forstå roller
