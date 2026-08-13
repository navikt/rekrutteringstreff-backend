# Plan: Domeneoppdeling av treffgjennomføring i backend

**Status:** Besluttet. Fase 0 implementert, fase 1–6 gjenstår  
**Omfang:** `rekrutteringstreff-api`, pakken `no.nav.toi.treffgjennomforing`  
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
| 2 | Del i `treffgjennomforing` og `oppfolging` | **Ja** | Oppfølging (vurdering) har null utgående avhengigheter til resten. Reneste snittet i hele domenet. |
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
| Eierskap til oppmøte | `JobbsøkerService` eier hele oppmøteoperasjonen og gjør **statusoppdatering, hendelse og deltakernummer i samme transaksjon**. Ingen delvis registrering skal kunne bli stående. |
| Låsen | Sikres i jobbsøker, men som en **delt låseprimitiv på `rekrutteringstreff`-raden** som begge domenene tar. Analysen under viser at låsen trengs fire steder, ikke bare i oppmøte. |
| Feilhåndtering | **Enklest mulig, minst mulig kode.** Låsen serialiserer, databasen håndhever unik-constraintene. Ingen retry-løkker, ingen nye exception-typer. |
| Deltakernummer | **Behold egen tabell.** WorkOp er trolig et mindretall av treffene, og en kolonne på `jobbsoker` ville gitt en tom kolonne for de fleste rader. |
| Subdomener | **Vurdering 4: `moteplan` og `matching`**, begge som underpakker av `treffgjennomforing`. |
| Leselag | **Ja.** Vi ønsker store lesekall som henter mye i én runde, framfor mange små. |

---

## Utgangspunktet

Pakken `no.nav.toi.treffgjennomforing` er på ca. 1800 linjer fordelt på ti filer:

| Fil | Linjer | Ansvar |
| --- | -----: | ------ |
| `TreffgjennomforingRepository.kt` | 512 | All lesing og skriving mot ni tabeller |
| `TreffgjennomforingService.kt` | 489 | All forretningslogikk og all hendelseskriving |
| `TreffgjennomforingController.kt` | 214 | Åtte endepunkter, felles tilgangskontroll |
| `dto/TreffgjennomforingDto.kt` | 128 | Én DTO for hele aggregatet |
| `Treffgjennomforing.kt` | 113 | Domenemodell og faseenum |
| `Intervjufordeler.kt` | 95 | Ren fordelingsalgoritme |
| `Treffkontekst.kt` | 94 | ID-oversetting treff-ID ↔ database-ID |
| `TreffgjennomforingValidering.kt` | 93 | Validering av innkommende DTO-er |
| `Romfordeler.kt` | 49 | Ren romfordelingsalgoritme |
| `OppmøteHarRegistreringerException.kt` | 10 | Kaskadeadvarsel |

Ni tabeller fra `V14__treffgjennomforing.sql`: `treffgjennomforing`, `moteoppsett`,
`deltakernummer`, `jobbsoker_rom_tildeling`, `arbeidsgiver_rotasjon`, `interesse`,
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
| Oppmøte (møtt) | `jobbsoker_hendelse`, `deltakernummer` | hele aggregatet |
| Oppmøte (angre) | `jobbsoker_hendelse`, sletter i `interesse`, `intervju_fordeling`, `vurdering`, `jobbsoker_rom_tildeling` | telling i tre tabeller |
| Møteoppsett | `moteoppsett`, `jobbsoker_rom_tildeling`, `arbeidsgiver_rotasjon`, `treffgjennomforing.fase`, to hendelsestabeller | hele aggregatet |
| Romfordeling | `jobbsoker_rom_tildeling`, `jobbsoker_hendelse` | rom og oppmøte |
| Interesse | `interesse`, **`intervju_fordeling`**, `treffgjennomforing.fase`, to hendelsestabeller | oppmøte og fordelinger |
| Intervjufordeling | `intervju_fordeling`, `fase`, to hendelsestabeller | fordelinger |
| Fordel på nytt | `intervju_fordeling`, `fase`, `rekrutteringstreff_hendelse` | **interesser** og fordelinger |
| Vurdering | `vurdering`, `vurdering_notat`, `fase`, to hendelsestabeller | vurderinger |

### Kanter som krysser en tenkt subdomenegrense

