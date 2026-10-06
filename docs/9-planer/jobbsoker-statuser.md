# Jobbsøkerstatus: regler og overganger

**Status:** Implementert på `refactor-status`  
**Fasit:** `JobbsøkerstatusPermutasjonKomponentTest` kjører 77 statusforløp og 15 forløp for
avlysning, fullføring og endring mot ekte database gjennom de samme tjenestene som API-et.

Backend utleder statusen fra hendelsesloggen i `Jobbsøkerstatusregler` og lagrer resultatet i
`jobbsoker.status`. Svar, oppmøte og formidling kan derfor registreres og angres i hvilken som
helst rekkefølge, og statusen blir den samme som om vi leste loggen på nytt.

## Regelen

Statusen er den første regelen ovenfra som gjelder:

| Prioritet | Status      | Gjelder når                                                           |
| --------- | ----------- | --------------------------------------------------------------------- |
| 1         | `FÅTT_JOBB` | Siste av `FÅTT_JOBB`/`ANGRE_FÅTT_JOBB` er `FÅTT_JOBB`                 |
| 2         | `MØTT_OPP`  | Siste av `REGISTRERT_OPPMØTE`/`REGISTRERT_OPPMØTE_FJERNET` er oppmøte |
| 3         | `SVART_JA`  | Gjeldende svar er ja                                                  |
| 3         | `SVART_NEI` | Gjeldende svar er nei                                                 |
| 4         | `INVITERT`  | Personen har en `INVITERT`-hendelse                                   |
| 5         | `SLETTET`   | Siste av `SLETTET`/`OPPRETTET` er `SLETTET`                           |
| 6         | `LAGT_TIL`  | Ellers                                                                |

Gjeldende svar (`Jobbsøkerstatusregler.gjeldendeSvar`) er den nyeste av svarhendelsene, uansett
status. Ja kommer fra `SVART_JA_TIL_INVITASJON` med eller uten `_AV_EIER`, og nei tilsvarende.
`INVITERT` og `SVAR_FJERNET_AV_EIER` nullstiller svaret.

Konsekvensen er at svaret er en egen akse. Den som har møtt opp og deretter svarer nei, har status
`MØTT_OPP` og svar nei. Fjernes oppmøtet, blir statusen `SVART_NEI`, ikke svaret fra før oppmøtet.

`SLETTET` ligger rett over `LAGT_TIL`, fordi bare `LAGT_TIL` uten registreringer kan slettes.
Ved sletting finnes det derfor ingen gjeldende invitasjon, svar, oppmøte eller formidling som kan
vinne over. Slettet er en endestasjon fordi tjenestene avviser hendelser for slettede personer.
Bare `OPPRETTET` (ny innlegging) opphever slettingen. Lekker en hendelse forbi en guard, vil
statusen endre seg, så nye skrivende tjenester må sjekke `SLETTET`.

| Skriver            | Avviser slettet med                                         | Serialisert mot sletting med     |
| ------------------ | ----------------------------------------------------------- | -------------------------------- |
| Svar (eier/borger) | `krevIkkeSlettetJobbsøker` (404)                            | `låsJobbsøker`                   |
| Fått jobb          | `krevIkkeSlettetJobbsøker` i `registrerFåttJobb` (404)      | `låsJobbsøker`                   |
| Angre fått jobb    | Skriver bare når status er `FÅTT_JOBB`                      | `låsJobbsøker` i `slett`         |
| Invitasjon         | Inviterer bare `LAGT_TIL`                                   | `hentStatus` (`FOR UPDATE`)      |
| Oppmøte            | `Treffkontekst` tar ikke med slettede (400)                 | `medLåstTreff`                   |

Formidling sjekker jobbsøkeren før kallene til stilling- og kandidatliste-API-et, men skriver
`FÅTT_JOBB` først etterpå. Derfor sjekker `registrerFåttJobb` på nytt under lås.

## Andre regler i tjenestene

- `inviter` inviterer bare personer med status `LAGT_TIL`. Andre hoppes over uten feilmelding.
  Den som har svart eller møtt uten invitasjon, kan derfor ikke inviteres etterpå. Det er
  bevisst, fordi invitasjonen sender SMS.
- Eieren kan svare for en som ikke er invitert, og en person kan registreres som møtt uten svar
  eller invitasjon.
- «Fått jobb» står til siste aktive formidling er angret. `FormidlingService.slett` låser
  jobbsøkeren og skriver `ANGRE_FÅTT_JOBB` bare når ingen annen formidling med utfall står igjen.
