CREATE TABLE rekrutteringstreff (
    db_id serial PRIMARY KEY,
    id uuid NOT NULL,
    tittel text,
    status text,
    opprettet_av_person text,
    opprettet_av_kontor text,
    opprettet_av_tidspunkt date NOT NULL,
    eiere text[]
);

CREATE INDEX rekrutteringstreff_uuid_id_idx ON rekrutteringstreff(id);


