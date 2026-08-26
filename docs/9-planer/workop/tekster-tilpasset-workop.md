# Plan: Tekster tilpasset WorkOp

**Status:** Retning er valgt. Tekstene må språkvaskes og godkjennes, og noen
avklaringer gjenstår før bygging.

## Mål

Gi jobbsøkere WorkOp-tilpasset informasjon før invitasjon, før formøtet og før
selve WorkOp-dagen, med minst mulig ny teknikk.

Planen berører:

- `rekrutteringsbistand-frontend`
- `rekrutteringstreff-api`
- `rekrutteringstreff-minside-api`
- `rekrutteringstreff-bruker`
- `rekrutteringsbistand-kandidatvarsel-api` (trinn 3)

## Kort oppsummert

Oppgaven består av fem tekster. De faller i to grupper med helt ulik
løsningsvei:

| Gruppe                             | Tekster                                                    | Løsning                                                     |
| ---------------------------------- | ---------------------------------------------------------- | ----------------------------------------------------------- |
| **Informasjon til alle inviterte** | Invitasjonstekst, program- og praktisk info (T-2)          | **Innlegg på treffet**, satt inn fra ferdig mal             |
| **Individuell purring på SMS**     | Kontaktforsøk, påminnelse formøte, påminnelse WorkOp-dagen | **To nye varselmaler** som kan sendes fra jobbsøkerlisten   |

Hovedgrepet er at all informasjon som gjelder alle inviterte skal ligge som
innlegg på treffet, satt inn fra en WorkOp-mal. Jobbsøkeren får ett varsel og
må logge inn på Nav for å lese innholdet. Det er både enklest teknisk og best
personvernmessig, fordi selve innholdet aldri sendes ut på SMS eller e-post.

De tre SMS-tekstene løses ikke med innlegg, fordi de er individuelle purringer
og ikke felles informasjon. De blir i stedet korte, innholdsløse varselmaler
etter samme mønster som dagens treffmaler.

## Beslutning

### 1. Informasjonstekstene blir innlegg med mal

- Ved opprettelse av et WorkOp-treff foreslås **invitasjonsteksten**
  automatisk som innlegg. Markedskontakten fyller ut dato, tidspunkt, sted og
  kontaktpunkt.
- Teksten når jobbsøkeren gjennom dagens invitasjonsflyt. Ingen egen
  utsending.
- **Program og praktisk informasjon** legges inn som en egen mal når
  detaljene er klare, normalt et par dager før WorkOp.
- Målbildet er at dette blir et **eget, nytt innlegg**, slik at jobbsøkeren
  ser en datert rekke av meldinger. Datamodellen støtter allerede flere
  innlegg per treff; det er resten av løsningen som i praksis antar ett.

### 2. SMS-tekstene blir to nye varselmaler

De tre SMS-tekstene slås sammen til **to** maler. De sendes manuelt fra
jobbsøkerlisten, ikke automatisk.

- **Kontaktforsøk-SMS-en er individuell.** Den går til dem som ikke har
  svart, og passer derfor ikke som innlegg, som er felles for alle inviterte.
- **Påminnelse før formøtet og påminnelse før WorkOp-dagen slås sammen til én
  mal.** Dagens SMS-tekster er bevisst korte og innholdsløse; det finnes en
  egen commit på at de skal være under 160 tegn. Når detaljene uansett ligger
  bak innlogging, er det ingen reell forskjell på de to tekstene.
- Automatisk utsending T-1 krever en scheduler som ikke finnes, og er ikke
  del av planen. Malene sendes manuelt.

