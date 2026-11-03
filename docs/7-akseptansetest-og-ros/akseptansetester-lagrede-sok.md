# Manuelle akseptansetester – Lagrede søk i rekrutteringstreff

Testene gjelder lagrede søk i treffsøket i rekrutteringsbistand: lagre, erstatte, endre navn, slette, gjøre til standard og bruke søk. De dekker også når standardsøket brukes. Som i stillingssøket brukes det bare fra menylenken, ikke når siden åpnes på andre måter eller med tilbakeknappen i nettleseren.

> **Målgruppe:** Domeneeksperter og utviklere. Alt testes i nettleseren. Tester merket «Utvikler» krever endringer i databasen og kan hoppes over.

> **Status:** Lagrede søk er på plass i dev for rekrutteringstreff. Stillingssøket og etterregistreringen bruker fortsatt det gamle standardsøket, og testes bare for å se at de er uendret (seksjon 11).

Bakgrunn og regler står i [planen for lagrede søk](../9-planer/lagrede-sok.md).

## Testmiljø

| System               | URL (dev)                                                 | Brukes av                          |
| -------------------- | --------------------------------------------------------- | ---------------------------------- |
| rekrutteringsbistand | rekrutteringsbistand.intern.dev.nav.no/rekrutteringstreff | Markedskontakt, Veileder, Utvikler |

> **Begreper:**
>
> - **Lagret søk** = filtre, sortering og søkeord som veilederen har lagret med et navn. Hver veileder kan ha opptil 10.
> - **Standardsøk** = det lagrede søket som er merket «Standard». Det brukes når du trykker menylenken.
> - **Vanlig søk** = treffsøket uten egne filtre: «Publisert», «Alle treff» og «Sist oppdaterte», uten søkeord. Vanlig søk er standard til veilederen gjør et lagret søk til standard.
> - **Menylenken** = «Rekrutteringstreff» i sidemenyen til venstre.
> - **Menyen** = knappen «Lagrede søk» under søkefeltet i treffsøket. Disketten ved siden av er knappen «Lagre søk».
> - **Statuslinjen** = teksten «Lagret søk: _navn_» under menyen. Den vises så lenge filtrene og søkeordene er like et lagret søk.
> - **Søkeord** = ord du skriver i «Søk i treff». De vises som chips over treffene.

## Forutsetninger før testing

1. Du er logget inn i dev som markedskontakt (arbeidsgiverrettet) med tilgang til rekrutteringstreff.
2. Det finnes publiserte treff i dev, helst i flere fylker.
3. Du starter uten lagrede søk. Har du søk fra før, sletter du dem i «Administrer søk».
4. Ikke skriv opplysninger om ekte personer i navn eller søkeord. Bruk ord som «kokk» og «lager». Når testen ber om et fødselsnummer, bruker du `12345678901`.

**Testdata.** Flere tester bruker disse tre søkene. Du lager dem i seksjon 1.

| Navn               | Filtre og søkeord                                    |
| ------------------ | ---------------------------------------------------- |
| «Oslo»             | Fylke: Oslo                                          |
| «Eldste fullførte» | Status: Fullført (ikke Publisert), sortering: Eldste |
| «Kokk»             | Søkeord: kokk, sortering: Nyeste                     |

---

## 1. Lagre nytt søk

**Hvor:** Treffsøket → disketten «Lagre søk» under søkefeltet

