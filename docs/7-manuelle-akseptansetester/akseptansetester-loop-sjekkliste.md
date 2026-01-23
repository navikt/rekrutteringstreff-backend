# Manuelle akseptansetester

Testscenarier for domeneeksperter før pilot og prodsetting.

**Tester:** _______________  
**Dato:** _______________  
**Miljø:** Dev

## Testmiljø

- rekrutteringsbistand: rekrutteringsbistand.intern.dev.nav.no
- rekrutteringstreff-bruker: rekrutteringstreff.ekstern.dev.nav.no
- Aktivitetsplan (veileder): veilarbpersonflate.intern.dev.nav.no
- Aktivitetsplan (bruker): aktivitetsplan.ekstern.dev.nav.no
- MinSide: min-side.dev.nav.no

---

## 1. Opprette rekrutteringstreff

**Hvor:** rekrutteringsbistand  
**Hva skjer:** Treffet lagres i databasen. Ingen varsler eller aktivitetskort - det skjer først ved invitasjon.

- [ ] **1.1** Markedskontakt - Opprett treff med påkrevde felter  
  ✓ Forventet: Treff opprettes, vises i "Mine treff"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **1.2** Markedskontakt - Opprett treff, fyll ut alle felter  
  ✓ Forventet: Alle felter lagres og vises korrekt  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **1.3** Markedskontakt - Opprett treff med ugyldig data  
  ✓ Forventet: Valideringsfeil vises, treff opprettes ikke  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **1.4** Markedskontakt - Sjekk at andre ikke ser upublisert treff  
  ✓ Forventet: Treffet vises kun for oppretter  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Autolagring (kladd-modus)

- [ ] **1.5** Markedskontakt - Skriv tittel i kladd  
  ✓ Forventet: Tittel lagres automatisk, hake i sidepanel  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **1.6** Markedskontakt - Lukk og åpne treffet på nytt  
  ✓ Forventet: Tittel er bevart  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **1.7** Markedskontakt - Skriv innlegg i kladd  
  ✓ Forventet: Innlegg lagres automatisk, hake i sidepanel  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **1.8** Markedskontakt - Lukk og åpne treffet på nytt  
  ✓ Forventet: Innlegg er bevart  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **1.9** Markedskontakt - Endre flere felt, lukk nettleser  
  ✓ Forventet: Alle felt er bevart ved neste innlogging  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **1.10** Markedskontakt - Se sidepanel etter lagring  
  ✓ Forventet: Felter vises med avhukning i sidepanel  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 2. Legge til arbeidsgiver

**Hvor:** rekrutteringsbistand  
**Hva skjer:** Arbeidsgiver kobles til treffet. Arbeidsgiverne blir synlige for jobbsøkere.

### I kladd-modus (før publisering)

- [ ] **2.1** Markedskontakt - Legg til arbeidsgiver via søk  
  ✓ Forventet: Arbeidsgiver vises i listen på treffet  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **2.2** Markedskontakt - Legg til flere arbeidsgivere  
  ✓ Forventet: Alle vises i listen  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **2.3** Markedskontakt - Fjern arbeidsgiver  
  ✓ Forventet: Arbeidsgiver fjernes fra listen  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Etter publisering

- [ ] **2.4** Markedskontakt - Åpne "Arbeidsgivere"-fanen  
  ✓ Forventet: Ser liste over arbeidsgivere på treffet  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **2.5** Markedskontakt - Legg til ny arbeidsgiver  
  ✓ Forventet: Arbeidsgiver legges til og vises i listen  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **2.6** Markedskontakt - Fjern arbeidsgiver etter publisering  
  ✓ Forventet: Arbeidsgiver fjernes fra listen  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **2.7** Jobbsøker - Sjekk arbeidsgiverliste  
  ✓ Forventet: Ser oppdatert liste i rekrutteringstreff-bruker  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 3. Publisere rekrutteringstreff

**Hvor:** rekrutteringsbistand  
**Hva skjer:** Treffet blir synlig for alle. Fortsatt ingen varsler - det skjer først ved invitasjon.

