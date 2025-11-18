-- Tabell for å tracke hvilke jobbsøker-hendelser som er sendt på rapid for varsling til Min side
CREATE TABLE varsling_polling (
    varsling_polling_id bigserial PRIMARY KEY,
    jobbsoker_hendelse_id bigint NOT NULL,
    sendt_tidspunkt timestamp with time zone NOT NULL,
    CONSTRAINT varsling_polling_jobbsoker_hendelse_fk
        FOREIGN KEY (jobbsoker_hendelse_id) REFERENCES jobbsoker_hendelse (jobbsoker_hendelse_id)
);
CREATE INDEX idx_varsling_polling_jobbsoker_hendelse_id ON varsling_polling (jobbsoker_hendelse_id);

-- Legg til indeks på aktivitetskort_polling for konsistens
CREATE INDEX IF NOT EXISTS idx_aktivitetskort_polling_jobbsoker_hendelse_id ON aktivitetskort_polling (jobbsoker_hendelse_id);
