SET @@location = 'europe-north1';

CREATE OR REPLACE TABLE `toi-prod-324e.rekrutteringstreff.tellinger_per_mnd` AS
SELECT *
FROM
    EXTERNAL_QUERY(
            "toi-prod-324e.europe-north1.rekrutteringstreff-api",
            '''
    WITH treff AS (
        SELECT rekrutteringstreff_id,
               date_trunc('month', tiltid AT TIME ZONE 'Europe/Oslo')::date AS maaned
        FROM rekrutteringstreff
        WHERE status = 'FULLFØRT'
    ),
    deltakende_arbeidsgiver AS (
         SELECT t.maaned,
                EXISTS (
                    SELECT 1
                    FROM formidling f
                    WHERE f.arbeidsgiver_id = ag.arbeidsgiver_id
                      AND f.slettet_tidspunkt IS NULL
                ) AS har_rekruttert
         FROM treff t
                  JOIN arbeidsgiver ag ON ag.rekrutteringstreff_id = t.rekrutteringstreff_id
         WHERE ag.status = 'AKTIV'
     ),
     arbeidsgivere_per_maaned AS (
         SELECT maaned,
                count(*)                               AS antall_arbeidsgivere,
                count(*) FILTER (WHERE har_rekruttert) AS antall_arbeidsgivere_som_har_rekruttert
         FROM deltakende_arbeidsgiver
         GROUP BY maaned
     ),
     jobbsokere_per_maaned AS (
         SELECT t.maaned,
                count(*) FILTER (WHERE js.status IN ('SVART_JA', 'MØTT_OPP', 'FÅTT_JOBB')) AS antall_svart_ja
         FROM treff t
                  JOIN jobbsoker js ON js.rekrutteringstreff_id = t.rekrutteringstreff_id
         GROUP BY t.maaned
     ),
     treff_per_maaned AS (
         SELECT t.maaned,
                count(DISTINCT t.rekrutteringstreff_id) AS antall_treff,
                count(f.formidling_id)                  AS antall_ansettelser,
                max(f.opprettet_tidspunkt)              AS siste_formidling
         FROM treff t
                  LEFT JOIN formidling f
                            ON f.rekrutteringstreff_id = t.rekrutteringstreff_id
                                AND f.slettet_tidspunkt IS NULL
         GROUP BY t.maaned
     )
SELECT to_char(tm.maaned, 'YYYY-MM')                     AS maaned,
       tm.antall_treff,
       coalesce(jm.antall_svart_ja, 0)                   AS antall_svart_ja,
       coalesce(am.antall_arbeidsgivere, 0)              AS antall_arbeidsgivere,
       coalesce(am.antall_arbeidsgivere_som_har_rekruttert, 0)         AS antall_arbeidsgivere_som_har_rekruttert,
       round(100.0 * am.antall_arbeidsgivere_som_har_rekruttert
                 / nullif(am.antall_arbeidsgivere, 0), 1)    AS andel_prosent,
       tm.antall_ansettelser,
       (tm.siste_formidling AT TIME ZONE 'Europe/Oslo')::date AS siste_formidling_dato
FROM treff_per_maaned tm
         LEFT JOIN arbeidsgivere_per_maaned am ON am.maaned = tm.maaned
         LEFT JOIN jobbsokere_per_maaned jm ON jm.maaned = tm.maaned
ORDER BY tm.maaned;
         ''');
