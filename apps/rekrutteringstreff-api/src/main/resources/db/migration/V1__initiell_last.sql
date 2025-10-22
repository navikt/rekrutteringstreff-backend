-- Initielt lasteskript uten ON DELETE CASCADE
-- Merk: Bruker Postgres-funksjonen gen_random_uuid() fra pgcrypto
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1) rekrutteringstreff
CREATE TABLE rekrutteringstreff
(
    db_id                        bigserial PRIMARY KEY,
    id                           uuid                     NOT NULL,
    tittel                       text                     NOT NULL,
    status                       text                     NOT NULL,
    opprettet_av_person_navident text,
    opprettet_av_kontor_enhetid  text,
    opprettet_av_tidspunkt       timestamp with time zone not null,
    fratid                       timestamp with time zone,
    tiltid                       timestamp with time zone,
    gateadresse                  text,
    postnummer                   text,
    poststed                     text,
    svarfrist                    timestamp with time zone,
    eiere                        text[]                   NOT NULL,
    beskrivelse                  text
);

-- Unik index på id (erstatter gammel ikke-unik idx)
CREATE UNIQUE INDEX rekrutteringstreff_id_uq ON rekrutteringstreff(id);

-- 2) arbeidsgiver
CREATE TABLE arbeidsgiver
(
    db_id       bigserial PRIMARY KEY,
    treff_db_id bigint NOT NULL,
    orgnr       text   NOT NULL,
    orgnavn     text,
    id          uuid   NOT NULL,
    CONSTRAINT arbeidsgiver_treff_db_id_fkey
        FOREIGN KEY (treff_db_id) REFERENCES rekrutteringstreff (db_id)
);

-- 3) jobbsoker
CREATE TABLE jobbsoker
(
    db_id             bigserial PRIMARY KEY,
    treff_db_id       bigint NOT NULL,
    fodselsnummer     text   NOT NULL,
    fornavn           text,
    etternavn         text,
    kandidatnummer    text,
    navkontor         text,
    veileder_navn     text,
    veileder_navident text,
    id                uuid   NOT NULL,
    CONSTRAINT jobbsoker_treff_db_id_fkey
        FOREIGN KEY (treff_db_id) REFERENCES rekrutteringstreff (db_id)
);

-- 4) jobbsoker_hendelse
CREATE TABLE jobbsoker_hendelse
(
    db_id                  bigserial PRIMARY KEY,
    id                     uuid                     NOT NULL,
    jobbsoker_db_id        bigint                   NOT NULL,
    tidspunkt              timestamp with time zone NOT NULL,
    hendelsestype          text                     NOT NULL,
    opprettet_av_aktortype text                     NOT NULL,
    aktøridentifikasjon    text,
    CONSTRAINT jobbsoker_hendelse_jobbsoker_fk
        FOREIGN KEY (jobbsoker_db_id) REFERENCES jobbsoker (db_id)
);

-- 5) arbeidsgiver_hendelse
CREATE TABLE arbeidsgiver_hendelse
(
    db_id                  bigserial PRIMARY KEY,
    id                     uuid                     NOT NULL,
    arbeidsgiver_db_id     bigint                   NOT NULL,
    tidspunkt              timestamp with time zone NOT NULL,
    hendelsestype          text                     NOT NULL,
    opprettet_av_aktortype text                     NOT NULL,
    aktøridentifikasjon    text,
    CONSTRAINT arbeidsgiver_hendelse_arbeidsgiver_fk
        FOREIGN KEY (arbeidsgiver_db_id) REFERENCES arbeidsgiver (db_id)
);
CREATE INDEX idx_arbeidsgiver_hendelse_arbeidsgiver_db_id ON arbeidsgiver_hendelse (arbeidsgiver_db_id);

-- 6) rekrutteringstreff_hendelse
CREATE TABLE rekrutteringstreff_hendelse
(
    db_id                    bigserial PRIMARY KEY,
    id                       uuid                     NOT NULL,
    rekrutteringstreff_db_id bigint                   NOT NULL,
    tidspunkt                timestamp with time zone NOT NULL,
    hendelsestype            text                     NOT NULL,
    opprettet_av_aktortype   text                     NOT NULL,
    aktøridentifikasjon      text,
    CONSTRAINT rekrutteringstreff_hendelse_treff_fk
        FOREIGN KEY (rekrutteringstreff_db_id) REFERENCES rekrutteringstreff (db_id)
);
CREATE INDEX idx_rekrutteringstreff_hendelse_rekrutteringstreff_db_id ON rekrutteringstreff_hendelse (rekrutteringstreff_db_id);

-- 7) innlegg
CREATE TABLE innlegg (
    db_id                          bigserial PRIMARY KEY,
    id                             uuid  NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    treff_db_id                    bigint NOT NULL REFERENCES rekrutteringstreff (db_id),
    tittel                         text NOT NULL,
    opprettet_av_person_navident   text NOT NULL,
    opprettet_av_person_navn       text NOT NULL,
    opprettet_av_person_beskrivelse text NOT NULL,
    sendes_til_jobbsoker_tidspunkt timestamp with time zone,
    html_content                   text NOT NULL,
    opprettet_tidspunkt            timestamp with time zone NOT NULL DEFAULT now(),
    sist_oppdatert_tidspunkt       timestamp with time zone NOT NULL DEFAULT now()
);
CREATE INDEX idx_innlegg_treff_db_id ON innlegg (treff_db_id);

-- 8) aktivitetskort_polling
CREATE TABLE aktivitetskort_polling (
    db_id                    bigserial PRIMARY KEY,
    jobbsoker_hendelse_db_id bigint  NOT NULL,
    sendt_tidspunkt          timestamp with time zone NOT NULL,
    CONSTRAINT aktivitetskort_polling_jobbsoker_hendelse_fk
        FOREIGN KEY (jobbsoker_hendelse_db_id) REFERENCES jobbsoker_hendelse (db_id)
);

-- 9) ki_spørring_logg
CREATE TABLE ki_spørring_logg
(
    db_id                                              bigserial PRIMARY KEY,
    id                                                 uuid                     NOT NULL DEFAULT gen_random_uuid(),
    opprettet_tidspunkt                                timestamp with time zone NOT NULL DEFAULT now(),

    treff_id                                           uuid REFERENCES rekrutteringstreff (id) ON DELETE SET NULL,
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
    manuell_kontroll_tidspunkt                         timestamp with time zone
);
CREATE INDEX ki_spørring_logg_treff_uuid_idx ON ki_spørring_logg (treff_id);

-- 10) naringskode
CREATE TABLE naringskode
(
    db_id             bigserial PRIMARY KEY,
    arbeidsgiver_db_id bigint NOT NULL,
    kode              text,
    beskrivelse       text,
    CONSTRAINT naringskode_arbeidsgiver_fk
        FOREIGN KEY (arbeidsgiver_db_id)
        REFERENCES arbeidsgiver (db_id),
    CONSTRAINT naringskode_unik_per_arbeidsgiver UNIQUE (arbeidsgiver_db_id, kode)
);
CREATE INDEX naringskode_arbeidsgiver_db_id_idx ON naringskode (arbeidsgiver_db_id);
