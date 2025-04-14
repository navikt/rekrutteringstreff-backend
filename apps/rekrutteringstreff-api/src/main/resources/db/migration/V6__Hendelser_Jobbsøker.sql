CREATE TABLE jobbsoker_hendelse
(
    db_id                  bigserial PRIMARY KEY,
    id                     uuid                     NOT NULL,
    jobbsoker_db_id        bigint                   NOT NULL,
    tidspunkt              TIMESTAMP WITH TIME ZONE NOT NULL,
    hendelsestype          text                     NOT NULL,
    opprettet_av_aktortype text                     NOT NULL,
    aktøridentifikasjon    text,
    FOREIGN KEY (jobbsoker_db_id) REFERENCES jobbsoker (db_id) ON DELETE CASCADE
);
