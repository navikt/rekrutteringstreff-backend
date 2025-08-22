CREATE TABLE naringskode
    db_id  BIGSERIAL PRIMARY KEY,
    kode text UNIQUE,
    beskrivelse text;