| #    | Test                                                                                                                                                                         | Forventet resultat                                                                                                                                                                                                                                                                                                                                                | ✅❌ | Notat |
| ---- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 1.1  | Åpne treffsøket uten filtre og søkeord                                                                                                                                       | «Lagre søk» er deaktivert.                                                                                                                                                                                                                                                                                                                                        |      |       |
| 1.2  | Velg fylke Oslo, og trykk «Lagre søk»                                                                                                                                        | Dialogen «Lagre søk» åpnes. Den har ikke valg mellom nytt og erstatt. Den sier at du kan gjøre søket til standard i «Administrer søk». Fokus står i «Navn på søket».                                                                                                                                                                                              |      |       |
| 1.3  | Skriv «Oslo», og trykk «Lagre»                                                                                                                                               | Dialogen lukkes. Du ser «Søket er lagret.». Statuslinjen viser «Lagret søk: Oslo».                                                                                                                                                                                                                                                                                |      |       |
| 1.4  | Åpne menyen                                                                                                                                                                  | «Vanlig søk» står øverst med merknaden «Standard». «Oslo» står under skillelinjen, er markert og har ikke merknaden «Standard». Det første lagrede søket blir ikke standard.                                                                                                                                                                                      |      |       |
| 1.5  | Trykk «Lagre søk» igjen uten å endre noe                                                                                                                                     | Knappen er deaktivert, fordi søket er lagret fra før.                                                                                                                                                                                                                                                                                                             |      |       |
| 1.6  | Velg «Fjern alle filtre». Velg status Fullført, fjern Publisert, og velg sortering Eldste. Lagre som «Eldste fullførte» med Enter i navnefeltet                              | Enter lagrer søket. Dialogen har nå valget «Lagre som nytt søk» og «Erstatt et lagret søk», og «Lagre som nytt søk» er valgt.                                                                                                                                                                                                                                     |      |       |
| 1.7  | Velg «Fjern alle filtre». Skriv «kokk» i søkefeltet, velg sortering Nyeste, og trykk «Lagre søk»                                                                             | Dialogen viser «Søkeord som lagres: «kokk»» og advarselen mot opplysninger om personer.                                                                                                                                                                                                                                                                           |      |       |
| 1.8  | Lagre som «Kokk». Se på adressefeltet                                                                                                                                        | Adressefeltet har `sortering=nyeste`, men ikke «kokk». Søkeord står aldri i adressefeltet.                                                                                                                                                                                                                                                                        |      |       |
| 1.9  | Lagre et nytt søk med tomt navn                                                                                                                                              | Feilmelding: «Du må skrive et navn på søket.» Ingenting lagres.                                                                                                                                                                                                                                                                                                   |      |       |
| 1.10 | Lagre med et navn på 26 tegn                                                                                                                                                 | Feilmelding: «Navnet kan ha maks 25 tegn.» Med 25 tegn lagres søket.                                                                                                                                                                                                                                                                                              |      |       |
| 1.11 | Lagre med navnet «Treff 12345678901»                                                                                                                                         | Feilmelding: «Navnet inneholder noe som kan være et fødselsnummer.» Ingenting lagres.                                                                                                                                                                                                                                                                             |      |       |
| 1.12 | Skriv søkeordet «12345678901», og lagre med navnet «Test»                                                                                                                    | Feilmelding: «Søkeordene inneholder noe som kan være et fødselsnummer. Fjern søkeordet før du lagrer.» Ingenting lagres.                                                                                                                                                                                                                                          |      |       |
| 1.13 | Lagre to søk med samme navn og ulike filtre                                                                                                                                  | Begge lagres og står i menyen. Hvert av dem gir sine egne filtre når du velger det.                                                                                                                                                                                                                                                                               |      |       |
| 1.14 | Åpne «Lagre søk», og lukk med «Avbryt», krysset og Esc                                                                                                                       | Dialogen lukkes og ingenting lagres. Fokus går tilbake til disketten.                                                                                                                                                                                                                                                                                             |      |       |
| 1.15 | Lag søk til du har 10, og åpne «Lagre søk» med et nytt filter                                                                                                                | «Lagre som nytt søk» er deaktivert med teksten «Du har 10 lagrede søk. Erstatt et søk, eller slett et først.» «Erstatt et lagret søk» er valgt, og dialogen har lenken «Slett søk i Administrer søk».                                                                                                                                                             |      |       |
| 1.16 | Velg «Fjern alle filtre». Velg sortering Nyeste, og skriv søkeordet «kokk» to ganger                                                                                         | Chipen «Kokk» vises én gang. Statuslinjen viser «Lagret søk: Kokk», og «Lagre søk» er deaktivert.                                                                                                                                                                                                                                                                 |      |       |
| 1.17 | Velg et filter som ikke er lagret, og skriv to søkeord. Lagre som «Begge». Velg «Fjern alle filtre», velg det samme filteret, og skriv søkeordene i motsatt rekkefølge       | Statuslinjen viser «Lagret søk: Begge». Søkeordene står i rekkefølgen du skrev dem nå. Slett «Begge» etterpå.                                                                                                                                                                                                                                                     |      |       |
| 1.18 | Åpne treffsøket i to faner med de samme filtrene og søkeordene, som ikke er lagret. Åpne «Lagre søk» i begge. Lagre som «Fane 1» i fane 1, og deretter som «Fane 2» i fane 2 | Fane 2 viser «Du har allerede lagret dette søket som «Fane 1». Endre filtrene eller søkeordene, eller avbryt.» hvis listen er hentet på nytt da fanen fikk fokus. Ellers lagres «Fane 2», statuslinjen viser «Lagret søk: Fane 1», og «Administrer søk» viser «Likt søket «Fane 2»» under «Fane 1» og «Likt søket «Fane 1»» under «Fane 2». Slett søkene etterpå. |      |       |
| 1.19 | Fortsett fra 1.15: trykk «Slett søk i Administrer søk». Slett ett søk, og lukk «Administrer søk»                                                                             | «Lagre søk» lukkes, og «Administrer søk (10 av 10)» åpnes. Etter slettingen står det «9 av 10». Når du lukker, står fokus på disketten. Trykker du den, er «Lagre som nytt søk» tilgjengelig. Slett søkene igjen til du har de tre i testdataene.                                                                                                                 |      |       |

