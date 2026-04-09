CREATE OR REPLACE VIEW jobbsoker_sok_view AS
SELECT
    j.jobbsoker_id,
    j.id AS person_treff_id,
    rt.id AS treff_id,
    j.rekrutteringstreff_id,
    j.fodselsnummer,
    j.fornavn,
    j.etternavn,
    j.navkontor,
    j.veileder_navn,
    j.veileder_navident,
    j.status,
    (SELECT jh.tidspunkt
     FROM jobbsoker_hendelse jh
     WHERE jh.jobbsoker_id = j.jobbsoker_id
       AND jh.hendelsestype = 'OPPRETTET'
     ORDER BY jh.tidspunkt ASC
     LIMIT 1) AS lagt_til_dato,
    (SELECT jh.aktøridentifikasjon
     FROM jobbsoker_hendelse jh
     WHERE jh.jobbsoker_id = j.jobbsoker_id
       AND jh.hendelsestype = 'OPPRETTET'
     ORDER BY jh.tidspunkt ASC
     LIMIT 1) AS lagt_til_av
FROM jobbsoker j
JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
WHERE j.er_synlig = true
  AND j.status != 'SLETTET';
