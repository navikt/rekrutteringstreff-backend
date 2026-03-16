# Jobbsøkersøk innad i et treff

## Idé

Paginering og filtrering av jobbsøkerne i et enkelt rekrutteringstreff. Kopierer strategi og struktur fra [rekrutteringstreffsøket](2.5-postgres-med-view-sok.md): view, dynamisk SQL, aggregeringer, paginering.

---

## Forutsetning

Rekrutteringstreffsøket (oppgave 1–2 i [2.5-postgres-med-view-sok.md](2.5-postgres-med-view-sok.md)) er ferdig. Vi har da et etablert mønster for view-basert søk med filtrering og aggregeringer som kan gjenbrukes.

---

## Hva vi kopierer fra rekrutteringstreffsøket

- **View** som samler jobbsøkerdata i en flat struktur
- **Lagdeling**: Controller → Service → Repository mot view
- **Dynamisk SQL** med valgfrie filtre
- **Aggregeringer** (antall per filterkategori)
- **Paginering** (offset/limit)
- **Frontend**: filter- og pagineringskomponenter

---

## TODO

- [ ] Kartlegg hvilke filtre som er relevante (status, evt. andre felt)
- [ ] Definer view (`jobbsoker_sok_view` e.l.)
- [ ] Opprett Flyway-migrasjon
- [ ] Implementer Controller → Service → Repository
- [ ] Implementer aggregeringer
- [ ] Implementer paginering
- [ ] Frontend: gjenbruk filter/paginering-mønsteret
- [ ] Detaljer og scope defineres nærmere når rekrutteringstreffsøket er ferdig
