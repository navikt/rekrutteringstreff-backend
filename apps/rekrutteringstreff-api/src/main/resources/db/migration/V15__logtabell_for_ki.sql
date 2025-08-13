-- Append-only loggtabell for KI-spørringer knyttet til rekrutteringstreff

CREATE TABLE ki_sporring_logg
(
    id                          bigserial PRIMARY KEY,
    treff_db_id                 bigint                   NOT NULL,
    felt_type                   text                     NOT NULL,
    antall_endringer_for_type_og_rekrutteringstreff          integer                  NOT NULL DEFAULT 0,

    sporring_fra_frontend       text                     NOT NULL,
    sporring_filtrert           text                     NOT NULL,
    systemprompt                text,
    ekstra_parametre            jsonb,

    ki_navn                     text                     NOT NULL,
    ki_versjon                  text                     NOT NULL,

    bryter_retningslinjer    boolean                  NOT NULL,
    begrunnelse              text,

    svartid_ms                  integer                  NOT NULL,
    lagret_likevel              boolean                  NOT NULL DEFAULT false,

    opprettet_tidspunkt         timestamp with time zone NOT NULL DEFAULT now(),

    FOREIGN KEY (treff_db_id) REFERENCES rekrutteringstreff (db_id),

    CHECK (felt_type IN ('tittel', 'innlegg')),
);

CREATE INDEX ki_sporring_logg_treff_idx ON ki_sporring_logg (treff_db_id);
