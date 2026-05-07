CREATE TABLE arbeidsgivers_behov
(
    arbeidsgiver_id         bigint PRIMARY KEY REFERENCES arbeidsgiver (arbeidsgiver_id),
    arbeidssprak            text[]      NOT NULL DEFAULT '{}',
    antall                  int         NOT NULL,
    samlede_kvalifikasjoner jsonb       NOT NULL DEFAULT '[]'::jsonb,
    ansettelsesformer       text[]      NOT NULL DEFAULT '{}',
    personlige_egenskaper   jsonb       NOT NULL DEFAULT '[]'::jsonb,
    oppdatert               timestamptz NOT NULL DEFAULT now()
);
