CREATE TABLE arbeidsgiver_hendelse
(
    db_id                  BIGSERIAL PRIMARY KEY,
    id                     UUID                     NOT NULL,
    arbeidsgiver_db_id     BIGINT                   NOT NULL,
    tidspunkt              TIMESTAMP WITH TIME ZONE NOT NULL,
    hendelsestype          TEXT                     NOT NULL,
    opprettet_av_aktortype TEXT                     NOT NULL,
    aktøridentifikasjon    TEXT,
    FOREIGN KEY (arbeidsgiver_db_id) REFERENCES arbeidsgiver (db_id) ON DELETE CASCADE
);

CREATE INDEX idx_arbeidsgiver_hendelse_arbeidsgiver_db_id ON arbeidsgiver_hendelse (arbeidsgiver_db_id);
