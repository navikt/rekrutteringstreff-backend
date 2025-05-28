CREATE TABLE aktivitetskort (
    db_id                       bigserial PRIMARY KEY,
    aktivitetskort_id           uuid NOT NULL UNIQUE,
    rekrutteringstreff_id       uuid NOT NULL,
    fnr                         TEXT NOT NULL,
    tittel                      TEXT NOT NULL,
    beskrivelse                 TEXT NOT NULL,
    aktivitets_status           TEXT,
    start_dato                   date,
    slutt_dato                   date,
    endret_av                   TEXT NOT NULL,
    endret_av_type              TEXT NOT NULL,
    endret_tidspunkt            timestamp with time zone NOT NULL
);