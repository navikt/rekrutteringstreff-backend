ALTER TABLE rekrutteringstreff
    ADD COLUMN sist_endret timestamp with time zone not null DEFAULT now(),
    ADD COLUMN sist_endret_av text;