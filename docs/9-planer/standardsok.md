# Plan: Lagrede søk for stillingsoppdrag og rekrutteringstreff i bruker-api

**Status:** Leveranse 1 (rekrutteringstreff) er implementert i bruker-api og frontend, men ikke rullet ut. Et ROS-utkast ligger i [`ros-standardsok.md`](ros-standardsok.md) og må godkjennes før produksjon. Leveranse 2 (stilling) er implementert i bruker-api, men frontend og konvertering fra stilling-api gjenstår.

## Kort fortalt

- Rekrutteringstreff får lagrede søk i `rekrutteringsbistand-bruker-api`. Hver veileder kan lagre opptil 10 søk per domene, gi dem navn, bruke dem og slette dem. En veileder med lagrede søk har alltid nøyaktig ett standardsøk, som brukes når siden åpnes.
- Standardsøk for stillingsoppdrag flyttes senere fra `rekrutteringsbistand-stilling-api` til samme API og får samme støtte for flere søk.
- Vi planlegger begge nå, men leverer én applikasjon av gangen helt til produksjon. Rekrutteringstreff kommer først.
- Hvert domene får sin egen tabell med lik oppbygning. De deler kode og opprydningsjobb.
- Hvert søk har en id (søkeident). Nav-identen er ikke lenger unik i tabellen.
- Navn bør være beskrivende, men trenger ikke være unike. Like navn gir ingen feilmelding. Alle valg og endringer bruker søkeidenten.
- Hvert søk har datoen `sistAktiv`. Lagring, bruk og «Gjør til standard» fornyer seksmånedersfristen. Ved bruk oppdateres datoen høyst én gang i døgnet.
- Navn og fritekst lagres bare i body og database, aldri i URL-en. Backend fjerner gyldige fødselsnumre før lagring.
- Et søk uten aktivitet på seks måneder slettes helt. Etter fem måneder får det en merknad i menyen.
- Det første søket blir automatisk standard. Veilederen kan velge et annet i «Administrer søk». Når standardsøket slettes eller utløper, blir søket veilederen brukte sist, standard.
- Når siden åpnes med standardsøket, viser statuslinjen «Valgt søk» med navnet. Uten lagrede søk åpnes standardvisningen, den samme som «Fjern alle filtre» gir.

## Slik virker det i dag (stillingsoppdrag)

Menylenken «Stillingsoppdrag» og knappen «Bruk standardsøk» går til `/stilling?brukStandardsok=true`. Da henter `page.tsx` standardsøket fra stilling-api og bytter URL-en med `window.history.replaceState`. Diskettikonet sender query-strengen med `PUT /standardsok` og overskriver det gamle søket. Hver veileder har ett søk.

| Del | Innhold |
| --- | --- |
| Frontend | `app/stilling/_ui/standardsøk/` og `app/api/stilling/standardsok/` |
| API | `GET /standardsok` gir `200` eller `404`. `PUT /standardsok` med `{ søk }` gir `201`. Nav-identen kommer fra tokenet. |
| Tabell `lagret_sok` | `id`, `nav_ident`, `sok` (query-streng), `tidspunkt` (uten tidssone) |
| Lagres ikke | `utenOppdrag`, `visStillingId`, `brukStandardsok`, `finnKandidater`, `visKandidatId`, fritekst |

Svakheter vi ikke tar med oss:

- `lagret_sok` mangler unik-indeks på `nav_ident`, og repositoryet leser før det skriver. To samtidige lagringer kan gi to rader.
- Diskettikonet er deaktivert når søket er likt det lagrede, og sammenligningen er en strengsammenligning. Med flere søk dropper vi denne sammenligningen.
- Mens `brukStandardsok` står i URL-en, ignorerer `StillingsSøkContext` fritekst. Det må endres når fritekst skal lagres.

## Arkitektur

`rekrutteringsbistand-bruker-api` har allerede brukerinnstillinger per Nav-ident, `accessPolicy` for frontend og de rollene vi trenger. Vi trenger ingen ny auth eller ny tjeneste.

```mermaid
flowchart LR
    subgraph FE[rekrutteringsbistand-frontend]
        T[Treffsøk]
        S[Stillingssøk, leveranse 2]
        K[LagredeSøk<br/>felles komponenter]
        P["/api/bruker/standardsok<br/>OBO-proxy"]
    end
    subgraph BA[rekrutteringsbistand-bruker-api]
        C[StandardsøkController]
        V[StandardsøkService]
        F[Fødselsnummerfilter]
        R[StandardsøkRepository]
        J[Opprydningsjobb]
    end
    DT[(standardsok_rekrutteringstreff)]
    DS[(standardsok_stilling, leveranse 2)]

    T --> K
    S --> K
    K --> P --> C --> V --> R
    V --> F
    R --> DT
    R --> DS
    J --> V
```

Controlleren håndterer auth, input-validering og HTTP-svar. Servicen samler reglene for eierskap, antallsgrense, standardvalg og utløp, og styrer transaksjonene. Repositoryet gjør databaseoppslagene, også for opprydningsjobben.

### API

Frontend kaller `/api/bruker/standardsok/{domene}`, og proxyen sender videre til `/api/standardsok/{domene}`. `domene` er `rekrutteringstreff`, og etter leveranse 2 også `stilling`. Andre verdier gir `404`. `{id}` er søkeidenten.

