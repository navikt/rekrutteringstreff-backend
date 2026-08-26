# Plan: Domeneoppdeling av treffgjennomføring i backend

**Status:** Ferdig. Alle faser implementert  
**Omfang:** `rekrutteringstreff-api`, pakken `no.nav.toi.treffgjennomføring`  
**Gjelder ikke:** frontendkontrakten. Alle forslagene her skal være usynlige for `rekrutteringsbistand-frontend`.

Dette er et vurderingsdokument. Det svarer på fire spørsmål:

1. Bør oppmøte flyttes ut av treffgjennomføring og inn i jobbsøker-domenet, med oppmøtestatus i tabell?
2. Bør treffgjennomføring deles i **treffgjennomføring** og **oppfølging**?
3. Bør treffgjennomføring deles videre i subdomener for **interesse** og **romfordeling**?
4. Finnes det andre oppdelinger som er mer verdt å gjøre?

Svaret på om en oppdeling er fornuftig avhenger av hvor mange avhengigheter som krysser
grensa. Derfor begynner dokumentet med et avhengighetskart, og bruker det som grunnlag
for hver anbefaling.

---

## Konklusjon

| # | Forslag | Anbefaling | Kort begrunnelse |
| - | ------- | ---------- | ---------------- |
| 1 | Flytt oppmøte til jobbsøker-domenet, med status i tabell | **Ja – gjør dette først** | Avledningslogikken er allerede duplisert to steder. Kolonnen fjerner duplikatet og åpner for filtrering på oppmøte i jobbsøkersøket. |
| 2 | Del i `treffgjennomføring` og `oppfølging` | **Ja** | Oppfølging (vurdering) har null utgående avhengigheter til resten. Reneste snittet i hele domenet. |
| 3 | Egne subdomener for interesse og romfordeling | **Nei, ikke slik** | Interesse og intervjufordeling skriver til hverandre i samme transaksjon. Romfordeling hører sammen med møteoppsett og rotasjon, ikke alene. |
| 4 | Alternativ oppdeling: **møteplan** og **matching** | **Ja – valgt i stedet for #3** | Følger de faktiske skrivetransaksjonene, og skiller samtidig WorkOp-spesifikk kode fra kode alle treff bruker. |
| 5 | Eget lesetjeneste-lag for aggregatet | **Ja – forutsetning for #2 og #4** | Alle åtte endepunkter returnerer hele aggregatet. Uten et samlende leselag blir enhver oppdeling reversert av lesevegen. |

Rekkefølgen betyr noe: **1 → 5 → 2 → 4**. Steg 5 er den tekniske forutsetningen for
at 2 og 4 skal gi gevinst i stedet for bare flere filer.

---

## Beslutninger

Avklart. Resten av dokumentet forutsetter disse valgene.

| Tema | Valg |
| ---- | ---- |
| Eierskap til oppmøte | `OppmøteService` i `jobbsoker/oppmøte` eier hele oppmøteoperasjonen og gjør **statusoppdatering, hendelse og deltakernummer i samme transaksjon**. Ingen delvis registrering skal kunne bli stående. |
| Låsen | Sikres i jobbsøker, men som en **delt låseprimitiv på `rekrutteringstreff`-raden** som begge domenene tar. Analysen under viser at låsen trengs fire steder, ikke bare i oppmøte. |
| Feilhåndtering | **Enklest mulig, minst mulig kode.** Låsen serialiserer, databasen håndhever unik-constraintene. Ingen retry-løkker, ingen nye exception-typer. |
| Deltakernummer | **Behold egen tabell.** WorkOp er trolig et mindretall av treffene, og en kolonne på `jobbsoker` ville gitt en tom kolonne for de fleste rader. |
| Subdomener | **Vurdering 4: `moteplan` og `matching`**, begge som underpakker av `treffgjennomføring`. |
| Leselag | **Ja.** Vi ønsker store lesekall som henter mye i én runde, framfor mange små. |

---

## Utgangspunktet

Pakken `no.nav.toi.treffgjennomføring` er på ca. 1800 linjer fordelt på ti filer:

| Fil | Linjer | Ansvar |
| --- | -----: | ------ |
| `TreffgjennomføringRepository.kt` | 512 | All lesing og skriving mot ni tabeller |
| `TreffgjennomføringService.kt` | 489 | All forretningslogikk og all hendelseskriving |
| `TreffgjennomføringController.kt` | 214 | Åtte endepunkter, felles tilgangskontroll |
| `dto/TreffgjennomføringDto.kt` | 128 | Én DTO for hele aggregatet |
| `Treffgjennomføring.kt` | 113 | Domenemodell og faseenum |
| `Intervjufordeler.kt` | 95 | Ren fordelingsalgoritme |
| `Treffkontekst.kt` | 94 | ID-oversetting treff-ID ↔ database-ID |
| `TreffgjennomføringValidering.kt` | 93 | Validering av innkommende DTO-er |
| `Romfordeler.kt` | 49 | Ren romfordelingsalgoritme |
| `OppmøteHarRegistreringerException.kt` | 10 | Kaskadeadvarsel |

Ni tabeller fra `V14__treffgjennomforing.sql`: `treffgjennomforing`, `moteoppsett`,
`deltakernumre`, `jobbsoker_rom_tildeling`, `arbeidsgiver_rotasjon`, `interesse`,
`intervju_fordeling`, `vurdering`, `vurdering_notat`.

Åtte endepunkter, allerede delt i tre stier:

```
GET  /api/rekrutteringstreff/{id}/treffgjennomforing-og-oppfolging   ← hele aggregatet
PUT  /api/rekrutteringstreff/{id}/treffgjennomforing/oppmote
PUT  /api/rekrutteringstreff/{id}/treffgjennomforing/moteoppsett
PUT  /api/rekrutteringstreff/{id}/treffgjennomforing/romfordeling
PUT  /api/rekrutteringstreff/{id}/treffgjennomforing/interesse
PUT  /api/rekrutteringstreff/{id}/treffgjennomforing/intervjufordeling
POST /api/rekrutteringstreff/{id}/treffgjennomforing/intervjufordeling/fordel
PUT  /api/rekrutteringstreff/{id}/oppfolging/vurderinger
```

Stiene forteller at skillet mellom treffgjennomføring og oppfølging allerede er
tatt i API-et og i frontend. Det er bare koden som ikke har fulgt etter.

---

## Avhengighetskart

Grunnlaget for alle vurderingene under. Kolonnen «Skriver til» viser hvilke tabeller
én transaksjon berører.