## 2. Erstatte innholdet i et lagret søk

**Hvor:** Treffsøket → disketten «Lagre søk» → «Erstatt et lagret søk»

Erstatting bytter filtre og søkeord i et lagret søk. Navnet på søket fylles inn, og du kan beholde det eller skrive et nytt.

| #    | Test                                                                                                                          | Forventet resultat                                                                                                                                                                          | ✅❌ | Notat |
| ---- | ----------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 2.1  | Velg «Oslo» i menyen. Legg til et fylke til, og trykk «Lagre søk»                                                             | «Lagre som nytt søk» er valgt. Velger du «Erstatt et lagret søk», er «Oslo» allerede valgt i «Søk som skal erstattes», fordi det var søket som ble vist sist. «Navn på søket» viser «Oslo». |      |       |
| 2.2  | Trykk «Erstatt søk»                                                                                                           | Du ser «Søket er lagret.». Søket heter fortsatt «Oslo», og statuslinjen viser «Lagret søk: Oslo». Velger du «Oslo» i menyen senere, får du begge fylkene.                                   |      |       |
| 2.3  | Velg «Fjern alle filtre», velg et filter, og velg «Erstatt et lagret søk» uten å velge søk i listen                           | Feilmelding: «Du må velge søket som skal erstattes.»                                                                                                                                        |      |       |
| 2.4  | Velg «Kokk» i menyen. Endre sortering til Eldste og tilbake til Nyeste                                                        | Statuslinjen forsvinner når du velger Eldste, og kommer tilbake med «Lagret søk: Kokk» når du velger Nyeste. «Lagre søk» er da deaktivert.                                                  |      |       |
| 2.5  | Velg «Fjern alle filtre». Velg status Fullført, fjern Publisert, og velg sortering Eldste, uten å velge søket i menyen        | Statuslinjen viser «Lagret søk: Eldste fullførte», og «Eldste fullførte» er markert i menyen. «Lagre søk» er deaktivert, fordi søket er lagret fra før.                                     |      |       |
| 2.6  | Gjør «Oslo» til standard (se seksjon 5). Erstatt «Oslo» med andre filtre                                                      | «Oslo» er fortsatt standard. Åpner du siden på nytt fra menylenken, får du de nye filtrene.                                                                                                 |      |       |
| 2.7  | Åpne treffsøket i to faner. Slett «Kokk» i fane 1. Forsøk å erstatte «Kokk» i fane 2                                          | Feilmelding: «Fant ikke søket du ville erstatte. Det kan være slettet.» Listen i dialogen oppdateres.                                                                                       |      |       |
| 2.8  | Erstatt et søk med søkeordet «12345678901»                                                                                    | Feilmelding om fødselsnummer, som i 1.12. Søket endres ikke.                                                                                                                                |      |       |
| 2.9  | Velg «Kokk» i menyen, og last siden på nytt (F5). Skriv søkeordet «lager», trykk «Lagre søk», og velg «Erstatt et lagret søk» | «Kokk» er valgt i «Søk som skal erstattes», selv om du ikke valgte søket i menyen etter at siden ble lastet.                                                                                |      |       |
| 2.10 | Velg «Erstatt et lagret søk», velg «Kokk», skriv «Kokk nyeste» i «Navn på søket», og trykk «Erstatt søk»                      | Søket heter nå «Kokk nyeste» og har de nye filtrene. Tomt navn, 26 tegn og «Treff 12345678901» gir de samme feilmeldingene som i 1.9–1.11.                                                  |      |       |

