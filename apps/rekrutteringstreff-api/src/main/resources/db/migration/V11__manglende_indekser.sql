CREATE INDEX idx_jobbsoker_rekrutteringstreff_id ON jobbsoker (rekrutteringstreff_id);

CREATE INDEX idx_jobbsoker_id ON jobbsoker (id);

CREATE INDEX idx_arbeidsgiver_rekrutteringstreff_id ON arbeidsgiver (rekrutteringstreff_id);

CREATE INDEX idx_arbeidsgiver_id ON arbeidsgiver (id);

CREATE INDEX idx_jobbsoker_hendelse_jobbsoker_id ON jobbsoker_hendelse (jobbsoker_id);

CREATE INDEX idx_jobbsoker_hendelse_hendelsestype ON jobbsoker_hendelse (hendelsestype);
