-- sql
CREATE TABLE ki_spørring_logg
(
    db_id                                              bigserial PRIMARY KEY,
    id                                                 uuid                     NOT NULL DEFAULT gen_random_uuid(),
    opprettet_tidspunkt                                timestamp with time zone NOT NULL DEFAULT now(),

    treff_db_id                                        bigint                   NOT NULL REFERENCES rekrutteringstreff (db_id) ON DELETE CASCADE,
    felt_type                                          text                     NOT NULL,

    spørring_fra_frontend                              text                     NOT NULL,
    spørring_filtrert                                  text                     NOT NULL,
    systemprompt                                       text,
    ekstra_parametre                                   jsonb,

    bryter_retningslinjer                              boolean                  NOT NULL,
    begrunnelse                                        text,

    ki_navn                                            text                     NOT NULL,
    ki_versjon                                         text                     NOT NULL,

    svartid_ms                                         integer                  NOT NULL,

    lagret                                             boolean                  NOT NULL DEFAULT false,

    manuell_kontroll_bryter_retningslinjer             boolean,
    manuell_kontroll_utført_av                         text,
    manuell_kontroll_tidspunkt                         timestamp with time zone,

    CHECK (felt_type IN ('tittel', 'innlegg'))
);

CREATE INDEX ki_spørring_logg_treff_idx ON ki_spørring_logg (treff_db_id);