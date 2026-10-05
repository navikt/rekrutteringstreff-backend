# Treffgjennomføring

Treffgjennomføringen er fanen arrangøren bruker på selve treffdagen. Der registrerer
arrangøren oppmøte, fordeler fremmøtte på rom, registrerer interesse, fordeler
intervjuer og registrerer vurderinger. WorkOp bruker alle seks stegene. Vanlige treff
bruker fire, og er foreløpig bare tilgjengelig lokalt.

Dokumentet beskriver løsningen slik den er nå. Bakgrunn og avveiinger står i planene
under [Bakgrunn](#bakgrunn).

## Stegene

| Steg                       | WorkOp | Vanlig treff | Lagres i                                                          |
| -------------------------- | :----: | :----------: | ----------------------------------------------------------------- |
| 1. Oppmøte                 |   ✅   |      ✅      | `jobbsoker.status = 'MØTT_OPP'`, `deltakernummer` (bare WorkOp)   |
| 2. Rom og rotasjon         |   ✅   |      –       | `moteoppsett`, `jobbsoker_romtildeling`, `arbeidsgiver_rotasjon`  |
| 3. Interesse               |   ✅   |      ✅      | `interesse`                                                       |
| 4. Intervjufordeling       |   ✅   |      –       | `intervjufordeling`                                               |
| 5. Vurdering og oppfølging |   ✅   |      ✅      | `vurdering`                                                       |
| 6. Oppsummering            |   ✅   |      ✅      | Ingenting. Steget leser bare.                                     |

`treffgjennomforing.gjeldende_steg` viser hvor langt arrangøren har kommet. Det går bare
framover, også når registreringer angres. Arrangøren kan alltid gå tilbake og rette.

## Tilgang og miljø

Tilgang krever rollen arbeidsgiverrettet, og at brukeren er eier av treffet eller
utvikler (`krevEierEllerUtvikler`).

| Miljø  | Frontend                         | Backend                                                         |
| ------ | -------------------------------- | --------------------------------------------------------------- |
| Prod   | Fanen er skjult, skriving stoppes | Interesse og vurdering avvises. WorkOp kan ikke opprettes.      |
| Dev    | Bare WorkOp                      | Interesse og vurdering krever WorkOp                            |
| Lokalt | Alle treff                       | Alle treff                                                      |

Regelen står i `erTreffgjennomføringTilgjengelig` i frontend og i
`Treffkontekst.krevWorkOpEllerLokalUtvikling` i backend. Rom og intervjufordeling krever
WorkOp i alle miljøer (`krevWorkOp`).

## Koden

Backend, `rekrutteringstreff-api`:

| Pakke                         | Ansvar                                                                                  |
| ----------------------------- | --------------------------------------------------------------------------------------- |
| `treffgjennomføring`          | Controller, felles skriving (`TreffgjennomføringWriter`) og lesing (`TreffgjennomføringReader`) |
| `treffgjennomføring/møteplan` | Møteoppsett, romfordeling og rotasjon. `Møteplansynk` holder lagrede plasseringer i takt |
| `treffgjennomføring/matching` | Interesse og intervjufordeling                                                          |
| `oppfølging`                  | Vurdering, notater, 2. intervju og jobbtilbud                                           |
| `jobbsoker/oppmøte`           | Oppmøte og deltakernummer                                                               |

Frontend, `rekrutteringsbistand-frontend`:

- `app/api/rekrutteringstreff/[...slug]/treffgjennomføring/` har skjema, endepunkter,
  mutasjoner og MSW-mocken.
- `app/rekrutteringstreff/[rekrutteringstreffId]/_ui/treffgjennomføring/` har én mappe per
  steg, i tillegg til `navigasjon/` og `felles/`.

## Skriving og lesing

Alle skrivinger går gjennom `TreffgjennomføringWriter.skriv`. Den tar radlås på treffet
(`medLåstTreff`), henter fersk kontekst og returnerer hele aggregatet. Samtidige
skrivinger på samme treff venter derfor på hverandre. Endringer i arbeidsgivere og
sletting av jobbsøkere tar samme lås.

`GET …/treffgjennomforing-og-oppfolging` skriver ingenting. Et treff uten lagret
gjennomføring gir et tomt aggregat.

Frontend bruker svaret fra skrivingen direkte og har to lagringsmønstre:

- `useSekvensiellAutolagring` legger endringer i kø og viser dem før serveren har svart.
  Brukes for oppmøte, interesse og vurdering.
- `useBekreftetLagring` lagrer én endring om gangen og viser bare det serveren svarer.
  Brukes for rom og intervjufordeling.

Feiler en lagring, henter frontend bekreftet tilstand før brukeren kan fortsette.

## Endepunkter

Alle stier starter med `/api/rekrutteringstreff/{id}`. Alle skrivinger svarer med hele
aggregatet.

| Metode | Sti                                         | Gjør                                                        |
| ------ | ------------------------------------------- | ----------------------------------------------------------- |
| GET    | `/treffgjennomforing-og-oppfolging`         | Henter hele aggregatet                                      |
| POST   | `/treffgjennomforing-og-oppfolging/jobbsokere` | Henter jobbsøkerne med id, navn, status og fødselsnummer. Usynlige tas med på WorkOp, uten fødselsnummer |
| PUT    | `/treffgjennomforing/oppmote`               | Registrerer eller angrer oppmøte                            |
| PUT    | `/treffgjennomforing/moteoppsett`           | Setter tidene. Første kall oppretter rom og rotasjon        |
| PUT    | `/treffgjennomforing/romfordeling/{person}` | Flytter én person til et rom                                |
| POST   | `/treffgjennomforing/romfordeling/fordel`   | Fordeler alle fremmøtte på nytt                             |
| PUT    | `/treffgjennomforing/interesse`             | Setter eller fjerner én interesse                           |
| PUT    | `/treffgjennomforing/intervjufordeling`     | Lagrer rekkefølgen hos én arbeidsgiver                      |
| POST   | `/treffgjennomforing/intervjufordeling/fordel` | Fordeler intervjuene på nytt                             |
| PUT    | `/treffgjennomforing/steg`                  | Flytter gjeldende steg framover                             |
| PUT    | `/oppfolging/vurderinger`                   | Setter eller fjerner vurderingen for én jobbsøker hos én arbeidsgiver |

## Regler

Rom og rotasjon:

- Antall rom er antall arbeidsgivere, minst 1. Tallet beregnes ved lesing og lagres ikke.
- Nye fremmøtte og nye arbeidsgivere får plass ved lesing. `Møteplansynk` lagrer
  plasseringene før og etter endringen, så ingen bytter rom fordi antallet endret seg.
- Rekkefølgen i et rom følger deltakernummeret.
- Deltakernummer deles ut ved oppmøte på WorkOp. Nummeret starter på 1, følger personen
  og gjenbrukes aldri av andre.

Interesse, intervjufordeling og vurdering:

- Interesse og vurdering krever at jobbsøkeren har møtt opp.
- Når intervjufordelingen på en WorkOp er påbegynt, følger den interessene. En ny
  interesse legges sist, en fjernet interesse tas ut. Vanlige treff har ingen
  intervjufordeling.
- Fordelingen hos en arbeidsgiver må inneholde nøyaktig de som har interesse for den.
  Ellers svarer backend 409, fordi klienten da bygger på utdatert tilstand.

Oppmøte og status:

- Statusen utledes fra hendelsene i `Jobbsøkerstatusregler`. Gjelder flere tilstander
  samtidig, vinner den øverste: slettet, fått jobb, møtt opp, gyldig svar, invitert og
  lagt til. Gyldig svar er ett nivå: det nyeste svaret gir `SVART_JA` eller `SVART_NEI`.
- Svaret leses med `Jobbsøkerstatusregler.gjeldendeSvar`, og det nyeste svaret gjelder. Svarer
  jobbsøkeren etter oppmøtet, endres svaret, men statusen er fortsatt `MØTT_OPP`.
- Angres oppmøtet eller formidlingen, blir statusen den neste i prioriteten, for eksempel
  det nyeste svaret.
- «Fått jobb» regnes også som fremmøtt.

Sperrer:

- Oppmøtet kan ikke fjernes så lenge jobbsøkeren har interesse, intervjufordeling eller
  vurdering (409).
- Interessen kan ikke fjernes så lenge jobbsøkeren har en vurdering hos arbeidsgiveren (409).
- Arbeidsgiveren kan ikke slettes med personer i rommet den starter i, eller med
  interesser, intervjufordeling eller vurderinger (409). Formidling alene sperrer ikke.
- En jobbsøker kan bare slettes med status `LAGT_TIL` og uten registreringer (422).

409-svarene er `ProblemDetails`. `feil` sier hva som er galt, og `hint` sier hva som må
ryddes. Skjermbildene viser egne tekster.

## Hendelser

Hendelsene skrives bare på jobbsøkeren. Arbeidsgiveren ligger i `hendelse_data`, og
frontend viser navnet i detaljteksten.

| Hendelse                                         | Skrives når                                   | `hendelse_data`                                     |
| ------------------------------------------------ | --------------------------------------------- | --------------------------------------------------- |
| `REGISTRERT_OPPMØTE`                             | Oppmøtet registreres                          | `deltakernummer` (bare WorkOp)                      |
| `REGISTRERT_OPPMØTE_FJERNET`                     | Oppmøtet angres                               | –                                                   |
| `VURDERT`                                        | Vurderingen endres                            | `arbeidsgiverTreffId`, `vurdering`, `forrigeVurdering` |
| `NOTAT_LAGT_TIL`, `NOTAT_FJERNET`                | Per notat                                     | `arbeidsgiverTreffId`, `notat`                      |
| `AVTALT_INTERVJU`                                | 2. intervju krysses av                        | `arbeidsgiverTreffId`, `dato`                       |
| `AVTALT_INTERVJU_ANGRET`                         | 2. intervju fjernes                           | `arbeidsgiverTreffId`                               |
| `AVTALT_INTERVJU_DATO_ENDRET`                    | Datoen for 2. intervju settes, flyttes eller fjernes | `arbeidsgiverTreffId`, `dato`                |
| `JOBBTILBUD_GITT`, `ANGRE_JOBBTILBUD_GITT`       | Jobbtilbud krysses av eller fjernes           | `arbeidsgiverTreffId`                               |

På treffet skrives `TREFFGJENNOMFØRING_OPPRETTET`, `TREFFGJENNOMFØRING_OPPSETT_ENDRET` og
`TREFFGJENNOMFØRING_INTERVJUFORDELING_FORDELT`. Rom, interesse og intervjufordeling
skriver ingen hendelser. Tabellene er selv fasiten.

## Tester

Backend:

- `TreffgjennomføringKomponentTest` kjører reglene over HTTP mot ekte database.
- `TreffgjennomføringAutorisasjonsTest` og `TreffgjennomføringPersisteringTest` dekker
  tilgang og lagring.
- `TreffgjennomføringReaderTest` låser antall spørringer ved lesing.
- `OppmøteServiceTest` dekker status og deltakernummer ved oppmøte.
- `MiljøsperreTest` dekker miljøregelen.
- `RomfordelerTest` og `IntervjufordelerTest` dekker fordelingsreglene.

Frontend har testene i `tests/rekrutteringstreff/treffgjennomføring/{enhet,e2e}`.
MSW-mocken i `treffgjennomføringMockDomene.msw.ts` speiler backendreglene. Endres en
regel i backend, må mocken endres også.

## Kjente mangler

Se «Kjente feil og uavklarte regler» i
[akseptansetester-workop.md](../7-akseptansetest-og-ros/akseptansetester-workop.md).

## Bakgrunn

Planene beskriver hvordan løsningen ble til. De er historikk, og der de avviker fra
dette dokumentet, gjelder dette dokumentet.

- [Oppmøte, rom og fordeling](../9-planer/workop/treffgjennomforing-oppmote-rom-og-fordeling.md): design og flyt
- [Domeneoppdeling](../9-planer/workop/treffgjennomforing-domeneoppdeling.md): oppdelingen i pakker
- [Paginert oppmøte og romflytting](../9-planer/workop/treffgjennomforing-oppmote-og-romflytting.md)
- [Hendelser i treffgjennomføringen](../9-planer/workop/hendelser-i-treffgjennomforing.md)
- [ROS for WorkOp](../9-planer/workop/ros-workop.md)
