CREATE TABLE naringskode
(
    db_id       BIGSERIAL PRIMARY KEY,
    arbeidsgiver_db_id BIGINT NOT NULL,
    kode        text UNIQUE,
    beskrivelse text,
    CONSTRAINT naringskode_arbeidsgiver_fk
        FOREIGN KEY (arbeidsgiver_db_id)
            REFERENCES arbeidsgiver (db_id)
            ON DELETE CASCADE,
    CONSTRAINT naringskode_unik_per_arbeidsgiver UNIQUE (arbeidsgiver_db_id, kode)
);

CREATE INDEX naringskode_arbeidsgiver_db_id_idx ON naringskode (arbeidsgiver_db_id);