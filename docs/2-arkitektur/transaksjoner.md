# Transaksjoner og låsing

Når en operasjon leser data for å avgjøre hva den skal endre, låser vi de aktuelle radene før vi leser dem. Vi bruker isolasjonsnivået `READ COMMITTED`, slik at operasjonen kan lese oppdaterte data etter å ha ventet på en lås.

En transaksjon sørger for at endringene i den enten lagres samlet (`commit`) eller rulles tilbake (`rollback`). Transaksjonshjelperne ruller tilbake ved alle feil, også `Error`. En radlås hindrer at andre transaksjoner endrer den låste raden mens vi arbeider med den. Låsen holdes til transaksjonen er ferdig, men hindrer ikke vanlige lesespørringer.

Reglene gjelder rekrutteringstreff-api. Appen rekrutteringsbistand-aktivitetskort har egen database og er ikke omfattet. [Database](database.md) beskriver tabellene, og [Jobbsøkerstatus](../9-planer/jobbsoker-statuser.md) beskriver statusreglene som låsene beskytter.

## Kort fortalt

- Vi bruker `READ COMMITTED` både i produksjon og i testene. Etter å ha ventet på en lås kan neste spørring lese endringene som ble committet mens operasjonen ventet.
- Vi låser før vi leser data som avgjør hva vi skal endre. Låsekallet kan utelates hvis hele vilkåret og endringen ligger i én `UPDATE`.
- Eksplisitte radlåser tas gjennom funksjonene i `låsing.kt`. En test sjekker at låse-SQL ikke legges i andre filer.
- Når vi trenger flere låser, låser vi treffet først, deretter jobbsøkerne sortert på `id`, og til slutt andre rader.
- Vi gjør ingen HTTP-kall og sender ingenting til Kafka mens vi holder en lås.
- En transaksjon kan ikke startes inne i en annen. Send `connection` videre i stedet.
- Servicelaget starter transaksjoner med flere repositorykall eller lås. Et repository kan bruke autocommit for én setning og en lesetransaksjon for søk med flere spørringer.
- Samtidighetstester skal vise både at operasjonen venter på låsen, og at den bruker oppdaterte data etterpå.

## Transaksjonsfunksjonene

Hjelpefunksjonene står i `transactionManager.kt` og `låsing.kt`. Alle databasekall som skal inngå i transaksjonen, må bruke `Connection`-objektet som blokken får.

| Funksjon | Bruk |
| --- | --- |
| `medLåstTreff(treffId) { connection -> ... }` | Brukes når operasjonen trenger trefflåsen. Starter transaksjonen og låser treffraden før blokken kjører. Trenger operasjonen også jobbsøkere, kaller blokken `låsJobbsøkere` eller `låsAlleJobbsøkerePåTreff` først. |
| `medLåsteJobbsøkere(treffId, personTreffIder) { connection -> ... }` | Brukes når operasjonen bare trenger jobbsøkerlåsen. Starter transaksjonen og låser jobbsøkerne før blokken kjører. |
| `executeInTransaction { connection -> ... }` | Brukes ved endringer som ikke trenger eksplisitt lås. Det gjelder for eksempel opprettelse av treff, hendelser som ikke påvirker status, og schedulerne. |
| `executeInReadOnlyTransaction { connection -> ... }` | Brukes når flere spørringer må se databasen slik den var på samme tidspunkt. Det gjelder for eksempel når vi teller søkeresultater og henter én side fra det samme søket. Bruker `REPEATABLE READ` og tillater ikke skriving (`readOnly`). |

Når blokken lykkes, committer hjelpefunksjonen transaksjonen. Kaster blokken et unntak, ruller hjelpefunksjonen tilbake og kaster unntaket videre.

Velg funksjon ut fra låsen operasjonen trenger. Da ser du hvilken lås som gjelder, på første linje, og låsen kan ikke havne etter en lesing.

### Hvor transaksjonen startes

