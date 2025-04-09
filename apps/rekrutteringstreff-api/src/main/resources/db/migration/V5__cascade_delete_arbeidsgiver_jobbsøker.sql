ALTER TABLE arbeidsgiver DROP CONSTRAINT arbeidsgiver_treff_db_id_fkey;
ALTER TABLE arbeidsgiver
    ADD CONSTRAINT arbeidsgiver_treff_db_id_fkey
        FOREIGN KEY (treff_db_id) REFERENCES rekrutteringstreff(db_id) ON DELETE CASCADE;

ALTER TABLE jobbsoker DROP CONSTRAINT jobbsoker_treff_db_id_fkey;
ALTER TABLE jobbsoker
    ADD CONSTRAINT jobbsoker_treff_db_id_fkey
        FOREIGN KEY (treff_db_id) REFERENCES rekrutteringstreff(db_id) ON DELETE CASCADE;