## 3. Endre navn

**Hvor:** Treffsøket → menyen → «Administrer søk» → «Endre navn»

| #   | Test                                                                       | Forventet resultat                                                                                                        | ✅❌ | Notat |
| --- | -------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 3.1 | Åpne «Administrer søk»                                                     | Overskriften er «Administrer søk (3 av 10)». «Vanlig søk» står for seg over listen. Hvert søk viser «Sist brukt» og dato. |      |       |
| 3.2 | Trykk «Endre navn» på «Oslo», skriv «Oslo og Viken», og trykk «Lagre navn» | Kvittering: «Oslo» heter nå «Oslo og Viken». Menyen viser det nye navnet. Filtre, søkeord og standardvalg er uendret.     |      |       |
| 3.3 | Endre navnet til tomt, 26 tegn og «Treff 12345678901»                      | Samme feilmeldinger som i 1.9, 1.10 og 1.11. Navnet endres ikke.                                                          |      |       |
| 3.4 | Trykk «Lagre navn» uten å endre navnet                                     | Du kommer tilbake til listen uten kvittering. Ingenting endres.                                                           |      |       |
| 3.5 | Trykk «Endre navn», og avbryt med «Avbryt», krysset og Esc                 | Du kommer tilbake til listen i «Administrer søk». Dialogen lukkes ikke.                                                   |      |       |

## 4. Slette søk

**Hvor:** Treffsøket → menyen → «Administrer søk» → «Slett»

| #   | Test                                                                                         | Forventet resultat                                                                                                                            | ✅❌ | Notat |
| --- | -------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 4.1 | Trykk «Slett» på et søk som ikke er standard                                                 | Dialogen spør «Vil du slette «_navn_»?». Fokus står på «Avbryt».                                                                              |      |       |
| 4.2 | Trykk «Avbryt», krysset eller Esc                                                            | Du kommer tilbake til listen. Søket er ikke slettet.                                                                                          |      |       |
| 4.3 | Trykk «Slett» og bekreft                                                                     | Kvittering: «_navn_» er slettet. Søket er borte fra listen og menyen. Antallet i overskriften går ned.                                        |      |       |
| 4.4 | Slett standardsøket                                                                          | Kvittering: «_navn_» er slettet. Vanlig søk er nå standard. «Vanlig søk» har merknaden «Standard» i menyen og i «Administrer søk».            |      |       |
| 4.5 | Velg et søk i menyen, så statuslinjen viser det. Slett det samme søket i «Administrer søk»   | Statuslinjen forsvinner. Filtrene og søkeordene på siden står som før, og du kan lagre dem som et nytt søk.                                   |      |       |
| 4.6 | Åpne treffsøket i to faner. Slett et søk i fane 1, og velg det samme søket i menyen i fane 2 | Varsel: «Fant ikke det lagrede søket. Det kan være slettet fordi det ikke er brukt på seks måneder.» Filtrene endres ikke. Menyen oppdateres. |      |       |
| 4.7 | Slett et søk når du har 10                                                                   | «Lagre som nytt søk» er tilgjengelig igjen i «Lagre søk».                                                                                     |      |       |

## 5. Gjøre et søk til standard

**Hvor:** Treffsøket → menyen → «Administrer søk» → «Gjør til standard»

| #   | Test                                         | Forventet resultat                                                                                                                                               | ✅❌ | Notat |
| --- | -------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 5.1 | Trykk «Gjør til standard» på «Oslo»          | Kvittering: «Oslo» er nå standardsøket. «Oslo» har merknaden «Standard», og «Vanlig søk» har den ikke lenger. Knappen «Gjør til standard» forsvinner fra «Oslo». |      |       |
| 5.2 | Gjør «Kokk» til standard                     | Bare «Kokk» har merknaden «Standard». Det kan bare være ett standardsøk.                                                                                         |      |       |
| 5.3 | Åpne menyen                                  | Rekkefølgen er «Vanlig søk», standardsøket og resten i alfabetisk rekkefølge.                                                                                    |      |       |
| 5.4 | Trykk «Gjør til standard» på «Vanlig søk»    | Kvittering: «Vanlig søk er nå standard.» Ingen lagrede søk har merknaden «Standard». Ingen søk er slettet.                                                       |      |       |
| 5.5 | Gjør «Oslo» til standard igjen før seksjon 7 | «Oslo» er standard.                                                                                                                                              |      |       |