Servicelaget starter transaksjoner som omfatter flere repositorykall, og alle transaksjoner som låser. Det er servicen som vet hva som må lykkes samlet, og hvilke rader som må låses (se [Prinsipper](prinsipper.md)).

Repositorylaget kan i to tilfeller hente tilkoblingen selv:

- **Én setning med autocommit** (`dataSource.connection.use`). En enkelt `SELECT`, `INSERT`, `UPDATE` eller `DELETE` er atomisk, og én `SELECT` ser alltid databasen slik den var da spørringen startet. Da trengs ingen transaksjon.
- **Flere lesespørringer i én repositorymetode** (`executeInReadOnlyTransaction`). Det gjelder søk som teller treff, lager aggregeringer og henter én side, for eksempel `RekrutteringstreffSokRepository`, `JobbsøkerSokRepository` og `JobbsøkerFormidlingSokRepository`. Øyeblikksbildet hører til implementasjonen av søket.

Bruk ikke `executeInReadOnlyTransaction` for én enkelt spørring. Det gir bare ekstra rundturer for å sette isolasjonsnivå, `BEGIN` og `COMMIT`.

Én enkelt SQL-spørring trenger ikke en slik transaksjonsblokk. Med autocommit kjører databasen spørringen i en egen transaksjon. Det gjelder også en `UPDATE` som har hele vilkåret i `WHERE`, slik vi bruker ved oppdatering av synlighet fra Kafka.

## Regler

### 1. Lås først, valider etterpå

Ta låsen før du leser verdiene som avgjør om operasjonen er tillatt. Det kan være treffstatus, jobbsøkerstatus, synlighet eller om en rad finnes fra før.

Hvis du leser at et treff er publisert og deretter venter på låsen, kan noen ha avlyst treffet mens du ventet. Derfor må du sjekke status etter at du har fått låsen.

Før et eksternt kall kan vi gjøre en foreløpig sjekk for å unngå et unødvendig kall. Denne sjekken erstatter ikke sjekken under låsen.

Tilgangssjekken i controlleren ligger utenfor denne regelen. Den kontrollerer hvem som får gjøre forespørselen. Inne i transaksjonen sjekker vi om operasjonen er tillatt med den tilstanden dataene har nå.

### 2. Den som starter transaksjonen, låser

Funksjonen som starter transaksjonen, har også ansvaret for å ta låsene. Den kan først slå opp id-en til raden som skal låses. Verdiene som avgjør hva operasjonen skal gjøre, leses etter at låsen er tatt.

Med en eksplisitt lås mener vi en lås vi ber om med en egen SQL-spørring. PostgreSQL tar også låser automatisk når vi endrer data (se regel 4).

- Vanlige service- og repositoryfunksjoner som får en `Connection`, tar ikke egne eksplisitte låser. Hvis de trenger en lås, skal kalleren allerede ha tatt den. Funksjoner som endrer jobbsøkerstatus eller treffstatus, har en KDoc-linje som sier hvilken lås kalleren må ha tatt, for eksempel «Kalleren må ha låst jobbsøkeren.» Kompilatoren sjekker ikke dette. Lesefunksjoner trenger ingen slik linje.
- Lesefunksjoner tar ikke eksplisitte låser.
- Selve låsefunksjonene står i `låsing.kt`. Bare der skriver vi SQL med `FOR NO KEY UPDATE`.

Slik blir det synlig hvor låsene tas. Vi unngår at en hjelpefunksjon tar en lås som kalleren allerede har tatt. `TransaksjonTest` leser kildekoden og feiler hvis den finner låse-SQL i andre filer.

Å ta samme lås to ganger i samme transaksjon er ufarlig. PostgreSQL ser at transaksjonen allerede holder låsen. Det farlige er å starte en ny transaksjon inne i en annen. Den indre transaksjonen får en egen tilkobling og venter på låsen som den ytre holder, mens den ytre venter på at den indre skal bli ferdig. PostgreSQL ser ingen deadlock, så kallet henger. Transaksjonsfunksjonene kaster derfor `IllegalStateException` hvis tråden allerede har en åpen transaksjon. Funksjoner som trenger databasen inne i en transaksjon, tar imot `connection` i stedet.

