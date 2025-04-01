CREATE TABLE jobbsoker
(
    db_id         bigserial PRIMARY KEY,
    treff_db_id   bigint NOT NULL,
    fodselsnummer text   NOT NULL,
    fornavn       text,
    etternavn     text,
    FOREIGN KEY (treff_db_id) REFERENCES rekrutteringstreff (db_id)
);
