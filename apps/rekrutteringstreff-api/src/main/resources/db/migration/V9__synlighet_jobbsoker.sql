
ALTER TABLE jobbsoker
ADD COLUMN er_synlig BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN synlighet_sist_oppdatert TIMESTAMP WITH TIME ZONE DEFAULT NULL;

CREATE INDEX idx_jobbsoker_synlig ON jobbsoker (er_synlig) WHERE er_synlig = TRUE;
CREATE INDEX idx_jobbsoker_fodselsnummer ON jobbsoker (fodselsnummer);

-- Indeks for scheduler-spørringen som finner jobbsøkere uten evaluert synlighet
-- Spørringen: WHERE synlighet_sist_oppdatert IS NULL AND status != 'SLETTET'
CREATE INDEX idx_jobbsoker_synlighet_ikke_evaluert
    ON jobbsoker (synlighet_sist_oppdatert)
    WHERE synlighet_sist_oppdatert IS NULL AND status != 'SLETTET';

