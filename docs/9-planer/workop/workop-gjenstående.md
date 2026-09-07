- for workop ønsker vi å bruke 'workop' i steden for 'treff' i sms og email. Se maler i
  rekrutteringsbistand-kandidatvarsel-api. Vi må få rekrutteringstreff-api til å trigge riktig melding der.
  Enten ny mal, eller en variabel med default treff i malene? gjelder både ny sms og endring.

- Aktivitetskort. Trenger å oversende en workopkategori i self service apiet som er brukt i rekrutteringsbistand-aktivitetsort.
  Må også der få sakgt fra til rekrutteirngsbistand-aktivitetskort at vi har et workop.
  Tenker en label med 'workop' inne i aktivitetskortet.
  Si fra om du trenger dokumentasjon.

- statussteget i treffgjennomføring fanen, ikke rød farge for det arbeidsgivere sier, finn en mer nøytral farge som ikke symboliserer fare, men bra den er annerledes enn den for jobbsøers utalelser.

- infobokser. ønsker wt par workop spesifikke infobikser kun for workop. Om det er ulikt 5 arbeidsgivere, for eksempel i registreringen før de er lagt til, eller i arbeidsgiveretabben, en infoboks i nærheten av der de er legges inn, som sier at det vanlige er 5 arbeidsgivere. Tilsvarende for jobbsøkere i jobbsøkerfanen. Der det er infoboks om at det er anbefalt 25 jobbsøkere. Den kan vises uansett, siden det er uklart om det 25 i listen eller 25 som har status møtt opp som vi skal fokusere på. Altså kun for workop. Infoboksen kan si at det er anbefalt at det er planlagt for 25 personer i møtene.

**_ ros-workop.md _**

- WO-01 kan du fikse den.
- WO-03 Den med interesse og oppfølging, sikre at det ikke skjer lagring der i prod, kan sikres backend, med prodsperre. Vi vil fortsatt gjerne kunne se på dette i lokal kjøring når det ikke er workop. Men skal ikke vises i dev eller prod.
- WO-04 pass på at man bare ser egne workop. frontend og backend.
- WO-05 Fiks backend filtrering for workop.
- WO-09 et tiltak er at påminnelser sendes kun via aktivitetsplan dialogen. Det er uansett kontaktperson telefonnummer i den, så den passer bedre der.
- se tidligere kommentar, vi wil sende en kategori dit, og ønsker implementasjon av det.
- WO-13 et tiltak er at vi ikke tilbyr marker alle knapp.
- WO-14 Mangler vi en slettenhendelse? Eller kan man dedusere seg frem til det ved å se på fjernet oppmøte? Burde vi ha slett notat hendelser? VI må uansett slette notat før vi ksan slette oppmøte nå med sperrene?
- WO-17 Du kan svare på om dette bare gjelder visning i første steg i treffgjennomføring, i så fall er dette lite problem, om de kan gjennomføre interesser og statuser og romfordeling for flere. Men ja workop får aldri 100 detakere på workop, så er ikke noe stort problem foreløpig.
- WO-18 Retry-knapp høres ikke bra ut, tenker at feilmedling + at krysset er uendret, og må krysses på nytt er best her.

- "Formøtet er utenfor omfanget i første versjon"
  Stemmer ikke helt, vi legger inn informasjon om formøte i invitasjonsmeldingen til brukerne. Vi kan senere legge ut dette som mer strukturert informasjon til svarsiden for brukerne, spom de kommer til fra aktivtetsplan eller sms.
  - "Oppsummeringssteget viser aggregerte nøkkeltall, ikke individoversikt, og har ingen eksportfunksjon" Greit så lenge vi har steg 5 som har individinformasjon. men kanskje tittelen på steg 5 er litt uklar om den brukes for å lese infomrasjon også?
