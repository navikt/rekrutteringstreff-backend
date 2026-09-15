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
    rt.opprettet_av_person_navident,
    rt.opprettet_av_tidspunkt,
    rt.sist_endret,
    COALESCE((SELECT array_agg(DISTINCT e.nav_ident)
              FROM rekrutteringstreff_eier e
              WHERE e.rekrutteringstreff_id = rt.rekrutteringstreff_id), '{}'::text[]) AS eiere,
    COALESCE((SELECT array_agg(DISTINCT e.kontor_enhetid)
              FROM rekrutteringstreff_eier e
              WHERE e.rekrutteringstreff_id = rt.rekrutteringstreff_id), '{}'::text[]) AS kontorer,
    CASE
        WHEN rt.svarfrist IS NOT NULL AND rt.svarfrist < now() THEN true
        ELSE false
    END AS frist_utgatt,
    (SELECT count(*) FROM arbeidsgiver a WHERE a.rekrutteringstreff_id = rt.rekrutteringstreff_id) AS antall_arbeidsgivere,
    (SELECT count(*) FROM jobbsoker j WHERE j.rekrutteringstreff_id = rt.rekrutteringstreff_id AND j.status != 'SLETTET' AND j.er_synlig = true) AS antall_jobbsokere,
    (SELECT count(*) FROM jobbsoker j WHERE j.rekrutteringstreff_id = rt.rekrutteringstreff_id AND j.status = 'SVART_JA' AND j.er_synlig = true) AS antall_jobbsokere_svart_ja,
    (SELECT count(*) FROM jobbsoker j WHERE j.rekrutteringstreff_id = rt.rekrutteringstreff_id AND j.status = 'FÅTT_JOBB' AND j.er_synlig = true) AS antall_jobbsokere_fatt_jobb,
    rt.kategori,
    rt.kommunenummer,
    rt.fylkesnummer,
    COALESCE((SELECT jsonb_agg(jsonb_build_object(
                  'navIdent', e.nav_ident,
                  'eierNavn', e.eier_navn,
                  'kontorEnhetId', e.kontor_enhetid
              ) ORDER BY e.rekrutteringstreff_eier_id)
              FROM rekrutteringstreff_eier e
              WHERE e.rekrutteringstreff_id = rt.rekrutteringstreff_id), '[]'::jsonb) AS eier_og_kontor
FROM rekrutteringstreff rt
WHERE rt.status != 'SLETTET';
