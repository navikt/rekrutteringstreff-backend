# Treffgjennomføring: paginert oppmøte og trygg romflytting

## Rammer

- Endre `rekrutteringsbistand-frontend` og `rekrutteringstreff-backend`.
- Treffgjennomføring er ikke i produksjon, og denne frontend er eneste
  API-konsument. Gjør endringene samlet og fjern det gamle rom-API-et direkte.
  Ingen utrullingsplan, overgangsstøtte eller ny API-versjon er nødvendig.
- Behold dagens personfelter, synlighetsregler og tilgangssjekker for
  eiere/utviklere. Romoperasjoner er fortsatt bare for WorkOp.
- Ingen nye avhengigheter, databasemigrasjoner eller endringer i Nais, CI/CD
  eller autentisering. Eksisterende feilhåndtering og HTTP-metrikker videreføres;
  ikke legg personopplysninger i logger.
- Prioriter lesbarhet og små, tydelige funksjoner. Gjenbruk eksisterende hjelpere
  fremfor å lage et generelt lagringsrammeverk. Behold navnene `toggleOppmøte`,
  `dravisning` og `settDravisning`.
- Responsive kortbredder fra tidligere review er **ikke** del av oppgaven.
  Ikke commit eller stage endringene; utvikleren gjør dette selv.

## 1. Delt datahenting: oppmøteside og komplett fremmøtteliste

`useAlleJobbsøkere` er erstattet med to databehov. Begge bruker eksisterende
`POST /jobbsoker/sok`, som tillater 1–100 personer per forespørsel.
Ingen ny backendkontrakt er nødvendig.

- Jobbsøkerfanen beholder `useJobbsøkerSøk`. Steg 1 bruker en avgrenset
  `useJobbsøkereForOppmøte` mot samme API, og henter bare valgt side med
  `antallPerSide: 100` og stabil navnesortering. Sidevelgeren skjules
  når det er høyst 100 personer. En feil på en ubesøkt side blokkerer ikke
  første side.
- Steg 2 og videre bruker `useJobbsøkereForGjennomføring`, med statusfilter
  `MØTT_OPP` og `FÅTT_JOBB`. Begge regnes som fremmøtte i backend.
  Hent alle filtrerte sider sekvensielt og samle dem i én SWR-cache, uten
  duplikater på `personTreffId`. Ikke hent dette datasettet i steg 1.
- Valider skjema, sidenummer og sidelengde. Den samlede fremmøttelisten krever
  også uendret totaltall, riktige statuser og riktig antall unike personer.
  Hentefeil eller avvik gir feilvisning og mulighet for ny henting, aldri en
  tilsynelatende komplett delliste.
- Streng sidevalidering er felles for de to gjennomføringshookene, men holdes
  utenfor `useSWRPost` og øvrige delte SWR-hooks. Eksisterende søkehook og
  responsskjema for andre konsumenter beholder sin oppførsel.
- Påmeldttallet i oppsummeringen gjelder hele treffet, ikke bare fremmøtte.
  Bruk summen av `antallPerStatus` fra første respons; backend beregner denne
  uten statusfilter. Tellingene skal **ikke summeres per side**.
- Etter oppretting, sletting og oppmøteendringer ugyldiggjøres fremmøttecachen.
  Hvis den ikke er i bruk, hentes den først ved neste stegovergang.
  Oppmøtesiden revalideres uten å fjerne radene, og tidligere besøkte
  oppmøtesider oppfriskes når de åpnes igjen. Øvrige jobbsøkercacher tømmes
  og revalideres hvis de er aktive. Bare cacher for aktuelt treff berøres.
- Treffgjennomføringsaggregatet oppdateres også ved sletting. Behold eksisterende
  tilgangs- og synlighetsregler.
- Oppmøtekøen eies av stegkomponenten, utenfor sidens laste-/feilvisning.
  Sidebytte skal verken tømme køen, fjerne radfeil eller miste optimistiske valg.
- Senere steg har alltid komplett fremmøttegrunnlag, også ved direkte åpning.
  Det må aldri avhenge av hvilke oppmøtesider brukeren har besøkt.

## 2. Samle oppmøteregistrering på gjennomføringssiden

Endre `FremmøtteJobbsøkere` til `Oppmøteliste`. Vis alle tilgjengelige jobbsøkere,
ikke bare de fremmøtte. Skjulte og slettede personer skal fortsatt håndteres av
dagens søke-/synlighetsregler.

- Erstatt fjern-krysset med en checkbox per person og kolonnetittelen
  **Oppmøte**. Avkrysset betyr møtt. Bruk eksisterende `oppdaterOppmøte`-API.
