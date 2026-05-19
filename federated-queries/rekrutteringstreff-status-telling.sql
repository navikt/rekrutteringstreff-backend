SELECT *
FROM
    EXTERNAL_QUERY(
            "toi-prod-324e.europe-north1.rekrutteringstreff-api",
            '''
            SELECT rt.opprettet_av_kontor_enhetid,
               v.status,
               v.kontorer,
               v.frist_utgatt,
               v.antall_arbeidsgivere,
               v.antall_jobbsokere,
               v.antall_jobbsokere_svart_ja
            FROM rekrutteringstreff_sok_view AS v
                 INNER JOIN rekrutteringstreff AS rt ON v.id = rt.id;
         ''');
