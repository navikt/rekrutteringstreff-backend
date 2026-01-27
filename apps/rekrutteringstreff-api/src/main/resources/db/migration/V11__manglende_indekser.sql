-- Indekser for foreign keys som brukes i JOINs og WHERE-klausuler

-- jobbsoker: rekrutteringstreff_id brukes i alle JOINs mot rekrutteringstreff
CREATE INDEX idx_jobbsoker_rekrutteringstreff_id ON jobbsoker (rekrutteringstreff_id);

-- jobbsoker: id (UUID) brukes ofte i subqueries for å finne jobbsoker_id
CREATE INDEX idx_jobbsoker_id ON jobbsoker (id);

-- arbeidsgiver: rekrutteringstreff_id brukes i JOINs for å hente arbeidsgivere per treff
CREATE INDEX idx_arbeidsgiver_rekrutteringstreff_id ON arbeidsgiver (rekrutteringstreff_id);

-- arbeidsgiver: id (UUID) brukes i WHERE-klausuler for oppslag
CREATE INDEX idx_arbeidsgiver_id ON arbeidsgiver (id);

-- jobbsoker_hendelse: jobbsoker_id brukes i LEFT JOINs og aggregering av hendelser
CREATE INDEX idx_jobbsoker_hendelse_jobbsoker_id ON jobbsoker_hendelse (jobbsoker_id);

-- jobbsoker_hendelse: hendelsestype brukes i WHERE-filtrering (aktivitetskort polling)
-- Kombinert med partial indeks for usendte hendelser
CREATE INDEX idx_jobbsoker_hendelse_hendelsestype ON jobbsoker_hendelse (hendelsestype);
