-- Legg til søkekolonner direkte på jobbsoker-tabellen
ALTER TABLE jobbsoker ADD COLUMN lagt_til_dato timestamptz DEFAULT now();
ALTER TABLE jobbsoker ADD COLUMN lagt_til_av text;

-- Backfill lagt_til_dato og lagt_til_av fra hendelsestabellen
UPDATE jobbsoker js SET
    lagt_til_dato = COALESCE(
        (SELECT MIN(jh.tidspunkt)
         FROM jobbsoker_hendelse jh
         WHERE jh.jobbsoker_id = js.jobbsoker_id AND jh.hendelsestype = 'OPPRETTET'),
        now()
    ),
    lagt_til_av = COALESCE(
        (SELECT jh.aktøridentifikasjon
         FROM jobbsoker_hendelse jh
         WHERE jh.jobbsoker_id = js.jobbsoker_id AND jh.hendelsestype = 'OPPRETTET'
         ORDER BY jh.tidspunkt ASC LIMIT 1),
        'ukjent'
    );

ALTER TABLE jobbsoker ALTER COLUMN lagt_til_dato SET NOT NULL;
ALTER TABLE jobbsoker ALTER COLUMN lagt_til_av SET NOT NULL;

-- B-tree indekser for prefikssøk på fornavn og etternavn (LIKE 'xxx%')
CREATE INDEX idx_jobbsoker_fornavn ON jobbsoker (LOWER(fornavn) text_pattern_ops);
CREATE INDEX idx_jobbsoker_etternavn ON jobbsoker (LOWER(etternavn) text_pattern_ops);

-- Partiell indeks for aktive jobbsøkere per treff
CREATE INDEX idx_jobbsoker_sok_aktiv
    ON jobbsoker (rekrutteringstreff_id) WHERE er_synlig = true AND status != 'SLETTET';

-- Komposittindeks for status-filtrering per treff
CREATE INDEX idx_jobbsoker_sok_treff_status
    ON jobbsoker (rekrutteringstreff_id, status) WHERE er_synlig = true;
