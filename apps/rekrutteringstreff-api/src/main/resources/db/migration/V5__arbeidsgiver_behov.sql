CREATE TABLE arbeidsgiver_behov
(
    behov_id                bigserial PRIMARY KEY,
    arbeidsgiver_id         bigint NOT NULL REFERENCES arbeidsgiver (arbeidsgiver_id),
    arbeidssprak            text[] NOT NULL DEFAULT '{}',
    antall                  int    NOT NULL,
    samlede_kvalifikasjoner jsonb  NOT NULL DEFAULT '[]'::jsonb,
    ansettelsesformer       text[] NOT NULL DEFAULT '{}',
    personlige_egenskaper   jsonb  NOT NULL DEFAULT '[]'::jsonb
);

CREATE UNIQUE INDEX idx_arbeidsgiver_behov_arbeidsgiver ON arbeidsgiver_behov (arbeidsgiver_id);