| Låsefunksjon | Låser |
| --- | --- |
| `medLåstTreff(treffId)` og `låsTreff(treffId)` | Treffraden. Kaster `RekrutteringstreffIkkeFunnetException` (404) hvis treffet ikke finnes. |
| `medLåsteJobbsøkere(treffId, personTreffIder)` og `låsJobbsøkere(treffId, personTreffIder)` | De oppgitte jobbsøkerne på treffet, sortert på `id`, i én spørring. Tar også med slettede og ikke-synlige jobbsøkere. Kalleren må deretter sjekke status og synlighet. Kaster `JobbsøkerIkkeFunnetException` (404) hvis en jobbsøker ikke finnes på treffet. |
| `låsAlleJobbsøkerePåTreff(treffId)` | Alle jobbsøkerne på treffet, også slettede, sortert på `id`. Ta trefflåsen først, slik at ingen legger til nye jobbsøkere mens operasjonen kjører. |

Ved publisering låser vi treffet før vi sjekker status:

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

Når vi registrerer et svar på vegne av en jobbsøker, trenger vi bare jobbsøkerlåsen:

```kotlin
fun svarPåVegneAvJobbsøker(personTreffId: PersonTreffId, treffId: TreffId, navIdent: String, svar: Boolean?) {
    dataSource.medLåsteJobbsøkere(treffId, listOf(personTreffId)) { connection ->
        // Leser synlighet, status og gjeldende svar, og skriver hendelsen
    }
}
```

#### Hvorfor `FOR NO KEY UPDATE`

En fremmednøkkel (FK) sikrer at en rad peker på en rad som finnes. Når vi legger til en hendelse for en jobbsøker, må PostgreSQL for eksempel sjekke at jobbsøkeren finnes. Denne sjekken tar en `FOR KEY SHARE`-lås på jobbsøkerraden.

`FOR UPDATE` er ikke forenlig med `FOR KEY SHARE`. Hvis en annen transaksjon allerede holder `FOR UPDATE` på jobbsøkeren, må innsettingen av hendelsen vente. Det samme gjelder hendelser, innlegg og KI-logg som peker på et låst treff. Forsøk 4 viser denne ventingen.

`FOR NO KEY UPDATE` lar FK-sjekken passere, men blokkerer fortsatt andre `FOR NO KEY UPDATE`-låser, `UPDATE` og `DELETE` på samme rad. Den passer fordi vi endrer vanlige felt, ikke nøkkelkolonnene. Vi sletter heller ikke treff-, jobbsøker- eller arbeidsgiverrader fysisk, men markerer dem som slettet.

Låsen hindrer altså ikke i seg selv at andre legger til hendelser eller andre barnerader. Hvis slike innsettinger kan påvirke avgjørelsen vår, må de også ta den samme låsen. Regel 5 beskriver hvordan vi bruker dette.

### 3. Alle skrivinger kjører `READ COMMITTED`

Med `READ COMMITTED` får hver spørring se data som var committet da spørringen startet. Hvis en låsespørring må vente på en rad som en annen transaksjon endrer, kan den låse den oppdaterte raden når den andre transaksjonen committer. Neste spørring kan så lese de oppdaterte verdiene.

Det betyr også at to spørringer i samme transaksjon kan få ulike resultater. Låsene beskytter radene vi har låst, men fryser ikke hele databasen eller listene over barnerader.

Med `REPEATABLE READ` beholder transaksjonen derimot øyeblikksbildet fra den første spørringen. Hvis den prøver å låse eller endre en rad som en annen transaksjon har endret etter dette tidspunktet, kan PostgreSQL ikke bruke den nye radversjonen. Operasjonen avbrytes da med `40001 could not serialize access due to concurrent update`.

Konflikten kan oppstå ved:

- eksplisitte låser som `FOR UPDATE` og `FOR NO KEY UPDATE`
- `UPDATE` og `DELETE`, også når hver spørring kjører i en egen transaksjon med autocommit
- `INSERT ... ON CONFLICT DO UPDATE`
- FK-sjekken ved `INSERT` når foreldreraden er låst med `FOR UPDATE` og endret

Konstanten `READ_COMMITTED` i `transactionManager.kt` brukes både i `InfrastructureContext` og i `TestDatabase`. Dermed bruker produksjon og tester samme isolasjonsnivå.

Når flere lesespørringer trenger samme øyeblikksbilde, bruker vi `executeInReadOnlyTransaction`. Den bruker `REPEATABLE READ`, men verken skriver eller tar skrivelåser. Den unngår derfor oppdateringskonflikten beskrevet over.

Isolasjonsnivået gjelder hver enkelt transaksjon, så en lesetransaksjon kan kjøre samtidig med skrivinger under `READ COMMITTED`. Vanlige `SELECT`-spørringer venter ikke på radlåser. Leseren ser raden slik den sist ble committet, og blir ikke blokkert av at en skriver holder en lås. Skriveren merker heller ikke at noen leser.

Leseren kan se data som er litt utdatert, fordi den ikke ser det som committes etter at øyeblikksbildet ble tatt. Det passer for søk og lister. En avgjørelse som bygger på dataene, må tas i en skrivetransaksjon med lås.

Et alternativ for skriving er `REPEATABLE READ` med ny kjøring av hele transaksjonen ved `40001`. Da må koden tåle at transaksjonsblokken kjøres flere ganger. Vi bruker i stedet `READ COMMITTED` og låsing, slik at operasjonen kan vente og deretter bruke oppdaterte data.

### 4. Fast låserekkefølge

1. Treff
2. Jobbsøkere, sortert på `id`
3. Andre rader, som eiere, arbeidsgivere, formidlinger og rom

To transaksjoner som låser de samme radene i ulik rekkefølge, kan bli stående og vente på hverandre. Hvis A holder trefflåsen og venter på jobbsøkeren, mens B holder jobbsøkerlåsen og venter på treffet, kommer ingen videre. Dette kalles deadlock. PostgreSQL avbryter da den ene transaksjonen med `40P01 deadlock detected`. For et HTTP-kall blir resultatet HTTP 500.

Låser som PostgreSQL tar automatisk, teller også. En `UPDATE` låser raden, og en `INSERT` med fremmednøkkel tar `FOR KEY SHARE` på foreldreraden. FK-låsen kan tas samtidig med vår `FOR NO KEY UPDATE`-lås. Dermed unngår vi at akkurat denne FK-sjekken må vente på oss. Det fjerner ikke behovet for en fast rekkefølge på de andre låsene.

`låsJobbsøkere` låser flere jobbsøkere i én spørring:

```sql
SELECT j.id
FROM jobbsoker j
JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
WHERE rt.id = ? AND j.id = ANY(?)
ORDER BY j.id
FOR NO KEY UPDATE OF j
```

`ORDER BY j.id` gir samme låserekkefølge når to kall ber om overlappende sett med jobbsøkere. `OF j` begrenser låsen til jobbsøkerradene. Trefftabellen er med for å sjekke tilhørighet, men treffraden låses ikke av denne spørringen.

### 5. Velg lås ut fra hva som endres

| Lås | Når | Operasjoner |
| --- | --- | --- |
| Jobbsøkerlås | Vi endrer bestemte jobbsøkere uten å bygge avgjørelsen på treffstatus eller andre felles data | Svar, invitasjon, fått jobb, angring av fått jobb og aktuell-status |
| Trefflås | Vi endrer treffet eller felles data, eller legger til nye rader som slike operasjoner må ta hensyn til | Treffstatus, treffdata, eiere, arbeidsgivere og behov, nye jobbsøkere, rom og deltakernummer |
| Trefflås, deretter jobbsøkerlås | Vi endrer både treffet og jobbsøkerne, eller trenger data fra begge for å avgjøre hva vi skal gjøre | Avlys, fullfør, registrer endring, oppmøte, sletting av jobbsøker og nye formidlinger |
| Ingen eksplisitt lås | Hele vilkåret og endringen ligger i én `UPDATE`, eller vi bare legger til en hendelse som ikke påvirker avgjørelser som krever lås | Synlighet og hendelser fra Kafka om aktivitetskort og varsler |

