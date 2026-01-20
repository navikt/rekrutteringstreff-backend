# Ordliste

Denne ordlisten forklarer domenebegreper og tekniske termer som brukes i rekrutteringstreff-backend.

---

## Domenebegreper

### Rekrutteringstreff

Et arrangert møte mellom arbeidsgivere og jobbsøkere, organisert av NAV. Formålet er at jobbsøkere skal få muligheter til jobb gjennom direkte kontakt med potensielle arbeidsgivere.

**Livssyklus:**

1. **OPPRETTET** - Treffet er opprettet, men ikke publisert
2. **PUBLISERT** - Treffet er aktivt og jobbsøkere kan inviteres
3. **FULLFØRT** - Treffet har funnet sted
4. **AVLYST** - Treffet er kansellert

### Jobbsøker

En person som er lagt til i et rekrutteringstreff. Jobbsøkeren kan være under oppfølging i NAV eller ikke.

**Statuser:**

- **LAGT_TIL** - Jobbsøker er lagt til treffet, men ikke invitert
- **INVITERT** - Jobbsøker har mottatt invitasjon
- **SVART_JA** - Jobbsøker har takket ja til invitasjonen
- **SVART_NEI** - Jobbsøker har takket nei til invitasjonen

### Arbeidsgiver

En virksomhet som deltar på et rekrutteringstreff. Identifiseres med organisasjonsnummer.

### Innlegg

En melding eller oppdatering knyttet til et rekrutteringstreff. Kan inneholde informasjon om programmet, praktiske detaljer eller annen relevant informasjon for deltakerne.

---

## Roller i NAV

### Veileder (Jobbsøkerrettet)

NAV-ansatt som jobber direkte med jobbsøkere. Har tilgang til:

- Se rekrutteringstreff
- Legge til egne jobbsøkere på treff
- Se innlegg

Kan **ikke** opprette eller administrere treff.

### Markedskontakt (Arbeidsgiverrettet)

NAV-ansatt som jobber med arbeidsgivere og rekruttering. Har tilgang til:

- Opprette og administrere rekrutteringstreff
- Legge til arbeidsgivere og jobbsøkere
- Invitere jobbsøkere
- Publisere og avlyse treff

### Utvikler

Teknisk rolle med full tilgang til alle funksjoner, inkludert KI-validering og logging.

---

## Tekniske begreper

### Synlighet

Indikerer om en jobbsøker skal vises i systemet. Jobbsøkere som ikke er synlige (f.eks. pga. adressebeskyttelse, død, manglende samtykke) filtreres automatisk bort fra alle API-responser.

Evalueres av **toi-synlighetsmotor** basert på data fra flere kildesystemer.

### Rapids & Rivers

NAVs Kafka-baserte meldingsplattform for asynkron kommunikasjon mellom mikrotjenester. Brukes for å publisere hendelser og lytte på meldinger.

**Event-pattern:** Publiser hendelse → Andre systemer reagerer asynkront

**Need-pattern:** Etterspør data → System svarer med data

### TokenX

Mekanisme for å veksle tokens mellom tjenester. Brukes når rekrutteringstreff-minside-api kommuniserer med rekrutteringstreff-api på vegne av en jobbsøker.

### Aktivitetskort

Et kort i jobbsøkerens aktivitetsplan som viser invitasjonen til rekrutteringstreffet. Synkroniseres automatisk med jobbsøkerens svar og treffets status.

### CEF (Common Event Format)

Standardformat for audit-logging i NAV. Brukes for å logge hvem som har sett eller endret sensitive data.

### SecureLog

Egen loggstrøm for sensitive data (fødselsnummer, navn, etc.) som kun er tilgjengelig for teammedlemmer.

---

## Forkortelser

| Forkortelse | Betydning                                                |
| ----------- | -------------------------------------------------------- |
| **API**     | Application Programming Interface                        |
| **CV**      | Curriculum Vitae (jobbsøkers CV i NAVs systemer)         |
| **KI**      | Kunstig intelligens                                      |
| **KRR**     | Kontakt- og reservasjonsregisteret                       |
| **KVP**     | Kvalifiseringsprogrammet                                 |
| **MinSide** | NAVs selvbetjeningsløsning for borgere                   |
| **NAIS**    | NAVs applikasjonsplattform (Kubernetes-basert)           |
| **PII**     | Personally Identifiable Information (personopplysninger) |
| **REST**    | Representational State Transfer (HTTP API-stil)          |

---

## Applikasjoner

| Applikasjon                                 | Beskrivelse                                   |
| ------------------------------------------- | --------------------------------------------- |
| **rekrutteringstreff-api**                  | Hoved-API for veiledere og markedskontakter   |
| **rekrutteringstreff-minside-api**          | API for jobbsøkere via MinSide                |
| **rekrutteringsbistand-aktivitetskort**     | Kafka-lytter som synkroniserer aktivitetskort |
| **rekrutteringsbistand-kandidatvarsel-api** | Sender SMS/e-post til jobbsøkere              |
| **rekrutteringsbistand-frontend**           | Brukergrensesnitt for NAV-ansatte             |
| **toi-synlighetsmotor**                     | Evaluerer om jobbsøkere er synlige            |

---

## Se også

- [Oversikt](oversikt.md) - Introduksjon til systemet
- [Tilgangsstyring](../3-sikkerhet/tilgangsstyring.md) - Detaljert beskrivelse av roller