- Fremmøtt (`OppmøteRepository.hentFremmøtteJobbsøkere`) følger siste oppmøtehendelse, ikke
  statusen. «Fått jobb» uten registrert oppmøte er ikke fremmøtt, fordi oppmøte ikke er
  obligatorisk. Et registrert oppmøte står når personen får jobb. Å fjerne et oppmøte som ikke
  er registrert, gjør ingenting.
- Bare `LAGT_TIL` uten registreringer kan slettes (422 ellers).

## Overganger

Alle forløp starter med «Legg til». «Ja/Nei» er svar fra eier, «(borger)» er svar fra Min side.
«Møtt» er avkrysning i oppmøtelisten. «Fått jobb A» og «Fått jobb B» er formidlinger til to
forskjellige arbeidsgivere, og «Angre A» sletter formidlingen til A. Kolonnen
«Møtt» viser om personen står som fremmøtt.

### Grunnflyt

| Forløp                    | Status      | Svar | Møtt |
| ------------------------- | ----------- | ---- | ---- |
| (bare lagt til)           | `LAGT_TIL`  | –    | nei  |
| Inviter                   | `INVITERT`  | –    | nei  |
| Inviter → Ja              | `SVART_JA`  | ja   | nei  |
| Inviter → Ja (borger)     | `SVART_JA`  | ja   | nei  |
| Inviter → Ja → Fjern svar | `INVITERT`  | –    | nei  |
| Inviter → Ja → Nei        | `SVART_NEI` | nei  | nei  |
| Inviter → Ja → Inviter    | `SVART_JA`  | ja   | nei  |

Den andre invitasjonen hoppes over, fordi statusen ikke er `LAGT_TIL`.

### Svar og oppmøte uten invitasjon

| Forløp                       | Status      | Svar | Møtt |
| ---------------------------- | ----------- | ---- | ---- |
| Ja                           | `SVART_JA`  | ja   | nei  |
| Ja → Fjern svar              | `LAGT_TIL`  | –    | nei  |
| Ja → Inviter                 | `SVART_JA`  | ja   | nei  |
| Møtt                         | `MØTT_OPP`  | –    | ja   |
| Møtt → Fjern møtt            | `LAGT_TIL`  | –    | nei  |
| Møtt → Inviter               | `MØTT_OPP`  | –    | ja   |
| Møtt → Fjern møtt → Inviter  | `INVITERT`  | –    | nei  |
| Møtt → Nei → Fjern møtt      | `SVART_NEI` | nei  | nei  |
| Ja → Møtt → Nei → Fjern møtt | `SVART_NEI` | nei  | nei  |

### Møtt opp og svar

| Forløp                                          | Status      | Svar | Møtt |
| ----------------------------------------------- | ----------- | ---- | ---- |
| Inviter → Ja → Møtt                             | `MØTT_OPP`  | ja   | ja   |
| Inviter → Ja → Møtt → Nei                       | `MØTT_OPP`  | nei  | ja   |
| Inviter → Ja → Møtt → Nei → Fjern møtt          | `SVART_NEI` | nei  | nei  |
| Inviter → Ja → Møtt → Nei (borger) → Fjern møtt | `SVART_NEI` | nei  | nei  |
| Inviter → Ja → Møtt → Fjern møtt                | `SVART_JA`  | ja   | nei  |
| Inviter → Nei → Møtt → Ja → Fjern møtt          | `SVART_JA`  | ja   | nei  |
| Inviter → Møtt → Fjern møtt                     | `INVITERT`  | –    | nei  |
| Inviter → Ja → Møtt → Fjern svar → Fjern møtt   | `INVITERT`  | –    | nei  |
| Inviter → Ja → Møtt → Nei → Fjern møtt → Møtt   | `MØTT_OPP`  | nei  | ja   |

### Fått jobb

| Forløp                                                         | Status      | Svar | Møtt |
| -------------------------------------------------------------- | ----------- | ---- | ---- |
| Fått jobb A                                                    | `FÅTT_JOBB` | –    | nei  |
| Fått jobb A → Angre A                                          | `LAGT_TIL`  | –    | nei  |
| Inviter → Ja → Fått jobb A                                     | `FÅTT_JOBB` | ja   | nei  |
| Inviter → Ja → Fått jobb A → Angre A                           | `SVART_JA`  | ja   | nei  |
| Inviter → Ja → Fått jobb A → Nei                               | `FÅTT_JOBB` | nei  | nei  |
| Inviter → Ja → Fått jobb A → Nei → Angre A                     | `SVART_NEI` | nei  | nei  |
| Inviter → Ja → Møtt → Fått jobb A                              | `FÅTT_JOBB` | ja   | ja   |
| Inviter → Ja → Møtt → Fått jobb A → Angre A                    | `MØTT_OPP`  | ja   | ja   |
| Inviter → Ja → Møtt → Fått jobb A → Nei → Angre A → Fjern møtt | `SVART_NEI` | nei  | nei  |
| Inviter → Ja → Møtt → Fått jobb A → Fjern møtt → Angre A       | `SVART_JA`  | ja   | nei  |
| Inviter → Ja → Fått jobb A → Møtt                              | `FÅTT_JOBB` | ja   | ja   |
| Inviter → Ja → Fått jobb A → Møtt → Angre A                    | `MØTT_OPP`  | ja   | ja   |
| Inviter → Ja → Fått jobb A → Angre A → Fått jobb A             | `FÅTT_JOBB` | ja   | nei  |