| Operasjon | Skriver til | Leser i tillegg |
| --------- | ----------- | --------------- |
| Oppmøte (møtt) | `jobbsoker_hendelse`, `deltakernumre` | hele aggregatet |
| Oppmøte (angre) | `jobbsoker_hendelse`, sletter i `interesse`, `intervju_fordeling`, `vurdering`, `jobbsoker_rom_tildeling` | telling i tre tabeller |
| Møteoppsett | `moteoppsett`, `jobbsoker_rom_tildeling`, `arbeidsgiver_rotasjon`, `treffgjennomforing.fase`, to hendelsestabeller | hele aggregatet |
| Romfordeling | `jobbsoker_rom_tildeling`, `jobbsoker_hendelse` | rom og oppmøte |
| Interesse | `interesse`, **`intervju_fordeling`**, `treffgjennomforing.fase`, to hendelsestabeller | oppmøte og fordelinger |
| Intervjufordeling | `intervju_fordeling`, `fase`, to hendelsestabeller | fordelinger |
| Fordel på nytt | `intervju_fordeling`, `fase`, `rekrutteringstreff_hendelse` | **interesser** og fordelinger |
| Vurdering | `vurdering`, `vurdering_notat`, `fase`, to hendelsestabeller | vurderinger |

### Kanter som krysser en tenkt subdomenegrense

```text
oppmøte ──sletter──────────► rom                                   (1 kant, skriving)
oppmøte ──teller───────────► interesse, vurdering                  (2 kanter, lesing: blokkerer fjerning)
oppmøte ──lesing───────────► rom (normalisering ved lesing)         (1 kant)
interesse ──speiling───────► fordeling                              (1 kant, skriving)
fordeling ──lesing─────────► interesse                              (1 kant)
møteoppsett ──oppretter────► rom + rotasjon                         (2 kanter, skriving)
alle åtte ──settFase───────► treffgjennomforing.fase                (8 kanter, delt tilstand)
alle åtte ──sikreOgLås─────► treffgjennomforing (rad-lås)           (8 kanter, delt lås)
alle åtte ──hentAggregat───► alle ni tabeller                       (retur-DTO)
```

Tre observasjoner styrer resten av dokumentet:

**Oppmøte er navet.** Fem av de sju kryssende datakantene starter i oppmøte. Alt annet
i domenet er avhengig av hvem som faktisk møtte opp. Det gjør oppmøte til den ene
tingen som er verdt å isolere først – ikke fordi den er løst koblet, men fordi den er
en forutsetning alle andre leser.

**Vurdering er den eneste noden uten utgående kanter.** Ingen annen operasjon leser
eller skriver `vurdering`. De to innkommende kantene er tellinga fra oppmøte, som
bare avgjør om oppmøtet kan fjernes, og den delte fasen. Det er det reneste
snittet i domenet.

**Interesse og fordeling er én node, ikke to.** Interesse skriver inn i
`intervju_fordeling` (`speilInteresseIFordeling`), og «fordel på nytt» leser
`interesse`. Kanten går begge veier, innenfor samme transaksjon.

---

## Vurdering 1: Oppmøte til jobbsøker-domenet, med status i tabell

**Anbefaling: ja.** Dette er endringen med best forhold mellom gevinst og risiko, og
den bør gjøres først – uavhengig av om resten av oppdelingene blir noe av.

### Problemet i dag

Oppmøte har ingen lagret tilstand. Det utledes fra hendelsene `MØTT_OPP` og
`ANGRE_MØTT_OPP`, der den siste vinner. Den utledningen finnes **to steder**, med
identisk `LATERAL`-spørring:

- `TreffgjennomføringRepository.hentOppmøte`
- `JobbsøkerSokRepository.hentOppmøte`

Duplisert avledningslogikk over samme datagrunnlag divergerer før eller siden. Endrer
noen sorteringa eller legger til en tredje hendelsestype, må begge stedene rettes, og
ingenting fanger opp at bare det ene ble gjort.

Utledningen har også en konkret funksjonell kostnad. I `JobbsøkerSokRepository` skjer
berikelsen **etter** filtrering, sortering og paginering. Oppmøte kan derfor ikke
brukes som filter eller sorteringsfelt i jobbsøkersøket. Det er en begrensning som
kommer til å bli et ønske.

### Forslag

Legg oppmøte som egen kolonne på `jobbsoker`, og la treffgjennomføring lese den i
stedet for å utlede den.

```sql
ALTER TABLE jobbsoker ADD COLUMN mott_tidspunkt timestamptz;
```

Nullable tidspunkt framfor boolean: `NULL` betyr «ikke møtt», og vi får «når» gratis
uten en ekstra kolonne. Angret oppmøte setter kolonnen tilbake til `NULL`.
Hendelsene beholdes uendret som revisjonsspor – kolonnen er en projeksjon, ikke en
erstatning.

### Dette er allerede mønsteret i jobbsøker-domenet

`jobbsoker.status` er nøyaktig det samme: en denormalisert projeksjon av
hendelsesstrømmen, vedlikeholdt av `JobbsøkerRepository.endreStatus` og utledet via
`tilJobbsøkerStatus`. Forslaget innfører ikke et nytt mønster, det bruker et som
allerede finnes.

### Ikke legg oppmøte inn i `JobbsøkerStatus`

`JobbsøkerStatus` er en livsløpsstatus med én verdi om gangen:
`LAGT_TIL → INVITERT → SVART_JA/SVART_NEI → FÅTT_JOBB → SLETTET`.

Oppmøte er ortogonalt til alle disse. En person kan være `SVART_JA` og møtt, eller
`SVART_JA` og ikke møtt. En `MØTT`-verdi i samme enum ville overskrive svarstatusen
og gjøre `FÅTT_JOBB` og `MØTT` gjensidig utelukkende. Det er feil modell.

Egen kolonne har også en sikkerhetsfordel: statusendringer utløser sideeffekter
(`registrerFåttJobb` i `FormidlingService`, aktivitetskortoppdateringer). En ny
kolonne trigger ingen av dem.

### Hva vi vinner

| Gevinst | Konkret |
| ------- | ------- |
| Én kilde til oppmøte | To dupliserte `LATERAL`-spørringer blir én kolonnelesing |
| Filtrering og sortering på oppmøte | `JobbsøkerSokRepository` kan filtrere før paginering |
| Enklere aggregatlesing | `hentOppmøte` blir `WHERE mott_tidspunkt IS NOT NULL` |
| Indeksen kan fjernes | Det spesiallagde partielle indekset på `jobbsoker_hendelse` i `V14` blir overflødig |
| Riktig domeneeierskap | «Møtte denne personen opp?» er en egenskap ved jobbsøkeren på treffet |

### Låsen: hvor trengs den egentlig?

**Beslutning: en delt låseprimitiv på `rekrutteringstreff`-raden, som både jobbsøker
og treffgjennomføring tar.**

