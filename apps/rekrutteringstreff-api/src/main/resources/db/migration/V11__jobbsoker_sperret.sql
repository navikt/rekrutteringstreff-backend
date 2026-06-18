ALTER TABLE jobbsoker
    ADD COLUMN sperret boolean NOT NULL DEFAULT FALSE;

-- Backfill av sperret for eksisterende jobbsøkere.
-- sperret kan kun være true for rader som allerede er er_synlig = false
-- (adressebeskyttelse og kode 6/7 tvinger erSynlig() = false i synlighetsmotor),
-- så vi re-evaluerer kun disse radene.
--
-- Ved å nulle synlighet_sist_oppdatert plukker SynlighetsBehovScheduler radene opp
-- og trigger need-meldinger på nytt. Need-svaret skriver da er_synlig + sperret.
UPDATE jobbsoker
SET synlighet_sist_oppdatert = NULL,
    synlighet_kilde = NULL
WHERE er_synlig = false
  AND status != 'SLETTET'
  AND synlighet_sist_oppdatert IS NOT NULL;
