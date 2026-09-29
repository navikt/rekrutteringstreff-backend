-- Fase 6b (contract) av planen i docs/9-planer/eiere-og-kontorer-egen-tabell.md
--
-- Eiere og kontorer leses og skrives bare via rekrutteringstreff_eier. GIN-indeksene fra V3
-- på kolonnene droppes automatisk sammen med dem.

ALTER TABLE rekrutteringstreff
    DROP COLUMN eiere,
    DROP COLUMN kontorer;