Spørsmålet var om låsen kan ligge kun i `JobbsøkerService`. Svaret er nei. En
gjennomgang av alle åtte skriveoperasjoner viser at `sikreOgLås` – som i dag kalles
én gang, fra `skriv`, og dermed dekker alle sammen – beskytter mot fire ulike
kappløp:

| # | Hvor | Kappløpet | Konsekvens uten lås |
| - | ---- | --------- | ------------------- |
| 1 | `tildelDeltakernummer` | `MAX(nummer) + 1` leses av to samtidige registreringer | To deltakere får samme fysiske kortnummer |
| 2 | `settFase` | `rad.fase` leses ved låsing, sammenlignes, skrives etterpå | Klassisk tapt oppdatering. Fasen kan gå bakover. |
| 3 | `erstattRomfordeling` | `DELETE` på hele treffet fulgt av batch-`INSERT` | Begge sletter, begge setter inn. Brudd på `jobbsoker_rom_tildeling_ett_rom_per_jobbsoker`, eller en blanding av to fordelinger. |
| 4 | `erstattIntervjufordelinger` | `DELETE` per arbeidsgiver fulgt av `INSERT` | Samme mønster som 3, mot `intervju_fordeling_unikt_par` |

Bare nummer 1 hører til oppmøte. De tre andre ligger i møteplan og matching. Låsen
kan derfor ikke bli liggende igjen som en privat detalj i `JobbsøkerService` – da
mister møteplan og matching beskyttelsen de har i dag.

Samtidig kan ikke jobbsøker-domenet kalle `sikreOgLås` i treffgjennomføring, siden
det gjeninnfører avhengigheten vi vil bli kvitt.

**Løsningen er en låseprimitiv som ikke tilhører noen av dem.** Begge domenene kjenner
allerede `rekrutteringstreff`, og treffet er den naturlige serialiseringsenheten:

```kotlin
// no.nav.toi/treffLås.kt, ved siden av executeInTransaction
fun Connection.låsTreff(treffDbId: Long)
```

Én `SELECT … FOR UPDATE` mot `rekrutteringstreff`-raden. Ingen ny tabell, ingen
retry-løkke, ingen ny exception-type. Hver skriveoperasjon i begge domenene kaller
den som første setning i transaksjonen.

`sikreOgLås` deles da i to, og beholder bare den ene halvdelen av navnet sitt:
`sikreRad` oppretter `treffgjennomforing`-raden hvis den mangler – noe `moteoppsett`
trenger på grunn av fremmednøkkelen – uten å ha noe med låsing å gjøre.

**Låsen blir bredere enn i dag.** To samtidige skrivinger på *samme* treff
serialiseres, også når de rører helt ulike tabeller. Det er akseptabelt: én arrangør
sitter og registrerer om gangen, transaksjonene er korte, og alternativet er fire
ulike låsestrategier som må holdes i hodet samtidig.

### Feilhåndtering: la databasen gjøre jobben

Enkleste løsning med minst kode. Låsen serialiserer, og unik-constraintene
`deltakernummer_unikt_per_treff` og `deltakernummer_ett_per_jobbsoker` står igjen som
et nett under. `tildelDeltakernummer` beholder `ON CONFLICT DO NOTHING` med
oppslag etterpå, slik den fungerer i dag – det gjør gjentatt registrering av samme
person idempotent uten en eneste linje ekstra feilhåndtering.

Ingen retry-løkker. Ingen nye exception-typer. En eventuell constraint-feil er en
ekte feil som skal boble opp, ikke noe som skal fanges og prøves på nytt.

### Deltakernummer beholder egen tabell

**Beslutning: `deltakernumre` blir liggende som egen tabell.**

Deltakernummer tildeles bare på WorkOp-treff, og WorkOp er trolig et mindretall av
møtene. En kolonne på `jobbsoker` ville stått tom for de fleste rader, og dratt et
WorkOp-begrep inn i en tabell alle treff bruker. Unik-constrainten per treff står
også enklere der den står nå.

Konsekvensen er at oppmøteoperasjonen i `JobbsøkerService` skriver til én tabell som
formelt hører til treffgjennomføring. Det er en bevisst, avgrenset kobling: én tabell,
ett kall, ingen lesing av øvrig treffgjennomføringstilstand.

---

## Vurdering 2: Del i treffgjennomføring og oppfølging

**Anbefaling: ja.** Dette er det reneste snittet i domenet.

### Avhengighetsregnskap

Oppfølging eier `vurdering` og `vurdering_notat`, og betjener ett endepunkt:
`PUT /oppfolging/vurderinger`.

| Retning | Antall | Hva |
| ------- | -----: | --- |
| Ut av oppfølging | **0** | Ingen annen operasjon leser eller skriver vurderinger |
| Inn i oppfølging | 2 | Kaskadeslettinga ved angret oppmøte, og den delte fasen |
| Delt lesevei | 1 | `vurderinger` er ett felt i `TreffgjennomføringDto` |

Null utgående kanter er uvanlig godt. Til sammenligning har interesse tre.

### De to innkommende kantene

**Kaskadeslettinga.** `slettRegistreringerFor` sletter i fire tabeller når et oppmøte
angres, inkludert `vurdering`. Etter oppdelinga må treffgjennomføring be oppfølging om
å rydde, i stedet for å slette i en tabell den ikke eier. Løses med et smalt kall:
`oppfølgingService.slettForJobbsøker(connection, jobbsøkerId)`, på samme connection.
Det samme gjelder tellinga i `tellRegistreringer`.

**Fasen.** `settFase(… VURDERING)` skriver til `treffgjennomforing.fase`. Fasen er
treffgjennomføringens tilstand, ikke oppfølgingens. Enten kaller oppfølging tilbake for
å melde framdrift, eller så eksponerer treffgjennomføring en smal
`meldFramdrift(fase)`-operasjon. Sistnevnte er ærligere: fasen er delt tilstand, og
det bør synes i signaturen.

### Grensa går ved skriving, ikke lesing

Frontend henter alt fra ett endepunkt,
`GET /treffgjennomforing-og-oppfolging`, og alle skriveendepunktene returnerer den
samme DTO-en. **Den kontrakten skal ikke endres.** Vi splitter skrivesida og
tabelleierskapet, og lar et leselag sette sammen svaret. Se vurdering 5.

### Forslag til pakkestruktur

```
no.nav.toi.oppfølging/
├── Vurdering.kt              domenemodell, Vurderingsvalg, Vurderingsnotat
├── OppfølgingRepository.kt   vurdering + vurdering_notat
├── OppfølgingService.kt      lagreVurdering, slettForJobbsøker, tellForJobbsøker
├── OppfølgingController.kt   PUT /oppfolging/vurderinger
└── OppfølgingValidering.kt
```

