CREATE TABLE treffgjennomforing
(
    treffgjennomforing_id bigserial PRIMARY KEY,
    rekrutteringstreff_id bigint NOT NULL UNIQUE REFERENCES rekrutteringstreff (rekrutteringstreff_id),
    fase                  text   NOT NULL
);

CREATE TABLE moteoppsett
(
    moteoppsett_id        bigserial PRIMARY KEY,
    treffgjennomforing_id bigint NOT NULL UNIQUE REFERENCES treffgjennomforing (treffgjennomforing_id),
    start_tidspunkt       time   NOT NULL,
    varighet_min          int    NOT NULL CHECK (varighet_min >= 1)
);

CREATE TABLE deltakernummer
(
    deltakernummer_id     bigserial PRIMARY KEY,
    rekrutteringstreff_id bigint NOT NULL REFERENCES rekrutteringstreff (rekrutteringstreff_id),
    jobbsoker_id          bigint NOT NULL REFERENCES jobbsoker (jobbsoker_id),
    nummer                int    NOT NULL CHECK (nummer >= 1),
    CONSTRAINT deltakernummer_unikt_per_treff UNIQUE (rekrutteringstreff_id, nummer),
    CONSTRAINT deltakernummer_ett_per_jobbsoker UNIQUE (rekrutteringstreff_id, jobbsoker_id)
);

CREATE TABLE jobbsoker_rom_tildeling
(
    jobbsoker_rom_tildeling_id bigserial PRIMARY KEY,
    rekrutteringstreff_id      bigint NOT NULL REFERENCES rekrutteringstreff (rekrutteringstreff_id),
    jobbsoker_id               bigint NOT NULL REFERENCES jobbsoker (jobbsoker_id),
    romnummer                  int    NOT NULL CHECK (romnummer >= 1),
    plassering                 int    NOT NULL CHECK (plassering >= 0),
    CONSTRAINT jobbsoker_rom_tildeling_ett_rom_per_jobbsoker UNIQUE (rekrutteringstreff_id, jobbsoker_id)
);

CREATE TABLE arbeidsgiver_rotasjon
(
    arbeidsgiver_rotasjon_id bigserial PRIMARY KEY,
    arbeidsgiver_id          bigint NOT NULL UNIQUE REFERENCES arbeidsgiver (arbeidsgiver_id),
    start_posisjon           int    NOT NULL CHECK (start_posisjon >= 0)
);

CREATE TABLE interesse
(
    interesse_id    bigserial PRIMARY KEY,
    jobbsoker_id    bigint NOT NULL REFERENCES jobbsoker (jobbsoker_id),
    arbeidsgiver_id bigint NOT NULL REFERENCES arbeidsgiver (arbeidsgiver_id),
    CONSTRAINT interesse_unikt_par UNIQUE (jobbsoker_id, arbeidsgiver_id)
);

CREATE TABLE intervju_fordeling
(
    intervju_fordeling_id bigserial PRIMARY KEY,
    jobbsoker_id          bigint  NOT NULL REFERENCES jobbsoker (jobbsoker_id),
    arbeidsgiver_id       bigint  NOT NULL REFERENCES arbeidsgiver (arbeidsgiver_id),
    plassering            int     NOT NULL CHECK (plassering >= 0),
    inkludert             boolean NOT NULL,
    CONSTRAINT intervju_fordeling_unikt_par UNIQUE (jobbsoker_id, arbeidsgiver_id)
);

CREATE TABLE vurdering
(
    vurdering_id            bigserial PRIMARY KEY,
    jobbsoker_id            bigint  NOT NULL REFERENCES jobbsoker (jobbsoker_id),
    arbeidsgiver_id         bigint  NOT NULL REFERENCES arbeidsgiver (arbeidsgiver_id),
    vurdering               text,
    andregangsintervju      boolean NOT NULL DEFAULT false,
    andregangsintervju_dato date,
    jobbtilbud              boolean NOT NULL DEFAULT false,
    CONSTRAINT vurdering_unikt_par UNIQUE (jobbsoker_id, arbeidsgiver_id),
    CONSTRAINT vurdering_dato_krever_andregangsintervju
        CHECK (andregangsintervju_dato IS NULL OR andregangsintervju)
);

CREATE TABLE vurdering_notat
(
    vurdering_notat_id bigserial PRIMARY KEY,
    vurdering_id       bigint NOT NULL REFERENCES vurdering (vurdering_id) ON DELETE CASCADE,
    notat              text   NOT NULL,
    CONSTRAINT vurdering_notat_unikt UNIQUE (vurdering_id, notat)
);

CREATE INDEX idx_deltakernummer_jobbsoker ON deltakernummer (jobbsoker_id);
CREATE INDEX idx_jobbsoker_rom_tildeling_treff ON jobbsoker_rom_tildeling (rekrutteringstreff_id);
CREATE INDEX idx_interesse_arbeidsgiver ON interesse (arbeidsgiver_id);
CREATE INDEX idx_intervju_fordeling_arbeidsgiver ON intervju_fordeling (arbeidsgiver_id);
CREATE INDEX idx_vurdering_arbeidsgiver ON vurdering (arbeidsgiver_id);
CREATE INDEX idx_vurdering_notat_vurdering ON vurdering_notat (vurdering_id);

DELETE
FROM aktivitetskort_polling
WHERE jobbsoker_hendelse_id IN (SELECT jobbsoker_hendelse_id
                                FROM jobbsoker_hendelse
                                WHERE hendelsestype IN ('MØTT_OPP', 'IKKE_MØTT_OPP'));

DELETE
FROM jobbsoker_hendelse
WHERE hendelsestype IN ('MØTT_OPP', 'IKKE_MØTT_OPP');

ALTER TABLE jobbsoker
    ADD COLUMN oppmote text;

CREATE INDEX idx_jobbsoker_oppmote
    ON jobbsoker (rekrutteringstreff_id)
    WHERE oppmote = 'REGISTRERT_OPPMØTE';
