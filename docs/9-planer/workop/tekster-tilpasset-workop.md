# Plan: Tekster tilpasset WorkOp

**Status:** Anbefalt første versjon er avklart, tekst og detaljer må
ferdigstilles.

## Mål

Gi jobbsøkere WorkOp-tilpasset informasjon før invitasjon, formøte og
WorkOp-dagen, med minst mulig ny teknikk.

Planen berører:

- `rekrutteringsbistand-frontend`
- `rekrutteringstreff-api`
- `rekrutteringsbistand-aktivitetskort`
- `rekrutteringsbistand-kandidatvarsel-api`
- `rekrutteringstreff-bruker`

## Beslutning

Første versjon bruker treffets eksisterende **innlegg** til all
WorkOp-informasjon, også formøte:

1. Ved opprettelse settes en standard WorkOp-tekst inn i innlegget.
2. Markedskontakten fyller inn dato, tidspunkt, sted og eventuell hilsen.
3. Treffet publiseres og jobbsøkerne inviteres gjennom dagens flyt.
4. Når møtedetaljene er klare, redigeres det samme innlegget. Ny informasjon
   legges øverst.
5. Republisering registreres som endring av `INTRODUKSJON`. De som har svart
   ja kan varsles gjennom dagens endringsflyt.

Denne løsningen krever ikke:

- egen formøtetabell
- flere innlegg per treff
- nytt aktivitetskort
- ny scheduler
- nye Kafka-hendelser
- nye varselmaler i kandidatvarsel-api

Det gir en enkel første versjon som kan prøves i reell bruk før mer
automatisering bygges.

## Avgrensning

Følgende er ikke del av første versjon:

- automatisk utsending to dager før WorkOp (T-2)
- automatisk SMS dagen før WorkOp eller formøte (T-1)
- SMS etter mislykket kontaktforsøk
- strukturert lagring av formøte
- flere daterte innlegg
- egne WorkOp-varianter av alle SMS-, endrings- og avlysningstekster

Disse behovene vurderes på nytt etter erfaring med den innleggbaserte
løsningen.

## Begreper

- **T:** Kalenderdagen WorkOp starter, basert på `fraTid` i `Europe/Oslo`.
- **T-2:** Kalenderdagen to dager før WorkOp, ikke nødvendigvis 48 timer før.
- **T-1:** Kalenderdagen før WorkOp, ikke nødvendigvis 24 timer før.

T-2 og T-1 brukes bare ved omtale av senere automatisering. Første versjon
har ingen scheduler og er derfor avhengig av en manuell rutine.

## Hvorfor bruke innlegget?

Innlegget dekker allerede de viktigste behovene:

- Det støtter rik tekst og vises på den lenkede siden i
  `rekrutteringstreff-bruker`.
- Aktivitetskortet lenker jobbsøkeren til denne siden.
- Innlegget kan redigeres etter publisering.
- `useRepubliser` oppdager endret `htmlContent` og registrerer
  `Endringsfelttype.INTRODUKSJON`.
- Dagens endringsflyt sender kortoppdatering til alle inviterte og kan sende
  MinSide-varsel til jobbsøkere med aktivt svar ja.
- KI-validering og KI-logg fungerer allerede for ett innlegg.

Feltet `sendesTilJobbsokerTidspunkt` finnes, men brukes ikke til å planlegge
eller filtrere visning. Det skal derfor ikke brukes som om det var en
scheduler.

### Begrensninger

- Tidspunktet for redigering er manuelt.
- Jobbsøkeren ser ett samlet innlegg, ikke en meldingshistorikk.
- Endringsvarselet er generisk; det sier at introduksjonen er endret, ikke at
  nye WorkOp-detaljer er publisert.
- Jobbsøkere uten aktivt svar ja får kortoppdatering, men ikke MinSide-varsel.
- Den som redigerer må velge varsling i republiseringsflyten.

Disse begrensningene er akseptable i en første versjon, men må være kjent av
dem som gjennomfører WorkOp.

## Foreslått brukerflyt i frontend

Legg to handlinger i `InnleggForm.tsx`, bare for treff med
`kategori = WORKOP`:

| Handling | Bruk |
| --- | --- |
| **Sett inn WorkOp-invitasjon** | Setter inn introduksjon, informasjon om WorkOp og formøte |
| **Sett inn møtedetaljer** | Setter inn program og praktisk informasjon øverst i eksisterende innlegg |

Standardtekstene bør ligge som konstanter i frontend i første versjon.
Tekstene kan senere flyttes til backend dersom de må kunne endres uten
deploy.

