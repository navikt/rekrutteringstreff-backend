# Treffgjennomføring: paginert oppmøte og trygg romflytting

Avklart 7. september 2026. Kodeendringene gjenstår. Planen er en selvstendig
overlevering til utvikler og Copilot, og krever ikke tidligere samtalehistorikk.

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

## 1. Komplett datagrunnlag, paginert visning

Gjennomføringen bruker i dag `useJobbsøkere`, som bare henter side 1 med
100 personer. Det gir manglende personer og feil oppsummering på større treff.
Backend tillater allerede 1–100 personer per forespørsel.

- Lag en avgrenset `useAlleJobbsøkere` for gjennomføringen. Gjenbruk eksisterende
  søke-API, skjema, fetcher og tilgangssjekker. Ikke endre oppførselen til andre
  konsumenter av `useJobbsøkere`.
- Hent alle sider sekvensielt med `antallPerSide: 100` og stabil navnesortering.
  Samle resultatet i én SWR-cache, uten duplikater på `personTreffId`.
  Globale tellinger fra responsene skal **ikke summeres per side**.
- Ikke presenter et delvis resultat som komplett ved hentefeil eller avvik i
  sider/tellinger. Vis feil og mulighet for ny henting.
- Oppmøtestegget viser 100 rader per side. Gjenbruk `LitenPaginering`, men skjul
  sidevelgeren når det er høyst 100 personer.
- Øvrige steg får hele det relevante datagrunnlaget, også ved direkte navigasjon
  til et senere steg. Det må aldri avhenge av hvilke oppmøtesider brukeren har
  besøkt. Behold dagens filtrering til fremmøtte der den gjelder.

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
- Behold `OppmøteBlokkert`: oppmøte kan ikke fjernes når personen har interesser
  eller vurderinger. Både frontendforklaringen og backendvalideringen må bevares.
- Behold lagringslåsene. Ikke tillat overlappende lokale oppmøtemutasjoner eller
  overgang til neste steg mens oppmøtet lagres.
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
Rom- og intervjulagring skal derfor ikke bare slippe optimistisk state og
påstå at flyttingen ble tilbakestilt.

- Etter lagringsfeil: hent aggregatet via eksisterende
  `GET …/treffgjennomforing-og-oppfolging` og vis serverens tilstand.
  Dette gjelder også «Fordel på nytt» og oppmøteflyten som endres.
- Avvent oppfriskningen før nye handlinger tillates. Feiler også hentingen,
  vis at tilstanden er ubekreftet, med mulighet for ny **henting**. Ikke skriv
  videre fra en ubekreftet gammel kopi eller send mutasjonen automatisk igjen.
- Sørg for at hente-feil faktisk rapporteres. Et fullført SWR-`mutate()` er ikke
  i seg selv bevis på vellykket henting; gammel cache kan fortsatt returneres.
- Gjenbruk en liten felles oppfriskingsfunksjon, men behold egne hooks for rom
  og intervju. Ikke endre FIFO-/feilsemantikken i `useSekvensiellAutolagring`.
- Backendens romrekkefølge er fasit: deltakernummer stigende, manglende nummer
  sist. Bruk samme hovedregel i optimistisk frontend og mock, og gjenbruk
  eksisterende nummeroppslag/sortering. Backendens interne ID som sekundær
  sortering trenger ikke eksponeres til frontend.
- Fjern teksten om at en flyttet person «legges sist». Ikke endre den manuelt
  styrte intervjurekkefølgen.

## Hvor du starter i koden

Forkortelser brukt i tabellen:

- **FE-API:** `rekrutteringsbistand-frontend/app/api/rekrutteringstreff/[...slug]/`
- **FE-UI:** `rekrutteringsbistand-frontend/app/rekrutteringstreff/[rekrutteringstreffId]/_ui/`
- **BE:** `rekrutteringstreff-backend/apps/rekrutteringstreff-api/src/main/kotlin/no/nav/toi/`

| Område | Sentrale filer |
| --- | --- |
| Datainnhenting | FE-API `jobbsøkere/`: `useJobbsøkere.ts`, `useJobbsøkerSøk.ts` |
| Datagrunnlag mellom steg | FE-UI `treffgjennomføring/`: `Treffgjennomføring.tsx`, `navigasjon/Steginnhold.tsx` |
| Oppmøte | FE-UI `treffgjennomføring/oppmøte/` |
| Gamle handlinger og statusvisning | FE-UI `jobbsøker/`: `JobbsøkerKort.tsx`, `JobbsokerKortValg.tsx`, `JobbsøkerHandlingsrad.tsx`, `JobbsøkerStatusTag.tsx`, `filter/StatusFilter.tsx` |
| Frontendkontrakt og mock | FE-API `treffgjennomføring/`: `mutations.ts`, `treffgjennomføringEndepunkter.ts`, `treffgjennomføringSchema.ts`, `useTreffgjennomføring.msw.ts` |
| Lagring og sortering | FE-UI `treffgjennomføring/`: `romOgRotasjon/useRomfordelingLagring.ts`, `romOgRotasjon/romplassering.ts`, `intervjufordeling/useIntervjufordelingLagring.ts`, `felles/deltakernavn.ts` |
| Romoperasjonen | BE `treffgjennomføring/`: `TreffgjennomføringController.kt`, `TreffgjennomføringWriter.kt`, `dto/TreffgjennomføringDto.kt` og `møteplan/` |
| Oppmøtereglene | BE `jobbsoker/oppmøte/OppmøteService.kt` |

Se også [arkitekturprinsippene](../../2-arkitektur/prinsipper.md).
Behold Javalin, ren SQL/JDBC og lagdelingen Controller → Service → Repository.

## Regresjonsdekning

Bruk eksisterende Playwright-oppsett og backendkomponenttester med ekte database.
Tilpass eksisterende tester fremfor å duplisere dem. Test brukerflyt og faktisk
persistering, ikke bare mockens algoritmer. Nye persondata skal være tydelig
syntetiske.

| Område | Nødvendig dekning |
| --- | --- |
| Paginering | 25 og 100 uten sidevelger; 101 og over 200 med riktige sider. Personer fra ubesøkte sider inngår i senere steg og oppsummering. Hentefeil på en senere side gir ikke et komplett resultat. |
| Oppmøte | Av/på på ulike sider, blokkert fjerning, lagringslås, tastatur/fokus og riktige statusmerker/tellinger på jobbsøkersiden etter fanebytte. |
| Rom-API | To uavhengige flyttinger fra klienter med ulikt gamle kopier bevares. Dekk samme mål to ganger, ugyldig rom/person, manglende oppmøte og tilgangsavslag. |
| Beregnet plassering | Flytt en nylig fremmøtt uten lagret romrad. Andre lagrede og beregnede plasseringer bevares. |
| Lagringsfeil | La serveren lagre, men mist svaret. Frontend henter og viser faktisk tilstand. Dekk også reell skrivefeil og feil under oppfriskning, for rom og intervju. |
| Rekkefølge | Optimistisk romflytting, lagret resultat og reload følger deltakernummer. |

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
