ALTER TABLE jobbsoker
    ADD COLUMN lagt_til_dato timestamptz,
    ADD COLUMN lagt_til_av text;

WITH forste_opprettelse AS (
    SELECT DISTINCT ON (jh.jobbsoker_id)
        jh.jobbsoker_id,
        jh.tidspunkt,
        jh.aktøridentifikasjon
    FROM jobbsoker_hendelse jh
    WHERE jh.hendelsestype = 'OPPRETTET'
    ORDER BY jh.jobbsoker_id, jh.tidspunkt ASC, jh.jobbsoker_hendelse_id ASC
),
jobbsoker_grunnlag AS (
    SELECT
        js.jobbsoker_id,
        COALESCE(fo.tidspunkt, now()) AS lagt_til_dato,
        COALESCE(fo.aktøridentifikasjon, 'ukjent') AS lagt_til_av
    FROM jobbsoker js
    LEFT JOIN forste_opprettelse fo ON fo.jobbsoker_id = js.jobbsoker_id
)
UPDATE jobbsoker js
SET lagt_til_dato = jobbsoker_grunnlag.lagt_til_dato,
    lagt_til_av = jobbsoker_grunnlag.lagt_til_av
FROM jobbsoker_grunnlag
WHERE jobbsoker_grunnlag.jobbsoker_id = js.jobbsoker_id;

ALTER TABLE jobbsoker
    ALTER COLUMN lagt_til_dato SET DEFAULT now(),
    ALTER COLUMN lagt_til_dato SET NOT NULL,
    ALTER COLUMN lagt_til_av SET NOT NULL;

CREATE INDEX idx_jobbsoker_fornavn
    ON jobbsoker (rekrutteringstreff_id, LOWER(fornavn) text_pattern_ops)
    WHERE er_synlig = true AND status != 'SLETTET';

CREATE INDEX idx_jobbsoker_etternavn
    ON jobbsoker (rekrutteringstreff_id, LOWER(etternavn) text_pattern_ops)
    WHERE er_synlig = true AND status != 'SLETTET';

CREATE INDEX idx_jobbsoker_sok_aktiv
    ON jobbsoker (rekrutteringstreff_id) WHERE er_synlig = true AND status != 'SLETTET';

CREATE INDEX idx_jobbsoker_sok_treff_lagt_til
    ON jobbsoker (rekrutteringstreff_id, lagt_til_dato DESC, jobbsoker_id DESC)
    WHERE er_synlig = true AND status != 'SLETTET';

CREATE INDEX idx_jobbsoker_sok_treff_status
    ON jobbsoker (rekrutteringstreff_id, status) WHERE er_synlig = true AND status != 'SLETTET';
