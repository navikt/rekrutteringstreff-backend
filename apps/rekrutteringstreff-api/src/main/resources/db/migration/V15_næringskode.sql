CREATE TABLE naringskode
    db_id  BIGSERIAL PRIMARY KEY,
    FOREIGN KEY (arbeidsgiver_db_id) REFERENCES arbeidsgiver (db_id) ON DELETE CASCADE
    kode text;
    beskrivelse text;