## 6. Bruke lagrede søk

**Hvor:** Treffsøket → menyen, og «Administrer søk»

| #   | Test                                                                           | Forventet resultat                                                                                                                           | ✅❌ | Notat |
| --- | ------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 6.1 | Velg «Kokk» i menyen                                                           | Menyen lukkes. Sortering er Nyeste, chipen «Kokk» vises, og statuslinjen viser «Lagret søk: Kokk». «Kokk» er markert i menyen.               |      |       |
| 6.2 | Endre sorteringen til Eldste                                                   | Statuslinjen forsvinner, og ingen søk er markert i menyen.                                                                                   |      |       |
| 6.3 | Endre sorteringen tilbake til Nyeste                                           | Statuslinjen viser «Lagret søk: Kokk» igjen, og «Kokk» er markert i menyen.                                                                  |      |       |
| 6.4 | Velg «Vanlig søk» i menyen                                                     | Filtre, sortering og søkeord settes tilbake til vanlig søk. Adressefeltet er `/rekrutteringstreff` uten parametere. Statuslinjen forsvinner. |      |       |
| 6.5 | Velg «Eldste fullførte», og trykk chipen «Fjern alle filtre»                   | Samme resultat som i 6.4.                                                                                                                    |      |       |
| 6.6 | Trykk navnet på et søk i «Administrer søk»                                     | Dialogen lukkes, og søket brukes som i 6.1.                                                                                                  |      |       |
| 6.7 | Trykk «Vanlig søk» i «Administrer søk»                                         | Dialogen lukkes, og vanlig søk brukes som i 6.4.                                                                                             |      |       |
| 6.8 | Bruk menyen med tastaturet: Tab til «Lagrede søk», Enter, piltaster, Enter     | Søket brukes. Fokus er synlig hele veien.                                                                                                    |      |       |
| 6.9 | Gjør nettleservinduet smalere enn 1024 px, og trykk «Filtrer» ved pagineringen | Panelet åpnes med søkefelt, lagrede søk og filtre. Menyen og «Lagre søk» virker som på bred skjerm.                                          |      |       |

## 7. Når siden åpnes

«Oslo» er standardsøket i denne seksjonen (se 5.5). Standardsøket brukes bare fra menylenken, som i stillingssøket.

| #   | Test                                                                                   | Forventet resultat                                                                                           | ✅❌ | Notat |
| --- | -------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------ | ---- | ----- |
| 7.1 | Skriv adressen til treffsøket uten parametere (`…/rekrutteringstreff`) i adressefeltet | Vanlig søk vises, ikke standardsøket «Oslo». Ingen statuslinje.                                              |      |       |
| 7.2 | Åpne `…/rekrutteringstreff?brukStandardsok=true`, for eksempel fra et bokmerke         | Standardsøket «Oslo» brukes, som fra menylenken. `brukStandardsok` forsvinner fra adressefeltet.             |      |       |
| 7.3 | Åpne en lenke med filtre, for eksempel `…/rekrutteringstreff?sortering=eldste`         | Filtrene fra lenken brukes, ikke standardsøket. En delt lenke gir det samme for alle.                        |      |       |
| 7.4 | Velg «Kokk» i menyen, og last siden på nytt (F5)                                       | «Kokk» brukes fortsatt. Chipen «Kokk» kommer tilbake, og statuslinjen viser «Lagret søk: Kokk».              |      |       |
| 7.5 | Velg «Kokk», endre sorteringen, og last siden på nytt                                  | Den endrede sorteringen og søkeordet «kokk» står. Ingen statuslinje, fordi søket ikke lenger er likt «Kokk». |      |       |
| 7.6 | Gå til forsiden, og trykk menylenken «Rekrutteringstreff»                              | Standardsøket «Oslo» brukes.                                                                                 |      |       |
| 7.7 | Gå til stillingssøket, og trykk menylenken «Rekrutteringstreff»                        | Standardsøket «Oslo» brukes.                                                                                 |      |       |
| 7.8 | Velg «Vanlig søk» i menyen, og last siden på nytt                                      | Vanlig søk vises fortsatt. Standardsøket «Oslo» brukes ikke.                                                 |      |       |

