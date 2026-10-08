# Transaksjoner og låsing

Skrivinger i rekrutteringstreff-api kjører under `READ COMMITTED` og låser radene de bestemmer ut fra, før de leser dem. Dokumentet beskriver reglene, låsefunksjonene og forsøkene mot PostgreSQL som reglene bygger på. Les det før du lager eller endrer en skriving.

Reglene gjelder rekrutteringstreff-api. Appen rekrutteringsbistand-aktivitetskort har egen database og står utenfor. [Database](database.md) beskriver skjemaet, og [Jobbsøkerstatus](../9-planer/jobbsoker-statuser.md) beskriver statusreglene som låsene beskytter.

## Kort fortalt

- Tilkoblingspoolen kjører `READ COMMITTED`, også i testene. En transaksjon som har ventet på en lås, leser det som ble lagret mens den ventet, i stedet for å feile med `40001`.
- Skrivinger som bestemmer ut fra data i databasen, låser før de leser dataene, eller har hele regelen i én `UPDATE`.
- Alle radlåser står i `låsing.kt`, og bare der bruker vi `FOR NO KEY UPDATE`. En test stopper radlåser i andre filer.
- Vi låser treffet først, så jobbsøkerne sortert på `id`, så andre rader.
- Vi gjør ingen HTTP-kall og sender ingenting til Kafka mens vi holder en lås.
- En samtidighetstest viser at operasjonen venter på låsen og ser endringen etterpå.

## Transaksjonsfunksjonene

Servicen starter transaksjonen (se [Prinsipper](prinsipper.md)). Funksjonene står i `transactionManager.kt` og `låsing.kt`.

| Funksjon | Bruk |
| --- | --- |
| `medLåstTreff(treffId) { connection -> ... }` | Skrivinger som låser treffet. Starter transaksjonen og låser treffraden før blokken kjører. |
| `executeInTransaction { connection -> ... }` | Andre skrivinger. Trenger skrivingen lås, kaller den låsefunksjonen på første linje i blokken. |
| `executeInReadOnlyTransaction { connection -> ... }` | Lesing der flere spørringer må se samme øyeblikksbilde, for eksempel totalt antall og én side. Kjører `REPEATABLE READ` og `readOnly`. |

En enkelt spørring trenger ingen transaksjon. Det gjelder også en `UPDATE` som har hele regelen i `WHERE`, som når vi oppdaterer synlighet fra Kafka.

## Regler

### 1. Lås først, valider etterpå

Ta låsen før du leser verdiene du bestemmer ut fra, som treffstatus, jobbsøkerstatus, synlighet og om raden finnes fra før. En sjekk før låsen kan være utdatert når du får låsen.

Før et eksternt kall kan det lønne seg å sjekke tidlig, så vi slipper kallet når svaret uansett er nei. Da gjentar vi sjekken under låsen.

Tilgangssjekken i controlleren er et unntak. Den avgjør hvem som får gjøre forespørselen, ikke hva som skal skrives.

### 2. Den som starter transaksjonen, låser

Funksjonen som starter transaksjonen, tar låsene først. Den kan slå opp id-en den skal låse, men leser alt annet etter låsen.

- Funksjoner som får en `Connection`, låser ikke selv. De forutsetter at kalleren har låst. Krever funksjonen en bestemt lås, skriver vi det i KDoc-en.
- Lesefunksjoner låser aldri.
- Alle låsefunksjonene står i `låsing.kt`. Bare der står `FOR NO KEY UPDATE`.

Da ser du alle låsene øverst i transaksjonen, og vi låser aldri samme rad to ganger. `LåsingTest` leser kildekoden og stopper radlåser i andre filer.

| Låsefunksjon | Låser |
| --- | --- |
| `medLåstTreff(treffId)` og `låsTreff(treffId)` | Treffraden. Kaster `RekrutteringstreffIkkeFunnetException` (404) hvis treffet ikke finnes. |
| `låsJobbsøkere(treffId, personTreffIder)` | De oppgitte jobbsøkerne på treffet, sortert på `id`, i én spørring. Slettede og ikke-synlige jobbsøkere blir også låst, så kalleren kan sjekke status og synlighet selv. Kaster `JobbsøkerIkkeFunnetException` (404) hvis en av dem ikke hører til treffet. |
| `låsAlleJobbsøkerePåTreff(treffId)` | Alle jobbsøkerne på treffet, sortert på `id`. Ta trefflåsen først. |

En skriving som låser treffet:

```kotlin
fun publiser(treffId: TreffId, navIdent: String) {
    dataSource.medLåstTreff(treffId) { connection ->
        val treff = hentTreff(connection, treffId)
        if (treff.status != RekrutteringstreffStatus.UTKAST) {
            throw UlovligOppdateringException("Kan kun publisere rekrutteringstreff som er i UTKAST status")
        }
        rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.PUBLISERT, navIdent)
        rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.PUBLISERT)
    }
}
```

En skriving som bare låser jobbsøkere:

```kotlin
fun svarPåVegneAvJobbsøker(personTreffId: PersonTreffId, treffId: TreffId, navIdent: String, svar: Boolean?) {
    dataSource.executeInTransaction { connection ->
        connection.låsJobbsøkere(treffId, listOf(personTreffId))
        // Leser synlighet, status og gjeldende svar, og skriver hendelsen
    }
}
```

#### Hvorfor `FOR NO KEY UPDATE`

`FOR UPDATE` stopper også FK-sjekker. Låser vi treffet med `FOR UPDATE`, må alle innsettinger med FK til treffet vente til vi er ferdige (forsøk 4). Det gjelder blant annet hendelser, innlegg og KI-logg. Endrer vi også raden, feiler de etterpå med `40001` under `REPEATABLE READ` (forsøk 1).

`FOR NO KEY UPDATE` stopper de samme låsene og endringene som `FOR UPDATE`, men slipper FK-sjekkene forbi (forsøk 3). Forskjellen betyr bare noe hvis vi sletter raden eller endrer nøkkelkolonner. Vi sletter aldri treff-, jobbsøker- eller arbeidsgiverrader. Vi setter status `SLETTET`. Nøkkelkolonnene endres heller ikke.

### 3. Alle skrivinger kjører `READ COMMITTED`

Under `REPEATABLE READ` ser transaksjonen ett øyeblikksbilde fra første spørring. Må den vente på en rad som en annen transaksjon endrer, avbryter PostgreSQL med `40001 could not serialize access due to concurrent update` når den andre har committet. Det gjelder:

- eksplisitte låser (`FOR UPDATE`, `FOR NO KEY UPDATE`)
- `UPDATE` og `DELETE`, også i autocommit, fordi poolens standardnivå gjelder hver enkelt spørring
- `INSERT ... ON CONFLICT DO UPDATE`
- FK-sjekken ved `INSERT` når foreldreraden er låst med `FOR UPDATE` og endret

Under `READ COMMITTED` venter spørringen, leser den nye versjonen av raden og fortsetter. Neste spørring ser det den andre transaksjonen lagret. Det er dette låsene bygger på.

Konstanten `READ_COMMITTED` i `transactionManager.kt` setter nivået både i `InfrastructureContext` og i `TestDatabase`. Lesetransaksjoner som trenger ett øyeblikksbilde over flere spørringer, bruker `executeInReadOnlyTransaction`. En lesetransaksjon under `REPEATABLE READ` får aldri `40001`.

Alternativet er `REPEATABLE READ` og ny kjøring ved `40001`. Da må alle skrivinger ligge i en løkke, og blokken må tåle å kjøres flere ganger. Med `READ COMMITTED` og lås venter transaksjonen i stedet og fortsetter med ferske data.

### 4. Fast låserekkefølge

1. Treff
2. Jobbsøkere, sortert på `id`
3. Andre rader, som eiere, arbeidsgivere, formidlinger og rom

To transaksjoner som låser de samme radene i ulik rekkefølge, kan låse hverandre fast. PostgreSQL avbryter da den ene med `40P01 deadlock detected`, og brukeren får HTTP 500.

Implisitte låser teller også. `UPDATE` låser raden til commit, og en `INSERT` med FK tar `FOR KEY SHARE` på foreldreraden. Med `FOR NO KEY UPDATE` venter ikke FK-sjekkene på de eksplisitte låsene våre, så de kan ikke låse oss fast.

`låsJobbsøkere` låser flere jobbsøkere i én spørring:

```sql
SELECT j.id
FROM jobbsoker j
JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
WHERE rt.id = ? AND j.id = ANY(?)
ORDER BY j.id
FOR NO KEY UPDATE OF j
```

### 5. Velg lås ut fra hva som endres

| Lås | Når | Operasjoner |
| --- | --- | --- |
| Jobbsøkerlås | Operasjonen endrer bare bestemte jobbsøkere og avhenger ikke av treffstatus eller andre data på treffet | Svar, invitasjon, fått jobb, angring av fått jobb og aktuell-status |
| Trefflås | Operasjonen endrer treffet eller noe som deles på treffet, eller legger til rader under treffet | Treffstatus, treffdata, eiere, arbeidsgivere og behov, nye jobbsøkere, rom og deltakernummer |
| Treff og så jobbsøkere | Operasjonen endrer begge, eller en jobbsøkeroperasjon avhenger av treffstatus eller andre data på treffet | Avlys, fullfør, registrer endring, oppmøte, sletting av jobbsøker og nye formidlinger |
| Ingen eksplisitt lås | Én `UPDATE` har hele regelen i `WHERE`, eller vi legger bare til en hendelse som ikke påvirker status | Synlighet og hendelser fra Kafka om aktivitetskort og varsler |

Skal en jobbsøkeroperasjon sjekke treffstatus, må den låse treffet før jobbsøkerne.

Finnes ikke raden ennå, låser vi forelderen og ser etter duplikater under låsen. En unik indeks er en ekstra sikring.