### Plassholdere

Variable verdier markeres tydelig, for eksempel:

- `#WORKOP_DATO#`
- `#WORKOP_TIDSPUNKT#`
- `#WORKOP_STED#`
- `#FORMØTE_DATO#`
- `#FORMØTE_TIDSPUNKT#`
- `#FORMØTE_STED#`
- `#KONTAKTPERSON#`

Publisering og republisering må blokkeres hvis innlegget fortsatt inneholder
en plassholder på formen `#...#`. Dette reduserer risikoen for at uferdig
tekst blir synlig for jobbsøkeren.

Innsetting skal ikke overskrive eksisterende tekst uten bekreftelse.
Møtedetaljene legges øverst slik at det nyeste innholdet er lettest å finne.

### KI-validering

Den ferdig utfylte teksten skal valideres, ikke bare standardteksten.
`htmlContentKiLoggId` peker på siste validering, og `useRepubliser` markerer
den som lagret. Dagens KI-logg fungerer derfor for valgt løsning.

Standardtekstene bør i tillegg forhåndsvalideres og språkvaskes før
produksjonssetting.

## Kartlegging av tekstene fra Trello

| Tekst | Første versjon |
| --- | --- |
| Første melding til jobbsøkeren | Legges i innlegget med **Sett inn WorkOp-invitasjon** |
| SMS etter kontaktforsøk | Utenfor omfang; sendes manuelt |
| SMS dagen før formøte | Utenfor omfang; krever strukturert formøte og scheduler |
| Møtedetaljer to dager før WorkOp | Legges øverst i eksisterende innlegg med **Sett inn møtedetaljer** |
| SMS dagen før WorkOp | Utenfor omfang; krever scheduler og ny varselmal |

Tekstene kan ikke brukes ordrett:

- «Svar her i dialogen» må erstattes med at jobbsøkeren svarer via lenken til
  treffet.
- Dato, tidspunkt, sted og kontaktperson må være tydelige plassholdere.
- Konkrete arbeidsgivere skal ikke nevnes, fordi disse skjules for inviterte
  på WorkOp.
- Bransjer må være redigerbare dersom de varierer mellom treff.
- Personnavn og direktenummer bør unngås dersom et felles kontaktpunkt eller
  «kontakt veilederen din» dekker behovet.

## Tekniske endringer i første versjon

### `rekrutteringsbistand-frontend`

1. Legg standardtekstene i en egen WorkOp-malmodul.
2. Vis de to innsettingshandlingene i `InnleggForm.tsx` for WorkOp.
3. Sett invitasjonsteksten inn i tomt innlegg.
4. Sett møtedetaljene inn øverst i eksisterende innlegg.
5. Varsle før eksisterende innhold overskrives.
6. Valider at ingen `#...#`-plassholdere står igjen ved publisering eller
   republisering.
7. Behold dagens KI-validering av `htmlContent`.

### Øvrige apper

Første versjon krever ingen endringer i:

- `rekrutteringstreff-api`
- `rekrutteringsbistand-aktivitetskort`
- `rekrutteringsbistand-kandidatvarsel-api`
- `rekrutteringstreff-bruker`

Eksisterende endringsflyt skal likevel verifiseres med et WorkOp-treff:

```text
useRepubliser
  → registrerEndring(INTRODUKSJON)
  → rekrutteringstreffoppdatering
  → KandidatInvitertTreffEndretLytter
  → MinSide-varsel til jobbsøkere med aktivt svar ja
```

## Senere behov

### Flere innlegg som en meldingshistorikk

Backend støtter en liste med innlegg, men resten av løsningen antar i stor
grad ett innlegg:

- `InnleggForm.tsx`, `useLagreInnlegg.ts`, `useRepubliser.ts` og interne
  treffvisninger bruker `innleggListe?.[0]`.
- `InnleggService` overskriver første innlegg ved ny opprettelse i `UTKAST`
  for å beskytte mot tidligere duplikatfeil.
- Tittelen er hardkodet til «Om treffet».
- Bruker-appen viser ikke dato på innlegg.
- Innlegg hentes med eldste først.
- KI-loggen er knyttet til treff og felttype, ikke `innlegg_id`.

Ekte meldingshistorikk krever derfor en samlet løsning for opprettelse,
redigering, sortering, tittel, KI-logg og varsling. Dette skal ikke bygges før
brukererfaring viser at ett samlet innlegg er utilstrekkelig.