Toppnivå-pakke, ikke underpakke av treffgjennomføring. Endepunktstien og
frontendbegrepet sier at dette er et sidestilt domene.

---

## Vurdering 3: Egne subdomener for interesse og romfordeling

**Anbefaling: nei, ikke slik.** Begge grensene skjærer tvers gjennom en transaksjon.

### Interesse alene fungerer ikke

Interesse har tre kanter til intervjufordeling:

1. `speilInteresseIFordeling` skriver til `intervju_fordeling` hver gang en interesse
   settes eller fjernes, i samme transaksjon.
2. `fordelIntervjuer` leser `aggregat.interesser` for å regne ut hele fordelinga.
3. Kaskadeslettinga ved angret oppmøte tømmer begge under ett.

Kant 1 er den avgjørende. Et eget `InteresseRepository` med et eget
`IntervjufordelingRepository` gir en tjeneste som må kalle den andres repository
midt i sin egen skriveoperasjon, på samme connection, for å holde invarianten.
Det er ikke et subdomene. Det er ett subdomene fordelt på to filer, med grensa
lagt der koblinga er sterkest.

### Romfordeling alene fungerer heller ikke

Romfordeling har tre kanter:

1. **Til møteoppsett.** `opprettMøteplan` oppretter romfordeling og rotasjon i samme
   operasjon som møteoppsettet lagres. Første `PUT /moteoppsett` skriver til alle tre
   tabellene.
2. **Til oppmøte.** Rommene normaliseres ved *lesing*
   (`Romfordeler.oppdaterEtterOppmøte`), fordi oppmøtet kan ha endret seg siden
   fordelinga ble lagret. Rom kan ikke leses uten oppmøte.
3. **Til rotasjon.** `arbeidsgiver_rotasjon` og `jobbsoker_rom_tildeling` er to sider
   av samme møteplan, og skrives sammen.

Rom, rotasjon og møteoppsett er tre tabeller som opprettes i én transaksjon, leses
sammen og gir ikke mening hver for seg. De er ett subdomene, ikke tre.

### Hva forslaget likevel peker på

Ønsket om å dele opp er riktig. `TreffgjennomføringService` på 489 linjer og
`TreffgjennomføringRepository` på 512 er for mye i én fil, og grensene finnes –
de går bare ikke der forslaget plasserer dem. Se vurdering 4.

---

## Vurdering 4: Valgt oppdeling – møteplan og matching

**Valgt.** Denne oppdelinga følger transaksjonsgrensene i stedet for å bryte dem, og
gir samtidig et skille som allerede finnes i domenet: WorkOp-spesifikk kode mot kode
alle treff bruker. Begge blir underpakker av `treffgjennomføring`.

| Subdomene | Tabeller | Endepunkter | Gjelder |
| --------- | -------- | ----------- | ------- |
| **Møteplan** | `moteoppsett`, `jobbsoker_rom_tildeling`, `arbeidsgiver_rotasjon` | `PUT /moteoppsett`, `PUT /romfordeling` | Kun WorkOp |
| **Matching** | `interesse`, `intervju_fordeling` | `PUT /interesse`, `PUT /intervjufordeling`, `POST /fordel` | Interesse: alle treff. Fordeling: kun WorkOp |
| **Kjerne** | `treffgjennomforing` (fase, lås) | `GET` | Alle treff |
| **Oppfølging** | `vurdering`, `vurdering_notat` | `PUT /oppfolging/vurderinger` | Alle treff |
| **Oppmøte** | `jobbsoker.mott_tidspunkt`, `deltakernumre` | `PUT /oppmote` | Alle treff |

### Kanter som gjenstår etter oppdelinga

```text
oppmøte ──► møteplan, matching, oppfølging   (romsletting + telling: 3 smale kall)
oppmøte ──► møteplan                         (rom leses med oppmøte som argument)
matching ──► matching                        (speiling: nå internt, ikke lenger en grense)
møteplan ──► møteplan                        (opprettMøteplan: nå internt)
alle ──► kjerne                              (fase og lås: eksplisitt, smal operasjon)
```

To av kantene som gjorde vurdering 3 uframkommelig – speilinga og
møteplanopprettelsen – blir *interne* her. Det er hele poenget: grensa legges der
koblinga er svak, ikke der begrepene tilfeldigvis har ulike navn.

Kaskadeslettinga fra oppmøte står igjen som den ene kanten som treffer alt. Den kan
ikke fjernes – det er en reell forretningsregel. Men den kan gjøres eksplisitt:
tre navngitte kall i stedet for én `slettRegistreringerFor` som sletter i fire
tabeller på tvers av eierskap.

### Møteoppsett og romfordeling er samme subdomene

Vær oppmerksom på at `PUT /moteoppsett` i dag gjør to helt ulike ting avhengig av
tilstand: hvis det ikke finnes rom, oppretter den hele møteplanen (rom + rotasjon +
fase); hvis rom finnes, endrer den bare tidene. Den doble oppførselen er lettere å se
– og eventuelt splitte i to operasjoner – når den ligger i en fil som bare handler om
møteplanen. Det er en sideeffekt av oppdelinga som er verdt å ta med.

---

## Vurdering 5: Leselag for aggregatet

**Valgt.** Vi ønsker store lesekall som henter mye i én runde, framfor mange små.
Leselaget er samtidig forutsetningen for at vurdering 2 og 4 skal gi gevinst.

### Problemet enhver oppdeling støter på

Alle åtte endepunkter, også skriveoperasjonene, returnerer hele
`TreffgjennomføringDto`. Metoden `skriv` avslutter alltid med:

```kotlin
repository.hentAggregat(connection, kontekst).tilDto(treffId.somString)
```

`hentAggregat` leser fra samtlige ni tabeller. Deler vi opp skrivesida i fem
subdomener uten å gjøre noe med lesesida, ender hvert subdomene med å kalle
`hentAggregat` – og da er alle koblet til alt igjen, bare med flere filer.

### Forslag

Ett leselag som eier sammensettinga:

```
no.nav.toi.treffgjennomføring/
└── TreffgjennomføringReader.kt
```

Readeren spør hvert subdomene om sin del og setter sammen `TreffgjennomføringDto`.
Hvert subdomene eksponerer én lesemetode for sin egen del, og kjenner ikke DTO-en.

Konsekvenser å ta stilling til:

- **Readeren avhenger av alle subdomenene.** Det er greit, så lenge avhengigheten går
  én vei og ingen subdomener avhenger av readeren.
- **Normaliseringa av rom må ha oppmøte.** `Romfordeler.oppdaterEtterOppmøte` kjører
  ved lesing. Enten tar møteplanens lesemetode oppmøte som argument – anbefales – eller
  så flyttes normaliseringa opp i readeren.