- [ ] **3.1** Markedskontakt - Publiser treff  
  ✓ Forventet: Status endres til "Publisert"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **3.2** Veileder - Søk etter publisert treff  
  ✓ Forventet: Treffet dukker opp i søkeresultater  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **3.3** Veileder - Åpne publisert treff  
  ✓ Forventet: Kan se treffdetaljer og jobbsøkerliste  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 4. Legge til jobbsøker

**Hvor:** rekrutteringsbistand (Jobbsøker-fanen på treffet)  
**Hva skjer:** Jobbsøkeren legges til med status "Lagt til". Synlighetssjekk kjører i bakgrunnen.

- [ ] **4.1** Veileder - Legg til synlig jobbsøker  
  ✓ Forventet: Jobbsøker vises i listen med status "Lagt til"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.2** Markedskontakt - Legg til synlig jobbsøker  
  ✓ Forventet: Jobbsøker vises i listen med status "Lagt til"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.3** Legg til flere jobbsøkere  
  ✓ Forventet: Alle vises i listen  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.4** Fjern jobbsøker fra treff  
  ✓ Forventet: Jobbsøker fjernes fra listen  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.5** Legg til jobbsøker som tidligere er slettet  
  ✓ Forventet: Jobbsøker legges til igjen, "Slettet"-teller synker  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Synlighet - CV og jobbprofil

> **Forutsetning:** Testpersonen må være synlig og lagt til på treffet først.

- [ ] **4.5** Person med aktiv CV  
  ✓ Forventet: Synlig ✅  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.6** Person uten CV  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.7** Person med slettet CV (meldingstype SLETT)  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.8** Person med CV men uten jobbprofil  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.9** Person med jobbprofil  
  ✓ Forventet: Synlig ✅  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Synlighet - Arbeidssøkerregister

- [ ] **4.11** Person registrert i arbeidssøkerregisteret  
  ✓ Forventet: Synlig ✅  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.12** Person IKKE i arbeidssøkerregisteret  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.13** Person med avsluttet arbeidssøkerperiode  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Synlighet - Oppfølging

- [ ] **4.14** Person under aktiv oppfølging  
  ✓ Forventet: Synlig ✅  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.15** Person med avsluttet oppfølgingsperiode  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.16** Person uten oppfølgingsperiode eller oppfølgingsinformasjon  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Synlighet - Adressebeskyttelse

- [ ] **4.17** Person med UGRADERT  
  ✓ Forventet: Synlig ✅  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.18** Person med FORTROLIG (kode 7)  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.19** Person med STRENGT_FORTROLIG (kode 6)  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.20** Person med STRENGT_FORTROLIG_UTLAND (§19)  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.21** Fjern adressebeskyttelse (sett til UGRADERT)  
  ✓ Forventet: Person dukker opp igjen ✅  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Synlighet - KVP

- [ ] **4.22** Person uten aktiv KVP  
  ✓ Forventet: Synlig ✅  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.23** Person med aktiv KVP (startet)  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Synlighet - Andre kriterier

- [ ] **4.25** Person som ikke er død  
  ✓ Forventet: Synlig ✅  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.26** Person markert som død  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.27** Fjern dødmarkering  
  ✓ Forventet: Person dukker opp igjen ✅  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.28** Person som ikke er sperret ansatt  
  ✓ Forventet: Synlig ✅  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **4.29** Person markert som sperret ansatt  
  ✓ Forventet: Ikke synlig ❌  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 5. Invitere jobbsøker

**Hvor:** Markedskontakt i rekrutteringsbistand, Jobbsøker mottar SMS/e-post  
**Hva skjer:** Status endres til "Invitert", varsel sendes, aktivitetskort opprettes.

- [ ] **5.1** Markedskontakt - Inviter jobbsøker  
  ✓ Forventet: Status endres til "Invitert"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **5.2** Markedskontakt - Sjekk varselstatus (~1 min)  
  ✓ Forventet: Varselstatus viser "Sendt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **5.3** Jobbsøker - Motta SMS  
  ✓ Forventet: SMS med lenke til rekrutteringstreff-bruker  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **5.4** Jobbsøker - Klikk lenke i SMS  
  ✓ Forventet: Kommer til rekrutteringstreff-bruker, ser treffdetaljer  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **5.5** Jobbsøker - Sjekk aktivitetskort  
  ✓ Forventet: Aktivitetskort finnes med status "Planlagt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **5.6** Jobbsøker - Klikk lenke i aktivitetskort  
  ✓ Forventet: Kommer til rekrutteringstreff-bruker  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **5.7** Markedskontakt - Se aktivitetskort for jobbsøker  
  ✓ Forventet: Ser samme kort med status "Planlagt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **5.8** Veileder - Prøv å invitere jobbsøker  
  ✓ Forventet: Inviter-knapp er IKKE synlig for veileder  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Varselkanaler

