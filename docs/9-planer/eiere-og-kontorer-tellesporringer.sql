-- Tellespørringer for fase 1 av
-- docs/9-planer/eiere-og-kontorer-egen-tabell.md
--
-- Formål: tallfeste konsekvensene av å flytte eiere og kontorer ut i
-- rekrutteringstreff_eier, der kontor er avledet av eierskap.
--
-- Koblingen eier -> kontor gjenskapes fra to kilder:
--   1. KONTOR_LAGT_TIL-hendelser (aktøridentifikasjon = eier, subjekt_id = kontor)
--   2. opprettet_av_person_navident + opprettet_av_kontor_enhetid på treffraden
--
-- Hver spørring er frittstående og kan kjøres alene — `backfill`-CTE-en er
-- bevisst gjentatt slik at ingen av dem krever oppsett på forhånd. Ingenting
-- opprettes i databasen; alt er rene SELECT-er som kan kjøres med read only-bruker.
--
-- Spørring 4 returnerer Nav-identer og enhetIder — ikke lim resultatet inn i
-- åpne kanaler.
--
-- Kjøring mot prod (krever naisdevice + tilgang):
--   nais postgres proxy rekrutteringstreff-api -c prod-gcp
--   psql -h localhost -p 5432 -U <bruker> rekrutteringstreff-api \
--        -f docs/9-planer/eiere-og-kontorer-tellesporringer.sql
--
-- Kjør gjerne i dev-gcp først for å verifisere at spørringene går gjennom.
--
-- MERK: treff med tomt `eiere`-array faller ut av `backfill` (unnest gir null
-- rader) og telles derfor ikke i spørring 1-4. Spørring 5 viser hvor mange
-- slike treff som finnes.


-- =============================================================================
-- 1. Hovedtall: hvor god blir gjenskapingen av eier -> kontor?
-- =============================================================================
WITH backfill AS (
    SELECT
        rt.rekrutteringstreff_id,
        e.nav_ident,
        coalesce(
            (SELECT h.subjekt_id
             FROM rekrutteringstreff_hendelse h
             WHERE h.rekrutteringstreff_id = rt.rekrutteringstreff_id
               AND h.hendelsestype = 'KONTOR_LAGT_TIL'
               AND h.aktøridentifikasjon = e.nav_ident
               AND h.subjekt_id IS NOT NULL
             ORDER BY h.tidspunkt DESC
             LIMIT 1),
            CASE WHEN e.nav_ident = rt.opprettet_av_person_navident
                 THEN rt.opprettet_av_kontor_enhetid END
        ) AS kontor_enhetid
    FROM rekrutteringstreff rt
    CROSS JOIN LATERAL unnest(rt.eiere) AS e(nav_ident)
    WHERE rt.status <> 'SLETTET'
      AND e.nav_ident IS NOT NULL
)
SELECT
    count(*)                                                        AS eierrader_totalt,
    count(kontor_enhetid)                                           AS med_kontor,
    count(*) - count(kontor_enhetid)                                AS uten_kontor,
    round(100.0 * count(kontor_enhetid) / nullif(count(*), 0), 1)   AS prosent_med_kontor
FROM backfill;


-- =============================================================================
-- 2. Kontorer som forsvinner fra treffet
--    Viktigste spørringen: kontorer som i dag ligger i kontorer[] men som ingen
--    gjenværende eier er knyttet til. Disse gir i dag kontorbasert tilgang
--    (EierService.harTilgangViaTreffkontor) som forsvinner ved migrering.
-- =============================================================================
WITH backfill AS (
    SELECT
        rt.rekrutteringstreff_id,
        rt.kontorer AS kontorer_i_dag,
        coalesce(
            (SELECT h.subjekt_id
             FROM rekrutteringstreff_hendelse h
             WHERE h.rekrutteringstreff_id = rt.rekrutteringstreff_id
               AND h.hendelsestype = 'KONTOR_LAGT_TIL'
               AND h.aktøridentifikasjon = e.nav_ident
               AND h.subjekt_id IS NOT NULL
             ORDER BY h.tidspunkt DESC
             LIMIT 1),
            CASE WHEN e.nav_ident = rt.opprettet_av_person_navident
                 THEN rt.opprettet_av_kontor_enhetid END
        ) AS kontor_enhetid
    FROM rekrutteringstreff rt
    CROSS JOIN LATERAL unnest(rt.eiere) AS e(nav_ident)
    WHERE rt.status <> 'SLETTET'
      AND e.nav_ident IS NOT NULL
), per_treff AS (
    SELECT
        rekrutteringstreff_id,
        kontorer_i_dag,
        array_remove(array_agg(DISTINCT kontor_enhetid), NULL) AS kontorer_etter
    FROM backfill
    GROUP BY rekrutteringstreff_id, kontorer_i_dag
)
SELECT
    count(*)                                                              AS treff_totalt,
    count(*) FILTER (WHERE coalesce(array_length(kontorer_i_dag, 1), 0) > 0
                       AND coalesce(array_length(kontorer_etter, 1), 0) = 0)
                                                                          AS treff_som_mister_alle_kontorer,
    count(*) FILTER (WHERE EXISTS (
        SELECT 1 FROM unnest(kontorer_i_dag) AS k(enhetid)
        WHERE k.enhetid IS NOT NULL AND NOT (k.enhetid = ANY (kontorer_etter))
    ))                                                                    AS treff_som_mister_minst_ett_kontor,
    coalesce(sum((
        SELECT count(*) FROM unnest(kontorer_i_dag) AS k(enhetid)
        WHERE k.enhetid IS NOT NULL AND NOT (k.enhetid = ANY (kontorer_etter))
    )), 0)                                                                AS kontorkoblinger_som_forsvinner
