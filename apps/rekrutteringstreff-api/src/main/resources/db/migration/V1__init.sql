CREATE TABLE rekrutteringstreff
(
    db_id                        bigserial PRIMARY KEY,
    id                           uuid                     NOT NULL,
    tittel                       text                     NOT NULL,
    status                       text                     NOT NULL,
    opprettet_av_person_navident text,
    opprettet_av_kontor_enhetid  text,
    opprettet_av_tidspunkt       timestamp with time zone not null,
    fratid                       timestamp with time zone,
    tiltid                       timestamp with time zone,
    sted                         text,
    eiere                        text[]                   NOT NULL,
    beskrivelse                  text
);

CREATE INDEX rekrutteringstreff_uuid_id_idx ON rekrutteringstreff (id);

CREATE TABLE arbeidsgiver
(
    db_id       bigserial PRIMARY KEY,
    treff_db_id bigint NOT NULL,
    orgnr       text   NOT NULL,
    orgnavn     text,
    FOREIGN KEY (treff_db_id) REFERENCES rekrutteringstreff (db_id)
);
