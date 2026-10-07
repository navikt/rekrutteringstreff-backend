# Låsing og samtidighet

**Status:** Arbeidsdokument. Strategien er et forslag. Vurderingen gjelder `main` etter #229, per 07.10.2026. Når strategien og koden er på plass, flytter vi strategidelen til `docs/2-arkitektur/transaksjoner.md`.

Dokumentet beskriver hvordan skrivende operasjoner bør låse rader i PostgreSQL, og vurderer dagens kode opp mot det. Les [Database](../2-arkitektur/database.md) for skjema og [Jobbsøkerstatus](jobbsoker-statuser.md) for statusreglene som låsene beskytter.

## Kort fortalt

- Poolen kjører `REPEATABLE READ`. Da kan FK-sjekker, upsert og `UPDATE` feile med `40001` når en annen transaksjon endrer samme rad samtidig. Det gjelder også autocommit. Brukeren får HTTP 500. Vi har bekreftet dette mot PostgreSQL 17.
- Avlys, fullfør, svar, invitasjon, fått jobb, oppmøte, sletting av jobbsøker, arbeidsgivere og treffgjennomføring låser riktig og kjører `READ COMMITTED`.
- Publiser, avpubliser, gjenåpne, slett treff, oppdater treff og registrer endring låser ikke. De validerer før transaksjonen eller ikke i det hele tatt.
- Legg til jobbsøkere, endre behov og lagring av formidlinger låser heller ikke. Eiere og aktuell-status låser, men kjører `REPEATABLE READ`.
- Låsene er vanskelige å se. Koden bruker sju låsemønstre i fire filer, noen låser ligger skjult i lesefunksjoner, og svar, invitasjon, fått jobb og angring låser samme jobbsøker to ganger.
- Vi foreslår å låse det som mangler lås først, og deretter bruke `READ COMMITTED` for alle skrivinger. Da trenger vi én transaksjonsfunksjon, og testene kjører som produksjon.

## Strategi

### 1. Lås først, valider etterpå

Ta låsen før du leser verdiene du bestemmer ut fra, som treffstatus, jobbsøkerstatus, synlighet og om raden finnes fra før. En sjekk før låsen kan være utdatert når du får låsen.

Før et eksternt kall kan det lønne seg å sjekke tidlig, så vi slipper kallet når svaret uansett er nei. Da gjentar vi sjekken under låsen.

Tilgangssjekken i controlleren er et unntak. Den avgjør hvem som får gjøre forespørselen, ikke hva som skal skrives.

### 2. Den som starter transaksjonen, låser

Funksjonen som starter transaksjonen, tar låsene først. Den kan slå opp id-en den skal låse, men leser alt annet etter låsen.

- Funksjoner som får en `Connection`, låser ikke selv. De forutsetter at kalleren har låst.
- Lesefunksjoner låser aldri.
- Alle låsefunksjonene ligger i én fil. Bare der står `FOR NO KEY UPDATE`.

Da ser du alle låsene øverst i transaksjonen, og vi låser aldri samme rad to ganger. En test som leser kildekoden, stopper låser andre steder.

| Låsefunksjon | Låser | I dag |
| --- | --- | --- |
| `medLåstTreff(treffId)` og `låsTreff(treffId)` | Treffraden | Finnes i `treffLås.kt` |
| `låsJobbsøkere(treffId, personTreffIder)` | De oppgitte jobbsøkerne på treffet, sortert på `id`, i én spørring. Feiler hvis en av dem ikke hører til treffet. | Delvis. `JobbsøkerRepository.låsJobbsøker` låser én om gangen og sjekker ikke treffet. |
| `låsAlleJobbsøkerePåTreff(treffId)` | Alle jobbsøkerne på treffet, sortert på `id` | Finnes som `JobbsøkerRepository.låsJobbsøkereForTreff` |

Med `READ COMMITTED` som standard (regel 3) trenger vi ikke wrappere for å velge isolasjonsnivå. Vi beholder `medLåstTreff` fordi den gjør de vanligste skrivingene korte. Andre skrivinger starter med `executeInTransaction` og kaller låsefunksjonen på første linje.

En vanlig skriving ser slik ut:

```kotlin
fun publiser(treffId: TreffId, navIdent: String) {
    dataSource.medLåstTreff(treffId) { connection ->
        val treff = rekrutteringstreffRepository.hent(connection, treffId)
            ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId ikke funnet")
        if (treff.status != RekrutteringstreffStatus.UTKAST) {
            throw UlovligOppdateringException("Kan kun publisere rekrutteringstreff som er i UTKAST status")
        }
        rekrutteringstreffRepository.leggTilHendelseForTreff(connection, treffId, RekrutteringstreffHendelsestype.PUBLISERT, navIdent)
        rekrutteringstreffRepository.endreStatus(connection, treffId, RekrutteringstreffStatus.PUBLISERT)
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
- FK-sjekken ved `INSERT` når foreldreraden er låst med `FOR UPDATE` og endret, for eksempel treffet under avlysning

Under `READ COMMITTED` venter spørringen, leser den nye versjonen av raden og fortsetter. Neste spørring ser det den andre transaksjonen lagret. Det er dette låsene bygger på.

Vi setter derfor `READ COMMITTED` på poolen, både i `InfrastructureContext` og i `TestDatabase`. Lesetransaksjoner som trenger ett øyeblikksbilde over flere spørringer, for eksempel søk med totalt antall og én side, bruker en egen funksjon med `REPEATABLE READ` og `readOnly`. En lesetransaksjon under `REPEATABLE READ` får aldri `40001`.

Alternativet er å beholde `REPEATABLE READ` og kjøre transaksjonen på nytt ved `40001`. Da må alle skrivinger ligge i en løkke, og blokken må tåle å kjøres flere ganger. Med `READ COMMITTED` og lås venter transaksjonen i stedet og fortsetter med ferske data.

### 4. Fast låserekkefølge

1. Treff
2. Jobbsøkere, sortert på `id`
3. Andre rader, som eiere, arbeidsgivere, formidlinger og rom

To transaksjoner som låser de samme radene i ulik rekkefølge, kan låse hverandre fast. PostgreSQL avbryter da den ene med `40P01 deadlock detected`, og brukeren får HTTP 500.

Implisitte låser teller også. `UPDATE` låser raden til commit, og en `INSERT` med FK tar `FOR KEY SHARE` på foreldreraden. Med `FOR NO KEY UPDATE` venter ikke FK-sjekkene på de eksplisitte låsene våre, så de kan ikke låse oss fast.

Skal du låse flere jobbsøkere, gjør du det i én spørring:

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
| Jobbsøkerlås | Operasjonen endrer bare bestemte jobbsøkere og avhenger ikke av treffstatus | Svar, invitasjon, fått jobb, angring av fått jobb og aktuell-status |
| Trefflås | Operasjonen endrer treffet eller noe som deles på treffet, eller legger til rader under treffet | Treffstatus, treffdata, eiere, arbeidsgivere og behov, nye jobbsøkere, nye formidlinger, rom og deltakernummer |
| Treff og så jobbsøkere | Operasjonen endrer begge, eller en jobbsøkeroperasjon avhenger av treffstatus | Avlys, fullfør, registrer endring, oppmøte og sletting av jobbsøker |
| Ingen eksplisitt lås | Én `UPDATE` har hele regelen i `WHERE`, eller vi legger bare til en hendelse som ikke påvirker status | Synlighet og hendelser fra Kafka om aktivitetskort og varsler |

Bestemmer vi at svar og invitasjon skal sjekke treffstatus (se steg 1), flytter de til «treff og så jobbsøkere».

Finnes ikke raden ennå, låser vi forelderen og ser etter duplikater under låsen. En unik indeks er en ekstra sikring.

Trefflåsen setter alle skrivinger på treffet i kø. Med få samtidige brukere per treff blir ventetiden kort. Jobbsøkerlåsen lar svar fra ulike jobbsøkere gå samtidig.

### 6. Bevis på lås, senere og bare hvis vi trenger det

En funksjon som krever lås, kan ta et bevis som parameter i stedet for en `Connection`. Da stopper kompilatoren et kall uten lås:

```kotlin
class LåstJobbsøker internal constructor(
    val connection: Connection,
    val personTreffId: PersonTreffId,
)