- Behold stabil navnerekkefølge når oppmøtet endres, slik at en person ikke
  hopper til en annen side etter avkrysning. Deltakernummer kan fortsatt vises.
- Gi checkboxen et tilgjengelig navn knyttet til personen, og behold god
  tastaturbetjening og fokus.
- Oppmøte kan ikke fjernes når personen har interesser eller vurderinger.
  Deaktiver checkboxen, forklar sperren ved tastaturfokus/hover, og behold
  backendvalideringen. Den tidligere blokkeringsmodalen fjernes.
- Gjenbruk interessestegets sekvensielle autolagringskø med obligatorisk
  registreringsnøkkel. Raske valg, også av/på på samme person, skal beholde
  siste ønskede verdi uten overlappende forespørsler. «Neste» og stegnavigasjon
  er deaktivert mens køen arbeider, både i oppmøte- og interessesteget.
  Det finnes ingen separat «gå videre når køen er tom»-operasjon.
- Vis feil ved den berørte raden. En senere vellykket lagring på en annen rad
  må ikke fjerne feilmarkeringen eller bli brukt som tekst i feilbanneret.
- Fjern individuell oppmøteredigering og oppmøtets massehandlinger fra
  jobbsøkersiden. Ikke innfør «marker alle møtt» på den nye listen.
  Behold generell kandidatmarkering som brukes til andre handlinger.
- **Behold «Møtt opp»-statusmerket og statusfilteret på jobbsøkersiden.**
  Oppfrisk relevante søkecacher etter oppmøteendringer, slik at merker og
  tellinger stemmer ved fanebytte.
- Behold sperren mot «Endre svar» når personen har møtt. Tilpass forklaringen
  til at oppmøtet nå fjernes på gjennomføringssiden.
- Fjern `useJobbsøkerOppmøte` og `useOppmøteForValgte` dersom de ikke lenger
  har konsumenter. Gjenbruk relevant logikk uten å beholde døde wrappers.

## 3. Atomisk romflytting

Dagens klient sender hele sin romfordeling. En gammel klientkopi kan dermed
overskrive en annen arrangørs flytting, selv om backend serialiserer skriving.

### Ny kontrakt

```http
PUT /api/rekrutteringstreff/{id}/treffgjennomforing/romfordeling/{personTreffId}
Content-Type: application/json

{ "romnummer": 2 }
```

Returner `200` med oppdatert `TreffgjennomføringDto`, som ved øvrige mutasjoner.
Bruk en egen request-DTO og oppdater OpenAPI. Frontendmutasjonen kan hete
`oppdaterRomplassering`; klienten sender bare personen og målrommet.

### Backend

1. Gjenbruk `krevEierEllerUtvikler` og `TreffgjennomføringWriter.skriv`.
   Valider WorkOp, trefftilhørighet, fremmøte, opprettet romfordeling og målrom.
2. Les gjeldende **normaliserte** møteplan under den eksisterende transaksjonen
   og trefflåsen. Flytt personen i denne ferske fordelingen, ikke i klientdata.
3. Gjenbruk `MøteplanRepository.erstattRomfordeling` til å lagre resultatet,
   og returner aggregatet. Andre personers plasseringer skal bevares.
4. Gjentatt flytting til samme rom skal være idempotent. For ulike mål for samme
   person gjelder siste serveroperasjon; flyttinger av ulike personer skal ikke
   overskrive hverandre.
5. Et lagret møteoppsett beholder tomme rom når siste oppmøte fjernes.
   Nye fremmøtte kan dermed få beregnet plassering og flyttes igjen.

**Viktig:** Nye fremmøtte kan ha en beregnet romplassering uten egen lagret
romrad. En ren `UPDATE` av én eksisterende rad er derfor ikke tilstrekkelig.
En isolert upsert kan også endre andre beregnede plasseringer. Gjenbruk derfor
serverens normalisering og persistering under låsen. Atomisk betyr her at én
flyttekommando utføres mot fersk servertilstand, ikke nødvendigvis én SQL-rad.

Fjern gamle `PUT …/romfordeling`, frontendens `oppdaterRomfordeling`, tilhørende
MSW-handler og ubrukt service-/valideringskode. Behold
`POST …/romfordeling/fordel` («Fordel på nytt») og repositoryfunksjonen som
fortsatt brukes ved oppretting og omfordeling.

## 4. Usikkert lagringsutfall og riktig romrekkefølge

