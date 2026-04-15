CREATE OR REPLACE VIEW jobbsoker_sok_view AS
SELECT
    j.jobbsoker_id,
    j.id AS person_treff_id,
    rt.id AS treff_id,
    j.rekrutteringstreff_id,
    j.fodselsnummer,
    j.fornavn,
    j.etternavn,
    j.status,
    opprettet.tidspunkt AS lagt_til_dato,
    opprettet.aktøridentifikasjon AS lagt_til_av,
    opprettet.hendelse_data ->> 'lagtTilAvNavn' AS lagt_til_av_navn
FROM jobbsoker j
JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
LEFT JOIN LATERAL (
    SELECT
        jh.tidspunkt,
        jh.aktøridentifikasjon,
        jh.hendelse_data
    FROM jobbsoker_hendelse jh
    WHERE jh.jobbsoker_id = j.jobbsoker_id
      AND jh.hendelsestype = 'OPPRETTET'
    ORDER BY jh.tidspunkt ASC
    LIMIT 1
) opprettet ON true
WHERE j.er_synlig = true
  AND j.status != 'SLETTET';