- [ ] **5.9** Inviter jobbsøker med mobilnr i KRR  
  ✓ Forventet: SMS sendes, varselstatus = "Sendt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **5.10** Inviter jobbsøker med kun e-post i KRR  
  ✓ Forventet: E-post sendes, varselstatus = "Sendt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **5.11** Inviter jobbsøker uten kontaktinfo i KRR  
  ✓ Forventet: Varsel på MinSide, status = "Varsel MinSide"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Feilsituasjoner

- [ ] **5.12** Trykk to ganger på inviter-knapp  
  ✓ Forventet: Kun én invitasjon registreres  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **5.13** Inviter jobbsøker som blir ikke-synlig  
  ✓ Forventet: Jobbsøker forsvinner, varsel sendes ikke  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 6. Jobbsøker svarer på invitasjon

**Hvor:** Jobbsøker i rekrutteringstreff-bruker, Markedskontakt ser status i rekrutteringsbistand  
**Hva skjer:** Ved svar oppdateres status. Aktivitetskort: Ja → "Gjennomføres", Nei → "Avbrutt"

- [ ] **6.1** Jobbsøker - Svar "Ja"  
  ✓ Forventet: Bekreftelse vises, svarknapper erstattes med status  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **6.2** Markedskontakt - Sjekk status etter ja-svar  
  ✓ Forventet: Status viser "Påmeldt" / "Svart ja"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **6.3** Jobbsøker - Sjekk aktivitetskort etter ja  
  ✓ Forventet: Status er "Gjennomføres"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **6.4** Markedskontakt - Sjekk aktivitetskort etter ja  
  ✓ Forventet: Ser samme status "Gjennomføres"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **6.5** Jobbsøker - Svar "Nei"  
  ✓ Forventet: Bekreftelse på avmelding vises  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **6.6** Markedskontakt - Sjekk status etter nei-svar  
  ✓ Forventet: Status viser "Avmeldt" / "Svart nei"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **6.7** Jobbsøker - Sjekk aktivitetskort etter nei  
  ✓ Forventet: Status er "Avbrutt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **6.8** Jobbsøker - Endre svar fra ja til nei  
  ✓ Forventet: Nytt svar registreres, aktivitetskort → "Avbrutt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **6.9** Jobbsøker - Endre svar fra nei til ja  
  ✓ Forventet: Nytt svar registreres, aktivitetskort → "Gjennomføres"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Tilstander i rekrutteringstreff-bruker

- [ ] **6.10** Åpne invitasjon før svarfrist  
  ✓ Forventet: Ser svarknapper, svarfrist, treffinfo  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **6.11** Åpne etter svarfrist utløpt  
  ✓ Forventet: Ser "Svarfrist er utløpt", ingen svarknapper  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **6.12** Åpne treff man ikke er invitert til  
  ✓ Forventet: Ser info om begrenset plass, tips om å kontakte veileder  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Feilsituasjoner

- [ ] **6.13** Jobbsøker - Åpne ugyldig treff-ID  
  ✓ Forventet: Vennlig feilmelding  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **6.14** Jobbsøker - Trykk to ganger på svar-knapp  
  ✓ Forventet: Kun ett svar registreres  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 7. Endre publisert treff

**Hvor:** Markedskontakt i rekrutteringsbistand  
**Hva skjer:** Ved lagring velger markedskontakt om varsel skal sendes. Svart nei får IKKE varsel.

### Varseldialog og feltvalg

