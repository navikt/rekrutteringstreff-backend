ALTER TABLE ki_spørring_logg
    ADD COLUMN treff_id uuid REFERENCES rekrutteringstreff (id) ON DELETE SET NULL;

CREATE INDEX ki_spørring_logg_treff_uuid_idx ON ki_spørring_logg (treff_id);

DROP INDEX IF EXISTS ki_spørring_logg_treff_idx;
ALTER TABLE ki_spørring_logg DROP COLUMN IF EXISTS treff_db_id;