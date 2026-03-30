CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE jobbsoker_sok (
    jobbsoker_id           bigint PRIMARY KEY REFERENCES jobbsoker(jobbsoker_id),
    rekrutteringstreff_id  bigint NOT NULL,

    status                 text NOT NULL DEFAULT 'LAGT_TIL',
    invitert_dato          timestamptz,
    lagt_til_dato          timestamptz,
    lagt_til_av            text,
    er_synlig              boolean NOT NULL DEFAULT TRUE,

    fornavn                text,
    etternavn              text,

    fylke                  text,
    kommune                text,
    poststed               text,

    navkontor              text,
    veileder_navident      text,
    veileder_navn          text,
    innsatsgruppe          text,

    sok_tekst              text GENERATED ALWAYS AS (
        LOWER(
            COALESCE(fornavn, '') || ' ' ||
            COALESCE(etternavn, '') || ' ' ||
            COALESCE(poststed, '') || ' ' ||
            COALESCE(kommune, '') || ' ' ||
            COALESCE(fylke, '') || ' ' ||
            COALESCE(veileder_navn, '') || ' ' ||
            COALESCE(veileder_navident, '')
        )
    ) STORED
);

-- Backfill fra eksisterende jobbsøkere
INSERT INTO jobbsoker_sok (
    jobbsoker_id, rekrutteringstreff_id, status, invitert_dato, lagt_til_dato, lagt_til_av, er_synlig,
    fornavn, etternavn, navkontor, veileder_navident, veileder_navn
)
SELECT
    js.jobbsoker_id,
    js.rekrutteringstreff_id,
    js.status,
    (SELECT MIN(jh.tidspunkt)
     FROM jobbsoker_hendelse jh
     WHERE jh.jobbsoker_id = js.jobbsoker_id AND jh.hendelsestype = 'INVITERT'),
    (SELECT MIN(jh.tidspunkt)
     FROM jobbsoker_hendelse jh
     WHERE jh.jobbsoker_id = js.jobbsoker_id AND jh.hendelsestype = 'OPPRETTET'),
    (SELECT jh.aktøridentifikasjon
     FROM jobbsoker_hendelse jh
     WHERE jh.jobbsoker_id = js.jobbsoker_id AND jh.hendelsestype = 'OPPRETTET'
     ORDER BY jh.tidspunkt ASC LIMIT 1),
    js.er_synlig,
    js.fornavn,
    js.etternavn,
    js.navkontor,
    js.veileder_navident,
    js.veileder_navn
FROM jobbsoker js;

-- GIN trigram-indeks for fritekstsøk
CREATE INDEX idx_jobbsoker_sok_tekst
    ON jobbsoker_sok USING gin (sok_tekst gin_trgm_ops);

-- Partiell indeks for aktive jobbsøkere per treff
CREATE INDEX idx_jobbsoker_sok_aktiv
    ON jobbsoker_sok (rekrutteringstreff_id) WHERE er_synlig = true AND status != 'SLETTET';

-- Komposittindeks for status-filtrering per treff
CREATE INDEX idx_jobbsoker_sok_treff_status
    ON jobbsoker_sok (rekrutteringstreff_id, status) WHERE er_synlig = true;
