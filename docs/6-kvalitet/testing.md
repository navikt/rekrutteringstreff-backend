# Testing

Teststrategien er lagdelt for å sikre rask feedback og god dekning.

## Testnivåer

### Komponenttester

Tester hele applikasjonen med ekte HTTP-kall mot en embedded database (Testcontainers).

**Karakteristikker:**

- Starter opp applikasjonen i test-modus
- Bruker Testcontainers for PostgreSQL
- Gjør ekte HTTP-kall mot API-et ved hjelp av `httpClient`
- Verifiserer hele flyten fra request til respons

**Eksempel:**

```kotlin
class TreffKomponenttest {
    @Test
    fun `kan opprette og hente treff`() {
        val response = httpClient.post("/api/rekrutteringstreff", treffData)
        assertEquals(201, response.statusCode)

        val hentet = httpClient.get("/api/rekrutteringstreff/${response.id}")
        assertEquals(200, hentet.statusCode)
    }
}
```

### Service-tester

Tester forretningslogikk isolert med mockede avhengigheter.

**Karakteristikker:**

- Mocker repositories og eksterne tjenester
- Fokuserer på forretningsregler
- Rask kjøring

### Repository-tester

Tester databaseoperasjoner mot en ekte database.

**Karakteristikker:**

- Bruker Testcontainers for PostgreSQL
- Verifiserer SQL-spørringer
- Tester komplekse database-operasjoner

### Enhetstester

Tester individuelle funksjoner og klasser.

**Karakteristikker:**

- Ingen eksterne avhengigheter
- Ekstremt rask kjøring
- Fokuserer på edge cases og logikk

## Testcontainers

Vi bruker Testcontainers for å kjøre PostgreSQL i tester:

```kotlin
@Container
val postgres = PostgreSQLContainer<Nothing>("postgres:15")
```

**Fordeler:**

- Identisk med produksjonsdatabase
- Isolert per test-kjøring
- Automatisk opprydding

## Kjøring

```bash
# Alle tester
./gradlew test

# Kun komponenttester
./gradlew test --tests "*Komponenttest*"

# Kun enhetstester
./gradlew test --tests "*Test*" --exclude-task "*Komponenttest*"
```

## Relaterte dokumenter

- [Database](../2-arkitektur/database.md) - Database-skjema som testes
- [Prinsipper](../2-arkitektur/prinsipper.md) - Arkitekturprinsipper som påvirker testbarhet
