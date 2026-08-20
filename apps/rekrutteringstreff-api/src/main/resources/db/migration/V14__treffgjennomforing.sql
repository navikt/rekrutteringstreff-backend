CREATE TABLE treffgjennomforing
(
    treffgjennomforing_id bigserial PRIMARY KEY,
    rekrutteringstreff_id bigint NOT NULL UNIQUE REFERENCES rekrutteringstreff (rekrutteringstreff_id),
    steg                  text   NOT NULL --enum
);

CREATE TABLE moteoppsett
(
    moteoppsett_id        bigserial PRIMARY KEY,
    treffgjennomforing_id bigint NOT NULL UNIQUE REFERENCES treffgjennomforing (treffgjennomforing_id),
    starttidspunkt        time   NOT NULL,
    varighet_min          int    NOT NULL CHECK (varighet_min >= 1)
);

CREATE TABLE deltakernummer
(
    deltakernummer_id     bigserial PRIMARY KEY,
    rekrutteringstreff_id bigint NOT NULL REFERENCES rekrutteringstreff (rekrutteringstreff_id),
    jobbsoker_id          bigint NOT NULL REFERENCES jobbsoker (jobbsoker_id),
    deltakernummer        int    NOT NULL CHECK (deltakernummer >= 1),
    CONSTRAINT deltakernummer_unikt_per_treff UNIQUE (rekrutteringstreff_id, deltakernummer),
    CONSTRAINT deltakernummer_ett_per_jobbsoker UNIQUE (rekrutteringstreff_id, jobbsoker_id)
);

CREATE TABLE jobbsoker_rom_tildeling
(
    jobbsoker_rom_tildeling_id bigserial PRIMARY KEY,
    rekrutteringstreff_id      bigint NOT NULL REFERENCES rekrutteringstreff (rekrutteringstreff_id),
    jobbsoker_id               bigint NOT NULL REFERENCES jobbsoker (jobbsoker_id),
    romnummer                  int    NOT NULL CHECK (romnummer >= 1),
    CONSTRAINT jobbsoker_rom_tildeling_ett_rom_per_jobbsoker UNIQUE (rekrutteringstreff_id, jobbsoker_id)
);

CREATE TABLE arbeidsgiver_rotasjon
(
    arbeidsgiver_rotasjon_id bigserial PRIMARY KEY,
    arbeidsgiver_id          bigint NOT NULL UNIQUE REFERENCES arbeidsgiver (arbeidsgiver_id),
    forste_romnummer         int    NOT NULL CHECK (forste_romnummer >= 1)
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
    vurderingsstatus        text,
    andregangsintervju      boolean NOT NULL DEFAULT false,
    andregangsintervju_dato date,
    jobbtilbud              boolean NOT NULL DEFAULT false,
    vurderingsnotat         text[]  NOT NULL DEFAULT '{}',
    CONSTRAINT vurdering_unikt_par UNIQUE (jobbsoker_id, arbeidsgiver_id),
    CONSTRAINT vurdering_dato_krever_andregangsintervju
        CHECK (andregangsintervju_dato IS NULL OR andregangsintervju)
);

CREATE INDEX idx_deltakernummer_jobbsoker ON deltakernummer (jobbsoker_id);
CREATE INDEX idx_jobbsoker_rom_tildeling_treff ON jobbsoker_rom_tildeling (rekrutteringstreff_id);
CREATE INDEX idx_interesse_arbeidsgiver ON interesse (arbeidsgiver_id);
CREATE INDEX idx_intervju_fordeling_arbeidsgiver ON intervju_fordeling (arbeidsgiver_id);
CREATE INDEX idx_vurdering_arbeidsgiver ON vurdering (arbeidsgiver_id);