- **Antall spørringer må ikke øke.** `hentAggregat` gjør i dag ti spørringer på én
  connection. Oppdelinga skal gi like mange, ikke flere. Verifiser med en test som
  teller spørringer, eller med `pg_stat_statements` i test.
- **Store lesekall er et mål, ikke en bivirkning.** Hvert subdomene skal eksponere
  én lesemetode som henter hele sin del i så få spørringer som mulig. Ingen
  N+1-mønstre der readeren kaller subdomenet én gang per jobbsøker eller arbeidsgiver.

### Alternativet vi ikke velger

Å splitte `GET`-endepunktet i to, ett per domene, ville fjernet behovet for et
leselag. Det krever endringer i frontend, gir to nettverkskall der det nå er ett, og
løser ingenting frontend har bedt om. Kontrakten står.

---

## Målstruktur ✅ nådd

```
no.nav.toi/
├── treffLås.kt                    delt låseprimitiv, ved siden av executeInTransaction
└── HendelseWriter.kt              delt hendelseskriving

no.nav.toi.jobbsoker/
└── oppmøte/
    ├── OppmøteService.kt          eier oppmøtetransaksjonen: status + hendelse + deltakernummer
    ├── OppmøteRepository.kt       jobbsoker.oppmote-kolonnen, deltakernummer
    └── OppmøteHarRegistreringerException.kt  Registreringer + 409-signalet

no.nav.toi.treffgjennomføring/
├── Treffgjennomføring.kt          aggregatmodell, TreffgjennomføringFase
├── Treffkontekst.kt               uendret
├── TreffgjennomføringReader.kt    setter sammen aggregat-DTO-en
├── TreffgjennomføringController.kt  uendret sti, delegerer videre
├── TreffgjennomføringWriter.kt    transaksjon + lås, felles for subdomenene
├── FaseRepository.kt              treffgjennomforing-raden: sikreRad, settFase, meldFramdrift
├── møteplan/
│   ├── Møteplan.kt                Møteoppsett, Rom, ArbeidsgiverRotasjon
│   ├── Romfordeler.kt             flyttet, uendret
│   ├── MøteplanRepository.kt      moteoppsett, rom, rotasjon
│   ├── MøteplanService.kt
│   └── MøteplanValidering.kt
└── matching/
    ├── Matching.kt                Interesse, ArbeidsgiverIntervjufordeling
    ├── Intervjufordeler.kt        flyttet, uendret
    ├── MatchingRepository.kt      interesse, intervju_fordeling
    ├── MatchingService.kt
    └── MatchingValidering.kt

no.nav.toi.oppfølging/
├── Vurdering.kt
├── OppfølgingRepository.kt
├── OppfølgingService.kt
├── OppfølgingController.kt
└── OppfølgingValidering.kt
```

Oppmøte har fått en egen `OppmøteService` under `jobbsoker/oppmøte/`, etter en
runde med `JobbsøkerService` som eier. `JobbsøkerService` bar alle
subdomenerepositoryene, og uttrekket samlet hele operasjonen – statusoppdatering,
hendelse, deltakernummer og blokkeringssjekk – på ett sted. Transaksjonen er den
samme: `OppmøteService` bruker `TreffgjennomføringWriter.skriv` som alle andre
skriveoperasjoner.

Hendelseskrivinga (`leggTilHendelseForJobbsøker`, `-Arbeidsgiver`, `-Treff`, `-Par`)
ligger i dag privat i `TreffgjennomføringService` og brukes av alle subdomenene.
Den må ut i en delt komponent som hvert subdomene får injisert – for eksempel
`TreffgjennomføringHendelser`, som fantes i en tidligere versjon av koden. Den skal
fortsatt skrive på samme connection som operasjonen, slik at ingen registrering kan
bli stående uten hendelse.

---

## Faseplan

Hver fase er en egen PR som kan slås sammen og deployes alene. Ingen fase endrer
frontendkontrakten.

### Fase 0 – karakteriseringstester ✅ implementert

`TreffgjennomføringKarakteriseringTest.kt`, 20 tester. Låser dagens oppførsel før
noe flyttes. Testene sier ikke at oppførselen er riktig – de sier at refaktoreringa
ikke skal endre den.

| Område | Tester | Låser |
| ------ | -----: | ----- |
| Aggregatet | 3 | Alle felter med data i alle ni tabellene, at skriveoperasjon og lesing gir identisk svar, og at tomtilstanden ikke oppretter rader |
| Kaskadesletting | 3 | Angret oppmøte tømmer alle fire tabellene for bare én person, og bekreftelseskravet |
| Fase | 2 | Fasen settes av hvert steg i rekkefølge, og går ikke bakover ved angret oppmøte |
| Deltakernummer | 3 | Samtidighet, gjenbruk av eget nummer, og at vanlige treff ikke tildeler nummer |
| Hendelser | 4 | Type, antall og innhold per skriveoperasjon, inkludert parhendelser og idempotens |
| Invarianter | 5 | Interessespeiling, romnormalisering etter oppmøte, og de to WorkOp-vaktene |

Testene er skrevet mot **service-laget**, ikke repository-laget eller HTTP. Da
overlever de at repositoryene splittes i fase 4 og 5, og at oppmøtet flytter til
jobbsøker-domenet i fase 2.

Samtidighetstesten kjører seks parallelle oppmøteregistreringer mot samme treff og
krever at numrene blir 1–6 uten duplikater. Den er portvakt for fase 2 steg 1–2.

**Uten fase 0 er resten av planen ikke forsvarlig.** Dette er ren refaktorering av
kode som allerede er i bruk, og eneste beskyttelse mot stille regresjon er tester som
låser dagens oppførsel.

> **Kjøring lokalt med Colima:** Testcontainers finner VM-IP-en, ikke `localhost`.
> Kjør med `TESTCONTAINERS_HOST_OVERRIDE=localhost` hvis testene feiler med
> «Could not connect to Ryuk» eller «Failed to initialize pool».

### Fase 1 – oppmøte i tabell ✅ implementert

**Kolonnen heter `oppmote`, er nullable tekst, og har `NULL` som standardverdi.**
Verdiene defineres av enumen `Oppmøte` i koden:

| Verdi | Betyr |
| ----- | ----- |
| `NULL` | Oppmøte er aldri registrert |
| `REGISTRERT_OPPMØTE` | Personen møtte opp |
| `REGISTRERT_OPPMØTE_FJERNET` | Oppmøtet ble registrert og deretter angret |

Ikke tidspunkt. Når noe skjedde kan utledes fra hendelsene, og en `timestamptz` ville
duplisert data hendelsesloggen allerede eier. Kolonnen svarer bare på *hva* som gjelder nå.

