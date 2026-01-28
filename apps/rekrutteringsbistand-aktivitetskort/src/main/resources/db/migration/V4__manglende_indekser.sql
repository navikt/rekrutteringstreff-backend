
CREATE INDEX idx_aktivitetskort_usendt ON aktivitetskort (sendt_tidspunkt) WHERE sendt_tidspunkt IS NULL;

CREATE INDEX idx_aktivitetskort_fnr ON aktivitetskort (fnr);

CREATE INDEX idx_aktivitetskort_hendelse_feil_usendt ON aktivitetskort_hendelse_feil (sendt_tidspunkt) WHERE sendt_tidspunkt IS NULL;

CREATE INDEX idx_rekrutteringstreff_aktivitetskort_id ON rekrutteringstreff (aktivitetskort_id);
