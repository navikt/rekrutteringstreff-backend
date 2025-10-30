-- Add hendelse_data column to event tables for storing additional JSON data

ALTER TABLE jobbsoker_hendelse
ADD COLUMN hendelse_data jsonb;

ALTER TABLE rekrutteringstreff_hendelse
ADD COLUMN hendelse_data jsonb;

ALTER TABLE arbeidsgiver_hendelse
ADD COLUMN hendelse_data jsonb;

