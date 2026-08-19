# Hendelser i treffgjennomføringen (WorkOp) — dokumentasjon og vurdering

**Status: gjennomført.** Anbefalingen under er implementert i backend og frontend.
Beskrivelsene av «i dag» er bevart som begrunnelse for endringen — se
[Slik ble det gjennomført](#slik-ble-det-gjennomført) for hva som faktisk ble gjort.

Designbegrunnelsen for at hendelsene i det hele tatt ble skrevet ligger i
[treffgjennomforing-oppmote-rom-og-fordeling.md](treffgjennomforing-oppmote-rom-og-fordeling.md#hendelser-på-treffgjennomføringen).
Dette dokumentet ser på det samme i etterkant, med koden som fasit.

---

## Sammendrag

- **30 hendelsestyper** er lagt til for treffgjennomføringen: 14 på jobbsøker,
  12 på arbeidsgiver, 4 på treffet.
- **Ingen av dem leses av noe i dag.** Ikke av frontend, ikke av
  aktivitetskort-pollingen, ikke av søkevisningene, ikke av Kafka.
- **`hendelse_data` er usynlig i API-et.** `parseHendelseData` returnerer `null`
  for alle typene, så `romnummer`, `vurdering`, `notat`, `startPosisjon` og
  `deltakernummer` skrives til basen og forsvinner ut av responsen.
- **Frontend har ingen etiketter for dem.** Jobbsøkerhendelser faller tilbake
  til rå enum-navn (`INTERESSE_REGISTRERT`), arbeidsgiver- og treffhendelser til
  tom streng. Hendelser-fanen filtrerer ikke, så de vises — bare uleselig.
- **Hendelsesloggen er allerede ufullstendig.** Tre skriveveier endrer
  current state uten å skrive hendelse (se [Hullene](#hullene-i-loggen)). Logg
  som ikke er komplett kan ikke brukes til å svare på «hva skjedde», som var
  hele begrunnelsen for å skrive den.
- **Anbefaling:** fjern 18 av 30 typer, behold 12. Oversikten etter
  workshop-dagen dekkes av current state-tabellene, som allerede finnes.

---

## Hva som skrives i dag

### Jobbsøkerhendelser (`jobbsoker_hendelse`)

| Type                              | Skrives i             | `hendelse_data`                              | Kun WorkOp |
| --------------------------------- | --------------------- | -------------------------------------------- | ---------- |
| `REGISTRERT_OPPMØTE`              | `OppmøteService`      | `deltakernummer` (kun WorkOp)                | Nei        |
| `REGISTRERT_OPPMØTE_FJERNET`      | `OppmøteService`      | `interesser`, `intervjuplasser`, `vurderinger` | Nei      |
| `PLASSERT_I_ROM`                  | `MøteplanService`     | `romnummer`, `forrigeRomnummer`              | Ja         |
| `INTERESSE_REGISTRERT`            | `MatchingService`     | `arbeidsgiverTreffId`                        | **Nei**    |
| `ANGRE_INTERESSE_REGISTRERT`      | `MatchingService`     | `arbeidsgiverTreffId`                        | **Nei**    |
| `SATT_OPP_TIL_INTERVJU`           | `MatchingService`     | `arbeidsgiverTreffId`                        | Ja         |
| `ANGRE_SATT_OPP_TIL_INTERVJU`     | `MatchingService`     | `arbeidsgiverTreffId`                        | Ja         |
| `VURDERT`                         | `OppfølgingService`   | `arbeidsgiverTreffId`, `vurdering`, `forrigeVurdering` | **Nei** |
| `NOTAT_LAGT_TIL`                  | `OppfølgingService`   | `arbeidsgiverTreffId`, `notat`               | **Nei**    |
| `NOTAT_FJERNET`                   | `OppfølgingService`   | `arbeidsgiverTreffId`, `notat`               | **Nei**    |
| `ANDREGANGSINTERVJU_AVTALT`       | `OppfølgingService`   | `arbeidsgiverTreffId`, `dato`                | **Nei**    |
| `ANGRE_ANDREGANGSINTERVJU_AVTALT` | `OppfølgingService`   | `arbeidsgiverTreffId`                        | **Nei**    |
| `JOBBTILBUD_GITT`                 | `OppfølgingService`   | `arbeidsgiverTreffId`                        | **Nei**    |
| `ANGRE_JOBBTILBUD_GITT`           | `OppfølgingService`   | `arbeidsgiverTreffId`                        | **Nei**    |

### Arbeidsgiverhendelser (`arbeidsgiver_hendelse`)

Elleve typer er speilbilder av jobbsøkerhendelsene over, med `personTreffId` i
`hendelse_data` i stedet for `arbeidsgiverTreffId`:
`INTERESSE_REGISTRERT`, `ANGRE_INTERESSE_REGISTRERT`, `SATT_OPP_TIL_INTERVJU`,
`ANGRE_SATT_OPP_TIL_INTERVJU`, `VURDERT`, `NOTAT_LAGT_TIL`, `NOTAT_FJERNET`,
`ANDREGANGSINTERVJU_AVTALT`, `ANGRE_ANDREGANGSINTERVJU_AVTALT`,
`JOBBTILBUD_GITT`, `ANGRE_JOBBTILBUD_GITT`.

Den tolvte, `ROTASJON_TILDELT`, står alene: den skrives én gang per arbeidsgiver
når møteplanen opprettes, med `startPosisjon` i `hendelse_data`.

### Treffhendelser (`rekrutteringstreff_hendelse`)

| Type                                           | Skrives i         | Status                       |
| ---------------------------------------------- | ----------------- | ---------------------------- |
| `TREFFGJENNOMFØRING_OPPRETTET`                 | `MøteplanService` | Skrives                      |
| `TREFFGJENNOMFØRING_OPPSETT_ENDRET`            | `MøteplanService` | Skrives                      |
| `TREFFGJENNOMFØRING_INTERVJUFORDELING_FORDELT` | `MatchingService` | Skrives                      |
| `TREFFGJENNOMFØRING_ROMFORDELING_ENDRET`       | —                 | **Død verdi, aldri skrevet** |

### Avvik fra planen

| Planen sier                                | Koden gjør                                                         |
| ------------------------------------------ | ------------------------------------------------------------------ |
| `MØTT_OPP` / `ANGRE_MØTT_OPP`              | `REGISTRERT_OPPMØTE` / `REGISTRERT_OPPMØTE_FJERNET`                |
| Oppmøte utledes av hendelser               | Oppmøte ligger i kolonnen `jobbsoker.oppmote` (V14)                |
| Interesse og vurdering er WorkOp-spesifikt | Ingen `krevWorkOp()` — de skrives på **alle** treff                |
| `TREFFGJENNOMFØRING_ROMFORDELING_ENDRET`   | Aldri implementert                                                 |

Det siste avviket er verdt å merke seg: dokumentet sier «alle fire er
WorkOp-spesifikke», men `settInteresse` og `lagreVurdering` mangler
`krevWorkOp()`. Hendelsene treffer altså også vanlige rekrutteringstreff.

---

## Hvem leser hendelsene

Ingen. Kartlagt lesevei for lesevei:

| Leser                                    | Filtrerer på                             | Berørt |
| ---------------------------------------- | ---------------------------------------- | ------ |
| `AktivitetskortRepository`               | `hendelsestype = 'INVITERT'`             | Nei    |
| `jobbsoker_sok_view`                     | `hendelsestype = 'OPPRETTET'`            | Nei    |
| `JobbsøkerhendelserScheduler`            | Invitasjonshendelser                     | Nei    |
| `TreffgjennomføringReader`               | Leser bare current state-tabellene       | Nei    |
| `Jobbsøker.erInvitert` / statusutledning | `INVITERT`, `SVART_*`                    | Nei    |
| GET `.../jobbsoker/hendelser`            | Ingen filtrering — returnerer alt        | **Ja** |
| Hendelser-fanen i frontend               | Ingen filtrering — rendrer alt den får   | **Ja** |
| Kafka / rapids                           | Ingen publisering av disse               | Nei    |

De to som er berørt, er berørt på verst tenkelige måte: hendelsene vises, men
uten etikett.

`HendelseLabel.tsx` i frontend mapper ingen av de nye typene. Resultatet er:

- **Jobbsøker:** `default: return t` — brukeren ser `ANGRE_SATT_OPP_TIL_INTERVJU`.
- **Arbeidsgiver:** `default: return ''` — brukeren ser en tom rad med et ikon.
- **Treff:** `default: return ''` — samme.

`hendelse_data` kommer ikke ut i det hele tatt. `parseHendelseData` har
eksplisitte grener for `MOTTATT_SVAR_FRA_MINSIDE` og
`TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON`, og `else -> null` for alt annet.
All JSON-en treffgjennomføringen skriver — romnummer, vurdering, notatkode,
startposisjon — serialiseres inn i basen og filtreres bort på vei ut.

---

## Hullene i loggen

Tre skriveveier endrer current state uten å skrive hendelse. Det gjør at
hendelsesloggen ikke kan brukes til å rekonstruere hva som skjedde.

1. **Automatisk romfordeling gir ingen `PLASSERT_I_ROM`.**
   `MøteplanService.opprettMøteplan` kaller `erstattRomfordeling` direkte.
   Bare manuell flytting via `lagreRomfordeling` skriver hendelser. En jobbsøker
   som aldri ble flyttet, har ingen spor av hvilket rom hen fikk.

2. **`speilInteresseIFordeling` endrer `intervju_fordeling` uten hendelse.**
   Krysser noen av interesse, legges personen automatisk inn i arbeidsgiverens
   fordeling. Ingen `SATT_OPP_TIL_INTERVJU` skrives. Samme sluttilstand kan
   altså ha hendelse eller ikke, avhengig av hvilken vei brukeren kom.

3. **«Fordel på nytt» gir bare treffhendelsen.** `fordelIntervjuer` overskriver
   alle fordelinger og skriver én `TREFFGJENNOMFØRING_INTERVJUFORDELING_FORDELT`.
   Personene som ble flyttet ut av en fordeling får ingen
   `ANGRE_SATT_OPP_TIL_INTERVJU`.

Konsekvensen: hvis noen spør «var denne personen satt opp hos denne
arbeidsgiveren?», må svaret uansett hentes fra `intervju_fordeling`. Loggen kan
verken bekrefte eller avkrefte. Da bærer den kostnaden uten å gi verdien.

---

## Volum

Per registrering, med parskriving:

| Handling                          | Rader                                        |
| --------------------------------- | -------------------------------------------- |
| Registrer oppmøte                 | 1                                            |
| Kryss av interesse                | 2 (jobbsøker + arbeidsgiver)                 |
| Flytt over sperrelinjen           | 2                                            |
| Sett vurdering                    | 2                                            |
| Kryss av ett notat                | 2                                            |
| Kryss av 2. intervju              | 2                                            |
| Kryss av jobbtilbud               | 2                                            |
| Opprett møteplan                  | 1 + 1 per arbeidsgiver                       |

Et treff med 25 fremmøtte og 5 arbeidsgivere, der hver jobbsøker krysser av
interesse hos tre arbeidsgivere og halvparten av parene får vurdering med
notat:

```
oppmøte                25
rotasjon                5
interesse   75 × 2 =  150
fordeling   50 × 2 =  100
vurdering  180 × 2 =  360
treff                   2
                      ————
                      ~640 rader på én dag
```

Til sammenligning skriver hele resten av treffets livsløp — opprettet,
invitert, svart — rundt 75 rader for de samme 25 personene. Treffgjennomføringen
produserer altså 8–10 ganger så mye historikk som alt annet til sammen, og
angring dobler tallet siden hver angring er en ny rad.

Det er ikke et databaseproblem. Det er et lesbarhetsproblem: Hendelser-fanen for
en jobbsøker går fra fire rader til femti, og de nye radene er de minst
interessante.

---

## Er koblingen jobbsøker/arbeidsgiver uklar?

Ja, og det er den mest kompliserte delen. Elleve av hendelsestypene beskriver
ikke en jobbsøker og ikke en arbeidsgiver, men **relasjonen mellom dem**.
Hendelsestabellene har ingen plass for en relasjon, så løsningen ble å skrive
til begge — samme handling blir to rader med hver sin halvdel av sannheten i
`hendelse_data`.

Konkrete problemer med det:

- **Ingen felles identitet.** De to radene har hver sin
  `jobbsoker_hendelse_id` / `arbeidsgiver_hendelse_id` og hvert sitt
  `tidspunkt` satt uavhengig av hverandre. Ingenting i basen sier at de er
  samme handling. Å telle «hvor mange interesser ble registrert» krever at man
  vet at man skal telle bare den ene tabellen.
- **De kan komme i utakt.** `HendelseWriter.forJobbsøkerOgArbeidsgiver` skriver
  begge i samme transaksjon, så de er konsistente i dag. Men invarianten er ikke
  håndhevet av skjemaet — en fremtidig skrivevei som glemmer den ene siden vil
  ikke feile.
- **Semantikken skurrer på arbeidsgiversiden.** `arbeidsgiver_hendelse` handlet
  fram til nå om arbeidsgiveren som deltaker på treffet: lagt til, oppdatert,
  behov endret. Nå inneholder den også vurderinger av enkeltpersoner. En
  `VURDERT`-rad på arbeidsgiveren sier ikke noe om arbeidsgiveren.
- **`personTreffId` i arbeidsgiverloggen.** Ikke et personvernbrudd —
  `personTreffId` er en treffintern UUID, ikke et fødselsnummer — men det gjør
  at en tabell som ellers ikke handler om personer, må vurderes i
  personvernsammenheng ved sletting og innsyn.
- **Dubletten er ubrukt.** Begrunnelsen i planen var at arbeidsgiveren skal
  kunne lese sin egen historikk uten å grave i jobbsøkerhendelser. Den
  visningen finnes ikke, og `ArbeidsgiverHendelserKort` har ingen etikett for
  noen av typene.

**Anbefalt retning hvis hendelsene beholdes:** skriv dem på **jobbsøkeren** og
legg `arbeidsgiverTreffId` i `hendelse_data`. Handlingen er en avgjørelse om en
person; arbeidsgiveren er konteksten. Trenger arbeidsgiverbildet en oversikt,
er kilden `interesse`, `intervju_fordeling` og `vurdering` — current
state-tabeller som allerede har begge fremmednøklene og er raske å spørre mot.
Det halverer volumet og fjerner utakt-risikoen.

---

## Notatene og parten

Notatene er det tydeligste tilfellet av forvirringen over, fordi de ser ut som
om de tilhører hver sin part. `Vurderingsnotat` har to prefikser:

| Prefiks | Betydning              | Eksempler                                                       |
| ------- | ---------------------- | --------------------------------------------------------------- |
| `AG_`   | «Arbeidsgiveren sier»  | `AG_GODT_INNTRYKK`, `AG_MANGLER_SPRÅK`, `AG_ANDRE_PASSET_BEDRE` |
| `JS_`   | «Jobbsøkeren sier»     | `JS_POSITIV`, `JS_REISEVEI`, `JS_HELSE_KAPASITET`               |

Frontend formaliserer det samme i `notatvalg.ts` med typen `Notatpart` og
overskriftene «Arbeidsgiveren sier» / «Jobbsøkeren sier».

Det er fristende å lese prefikset som en anvisning om hvilken hendelsestabell
notatet hører hjemme i. **Det er feil lesning.** Tre ting blandes sammen, og de
har tre forskjellige svar:

| Spørsmål                       | Svar                                                       | Ligger i                         |
| ------------------------------ | ---------------------------------------------------------- | -------------------------------- |
| Hvem **uttalte** seg?          | Arbeidsgiveren eller jobbsøkeren                           | Prefikset i notatkoden           |
| Hva **handler** notatet om?    | Alltid paret jobbsøker × arbeidsgiver                      | `vurdering`-raden                |
| Hvem **registrerte** det?      | Alltid en markedskontakt eller veileder                    | `aktøridentifikasjon` på hendelsen |

`AG_MANGLER_SPRÅK` er ikke en opplysning om arbeidsgiveren. Det er
arbeidsgiverens **uttalelse om jobbsøkeren**, ført i systemet av en
Nav-ansatt. Subjektet er personen uansett hvem som sa det. Prefikset er kilden,
ikke eieren.

### Anbefaling: skriv notathendelsene på jobbsøkeren

`NOTAT_LAGT_TIL` og `NOTAT_FJERNET` skrives **kun** på `jobbsoker_hendelse`,
med `arbeidsgiverTreffId` og `notat` i `hendelse_data`. Arbeidsgiverdubletten
fjernes. Fire grunner:

1. **Notatet begrunner en vurdering av en person.** Det leses alltid sammen med
   `vurdering`-raden for det paret, aldri alene.
2. **Personvern peker samme vei.** Flere av kodene er vurderinger av
   enkeltpersoner — `AG_MANGLER_KOMPETANSE`, `AG_MANGLER_SPRÅK` og særlig
   `JS_HELSE_KAPASITET`, som grenser mot helseopplysning. Slikt hører hjemme i
   jobbsøkerens spor, med jobbsøkerens sletteregler. Å duplisere det inn i
   `arbeidsgiver_hendelse` sprer personopplysninger til en tabell som ellers
   ikke handler om personer.
3. **Innsyn blir ett oppslag.** Ber en jobbsøker om innsyn, skal alt som er
   registrert om hen kunne hentes fra ett spor.
4. **Parten er ikke tapt.** Den kan utledes av prefikset ved visning —
   `finnNotat(verdi).part` finnes allerede i frontend. Hendelsen trenger ikke
   lagre den, og skal ikke lagre notatteksten.

Trenger arbeidsgiverbildet «hva har vi sagt om kandidatene våre?», er kilden
`vurdering` + `vurdering_notat` koblet på `arbeidsgiver_id` — begge har
fremmednøkkelen allerede. Historikken kan ved behov hentes med
`hendelse_data ->> 'arbeidsgiverTreffId'`; volumet her er hundrevis av rader per
treff, ikke millioner.

### Hvorfor hendelse og ikke bare kolonner på `vurdering_notat`

Alternativet er `opprettet_av` + `opprettet_tidspunkt` direkte på
`vurdering_notat`, etter mønsteret fra `jobbsoker.oppmote`. Det er billigere,
men svarer på mindre:

| Spørsmål                                   | Hendelse | Kolonner på raden       |
| ------------------------------------------ | -------- | ----------------------- |
| Hvem la til notatet, og når?               | ✅       | ✅                      |
| Hvem fjernet det, og når?                  | ✅       | ❌ – raden er borte     |
| Sto notatet der en periode og ble trukket? | ✅       | ❌                      |
| Rekkefølgen notatene kom i                 | ✅       | ✅                      |
| Synlig i Hendelser-fanen                   | ✅       | ❌ – krever ny visning  |

Fjerning er det avgjørende. «Hvem tok bort `AG_GODT_INNTRYKK`, og når?» er
nøyaktig den typen spørsmål som dukker opp når et notat er ført inn i ettertid
og noen senere er uenig i det. Kolonneløsningen kan ikke svare, fordi raden
ikke finnes lenger. Derfor beholdes begge typene, ikke bare `NOTAT_LAGT_TIL`.

### To ting å være klar over

- **Kaskaden skriver ikke notathendelser.** Fjernes et oppmøte, sletter
  `oppfølgingRepository.slettForJobbsøker` vurderinger og notater uten en
  `NOTAT_FJERNET` per rad. Tellingen ligger i `REGISTRERT_OPPMØTE_FJERNET`.
  Sletting av hele vurderingen via skjemaet skriver derimot `NOTAT_FJERNET` for
  hvert notat, siden `skrivHendelser` sammenligner listene uansett.
- **Notat i ettertid flytter fasen.** `lagreVurdering` kaller
  `settFase(..., VURDERING)`. Et notat ført inn uker etter treffet vil dermed
  sette treffgjennomføringen tilbake til vurderingsfasen. Verdt å sjekke om det
  er ønsket når notater skal kunne føres i ettertid.

---

## Anbefaling per hendelsestype

### Fjern (18 typer: 5 jobbsøker, 12 arbeidsgiver, 1 treff)

| Type                                                                  | Hvorfor                                                                     |
| --------------------------------------------------------------------- | --------------------------------------------------------------------------- |
| `PLASSERT_I_ROM`                                                      | Skrives bare ved manuell flytting; romnummeret ligger i `jobbsoker_rom_tildeling` og er irrelevant dagen etter |
| `INTERESSE_REGISTRERT` / `ANGRE_` (begge sider)                       | Ren avkryssing med autolagring; sluttilstanden ligger i `interesse`         |
| `SATT_OPP_TIL_INTERVJU` / `ANGRE_` (begge sider)                      | Skrives inkonsistent (hull 2 og 3); `intervju_fordeling` er sannhetskilden  |
| `ROTASJON_TILDELT`                                                    | Skrives én gang ved opprettelse, verdien ligger i `arbeidsgiver_rotasjon`   |
| `TREFFGJENNOMFØRING_ROMFORDELING_ENDRET`                              | Død enum-verdi, aldri skrevet                                               |
| Arbeidsgiverdublettene av `VURDERT`, `NOTAT_LAGT_TIL`, `NOTAT_FJERNET`, `ANDREGANGSINTERVJU_AVTALT`, `JOBBTILBUD_GITT` og deres angre-varianter | Speiling uten leser; se [Notatene og parten](#notatene-og-parten) |

### Behold (12 typer: 9 jobbsøker, 0 arbeidsgiver, 3 treff)

| Type                                           | Hvorfor                                                                                             |
| ---------------------------------------------- | --------------------------------------------------------------------------------------------------- |
| `REGISTRERT_OPPMØTE`                           | `jobbsoker.oppmote` har bare siste verdi; hendelsen bærer `deltakernummer`, som ikke finnes andre steder etter at raden slettes |
| `REGISTRERT_OPPMØTE_FJERNET`                   | **Eneste** spor av kaskadeslettingen. Uten den finnes ingen forklaring på at interesser og vurderinger forsvant |
| `VURDERT` (jobbsøker)                          | `forrigeVurdering` er den eneste kilden til at noen gikk fra «Aktuell» til «Ikke aktuell»           |
| `NOTAT_LAGT_TIL` / `NOTAT_FJERNET` (jobbsøker) | Notater føres ofte inn i **ettertid**, etter samtaler med arbeidsgiver eller jobbsøker. Hvem og når er da selve poenget — og `vurdering_notat` mister raden ved fjerning |
| `ANDREGANGSINTERVJU_AVTALT` / `ANGRE_` (jobbsøker) | Utfallsnær, lavt volum, kan bli etterspurt                                                     |
| `JOBBTILBUD_GITT` / `ANGRE_` (jobbsøker)       | Samme; merk at dette **ikke** er `FÅTT_JOBB`, som kommer fra Formidlinger                           |
| `TREFFGJENNOMFØRING_OPPRETTET` / `_OPPSETT_ENDRET` / `_INTERVJUFORDELING_FORDELT` | Få rader, treffnivå, og de to siste beskriver operasjoner som overskriver manuelt arbeid |

Med dette går et typisk treff fra ~640 til ~200 hendelsesrader, og
`arbeidsgiver_hendelse` går tilbake til å handle om arbeidsgivere.

---

## «Statuser i oversikten etter workshop-dagen»

Behovet dekkes allerede av current state, uten hendelser:

| Spørsmål i oversikten           | Kilde                                             |
| ------------------------------- | ------------------------------------------------- |
| Møtte personen opp?             | `jobbsoker.oppmote`                               |
| Hvilket deltakernummer?         | `deltakernummer`                                  |
| Hvilke arbeidsgivere interessert i? | `interesse`                                   |
| Satt opp til intervju hos hvem? | `intervju_fordeling` (`inkludert`)                |
| Hva ble vurderingen?            | `vurdering.vurdering`                             |
| Notater                         | `vurdering_notat`                                 |
| 2. intervju / jobbtilbud        | `vurdering.andregangsintervju`, `.jobbtilbud`     |

Alt leses allerede av `TreffgjennomføringReader`, som ikke rører
hendelsestabellene i det hele tatt. En oversikt etter workshop-dagen trenger
altså ingen nye hendelser — og heller ingen av de eksisterende.

Det eneste current state ikke svarer på, er **hvem som registrerte det og når**.
Trengs det, er det billigere å følge mønsteret fra `jobbsoker.oppmote` og legge
`sist_endret_av` + `sist_endret_tidspunkt` på `vurdering` og
`intervju_fordeling`, enn å beholde hundrevis av hendelsesrader for å utlede det
samme.

Merk at hendelsestabellene **ikke** er det lovpålagte sporet. Audit-logging
dekker oppslag og går til ArcSight via `audit-log`-biblioteket, se
[audit-og-secure-log.md](../../3-sikkerhet/audit-og-secure-log.md). Å fjerne
hendelser her er derfor en produktbeslutning, ikke en compliance-beslutning.

---

## Slik ble det gjennomført

Rekkefølgen betyr noe — slutt å skrive før du sletter.

1. **Sluttet å skrive.** Kallene i `MatchingService`, `MøteplanService` og
   `OppfølgingService` er fjernet. `skrivRomhendelser` og
   `skrivFordelingshendelser` er borte helt. `skrivHendelser` i
   `OppfølgingService` skriver nå bare på jobbsøkeren. Parameteren `navIdent` er
   fjernet fra `settInteresse`, `lagreIntervjufordeling` og `lagreRomfordeling`,
   siden den bare fantes for hendelsesskrivingen.
2. **Enum-verdiene er fjernet** i `typer.kt`: 5 jobbsøkertyper, alle 12
   arbeidsgivertypene for treffgjennomføring, og den døde
   `TREFFGJENNOMFØRING_ROMFORDELING_ENDRET`. `ArbeidsgiverHendelsestype` har fått
   KDoc som forklarer hvorfor den ikke lenger har typer for treffgjennomføringen.
3. **`forJobbsøkerOgArbeidsgiver` er fjernet** fra `HendelseWriter`. Ingen
   parskriving finnes lenger — arbeidsgiverkoblingen ligger i
   `hendelse_data.arbeidsgiverTreffId`.
4. **Testene er oppdatert.** `TreffgjennomføringKomponentTest` og
   `TreffgjennomføringKarakteriseringTest` dokumenterer nå at interesse og
   romfordeling endrer current state uten å skrive hendelser. Assertionene ble
   snudd, ikke slettet, slik at fraværet av hendelser er testet.
5. **Ingen migrasjon.** V14 er aldri deployet, så hendelsene som fjernes har
   aldri eksistert i noe miljø. En slettemigrasjon ville ryddet i rader som ikke
   finnes. V14 trengte heller ingen endring: `hendelsestype` er en ren
   `text`-kolonne uten `CHECK`, så de fjernede enum-verdiene satte aldri spor i
   skjemaet. Lokale utviklerbaser som allerede har kjørt V14 kan ha rader
   liggende — de ryddes enklest ved å resette basen.
6. **`parseHendelseData` ser nå `hendelse_data`.** Både backend
   (`HendelseDataDto`) og frontend (`useRekrutteringstreff.ts`) har fått
   DTO-er/skjemaer for `REGISTRERT_OPPMØTE`, `REGISTRERT_OPPMØTE_FJERNET`,
   `VURDERT`, `NOTAT_LAGT_TIL`, `NOTAT_FJERNET`, `ANDREGANGSINTERVJU_AVTALT`,
   `ANGRE_ANDREGANGSINTERVJU_AVTALT`, `JOBBTILBUD_GITT` og
   `ANGRE_JOBBTILBUD_GITT`.
7. **Frontend viser de beholdte typene.** `constants.ts` har fått enum-verdier og
   etiketter for de 9 jobbsøkertypene og 3 trefftypene. `HendelseLabel.tsx` viser
   i tillegg en detaljlinje under etiketten: notatet med part
   («Arbeidsgiveren sier: savner språknivå») via `finnNotat` og `PARTSETIKETT`,
   vurderingsovergangen, deltakernummeret, og hvor mange registreringer en
   oppmøtefjerning slettet.

---

## Åpne spørsmål

1. Skal `settInteresse` og `lagreVurdering` ha `krevWorkOp()`? De skriver i dag
   på alle treff, i strid med planen. Uavhengig av hendelsesspørsmålet.
2. Skal `ANDREGANGSINTERVJU_AVTALT` og `JOBBTILBUD_GITT` heller bli felter med
   `sist_endret_av` enn hendelser? De er de eneste beholdte typene som ikke
   beskriver noe destruktivt.
3. ~~Trenger arbeidsgiverbildet en historikk i det hele tatt?~~ Avklart:
   current state holder. Parskrivingen er fjernet, og arbeidsgiveren finnes i
   `hendelse_data.arbeidsgiverTreffId` på jobbsøkerhendelsen.
4. Skal notater kunne føres i ettertid uten at fasen settes tilbake til
   `VURDERING`? Se [To ting å være klar over](#to-ting-å-være-klar-over).
5. Bør `NOTAT_LAGT_TIL` bære parten (`AG`/`JS`) eksplisitt i `hendelse_data`,
   eller er det nok at den kan utledes av prefikset? Anbefalingen er å utlede
   den, men det forutsetter at prefikskonvensjonen holdes ved like i
   `Vurderingsnotat`.