- [ ] **7.1** Markedskontakt - Endre felt og lagre  
  ✓ Forventet: Dialog åpnes med spørsmål om varsel  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **7.2** Markedskontakt - Velg "Ikke send varsel"  
  ✓ Forventet: Endring lagres, ingen varsel sendes  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **7.3** Markedskontakt - Velg "Send varsel", alle felt på  
  ✓ Forventet: Varsel sendes med alle endrede felt nevnt  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **7.4** Markedskontakt - "Send varsel", kun tidspunkt på  
  ✓ Forventet: Varsel nevner kun tidspunkt, ikke andre endringer  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **7.5** Markedskontakt - Velg "Send varsel", kun sted på  
  ✓ Forventet: Varsel nevner kun sted  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **7.6** Markedskontakt - "Send varsel", ingen felt valgt  
  ✓ Forventet: Varsel sendes med generell melding om endring  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Mottakere og varselinnhold

- [ ] **7.7** Jobbsøker (invitert, ikke svart) - Motta endringsvarsel  
  ✓ Forventet: SMS/e-post med info om valgte felt  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **7.8** Jobbsøker (svart ja) - Motta endringsvarsel  
  ✓ Forventet: SMS/e-post med info om valgte felt  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **7.9** Jobbsøker (svart nei) - Sjekk varsel  
  ✓ Forventet: Skal IKKE motta varsel  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **7.10** Jobbsøker - Sjekk SMS-tekst  
  ✓ Forventet: Teksten inneholder de valgte feltnavnene  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Oppdatering i systemer

- [ ] **7.11** Jobbsøker - Åpne treff etter endring  
  ✓ Forventet: Ser oppdaterte detaljer i rekrutteringstreff-bruker  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **7.12** Jobbsøker - Sjekk aktivitetskort etter endring  
  ✓ Forventet: Aktivitetskort har oppdaterte detaljer  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **7.13** Veileder - Sjekk aktivitetskort etter endring  
  ✓ Forventet: Ser oppdaterte detaljer  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 8. Avlyse treff

**Hvor:** Markedskontakt i rekrutteringsbistand  
**Hva skjer:** Treffstatus endres til "Avlyst". Varsel sendes KUN til de som svarte ja.

- [ ] **8.1** Markedskontakt - Avlys treff  
  ✓ Forventet: Status endres til "Avlyst"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **8.2** Jobbsøker (svart ja) - Motta avlysningsvarsel  
  ✓ Forventet: SMS/e-post om at treffet er avlyst  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **8.3** Jobbsøker (svart ja) - Sjekk aktivitetskort  
  ✓ Forventet: Status er "Avbrutt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **8.4** Jobbsøker (invitert) - Sjekk varsel  
  ✓ Forventet: Skal IKKE motta varsel  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **8.5** Jobbsøker (invitert) - Sjekk aktivitetskort  
  ✓ Forventet: Status er "Avbrutt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **8.6** Jobbsøker (svart nei) - Sjekk varsel  
  ✓ Forventet: Skal IKKE motta varsel  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **8.7** Jobbsøker (svart nei) - Sjekk aktivitetskort  
  ✓ Forventet: Status er "Avbrutt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **8.8** Jobbsøker - Åpne avlyst treff  
  ✓ Forventet: Ser tydelig avlysningsmelding  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **8.9** Markedskontakt (eier) - Se jobbsøkerliste etter avlysning  
  ✓ Forventet: Alle jobbsøkere vises fortsatt med sine statuser  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 9. Treff gjennomføres og avsluttes

**Hva skjer:** Når sluttidspunkt passerer: Svart ja → "Fullført", andre → "Avbrutt"

- [ ] **9.1** Jobbsøker - Åpne treff som pågår  
  ✓ Forventet: Ser "Treffet er i gang"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **9.2** Jobbsøker - Åpne treff som er passert  
  ✓ Forventet: Ser "Treffet er over"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **9.3** Jobbsøker (svart ja) - Sjekk aktivitetskort  
  ✓ Forventet: Status er "Fullført"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **9.4** Jobbsøker (invitert, ikke svart) - Sjekk aktivitetskort  
  ✓ Forventet: Status er "Avbrutt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **9.5** Jobbsøker (svart nei) - Sjekk aktivitetskort  
  ✓ Forventet: Status er "Avbrutt"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **9.6** Markedskontakt - Sjekk aktivitetskort (svart ja)  
  ✓ Forventet: Status er "Fullført"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **9.7** Markedskontakt (eier) - Se jobbsøkerliste etter treff  
  ✓ Forventet: Alle jobbsøkere vises med sine statuser  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 10. Innlegg på treff