`REGISTRERT_OPPMØTE_FJERNET` og `NULL` gir samme svar på «møtte personen opp?». De
holdes likevel fra hverandre, fordi de sier ulike ting: den ene er en angret
registrering, den andre er fravær av registrering.

Ingen `CHECK`-constraint. `jobbsoker.status` er også tekst uten constraint, og enumen
håndheves i koden. Samme mønster, minst kode.

**Kolonnen ligger i `V14__treffgjennomforing.sql`,** ikke i en egen migrasjon.
Treffgjennomføringa er ikke deployet ennå, så oppmøtekolonnen hører hjemme i samme
migrasjon som resten av funksjonaliteten.

#### Den gamle oppmøtefunksjonen er ryddet bort

Den opprinnelige planen hadde en backfill fra `jobbsoker_hendelse`. Den er fjernet, og
i stedet sletter `V14` de gamle radene.

`jobbsoker_hendelse` inneholdt `MØTT_OPP`- og `IKKE_MØTT_OPP`-rader fra **en
oppmøtefunksjon som ble fjernet i oktober 2025** (commit `d013441b`, «Fjern
oppmøtelogikk»). Den var deployet og skrev hendelser via `registrerOppmøte`, med en
egen scheduler mot aktivitetskortet.

De radene henger ikke sammen med treffgjennomføringa:

- **Ingen aktiv kode leser dem.** `JobbsøkerhendelserScheduler` poller dem ikke, og
  koden som gjorde det ble slettet sammen med funksjonen.
- **`IKKE_MØTT_OPP` finnes ikke lenger i `JobbsøkerHendelsestype`.** En slik rad ville
  kastet `IllegalArgumentException` i `valueOf` ved lesing av jobbsøkeren.
- **`MØTT_OPP` betyr noe annet nå.** Hendelsestypene for oppmøte er samtidig døpt om
  til `REGISTRERT_OPPMØTE` og `REGISTRERT_OPPMØTE_FJERNET`, så de gamle navnene
  tilhører utelukkende den nedlagte funksjonen.

`V14` sletter derfor radene, og de tilhørende radene i `aktivitetskort_polling` som
peker på dem via fremmednøkkel. Slettinga skjer før den nye funksjonaliteten tas i
bruk, så alle `MØTT_OPP`-rader fra nå av tilhører treffgjennomføringa.

Oppmøte starter tomt: alle får `NULL`. Det er riktig utgangspunkt for en funksjon som
ikke har vært i drift.

Tell radene før deploy, så dere vet hva slettinga faktisk fjerner:

```sql
SELECT hendelsestype, COUNT(*)
FROM jobbsoker_hendelse
WHERE hendelsestype IN ('MØTT_OPP', 'IKKE_MØTT_OPP')
GROUP BY hendelsestype;
```

Indekset `idx_jobbsoker_hendelse_oppmote` er samtidig fjernet fra `V14`. Det støttet
`LATERAL`-utledninga som ikke finnes lenger.

**Gjort:**

1. `V14__treffgjennomforing.sql` sletter de gamle oppmøtehendelsene, legger til
   kolonnen og indekset `idx_jobbsoker_oppmote`.
2. Enumen `Oppmøte` i `no.nav.toi.jobbsoker`. Hendelsestypene er døpt om til
   `REGISTRERT_OPPMØTE` og `REGISTRERT_OPPMØTE_FJERNET`, slik at de heter det samme
   som oppmøteverdiene. Koblinga står i `Oppmøte.hendelsestype`, så de to enumene ikke
   kan komme fra hverandre.
3. `JobbsøkerRepository.settOppmøte` skriver kolonnen i samme transaksjon som hendelsen.
4. Lesevegen bytta i både `TreffgjennomføringRepository` og `JobbsøkerSokRepository`.
   Den dupliserte `LATERAL`-spørringa er borte begge steder.

Hendelsene skrives fortsatt, nå som `REGISTRERT_OPPMØTE` og
`REGISTRERT_OPPMØTE_FJERNET`. Kolonnen er en projeksjon av dem, ikke en erstatning, og
hele revisjonssporet står igjen.

> **Lokale databaser må resettes.** `V14` har endret innhold, og Flyway vil avvise en
> database der den gamle versjonen allerede er kjørt.

### Fase 2 – oppmøteoperasjonen til JobbsøkerService ✅ implementert

`JobbsøkerService.oppdaterOppmøte` eier nå operasjonen, og gjør kolonne, hendelse og
deltakernummer i **samme transaksjon**.

**Gjort:**

1. `Connection.låsTreff(treffDbId)` i `no.nav.toi/treffLås.kt`. Én `SELECT … FOR UPDATE`
   mot `rekrutteringstreff`-raden.
2. `sikreOgLås` delt i to: `låsTreff` tar låsen, `sikreRad` oppretter raden som
   møteoppsettet trenger på grunn av fremmednøkkelen. `skriv` kaller begge, i den
   rekkefølgen.
3. Oppmøtet flyttet til `JobbsøkerService`, som kaller `låsTreff` først.
   `TreffgjennomføringService` har ikke lenger noe med oppmøte å gjøre.
4. `TreffgjennomføringController` beholder ruta `PUT /treffgjennomforing/oppmote` og
   delegerer til `JobbsøkerService`. Frontendkontrakten er uendret.

Begge domenene tar låsen i samme rekkefølge (`låsTreff` → `sikreRad`), så de kan ikke
låse hverandre fast.

**Endring underveis:** sjekken på om oppmøtet allerede har ønsket verdi leser nå
kolonnen direkte i stedet for å hente hele aggregatet. Fase 1 gjorde det mulig, og det
sparer ti spørringer på en operasjon som ofte er et no-op.

**To ting som var bevisst midlertidige:**

- ~~`JobbsøkerService` returnerer `TreffgjennomføringDto` bygget fra repositoryet.~~
  Løst i fase 3: tjenesten kaller `TreffgjennomføringReader`.
- Romfrigivinga står fortsatt som direkte tabellsletting via
  `TreffgjennomføringRepository`. Fase 4 og 5 gir eierne noe å kalle på.

`JobbsøkerService` kjenner derfor fortsatt `TreffgjennomføringRepository`, men nå bare
for deltakernummeret og rommet. Det er en kjent, avgrenset kobling som krymper i
fase 4–5, ikke et sluttbilde.

### Fase 3 – leselaget ✅ implementert

`TreffgjennomføringReader` eier nå sammensettinga av svaret. Ingenting annet er flyttet.

**Gjort:**

1. `TreffgjennomføringReader.les(connection, kontekst)` bygger `TreffgjennomføringDto`.
2. `TreffgjennomføringService.hent` og `skriv` kaller readeren i stedet for å bygge
   DTO-en selv.