### To formidlinger

| Forløp                                                              | Status      | Svar | Møtt |
| ------------------------------------------------------------------- | ----------- | ---- | ---- |
| Inviter → Ja → Fått jobb A → Fått jobb B                            | `FÅTT_JOBB` | ja   | nei  |
| Inviter → Ja → Fått jobb A → Fått jobb B → Angre A                  | `FÅTT_JOBB` | ja   | nei  |
| Inviter → Ja → Fått jobb A → Fått jobb B → Angre A → Angre B        | `SVART_JA`  | ja   | nei  |
| Inviter → Ja → Møtt → Fått jobb A → Fått jobb B → Angre B → Angre A | `MØTT_OPP`  | ja   | ja   |
| Inviter → Ja → Fått jobb A → Fått jobb B → Angre A → Nei → Angre B  | `SVART_NEI` | nei  | nei  |

### Sletting

| Forløp                                            | Status      | Merknad                                      |
| ------------------------------------------------- | ----------- | -------------------------------------------- |
| Slett                                             | `SLETTET`   |                                              |
| Slett → Legg til igjen                            | `LAGT_TIL`  | Samme `personTreffId`, gammel historikk står |
| Møtt → Fjern møtt → Slett → Legg til igjen → Møtt | `MØTT_OPP`  |                                              |
| Inviter → Slett                                   | `INVITERT`  | Avvist, bare `LAGT_TIL` kan slettes          |
| Møtt → Slett                                      | `MØTT_OPP`  | Avvist                                       |
| Fått jobb A → Slett                               | `FÅTT_JOBB` | Avvist                                       |
| Fått jobb A → Angre A → Slett                     | `SLETTET`   |                                              |
| Slett → Ja                                        | `SLETTET`   | Avvist (404)                                 |
| Slett under formidling (mellom sjekk og utfall)   | `SLETTET`   | Formidlingen avvises (404)                   |

## Avlysning, fullføring og endring

`Jobbsøkerstatusregler.hendelseNårTreffetAvlyses` og `hendelseNårTreffetFullføres` avgjør hvilken
hendelse hver jobbsøker får, og dermed hva som skjer med aktivitetskortet. Ingen hendelse betyr at
kortet blir stående. Slettede og usynlige jobbsøkere er ikke med i utvalget og får aldri hendelse.

### Avlysning

Avlysning avgjøres først av om det nyeste svaret er ja (`harSvartJa`), uansett status. Statusen
brukes bare for å finne den som er invitert uten å ha svart.

| Jobbsøker                                               | Hendelse                  | Aktivitetskort     | SMS |
| ------------------------------------------------------- | ------------------------- | ------------------ | --- |
| Nyeste svar er ja (`SVART_JA`, `MØTT_OPP`, `FÅTT_JOBB`) | `SVART_JA_TREFF_AVLYST`   | `AVBRUTT`          | Ja  |
| `INVITERT`                                              | `IKKE_SVART_TREFF_AVLYST` | `AVBRUTT`          | Nei |
| `SVART_NEI`                                             | –                         | allerede `AVBRUTT` | Nei |
| `MØTT_OPP` eller `FÅTT_JOBB` uten ja                    | –                         | uendret            | Nei |
| `LAGT_TIL`                                              | –                         | har ikke kort      | Nei |

Den som har svart ja og møtt opp, får SMS hvis treffet avlyses etterpå. Det skjer sjelden, men
personen har sagt ja og skal få beskjed.

### Fullføring

Fullføring tar status og om personen er invitert (`erInvitert`). Aktivitetskortet opprettes ved
invitasjon, så den som ikke er invitert, har ikke kort og får ingen hendelse. Her avgjør statusen, så regelen har én
`when`-gren per status og ingen `else`. Kommer det en ny status, kompilerer ikke koden før noen har
bestemt hva den skal gi ved fullføring.