**Hva skjer:** Innlegget vises under "Siste aktivitet". Ingen varsel sendes for innlegg.

- [ ] **10.1** Markedskontakt - Legg til innlegg  
  ✓ Forventet: Innlegg vises på treffet i rekrutteringsbistand  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **10.2** Jobbsøker - Se innlegg  
  ✓ Forventet: Innlegg vises under "Siste aktivitet"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **10.3** Markedskontakt - Rediger eksisterende innlegg  
  ✓ Forventet: Samme innlegg oppdateres, ikke nytt  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **10.4** Markedskontakt - Sjekk at det ikke kan legges til flere  
  ✓ Forventet: Ingen knapp for å legge til nytt innlegg  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **10.5** Markedskontakt - Tøm innlegget  
  ✓ Forventet: Innlegget fjernes fra visningen  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 11. KI-moderering

**Hva skjer:** Tekst valideres av KI. Ved advarsel må bruker trykke "Lagre likevel".

### KI-validering av tittel

- [ ] **11.1** Markedskontakt - Skriv nøytral tittel (kladd)  
  ✓ Forventet: Ingen advarsel, tekst godkjennes  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.2** Markedskontakt - Skriv diskriminerende tittel (kladd)  
  ✓ Forventet: Advarsel vises, "Lagre likevel"-knapp  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.3** Markedskontakt - Endre tittel (etter publisering)  
  ✓ Forventet: KI validerer ved "Lagre" i dialog  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.4** Markedskontakt - Endre til diskriminerende tittel (publisert)  
  ✓ Forventet: Advarsel vises, kan ikke lagre uten knapp  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### KI-validering av innlegg

- [ ] **11.5** Markedskontakt - Skriv nøytralt innlegg (kladd)  
  ✓ Forventet: Ingen advarsel, tekst godkjennes  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.6** Markedskontakt - Skriv diskriminerende innlegg (kladd)  
  ✓ Forventet: Advarsel vises, "Lagre likevel"-knapp  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.7** Markedskontakt - Endre innlegg (etter publisering)  
  ✓ Forventet: KI validerer ved "Lagre" i dialog  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.8** Markedskontakt - Endre til diskriminerende innlegg (publisert)  
  ✓ Forventet: Advarsel vises, kan ikke lagre uten knapp  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### "Lagre likevel"-funksjonalitet

- [ ] **11.9** Markedskontakt - Advarsel vist, IKKE trykk "Lagre likevel"  
  ✓ Forventet: Kan ikke publisere/lagre treffet  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.10** Markedskontakt - Advarsel vist, trykk "Lagre likevel"  
  ✓ Forventet: Teksten lagres, kan fortsette  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.11** Markedskontakt - Prøv å publisere uten "Lagre likevel"  
  ✓ Forventet: Publisering blokkert inntil valg er tatt  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### KI-logg (krever utviklertilgang)

- [ ] **11.12** Utvikler - Åpne KI-logg  
  ✓ Forventet: Ser liste over alle KI-valideringer  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.13** Utvikler - Sjekk logg for kladd-treff  
  ✓ Forventet: lagret=true for tekst som ble autolagret  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.14** Utvikler - Sjekk logg etter publisert endring  
  ✓ Forventet: lagret=true kun når bruker trykket "Lagre" i dialog  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.15** Utvikler - Sjekk tekst som ble forkastet  
  ✓ Forventet: lagret=false for tekst som ble endret før lagring  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.16** Utvikler - Legg inn manuell vurdering  
  ✓ Forventet: Kan registrere egen vurdering for kvalitetskontroll  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **11.17** Utvikler - Filtrer på avvik  
  ✓ Forventet: Kan finne tilfeller der KI vurderte feil  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 12. Søke etter rekrutteringstreff

- [ ] **12.1** Veileder - Åpne rekrutteringstreff-oversikten  
  ✓ Forventet: Ser liste over publiserte treff  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **12.2** Markedskontakt - Åpne oversikten  
  ✓ Forventet: Ser publiserte treff + egne upubliserte treff  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **12.3** Veileder - Klikk på et treff  
  ✓ Forventet: Åpner treffet i lesemodus  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **12.4** Markedskontakt (ikke eier) - Klikk på andres treff  
  ✓ Forventet: Åpner treffet i lesemodus  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 13. Finn jobbsøkere (kandidatsøk)

