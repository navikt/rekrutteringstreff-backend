CREATE TABLE innlegg (
                         db_id  BIGSERIAL PRIMARY KEY,
                         id     UUID  NOT NULL DEFAULT gen_random_uuid() UNIQUE,
                         treff_db_id BIGINT NOT NULL REFERENCES rekrutteringstreff (db_id) ON DELETE CASCADE,
                         tittel                         TEXT NOT NULL,
                         opprettet_av_person_navident   TEXT NOT NULL,
                         opprettet_av_person_navn       TEXT NOT NULL,
                         opprettet_av_person_beskrivelse TEXT NOT NULL,
                         sendes_til_jobbsoker_tidspunkt TIMESTAMP WITH TIME ZONE,
                         html_content                   TEXT NOT NULL,
                         opprettet_tidspunkt       TIMESTAMP WITH TIME ZONE NOT NULL  DEFAULT now(),
                         sist_oppdatert_tidspunkt  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_innlegg_treff_db_id ON innlegg (treff_db_id);

DROP INDEX IF EXISTS rekrutteringstreff_uuid_id_idx;
CREATE UNIQUE INDEX rekrutteringstreff_id_uq ON rekrutteringstreff(id);