# Låsing og samtidighet

**Status:** Strategien er et forslag. Deler av den er innført i `refactor-status` (se «Gjort i refactor-status»). Vurderingen gjelder koden per 07.10.2026.

Dokumentet beskriver hvordan skrivende operasjoner bør låse rader i PostgreSQL. Det vurderer også dagens kode opp mot strategien. Les [Database](../2-arkitektur/database.md) for skjema og [Jobbsøkerstatus](jobbsoker-statuser.md) for statusreglene som låsene beskytter.

## Kort fortalt

- Treffgjennomføring og arbeidsgivere følger strategien gjennom `medLåstTreff`.
- Statusskrivinger på jobbsøkere (svar, invitasjon, fått jobb, oppmøte) kjører nå med `READ COMMITTED` og låser jobbsøkeren før validering.
- Aktuell-status og eierlåsene kjører fortsatt under `REPEATABLE READ`. Der gir samtidige kall trolig HTTP 500 i stedet for å vente og fortsette. De fleste testene fanger ikke det, fordi testdatabasen kjører `READ COMMITTED`.
- Fem ulike låsemønstre gjør det vanskelig å se hvor koden låser. `hentStatus` låser uten at navnet sier det.

## Strategi

### 1. Lås først, valider etterpå

Ta låsen før du leser verdiene du bestemmer ut fra: status, synlighet, svar, registreringer. En sjekk før låsen kan være utdatert når låsen er tatt.

### 2. Bare låsefunksjoner låser

`FOR UPDATE` står bare i låsefunksjonene. Repository-funksjoner som leser, låser aldri. Da ser du låsingen i servicekoden, og ingen lesing tar en lås som ikke var tiltenkt.

| Låsefunksjon | Låser | Finnes i dag |
| --- | --- | --- |
| `medLåstTreff` / `Connection.låsTreff` | Treffraden | Ja, `treffLås.kt` |
| `medLåstJobbsøker` / `Connection.låsJobbsøker` | Én jobbsøkerrad | Nei, bare `JobbsøkerRepository.låsJobbsøker` uten transaksjon |
| `Connection.låsJobbsøkere` | Flere jobbsøkerrader, sortert på id | Nei |

### 3. Skrivinger med lås kjører `READ COMMITTED`