Trefflåsen setter alle skrivinger på treffet i kø. Med få samtidige brukere per treff blir ventetiden kort. Jobbsøkerlåsen lar svar fra ulike jobbsøkere gå samtidig.

### 6. Låst type som parameter, senere og bare hvis vi trenger det

En funksjon som krever lås, kan ta en egen type i stedet for en `Connection`. Bare låsefunksjonene kan lage typen, fordi konstruktøren er `internal`. Har kalleren typen, er låsen tatt, og et kall uten lås kompilerer ikke:

```kotlin
class LåstJobbsøker internal constructor(
    val connection: Connection,
    val personTreffId: PersonTreffId,
)

fun registrerFåttJobb(låst: LåstJobbsøker, navIdent: String) { ... }
```

Prisen er en ekstra type og nye signaturer. Regel 2 gjør behovet mindre, så vi venter.

### 7. Ingen eksterne kall under lås

HTTP-kall og Kafka-sending skjer før eller etter transaksjonen med lås. Alt vi sjekket før kallet, sjekker vi på nytt under låsen.

- `leggTilJobbsøkere` kaller kandidatsøk bare for dem som ikke er på treffet. Under låsen ser den etter duplikater på nytt.
- `lagreFormidlinger` sjekker arbeidsgiveren, at jobbsøkerne finnes, adressebeskyttelse (`sperret`) og eksisterende formidlinger på nytt under låsen.

Bare schedulerne sender til Kafka. De leser det som er lagret, og tar ingen radlås (se «Asynkron fan-out» i [Prinsipper](prinsipper.md)).

Ved dobbel innsending av formidling oppretter begge innsendingene en stilling før de får låsen. Den som får låsen sist, lagrer ingen formidling, og stillingen den opprettet, blir liggende ubrukt.

### 8. Testene kjører med samme isolasjonsnivå som produksjon

`TestDatabase` bruker `READ_COMMITTED`, som `InfrastructureContext`. En samtidighetstest bør vise at operasjonen venter på låsen og ser endringen etterpå.

`medVentendeOperasjon` i `låsetestutils.kt` gjør dette. Den tar låsen i en egen tilkobling og venter til operasjonen står i kø bak den (`pg_blocking_pids`). Så endrer den data og committer. Står ikke operasjonen i kø innen fem sekunder, feiler testen. `LåsingTest` har eksempler.

Fjern låsen og se at testen feiler, før du stoler på testen.

🔴 Rød sone: samtidighet, isolasjonsnivå og låserekkefølge. Den som endrer låsingen, bør skrive samtidighetstesten selv.

## Bekreftet mot PostgreSQL

Vi kjørte forsøkene i `postgres:17.2-alpine`, samme image som testene. Transaksjon A tar låsen og holder den i to sekunder. Transaksjon B starter et halvt sekund senere. RR er `REPEATABLE READ`, RC er `READ COMMITTED`.

| # | A holder låsen | B | Resultat for B |
| --- | --- | --- | --- |
| 1 | `FOR UPDATE` og `UPDATE` på treffet, som avlys | RR: `INSERT` med FK til treffet | Venter, så `40001` |
| 2 | Som 1 | RC: `INSERT` med FK til treffet | Venter, så OK |
| 3 | `FOR NO KEY UPDATE` og `UPDATE` på treffet | RR: `INSERT` med FK til treffet | OK uten å vente |
| 4 | Bare `FOR UPDATE` på treffet | RR: `INSERT` med FK til treffet | Venter, så OK |
| 5 | Lås og `UPDATE` på treffet | RR: `FOR NO KEY UPDATE` på samme rad | Venter, så `40001` |
| 6 | Som 5 | RC: lås på samme rad | Venter, så OK. Ser ny status (`AVLYST`). |
| 7 | Som 5 | `UPDATE` i autocommit med RR som standardnivå | Venter, så `40001` |
| 8 | Upsert (`ON CONFLICT DO UPDATE`) | RR: upsert på samme nøkkel | Venter, så `40001` |
| 9 | Som 8 | RC: upsert på samme nøkkel | Venter, så OK |

FK-sjekken tar `FOR KEY SHARE` på treffet og må vente på `FOR UPDATE` (forsøk 4). Endrer A raden etter `FOR UPDATE`, regner PostgreSQL endringen som en nøkkelendring. Da feiler FK-sjekken med `40001` under `REPEATABLE READ` (forsøk 1). Med `FOR NO KEY UPDATE` slipper den forbi uten å vente (forsøk 3).

`ExceptionMapping` gjør `SQLException` om til HTTP 500, unntatt `23503` (FK-brudd), som blir 409. `40001` og `40P01` blir derfor HTTP 500. Vi har ikke kjørt forsøk med deadlock.

## Relaterte dokumenter

- [Prinsipper](prinsipper.md) - Lagdeling og feilhåndtering med `ExceptionMapping`
- [Database](database.md) - Skjema, hendelsestabeller og treffgjennomføring
- [Jobbsøkerstatus](../9-planer/jobbsoker-statuser.md) - Statusreglene som låsene beskytter
