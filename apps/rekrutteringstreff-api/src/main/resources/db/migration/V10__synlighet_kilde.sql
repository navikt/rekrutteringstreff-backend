-- Legger til synlighet_kilde for å skille mellom events og need-svar.
-- Dette gjør at event-strømmen alltid kan overskrive need-svar,
-- mens events bare overskriver andre events hvis tidspunktet er nyere.

ALTER TABLE jobbsoker
ADD COLUMN synlighet_kilde TEXT DEFAULT NULL;
