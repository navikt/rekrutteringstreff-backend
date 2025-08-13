CREATE TABLE ki_sporring_logg
(
    id                                              bigserial PRIMARY KEY,
    opprettet_tidspunkt                             timestamp with time zone NOT NULL DEFAULT now(),

    treff_db_id                                     bigint                   NOT NULL REFERENCES rekrutteringstreff (db_id),
    felt_type                                       text                     NOT NULL,
    antall_endringer_for_type_og_rekrutteringstreff integer                  NOT NULL DEFAULT 0,
    siste_endring_for_type_og_rekrutteringstreff    boolean                  NOT NULL DEFAULT false,

    sporring_fra_frontend                           text                     NOT NULL,
    sporring_filtrert                               text                     NOT NULL,
    systemprompt                                    text,
    ekstra_parametre                                jsonb,

    bryter_retningslinjer                           boolean                  NOT NULL,
    begrunnelse                                     text,

    ki_navn                                         text                     NOT NULL,
    ki_versjon                                      text                     NOT NULL,

    svartid_ms                                      integer                  NOT NULL,
    lagret_til_tross_for_negativt_svar              boolean                  NOT NULL DEFAULT false,

    CHECK (felt_type IN ('tittel', 'innlegg'))
);

CREATE INDEX ki_sporring_logg_treff_idx ON ki_sporring_logg (treff_db_id);