FROM per_treff;


-- =============================================================================
-- 3. Fordeling av årsak til manglende kontor
--    Skiller "eier nr. 2 fra samme kontor" (ufarlig — kontoret lever videre via
--    en annen eier) fra reelt tap.
-- =============================================================================
WITH backfill AS (
    SELECT
        rt.rekrutteringstreff_id,
        coalesce(
            (SELECT h.subjekt_id
             FROM rekrutteringstreff_hendelse h
             WHERE h.rekrutteringstreff_id = rt.rekrutteringstreff_id
               AND h.hendelsestype = 'KONTOR_LAGT_TIL'
               AND h.aktøridentifikasjon = e.nav_ident
               AND h.subjekt_id IS NOT NULL
             ORDER BY h.tidspunkt DESC
             LIMIT 1),
            CASE WHEN e.nav_ident = rt.opprettet_av_person_navident
                 THEN rt.opprettet_av_kontor_enhetid END
        ) AS kontor_enhetid
    FROM rekrutteringstreff rt
    CROSS JOIN LATERAL unnest(rt.eiere) AS e(nav_ident)
    WHERE rt.status <> 'SLETTET'
      AND e.nav_ident IS NOT NULL
), per_treff AS (
    SELECT
        rekrutteringstreff_id,
        array_remove(array_agg(DISTINCT kontor_enhetid), NULL) AS kontorer_etter
    FROM backfill
    GROUP BY rekrutteringstreff_id
)
SELECT
    count(*) FILTER (WHERE b.kontor_enhetid IS NOT NULL)          AS kontor_gjenskapt,
    count(*) FILTER (WHERE b.kontor_enhetid IS NULL
                       AND coalesce(array_length(p.kontorer_etter, 1), 0) > 0)
                                                                  AS uten_kontor_men_treffet_har_kontor,
    count(*) FILTER (WHERE b.kontor_enhetid IS NULL
                       AND coalesce(array_length(p.kontorer_etter, 1), 0) = 0)
                                                                  AS uten_kontor_og_treffet_star_uten
FROM backfill b
JOIN per_treff p USING (rekrutteringstreff_id);


-- =============================================================================
-- 4. Detaljer for manuell gjennomgang (begrenset)
--    Bruk hvis spørring 2 gir et håndterbart antall treff — da kan kontorene
--    eventuelt rettes manuelt, eller berørte kontorer varsles.
--    NB: returnerer Nav-identer.
-- =============================================================================
WITH backfill AS (
    SELECT
        rt.rekrutteringstreff_id,
        rt.id     AS treff_id,
        rt.status,
        rt.kontorer AS kontorer_i_dag,
        e.nav_ident,
        coalesce(
            (SELECT h.subjekt_id
             FROM rekrutteringstreff_hendelse h
             WHERE h.rekrutteringstreff_id = rt.rekrutteringstreff_id
               AND h.hendelsestype = 'KONTOR_LAGT_TIL'
               AND h.aktøridentifikasjon = e.nav_ident
               AND h.subjekt_id IS NOT NULL
             ORDER BY h.tidspunkt DESC
             LIMIT 1),
            CASE WHEN e.nav_ident = rt.opprettet_av_person_navident
                 THEN rt.opprettet_av_kontor_enhetid END
        ) AS kontor_enhetid
    FROM rekrutteringstreff rt
    CROSS JOIN LATERAL unnest(rt.eiere) AS e(nav_ident)
    WHERE rt.status <> 'SLETTET'
      AND e.nav_ident IS NOT NULL
), per_treff AS (
    SELECT
        treff_id,
        status,
        kontorer_i_dag,
        array_remove(array_agg(DISTINCT kontor_enhetid), NULL) AS kontorer_etter,
        array_agg(DISTINCT nav_ident)                          AS eiere
    FROM backfill
    GROUP BY treff_id, status, kontorer_i_dag
)
SELECT
    treff_id,
    status,
    eiere,
    kontorer_i_dag,
    kontorer_etter,
    array(
        SELECT k.enhetid FROM unnest(kontorer_i_dag) AS k(enhetid)
        WHERE k.enhetid IS NOT NULL AND NOT (k.enhetid = ANY (kontorer_etter))
    ) AS kontorer_som_forsvinner
