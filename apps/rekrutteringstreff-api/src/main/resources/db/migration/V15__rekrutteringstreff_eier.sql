-- Fase 1 (expand) av planen i docs/9-planer/eiere-og-kontorer-egen-tabell.md
--
-- Oppretter tabellen som skal bevare koblingen «eier X kom fra kontor Y», som i dag går
-- tapt fordi rekrutteringstreff.eiere[] og rekrutteringstreff.kontorer[] er to uavhengige
-- arrays.

CREATE TABLE rekrutteringstreff_eier
(
    rekrutteringstreff_eier_id bigserial PRIMARY KEY,
    id                         uuid                     NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    rekrutteringstreff_id      bigint                   NOT NULL REFERENCES rekrutteringstreff (rekrutteringstreff_id),
    nav_ident                  text                     NOT NULL,
    eier_navn                  text,
    kontor_enhetid             text,
    lagt_til_tidspunkt         timestamp with time zone NOT NULL DEFAULT now(),
    lagt_til_av                text,
    CONSTRAINT rekrutteringstreff_eier_unik UNIQUE (rekrutteringstreff_id, nav_ident)
);

CREATE INDEX idx_rekrutteringstreff_eier_treff ON rekrutteringstreff_eier (rekrutteringstreff_id);
CREATE INDEX idx_rekrutteringstreff_eier_ident ON rekrutteringstreff_eier (nav_ident);
CREATE INDEX idx_rekrutteringstreff_eier_kontor ON rekrutteringstreff_eier (kontor_enhetid);
