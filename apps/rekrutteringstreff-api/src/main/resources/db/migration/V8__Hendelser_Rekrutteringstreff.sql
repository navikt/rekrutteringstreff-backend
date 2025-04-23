CREATE TABLE rekrutteringstreff_hendelse
(
    db_id                    BIGSERIAL PRIMARY KEY,
    id                       UUID                     NOT NULL,
    rekrutteringstreff_db_id BIGINT                   NOT NULL,
    tidspunkt                TIMESTAMP WITH TIME ZONE NOT NULL,
    hendelsestype            TEXT                     NOT NULL,
    opprettet_av_aktortype   TEXT                     NOT NULL,
    aktøridentifikasjon      TEXT,
    FOREIGN KEY (rekrutteringstreff_db_id) REFERENCES rekrutteringstreff (db_id) ON DELETE CASCADE
);

CREATE INDEX idx_rekrutteringstreff_hendelse_rekrutteringstreff_db_id
    ON rekrutteringstreff_hendelse (rekrutteringstreff_db_id);