Et feilet HTTP-svar betyr ikke nødvendigvis at serveren avviste lagringen.
Rom- og intervjulagring skal derfor ikke påstå at en flytting ble tilbakestilt
uten å hente bekreftet servertilstand.

- `brukLagretSvar(data)` tar inn et bekreftet, validert serversvar i SWR uten
  ekstra GET. `hentBekreftetTilstand()` brukes ved usikkert lagringsutfall.
  Disse er eksplisitte operasjoner, ikke én callback med valgfritt argument.
- Etter lagringsfeil: hent aggregatet via eksisterende
  `GET …/treffgjennomforing-og-oppfolging` og vis serverens tilstand.
  Dette gjelder også «Fordel på nytt» og oppmøteflyten som endres.
- Avvent oppfriskningen før nye handlinger tillates. Feiler også hentingen,
  vis at tilstanden er ubekreftet, med mulighet for ny **henting**. Ikke skriv
  videre fra en ubekreftet gammel kopi eller send mutasjonen automatisk igjen.
- Sørg for at hente-feil faktisk rapporteres. Et fullført SWR-`mutate()` er ikke
  i seg selv bevis på vellykket henting; gammel cache kan fortsatt returneres.
- Hent eksplisitt, valider aggregatet med Zod og oppdater SWR uten ny revalidering.
  Hold kø og steglåser mens «Hent på nytt» venter. Bekreftelsesdialogen for
  omfordeling lukkes ved bekreftelse, slik at den ikke sperrer gjenhentingen.
- Gjenbruk en liten felles oppfriskingsfunksjon, men behold egne hooks for rom
  og intervju. Ikke endre FIFO-/feilsemantikken i `useSekvensiellAutolagring`.
- Romflytting vises først når serversvaret foreligger. Behold «Lagrer» og
  steglås, men ikke en lokal optimistisk romkopi eller lokal romflyttingsalgoritme.
  API-et sender fortsatt bare person og målrom. Trefflåsen, den atomiske
  flyttingen og andre eieres plasseringer påvirkes ikke av denne UI-forenklingen.
- Optimistiske feltverdier for oppmøte, interesser og vurderinger beholdes,
  inkludert vernet mot at eldre svar fjerner nyere valg. Intervjufordelingen
  beholder også sin optimistiske visning.
- Backendens romrekkefølge er fasit: deltakernummer stigende, manglende nummer
  sist. Frontend viser den returnerte romfordelingen direkte, og mocken følger
  samme hovedregel. Backendens interne ID som sekundær
  sortering trenger ikke eksponeres til frontend.
- Fjern teksten om at en flyttet person «legges sist». Ikke endre den manuelt
  styrte intervjurekkefølgen.

## Hvor du starter i koden

Forkortelser brukt i tabellen:

- **FE-API:** `rekrutteringsbistand-frontend/app/api/rekrutteringstreff/[...slug]/`
- **FE-UI:** `rekrutteringsbistand-frontend/app/rekrutteringstreff/[rekrutteringstreffId]/_ui/`
- **BE:** `rekrutteringstreff-backend/apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/`

| Område                            | Sentrale filer                                                                                                                                                                         |
| --------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Datainnhenting                    | FE-API `jobbsøkere/`: `useJobbsøkereForOppmøte.ts`, `useJobbsøkereForGjennomføring.ts`, `hentJobbsøkersideForGjennomføring.ts`, `useOppdaterJobbsøkere.ts` |
| Datagrunnlag mellom steg          | FE-UI `treffgjennomføring/`: `Treffgjennomføring.tsx`, `navigasjon/Steginnhold.tsx`, `navigasjon/StegMedFremmøtte.tsx` |
| Oppmøte                           | FE-UI `treffgjennomføring/oppmøte/`                                                                                                                                                    |
| Gamle handlinger og statusvisning | FE-UI `jobbsøker/`: `JobbsøkerKort.tsx`, `JobbsokerKortValg.tsx`, `JobbsøkerHandlingsrad.tsx`, `JobbsøkerStatusTag.tsx`, `filter/StatusFilter.tsx`                                     |
| Frontendkontrakt og mock          | FE-API `treffgjennomføring/`: `mutations.ts`, `treffgjennomføringEndepunkter.ts`, `treffgjennomføringSchema.ts`, `useTreffgjennomføring.msw.ts`                                        |
| Lagring og sortering              | FE-UI `treffgjennomføring/`: `felles/useTreffgjennomføringOppdatering.ts`, `felles/useSekvensiellAutolagring.ts`, `romOgRotasjon/useRomfordelingLagring.ts`, `intervjufordeling/useIntervjufordelingLagring.ts`, `felles/deltakernavn.ts` |
| Romoperasjonen                    | BE `treffgjennomføring/`: `TreffgjennomføringController.kt`, `TreffgjennomføringWriter.kt`, `dto/TreffgjennomføringDto.kt` og `møteplan/`                                              |
| Oppmøtereglene                    | BE `jobbsoker/oppmøte/OppmøteService.kt`                                                                                                                                               |

