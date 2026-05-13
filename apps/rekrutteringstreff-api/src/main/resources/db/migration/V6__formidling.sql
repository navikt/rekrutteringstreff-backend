CREATE TABLE formidling
(
    formidling_id         bigserial PRIMARY KEY,
    id                    uuid NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    rekrutteringstreff_id bigint      NOT NULL REFERENCES rekrutteringstreff (rekrutteringstreff_id),
    jobbsoker_id          bigint      NOT NULL REFERENCES jobbsoker (jobbsoker_id),
    arbeidsgiver_id       bigint      NOT NULL REFERENCES arbeidsgiver (arbeidsgiver_id),
    stilling_id           uuid        NOT NULL,
    opprettet_tidspunkt   timestamptz NOT NULL DEFAULT now(),
    slettet_tidspunkt     timestamptz
);

CREATE INDEX idx_formidling_rekrutteringstreff_id ON formidling (rekrutteringstreff_id);
CREATE INDEX idx_formidling_jobbsoker_id ON formidling (jobbsoker_id);
CREATE INDEX idx_formidling_arbeidsgiver_id ON formidling (arbeidsgiver_id);
