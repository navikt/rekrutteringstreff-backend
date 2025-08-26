CREATE TABLE naringskode
(
    db_id       BIGSERIAL PRIMARY KEY,
    arbeidsgiver_db_id BIGINT NOT NULL,
    kode        text UNIQUE,
    beskrivelse text,
    FOREIGN KEY (arbeidsgiver_db_id) REFERENCES arbeidsgiver (db_id)
);