Se også [arkitekturprinsippene](../../2-arkitektur/prinsipper.md).
Behold Javalin, ren SQL/JDBC og lagdelingen Controller → Service → Repository.

## Regresjonsdekning

Bruk eksisterende Playwright-oppsett og backendkomponenttester med ekte database.
Tilpass eksisterende tester fremfor å duplisere dem. Test brukerflyt og faktisk
persistering, ikke bare mockens algoritmer. Nye persondata skal være tydelig
syntetiske.

| Område              | Nødvendig dekning                                                                                                                                                                          |
| ------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| Paginering          | 25 og 100 uten sidevelger; 101 og over 200 med riktige sider. Steg 1 henter bare valgt side. Senere steg henter alle fremmøtte, også over 100 og med status FÅTT_JOBB. Påmeldttallet gjelder hele treffet. |
| Delt datagrunnlag    | Feil på ubesøkt oppmøteside blokkerer ikke første side. Ufullstendig fremmøtteliste vises ikke. Sidebytte bevarer kø/radfeil, og stegovergang henter oppdaterte fremmøtte uten å gjenbruke gammel cache. |
| Oppmøte             | Av/på på ulike sider, blokkert fjerning, lagringslås, tastatur/fokus og riktige statusmerker/tellinger på jobbsøkersiden etter fanebytte.                                                  |
| Rom-API             | To uavhengige flyttinger fra klienter med ulikt gamle kopier bevares. Dekk samme mål to ganger, ugyldig rom/person, manglende oppmøte og tilgangsavslag.                                   |
| Beregnet plassering | Flytt en nylig fremmøtt uten lagret romrad. Andre lagrede og beregnede plasseringer bevares.                                                                                               |
| Lagringsfeil        | La serveren lagre, men mist svaret. Frontend henter og viser faktisk tilstand. Dekk også reell skrivefeil og feil under oppfriskning, for rom og intervju.                                 |
| Rekkefølge          | Rommet endres først ved bekreftet serversvar. Flytting og reload følger serverens rekkefølge. En klient med gammel visning får også med andre klienters romflyttinger fra serversvaret. |

Frontendtestene ligger i
`tests/rekrutteringstreff/treffgjennomføring/{enhet,e2e}`. Backend har
`TreffgjennomføringKomponentTest`, `TreffgjennomføringAutorisasjonsTest`,
`TreffgjennomføringPersisteringTest` og øvrige tester i samme pakke.
Oppdater også `AutentiseringAlleEndepunkterTest` når endepunktet byttes.

Kjør fra backend-repoet (krever Docker for Testcontainers):

```bash
./gradlew :apps:rekrutteringstreff-api:test \
  --tests '*Treffgjennomføring*' --tests '*AutentiseringAlleEndepunkterTest'
```

Kjør fra frontend-repoet, med testserver på en ledig port (her 1341):

```bash
./node_modules/.bin/tsc --noEmit --incremental false
CI=true PLAYWRIGHT_PORT=1341 ./node_modules/.bin/playwright test \
  tests/rekrutteringstreff/treffgjennomføring --workers=2 --retries=0
```

Gjenbruk helst en eksisterende testserver. Ved egen server må
`NEXT_PUBLIC_PLAYWRIGHT_TEST_MODE=true` settes **ved build**, ikke bare oppstart.
I sandkasse kan `next dev` feile på `.env.development`; bruk da `next build` og
`next start`. `CI=true` hindrer Playwright i å starte sin egen webServer, som kan
drepe en annen prosess på testporten. Stopp bare servere du selv har startet.

Bevar særlig eksisterende lagringslåser og dato-/rollback-håndtering.
I Aksel kan `disabled={false}` overstyre `loading={true}`; eksplisitte
`disabled`-uttrykk må derfor inkludere egen lagringsstatus.

Oppdater OpenAPI og gjeldende beskrivelser av oppmøte, rom-API og feilhåndtering i
[den opprinnelige gjennomføringsplanen](treffgjennomforing-oppmote-rom-og-fordeling.md).
Denne planen gjelder foran eldre, motstridende beskrivelser av disse punktene.