FROM per_treff
WHERE EXISTS (
    SELECT 1 FROM unnest(kontorer_i_dag) AS k(enhetid)
    WHERE k.enhetid IS NOT NULL AND NOT (k.enhetid = ANY (kontorer_etter))
)
ORDER BY treff_id
LIMIT 100;


-- =============================================================================
-- 5. Sanity: datakvalitet i dagens arrays
--    opprett() setter kontorer = ARRAY[opprettetAvNavkontorEnhetId], som kan
--    være NULL — da havner et NULL-element i arrayet.
-- =============================================================================
SELECT
    count(*) FILTER (WHERE rt.kontorer IS NULL)                    AS kontorer_er_null,
    count(*) FILTER (WHERE array_length(rt.kontorer, 1) IS NULL)   AS kontorer_tom_eller_null,
    count(*) FILTER (WHERE array_position(rt.kontorer, NULL) IS NOT NULL)
                                                                   AS kontorer_har_null_element,
    count(*) FILTER (WHERE array_length(rt.eiere, 1) IS NULL)      AS eiere_tom,
    count(*) FILTER (WHERE rt.opprettet_av_person_navident IS NOT NULL
                       AND NOT (rt.opprettet_av_person_navident = ANY (rt.eiere)))
                                                                   AS oppretter_ikke_lenger_eier
FROM rekrutteringstreff rt
WHERE rt.status <> 'SLETTET';


-- =============================================================================
-- 6. Kan kontor_enhetid gjøres NOT NULL?
--    Eierrader uten gjenskapt kontor oppstår primært fordi kontoret eieren
--    brakte med seg allerede lå på treffet (leggTilKontor ga false). Eierens
--    kontor er derfor ett av treffets kontorer — og er det bare ett, er
--    tilordningen entydig.
--
--    Er `uten_kontor_men_ett_entydig_kontor` = `uten_kontor_totalt`, kan alle
--    hullene fylles deterministisk og kolonnen settes NOT NULL.
-- =============================================================================
WITH backfill AS (
    SELECT
        rt.rekrutteringstreff_id,
        rt.kontorer AS kontorer_i_dag,
        coalesce(
            (SELECT h.subjekt_id
             FROM rekrutteringstreff_hendelse h
             WHERE h.rekrutteringstreff_id = rt.rekrutteringstreff_id
               AND h.hendelsestype = 'KONTOR_LAGT_TIL'
               AND h.aktøridentifikasjon = e.nav_ident
               AND h.subjekt_id IS NOT NULL
             ORDER BY h.tidspunkt DESC
             LIMIT 1),
            CASE WHEN e.nav_ident = rt.opprettet_av_person_navident
                 THEN rt.opprettet_av_kontor_enhetid END
        ) AS kontor_enhetid
    FROM rekrutteringstreff rt
    CROSS JOIN LATERAL unnest(rt.eiere) AS e(nav_ident)
    WHERE rt.status <> 'SLETTET'
      AND e.nav_ident IS NOT NULL
)
SELECT
    count(*) FILTER (WHERE kontor_enhetid IS NULL)                       AS uten_kontor_totalt,
    count(*) FILTER (WHERE kontor_enhetid IS NULL
                       AND array_length(array_remove(kontorer_i_dag, NULL), 1) = 1)
                                                                         AS uten_kontor_men_ett_entydig_kontor,
    count(*) FILTER (WHERE kontor_enhetid IS NULL
                       AND array_length(array_remove(kontorer_i_dag, NULL), 1) > 1)
                                                                         AS uten_kontor_og_flere_kontorer,
    count(*) FILTER (WHERE kontor_enhetid IS NULL
                       AND coalesce(array_length(array_remove(kontorer_i_dag, NULL), 1), 0) = 0)
                                                                         AS uten_kontor_og_treffet_har_ingen
FROM backfill;