| Status                                   | Hendelse                    | Aktivitetskort     |
| ---------------------------------------- | --------------------------- | ------------------ |
| `FÅTT_JOBB`, `MØTT_OPP` eller `SVART_JA` | `SVART_JA_TREFF_FULLFØRT`   | `FULLFORT`         |
| `INVITERT`                               | `IKKE_SVART_TREFF_FULLFØRT` | `AVBRUTT`          |
| `SVART_NEI`                              | –                           | allerede `AVBRUTT` |
| `LAGT_TIL` eller `SLETTET`               | –                           | har ikke kort      |
| Ikke invitert, uansett status            | –                           | har ikke kort      |

Den som har møtt opp eller fått jobb, får kortet fullført uansett svar, også etter et nei.
Aktivitetskort-appen setter bare `FULLFORT` når meldingen har `svar=true`, så hendelsen heter
`SVART_JA_TREFF_FULLFØRT` også for disse.

### Endring

Når et publisert treff endres, får alle med en `INVITERT`-hendelse `TREFF_ENDRET_ETTER_PUBLISERING`,
som oppdaterer aktivitetskortet. Varselet om endringen
(`TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON`) går til alle med gjeldende svar ja
(`skalVarslesOmEndringer`). For alle forløp tjenestene kan lage, er det de samme som får
`SVART_JA_TREFF_AVLYST`.

### Forløp

`JobbsøkerstatusPermutasjonKomponentTest` kjører hvert forløp under på tre nye, publiserte treff:
ett avlyses, ett fullføres og ett endres.

| Forløp                      | Status      | Svar | Ved avlysning             | Ved fullføring              |
| --------------------------- | ----------- | ---- | ------------------------- | --------------------------- |
| (bare lagt til)             | `LAGT_TIL`  | –    | –                         | –                           |
| Inviter                     | `INVITERT`  | –    | `IKKE_SVART_TREFF_AVLYST` | `IKKE_SVART_TREFF_FULLFØRT` |
| Inviter → Ja                | `SVART_JA`  | ja   | `SVART_JA_TREFF_AVLYST`   | `SVART_JA_TREFF_FULLFØRT`   |
| Inviter → Ja → Fjern svar   | `INVITERT`  | –    | `IKKE_SVART_TREFF_AVLYST` | `IKKE_SVART_TREFF_FULLFØRT` |
| Inviter → Nei               | `SVART_NEI` | nei  | –                         | –                           |
| Inviter → Ja → Møtt         | `MØTT_OPP`  | ja   | `SVART_JA_TREFF_AVLYST`   | `SVART_JA_TREFF_FULLFØRT`   |
| Inviter → Ja → Fått jobb A  | `FÅTT_JOBB` | ja   | `SVART_JA_TREFF_AVLYST`   | `SVART_JA_TREFF_FULLFØRT`   |
| Inviter → Ja → Møtt → Nei   | `MØTT_OPP`  | nei  | –                         | `SVART_JA_TREFF_FULLFØRT`   |
| Inviter → Nei → Møtt        | `MØTT_OPP`  | nei  | –                         | `SVART_JA_TREFF_FULLFØRT`   |
| Inviter → Møtt              | `MØTT_OPP`  | –    | –                         | `SVART_JA_TREFF_FULLFØRT`   |
| Inviter → Møtt → Fjern møtt | `INVITERT`  | –    | `IKKE_SVART_TREFF_AVLYST` | `IKKE_SVART_TREFF_FULLFØRT` |
| Inviter → Fått jobb A       | `FÅTT_JOBB` | –    | –                         | `SVART_JA_TREFF_FULLFØRT`   |
| Ja                          | `SVART_JA`  | ja   | `SVART_JA_TREFF_AVLYST`   | –                           |
| Møtt                        | `MØTT_OPP`  | –    | –                         | –                           |
| Slett                       | `SLETTET`   | –    | –                         | –                           |

## Frontend

«Endre svar» er sperret for `LAGT_TIL`, `MØTT_OPP` og `SLETTET`. Ved møtt opp må eieren fjerne
oppmøtet først. Rydding for den som er invitert og har møtt uten å svare, gjøres i
aktivitetskortløsningen. For `FÅTT_JOBB` er valget åpent, men dialogen leser svaret fra statusen
og viser derfor «Ikke svart».

## Åpne punkter

- Tellingene for «svart ja» (`hentAntallJobbsøkereSvartJa`, `useInviteringsStatus`) leser
  statusen. Den som har svart ja og deretter møtt opp eller fått jobb, telles ikke. Endres ikke nå.
- Invitasjon etter svar eller oppmøte uten invitasjon vurderes på nytt senere.
