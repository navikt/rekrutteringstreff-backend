SELECT *
FROM
    EXTERNAL_QUERY(
            "toi-prod-324e.europe-north1.rekrutteringstreff-api",
            '''
            SELECT kontor_enhetid,
                   DATE(MIN(fullfoert_treff_dato)) AS foerste_fullfoerte_treff_dato,
                   DATE(MAX(fullfoert_treff_dato)) AS siste_fullfoerte_treff_dato,
                   SUM(utkast)                     AS antall_treff_med_status_utkast,
                   SUM(publisert)                  AS antall_treff_med_status_publisert,
                   SUM(fullfoert)                  AS antall_treff_med_status_fullfoert,
                   SUM(avlyst)                     AS antall_treff_med_status_avlyst,
                   SUM(slettet)                    AS antall_treff_med_status_slettet
            FROM (SELECT rt.opprettet_av_kontor_enhetid                             AS kontor_enhetid,
                         v.fra_tid,
                         CASE WHEN v.status = 'FULLFØRT' THEN fra_tid ELSE NULL END AS fullfoert_treff_dato,
                         CASE WHEN v.status = 'UTKAST' THEN 1 ELSE 0 END            AS utkast,
                         CASE WHEN v.status = 'PUBLISERT' THEN 1 ELSE 0 END         AS publisert,
                         CASE WHEN v.status = 'FULLFØRT' THEN 1 ELSE 0 END          AS fullfoert,
                         CASE WHEN v.status = 'AVLYST' THEN 1 ELSE 0 END            AS avlyst,
                         CASE WHEN v.status = 'SLETTET' THEN 1 ELSE 0 END           AS slettet
                  FROM rekrutteringstreff_sok_view AS v
                           INNER JOIN rekrutteringstreff AS rt ON v.id = rt.id)
            GROUP BY kontor_enhetid
         ''');