-- =============================================================================
-- 7. De tvetydige radene som må avklares manuelt
--    Eierrader uten gjenskapt kontor på treff med flere enn ett kontor
--    (`uten_kontor_og_flere_kontorer` i spørring 6 — målt til 20).
--    Riktig kontor er ett av verdiene i `kandidatkontorer`.
--
--    NB: returnerer Nav-identer. Behandle resultatet deretter.
-- =============================================================================
WITH backfill AS (
    SELECT
        rt.rekrutteringstreff_id,
        rt.id     AS treff_id,
        rt.tittel,
        rt.status,
        rt.opprettet_av_person_navident,
        rt.opprettet_av_kontor_enhetid,
        array_remove(rt.kontorer, NULL) AS kandidatkontorer,
        e.nav_ident,
        coalesce(
            (SELECT h.subjekt_id
             FROM rekrutteringstreff_hendelse h
             WHERE h.rekrutteringstreff_id = rt.rekrutteringstreff_id
               AND h.hendelsestype = 'KONTOR_LAGT_TIL'
               AND h.aktøridentifikasjon = e.nav_ident
               AND h.subjekt_id IS NOT NULL
             ORDER BY h.tidspunkt DESC
             LIMIT 1),
            CASE WHEN e.nav_ident = rt.opprettet_av_person_navident
                 THEN rt.opprettet_av_kontor_enhetid END
        ) AS kontor_enhetid
    FROM rekrutteringstreff rt
    CROSS JOIN LATERAL unnest(rt.eiere) AS e(nav_ident)
    WHERE rt.status <> 'SLETTET'
      AND e.nav_ident IS NOT NULL
)
SELECT
    b.nav_ident,
    b.treff_id,
    b.tittel,
    b.status,
    b.kandidatkontorer,
    b.opprettet_av_kontor_enhetid                       AS treffets_oppretterkontor,
    (b.nav_ident = b.opprettet_av_person_navident)      AS er_oppretter,
    (SELECT min(h.tidspunkt)
     FROM rekrutteringstreff_hendelse h
     WHERE h.rekrutteringstreff_id = b.rekrutteringstreff_id
       AND h.hendelsestype = 'EIER_LAGT_TIL'
       AND h.subjekt_id = b.nav_ident)                  AS ble_eier_tidspunkt
FROM backfill b
WHERE b.kontor_enhetid IS NULL
  AND array_length(b.kandidatkontorer, 1) > 1
ORDER BY b.nav_ident, b.treff_id;


-- =============================================================================
-- 8. Unike identer å slå opp
--    Samme person kan eie flere treff. Denne gir én rad per ident, slik at
--    kontoret bare trenger å slås opp én gang per person.
-- =============================================================================
WITH backfill AS (
    SELECT
        rt.rekrutteringstreff_id,
        rt.id AS treff_id,
        array_remove(rt.kontorer, NULL) AS kandidatkontorer,
        e.nav_ident,
        coalesce(
            (SELECT h.subjekt_id
             FROM rekrutteringstreff_hendelse h
             WHERE h.rekrutteringstreff_id = rt.rekrutteringstreff_id
               AND h.hendelsestype = 'KONTOR_LAGT_TIL'
               AND h.aktøridentifikasjon = e.nav_ident
               AND h.subjekt_id IS NOT NULL
             ORDER BY h.tidspunkt DESC
             LIMIT 1),
            CASE WHEN e.nav_ident = rt.opprettet_av_person_navident
                 THEN rt.opprettet_av_kontor_enhetid END
        ) AS kontor_enhetid
    FROM rekrutteringstreff rt
    CROSS JOIN LATERAL unnest(rt.eiere) AS e(nav_ident)
    WHERE rt.status <> 'SLETTET'
      AND e.nav_ident IS NOT NULL
), tvetydige AS (
    SELECT nav_ident, treff_id, kandidatkontorer
    FROM backfill
    WHERE kontor_enhetid IS NULL
      AND array_length(kandidatkontorer, 1) > 1
)
SELECT
    t.nav_ident,
    count(*)                                          AS antall_rader_a_fylle,
    array_agg(DISTINCT t.treff_id)                    AS treff,
    (SELECT array_agg(DISTINCT k.enhetid)
     FROM tvetydige t2
     CROSS JOIN LATERAL unnest(t2.kandidatkontorer) AS k(enhetid)
     WHERE t2.nav_ident = t.nav_ident)                AS mulige_kontorer,
    -- Kontor personen er kjent med fra ANDRE treff, der koblingen finnes.
    -- Er det nøyaktig ett, er det en sterk indikasjon på riktig svar.
    (SELECT array_agg(DISTINCT b2.kontor_enhetid)
     FROM backfill b2
     WHERE b2.nav_ident = t.nav_ident
       AND b2.kontor_enhetid IS NOT NULL)             AS kjent_kontor_fra_andre_treff
FROM tvetydige t
GROUP BY t.nav_ident
ORDER BY t.nav_ident;
