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
        WHEN rt.status = 'PUBLISERT' AND (rt.svarfrist IS NULL OR rt.svarfrist >= now()) THEN true
        ELSE false
    END AS apen_for_sokere
FROM rekrutteringstreff rt
WHERE rt.status != 'SLETTET';
