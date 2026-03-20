CREATE OR REPLACE VIEW rekrutteringstreff_sok_view AS
SELECT
    rt.id,
    rt.tittel,
    rt.beskrivelse,
    rt.status,
    rt.fratid AS fra_tid,
    rt.tiltid AS til_tid,
    rt.svarfrist,
    rt.gateadresse,
    rt.postnummer,
    rt.poststed,
    rt.opprettet_av_tidspunkt,
    rt.sist_endret,
    rt.eiere,
    rt.kontorer,
    CASE
        WHEN rt.svarfrist IS NOT NULL AND rt.svarfrist < now() THEN true
        ELSE false
    END AS frist_utgatt,
    (SELECT count(*) FROM arbeidsgiver a WHERE a.rekrutteringstreff_id = rt.rekrutteringstreff_id) AS antall_arbeidsgivere,
    (SELECT count(*) FROM jobbsoker j WHERE j.rekrutteringstreff_id = rt.rekrutteringstreff_id) AS antall_jobbsokere
FROM rekrutteringstreff rt
WHERE rt.status != 'SLETTET';
