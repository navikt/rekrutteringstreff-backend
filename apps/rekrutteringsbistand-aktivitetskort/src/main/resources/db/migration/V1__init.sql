CREATE TABLE aktivitetskort (
    db_id                       bigserial PRIMARY KEY,
    aktivitetskort_id           uuid NOT NULL UNIQUE,
    rekrutteringstreff_id       uuid NOT NULL,
    fnr                         TEXT NOT NULL,
    tittel                      TEXT NOT NULL,
    beskrivelse                 TEXT NOT NULL,
    start_dato                   date,
    slutt_dato                   date,
    opprettet_av                   TEXT NOT NULL,
    opprettet_av_type              TEXT NOT NULL,
    opprettet_tidspunkt            timestamp with time zone NOT NULL,
    UNIQUE (rekrutteringstreff_id, fnr)
);

CREATE TABLE aktivitetskort_hendelse (
    db_id                       bigserial PRIMARY KEY,
    message_id                  uuid NOT NULL UNIQUE,
    aktivitetskort_id           uuid NOT NULL,
    action_type                 TEXT NOT NULL,
    endret_av                   TEXT NOT NULL,
    endret_av_type              TEXT NOT NULL,
    endret_tidspunkt            timestamp with time zone NOT NULL,
    aktivitets_status           TEXT NOT NULL,
    sendt_tidspunkt             timestamp
)