```text
oppmøte ──kaskadesletting──► rom, interesse, fordeling, vurdering   (4 kanter, skriving)
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
eller skriver `vurdering` eller `vurdering_notat`. De to innkommende kantene er
kaskadeslettinga fra oppmøte og den delte fasen. Det er det reneste snittet i domenet.

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

- `TreffgjennomforingRepository.hentOppmøte`
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

**Beslutning: `deltakernummer` blir liggende som egen tabell.**

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
| Delt lesevei | 1 | `vurderinger` er ett felt i `TreffgjennomforingDto` |

Null utgående kanter er uvanlig godt. Til sammenligning har interesse tre.

### De to innkommende kantene

**Kaskadeslettinga.** `slettRegistreringerFor` sletter i fire tabeller når et oppmøte
angres, inkludert `vurdering`. Etter oppdelinga må treffgjennomføring be oppfølging om
å rydde, i stedet for å slette i en tabell den ikke eier. Løses med et smalt kall:
`oppfolgingService.slettForJobbsøker(connection, jobbsøkerId)`, på samme connection.
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
no.nav.toi.oppfolging/
├── Vurdering.kt              domenemodell, Vurderingsvalg, Vurderingsnotat
├── OppfolgingRepository.kt   vurdering + vurdering_notat
├── OppfolgingService.kt      lagreVurdering, slettForJobbsøker, tellForJobbsøker
├── OppfolgingController.kt   PUT /oppfolging/vurderinger
└── OppfolgingValidering.kt
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

Ønsket om å dele opp er riktig. `TreffgjennomforingService` på 489 linjer og
`TreffgjennomforingRepository` på 512 er for mye i én fil, og grensene finnes –
de går bare ikke der forslaget plasserer dem. Se vurdering 4.

---

## Vurdering 4: Valgt oppdeling – møteplan og matching

**Valgt.** Denne oppdelinga følger transaksjonsgrensene i stedet for å bryte dem, og
gir samtidig et skille som allerede finnes i domenet: WorkOp-spesifikk kode mot kode
alle treff bruker. Begge blir underpakker av `treffgjennomforing`.

| Subdomene | Tabeller | Endepunkter | Gjelder |
| --------- | -------- | ----------- | ------- |
| **Møteplan** | `moteoppsett`, `jobbsoker_rom_tildeling`, `arbeidsgiver_rotasjon` | `PUT /moteoppsett`, `PUT /romfordeling` | Kun WorkOp |
| **Matching** | `interesse`, `intervju_fordeling` | `PUT /interesse`, `PUT /intervjufordeling`, `POST /fordel` | Interesse: alle treff. Fordeling: kun WorkOp |
| **Kjerne** | `treffgjennomforing` (fase, lås) | `GET` | Alle treff |
| **Oppfølging** | `vurdering`, `vurdering_notat` | `PUT /oppfolging/vurderinger` | Alle treff |
| **Oppmøte** | `jobbsoker.mott_tidspunkt`, `deltakernummer` | `PUT /oppmote` | Alle treff |

### Kanter som gjenstår etter oppdelinga

```text
oppmøte ──► møteplan, matching, oppfølging   (kaskadesletting + telling: 3 smale kall)
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
`TreffgjennomforingDto`. Metoden `skriv` avslutter alltid med:

```kotlin
repository.hentAggregat(connection, kontekst).tilDto(treffId.somString)
```

`hentAggregat` leser fra samtlige ni tabeller. Deler vi opp skrivesida i fem
subdomener uten å gjøre noe med lesesida, ender hvert subdomene med å kalle
`hentAggregat` – og da er alle koblet til alt igjen, bare med flere filer.

### Forslag

Ett leselag som eier sammensettinga:

```
no.nav.toi.treffgjennomforing/
└── TreffgjennomforingLeser.kt
```

Leseren spør hvert subdomene om sin del og setter sammen `TreffgjennomforingDto`.
Hvert subdomene eksponerer én lesemetode for sin egen del, og kjenner ikke DTO-en.

Konsekvenser å ta stilling til:

- **Leseren avhenger av alle subdomenene.** Det er greit, så lenge avhengigheten går
  én vei og ingen subdomener avhenger av leseren.
- **Normaliseringa av rom må ha oppmøte.** `Romfordeler.oppdaterEtterOppmøte` kjører
  ved lesing. Enten tar møteplanens lesemetode oppmøte som argument – anbefales – eller
  så flyttes normaliseringa opp i leseren.
- **Antall spørringer må ikke øke.** `hentAggregat` gjør i dag ti spørringer på én
  connection. Oppdelinga skal gi like mange, ikke flere. Verifiser med en test som
  teller spørringer, eller med `pg_stat_statements` i test.
- **Store lesekall er et mål, ikke en bivirkning.** Hvert subdomene skal eksponere
  én lesemetode som henter hele sin del i så få spørringer som mulig. Ingen
  N+1-mønstre der leseren kaller subdomenet én gang per jobbsøker eller arbeidsgiver.

