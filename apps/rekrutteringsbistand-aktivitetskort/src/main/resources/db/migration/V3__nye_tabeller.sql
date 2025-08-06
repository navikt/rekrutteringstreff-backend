drop table aktivitetskort;
drop table aktivitetskort_hendelse;
drop table aktivitetskort_dynamisk;
CREATE TABLE aktivitetskort
(
    db_id                 bigserial PRIMARY KEY,
    message_id            uuid                     NOT NULL UNIQUE,
    aktivitetskort_id     uuid                     NOT NULL,
    fnr                   TEXT                     NOT NULL,
    tittel                TEXT                     NOT NULL,
    aktivitets_status     TEXT                     NOT NULL,
    beskrivelse           TEXT,
    start_dato            date,
    slutt_dato            date,
    detaljer              json                     NOT NULL CHECK (json_typeof(detaljer)     = 'array'),
    handlinger            json                     CHECK (json_typeof(handlinger)     = 'array'),
    etiketter             json                     NOT NULL CHECK (json_typeof(etiketter)     = 'array'),
    oppgave               json,
    action_type           TEXT                     NOT NULL,
    avtalt_med_nav        BOOLEAN                  NOT NULL,
    endret_av             TEXT                     NOT NULL,
    endret_av_type        TEXT                     NOT NULL,
    endret_tidspunkt      timestamp with time zone NOT NULL,
    sendt_tidspunkt       timestamp with time zone
);
CREATE INDEX idx_aktivitetskort_aktivitetskort_id ON aktivitetskort (aktivitetskort_id);
CREATE TABLE rekrutteringstreff(
    db_id                   bigserial               PRIMARY KEY,
    aktivitetskort_id       UUID                    NOT NULL,
    fnr                     TEXT                    NOT NULL,
    rekrutteringstreff_id   UUID                    NOT NULL,
    UNIQUE (rekrutteringstreff_id, fnr)
);