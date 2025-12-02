ALTER TABLE rekrutteringstreff
    ADD COLUMN sist_endret timestamp with time zone not null default now(),
    ADD COLUMN sist_endret_av text;