| Metode | Sti | Bruk | Svar |
| --- | --- | --- | --- |
| `GET` | `/api/standardsok/{domene}` | Henter alle veilederens søk. Brukes av menyen, lagre-dialogen og menylenken. | `200` med liste av `StandardsøkDto`. Standardsøket står først, resten er sortert på navn (norsk alfabet) og deretter id. Tom liste uten lagrede søk. |
| `POST` | `/api/standardsok/{domene}` | «Lagre som nytt søk» | `201` med `StandardsøkDto`. `409` når veilederen allerede har 10 søk. Like navn tillates. |
| `PUT` | `/api/standardsok/{domene}/{id}` | «Erstatt et lagret søk». Endrer ikke standardvalget. | `200` med `StandardsøkDto` |
| `PUT` | `/api/standardsok/{domene}/{id}/standard` | «Gjør til standard» i administrasjonen. Fjerner merket fra det forrige og fornyer fristen for det valgte søket. | `204` |
| `DELETE` | `/api/standardsok/{domene}/{id}` | «Slett» | `204` |
| `POST` | `/api/standardsok/{domene}/{id}/bruk` | Kontrollerer og registrerer bruk før søket aktiveres. Ingen body. | `200` med gjeldende `StandardsøkDto` |

Alle kall med `{id}` gir `404` hvis søket ikke finnes, er utløpt eller tilhører en annen veileder. Svaret skiller ikke mellom disse tilfellene. Ugyldig body gir `400` med en fast feilmelding uten innhold fra input.

`POST .../bruk` er `POST` fordi kallet endrer data. En `GET` kan sendes på nytt av SWR, forhåndshenting eller proxyer og skal ikke fornye fristen. Svaret fra `.../bruk` oppdaterer SWR-cachen uten et ekstra `GET`-kall.

| Domene | Roller |
| --- | --- |
| `rekrutteringstreff` | `ARBEIDSGIVER_RETTET`, `JOBBSOKER_RETTET`, `UTVIKLER`, som menypunktet |
| `stilling` | Alle fem rollene i bruker-api, som `/api/innstillinger`. Stillingssøket har ingen rollekrav i menyen. |

Nav-identen kommer alltid fra tokenet, aldri fra body eller sti. Hver spørring med `{id}` har både `id` og `navIdent` i `WHERE`.

| DTO | Felt | Type | Regel |
| --- | --- | --- | --- |
| `LagreStandardsøkDto` | `navn` | String | 1 til 25 tegn etter trimming. Går gjennom fødselsnummerfilteret. Like navn tillates. |
| | `filter` | String | Query-streng uten `?`, maks 2 000 tegn, kan være tom. Backend validerer tillatte parametere og verdier per domene. |
| | `fritekst` | Liste av String | Maks 10 ord og 200 tegn totalt, kan være tom |
| `StandardsøkDto` | `id` | UUID | Søkeidenten |
| | `navn` | String | |
| | `erStandard` | Boolean | |
| | `filter` | String | |
| | `fritekst` | Liste av String | Etter filtrering |
| | `sistAktiv` | Dato | Siste lagring, bruk eller valg som standard |
| | `slettes` | Dato | Seks kalendermåneder etter `sistAktiv` |
| | `visSlettevarsel` | Boolean | Beregnes av backend. Sann fra fem kalendermåneder etter `sistAktiv` til søket utløper. |
| | `fritekstFiltrert` | Boolean | Bare i svar på `POST` og `PUT`. `true` hvis backend fjernet noe fra navn eller fritekst. |

Standardvalg inngår ikke i `LagreStandardsøkDto`. Backend sørger for at en veileder med aktive søk alltid har nøyaktig ett standardsøk. Det første søket blir standard. Slettes eller utløper standardsøket, blir det aktive søket med nyest `sistAktiv` standard, med navn og id som andre og tredje sorteringsnøkkel.

### Tabeller: én per domene

Begge tabellene opprettes i `V9__standardsok.sql`. Ingen av dem er rullet ut, så de deler migrering. Kolonnenavnene følger stilen i repoet (`navIdent`, som i `brukerinnstillinger`).

Databasen i bruker-api etter begge leveransene. Ingen tabeller har fremmednøkler. Den stiplede linjen viser at søkene og brukerinnstillingene bruker samme `navIdent`, uten at databasen håndhever koblingen. En veileder kan ha søk uten en rad i `brukerinnstillinger`. `text_array` er `text[]`, fordi Mermaid ikke tillater klammer i typenavn.

```mermaid
erDiagram
    brukerinnstillinger |o..o{ standardsok_rekrutteringstreff : "samme navIdent"
    brukerinnstillinger |o..o{ standardsok_stilling : "samme navIdent"

    standardsok_rekrutteringstreff {
        uuid id PK "Ny i V9"
        text navIdent "Ikke unik"
        text navn "Maks 25 tegn"
        boolean erStandard "Høyst ett per navIdent"
        text filter "Query-streng"
        text_array fritekst "Fødselsnumre fjernet"
        date sistAktiv "Raden slettes etter 6 mnd"
    }

    standardsok_stilling {
        uuid id PK
        text navIdent "Ikke unik"
        text navn "Maks 25 tegn"
        boolean erStandard "Høyst ett per navIdent"
        text filter "Query-streng"
        text_array fritekst "Fødselsnumre fjernet"
        date sistAktiv "Raden slettes etter 6 mnd"
    }

    brukerinnstillinger {
        text navIdent PK "Finnes fra før"
        integer antallLesteNyheter
        boolean darkMode
        boolean windowMode
        text tekststorrelse
    }

    nyheter {
        bigserial id PK "Finnes fra før"
        uuid nyhetId UK
        text tittel
        text innhold
        text status
        timestamp opprettetDato
        text opprettetAv
        timestamp sistEndretDato
        text sistEndretAv
    }

    tilbakemeldinger {
        uuid id PK "Finnes fra før"
        varchar navn
        text tilbakemelding
        timestamp dato
        varchar status
        varchar kategori
        varchar trelloLenke
    }
```

| Tabell | Status | Innhold |
| --- | --- | --- |
| `standardsok_rekrutteringstreff` | Ny, implementert i V9 | Lagrede søk for rekrutteringstreff |
| `standardsok_stilling` | Ny, leveranse 2 | Lagrede søk for stillingsoppdrag, med samme kolonner. Data flyttes fra stilling-api. |
| `brukerinnstillinger` | Finnes fra før | Visningsvalg per veileder |
| `nyheter` | Finnes fra før | Nyheter i Rekrutteringsbistand |
| `tilbakemeldinger` | Finnes fra før | Tilbakemeldinger fra veiledere |

