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

## Utviklingsmiljø

Vi anbefaler å bruke **IntelliJ IDEA** som utviklingsmiljø. Det gir utmerket støtte for Kotlin, Gradle og integrasjon med testene.

---

## Utvikling

Vi kjører **ikke** applikasjonen lokalt. I stedet bruker vi:

1. **JUnit-tester lokalt** - For å verifisere kode under utvikling
2. **Dev-miljøet** - For integrasjonstesting og manuell testing

### Kjøre tester

Testene bruker Testcontainers og spinner opp en PostgreSQL-database automatisk. Sørg for at Docker eller Colima kjører før du kjører testene.

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