En mulig senere utvidelse er å vise flere innlegg som en enkel
meldingshistorikk. Markedskontakten kan da publisere en første
WorkOp-invitasjon og senere legge til møtedetaljer som et nytt innlegg.
Jobbsøkeren ser meldingene kronologisk, men kan ikke svare. Dette blir derfor
en **monolog**, ikke en dialog.

En minimumsløsning krever:

1. En «Nytt innlegg»-handling og valg av hvilket innlegg som redigeres.
2. Tittel og publiseringstidspunkt på hvert innlegg.
3. Nyeste innlegg først, eller en tydelig kronologisk tidslinje.
4. KI-validering og KI-logg koblet til riktig `innlegg_id`.
5. Registrering av `INTRODUKSJON` når et nytt innlegg publiseres, slik at
   dagens endringsvarsel kan gjenbrukes.
6. Avklaring av om publiserte innlegg kan redigeres eller slettes. Av hensyn
   til historikken bør hovedregelen være at nytt innhold publiseres som et
   nytt innlegg.

Dette gir bedre oversikt for jobbsøkeren og skiller invitasjon,
formøteinformasjon og møtedetaljer uten å introdusere en ny dialogtjeneste.
Det er likevel mer enn en liten frontend-endring, fordi både lagring,
KI-sporbarhet, intern redigering og visningen i
`rekrutteringstreff-bruker` må tilpasses samlet.

### Strukturert formøte

En senere versjon kan bruke en valgfri én-til-én-relasjon:

```text
rekrutteringstreff 1 ── 0..1 rekrutteringstreff_formote
```

Et formøte trenger minst starttid og sted. Modellen bør være generell, slik
at den også kan brukes av andre rekrutteringstreff. Endring etter invitasjon
må da føres gjennom dagens republiserings- og endringsflyt; ellers kan
aktivitetskortet vise utdatert informasjon.

Strukturert formøte er først nødvendig når dataene skal brukes til
automatisk påminnelse, egen visning, rapportering eller validering.

### Automatiske påminnelser

Automatisk T-2/T-1-utsending krever:

- en idempotent scheduler i `rekrutteringstreff-api`
- mottakerutvalg, normalt jobbsøkere med aktivt svar ja
- håndtering av flyttet eller avlyst treff og endret svar
- ny SMS-mal i kandidatvarsel-api for T-1
- observerbarhet for planlagte, sendte og feilede varsler

Det skal fortsatt være ett aktivitetskort per jobbsøker og treff. Tabellen i
aktivitetskort-appen har en unik kobling på treff og jobbsøker, og svar- og
statusflyten forutsetter ett kort. Et nytt kort for en påminnelse er derfor
ikke aktuelt.

### Egne WorkOp-varseltekster

Hvis SMS, endringsvarsel eller avlysningsvarsel senere skal få egne
WorkOp-varianter, må `treffkategori` sendes i de relevante eventene.
Konsumentene må støtte det valgfrie feltet før produsenten begynner å sende
det.

Nye malnavn i kandidatvarsel-api må innføres i to steg:

1. Deploy støtte for malnavnet i kandidatvarsel-api og frontend.
2. Deploy lytteren som begynner å bruke malnavnet for WorkOp.

Dette er nødvendig fordi `Maler.valueOf(mal)` kaster for ukjente malnavn.
Det bør ikke legges inn en generell fallback som kan skjule kontraktsfeil og
sende feil tekst.

### Dialog i aktivitetsplanen

Dialog kan være et alternativ hvis møtedetaljene skal være en samtale, ikke
bare informasjon. Dette er ikke utredet. Før sporet vurderes må det avklares
om aktivitetskortet har tilgjengelig dialog, hvem som følger opp svar, om
utsending kan gjøres samlet, og hvilket team som eier integrasjonen.

## Teststrategi

### Frontend

- Innsettingshandlingene vises bare for WorkOp.
- Invitasjonsmalen settes inn i tomt innlegg.
- Møtedetaljene legges øverst uten å fjerne eksisterende tekst.
- Brukeren må bekrefte før innhold overskrives.
- Publisering blokkeres ved én eller flere ufylte plassholdere.
- Vanlige rekrutteringstreff er uendret.
- KI-logg-id fra ferdig validert tekst markeres som lagret.

### Integrert flyt

- Publiser og inviter til et WorkOp-treff med utfylt innlegg.
- Rediger innlegget etter publisering.
- Velg varsling for `INTRODUKSJON`.
- Kontroller at jobbsøkere med aktivt svar ja får endringsvarsel.
- Kontroller at innlegget vises med nytt innhold i
  `rekrutteringstreff-bruker`.