### Alternativet vi ikke velger

Å splitte `GET`-endepunktet i to, ett per domene, ville fjernet behovet for et
leselag. Det krever endringer i frontend, gir to nettverkskall der det nå er ett, og
løser ingenting frontend har bedt om. Kontrakten står.

---

## Målstruktur

```
no.nav.toi/
└── treffLås.kt                    delt låseprimitiv, ved siden av executeInTransaction

no.nav.toi.jobbsoker/
├── JobbsøkerService.kt            eier oppmøtetransaksjonen: status + hendelse + deltakernummer
└── oppmote/
    └── OppmoteRepository.kt       jobbsoker.mott_tidspunkt, deltakernummer

no.nav.toi.treffgjennomforing/
├── Treffgjennomforing.kt          aggregatmodell, TreffgjennomføringFase
├── Treffkontekst.kt               uendret
├── TreffgjennomforingLeser.kt     setter sammen aggregat-DTO-en
├── TreffgjennomforingController.kt  uendret sti, delegerer videre
├── FaseRepository.kt              treffgjennomforing-raden: sikreRad og settFase
├── moteplan/
│   ├── Moteplan.kt                Møteoppsett, Rom, ArbeidsgiverRotasjon
│   ├── Romfordeler.kt             flyttet, uendret
│   ├── MoteplanRepository.kt      moteoppsett, rom, rotasjon
│   └── MoteplanService.kt
└── matching/
    ├── Matching.kt                Interesse, ArbeidsgiverIntervjufordeling
    ├── Intervjufordeler.kt        flyttet, uendret
    ├── MatchingRepository.kt      interesse, intervju_fordeling
    └── MatchingService.kt

no.nav.toi.oppfolging/
├── Vurdering.kt
├── OppfolgingRepository.kt
├── OppfolgingService.kt
└── OppfolgingController.kt
```

Oppmøte får et repository under `jobbsoker/oppmote/`, men **ingen egen service**.
`JobbsøkerService` eier operasjonen, slik at statusoppdatering, hendelse og
deltakernummer skjer i én transaksjon uten et ekstra lag imellom.

Hendelseskrivinga (`leggTilHendelseForJobbsøker`, `-Arbeidsgiver`, `-Treff`, `-Par`)
ligger i dag privat i `TreffgjennomforingService` og brukes av alle subdomenene.
Den må ut i en delt komponent som hvert subdomene får injisert – for eksempel
`TreffgjennomforingHendelser`, som fantes i en tidligere versjon av koden. Den skal
fortsatt skrive på samme connection som operasjonen, slik at ingen registrering kan
bli stående uten hendelse.

---

## Faseplan

Hver fase er en egen PR som kan slås sammen og deployes alene. Ingen fase endrer
frontendkontrakten.

### Fase 0 – karakteriseringstester ✅ implementert

`TreffgjennomforingKarakteriseringTest.kt`, 20 tester. Låser dagens oppførsel før
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

### Fase 1 – oppmøte i tabell

1. `V15__jobbsoker_mott_tidspunkt.sql`: legg til kolonnen.
2. Backfill fra hendelsene med samme `LATERAL`-spørring som i dag. Idempotent.
3. Skriv til kolonnen i tillegg til hendelsen. Les fortsatt fra hendelsene.
4. Verifiser i dev at kolonne og utledning gir samme svar for alle treff.
5. Bytt lesevegen i `TreffgjennomforingRepository` og `JobbsøkerSokRepository` til kolonnen.
6. Fjern det partielle indekset på `jobbsoker_hendelse` i en senere migrasjon.

Steg 3–5 er bevisst delt: en periode med dobbeltskriving gjør at feil oppdages før
lesevegen er avhengig av den nye kolonnen. Steg 6 tas etter at 5 har stått i prod.

### Fase 2 – oppmøteoperasjonen til JobbsøkerService

Flytt registrering og angring av oppmøte til `JobbsøkerService`, som en operasjon:
statusoppdatering, hendelse og deltakernummer i **samme transaksjon**.

1. Innfør `Connection.låsTreff(treffDbId)` i `no.nav.toi/treffLås.kt`.
2. Bytt `sikreOgLås` i `skriv` til `låsTreff` + `sikreRad`. Ingen adferdsendring.
3. Flytt oppmøteoperasjonen til `JobbsøkerService`, som kaller `låsTreff` først.
4. `TreffgjennomforingController` beholder ruta `PUT /treffgjennomforing/oppmote` og
   delegerer til `JobbsøkerService`. Frontend merker ingenting.