Skal en operasjon på en jobbsøker sjekke treffstatus, må den låse treffet før jobbsøkeren. En jobbsøkerlås alene hindrer ikke at noen endrer treffstatus. Bruk da `medLåstTreff` og kall `låsJobbsøkere` i blokken, ikke `medLåsteJobbsøkere`.

En rad som ikke finnes ennå, kan ikke radlåses. Når vi legger til jobbsøkere, låser vi derfor treffet og sjekker om personene allerede er lagt til. En unik indeks gir ekstra beskyttelse mot duplikater.

Alle kall som legger til jobbsøkere, tar den samme trefflåsen. Når fullføring holder trefflåsen, må disse kallene vente. Dermed kommer det ikke nye jobbsøkere til mens fullføringen pågår.

Tilsvarende må alle som skriver hendelser som påvirker jobbsøkerstatus eller svar, ta jobbsøkerlåsen først. Hendelser som ikke påvirker disse avgjørelsene, kan komme til underveis uten å endre resultatet. Det er altså felles bruk av låsen som beskytter oss, ikke fremmednøkkelen alene.

Operasjoner som tar trefflåsen, må vente på hverandre. Vi forventer få samtidige brukere per treff og holder transaksjonene korte. Operasjoner som bare tar jobbsøkerlåsen, kan kjøre samtidig når de gjelder ulike jobbsøkere.

### 6. En egen type kan gjøre låsekravet synlig

Dette er en mulig utvidelse, ikke noe vi bruker i dag. En funksjon som krever jobbsøkerlås, kan ta et `LåstJobbsøker`-objekt i stedet for en vanlig `Connection`. Tanken er at låsefunksjonen oppretter objektet etter at den har tatt låsen:

```kotlin
class LåstJobbsøker internal constructor(
    val connection: Connection,
    val personTreffId: PersonTreffId,
)

fun registrerFåttJobb(låst: LåstJobbsøker, navIdent: String) { ... }
```

Da krever kompilatoren at kalleren sender inn et `LåstJobbsøker`-objekt. Den kontrollerer derimot ikke om databasen faktisk holder låsen. `internal` gjør konstruktøren tilgjengelig i hele Kotlin-modulen, ikke bare i låsefunksjonene. Objektet kan dessuten beholdes etter at transaksjonen er avsluttet og låsen frigitt.

Typen kan gjøre kravet tydeligere, men eksemplet gir ikke alene noen garanti for at låsen er tatt og fortsatt holdes. Vi venter med å innføre en slik type. Foreløpig bruker vi ansvarsfordelingen i regel 2 og samtidighetstester.

### 7. Ingen eksterne kall under lås

Gjør HTTP-kall og send Kafka-meldinger utenfor transaksjoner som holder lås. Da slipper andre operasjoner å vente på låsen mens vi venter på en ekstern tjeneste. Hvis vi gjorde foreløpige sjekker før kallet, gjentar vi dem under låsen før vi lagrer.

- `leggTilJobbsøkere` kaller kandidatsøk bare for dem som ikke er på treffet. Under låsen ser den etter duplikater på nytt.
- `lagreFormidlinger` sjekker på nytt at arbeidsgiveren og jobbsøkerne finnes, om jobbsøkerne har adressebeskyttelse (`sperret`), og om formidlingene allerede er registrert.

Bare schedulerne sender til Kafka. De leser lagrede hendelser uten å ta eksplisitte radlåser (se «Asynkron fan-out» i [Prinsipper](prinsipper.md)).

