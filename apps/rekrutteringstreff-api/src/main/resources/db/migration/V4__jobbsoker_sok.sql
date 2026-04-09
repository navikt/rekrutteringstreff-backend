CREATE INDEX idx_jobbsoker_fornavn
    ON jobbsoker (rekrutteringstreff_id, LOWER(fornavn) text_pattern_ops)
    WHERE er_synlig = true AND status != 'SLETTET';

CREATE INDEX idx_jobbsoker_etternavn
    ON jobbsoker (rekrutteringstreff_id, LOWER(etternavn) text_pattern_ops)
    WHERE er_synlig = true AND status != 'SLETTET';

CREATE INDEX idx_jobbsoker_sok_aktiv
    ON jobbsoker (rekrutteringstreff_id) WHERE er_synlig = true AND status != 'SLETTET';

CREATE INDEX idx_jobbsoker_sok_treff_status
    ON jobbsoker (rekrutteringstreff_id, status) WHERE er_synlig = true AND status != 'SLETTET';

CREATE INDEX idx_jobbsoker_hendelse_opprettet
    ON jobbsoker_hendelse (jobbsoker_id, tidspunkt ASC)
    WHERE hendelsestype = 'OPPRETTET';
