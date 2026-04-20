# Typeahead for arbeidsgivers behov i pam-ontologi

> Denne planen er erstattet av [kombinert-typeahead-i-pam-ontologi.md](./kombinert-typeahead-i-pam-ontologi.md).
>
> Den tidligere modellen med én smal `typeahead_behov`-tabell og ett kombinert endepunkt med `kategorier`-parameter er forkastet etter review:
>
> - Skjemaet skiller seg ut fra de andre `typeahead_*`-tabellene (smale kolonner, `label`/`label_lc`, serial PK).
> - Softskills og førerkort dro inn duplikater på tvers av kategorier.
> - `kategorier`-parameteren ga unødvendig API-kompleksitet og en `ResponseEntity<Any>` med problemdetail-aktige feilmeldinger.
>
> Ny løsning:
>
> - `typeahead_samlede_kvalifikasjoner` (kombinert: yrkestittel, kompetanse, autorisasjon, fagdokumentasjon, førerkort) — samme skjema og indeksering som de andre typeahead-tabellene, pluss en `kategori`-kolonne. Hentes parameterløst via `GET /rest/typeahead/samlede_kvalifikasjoner`.
> - `typeahead_personlige_egenskaper` (softskills) — identisk skjema med `typeahead_kompetanse`, søkbar via `GET /rest/typeahead/personlige_egenskaper?q=...` med samme `finnTypeahead`-mønster som `/kompetanse`.
>
> Se den nye planen for full begrunnelse, skjema, populering, API og testopplegg.