Se [Trinn 3](#trinn-3--påminnelse-fra-jobbsøkerlisten) for teknisk omfang.

## Foreslått plan i tre trinn

### Trinn 1 — Maler i dagens ett-innleggs-løsning

Dette kan gjøres uten backend-endringer.

1. Legg WorkOp-standardtekstene i en egen malmodul i frontend.
2. Vis to handlinger i `InnleggForm.tsx`, bare når `kategori = WORKOP`:

   | Handling                       | Bruk                                                                    |
   | ------------------------------ | ----------------------------------------------------------------------- |
   | **Sett inn WorkOp-invitasjon** | Setter inn introduksjon, informasjon om WorkOp og formøte               |
   | **Sett inn møtedetaljer**      | Setter inn program og praktisk informasjon øverst i eksisterende innlegg |

3. Invitasjonsmalen foreslås automatisk i tomt innlegg ved opprettelse av
   WorkOp-treff.
4. Møtedetaljene legges **øverst** i innlegget, slik at det nyeste innholdet
   er lettest å finne. Eksisterende tekst overskrives aldri uten bekreftelse.
5. Blokker publisering og republisering så lenge innlegget inneholder en
   plassholder på formen `#...#`.
6. Behold dagens KI-validering av `htmlContent`.

Etter publisering registreres redigering som `Endringsfelttype.INTRODUKSJON`,
og dagens endringsflyt varsler jobbsøkere med aktivt svar ja.

### Trinn 2 — Flere innlegg per treff

Målbildet. Gir jobbsøkeren en datert meldingsrekke i stedet for ett innlegg
som endrer seg.

Bruker-appen viser allerede hele listen under fanen «Siste aktivitet (n)», så
visningen krever lite. Det som må ryddes:

| Sted                                                                         | Dagens antakelse                            | Må endres til                              |
| ---------------------------------------------------------------------------- | ------------------------------------------- | ------------------------------------------ |
| `InnleggService.opprettInnlegg`                                              | Overskriver første innlegg i `UTKAST`       | Overskriv bare når det er samme innlegg    |
| `InnleggForm.tsx`, `useLagreInnlegg.ts`, `useRepubliser.ts`                  | `innleggListe?.[0]`                         | Håndter liste                              |
| `OmTreffetForEier.tsx`, `OmTreffetForIkkeEier.tsx`                           | `innleggListe?.[0]`                         | Vis alle innlegg                           |
| Tittel                                                                       | Hardkodet «Om treffet»                      | Tittel per mal, f.eks. «Program for dagen» |
| `InnleggOutboundDto` i `rekrutteringstreff-minside-api`                      | Bare `tittel` og `htmlContent`              | Ta med dato                                |
| `InnleggRepository`                                                          | `ORDER BY opprettet` (eldst først)          | Bevisst sortering, avklar retning          |
| KI-logg                                                                      | Knyttet til treff + felttype                | Knyttes til `innlegg_id`                   |
| Varsling                                                                     | Bare republisering med `INTRODUKSJON`       | Nytt innlegg bør kunne utløse varsel       |

Merk at `sendesTilJobbsokerTidspunkt` finnes på innlegget, men **ikke** brukes
til å planlegge eller filtrere visning. Feltet skal ikke behandles som en
scheduler før det faktisk er implementert som det.

Varslingsspørsmålet er det viktigste: hvis et nytt innlegg ikke utløser noe
varsel, får ikke jobbsøkeren vite at det er kommet ny informasjon.

### Trinn 3 — Påminnelse fra jobbsøkerlisten

Kan bygges parallelt med trinn 2. Omfanget er mindre enn først antatt, fordi
maskineriet i `kandidatvarsel-api` allerede er malagnostisk.

#### Eksisterende malkatalog

| Mal                              | Type              | Utsending i dag         |
| -------------------------------- | ----------------- | ----------------------- |
| `VURDERT_SOM_AKTUELL`            | Stilling          | Ad-hoc fra kandidatlista |
| `PASSENDE_STILLING`              | Stilling          | Ad-hoc fra kandidatlista |
| `PASSENDE_JOBBARRANGEMENT`       | Stilling          | Ad-hoc fra kandidatlista |
| `KANDIDAT_INVITERT_TREFF`        | Rekrutteringstreff | Kafka-lytter            |
| `KANDIDAT_INVITERT_TREFF_ENDRET` | Rekrutteringstreff | Kafka-lytter, parametrisert med `{{ENDRINGER}}` |
| `KANDIDAT_INVITERT_TREFF_AVLYST` | Rekrutteringstreff | Kafka-lytter            |

Det finnes altså **ingen** påminnelsesmal eller «vi har prøvd å kontakte
deg»-mal i dag, verken for stilling eller treff, og ingen er fjernet
tidligere. Nærmeste slektning er `PASSENDE_JOBBARRANGEMENT`, som brukes for
stillingskategori Jobbmesse, men den er en «dette kan passe for deg»-melding,
ikke en purring.

#### Forslag: to nye maler

```text
KANDIDAT_TREFF_PAAMINNELSE   (dekker tekst 3 og 5)
  Hei! Vi minner om et treff du er invitert til.
  Logg inn på Nav for å se tid og sted. Vennlig hilsen Nav        103 tegn

KANDIDAT_TREFF_TA_KONTAKT    (dekker tekst 2)
  Hei! Vi har prøvd å kontakte deg om et treff du er invitert til.
  Ta kontakt med veilederen din. Vennlig hilsen Nav               114 tegn
```

Begge er under 160 tegn og følger mønsteret til dagens treffmaler. Setningen
«Det gjelder en mulighet for ordinært arbeid» er bevisst utelatt, fordi den
røper oppfølgingsstatus i klartekst på SMS.

Malene trenger også `epostTittel()` og `epostHtmlBody()`, og skal være
`RekrutteringstreffMal`.

#### Hva som allerede virker

- `MinsideVarsel.create(mal, avsenderReferanseId, …)` er malagnostisk.
- POST `/api/varsler/stilling/{stillingId}` bruker bare path-parameteren som
  `avsenderReferanseId`. Feltkommentaren i `VarselResponseDto` sier eksplisitt
  at feltet også kan inneholde id til et rekrutteringstreff.
- `Mal.lenkeurl()` brancher på `varselType`, så en `RekrutteringstreffMal`
  lenker automatisk til `rekrutteringstreff-bruker`.
- `brukerRapid()` styrer bare at *resultatet* publiseres tilbake på rapid.
  `MinsideVarselSvarLytter` i `rekrutteringstreff-api` leser `mal` som ren
  data uten noen switch, så nye maler blir automatisk registrert som hendelse
  på jobbsøkeren. Sporing og visning av leveringsstatus kommer gratis.

#### Hva som må bygges

1. De to nye malene i `Mal.kt`, lagt til i `Maler.valueOf` og
   `Maler.malerForVarselType(REKRUTTERINGSTREFF)`.
2. Nytt endepunkt `POST /api/varsler/rekrutteringstreff/{treffId}`, i praksis
   en kopi av stilling-varianten. Autorisasjon må vurderes: stilling-varianten
   krever `REKBIS_UTVIKLER` eller `REKBIS_ARBEIDSGIVERRETTET`, mens det er
   jobbsøkerrettet veileder som typisk purrer.
3. Utsendingsknapp i jobbsøkerlisten med valg av mal og mottakerutvalg, etter
   mønster fra `SendSmsModal.tsx`.
4. Ny case i `getMalTekst` i `minsideStatusUtil.ts`. Funksjonen har
   `default: return null`, så en ukjent mal degraderer pent, men vises uten
   etikett.

Ingen ny Kafka-hendelse, ingen ny lytter og ingen scheduler er nødvendig.

#### Rekkefølge på deploy

Nye malnavn må innføres i to steg, fordi `Maler.valueOf(mal)` kaster for
ukjente navn:

1. Deploy støtte for malnavnene i `kandidatvarsel-api` og frontend.
2. Deploy den som begynner å bruke malnavnene.

Det bør ikke legges inn en generell fallback. Den ville skjult kontraktsfeil
og kunne sendt feil tekst.

## Kartlegging av tekstene fra Trello

| #   | Tekst                                      | Kanal                                | Trinn |
| --- | ------------------------------------------ | ------------------------------------ | ----- |
| 1   | Første melding til unge i aktivitetsplanen | Innlegg, mal «Invitasjon»            | 1     |
| 2   | SMS-påminnelse om kontakt (ikke respons)   | Mal `KANDIDAT_TREFF_TA_KONTAKT`      | 3     |
| 3   | SMS-påminnelse dagen før formøte           | Mal `KANDIDAT_TREFF_PAAMINNELSE`     | 3     |
| 4   | Melding to dager før WorkOp                | Innlegg, mal «Møtedetaljer»          | 1 → 2 |
| 5   | SMS-påminnelse dagen før WorkOp            | Mal `KANDIDAT_TREFF_PAAMINNELSE`     | 3     |

Tekst 3 og 5 er slått sammen til én mal. Fram til trinn 3 er levert sendes
disse tre meldingene fra dagens verktøy.

Tekstene kan ikke brukes ordrett:

- «Svar her i dialogen» må erstattes med at jobbsøkeren svarer via lenken til
  treffet.
- Dato, tidspunkt, sted og kontaktperson må være tydelige plassholdere.
- Konkrete arbeidsgivere skal ikke nevnes, fordi arbeidsgiverne skjules for
  inviterte på WorkOp.
- Bransjelisten må kunne redigeres dersom den varierer mellom treff.
- Personnavn og direktenummer bør unngås dersom et felles kontaktpunkt eller
  «kontakt veilederen din» dekker behovet.
- «Nav X» må bli et reelt kontornavn, og «Nav» skrives slik, ikke «NAV».

### Plassholdere

- `#WORKOP_DATO#`
- `#WORKOP_TIDSPUNKT#`
- `#WORKOP_STED#`
- `#FORMØTE_DATO#`
- `#FORMØTE_TIDSPUNKT#`
- `#FORMØTE_STED#`
- `#KONTAKTPUNKT#`

Standardtekstene bør forhåndsvaskes og godkjennes før produksjonssetting.
KI-valideringen skal kjøres på den ferdig utfylte teksten, ikke bare på malen.

## Begreper

- **T:** Kalenderdagen WorkOp starter, basert på `fraTid` i `Europe/Oslo`.
- **T-2:** Kalenderdagen to dager før WorkOp, ikke nødvendigvis 48 timer før.
- **T-1:** Kalenderdagen før WorkOp, ikke nødvendigvis 24 timer før.

T-2 og T-1 brukes bare når senere automatisering omtales. Trinn 1 og 2 har
ingen scheduler og forutsetter en manuell rutine.

## Hvorfor innlegg?

- Innlegget støtter rik tekst og vises på siden i `rekrutteringstreff-bruker`.
- Aktivitetskortet lenker jobbsøkeren til denne siden.
- Innlegget kan redigeres etter publisering.
- `useRepubliser` oppdager endret `htmlContent` og registrerer
  `Endringsfelttype.INTRODUKSJON`.
- Dagens endringsflyt sender kortoppdatering til alle inviterte og MinSide-
  varsel til jobbsøkere med aktivt svar ja.
- KI-validering og KI-logg fungerer allerede.
- **Personvern:** innholdet ligger bak innlogging. Varselet sier bare at det
  finnes ny informasjon. Detaljer om at personen er arbeidssøker, hvilke
  bransjer det gjelder og hvilket arrangement hen er invitert til, sendes
  aldri ut på SMS eller e-post.

### Kjente begrensninger

- Tidspunktet for oppdatering er manuelt.
- I trinn 1 ser jobbsøkeren ett samlet innlegg, ikke en meldingshistorikk.
- Endringsvarselet er generisk; det sier at introduksjonen er endret, ikke at
  nye WorkOp-detaljer er publisert.
- Jobbsøkere uten aktivt svar ja får kortoppdatering, men ikke MinSide-varsel.
- Den som redigerer må aktivt velge varsling i republiseringsflyten.

Begrensningene er akseptable, men må være kjent av dem som gjennomfører
WorkOp.

## Avgrensning

Ikke del av noen av trinnene:

- automatisk utsending T-2 eller T-1; alle utsendinger er manuelle
- strukturert lagring av formøte
- egne WorkOp-varianter av endrings- og avlysningstekster
- egen WorkOp-variant av invitasjons-SMS-en

De to nye malene er generelle for rekrutteringstreff, ikke WorkOp-spesifikke.
Det er et bevisst valg: teksten er så kort og innholdsløs at en egen
WorkOp-variant ikke ville gitt jobbsøkeren noe ekstra, og det unngår at
`treffkategori` må sendes i flere hendelser.

## Senere behov

### Strukturert formøte

En senere versjon kan bruke en valgfri én-til-én-relasjon:

```text
rekrutteringstreff 1 ── 0..1 rekrutteringstreff_formote
```

Et formøte trenger minst starttid og sted. Modellen bør være generell, slik at
den også kan brukes av andre rekrutteringstreff. Endring etter invitasjon må
føres gjennom dagens republiserings- og endringsflyt; ellers kan
aktivitetskortet vise utdatert informasjon.

Strukturert formøte er først nødvendig når dataene skal brukes til automatisk
påminnelse, egen visning, rapportering eller validering.

### Automatiske påminnelser

Når malene fra trinn 3 er i bruk, er det bare selve utløsningen som gjenstår
for å automatisere. Det krever:

- en idempotent scheduler i `rekrutteringstreff-api`
- mottakerutvalg, normalt jobbsøkere med aktivt svar ja
- håndtering av flyttet eller avlyst treff og endret svar
- observerbarhet for planlagte, sendte og feilede varsler

Malene og utsendingsveien finnes da allerede, så dette er et avgrenset tillegg.

Det skal fortsatt være **ett aktivitetskort per jobbsøker og treff**. Tabellen
i aktivitetskort-appen har en unik kobling på treff og jobbsøker, og svar- og
statusflyten forutsetter ett kort. Et eget kort for en påminnelse er derfor
ikke aktuelt.

### Egne WorkOp-varseltekster

Hvis SMS, endringsvarsel eller avlysningsvarsel senere skal få egne
WorkOp-varianter, må `treffkategori` sendes i de relevante hendelsene.
Konsumentene må støtte det valgfrie feltet før produsenten begynner å sende
det.

### Dialog i aktivitetsplanen

Dialog kan være riktig hvis oppfølgingen skal være en samtale, ikke bare
informasjon. Dette er ikke utredet. Før sporet vurderes må det avklares om
aktivitetskortet har tilgjengelig dialog, hvem som følger opp svar, om
utsending kan gjøres samlet, og hvilket team som eier integrasjonen.

## Teststrategi

### Frontend

- Innsettingshandlingene vises bare for WorkOp.
- Invitasjonsmalen foreslås i tomt innlegg.
- Møtedetaljene legges øverst uten å fjerne eksisterende tekst.
- Brukeren må bekrefte før innhold overskrives.
- Publisering blokkeres ved gjenstående plassholdere.
- Vanlige rekrutteringstreff er uendret.
- KI-logg-id fra ferdig validert tekst markeres som lagret.

### Backend, trinn 2

- Flere innlegg kan opprettes på samme treff.
- Innlegg i `UTKAST` overskriver ikke et annet innlegg.
- Innlegg returneres i avtalt rekkefølge.
- KI-logg knyttes til riktig innlegg.

### Backend, trinn 3

- `Maler.valueOf` kjenner igjen begge nye malnavn.
- `malerForVarselType(REKRUTTERINGSTREFF)` returnerer de nye malene.
- Alle SMS-tekster er under 160 tegn.
- Endepunktet oppretter ett varsel per fnr med treff-id som
  `avsenderReferanseId`.
- Lenken i varselet peker til `rekrutteringstreff-bruker`, ikke til stilling.
- Resultatet publiseres på rapid og registreres som hendelse på jobbsøkeren.
- Autorisasjon avviser roller som ikke skal kunne sende.

### Integrert flyt

```text
useRepubliser
  → registrerEndring(INTRODUKSJON)
  → rekrutteringstreffoppdatering
  → KandidatInvitertTreffEndretLytter
  → MinSide-varsel til jobbsøkere med aktivt svar ja
```

- Publiser og inviter til et WorkOp-treff med utfylt innlegg.
- Rediger innlegget etter publisering og velg varsling.
- Kontroller at jobbsøkere med aktivt svar ja får endringsvarsel.
- Kontroller at innlegget vises med nytt innhold i `rekrutteringstreff-bruker`.

## Personvern og sikkerhet

- Ikke legg fødselsnummer eller andre unødvendige personopplysninger i
  tekstene eller i logger.
- Ikke nevn konkrete arbeidsgivere i tekst til inviterte.
- SMS- og e-postvarsler skal ikke røpe innholdet. Setningen «det gjelder en
  mulighet for ordinært arbeid» er derfor tatt ut av `KANDIDAT_TREFF_TA_KONTAKT`,
  fordi den røper oppfølgingsstatus i klartekst.
- Malene er generelle for rekrutteringstreff og nevner ikke WorkOp. Da røper
  de heller ikke hvilken målgruppe eller hvilket tiltak det gjelder.
- Utsending fra jobbsøkerlisten er en manuell handling mot navngitte personer
  og bør auditlogges på linje med øvrige oppslag.
- Avklar behovet før navn og direktenummer til en ansatt brukes.
- Tilgang og mottakerregler endres ikke i trinn 1 og 2.
- Ikke logg innholdet i innlegget; logg treff-id og teknisk status.

## Observerbarhet og oppfølging

Trinn 1 trenger ingen nye alarmer, men det første WorkOp-treffet etter
utrulling bør følges manuelt:

- Ble riktig standardtekst satt inn?
- Var alle plassholdere fylt ut?
- Ble den ferdige teksten KI-validert?
- Ble endringen registrert som `INTRODUKSJON`?
- Fikk jobbsøkere med aktivt svar ja varsel?
- Viste jobbsøkersiden den oppdaterte teksten?

Erfaringene avgjør rekkefølgen på trinn 2 og 3.

## Åpne spørsmål

Må avklares før trinn 1 ferdigstilles:

1. Hvem har ansvar for å oppdatere innlegget før WorkOp-dagen?
2. Når skal oppdateringen gjøres, og hvordan blir den ansvarlige minnet på det
   når løsningen ikke har scheduler?
3. Er bransjene faste, eller skal de fylles ut per treff?
4. Skal navn og direktenummer brukes, eller erstattes med et generelt
   kontaktpunkt?
5. Er standardtekstene godkjent og språkvasket?

Må avklares før trinn 2:

6. Skal innlegg vises nyest først eller eldst først for jobbsøkeren?
7. Skal et nytt innlegg utløse varsel, og til hvem?
8. Skal innlegg kunne slettes eller bare redigeres etter publisering?

Må avklares før trinn 3:

9. **`KANDIDAT_TREFF_TA_KONTAKT` har et innebygd problem.** Poenget med
   meldingen er at jobbsøkeren skal ta kontakt tilbake, men «logg inn på Nav»
   oppnår ikke det. Enten viser vi til veileder og dialogen i
   aktivitetsplanen, eller så må et fast kontaktnummer inn i teksten.
   Sistnevnte bryter med mønsteret i de øvrige malene og må velges bevisst.
10. Hvilken rolle skal kunne sende? Stilling-endepunktet krever
    `REKBIS_UTVIKLER` eller `REKBIS_ARBEIDSGIVERRETTET`, men det er typisk den
    jobbsøkerrettede veilederen som purrer.
11. Skal det være sperre mot å sende samme påminnelse flere ganger til samme
    person?

Kan vente:

12. Må utsendingen automatiseres på T-2/T-1?
13. Trenger formøtet strukturert lagring?
14. Er dialog i aktivitetsplanen et bedre sted for individuell oppfølging?

## Leveranseplan

Trinn 3 er uavhengig av trinn 2 og kan tas parallelt, eller først dersom
purrebehovet er mest akutt.

| Steg | Leveranse                                                      | Avhengighet         |
| ---- | -------------------------------------------------------------- | ------------------- |
| 1    | Godkjenn og språkvask de to innleggstekstene                   | Åpent spørsmål 3–5  |
| 2    | Lag maler og plassholdervalidering i frontend (trinn 1)        | Steg 1              |
| 3    | Test publisering, republisering, KI-logg og endringsvarsel     | Steg 2              |
| 4    | Prøv løsningen på ett WorkOp og samle erfaring                 | Steg 3              |
| 5    | Åpne for flere innlegg per treff (trinn 2)                     | Åpent spørsmål 6–8  |
| 6    | Godkjenn de to SMS-tekstene                                    | Åpent spørsmål 9    |
| 7    | Legg til malene i `kandidatvarsel-api` og deploy               | Steg 6              |
| 8    | Nytt treffendepunkt og utsendingsknapp i jobbsøkerlisten       | Steg 7, spørsmål 10–11 |

### Utrulling og rollback

Trinn 1 er en ren frontend-endring uten nye kontrakter eller
databasemigrasjoner. Den kan rulles tilbake ved å fjerne malhandlingene.
Innlegg som allerede er opprettet påvirkes ikke.

Trinn 2 endrer backend-oppførsel, men ikke datamodellen, siden flere innlegg
allerede er støttet i databasen. Rollback krever likevel at det håndteres at
det kan finnes treff med mer enn ett innlegg.

Trinn 3 må rulles ut i to steg, se rekkefølgen under trinn 3. Malene kan ikke
fjernes igjen uten videre: `Maler.valueOf` kaster for ukjente navn, så et
rullback av malene ville brutt lesing av varsler som allerede er sendt.

## Definition of done

### Trinn 1

- Godkjente WorkOp-tekster kan settes inn fra `InnleggForm.tsx`.
- Invitasjonsmalen foreslås automatisk for nye WorkOp-treff.
- Møtedetaljene kan legges øverst uten at eksisterende tekst går tapt.
- Publisering og republisering blokkeres ved gjenstående plassholdere.
- Den ferdige teksten går gjennom dagens KI-validering og logges som lagret.
- Redigering av et publisert innlegg registreres som `INTRODUKSJON` og varsles
  gjennom dagens flyt.
- Vanlige rekrutteringstreff er uendret.

### Trinn 2

- Et WorkOp-treff kan ha flere innlegg, hvert med egen tittel og dato.
- Jobbsøkeren ser innleggene som en datert rekke i `rekrutteringstreff-bruker`.
- Nytt innlegg utløser varsel etter avtalt regel.

### Trinn 3

- `KANDIDAT_TREFF_PAAMINNELSE` og `KANDIDAT_TREFF_TA_KONTAKT` finnes som
  `RekrutteringstreffMal` med SMS-, e-posttittel- og e-postkropp-tekst.
- Begge SMS-tekster er under 160 tegn og røper ikke innholdet.
- Veileder kan sende en av malene til valgte jobbsøkere fra jobbsøkerlisten.
- Varselet lenker til treffet i `rekrutteringstreff-bruker`.
- Utsendingen registreres som hendelse på jobbsøkeren og vises med
  leveringsstatus i listen.
- Stillingsvarsler er uendret.

## Review

| Perspektiv              | Vurdering        | Begrunnelse                                                                       |
| ----------------------- | ---------------- | --------------------------------------------------------------------------------- |
| Arkitektur              | ✅               | Gjenbruker innlegg, endringsflyt og eksisterende varselmaskineri                   |
| Sikkerhet og personvern | ✅ med avklaring | Innhold bak innlogging; kontaktinfo, rolletilgang og auditlogg må avklares         |
| Plattform               | ✅               | Ingen nye topics, schedulere, databaser eller Nais-ressurser i noen av trinnene    |
| Endringssikkerhet       | ⚠️               | Trinn 2 endrer antakelsen om ett innlegg; trinn 3 krever to-stegs deploy av maler |

**Konklusjon:** Start med trinn 1. Trinn 2 og 3 er uavhengige av hverandre og
prioriteres etter hva WorkOp trenger mest. Trinn 3 er vesentlig billigere enn
først antatt, fordi `avsenderReferanseId`, `lenkeurl()` og
`MinsideVarselSvarLytter` allerede er malagnostiske.

