-- Alle instanser skal kjøre dual write (fase 2) før denne migreringen deployes.
-- Flyway holder transaksjonen og låsene gjennom begge datastegene.
SET LOCAL lock_timeout = '10s';

-- Samme låserekkefølge som skriverne: treff før eierrader.
-- ACCESS EXCLUSIVE blokkerer også SELECT FOR UPDATE og vanlige lesere.
LOCK TABLE rekrutteringstreff IN ACCESS EXCLUSIVE MODE;
LOCK TABLE rekrutteringstreff_eier IN ACCESS EXCLUSIVE MODE;

INSERT INTO rekrutteringstreff_eier (
    rekrutteringstreff_id, nav_ident, kontor_enhetid, lagt_til_tidspunkt, lagt_til_av
)
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
         ORDER BY h.tidspunkt DESC, h.rekrutteringstreff_hendelse_id DESC
         LIMIT 1),
        CASE WHEN e.nav_ident = rt.opprettet_av_person_navident
             THEN rt.opprettet_av_kontor_enhetid END
    ),
    coalesce(
        (SELECT min(h.tidspunkt)
         FROM rekrutteringstreff_hendelse h
         WHERE h.rekrutteringstreff_id = rt.rekrutteringstreff_id
           AND h.hendelsestype = 'EIER_LAGT_TIL'
           AND h.subjekt_id = e.nav_ident),
        rt.opprettet_av_tidspunkt
    ),
    'migrering-V16'
FROM rekrutteringstreff rt
CROSS JOIN LATERAL unnest(rt.eiere) AS e(nav_ident)
WHERE e.nav_ident IS NOT NULL
ON CONFLICT (rekrutteringstreff_id, nav_ident) DO NOTHING;

-- Bare entydige kontorer fylles. Resten avklares per eierrad i fase 4.
UPDATE rekrutteringstreff_eier e
SET kontor_enhetid = k.enhetid
FROM rekrutteringstreff rt
CROSS JOIN LATERAL unnest(array_remove(rt.kontorer, NULL)) AS k(enhetid)
WHERE e.rekrutteringstreff_id = rt.rekrutteringstreff_id
  AND e.kontor_enhetid IS NULL
  AND array_length(array_remove(rt.kontorer, NULL), 1) = 1;

-- Ved feil ruller Flyway tilbake hele migreringen. Etter fullføring beholdes
-- radene ved kode-rollback; arrayene er fortsatt fasit frem til fase 5.