## 8. Menylenken mens du står i treffsøket

Menylenken bruker alltid standardsøket, også når du allerede står i treffsøket eller i et treff.

| #   | Test                                                                                             | Forventet resultat                                                                                             | ✅❌ | Notat |
| --- | ------------------------------------------------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 8.1 | Velg sortering Eldste, skriv søkeordet «lager», og trykk menylenken                              | Standardsøket «Oslo» brukes. Sorteringen er tilbake til standard, og «lager» er borte.                         |      |       |
| 8.2 | Gjør «Vanlig søk» til standard. Velg sortering Eldste, skriv «lager», og trykk menylenken        | Vanlig søk vises. Sorteringen er Sist oppdaterte, og «lager» er borte. Gjør «Oslo» til standard igjen etterpå. |      |       |
| 8.3 | Trykk menylenken to ganger etter hverandre                                                       | Standardsøket brukes begge gangene.                                                                            |      |       |
| 8.4 | Skriv søkeordet «lager» mens siden venter på standardsøket (rett etter at du trykket menylenken) | «lager» blir stående. Søkeord du skriver mens siden laster, overskrives ikke.                                  |      |       |
| 8.5 | Velg «Eldste fullførte». Åpne et treff, og trykk menylenken for å komme tilbake til listen       | Standardsøket «Oslo» brukes, ikke «Eldste fullførte» som du kom fra. Stillingssøket gjør det samme.            |      |       |
| 8.6 | Fortsett fra 8.5: kom tilbake til «Eldste fullførte» uten tilbakeknappen                         | Du må velge «Eldste fullførte» i menyen igjen. Velger du «Vanlig søk», får du vanlig søk.                      |      |       |

## 9. Tilbakeknappen i nettleseren

Tilbakeknappen gir det samme søket som før. Var et lagret søk i bruk, og filtrene er uendret, vises søket igjen med søkeordene og «Lagret søk». Søkeord som ikke er lagret, huskes i fanen og kommer også tilbake. Du trenger altså ikke lagre søket for å få det tilbake etter et treff. Når du endrer filtre eller velger et søk, lager ikke siden et nytt steg i historikken. Tilbakeknappen hopper derfor over endringene du gjør inne i treffsøket.

| #    | Test                                                                                                               | Forventet resultat                                                                                               | ✅❌ | Notat |
| ---- | ------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 9.1  | Velg «Kokk» i menyen. Åpne et treff, og trykk tilbakeknappen                                                       | Du kommer tilbake til «Kokk» med søkeordet «kokk» og «Lagret søk: Kokk».                                         |      |       |
| 9.2  | Velg sortering Nyeste, og skriv søkeordet «lager» uten å lagre. Åpne et treff, og trykk tilbakeknappen             | Sorteringen Nyeste og «lager» står. Ingen statuslinje.                                                           |      |       |
| 9.3  | Velg «Vanlig søk» i menyen. Åpne et treff, og trykk tilbakeknappen                                                 | Vanlig søk vises fortsatt. Standardsøket «Oslo» brukes ikke.                                                     |      |       |
| 9.4  | Velg sortering Nyeste, og skriv søkeordet «lager». Trykk menylenken, så standardsøket brukes. Trykk tilbakeknappen | Sorteringen Nyeste og «lager» kommer tilbake. Søkeordene fra standardsøket er borte, og ingen statuslinje vises. |      |       |
| 9.5  | Velg sortering Nyeste. Åpne et treff, og trykk menylenken. Trykk tilbakeknappen én gang, og så én gang til         | Første gang kommer du til treffet. Andre gang kommer du til treffsøket med sorteringen Nyeste.                   |      |       |
| 9.6  | Velg «Kokk» i menyen, og trykk menylenken, så standardsøket «Oslo» brukes. Trykk tilbakeknappen                    | Du kommer tilbake til «Kokk» med søkeordet «kokk» og «Lagret søk: Kokk».                                         |      |       |
| 9.7  | Fortsett fra 9.6: trykk framknappen                                                                                | «Oslo» vises igjen med «Lagret søk: Oslo», uten søkeordet «kokk».                                                |      |       |
| 9.8  | Kom til treffsøket fra forsiden. Velg «Oslo», så «Eldste fullførte» og så «Kokk» i menyen. Trykk tilbakeknappen    | Du kommer til forsiden. Valg i treffsøket lager ikke egne steg i historikken.                                    |      |       |
| 9.9  | Skriv søkeordet «lager» uten å lagre, og last siden på nytt (F5)                                                   | «lager» står.                                                                                                    |      |       |
| 9.10 | Fortsett fra 9.9: kopier adressen, og åpne den i en ny fane                                                        | «lager» vises ikke. Søkeord som ikke er lagret, huskes bare i fanen der du skrev dem.                            |      |       |
| 9.11 | Skriv søkeordene «lager» og «12345678901» uten å lagre, og last siden på nytt (F5)                                 | «lager» står, men «12345678901» er borte. Søkeord som kan være et fødselsnummer, huskes ikke i fanen.            |      |       |