Steg 1–2 er den risikofylte delen og bør være en egen PR med samtidighetstesten fra
fase 0 som portvakt. Steg 3–4 er ren flytting etterpå.

Kaskadeslettinga følger med oppmøtet, og blir stående som direkte tabellsletting
fram til fase 4 og 5 gir eierne noe å kalle på.

### Fase 3 – leselaget

Innfør `TreffgjennomforingLeser` uten å flytte noe annet. `hentAggregat` blir
kalt fra leseren i stedet for fra `skriv`. Ingen adferdsendring, men strukturen
som fase 4 og 5 trenger kommer på plass.

### Fase 4 – skill ut oppfølging

Flytt `vurdering` og `vurdering_notat` med tilhørende service, repository og
controller. Erstatt de to innkommende kantene med eksplisitte kall.

### Fase 5 – skill ut møteplan og matching

Gjøres til slutt, fordi den er størst og har minst risiko når 0–4 er på plass.

### Fase 6 – rydd i hendelseskrivinga

Trekk `leggTilHendelseFor*` ut i en delt komponent. Kan gjøres tidligere hvis
fase 4 eller 5 trenger den først.

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
| Backfill treffer feil rader | Feil oppmøte i prod | Migrasjonen er idempotent og kun `UPDATE`. Kjør telling i dev og sammenlign mot utledningen før prod. |

**Rollback per fase:**

- Fase 1 steg 1–4: kolonnen er ubrukt av lesevegen. Rull tilbake koden, la kolonnen stå.
- Fase 1 steg 5: rull tilbake koden. Kolonnen er fortsatt korrekt fordi hendelsene skrives uendret.
- Fase 2–6: ren kodeendring uten skjemaendring. Vanlig tilbakerulling av deploy.

Fase 1 steg 6 – å fjerne indekset – er det eneste irreversible steget, og bør vente
til lesevegen har stått i prod en stund.

## Rød sone

Deler den som implementerer bør skrive selv og forstå i dybden, ikke generere:

- **Låseprimitiven `låsTreff`.** Samtidighet med synlig konsekvens for brukeren, og
  den beskytter fire ulike kappløp – ikke bare deltakernummer. Forstå hvorfor
  `sikreOgLås` finnes før du erstatter den.
- **Oppmøtetransaksjonen i `JobbsøkerService`.** Status, hendelse og deltakernummer
  må stå og falle sammen. En delvis registrering gir en person som er møtt uten
  kortnummer, eller motsatt.
- **Backfill-migrasjonen.** Skriver til produksjonsdata om personers oppmøte. Må være
  idempotent og verifisert mot utledningen før den kjøres.
- **Kaskadeslettinga etter oppdelinga.** Regelen om at angret oppmøte fjerner alle
  registreringer er forretningslogikk, ikke teknisk detalj. Den skal ikke gå tapt i
  en flytting.

Grønn sone – trygt å generere, men les gjennom: filflytting uten adferdsendring,
pakkeomorganisering, DTO-mapping, testskjeletter, `ApplicationContext`-kobling.

## Åpne spørsmål

Avklart siden forrige versjon: eierskap til oppmøte, låsestrategi, feilhåndtering,
plassering av deltakernummer, valg av subdomener og leselag. Se
[Beslutninger](#beslutninger).

Gjenstår:

1. **Skal `mott_tidspunkt` være nullable timestamp eller boolean?** Forslaget er
   timestamp. Trenger vi «når møtte personen» til noe, eller er det bare mer data å
   forvalte?
2. **Skal oppfølging ha egen controller, eller beholde ruta i dagens controller?**
   Egen controller er ryddigere, men dupliserer `krevTilgang`. Vurder å trekke
   tilgangssjekken ut i en delt hjelper først.
3. **Trenger vi filtrering på oppmøte i jobbsøkersøket nå,** eller er det bare en
   mulighet fase 1 åpner for? Svaret påvirker hvor høyt fase 1 skal prioriteres.
4. **Skal `PUT /moteoppsett` splittes** i «opprett møteplan» og «endre tider»?
   Utenfor omfanget her, men oppdelinga gjør valget synlig.
5. **Behandlingsgrunnlag for oppmøte i kolonne.** Se [Personvern](#personvern).

## Videre lesing

- [treffgjennomforing-oppmote-rom-og-fordeling.md](treffgjennomforing-oppmote-rom-og-fordeling.md) – design og flyt for de seks stegene
- [../../2-arkitektur/prinsipper.md](../../2-arkitektur/prinsipper.md) – lagdeling og konstruktørbasert DI
- [../../2-arkitektur/database.md](../../2-arkitektur/database.md) – migrasjonskonvensjoner
