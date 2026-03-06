ALTER TABLE rekrutteringstreff ADD COLUMN kontorer text[];

UPDATE rekrutteringstreff
SET kontorer = ARRAY[opprettet_av_kontor_enhetid]
WHERE opprettet_av_kontor_enhetid IS NOT NULL;

ALTER TABLE rekrutteringstreff_hendelse ADD COLUMN subjekt_id text;
ALTER TABLE rekrutteringstreff_hendelse ADD COLUMN subjekt_navn text;