3. `JobbsøkerService` kaller readeren. Den bygger ikke lenger DTO-en fra
   `TreffgjennomføringRepository` – én av de to midlertidige koblingene fra fase 2 er
   dermed borte. Igjen står bare romfrigivinga og deltakernummeret.

Readeren tar `Treffkontekst`, ikke `TreffId`. Skriveoperasjonene har allerede konteksten,
og slipper å lese den to ganger i samme transaksjon.

**Vakt på antall spørringer.** Planen krevde en test som teller spørringer.
`TreffgjennomføringReaderTest` bruker en proxy-`Connection` som teller
`prepareStatement`, og slår fast at lesevegen bruker **ti** spørringer: fase,
møteoppsett, oppmøte, deltakernummer, rom, rotasjon, interesser, fordelinger,
vurderinger og notater.

En egen test kjører samme lesing mot et lite og et stort treff og krever likt antall.
Den fanger N+1-mønstre i fase 4 og 5, der hvert subdomene får sin egen lesemetode og
det er lett å ende opp med én spørring per jobbsøker.

Feiler de testene etter en oppdeling, er det oppdelinga som er feil – ikke tallet.

**Merk:** `Treffkontekst` koster tre spørringer i tillegg (treff, jobbsøkere,
arbeidsgivere). De ligger utenfor readeren og telles ikke, siden konteksten hentes én
gang per transaksjon uansett hvor mange lesinger som skjer.

### Fase 4 – skill ut oppfølging ✅ implementert

`vurdering` og `vurdering_notat` eies nå av `no.nav.toi.oppfølging`.

```
no.nav.toi.oppfølging/
├── Vurdering.kt              Vurdering, Vurderingsvalg, Vurderingsnotat
├── OppfølgingRepository.kt   vurdering + vurdering_notat
├── OppfølgingService.kt      lagreVurdering, slettForJobbsøker, tellForJobbsøker
├── OppfølgingController.kt   PUT /oppfolging/vurderinger
└── OppfølgingValidering.kt
```

**De to innkommende kantene er erstattet med eksplisitte kall:**

- **Blokkeringstellinga.** `MatchingRepository.tellInteresserForJobbsøker` teller
  bare i `interesse`. `OppmøteService` setter sammen `Registreringer` ved også å
  kalle `oppfølgingRepository.tellForJobbsøker` på samme connection, og lar
  hvert domene svare for sine egne tabeller.
- **Fasen.** `TreffgjennomføringRepository.meldFramdrift(connection, treffDbId, fase)`
  er den smale operasjonen oppfølging kaller. Fasen forblir treffgjennomføringens
  tilstand, og det synes i signaturen.

**Readeren setter sammen begge domenene.** `Treffgjennomføring`-aggregatet har ikke
lenger `vurderinger`; `TreffgjennomføringReader` henter dem fra `OppfølgingRepository`
og sender dem inn i `tilDto`. Frontendkontrakten er uendret — `GET
/treffgjennomforing-og-oppfolging` svarer likt som før.

**Antall spørringer er uendret.** Aggregatet mistet to spørringer, oppfølging la til
to. `TreffgjennomføringReaderTest` står fortsatt på ti.

#### Fase 6 hentet fram

Begge domenene måtte skrive parhendelser. I stedet for å duplisere hjelperne er de
trukket ut i `no.nav.toi.HendelseWriter` med `forJobbsøker`, `forArbeidsgiver`,
`forTreff` og `forJobbsøkerOgArbeidsgiver`. Planen åpnet for dette: fase 6 kan gjøres
tidligere hvis fase 4 eller 5 trenger den. `TreffgjennomføringService` mistet dermed
fire private hjelpere og tre repository-avhengigheter.

#### Delt tilgangssjekk

`krevTilgang` lå privat i `TreffgjennomføringController`. Den er flyttet til
`Context.krevEierEllerUtvikler(eierService, treffId)` i `rekrutteringstreff.eier`, slik
at `OppfølgingController` bruker nøyaktig samme regel i stedet for en kopi. Det svarer
ut åpent spørsmål 2.

#### Kjent kobling

`OppfølgingService` returnerer `TreffgjennomføringDto` og bruker derfor
`TreffgjennomføringReader`, samtidig som readeren bruker `OppfølgingRepository`. Det
er en syklus på pakkenivå, og den er bevisst: endepunktet svarer med hele aggregatet,
og alternativet er to transaksjoner eller en delt DTO-pakke. Samme mønster som
`OppmøteService` har.

### Fase 5 – skill ut møteplan og matching ✅ implementert

Ingen fil i domenet eier lenger mer enn ett subdomene.

```
no.nav.toi.jobbsoker/
└── oppmøte/                       OppmøteService, OppmøteRepository, 409-signalet

no.nav.toi.treffgjennomføring/
├── Treffgjennomføring.kt          delene satt sammen, fase
├── Treffkontekst.kt
├── FaseRepository.kt              treffgjennomforing-raden: sikreRad, settFase
├── TreffgjennomføringWriter.kt    transaksjon + lås + svar, felles for alle skrivinger
├── TreffgjennomføringReader.kt    setter sammen aggregat-DTO-en
├── TreffgjennomføringService.kt   kun GET
├── TreffgjennomføringController.kt  uendrede ruter, delegerer videre
├── møteplan/                      moteoppsett, rom, rotasjon
└── matching/                      interesse, intervju_fordeling
```

**Speilinga og møteplanopprettelsen er nå interne.** De to kantene som gjorde
vurdering 3 uframkommelig ligger begge inne i ett subdomene: `speilInteresseIFordeling`
i matching, `opprettMøteplan` i møteplan.

**`TreffgjennomføringWriter`** er rammen alle skriveoperasjoner deler: én transaksjon,
`låsTreff` først, `sikreRad`, og hele aggregatet som svar. Subdomenene fyller inn
skrivinga og slipper å gjenta transaksjonshåndteringa.

**Kaskadeslettinga er tre navngitte kall**, slik planen beskrev. `OppmøteService`
kaller `matchingRepository.slettForJobbsøker`, `møteplanRepository.slettRomForJobbsøker`
og `oppfølgingRepository.slettForJobbsøker`. Tellinga følger samme mønster.

**Oppmøte og deltakernummer flyttet til `jobbsoker/oppmøte/`.** Uten det ville kjernen
blitt en samlepose. `TreffgjennomføringRepository` finnes ikke lenger. I den endelige
strukturen eier `OppmøteService` hele operasjonen (uttrekket fra `JobbsøkerService`),
og `settOppmøte`/`hentOppmøte` ligger i `OppmøteRepository`.

**Antall spørringer er uendret.** Readeren spør fire repositories i stedet for ett, men
hvert subdomene henter sin del i ett kall. `TreffgjennomføringReaderTest` står på ti,
og testen som sammenligner lite og stort treff fanger N+1.