## 10. Feil og nettverksproblemer

| #    | Test                                                                               | Forventet resultat                                                                                                         | ✅❌ | Notat |
| ---- | ---------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------- | ---- | ----- |
| 10.1 | Utvikler: slå av nettverket i utviklerverktøyet, og velg et søk i menyen           | Varsel: «Kunne ikke bruke det lagrede søket. Prøv igjen.» Filtrene endres ikke.                                            |      |       |
| 10.2 | Utvikler: blokker `/api/bruker/lagrede-sok`, og trykk menylenken                   | Varsel: «Kunne ikke hente standardsøket. Viser vanlig søk.» Treffene vises med vanlig søk.                                 |      |       |
| 10.3 | Utvikler: slå av nettverket, og lagre et søk                                       | Feilmelding i dialogen: «Kunne ikke lagre søket. Prøv igjen.» Dialogen blir stående og kan sendes på nytt.                 |      |       |
| 10.4 | Utvikler: sett `sistAktiv` for et søk til mer enn fem måneder tilbake i databasen  | Menyen viser «Slettes snart» under søket. «Administrer søk» viser «Slettes _dato_». Bruker du søket, forsvinner merknaden. |      |       |
| 10.5 | Utvikler: sett `sistAktiv` for et søk til mer enn seks måneder tilbake i databasen | Søket vises ikke i menyen. Var det standard, er «Vanlig søk» standard.                                                     |      |       |

## 11. Stillingssøket og etterregistreringen er uendret

| #    | Test                                                        | Forventet resultat                                                                             | ✅❌ | Notat |
| ---- | ----------------------------------------------------------- | ---------------------------------------------------------------------------------------------- | ---- | ----- |
| 11.1 | Trykk menylenken «Stillingsoppdrag»                         | Stillingssøket bruker det gamle standardsøket, som før. Lagrede søk fra treffsøket vises ikke. |      |       |
| 11.2 | Lagre et standardsøk i stillingssøket, og gå til treffsøket | Treffsøket påvirkes ikke. Standardsøket i stillingssøket er det samme som før.                 |      |       |
| 11.3 | Åpne etterregistreringen, og bruk «Bruk standardsøk»        | Den virker som før og åpner stillingssøket med det gamle standardsøket.                        |      |       |

---

## Avklaringer om når standardsøket skal brukes

Oppførselen er observert 2. og 3. november 2026 mot bygget frontend med mock.

Besluttet 3. november 2026:

- Standardsøket brukes bare fra menylenken, som i stillingssøket. Siden bruker det ikke når den åpnes uten parametere, etter refresh eller med tilbakeknappen.
- Menylenken gir standardsøket også når veilederen står i et treff, som i stillingssøket (8.5).
- Tilbakeknappen gir de samme filtrene som før, og viser det lagrede søket som var i bruk hvis filtrene er uendret (9.4, 9.6). Valg inne i treffsøket lager ikke egne steg i historikken (9.8).
- Søket er uendret når veilederen åpner et treff og går tilbake, også søkeord som ikke er lagret (9.2). De huskes i fanen, ikke i adressefeltet.
- Statuslinjen «Lagret søk» viser søket som har de samme filtrene og søkeordene som siden, og kommer tilbake når filtrene settes tilbake (2.4, 6.3). Rekkefølgen på søkeordene teller ikke. Frontend lagrer ikke et søk som finnes fra før. Backend tillater like søk, og blant dem er det øverste valgt. «Administrer søk» viser hvilke som er like (1.16–1.18).

Ingen avklaringer gjenstår.