fun registrerFåttJobb(låst: LåstJobbsøker, navIdent: String) { ... }
```

Prisen er en ekstra type og nye signaturer. Regel 2 gjør behovet mindre, så vi venter.

### 7. Ingen eksterne kall under lås

HTTP-kall og Kafka-sending skjer før eller etter transaksjonen med lås. Alt vi sjekket før kallet, sjekker vi på nytt under låsen. `FormidlingService` sjekker jobbsøkerstatus på nytt, men ikke adressebeskyttelse (`sperret`) eller om formidlingen allerede finnes.

### 8. Testene kjører med samme isolasjonsnivå som produksjon

`TestDatabase` setter samme nivå som `InfrastructureContext`. En samtidighetstest bør vise at operasjonen venter på låsen og ser endringen etterpå.

`medVentendeOperasjon` i `TreffgjennomføringKomponentTest` gjør dette. Den holder trefflåsen i en egen tilkobling og venter til operasjonen står i kø bak den (`pg_blocking_pids`). Så endrer den data og committer.

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

## Vurdering av dagens kode

✅ følger strategien. ⚠️ avvik uten feil data. ❌ avvik som kan gi feil data eller HTTP 500. RC er `READ COMMITTED`, RR er `REPEATABLE READ`.

Forrige versjon av dokumentet tok feil på to punkter. Endring av behov bruker ikke `medLåstTreff`, og synlighet kjører i en `REPEATABLE READ`-transaksjon, ikke i autocommit.

### Treffet

| Operasjon | Lås | Isolasjon | Validering | Vurdering |
| --- | --- | --- | --- | --- |
| Opprett treff | Ingen | RR | Ikke relevant | ✅ Nye rader som ingen andre skriver til ennå. |
| Avlys og fullfør | `medLåstTreff`, så `låsJobbsøkereForTreff` | RC | Under lås, i `valider`-lambdaen | ✅ |
| Fullfør fra `RekrutteringstreffScheduler` | Som fullfør | RC | Under lås | ⚠️ Feiler ett treff, hopper kjøringen over resten. Neste forsøk er om 15 minutter. |
| Publiser og gjenåpne | Ingen | RR | Før transaksjonen, på en annen tilkobling | ❌ To samtidige kall gir to hendelser eller HTTP 500. Publiser og slett samtidig kan begge gå gjennom, så et slettet treff kan ende som publisert. |
| Avpubliser | Ingen | RR | Ingen statussjekk | ❌ Kan sette et avlyst eller fullført treff tilbake til utkast. Regelen må avklares. |
| Slett treff (`markerSlettet`) | Ingen | RR | Før transaksjonen. Arbeidsgiverne leses på en annen tilkobling. | ❌ Kan slette et treff som fikk jobbsøkere i mellomtiden. |
| Oppdater treff | Ingen | RR | Ingen statussjekk | ❌ `40001` mot avlys, fullfør og andre oppdateringer av treffet. |
| Registrer endring (`registrerEndring`) | Ingen | RR | Treffstatus sjekkes i controlleren | ❌ Velger hendelser ut fra jobbsøkerstatus uten lås, slik avlys gjorde før #229. Kan få `40001` mot samtidige svar. |
| Eiere: legg til og slett (`EierService`) | `EierRepository.hent(forUpdate = true)` låser treffet og så eierne | RR | Under lås | ❌ Kan få `40001` mot andre eierendringer og mot avlys og fullfør. |
| Innlegg og KI-logg | Ingen | RR i autocommit | Ikke relevant | ⚠️ FK-sjekken ved nye rader kan få `40001` mot avlys og fullfør. |

### Jobbsøkere

| Operasjon | Lås | Isolasjon | Validering | Vurdering |
| --- | --- | --- | --- | --- |
| Legg til jobbsøkere (`leggTilJobbsøkere`) | Ingen | RR | Eksisterende og slettede jobbsøkere leses før transaksjonen | ❌ To samtidige kall kan legge til samme person to ganger. Ingen unik indeks stopper det. Kan få `40001` mot avlys og fullfør. |
| Invitasjon (`inviter`) | `låsJobbsøker` én og én, sortert | RC | Under lås | ✅ ⚠️ `hentStatus` låser på nytt. Sjekker ikke treffstatus, så vi kan invitere til et avlyst treff. |
| Svar fra jobbsøker og på vegne av jobbsøker (`registrerSvar`) | `låsJobbsøker` | RC | Under lås | ✅ ⚠️ `hentStatus` låser på nytt. Sjekker ikke treffstatus. |
| Fått jobb (`registrerFåttJobb`) | Låser selv | RC | Under lås | ✅ ⚠️ Låser selv, mens `angreFåttJobb` forutsetter at kalleren har låst. Begge låser på nytt gjennom `hentStatus`. |
| Angre fått jobb (`FormidlingService.slett`) | Kalleren låser med `JobbsøkerService.låsJobbsøker` | RC | Under lås | ✅ ⚠️ Se fått jobb. |
| Oppmøte (`OppmøteService`) | `medLåstTreff` gjennom `TreffgjennomføringWriter.skriv`, så `låsJobbsøker` | RC | Under lås | ✅ |
| Slett jobbsøker (`markerSlettet`) | `medLåstTreff`, så `hentSlettestatus` (`FOR UPDATE OF j`) | RC | Under lås | ✅ ⚠️ Jobbsøkerlåsen er skjult i en lesefunksjon. |
| Aktuell-status (`endreAktuellForTreffStatus`) | `hentAktuellForTreffStatusForOppdatering` (`FOR UPDATE OF j`) | RR | Under lås | ❌ `40001` mot svar, invitasjon og oppmøte på samme jobbsøker. |
| Synlighet fra Kafka (`oppdaterSynlighetFraEvent` og `oppdaterSynlighetFraNeed`) | Ingen. Regelen står i `WHERE`. | RR | I `UPDATE` | ⚠️ Regelen er riktig, men `UPDATE` kan få `40001` mot statusskrivinger på samme person. Da feiler behandlingen av Kafka-meldingen. |
| Hendelser fra Kafka som ikke endrer status (`registrerAktivitetskortOpprettelseFeilet` og `registrerMinsideVarselSvar`) | Ingen | RR | Ikke relevant | ⚠️ Trenger ingen lås, men FK-sjekken kan få `40001` mot svar og invitasjon på samme jobbsøker. |
| `JobbsøkerhendelserScheduler` | Ingen | RR | Ikke relevant | ✅ Skriver bare egne pollingrader. |

### Arbeidsgivere, formidling og treffgjennomføring

| Operasjon | Lås | Isolasjon | Validering | Vurdering |
| --- | --- | --- | --- | --- |
| Treffgjennomføring (`TreffgjennomføringWriter.skriv`): steg, møteoppsett, rom, interesse, intervjufordeling og vurdering | `medLåstTreff` | RC | Under lås, i `krevKontekst` | ✅ |
| Arbeidsgiver: legg til, legg til med behov og slett | `medLåstTreff` | RC | Under lås | ✅ |
| Endre behov (`oppdaterBehov`) | Ingen | RR | I upsert-SQL-en (`status <> 'SLETTET'`) | ❌ Samtidige endringer av samme behov gir `40001`. Kan lagre behov på en arbeidsgiver som slettes samtidig. |
| Opprett formidling: lagring (`lagreFormidlinger`) | Ingen | RR | `sperret` og eksisterende formidlinger sjekkes før de eksterne kallene | ❌ Dobbel innsending kan gi to formidlinger og to stillinger. Får lagringen `40001`, blir stillingen liggende uten formidling. |
| Opprett formidling: fått jobb | `registrerFåttJobb` låser jobbsøkeren | RC | Under lås | ✅ |
| Slett formidling | `låsJobbsøker` | RC | Under lås | ✅ |

## Lesbarhet

Det er vanskelig å se hvor koden låser. Vi bruker sju mønstre i fire filer:

| Mønster | Eksempel | Ser du låsen der den brukes? |
| --- | --- | --- |
| Wrapper som starter transaksjon og låser | `medLåstTreff` | Ja |
| Låsefunksjon i repository | `JobbsøkerRepository.låsJobbsøker`, `låsJobbsøkereForTreff` | Ja |
| Låsefunksjon sendt videre gjennom service | `JobbsøkerService.låsJobbsøker` | Ja, men SQL-en ligger i en annen fil |
| Lesing med `ForOppdatering` i navnet | `hentAktuellForTreffStatusForOppdatering` | Ja |
| Lesing som låser uten å si det | `hentStatus`, `hentSlettestatus` | Nei |
| Boolsk parameter | `EierRepository.hent(forUpdate = true)` | Ja |
| Lås inne i en `INSERT` | `EierRepository.leggTil` (`INSERT ... SELECT ... FOR UPDATE`) | Nei |

Andre ting som gjør koden tyngre å lese:

- `executeInTransaction` og `executeInLockingTransaction` skiller seg bare på isolasjonsnivå. Den som skriver ny kode, må vite hvilken som er riktig.
- `registrerFåttJobb` låser selv, mens `angreFåttJobb` forutsetter at kalleren har låst.
- `finnStatuskrevIkkeSlettetJobbsøker` ser ut som to navn som er slått sammen, og `registrerSvar` bruker ikke returverdien. [Jobbsøkerstatus](jobbsoker-statuser.md) kaller den `krevIkkeSlettetJobbsøker`.
- Avlys og fullfør sender valideringen som en lambda inn i en hjelpefunksjon med seks parametere. Det er vanskelig å se at valideringen skjer under lås. `låsTreff` leser `rekrutteringstreff_id` uten å returnere den, så hjelpefunksjonen henter den på nytt med `hentRekrutteringstreffDbId`.
- `låsTreff`, `EierService`, `EierRepository` og `FormidlingService.slett` kaster Javalins `NotFoundResponse` eller `BadRequestResponse`. [Prinsippene](../2-arkitektur/prinsipper.md) sier at vi skal bruke unntak som `ExceptionMapping` håndterer. `RekrutteringstreffIkkeFunnetException` finnes allerede.
- Regelen om at siste eier ikke kan slettes, står både i `EierService.slettEier` og i `EierRepository.slett`.
- `EierService.leggTilEierMedKontor(connection, …)` er offentlig, men bare varianten uten `connection` bruker den. `EierRepository.leggTil` og `slett` har varianter som starter egen transaksjon, og bare testene bruker dem.

## Tester

`TestDatabase` bruker standardnivået i PostgreSQL, `READ COMMITTED`. Produksjonspoolen bruker `REPEATABLE READ`. Bare `JobbsøkerstatusSamtidighetTest` og `TreffgjennomføringKomponentTest` setter `REPEATABLE READ` selv.

Disse samtidighetstestene kjører derfor med et annet nivå enn produksjon:

- `EierRepositoryTest`: `samtidige tillegg av samme eier lager én eierrad og én eierhendelse` og `samtidige slettinger beholder siste eier`
- `JobbsøkerInnloggetBorgerTest`: `samtidige svar ja kall håndteres konsistent`
- `InvitasjonFeilhåndteringTest`: `samtidige invitasjoner registrerer kun én INVITERT-hendelse`
- `OppmøteServiceTest`: `samtidige oppmøteregistreringer gir ulike deltakernummer`

Ingen tester dekker samtidige statusoverganger på treffet (publiser, avpubliser, gjenåpne, slett), registrer endring, legg til jobbsøkere, endre behov, aktuell-status eller oppretting av formidlinger.

## Innført i #229

- `executeInLockingTransaction` kjører `READ COMMITTED`. `medLåstTreff`, svar, invitasjon og formidling bruker den.
- Svar og invitasjon låser jobbsøkeren før de sjekker synlighet og status.
- `inviter` låser jobbsøkerne i fast rekkefølge.
- Oppmøte låser jobbsøkeren etter treffet.
- Avlys og fullfør låser treffet og alle jobbsøkerne, og validerer under lås.
- `JobbsøkerstatusSamtidighetTest` kjører med `REPEATABLE READ`, som produksjon.

## Tiltak

Hvert steg kan være én pull request. Ta steg 1 før steg 2. I dag blir to samtidige publiseringer av og til HTTP 500 under `REPEATABLE READ`. Bytter vi til `READ COMMITTED` før låsen er på plass, blir det i stedet to hendelser.

### Steg 1: Lås skrivingene som mangler lås

- Publiser, avpubliser, gjenåpne, slett treff og oppdater treff bruker `medLåstTreff` og leser treffet med `hent(connection, treffId)`. `markerSlettet` leser jobbsøkere og arbeidsgivere på samme tilkobling.
- `registrerEndring` følger mønsteret fra avlys. Den låser treffet, sjekker status og låser så alle jobbsøkerne. Statussjekken skal ligge i servicen, ikke i controlleren.
- `leggTilJobbsøkere` kaller kandidatsøk først, tar så `medLåstTreff` og leser eksisterende og slettede jobbsøkere på nytt under låsen. Vurder en unik indeks på `(rekrutteringstreff_id, fodselsnummer)` for jobbsøkere som ikke er slettet, når vi har sjekket at produksjon ikke har duplikater.
- `oppdaterBehov` bruker `medLåstTreff`.
- `EierService` bruker `medLåstTreff`. Fjern `forUpdate` og låsen i `EierRepository.leggTil`, og skriv om testen `forUpdate låser både treffraden og eierradene`.
- `lagreFormidlinger` bruker `medLåstTreff` og sjekker `sperret` og eksisterende formidlinger på nytt. Stillingen som blir til overs ved dobbel innsending, er en egen sak. Den krever et reservasjonssteg eller et idempotent stilling-API.
- Avklar reglene:
  - Fra hvilke statuser kan et treff avpubliseres?
  - Kan et avlyst eller fullført treff endres?
  - Kan vi invitere, og kan jobbsøkere svare, når treffet ikke er publisert?
  - Kan vi legge til jobbsøkere på et avlyst eller fullført treff?
- Skriv samtidighetstester for det som endres, med `REPEATABLE READ` på poolen.

### Steg 2: `READ COMMITTED` som standard

Gjør dette rett etter steg 1.

- Sett `TRANSACTION_READ_COMMITTED` i `InfrastructureContext` og i `TestDatabase`.
- Lag en lesefunksjon med `REPEATABLE READ` og `readOnly`, og bruk den i de fem lesetransaksjonene: `JobbsøkerSokRepository`, `JobbsøkerFormidlingSokRepository`, `FormidlingRepository` og to i `TreffgjennomføringService`. Vurder samtidig å flytte de tre første til servicelaget.
- Fjern `executeInLockingTransaction`. Alle skrivinger bruker `executeInTransaction`.
- Bytt `FOR UPDATE` til `FOR NO KEY UPDATE` i låsefunksjonene.
- Fjern `REPEATABLE READ`-overstyringen i `JobbsøkerstatusSamtidighetTest` og `TreffgjennomføringKomponentTest`.

Dette fjerner `40001` for aktuell-status, synlighet, hendelsene fra Kafka, innlegg og KI-logg uten flere endringer.

### Steg 3: Lesbarhet

- Samle låsefunksjonene i én fil: `låsTreff`, `låsJobbsøkere(treffId, personTreffIder)` og `låsAlleJobbsøkerePåTreff`.
- La `inviter` låse alle jobbsøkerne med `låsJobbsøkere` i stedet for én og én.
- Gjør `hentStatus`, `hentSlettestatus`, `hentAktuellForTreffStatusForOppdatering` og `EierRepository.hent` til rene lesinger. Fjern `ForOppdatering` fra navnet.
- Fjern `JobbsøkerService.låsJobbsøker`. Den som starter transaksjonen, kaller låsefunksjonen direkte.
- La kalleren låse både før `registrerFåttJobb` og før `angreFåttJobb`.
- Gi `finnStatuskrevIkkeSlettetJobbsøker` navnet `krevIkkeSlettetJobbsøker`.
- Skriv avlys og fullfør uten `valider`-lambdaen. Hver av dem kan låse og validere selv, og så kalle en felles funksjon som skriver hendelsene.
- La `låsTreff` kaste `RekrutteringstreffIkkeFunnetException`, og oppdater testene som venter `NotFoundResponse`.
- Fjern regelen om siste eier fra `EierRepository.slett`, gjør `leggTilEierMedKontor(connection, …)` privat, og flytt variantene i `EierRepository` som starter egen transaksjon, til testkoden.
- La `RekrutteringstreffScheduler` fange feil per treff, så ett treff som feiler, ikke stopper de andre.
- Legg til en test som leser kildekoden under `src/main` og bare tillater `FOR UPDATE`, `FOR NO KEY UPDATE` og `FOR SHARE` i låsefilen.
- Vurder bevis-typen fra regel 6.

### Steg 4: Flytt strategien

- Lag `docs/2-arkitektur/transaksjoner.md` fra strategidelen.
- Oppdater [database.md](../2-arkitektur/database.md), [treffgjennomforing.md](../2-arkitektur/treffgjennomforing.md) og [jobbsoker-statuser.md](jobbsoker-statuser.md), som beskriver dagens isolasjonsnivå.
- Slett dette dokumentet.

🔴 Rød sone: samtidighet, isolasjonsnivå og låserekkefølge. Den som gjør endringene, bør skrive samtidighetstestene selv og forstå hvorfor de feiler før rettingen.