Poolen har `REPEATABLE READ` som standard. Under `REPEATABLE READ` tar transaksjonen et øyeblikksbilde ved første spørring. Venter transaksjonen på en lås, og den andre transaksjonen endrer eller sletter raden før commit, avbryter PostgreSQL med `40001 could not serialize access due to concurrent update`. Det samme gjelder `INSERT ... ON CONFLICT DO UPDATE` mot en rad som en annen transaksjon nettopp har lagt inn. Se [PostgreSQL 13.2.2](https://www.postgresql.org/docs/current/transaction-iso.html#XACT-REPEATABLE-READ).

Under `READ COMMITTED` får hver spørring et nytt øyeblikksbilde. Etter at låsen er tatt, ser neste spørring det den andre transaksjonen lagret. Det er dette `medLåstTreff` bygger på.

Isolasjonsnivået må settes før første spørring i transaksjonen. Den som starter transaksjonen, må derfor velge nivået. Låsefunksjonen kan ikke gjøre det. Derfor er strategien å bruke wrappere (`medLåstTreff`, `medLåstJobbsøker`) som både starter transaksjonen og tar låsen.

Lesende GET-kall beholder `REPEATABLE READ`, slik at én respons ser ett konsistent øyeblikksbilde.

Alternativet er å beholde `REPEATABLE READ` og prøve på nytt ved `40001`. Det krever at hele transaksjonen kan kjøres på nytt uten bivirkninger. Det gjelder ikke når det skjer HTTP-kall før transaksjonen, som i `FormidlingService`. Vi velger derfor `READ COMMITTED`.

### 4. Fast låserekkefølge

1. Treff
2. Jobbsøkere, sortert på `id`
3. Andre rader (eiere, formidlinger, rom)

Lås aldri i motsatt rekkefølge. Skal du låse flere jobbsøkere, gjør du det i én spørring:

```sql
SELECT 1 FROM jobbsoker WHERE id = ANY(?) ORDER BY id FOR UPDATE
```

To transaksjoner som låser de samme radene i ulik rekkefølge, kan låse hverandre fast. PostgreSQL bryter da den ene med `40P01 deadlock detected`.

### 5. Velg treff- eller jobbsøkerlås ut fra hva som endres

- **Trefflås** når operasjonen endrer tilstand som deles på treffet: rom, deltakernummer, arbeidsgivere, eiere, eller flere jobbsøkere samtidig.
- **Jobbsøkerlås** når operasjonen bare gjelder én person: svar, fått jobb, aktuell-status.

Trefflåsen setter alle skrivinger på treffet i kø. Det er enkelt og trygt, men gir mer venting. Med få samtidige brukere per treff er ventetiden lav. Jobbsøkerlåsen gir mer samtidighet, men da må låserekkefølgen følges.

### 6. Bevis på lås i signaturen

Funksjoner som krever lås, tar et bevis som parameter i stedet for en `Connection`:

```kotlin
class LåstJobbsøker internal constructor(
    val connection: Connection,
    val personTreffId: PersonTreffId,
)

fun <T> DataSource.medLåstJobbsøker(personTreffId: PersonTreffId, block: (LåstJobbsøker) -> T): T =
    executeInTransaction(transactionIsolation = Connection.TRANSACTION_READ_COMMITTED) { connection ->
        block(connection.låsJobbsøker(personTreffId))
    }

fun registrerFåttJobb(låst: LåstJobbsøker, navIdent: String) { ... }
```

Kompilatoren stopper et kall uten lås, og funksjonen trenger ikke låse på nytt. Prisen er en ekstra type og endrede signaturer. Innfør dette etter at punkt 1 til 5 er på plass.

### 7. Ingen eksterne kall under lås

HTTP-kall og Kafka-sending skjer før eller etter transaksjonen med lås. `FormidlingService` gjør dette riktig: den kaller stilling- og kandidatliste-API-et først, og sjekker jobbsøkeren på nytt under lås.

### 8. Samtidighetstester bruker samme isolasjonsnivå som produksjon

Tester av samtidige kall må bruke `REPEATABLE READ` som standard på poolen. Ellers tester de ikke det som kjører i produksjon.

## Vurdering av dagens kode

✅ følger strategien. ⚠️ avvik uten feil data. ❌ avvik som trolig gir feil eller HTTP 500.

| Operasjon | Lås | Isolasjon | Validering | Vurdering |
| --- | --- | --- | --- | --- |
| Treffgjennomføring (`TreffgjennomføringWriter.skriv`): rom, møteplan | `medLåstTreff` | RC | Etter lås, i `krevKontekst` | ✅ |
| Oppmøte (`OppmøteService.oppdaterOppmøte`) | `medLåstTreff`, så `låsJobbsøker` | RC | Etter lås | ✅ |
| Arbeidsgiver: legg til, endre behov, slett (`ArbeidsgiverService`) | `medLåstTreff` | RC | Etter lås | ✅ |
| Slett jobbsøker (`JobbsøkerService.markerSlettet`) | `medLåstTreff`, så `hentSlettestatus` med `FOR UPDATE OF j` | RC | Etter lås | ✅ Riktig rekkefølge. ⚠️ Jobbsøkerlåsen er skjult i en lesefunksjon. |
| Svar fra eier eller borger (`JobbsøkerService.registrerSvar`) | `låsJobbsøker`, så `hentStatus` med `FOR UPDATE` | RC | Etter lås | ✅ ⚠️ Dobbel lås. |
| Invitasjon (`JobbsøkerService.inviter`) | `låsJobbsøker` én og én, sortert på id | RC | Etter lås | ✅ ⚠️ Dobbel lås. Én spørring for alle ville vært enklere. |
| Fått jobb (`FormidlingService.opprettFormidling` → `registrerFåttJobb`) | `låsJobbsøker`, så `hentStatus` én gang | RC | Etter lås | ✅ ⚠️ Dobbel lås. |
| Slett formidling (`FormidlingService.slett` → `angreFåttJobb`) | `låsJobbsøker` først | RC | Etter lås | ✅ ⚠️ `angreFåttJobb` låser på nytt gjennom `hentStatus`. |
| Aktuell-status (`JobbsøkerService.endreAktuellForTreffStatus`) | `hentAktuellForTreffStatusForOppdatering` | RR | I samme spørring | ✅ Navnet viser låsen. ⚠️ `40001`. |
| Eiere: legg til og slett (`EierService`) | `EierRepository.hent(forUpdate = true)`: treff, så eiere | RR | Etter lås | ✅ Riktig rekkefølge. ❌ `40001` når to kall endrer samme eierrader. ⚠️ `FOR UPDATE` også i `EierRepository.leggTil`. |
| Synlighet fra event og need (`oppdaterSynlighetFraEvent`/`FraNeed`) | Ingen eksplisitt lås. `UPDATE` låser radene til commit. | Autocommit | Ikke relevant | ✅ Kort transaksjon. |

RC er `READ COMMITTED`, RR er `REPEATABLE READ`.

### Låsemønstre i bruk

| Mønster | Eksempel | Ser du låsen ved kallet? |
| --- | --- | --- |
| Wrapper som starter transaksjon og låser | `medLåstTreff` | Ja |
| Egen låsemetode | `JobbsøkerRepository.låsJobbsøker` | Ja |
| Lesing med `ForOppdatering` i navnet | `hentAktuellForTreffStatusForOppdatering` | Ja |
| Lesing som låser uten å si det | `hentStatus`, `hentSlettestatus` | Nei |
| Boolsk parameter | `EierRepository.hent(forUpdate = true)` | Ja, ved kallstedet |

Låsene i lesefunksjonene er ikke der for ytelse. En spørring som både leser og låser, sparer én rundtur til databasen. Det viktige er at verdien beslutningen bygger på, leses under lås.

### Tester

`TestDatabase` bruker standardnivået til PostgreSQL, `READ COMMITTED`. Produksjonspoolen i `InfrastructureContext` bruker `REPEATABLE READ`. Bare `TreffgjennomføringKomponentTest` setter `REPEATABLE READ` selv.

Disse testene viser derfor oppførselen under `READ COMMITTED`, ikke i produksjon:

- `JobbsøkerInnloggetBorgerTest`: `samtidige svar ja kall håndteres konsistent`
- `InvitasjonFeilhåndteringTest`: `samtidige invitasjoner registrerer kun én INVITERT-hendelse`
- `EierRepositoryTest`: `samtidige tillegg av samme eier ...` og `samtidige slettinger beholder siste eier`

`JobbsøkerstatusSamtidighetTest` kjører svar og oppmøte samtidig på samme person med `REPEATABLE READ` på poolen, som i produksjon.

### Usikkerhet

Funnene om `40001` er utledet fra PostgreSQL-dokumentasjonen og kodelesing. `ExceptionMapping` gjør `SQLException` om til HTTP 500, unntatt `23503`, så en `40001` blir trolig en 500-feil. Deadlock i `inviter` er ikke kjørt.

## Gjort i refactor-status

- `executeInLockingTransaction` i `transactionManager.kt` kjører med `READ COMMITTED`. `medLåstTreff` bruker den.
- Svar, invitasjon, fått jobb og sletting av formidling bruker `executeInLockingTransaction`.
- `registrerSvar` og `inviter` låser jobbsøkeren før synlighet og status sjekkes.
- `inviter` låser i fast rekkefølge (sortert på id).
- `OppmøteService` låser jobbsøkeren etter treffet, så oppmøte og svar ikke skriver status samtidig.
- `registrerFåttJobb` leser statusen én gang.
- `JobbsøkerstatusSamtidighetTest` dekker samtidig svar og oppmøte.

## Tiltak

Gjør tiltakene i egen branch og i denne rekkefølgen:

1. **Bekreft funnene for resten.** Kjør testene over med `REPEATABLE READ` på poolen, slik `TreffgjennomføringKomponentTest` gjør. Forvent 500 eller feilende tester for eiere.
2. **Innfør `medLåstJobbsøker`** som starter transaksjonen og låser. Flytt `registrerSvar`, `registrerFåttJobb`, `FormidlingService.slett` og `endreAktuellForTreffStatus` over.
3. **Bruk `READ COMMITTED` i `EierService`** og `endreAktuellForTreffStatus`, for eksempel med `executeInLockingTransaction`.
4. **Gjør `hentStatus` til en ren lesing.** Flytt `FOR UPDATE` til låsefunksjonene. `hentSlettestatus` og `hentAktuellForTreffStatusForOppdatering` kan bli rene lesinger etter en eksplisitt lås.
5. **Lås alle i én spørring i `inviter`**, sortert på `id`.
6. **La samtidighetstestene kjøre med samme isolasjonsnivå som produksjon**, for eksempel ved å sette `transactionIsolation` i `TestDatabase`.
7. **Legg til en enkel arkitekturtest** som feiler hvis `FOR UPDATE` står utenfor låsefilene.
8. **Vurder bevis-typen** fra punkt 6 i strategien.
9. **Oppdater avsnittet om låsing** i [database.md](../2-arkitektur/database.md).

🔴 Rød sone: samtidighet, isolasjonsnivå og låserekkefølge. Den som gjør endringene, bør skrive testene i tiltak 1 selv og forstå hvorfor de feiler.