#### Kanter som står igjen

- Kaskaden fra oppmøte treffer alle tre subdomenene. Det er en reell forretningsregel,
  ikke en tilfeldig kobling — den er nå eksplisitt i stedet for skjult i én metode.
- `MøteplanService` og `MatchingService` leser oppmøte via `OppmøteRepository`.
  Møteplanen kan ikke normaliseres uten å vite hvem som møtte.
- Subdomenene kaller `FaseRepository.settFase`. Fasen er delt tilstand, og det synes.

#### Merk

`PUT /moteoppsett` gjør fortsatt to ting: oppretter hele møteplanen første gang, og
endrer bare tidene senere. Oppdelinga gjør det tydelig — se åpent spørsmål 2.

### Fase 6 – rydd i hendelseskrivinga ✅ gjort i fase 4

`no.nav.toi.HendelseWriter` ble innført da oppfølging trengte parhendelser.

---

## Personvern

Oppmøte er en personopplysning: den forteller at en navngitt person var fysisk til
stede på et arbeidsmarkedstiltak. I dag ligger den bare i hendelsesloggen. Fase 1
lager en ny lagringsplass for den, og det utløser tre spørsmål som må besvares før
migrasjonen skrives.

| Spørsmål | Status |
| -------- | ------ |
| Dekker eksisterende behandlingsgrunnlag lagring i kolonne, ikke bare i hendelseslogg? | **Må avklares.** Ny lagringsplass, samme opplysning. |
| Følger kolonnen samme sletterutine som resten av jobbsøkerdata? | **Må verifiseres.** Kolonnen er en del av `jobbsoker`-raden og arver dens livsløp, men det bør bekreftes mot sletterutinen. |
| Endres eksponeringen utover? | Nei, hvis `mott_tidspunkt` ikke legges inn i `JobbsøkerOutboundDto` eller søke-viewet uten egen vurdering. |

To regler som gjelder gjennom hele arbeidet:

- **Ingen personopplysninger i hendelsedata.** Dagens kode skriver bare ID-er og enkle
  verdier. Den regelen skal ikke svekkes når hendelseskrivinga flyttes i fase 6.
- **Tilgangskontrollen skal ikke røres.** `krevTilgang` i controlleren – eier eller
  utvikler, ikke kontortilgang – gjelder uendret for alle nye controllere. Får
  oppfølging en egen controller i fase 4, må den ha nøyaktig samme sjekk.

## Risiko og rollback

| Risiko | Konsekvens | Tiltak |
| ------ | ---------- | ------ |
| Deltakernummer dupliseres når låsen flyttes | To personer får samme fysiske kortnummer | `deltakernummer_unikt_per_treff` fanger det i databasen. Samtidighetstesten fra fase 0 er portvakt for fase 2 steg 1–2. |
| Møteplan eller matching mister låsen | Halv romfordeling, eller fase som går bakover | `låsTreff` kalles som første setning i hver skriveoperasjon i begge subdomenene. Verifiseres i fase 5. |
| Kolonne og hendelser divergerer | Oppmøte vises ulikt i søk og i treffgjennomføring | Dobbeltskriving i en periode, og en avstemmingsspørring som sammenligner kolonne mot utledning |
| Kaskadeslettinga glipper etter oppdelinga | Vurdering blir stående for en person som ikke møtte | Karakteriseringstest i fase 0, kjøres etter hver fase |
| Antall spørringer øker i lesevegen | Tregere `GET` på treff med mange deltakere | Tell spørringer i test. Mål på et treff med minst 100 jobbsøkere. |
| Sletting av gamle hendelser treffer for bredt | Revisjonsspor forsvinner | `DELETE` er avgrenset til `MØTT_OPP` og `IKKE_MØTT_OPP`, som begge tilhører den nedlagte funksjonen. Kjør tellinga under før prod. |

**Rollback per fase:**

- Fase 1: kolonnen ligger i `V14`, som ikke er deployet. Ingen data å rulle tilbake –
  treffgjennomføringa deployes som en enhet, eller ikke i det hele tatt.
- Fase 2–6: ren kodeendring uten skjemaendring. Vanlig tilbakerulling av deploy.

## Rød sone

Deler den som implementerer bør skrive selv og forstå i dybden, ikke generere:

- **Låseprimitiven `låsTreff`** (implementert i fase 2). Samtidighet med synlig
  konsekvens for brukeren, og den beskytter fire ulike kappløp – ikke bare
  deltakernummer. Gå gjennom den før du endrer noe rundt transaksjonene.
- **Oppmøtetransaksjonen i `JobbsøkerService`** (implementert i fase 2). Kolonne,
  hendelse og deltakernummer må stå og falle sammen. En delvis registrering gir en
  person som er møtt uten kortnummer, eller motsatt.
- **Slettinga av gamle oppmøtehendelser i `V14`.** Sletting av produksjonsdata er
  irreversibel. Tell radene i dev og prod før deploy, og bekreft at ingen av dem
  tilhører noe som fortsatt er i bruk.
- **Kaskadeslettinga etter oppdelinga.** Regelen om at angret oppmøte fjerner alle
  registreringer er forretningslogikk, ikke teknisk detalj. Den skal ikke gå tapt i
  en flytting.

Grønn sone – trygt å generere, men les gjennom: filflytting uten adferdsendring,
pakkeomorganisering, DTO-mapping, testskjeletter, `ApplicationContext`-kobling.

## Åpne spørsmål

Avklart: eierskap til oppmøte, låsestrategi, feilhåndtering, plassering av
deltakernummer, valg av subdomener og leselag (se [Beslutninger](#beslutninger)), samt
de tidligere spørsmålene om `mott_tidspunkt`-typen og egen oppfølgingscontroller.

Gjenstår:

1. **Trenger vi filtrering på oppmøte i jobbsøkersøket nå,** eller er det bare en
   mulighet fase 1 åpner for? Svaret påvirker hvor høyt fase 1 skal prioriteres.
2. **Skal `PUT /moteoppsett` splittes** i «opprett møteplan» og «endre tider»?
   Utenfor omfanget her, men oppdelinga gjør valget synlig.
3. **Behandlingsgrunnlag for oppmøte i kolonne.** Se [Personvern](#personvern).

## Videre lesing

- [treffgjennomforing-oppmote-rom-og-fordeling.md](treffgjennomforing-oppmote-rom-og-fordeling.md) – design og flyt for de seks stegene
- [../../2-arkitektur/prinsipper.md](../../2-arkitektur/prinsipper.md) – lagdeling og konstruktørbasert DI
- [../../2-arkitektur/database.md](../../2-arkitektur/database.md) – migrasjonskonvensjoner
