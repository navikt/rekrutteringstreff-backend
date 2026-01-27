-- Indekser for spørringer som vil bli trege med mye data

-- aktivitetskort: Partial indeks for å finne usendte aktivitetskort effektivt
CREATE INDEX idx_aktivitetskort_usendt ON aktivitetskort (sendt_tidspunkt) WHERE sendt_tidspunkt IS NULL;

-- aktivitetskort: fnr brukes i spørringer for å finne aktivitetskort per person
CREATE INDEX idx_aktivitetskort_fnr ON aktivitetskort (fnr);

-- aktivitetskort_hendelse_feil: Partial indeks for å finne usendte feilhendelser
CREATE INDEX idx_aktivitetskort_hendelse_feil_usendt ON aktivitetskort_hendelse_feil (sendt_tidspunkt) WHERE sendt_tidspunkt IS NULL;

-- rekrutteringstreff: aktivitetskort_id brukes i JOIN for å koble aktivitetskort til treff
CREATE INDEX idx_rekrutteringstreff_aktivitetskort_id ON rekrutteringstreff (aktivitetskort_id);