## Personvern og sikkerhet

- Ikke legg fødselsnummer eller andre unødvendige personopplysninger i
  tekstene eller logger.
- Ikke nevn konkrete arbeidsgivere i tekst til inviterte.
- Avklar behovet før navn og direktenummer til en ansatt brukes.
- Tilgang og mottakerregler endres ikke i første versjon.
- Ikke logg innholdet i innlegget; logg treff-id og teknisk status.

## Observerbarhet og oppfølging

Første versjon trenger ingen nye alarmer, men første WorkOp etter utrulling
bør følges manuelt:

- Ble riktig standardtekst satt inn?
- Var alle plassholdere fylt ut?
- Ble ferdig tekst KI-validert?
- Ble endringen registrert som `INTRODUKSJON`?
- Fikk jobbsøkere med aktivt svar ja varsel?
- Viste jobbsøkersiden den oppdaterte teksten?

Erfaringene bør brukes til å avgjøre om det er behov for flere innlegg,
strukturert formøte eller automatisk påminnelse.

## Åpne spørsmål

Følgende må avklares før første versjon ferdigstilles:

1. Hvem har ansvar for å oppdatere innlegget før WorkOp-dagen?
2. Når skal oppdateringen gjøres, og hvordan blir den ansvarlige minnet på
   det når løsningen ikke har scheduler?
3. Skal møtedetaljene alltid legges øverst, eller skal eldre tekst kunne
   erstattes?
4. Er bransjene faste, eller skal de alltid fylles ut per treff?
5. Skal navn og direktenummer brukes, eller erstattes med et generelt
   kontaktpunkt?
6. Er standardtekstene godkjent og språkvasket?

Disse spørsmålene kan vente til etter utprøving:

7. Er ett samlet innlegg godt nok, eller trenger jobbsøkeren flere daterte
   meldinger?
8. Må T-2/T-1 automatiseres?
9. Skal aktivitetskort, invitasjons-SMS, endringsvarsel, avlysning og
   jobbsøkersiden få egne WorkOp-tekster?
10. Trenger formøte strukturert lagring?
11. Er dialog i aktivitetsplanen et bedre sted for oppfølging?

## Leveranseplan

| Steg | Leveranse | Avhengighet |
| --- | --- | --- |
| 1 | Godkjenn og språkvask de to standardtekstene | Åpent spørsmål 4–6 |
| 2 | Lag innsettingshandlinger og plassholdervalidering i frontend | Steg 1 |
| 3 | Test publisering, republisering, KI-logg og endringsvarsel | Steg 2 |
| 4 | Prøv løsningen på ett WorkOp og samle erfaring | Steg 3 |
| 5 | Beslutt om senere behov skal prioriteres | Erfaring fra steg 4 |

### Utrulling og rollback

Første versjon er en frontend-endring uten nye kontrakter eller
databasemigrasjoner. Den kan rulles tilbake ved å fjerne
innsettingshandlingene. Innlegg som allerede er opprettet påvirkes ikke.

## Definition of done

- Godkjente WorkOp-tekster kan settes inn fra `InnleggForm.tsx`.
- Invitasjonsteksten kan fylles ut med WorkOp- og formøteinformasjon.
- Møtedetaljene kan legges øverst uten at eksisterende tekst går tapt.
- Publisering og republisering blokkeres ved ufylte plassholdere.
- Den ferdige teksten går gjennom dagens KI-validering og logges som lagret.
- Redigering av et publisert innlegg kan registreres som
  `INTRODUKSJON` og varsles gjennom dagens flyt.
- Vanlige rekrutteringstreff er uendret.
- Den innleggbaserte løsningen er prøvd på minst ett WorkOp før mer
  automatisering besluttes.

## Review

| Perspektiv | Vurdering | Begrunnelse |
| --- | --- | --- |
| Arkitektur | ✅ | Gjenbruker eksisterende innlegg og endringsflyt |
| Sikkerhet og personvern | ✅ med avklaring | Ingen nye mottakere eller data; kontaktinfo må avklares |
| Plattform | ✅ | Ingen nye topics, schedulere, databaser eller Nais-ressurser |
| Endringssikkerhet | ✅ | Avgrenset frontend-endring med enkel rollback |

**Konklusjon:** Gjennomfør den innleggbaserte første versjonen. Ikke bygg
flere innlegg, strukturert formøte eller automatiske påminnelser før
utprøvingen viser at behovet er reelt.
