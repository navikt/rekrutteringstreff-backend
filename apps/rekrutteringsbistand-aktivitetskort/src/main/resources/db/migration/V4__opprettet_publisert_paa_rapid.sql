-- Legg til kolonne for å spore om aktivitetskort-oppdatering er publisert på rapid
ALTER TABLE aktivitetskort
    ADD COLUMN publisert_paa_rapid_tidspunkt timestamp with time zone;