- [ ] **13.1** Markedskontakt (eier) - Klikk "Finn jobbsøkere"  
  ✓ Forventet: Åpner kandidatsøk med filter  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **13.2** Markedskontakt - Søk med kompetansefilter  
  ✓ Forventet: Kandidater som matcher vises  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **13.3** Markedskontakt - Legg til kandidat fra søk  
  ✓ Forventet: Kandidat legges til på treffet  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **13.4** Markedskontakt - Legg til flere kandidater  
  ✓ Forventet: Alle legges til på treffet  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **13.5** Veileder (ikke eier) - Klikk "Finn jobbsøkere"  
  ✓ Forventet: Åpner kandidatsøk  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **13.6** Veileder - Legg til kandidat  
  ✓ Forventet: Kandidat legges til på treffet  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 14. Hendelseslogg

> **Tips:** Test på et treff med mange hendelser.

- [ ] **14.1** Markedskontakt (eier) - Åpne Hendelser-fanen  
  ✓ Forventet: Ser liste over alle hendelser på treffet  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **14.2** Sjekk at opprettelse vises  
  ✓ Forventet: "Opprettet" med tidspunkt og utført av  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **14.3** Sjekk at publisering vises  
  ✓ Forventet: "Publisert" med tidspunkt  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **14.4** Sjekk at jobbsøker-hendelser vises  
  ✓ Forventet: "Lagt til", "Invitert", "Svart ja/nei" etc.  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **14.5** Sjekk at arbeidsgiver-hendelser vises  
  ✓ Forventet: "Lagt til", "Fjernet" etc.  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **14.6** Sjekk at endringshendelser vises  
  ✓ Forventet: "Endret" med info om hva som ble endret  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **14.7** Sjekk kronologisk rekkefølge  
  ✓ Forventet: Nyeste hendelser øverst eller tydelig sortert  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **14.8** Veileder (ikke eier) - Prøv Hendelser-fanen  
  ✓ Forventet: Fanen er ikke tilgjengelig  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## 15. Tilgangsstyring og roller

### Veileder (jobbsøkerrettet)

- [ ] **15.1** Veileder - Åpne publisert treff  
  ✓ Forventet: Ser treffdetaljer i lesemodus  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.2** Veileder - Prøv å redigere treffdetaljer  
  ✓ Forventet: Ingen redigeringsknapper synlige  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.3** Veileder - Se jobbsøkerlisten  
  ✓ Forventet: Ser IKKE andre veilederes jobbsøkere  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.4** Veileder - Legg til jobbsøker  
  ✓ Forventet: Kan legge til jobbsøker på treffet  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.5** Veileder - Se egen jobbsøker  
  ✓ Forventet: Ser jobbsøkeren man selv la til  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.6** Veileder - Prøv å invitere jobbsøker  
  ✓ Forventet: Inviter-knapp er IKKE synlig for veileder  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.7** Veileder - Prøv å se Hendelser-fanen  
  ✓ Forventet: Fanen er ikke synlig/tilgjengelig  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.8** Veileder - Prøv å opprette nytt treff  
  ✓ Forventet: Knapp for opprett treff ikke synlig  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Markedskontakt (arbeidsgiverrettet) - ikke eier

- [ ] **15.9** Markedskontakt - Åpne andres publiserte treff  
  ✓ Forventet: Ser treffdetaljer i lesemodus  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.10** Markedskontakt - Prøv å redigere andres treff  
  ✓ Forventet: Ingen redigeringsknapper synlige  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.11** Markedskontakt - Se jobbsøkerlisten på andres treff  
  ✓ Forventet: Ser IKKE andres jobbsøkere  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.12** Markedskontakt - Legg til jobbsøker på andres treff  
  ✓ Forventet: Kan legge til jobbsøker  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.13** Markedskontakt - Opprette eget treff  
  ✓ Forventet: Knapp synlig, kan opprette  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.14** Markedskontakt - Invitere jobbsøker på andres treff  
  ✓ Forventet: Kan invitere jobbsøker man la til  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Markedskontakt (arbeidsgiverrettet) - eier

