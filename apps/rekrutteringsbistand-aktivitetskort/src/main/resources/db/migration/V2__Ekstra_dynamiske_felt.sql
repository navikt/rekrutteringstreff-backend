CREATE TABLE aktivitetskort_dynamisk (
    db_id      bigserial PRIMARY KEY,
    message_id uuid NOT NULL UNIQUE,
    detaljer   TEXT,
    handlinger TEXT,
    etiketter  TEXT,
    oppgave   TEXT
);