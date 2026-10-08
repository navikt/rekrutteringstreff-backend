# Låsing og samtidighet

**Status:** Arbeidsdokument. Strategien står i [Transaksjoner og låsing](../2-arkitektur/transaksjoner.md). Her vurderer vi koden opp mot strategien og samler det som ikke er avklart. Når de fire forretningsreglene under «Åpne spørsmål» er avklart, sletter vi dokumentet.

## Kort fortalt

- Koden i branchen `fiks-transaksjonslåsing` følger strategien per 08.10.2026.
- Publiser, avpubliser, gjenåpne, slett treff, oppdater treff, registrer endring, legg til jobbsøkere, endre behov og lagring av formidlinger låste ikke før denne branchen.
- `låsJobbsøkere` sjekker at jobbsøkerne hører til treffet i stien. Invitasjon og svar på vegne av jobbsøker gir derfor 404 for en jobbsøker fra et annet treff, og skriver ingenting.
- Fire forretningsregler er ikke avklart. Til vi har bestemt oss, følger koden reglene slik de er i dag.

## Vurdering av koden

✅ følger strategien. ⚠️ en kjent svakhet eller en forretningsregel som ikke er avklart. Alle skrivinger kjører `READ COMMITTED`.

### Treffet

| Operasjon | Lås | Sjekker under lås | Vurdering |
| --- | --- | --- | --- |
| Opprett treff | Ingen | Ikke relevant | ✅ Nye rader som ingen andre skriver til ennå. |
| Publiser og gjenåpne | `medLåstTreff` | Status | ✅ |
| Avlys og fullfør | `medLåstTreff`, så `låsAlleJobbsøkerePåTreff` i `avsluttTreff` | Status. Fullfør sjekker også sluttidspunktet. | ✅ |
| Fullfør fra `RekrutteringstreffScheduler` | Som fullfør | Som fullfør | ✅ Feiler ett treff, fortsetter kjøringen med de andre. |
| Avpubliser | `medLåstTreff` | Ingen statussjekk | ⚠️ Kan sette et avlyst eller fullført treff tilbake til utkast. Se åpent spørsmål 1. |
| Slett treff (`markerSlettet`) | `medLåstTreff` | Status `UTKAST` og ingen jobbsøkere. Leser arbeidsgiverne på samme tilkobling. | ✅ |
| Oppdater treff | `medLåstTreff` | Ingen statussjekk | ⚠️ Kan endre et avlyst eller fullført treff. Se åpent spørsmål 2. |
| Registrer endring (`registrerEndring`) | `medLåstTreff`, så `låsAlleJobbsøkerePåTreff` | Status `PUBLISERT`. Velger hendelser ut fra jobbsøkerstatus. | ✅ |
| Eiere: legg til og slett (`EierService`) | `medLåstTreff` | Eierne og regelen om siste eier | ✅ |
| Innlegg og KI-logg | Ingen | Ikke relevant | ✅ FK-sjekken ved nye rader venter ikke på trefflåsen (forsøk 3 i [transaksjoner.md](../2-arkitektur/transaksjoner.md#bekreftet-mot-postgresql)). |

### Jobbsøkere

| Operasjon | Lås | Sjekker under lås | Vurdering |
| --- | --- | --- | --- |
| Legg til jobbsøkere (`leggTilJobbsøkere`) | `medLåstTreff` etter kandidatsøk | Eksisterende og slettede jobbsøkere | ✅ Sjekker ikke treffstatus. Se åpent spørsmål 4. |
| Invitasjon (`inviter`) | `låsJobbsøkere` for hele lista | Synlighet og status | ✅ Sjekker ikke treffstatus. Se åpent spørsmål 3. |
| Svar fra jobbsøker | Slår opp jobbsøkeren på fødselsnummer, så `låsJobbsøkere` | Synlighet, slettet og gjeldende svar | ✅ Sjekker ikke treffstatus. Se åpent spørsmål 3. |
| Svar på vegne av jobbsøker | `låsJobbsøkere` med treffet fra stien | Synlighet, slettet og gjeldende svar | ✅ Som svar fra jobbsøker. |
| Fått jobb og angring (`registrerFåttJobb` og `angreFåttJobb`) | Kalleren låser med `låsJobbsøkere` | Status | ✅ |
| Oppmøte (`OppmøteService`) | `medLåstTreff` gjennom `TreffgjennomføringWriter.skriv`, så `låsJobbsøkere` | Gjeldende oppmøte og registreringer | ✅ |
| Slett jobbsøker (`markerSlettet`) | `medLåstTreff`, så `låsJobbsøkere` | Status `LAGT_TIL` og ingen registreringer | ✅ |
| Aktuell-status (`endreAktuellForTreffStatus`) | `låsJobbsøkere` | Gjeldende aktuell-status | ✅ |
| Synlighet fra Kafka (`oppdaterSynlighetFraEvent` og `oppdaterSynlighetFraNeed`) | Ingen. Regelen står i `WHERE`. | I `UPDATE` | ✅ Venter på låser på samme jobbsøker i stedet for å feile med `40001`. |
| Hendelser fra Kafka som ikke endrer status (`registrerAktivitetskortOpprettelseFeilet` og `registrerMinsideVarselSvar`) | Ingen | Ikke relevant | ✅ |
| `JobbsøkerhendelserScheduler` | Ingen | Ikke relevant | ✅ Skriver bare egne pollingrader. |

### Arbeidsgivere, formidling og treffgjennomføring

| Operasjon | Lås | Sjekker under lås | Vurdering |
| --- | --- | --- | --- |
| Treffgjennomføring (`TreffgjennomføringWriter.skriv`): steg, møteoppsett, rom, interesse, intervjufordeling og vurdering | `medLåstTreff` | Treffkonteksten (`krevKontekst`) | ✅ |
| Arbeidsgiver: legg til, legg til med behov og slett | `medLåstTreff` | Slettet arbeidsgiver som kan reaktiveres, siste arbeidsgiver og registreringer | ✅ |
| Endre behov (`oppdaterBehov`) | `medLåstTreff` | At arbeidsgiveren ikke er slettet, i upsert-SQL-en | ✅ |
| Opprett formidling: lagring (`lagreFormidlinger`) | `medLåstTreff`, så `låsJobbsøkere` | Arbeidsgiveren, at jobbsøkerne finnes, adressebeskyttelse og eksisterende formidlinger | ⚠️ Låsingen er riktig. Ved dobbel innsending blir stillingen fra innsendingen som får låsen sist, liggende ubrukt. |
| Opprett formidling: fått jobb | `låsJobbsøkere`, så `registrerFåttJobb` | Status | ✅ |
| Slett formidling | `låsJobbsøkere`, så `angreFåttJobb` | Status | ✅ |

## Lesbarhet

Alle låsene står i `låsing.kt`, og ingen lesefunksjoner låser. To ting gjenstår:

- `EierService`, `FormidlingService.slett` og `RekrutteringstreffRepository` kaster fortsatt Javalins `NotFoundResponse` og `BadRequestResponse`. [Prinsippene](../2-arkitektur/prinsipper.md) sier at vi skal bruke unntak som `ExceptionMapping` håndterer. Vi lot dem stå for ikke å endre HTTP-svarene i denne branchen. «Kan ikke slette siste eier» har heller ikke et domeneunntak som gir 400.
- `JobbsøkerSokRepository`, `JobbsøkerFormidlingSokRepository` og `FormidlingRepository` starter egne lesetransaksjoner. Vi kan flytte dem til servicelaget.

## Tester

`TestDatabase` og produksjon bruker samme isolasjonsnivå, og ingen tester overstyrer det.

Nye samtidighetstester viser at operasjonen venter på låsen og ser endringen etterpå:

- `LåsingTest`: publiser, slett treff, registrer endring, legg til jobbsøkere, endre behov og aktuell-status
- `FormidlingServiceTest`: dobbel innsending, og en jobbsøker som blir slettet mens formidlingen venter
- `EierRepositoryTest`: sletting av eier mens en annen eier blir lagt til

For hver av testene har vi fjernet låsen, flyttet den eller fjernet sjekken under den, og sett at testen feiler. Samtidighetstestene fra før ligger i `JobbsøkerstatusSamtidighetTest`, `EierRepositoryTest`, `InvitasjonFeilhåndteringTest`, `JobbsøkerInnloggetBorgerTest` og `OppmøteServiceTest`.

`bare låsefila låser rader` i `LåsingTest` leser kildekoden under `src/main/kotlin`. Den feiler hvis `FOR UPDATE`, `FOR NO KEY UPDATE`, `FOR SHARE` eller `FOR KEY SHARE` står i en annen fil enn `låsing.kt`.

`JobbsøkerTest` sjekker at invitasjon og svar på vegne av jobbsøker gir 404 for en jobbsøker fra et annet treff, og ikke skriver noe.

Gjenåpne, avpubliser og oppdater treff har ingen egen samtidighetstest. De bruker samme mønster som publiser.

## Innført i #229

Dette kom før `fiks-transaksjonslåsing`. Noe av det er endret siden, se neste avsnitt.

- `executeInLockingTransaction` kjørte `READ COMMITTED`. `medLåstTreff`, svar, invitasjon og formidling brukte den.
- Svar og invitasjon låser jobbsøkeren før de sjekker synlighet og status.
- `inviter` låser jobbsøkerne i fast rekkefølge.
- Oppmøte låser jobbsøkeren etter treffet.
- Avlys og fullfør låser treffet og alle jobbsøkerne, og validerer under lås.
- `JobbsøkerstatusSamtidighetTest` kjørte med `REPEATABLE READ`, som produksjon gjorde da.

## Endret i `fiks-transaksjonslåsing`

Steg 1 til 3 under «Tiltak» er gjort. Dette endrer oppførselen:

- Invitasjon og svar på vegne av jobbsøker gir 404 og skriver ingenting når jobbsøkeren hører til et annet treff. Er én slik jobbsøker med i en invitasjon, blir ingen invitert.
- Registrer endring på et treff som ikke er publisert, gir 409 i stedet for 400. Sjekken ligger i servicen.
- Lagring av formidling sjekker arbeidsgiveren og jobbsøkerne på nytt under låsen. Feiler sjekken, gir den samme feilkode som når den feiler før stillingen opprettes. En jobbsøker som blir slettet mens stillingen opprettes, gir derfor 400 i stedet for 404. Jobbsøkere som har fått formidling hos arbeidsgiveren i mellomtiden, hopper den over.
- `leggTilJobbsøkere` returnerer hvor mange som faktisk ble lagt til.
- Låsene kaster domeneunntak. Et treff som ikke finnes, gir `RekrutteringstreffIkkeFunnetException`, også når vi legger til eller sletter eiere. Sletting av en jobbsøker som ikke finnes på treffet, gir `JobbsøkerIkkeFunnetException`. Begge gir 404 som før, men svaret følger nå `ProblemDetails`.
- `RekrutteringstreffScheduler` fortsetter med neste treff når ett feiler.

Ellers i koden:

- `executeInLockingTransaction` er fjernet. Skrivinger bruker `executeInTransaction` eller `medLåstTreff`, og lesetransaksjoner bruker `executeInReadOnlyTransaction`.
- `låsing.kt` erstatter `treffLås.kt` og låsene i `JobbsøkerRepository` og `EierRepository`.
- `hentStatus`, `hentSlettestatus`, `hentAktuellForTreffStatus` (før `hentAktuellForTreffStatusForOppdatering`) og `EierRepository.hent` låser ikke lenger.
- Avlys og fullfør validerer selv og kaller `avsluttTreff`, i stedet for å sende en `valider`-lambda.
- `finnStatuskrevIkkeSlettetJobbsøker` heter `krevIkkeSlettetJobbsøker`, og `JobbsøkerService.låsJobbsøker` er fjernet.
- Regelen om siste eier står bare i `EierService`. `EierService.leggTilEierMedKontor(connection, …)` er fjernet. Variantene i `EierRepository` som startet egen transaksjon, ligger nå i testkoden.

## Åpne spørsmål

Koden følger reglene slik de er i dag, til vi har avklart disse. Svaret på spørsmål 1, 2 og 4 gir en statussjekk under låsen vi allerede tar. Spørsmål 3 endrer også låsingen.

1. Fra hvilke statuser kan et treff avpubliseres? I dag fra alle. Frontend kaller ikke endepunktet. Testen `Endepunkter som kun legger til hendelse` i `RekrutteringstreffTest` avpubliserer et utkast og forventer 200.
2. Kan et avlyst eller fullført treff oppdateres? I dag ja. Frontend viser «Rediger» bare for utkast og publiserte treff.
3. Kan vi invitere, og kan jobbsøkere svare, når treffet ikke er publisert? I dag ja, uansett treffstatus. [Ordlista](../1-oversikt/ordliste.md) sier at jobbsøkere kan inviteres når treffet er publisert, og bruker-frontenden skjuler svarknappene når treffet er avlyst. Mange tester inviterer og svarer uten å publisere treffet først. Skal vi sjekke treffstatus, må svar og invitasjon låse treffet før jobbsøkerne (regel 5 i [Transaksjoner og låsing](../2-arkitektur/transaksjoner.md)).
4. Kan vi legge til jobbsøkere på et avlyst eller fullført treff? I dag ja.

## Gjenstår

- Unik indeks på jobbsøker `(rekrutteringstreff_id, fodselsnummer)` for rader som ikke er slettet. Låsen hindrer duplikater fra appen i dag, og indeksen stopper også feil i ny kode. Sjekk først at produksjon ikke har duplikater.
- Ved dobbel innsending av formidling blir den ene stillingen liggende ubrukt. Det krever et reservasjonssteg før stillingen opprettes, eller et idempotent stilling-API.
- Javalin-unntakene og lesetransaksjonene i repositoriene (se «Lesbarhet»).
- Frontend: `/rediger` sjekker ikke treffstatus, og autolagringen er på for avlyste treff. `useRepubliser` kaller `registrerEndring` også for fullførte treff og logger bare feilen. Avklar sammen med spørsmål 2.

## Tiltak

Steg 1 til 3 er gjort i `fiks-transaksjonslåsing`:

1. ✅ Lås skrivingene som manglet lås.
2. ✅ `READ COMMITTED` som standard, `executeInReadOnlyTransaction` for lesing og `FOR NO KEY UPDATE`.
3. ✅ Lesbarhet: én låsefil, lesefunksjoner uten lås, nye navn, domeneunntak fra låsene og testen som stopper låser utenfor låsefila.

### Steg 4: Flytt strategien

- ✅ Lag [transaksjoner.md](../2-arkitektur/transaksjoner.md) av strategien og forsøkene mot PostgreSQL.
- ✅ Pek lenken i [jobbsoker-statuser.md](jobbsoker-statuser.md) til det nye dokumentet.
- Avklar de åpne spørsmålene og legg inn statussjekkene.
- Slett dette dokumentet.

Eldre planer nevner fortsatt `FOR UPDATE` og `treffLås.kt`: [eiere-og-kontorer-egen-tabell.md](eiere-og-kontorer-egen-tabell.md) og [treffgjennomforing-domeneoppdeling.md](workop/treffgjennomforing-domeneoppdeling.md). De viser hva vi planla da, og vi lar dem stå.

🔴 Rød sone: samtidighet, isolasjonsnivå og låserekkefølge. Den som endrer låsingen, bør skrive samtidighetstesten selv og se den feile uten låsen.