Denne oppdelingen betyr at en lokal rollback ikke kan angre det eksterne kallet. Ved to samtidige innsendinger av samme formidling kan begge opprette en stilling før de får låsen. Den som får låsen sist, oppdager at formidlingen allerede finnes og lagrer ingen ny formidling. Stillingen den opprettet, blir da liggende ubrukt.

### 8. Testene kjører med samme isolasjonsnivå som produksjon

`TestDatabase` og `InfrastructureContext` bruker begge `READ_COMMITTED`. Testene skal dermed ha samme oppførsel ved venting og samtidige endringer som produksjon.

En samtidighetstest bør vise at operasjonen både venter på riktig lås og bruker endringene som ble lagret mens den ventet. `medVentendeOperasjon` i `låsetestutils.kt` hjelper oss å sette opp dette:

1. Hjelpefunksjonen tar låsen i en egen transaksjon og starter operasjonen som skal testes, i en annen tråd.
2. Den bruker PostgreSQL-funksjonen `pg_blocking_pids` til å sjekke at operasjonen venter på låsen. Testen feiler hvis ventingen ikke er registrert innen fem sekunder.
3. Den gjør den avtalte endringen og committer. Operasjonen får fortsette, og testen kan kontrollere at resultatet bygger på den nye verdien.

`TransaksjonTest` har ett eksempel for hver låsefunksjon og for hver låserekkefølge som må holde:

- Oppmøte låser jobbsøkeren i tillegg til treffet, siden svar bare låser jobbsøkeren.
- Avlys og fullfør låser alle jobbsøkerne før de velger hendelse ut fra statusen.
- Oppmøte teller deltakernummer under trefflåsen.
- Trefflåsen hindrer at to samtidige eierslettinger fjerner begge eierne.

Fjern låsen midlertidig og kontroller at testen da feiler. Slik sjekker du at testen faktisk oppdager den manglende låsen.

Vi tester mekanismen i `TransaksjonTest`, ikke hver operasjon som bruker den. Lag en ny samtidighetstest bare når du innfører en ny type lås eller låserekkefølge. Skal du teste en sjekk som gjøres på nytt etter et eksternt kall, kan du gjøre endringen inne i mocken av det eksterne kallet. Da trenger du verken tråder eller låser i testen.

🔴 Rød sone: samtidighet, isolasjonsnivå og låserekkefølge. Den som endrer låsingen, bør skrive samtidighetstesten selv.

## Bekreftet mot PostgreSQL

Forsøkene under ble kjørt med `postgres:17.2-alpine`, samme image som testene. Transaksjon A tar låsen og holder den i to sekunder før den committer. Transaksjon B starter et halvt sekund etter A. Tabellen viser om B må vente, og om den lykkes etter at A har committet.

RR betyr `REPEATABLE READ`, RC betyr `READ COMMITTED`, og FK betyr fremmednøkkel.

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

Forsøk 4 viser at FK-sjekken må vente på en `FOR UPDATE`-lås, selv om A ikke endrer raden. I forsøk 1 oppdaterer A også raden. Etter ventingen feiler B med `40001` under `REPEATABLE READ`. Med `FOR NO KEY UPDATE` kan FK-sjekken kjøre mens transaksjon A holder låsen, og innsettingen lykkes uten venting (forsøk 3).

`ExceptionMapping` gjør normalt `SQLException` om til HTTP 500. Unntaket er `23503`, som betyr brudd på en fremmednøkkel og gir HTTP 409. Både `40001` og `40P01` gir derfor HTTP 500. Tabellen omfatter ikke forsøk med deadlock.

## Relaterte dokumenter

- [Prinsipper](prinsipper.md) - Lagdeling og feilhåndtering med `ExceptionMapping`
- [Database](database.md) - Skjema, hendelsestabeller og treffgjennomføring
- [Jobbsøkerstatus](../9-planer/jobbsoker-statuser.md) - Statusreglene som låsene beskytter
