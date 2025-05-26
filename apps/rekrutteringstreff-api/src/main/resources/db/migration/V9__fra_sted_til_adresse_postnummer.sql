ALTER TABLE rekrutteringstreff
DROP COLUMN sted,
    ADD COLUMN gateadresse text,
    ADD COLUMN postnummer text;