-- Synlighetsfelter på jobbsoker-tabellen
-- er_synlig: Om jobbsøkeren skal vises i frontend (default TRUE for "synlig frem til svar")
-- synlighet_sist_oppdatert: Når synlighetsmotor sist ga sin vurdering (NULL = ikke vurdert ennå)

ALTER TABLE jobbsoker
ADD COLUMN er_synlig BOOLEAN NOT NULL DEFAULT TRUE,
ADD COLUMN synlighet_sist_oppdatert TIMESTAMP WITH TIME ZONE DEFAULT NULL;

-- Indeks for raskere filtrering av ikke-synlige jobbsøkere
CREATE INDEX idx_jobbsoker_synlig ON jobbsoker (er_synlig) WHERE er_synlig = FALSE;

-- Indeks på fødselsnummer for rask oppslag ved synlighetsmeldinger
-- (synlighetsmeldinger fra Rapid må slå opp på tvers av alle rekrutteringstreff)
CREATE INDEX idx_jobbsoker_fodselsnummer ON jobbsoker (fodselsnummer);
