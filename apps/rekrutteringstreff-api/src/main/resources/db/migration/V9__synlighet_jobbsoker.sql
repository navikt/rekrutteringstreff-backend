
ALTER TABLE jobbsoker
ADD COLUMN er_synlig BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN synlighet_sist_oppdatert TIMESTAMP WITH TIME ZONE DEFAULT NULL;

CREATE INDEX idx_jobbsoker_synlig ON jobbsoker (er_synlig) WHERE er_synlig = FALSE;
CREATE INDEX idx_jobbsoker_fodselsnummer ON jobbsoker (fodselsnummer);
