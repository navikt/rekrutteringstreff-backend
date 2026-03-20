CREATE INDEX idx_rekrutteringstreff_eiere ON rekrutteringstreff USING GIN (eiere);
CREATE INDEX idx_rekrutteringstreff_kontorer ON rekrutteringstreff USING GIN (kontorer);
CREATE INDEX idx_rekrutteringstreff_status ON rekrutteringstreff (status);