- [ ] **15.15** Markedskontakt (eier) - Åpne eget treff  
  ✓ Forventet: Ser alle faner inkl. Hendelser  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.16** Markedskontakt (eier) - Redigere treffdetaljer  
  ✓ Forventet: Kan redigere tittel, tid, sted, etc.  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.17** Markedskontakt (eier) - Se alle jobbsøkere  
  ✓ Forventet: Ser alle jobbsøkere på treffet  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.18** Markedskontakt (eier) - Invitere alle jobbsøkere  
  ✓ Forventet: Kan invitere alle, ikke bare egne  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.19** Markedskontakt (eier) - Publisere treff  
  ✓ Forventet: Publiser-knapp synlig og fungerer  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.20** Markedskontakt (eier) - Avlyse treff  
  ✓ Forventet: Avlys-knapp synlig og fungerer  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.21** Markedskontakt (eier) - Legge til/fjerne arbeidsgivere  
  ✓ Forventet: Kan administrere arbeidsgiverlisten  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Utvikler

- [ ] **15.22** Utvikler - Åpne KI-logg  
  ✓ Forventet: Ser liste over alle KI-valideringer  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.23** Utvikler - Se alle treff  
  ✓ Forventet: Kan se alle treff inkludert kladder  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.24** Utvikler - Tilgang uten pilotkontor  
  ✓ Forventet: Får tilgang uansett kontor  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.25** Utvikler - Se andres hendelseslogg  
  ✓ Forventet: Kan se hendelser på alle treff  
  Status: ⚪ / ✅ / ❌  
  Notat: 

### Pilotkontor-tilgang

- [ ] **15.26** Veileder på pilotkontor - Åpne rekrutteringstreff  
  ✓ Forventet: Får tilgang til rekrutteringstreff  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.27** Veileder på ikke-pilotkontor - Åpne rekrutteringstreff  
  ✓ Forventet: Ser melding "Du har ikke tilgang til rekrutteringstreff"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.28** Markedskontakt på pilotkontor - Åpne rekrutteringstreff  
  ✓ Forventet: Får tilgang til rekrutteringstreff  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.29** Markedskontakt på ikke-pilotkontor - Åpne rekrutteringstreff  
  ✓ Forventet: Ser melding "Du har ikke tilgang til rekrutteringstreff"  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.30** Markedskontakt på pilotkontor - Opprette treff  
  ✓ Forventet: Kan opprette treff  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.31** Markedskontakt på ikke-pilotkontor - Opprette treff  
  ✓ Forventet: Kan IKKE opprette treff  
  Status: ⚪ / ✅ / ❌  
  Notat: 

- [ ] **15.32** Utvikler - Åpne uansett kontor  
  ✓ Forventet: Får alltid tilgang  
  Status: ⚪ / ✅ / ❌  
  Notat: 

---

## Oppsummering

Fyll ut etter testing:

- 1. Opprette rekrutteringstreff (10 tester): ___ OK, ___ Feilet
- 2. Legge til arbeidsgiver (7 tester): ___ OK, ___ Feilet
- 3. Publisere rekrutteringstreff (3 tester): ___ OK, ___ Feilet
- 4. Legge til jobbsøker (25 tester): ___ OK, ___ Feilet
- 5. Invitere jobbsøker (13 tester): ___ OK, ___ Feilet
- 6. Jobbsøker svarer (14 tester): ___ OK, ___ Feilet
- 7. Endre publisert treff (13 tester): ___ OK, ___ Feilet
- 8. Avlyse treff (9 tester): ___ OK, ___ Feilet
- 9. Treff avsluttes (7 tester): ___ OK, ___ Feilet
- 10. Innlegg (5 tester): ___ OK, ___ Feilet
- 11. KI-moderering (17 tester): ___ OK, ___ Feilet
- 12. Søke etter treff (4 tester): ___ OK, ___ Feilet
- 13. Kandidatsøk (6 tester): ___ OK, ___ Feilet
- 14. Hendelseslogg (8 tester): ___ OK, ___ Feilet
- 15. Tilgangsstyring (32 tester): ___ OK, ___ Feilet

**TOTALT: 173 tester**