Kolonnene i begge søketabellene:

| Kolonne | Type | Merknad |
| --- | --- | --- |
| `id` | `uuid` primærnøkkel | Søkeidenten, lages av backend |
| `navIdent` | `text not null` | Ikke unik. Indeks for oppslag per veileder. |
| `navn` | `text not null` | Maks 25 tegn etter trimming. Ikke unikt, heller ikke sammen med `navIdent`. |
| `erStandard` | `boolean not null default false` | Delvis unik indeks på `navIdent` der `erStandard` er sann, så hver veileder har høyst ett standardsøk |
| `filter` | `text not null` | |
| `fritekst` | `text[] not null default '{}'` | |
| `sistAktiv` | `date not null` | Settes ved lagring, bruk og valg som standard. Indeks for opprydningsjobben, som sletter rader med `sistAktiv` på eller før seneste utløpte dato. |

Regler i backend:

- Grensen på 10 aktive søk gjelder også ved samtidige opprettelser. Opprettelse og standardbytte tar en advisory lock per tabell og Nav-ident (`pg_advisory_xact_lock`), som gjelder på tvers av podene. Transaksjonen kjører med `READ COMMITTED`, så tellingen etter låsen ser det andre kall har committet. Under `REPEATABLE READ`, som er standard i bruker-api, ville tellingen vært utdatert.
- Bytte av standardsøk fjerner merket fra det gamle, setter det på det nye og oppdaterer det nye søkets `sistAktiv` i én transaksjon. Datoen for det gamle søket endres ikke. Den delvise unike indeksen er et siste vern mot flere standardsøk.
- Når veilederen sletter standardsøket, velger backend et nytt i samme transaksjon. Sletter opprydningsjobben det, velges et nytt neste gang veilederen henter listen eller oppretter et søk. Utløpte søk slettes før valget, fordi et utløpt standardsøk ellers stopper den unike indeksen.
- Ett repository dekker begge tabellene. Tabellnavnet kommer fra en enum i koden, aldri fra input.
- Søk slettes når veilederen sletter dem, eller etter seks måneder uten aktivitet. Se [Sletting etter seks måneder uten aktivitet](#sletting-etter-seks-måneder-uten-aktivitet).

| | To tabeller (valgt) | Én tabell med `domene`-kolonne |
| --- | --- | --- |
| Nøkkel | `id`, indeks på `navIdent` | `id`, indeks på `navIdent` + `domene` |
| Unike regler | Høyst ett standardsøk per `navIdent`. Like navn tillates. | Høyst ett standardsøk per `navIdent` + `domene`. Like navn tillates. |
| Ulik utvikling | Hvert domene kan få egne kolonner eller regler uten å påvirke det andre | Domenespesifikke kolonner blir `null` for det andre domenet |
| Levere per applikasjon | Hver leveranse har sin egen tabell og migrering | Felles tabell fra første leveranse, nye domener kan innføres senere |
| Fjerne et domene | `DROP TABLE` | `DELETE` og en sjekk-constraint å endre |
| Opprydning | Én setning per tabell | Én setning |

Filtrene lagres som en query-streng. Frontend bruker dem, og backend validerer tillatte parametere og verdier per domene. Ulike filtre i seg selv tvinger derfor ikke fram ulike tabeller. Grunnen til å skille er at domenene trolig får ulike regler, og at vi leverer dem hver for seg. Én tabell blir bedre hvis vi får mange domener, for eksempel kandidatsøk og jobbsøkersøk i treff.

### Dato for siste aktivitet

Vi lagrer en dato og ikke et tidspunkt. Det holder for sletting etter seks måneder og for å vise «sist aktiv». Det sier også mindre om når den ansatte jobber. Backend regner datoen i tidssonen `Europe/Oslo`.

`sistAktiv` settes til i dag når veilederen lagrer søket som nytt, erstatter det, tar det i bruk eller velger «Gjør til standard». Å hente listen eller åpne menyen endrer den ikke.

`POST .../bruk` leser søket og oppdaterer bare når `sistAktiv` er eldre enn i dag. Det gir høyst én databaseskriving per søk i døgnet ved bruk, selv om frontend kaller hver gang. Ved lagring og standardbytte skriver backend de øvrige endringene som vanlig.

Slettefristen er seks kalendermåneder etter `sistAktiv`, og varselet starter etter fem. Finnes ikke dagen i målmåneden, brukes månedens siste dag. Søket er utløpt fra og med slettefristen. Backend bruker samme regel for svar, oppslag og opprydning, og beregner `visSlettevarsel` for frontend. Reglene testes ved månedsslutt og skuddår.

Én dato per søk erstatter egne datoer for navn og fritekst. Begge følger raden.

## Fritekst og navn

### Hvorfor bare i body

Fritekst ligger i React-state i begge søkene i dag, og ikke i URL-en. Det hindrer at den havner i nettleserhistorikk, tilgangslogger og Umami. Vi beholder det: fritekst og navn går bare i body på `POST` og `PUT` og i svarene fra API-et. Søkeidenten brukes i stien til API-kallene. På søkesiden står den i URL-en som `lagretSok`, så valgt søk overlever refresh. Id-en er en tilfeldig UUID og sier ingenting om innholdet. Når et søk brukes, legges fritekst i state og vises som chips.

Backend avviser ukjente filterparametere og ugyldige verdier. `filter` kan dermed ikke brukes til å lagre navn eller fritekst som senere legges i URL-en.

### Fjerning av fødselsnumre

Backend fjerner fødselsnumre fra navn og fritekst før lagring. Søket filtreres ikke, så veilederen får treff som før.

```mermaid
sequenceDiagram
    actor V as Veileder
    participant FE as Frontend
    participant API as bruker-api
    participant DB as Tabell for domenet
    V->>FE: Lagre i lagre-dialogen
    FE->>API: POST eller PUT { navn, filter, fritekst }
    API->>API: Valider lengder og tillatte filterparametere og verdier
    API->>API: Finn 11 sifre, også med mellomrom eller punktum etter 6 sifre
    API->>API: Fjern dem som er gyldige fødselsnumre etter dagens regler
    API->>API: Trim, fjern tomme ord
    API->>DB: Lagre med navIdent fra tokenet, sistAktiv settes til i dag
    API-->>FE: StandardsøkDto med fritekstFiltrert
    FE-->>V: «Fødselsnummer ble fjernet fra navn eller søkeord» hvis filtrert
```

Et tall fjernes når det har 11 sifre, også med mellomrom eller punktum etter de seks første. Filteret sjekker ikke dato eller kontrollsiffer. Da fanger det D-nummer, H-nummer, syntetiske nummer og tastefeil, og nye regler fra Skatteetaten (som reglene fra 2032) krever ingen endring. Vi fjerner heller for mye enn for lite. Tall med færre eller flere sifre, som organisasjonsnumre, blir stående. Blir navnet tomt etter filtrering, gir backend `400`.

### Sletting etter seks måneder uten aktivitet

Navn er også fritekst, så vi sletter hele søket og ikke bare søkeordene. Søket utløper seks kalendermåneder etter `sistAktiv`.

- `GET` returnerer aldri utløpte søk, selv om jobben ikke har kjørt.
- Kall med `{id}` mot et utløpt søk gir `404`. Eierskap og utløp kontrolleres før eventuell oppdatering, så et kall ikke kan vekke søket til live.
- `POST` sletter veilederens utløpte søk i samme transaksjon før grensen på 10 sjekkes. Da teller ikke usynlige søk med, og et utløpt standardsøk erstattes før det nye søket lagres.
- Jobben sletter raden fysisk ved neste kjøring, innen en time. Skjuling ved fristen og ventetiden fram til sletting er beskrevet i ROS-en (LS-03).

Et søk som brukes, lagres eller velges som standard jevnlig, har ingen øvre grense for hvor lenge navn og søkeord blir liggende. Fødselsnummerfilteret fjerner ikke andre personopplysninger. Denne oppbevaringen er vurdert i [ROS-en](ros-standardsok.md) (LS-01 og LS-02), og må godkjennes før produksjon.

### Merknad før sletting

Vi markerer søk fra fem kalendermåneder etter `sistAktiv`. Merknaden lagres ikke. Backend beregner `slettes` og `visSlettevarsel`, som frontend bruker i visningen.

- Merknaden koster ingen ny kolonne, jobb eller varsling.
- Veilederen ser den i menyen og i «Administrer søk». Bruk, lagring eller «Gjør til standard» starter de seks månedene på nytt. Vi lager ingen egen knapp for å beholde søk.
- Uten merknad forsvinner søk uten forvarsel. Det er særlig ille for standardsøket, som brukes når siden åpnes.

Vi sender ikke varsel utenfor løsningen, for eksempel e-post. Det krever mer enn gevinsten forsvarer.

### Opprydningsjobb

- Kjører hver time i bruker-api. For hver tabell sletter den rader som har nådd slettefristen. Spørringen bruker indeksen på `sistAktiv` og koster lite.
- Én felles jobb for alle domenetabellene.
- Jobben er idempotent. Den kan derfor kjøre på alle podene uten leader election.
- Jobben stopper når Javalin stopper. Gjentatte start- og stoppkall oppretter ikke flere jobber.
- Jobben logger og teller bare antall rader, aldri innhold.
- Feil og tidspunktet for siste vellykkede kjøring måles. Det varsles hvis jobben ikke har kjørt uten feil på seks timer. Etter en deploy varsles det bare hvis de to første kjøringene feiler.

## Frontend

Vi beholder plasseringen under søkefeltet. «Bruk standardsøk» blir en meny med de lagrede søkene, og diskettikonet blir stående ved siden av menyen. Skissene under er et utgangspunkt for designer, ikke et ferdig design. Alt bygges med Aksel-komponenter (`ActionMenu`, `Tooltip`, `Modal`, `RadioGroup`, `TextField`, `Select`, `Link`).

### Meny og statuslinje

Menyen erstatter knappen «Bruk standardsøk». Et klikk på et søk tar det i bruk og lukker menyen. Søkene er radiovalg (`ActionMenu.RadioItem`), så søket som er i bruk, er markert så lenge filtrene er uendret. Like navn er tillatt, men hvert menyelement bruker id-en til riktig søk. Standardsøket står øverst, og resten står i alfabetisk rekkefølge. Da står søkene på samme plass hver gang, også etter at veilederen har brukt et av dem.

```text
[ Lagrede søk ▾ ] [💾]
Valgt søk: Treff i Oslo

  ┌ Lagrede søk ──────────┐
  │ (•) Treff i Oslo      │
  │     Standard          │
  │ ( ) Treff Vestland    │
  │ ( ) Mine utkast       │
  │     Slettes snart     │
  │ ───────────────────── │
  │ ⚙ Administrer søk     │
  └───────────────────────┘
```

- Under navnet står en liten grå merknad: «Standard» for standardsøket og «Slettes snart» for søk med `visSlettevarsel`. Har søket begge, skilles de med « · ». Datoen står i «Administrer søk», fordi menyen er smal.
- Vi bruker ikke stjerne eller hake. Stjernen var ikke intuitiv, og Aksel fraråder hake i menyer fordi den ligner radiovalg.
- Disketten er en ikonknapp på samme rad som menyen. Sidepanelet er for smalt til en tekstknapp ved siden av menyen. Knappen har tooltip og tilgjengelig navn «Lagre søk», og er deaktivert uten filter eller søkeord.
- Statuslinjen viser navnet på søket som er i bruk, men bare så lenge filtrene og søkeordene er like det lagrede søket. Endrer veilederen noe, også med «Fjern alle filtre», forsvinner linjen og markeringen i menyen. Slik kan veilederen velge bort et søk uten en egen knapp. Linjen kommer tilbake hvis filtrene blir like søket igjen. Linjen er en `aria-live`-region.
- Uten lagrede søk viser menyen «Du har ingen lagrede søk» og «Velg filtre og trykk på disketten.».
- Fokus går tilbake til knappen som åpnet dialogen, også når dialogen lukkes etter lagring eller med Esc.

### Når siden åpnes

- Siden åpnes med standardsøket når URL-en ikke har søkeparametere (`visning`, `statuser`, `publisertStatuser`, `fylker`, `kommuner`, `kontorer`, `sortering` eller `side`). Statuslinjen viser «Valgt søk» med navnet på standardsøket.
- Har URL-en søkeparametere, for eksempel etter refresh eller fra en delt lenke, går de foran standardsøket.
- Id-en til søket som er i bruk, står i URL-en som `lagretSok`. Ved refresh hentes søkeordene fra det lagrede søket når filtrene i URL-en er like søkets filtre. Statuslinjen viser da navnet, og søket er markert i menyen. Søket mot treffene venter til søkeordene er på plass.
- Endrer veilederen filtre, sortering eller søkeord, fjernes `lagretSok`. Det samme skjer når søket er slettet, utløpt eller tilhører en annen veileder.
- Menylenken legger på `?brukStandardsok=true`. Det trengs fordi siden ikke lastes på nytt når veilederen allerede står på treffsøket.
- Uten lagrede søk åpnes standardvisningen: «Publisert», «Alle treff» og «Sist oppdaterte», uten søkeord.

### Fjern alle filtre

Chipen «Fjern alle filtre» står først i chip-raden over treffene. Den vises når filtre, sortering eller søkeord avviker fra standardvisningen, også når ingen av endringene vises som chip. Et klikk setter alt tilbake til standardvisningen og fjerner parameterne fra URL-en. Statuslinjen forsvinner, fordi filtrene ikke lenger er like et lagret søk. Standardvisningen tilsvarer stillingssøket, som faller tilbake til `publisert=intern&statuser=publisert`.

### Smale skjermer

Under 1024 px er filterpanelet skjult. Knappen «Filtrer» ved pagineringen åpner panelet i et ark, med søkefelt, lagrede søk og filtre. Kandidatsøket bruker samme mønster.

Kjent feil: Esc i menyen eller en dialog inne i arket lukker hele arket. Feilen gjelder det felles arket (`Sidepanel.tsx`), der Radix og Aksel begge lytter etter Esc. Den rettes i en egen oppgave, fordi rettingen påvirker alle sidepaneler.

### Dialog: «Lagre søk»

Disketten åpner dialogen. Gjeldende filtre og søkeord lagres. Enter sender skjemaet.

```text
┌ Lagre søk ─────────────────────────────────────┐
│ (•) Lagre som nytt søk                         │
│ ( ) Erstatt et lagret søk                      │
│                                                │
│ Søk som skal erstattes                         │
│ Filtrene og søkeordene i søket byttes ut med   │
│ dem du har valgt nå.                           │
│ [ Velg søk            ▾ ]                      │
│                                                │
│ Navn på søket                                  │
│ Maks 25 tegn. Ikke skriv personopplysninger.   │
│ [_______________]                              │
│                                                │
│ Søk du ikke har brukt på seks måneder, slettes.│
│                                                │
│            [ Avbryt ]  [ Lagre | Erstatt søk ] │
└────────────────────────────────────────────────┘
```

- Uten lagrede søk vises ikke valgene. Dialogen sier i stedet: «Det første søket du lagrer, blir standardsøk. Det brukes når du åpner siden.»
- Med 10 søk er «Lagre som nytt søk» deaktivert, med teksten «Du har 10 lagrede søk. Erstatt et søk eller slett et i Administrer søk.»
- Dialogen starter på «Lagre som nytt søk», eller på «Erstatt et lagret søk» når veilederen har 10 søk. Listen starter på «Velg søk», og veilederen må velge før lagring. Vi velger ikke søket i bruk på forhånd, så veilederen ikke overskriver et søk ved et uhell.
- Navnet for nytt søk og navnet ved erstatting holdes hver for seg. Når veilederen velger søket som skal erstattes, fylles navnet inn og kan endres.
- Knappen heter «Erstatt søk» ved erstatting og «Lagre» ellers.
- Lagre-dialogen avviser lukking mens lagring pågår, både med lukkeknappen og med Esc. Ved feil blir dialogen stående med feilmeldingen og kan lukkes eller sendes på nytt. Dette løser ikke den kjente Esc-feilen i filterarket på smale skjermer.
- Frontend og backend validerer lengden. Vi ber om et beskrivende navn, men validerer ikke at navnet er unikt.
- Dialogen har ikke standardvalg. Det første søket blir automatisk standard, senere valg gjøres i «Administrer søk».

### Dialog: «Administrer søk»

```text
Administrer søk (3 av 10)

Velg et navn for å bruke søket. Standardsøket brukes når du åpner siden. Slettes
standardsøket, blir søket du brukte sist, standard.
Søk du ikke har brukt på seks måneder, slettes.
──────────────────────────────────────────────────────────────
Treff i Oslo                                            [Slett]
Sist brukt 20.10.2026 · Standard
──────────────────────────────────────────────────────────────
Treff Vestland                      [Gjør til standard] [Slett]
Sist brukt 12.10.2026
──────────────────────────────────────────────────────────────
Mine utkast                         [Gjør til standard] [Slett]
Sist brukt 01.05.2026 · Slettes 01.11.2026
──────────────────────────────────────────────────────────────
                                                         [Lukk]
```

- Søkene er en liste, ikke en tabell. På smale skjermer flytter knappene seg under navnet, så datoene ikke presses sammen.
- Navnet er en lenke som tar søket i bruk og lukker dialogen.
- Linjen under navnet viser «Sist brukt», «Standard» i halvfet skrift for standardsøket og «Slettes dd.mm.åååå» for søk med `visSlettevarsel`. Vi viser ikke slettedatoen for alle søk.
- Etter «Gjør til standard» leses kvitteringen ««Treff Vestland» er nå standardsøket.» opp.
- «Slett» bytter innholdet i dialogen til «Slett lagret søk» med spørsmålet «Vil du slette «Treff i Oslo»?» og knappene «Slett» og «Avbryt». Esc og lukkeknappen går tilbake til listen.
- Fokus flyttes til «Avbryt» i bekreftelsen, tilbake til radens «Slett» ved avbryt og til «Lukk» etter sletting. Kvitteringen leses opp.

«Gjør til standard» fornyer fristen for det valgte søket. Hvis standardsøket slettes eller utløper, blir søket veilederen brukte sist, standard. Listen hentes på nytt etter sletting, så merket «Standard» flytter seg med en gang.

### Filer

| Hvor | Endring |
| --- | --- |
| `components/standardsøk/` (ny) | Felles meny, lagre-dialog og administrer-dialog. Domenet sender inn hvilke parametre som lagres og hvordan et søk tas i bruk. |
| `components/standardsøk/useStandardsøkFraMenylenke.ts` | Felles oppstartsflyt for standardsøket, både når siden åpnes og fra menylenken. Venter på søkelisten og starter høyst én bruk per inngang. Domenet bestemmer hvordan søket brukes og hvilke meldinger og målinger som sendes. |
| `app/rekrutteringstreff/_utils/lagredeSøkFilter.ts` | Bygger og tolker filterstrengen med domenets typer og standardverdier. Beholder skillet mellom manglende filter og tom liste. |
| `app/api/bruker/standardsok/[domene]/` (ny) | Proxy-rute for liste og `[id]`, SWR-hook per domene, mutasjoner, Zod-skjema og MSW-mock |
| Treffsøket | Meny og diskett under søkefeltet, og «Filtrer» for smale skjermer. `RekrutteringstreffSøkContext` holder id-en til søket som er i bruk i URL-en (`lagretSok`) og søkeordene i state. Ved `brukStandardsok` venter søket mot `/sok` på at standardsøket er kontrollert, eller at frontend har vist en feil og gått videre med vanlig søk. |
| `AppSidebar.tsx` | `?brukStandardsok=true` på menylenken til rekrutteringstreff |
| `components/filter/ValgteFiltre.tsx` og `TømFiltre.tsx` | Valgfrie props for å vise «Fjern alle filtre» uten chips og nullstille med domenets egen funksjon. Andre søk beholder dagens oppførsel. |
| Stillingssøket (leveranse 2) | Bytter til bruker-api og de felles komponentene. Setter fritekst fra søket i `StillingsSøkContext` og fjerner blokkeringen av fritekst under `brukStandardsok`. Lagre er mulig når filteret er tomt, men fritekst finnes. Reglene for kandidat (`visKandidatnr`) og portefølje beholdes. `app/api/stilling/standardsok/` slettes. |

Slik tas et søk i bruk:

```mermaid
flowchart TD
    A[Veileder åpner siden] --> B[GET henter listen]
    B --> C{Hvordan?}
    C -->|Åpner siden uten søkeparametere eller fra menylenken| D{Har lagrede søk?}
    D -->|Ja| E[Bruk standardsøket]
    D -->|Nei| F[Standardvisning]
    C -->|Velger i menyen| G[Bruk valgt søk]
    E --> H{Er søket utløpt i cachen?}
    G --> H
    H -->|Ja| X[Vis melding og oppdater listen]
    H -->|Nei| J[POST .../bruk før aktivering]
    J --> L{Svar}
    L -->|200| M[Oppdater cachen med svaret]
    L -->|404| X
    L -->|Annen feil| Y[Vis feil, ikke aktiver lagret søk]
    M --> N[Sett filtre og lagretSok i URL, fritekst i state]
```

Frontend kaller alltid `POST .../bruk` før et søk aktiveres, også når søket er brukt i dag. Søket kan være slettet eller endret i en annen fane, og svaret gir gjeldende innhold eller `404`. Oppstartsflyten venter også til listen er hentet på nytt, så den ikke velger standardsøk fra en utdatert cache.

Ved utløpt søk eller `404` fjernes søket fra cachen, listen hentes på nytt og veilederen får en melding om at søket er slettet eller utløpt. Filtre og fritekst fra det gamle søket aktiveres ikke. Ved andre feil vises en feilmelding med mulighet til å prøve igjen. Vanlig søk er fortsatt tilgjengelig. En feil ved henting av listen vises som en feil, ikke som en tom liste.

Detaljer for treffsøket:

- Lagres: `visning`, `kontorer`, `statuser`, `publisertStatuser`, `fylker`, `kommuner`, `sortering`. Lagres ikke: `side`, `visRekrutteringstreffId`, `brukStandardsok`, `lagretSok`.
- Lagret `visning=mine` blir `alle` for veiledere uten arbeidsgiverrettet rolle. `UTKAST` filtreres allerede bort for dem.
- `visning=mitt_kontor` følger veilederens aktive kontor.
- Diskettikonet er aktivt når søket har minst ett filter eller søkeord.
- Svaret fra `.../bruk` oppdaterer SWR-cachen, så merknaden forsvinner uten et ekstra `GET`-kall. Etter standardbytte hentes listen på nytt, siden også det gamle standardmerket er endret.
- Listen oppdateres ved nytt døgn og når siden får fokus igjen, slik at utløpsvarsler og slettede søk ikke blir liggende i en gammel visning.

## Leveranser

Hver leveranse går helt til produksjon før neste starter.

```mermaid
flowchart TD
    subgraph L1[Leveranse 1: rekrutteringstreff]
        A1[bruker-api: felles kode, fødselsnummerfilter, jobb og tabell for treff] --> A2[Frontend: meny, dialoger og treffsøk]
        A2 --> A3[ROS oppdatert]
        A3 --> A4[Produksjon, så menylenken]
    end
    subgraph L2[Leveranse 2: stillingsoppdrag]
        B1[bruker-api: tabell for stilling og konvertering med Flyway] --> B2[Frontend: stillingssøket bytter til bruker-api]
        B2 --> B3[Produksjon]
        B3 --> B4[Fjern endepunkt i stilling-api og dropp lagret_sok]
    end
    L1 --> L2
```

**Leveranse 1** kan ikke gå til produksjon før [ROS-en](ros-standardsok.md) er godkjent og beslutningene der er tatt.

**Leveranse 2** konverterer data med Flyway:

- Vi tar den nyeste raden per `nav_ident`, siden `lagret_sok` kan ha duplikater.
- Hver veileder får ett søk med navnet «Standardsøk» og `erStandard` satt. `sok` blir `filter`, og `fritekst` blir tom.
- `sistAktiv` settes til konverteringsdatoen. Stilling-api registrerer ikke bruk, og `tidspunkt` er bare når søket sist ble lagret. Med `tidspunkt` ville søk som brukes daglig, men ble lagret for lenge siden, blitt slettet med en gang.
- Konverteringen må kjøre før frontend bytter, eller hoppe over veiledere som allerede har søk i bruker-api hvis den kjører etter.
- Tilgang mellom databasene, lagringer under overgangen og tidspunktet for siste synkronisering avklares før leveranse 2.
- Nav-identer skal ikke ligge i migreringsfiler i git.
- `sok` renses med `Filterregler` for stilling før lagring, med samme regler som API-et bruker. Ukjente parametere og ugyldige verdier fjernes, og resten beholdes. Eksisterende rader kan ha parametere som ikke skal lagres (`brukStandardsok`) og eldre statusverdier (`publisert`, `utløpt`, `stoppet`). Uten rensing havner de i URL-en når søket brukes, og søket kan ikke erstattes uten at veilederen endrer det.

**Backend i leveranse 2 (implementert).** Controller, service, repository, låsing, utløp, opprydningsjobb, metrikker og fødselsnummerfilter er felles for alle domener. Det som er særskilt for hvert domene, ligger i én fil i pakken `domene`. Pakkene under `standardsok`:

| Pakke | Innhold |
| --- | --- |
| `standardsok` | `Standardsøk`, controller, service, feil og metrikker |
| `domene` | Den abstrakte `StandardsøkDomene` (`stinavn`, `tabell`, `roller`, `filterregler`), registeret `Standardsøkdomener` med `alle` og `fraStinavn`, og én fil per domene: `RekrutteringstreffDomene` med `RekrutteringstreffFilterparameter` og `StillingDomene` med `StillingFilterparameter` |
| `filter` | `Filterregler`, interfacet `Filterparameter`, `filterreglerFor<P>()`, felles verdiregler (`enAv`, `liste`, fylkes-, kommune- og kontornumre) og `Fødselsnummerfilter` |
| `database` | `StandardsøkRepository` og `Låsing.kt` |
| `utlop` | `Utløpsregler` og opprydningsjobben |

- `standardsok_stilling` i `V9__standardsok.sql`, med samme kolonner og indekser som `standardsok_rekrutteringstreff`.
- Et nytt domene er et `object` som arver `StandardsøkDomene`, en enum som implementerer `Filterparameter` og en linje i `Standardsøkdomener.alle`. Registeret står i et eget `object` og ikke i en `companion` i basisklassen, fordi en liste over objektene der kan gi `null` ved oppstart av klassene.
- `StillingDomene` gir tilgang til alle fem rollene.
- Komponenttestene ligger i den abstrakte `StandardsøkKomponenttest`. `RekrutteringstreffStandardsøkTest` og `StillingStandardsøkTest` kjører dem med egne filtre og har egne rolletester. `FilterreglerTest` tester mekanikken med egne testparametere, og `RekrutteringstreffFilterTest` og `StillingFilterTest` tester reglene for hvert domene.

Gjenstår i backend: konverteringen fra `lagret_sok` (åpent spørsmål 1). Den skal rense `sok` med de samme `Filterregler`. Rensemetoden lages sammen med konverteringen.

**Filterregler for stilling.** Parameterne og verdiene følger stillingssøket i frontend (`StillingsSøkQueryparam` og enumene som hører til):

| Parameter | Regel |
| --- | --- |
| `portefolje` | `intern`, `visMine`, `mittKontor` eller `arbeidsplassen` |
| `sortering` | `publiseringsdato`, `utløpsdato` eller `mestRelevant` |
| `statuser` | Liste av statusene i statusfilteret (`VisningsStatus`): «Åpen for søkere», «Stengt for søkere», «Utløpt - Stengt for søkere», «Fullført» og «Ikke publisert» |
| `stillingskategori` | Liste av verdiene i `Stillingskategori` |
| `inkludering`, `inkluderingUnderkategori` | Liste av verdiene i `Hovedtag` og `Subtag` |
| `fylker`, `kommuner` | Liste av fylkesnumre (2 sifre) og kommunenumre (4 sifre), som i rekrutteringstreff |

Disse lagres ikke og avvises: `fritekst`, `side`, `brukStandardsok`, `brukKriterierFraKandidat`, `modal`, `utenOppdrag`, `visStillingId`, `finnKandidater` og `visKandidatId`. `publisert` og `felter` er heller ikke med, fordi ingen del av stillingssøket setter dem i dag. Verdisettene står i bruker-api, så en ny filterverdi i stillingssøket krever en endring i `StillingFilterparameter`. Ellers avvises lagringen med `400`.

Rollback i leveranse 2, før stilling-api ryddes: frontend peker tilbake til stilling-api. Søk lagret i bruker-api i mellomtiden beholdes der, men er ikke tilgjengelige i den gamle løsningen. Flere søk kan ikke tilbakeføres direkte til dagens ene standardsøk. Rollback-vindu og håndtering av disse endringene avklares i leveranse 2 før det gamle endepunktet og tabellen fjernes.

## Tester

| Nivå | Hva |
| --- | --- |
| Enhetstest, bruker-api | Fødselsnummerfilteret: tall med 11 sifre (uavhengig av dato og kontrollsiffer), nummer med mellomrom eller punktum, tall som er del av et lengre tall, organisasjonsnumre og vanlige ord. Testene bruker åpenbart falske tall. |
| Komponenttest, bruker-api | Tom liste uten lagrede søk. Opprett, erstatt og slett. Like navn tillates, og endring eller sletting av ett søk påvirker ikke et annet med samme navn. Det ellevte aktive søket gir `409`, også ved samtidige opprettelser. |
| Komponenttest, bruker-api | Det første søket blir standard. Erstatting endrer ikke standardvalget. Bytte av standard fjerner det gamle merket og fornyer bare det valgte søkets dato. Samtidige standardvalg gir høyst ett standardsøk. Sletting av standardsøket gjør det sist brukte søket til standard, og sletting av et annet søk beholder standardvalget. Et utløpt standardsøk erstattes når listen hentes, også etter opprydningsjobben. |
| Komponenttest, bruker-api | En veileder får `404` på `PUT`, `DELETE`, `.../standard` og `.../bruk` mot en annen veileders søk, og listen viser bare egne søk. Ukjent domene gir `404`. Manglende rolle gir `403`. Navn på 25 tegn godtas, mens 26 tegn etter trimming gir `400` ved både opprettelse og erstatting. For lang input, ugyldige filterparametere eller verdier og ugyldig JSON gir `400` uten innholdet i svaret. |
| Komponenttest, bruker-api | Lagring, bruk og standardvalg setter `sistAktiv`. `GET` endrer den ikke. `.../bruk` skriver bare når datoen er eldre enn i dag og returnerer gjeldende søk. Jobben sletter utløpte søk. Før jobben har kjørt, skjuler `GET` slike søk, kall med id gir `404`, og de teller ikke mot grensen på 10. |
| Enhetstest, bruker-api | Kalenderregler for varsel etter fem måneder og utløp fra seks måneder. Test månedsslutt, skuddår og dato i `Europe/Oslo`. |
| Enhetstest, frontend | Hvilke parametre som lagres og fjernes, og om `.../bruk` kalles. Utløpt cache eller `404` aktiverer ikke gamle filtre eller søkeord. Svaret ved bruk oppdaterer cachen. |
| Playwright med MSW | Valg i menyen setter filtre og chips. Fritekst og navn står ikke i URL-en. Lagre som nytt og erstatt, også med like navn. Lagre-dialogen har ikke standardvalg. «Lagre som nytt» er deaktivert ved 10 søk. Sletting med bekreftelse. Varsel etter fem måneder forsvinner ved aktivitet. Siden åpnes med standardsøket og viser «Valgt søk». Søkeparametere i URL-en går foran standardsøket. Valgt søk og søkeord beholdes ved refresh, men ikke etter endrede filtre. Uten lagrede søk åpnes standardvisningen. Sletting av standardsøket flytter merket «Standard». «Fjern alle filtre» gir standardvisningen. Feil i bruker-api blokkerer ikke vanlig søk. |

## Personvern og sikkerhet

- Tabellene har Nav-ident om ansatte og filtre uten opplysninger om jobbsøkere. Navn og fritekst kan inneholde personopplysninger. Vi fjerner gyldige fødselsnumre fra begge og sletter hele søket etter seks måneder uten aktivitet. Andre personopplysninger kan bli liggende så lenge søket holdes aktivt. Dialogen ber derfor veilederen om å ikke skrive personopplysninger i navnet.
- Datoer i stedet for tidspunkt gir mindre informasjon om når den ansatte jobber.
- Søkeidenten er en tilfeldig UUID. Eierskapet sjekkes likevel i hver spørring, og et fremmed søk gir samme `404` som et søk som ikke finnes.
- Bruker-api logger meldingen fra `IllegalArgumentException` og returnerer meldingen fra alle unntak. Valideringsfeil må derfor ha faste tekster uten innhold fra body. Jackson legger verdier inn i feilmeldinger ved ugyldig JSON, så controlleren må fange parsefeil og kaste en fast feil uten årsakens melding.
- Ingen logging av `navn`, `filter` eller `fritekst`, heller ikke på debug-nivå.

🔴 Rød sone: fødselsnummerfilteret, validering av filterparametere, Nav-ident fra tokenet, eierskap, transaksjoner for antallsgrense og standardvalg, feilhåndtering uten innhold i logg og svar, og utløpsreglene i API, cache og opprydningsjobb. Utvikleren bør skrive og forstå disse selv. Resten er grønn sone.

## Observerbarhet

- Tellere i bruker-api: opprettede, erstattede og slettede søk, søk der navn eller fritekst ble filtrert, og rader jobben slettet. Alle med `domene` som label.
- Opprydningsjobben har feilteller og tidspunkt for siste vellykkede kjøring, med varsling ved uteblitt kjøring. Ingen målinger har Nav-ident, søkeident, navn eller søkeord som label.
- Umami i frontend: søk brukt fra menyen, fra «Administrer søk» og som standardsøk når siden åpnes, lagret som nytt, erstattet og slettet, per domene.

## Åpne spørsmål

1. Hvordan når Flyway-migreringen i bruker-api dataene i stilling-api sin database? Flyway kjører mot én database, og de to applikasjonene har hver sin. Avklares når leveranse 2 starter.
2. Tekster og plassering avklares med designer. Skissene er et utgangspunkt.
