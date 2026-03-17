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
    rt.kommunenummer,
    rt.kommune,
    rt.fylkesnummer,
    rt.fylke,
    rt.opprettet_av_tidspunkt,
    rt.sist_endret,
    rt.eiere,
    rt.kontorer,
    CASE
        WHEN rt.status = 'UTKAST' THEN 'UTKAST'
        WHEN rt.status = 'AVLYST' THEN 'AVLYST'
        WHEN rt.status = 'FULLFØRT' THEN 'FULLFORT'
        WHEN rt.status = 'PUBLISERT' AND rt.svarfrist IS NOT NULL AND rt.svarfrist < now() THEN 'SOKNADSFRIST_PASSERT'
        WHEN rt.status = 'PUBLISERT' THEN 'PUBLISERT'
    END AS visningsstatus
FROM rekrutteringstreff rt
WHERE rt.status != 'SLETTET';
