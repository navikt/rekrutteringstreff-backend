ALTER TABLE rekrutteringstreff
    ADD COLUMN sok_tsv tsvector GENERATED ALWAYS AS (
        setweight(to_tsvector('norwegian'::regconfig, coalesce(tittel, '')), 'A') ||
        setweight(to_tsvector('norwegian'::regconfig,
                              coalesce(poststed, '') || ' ' || coalesce(gateadresse, '') || ' ' ||
                              coalesce(kommune, '')  || ' ' || coalesce(fylke, '')), 'B') ||
        setweight(to_tsvector('norwegian'::regconfig,
                              left(regexp_replace(coalesce(beskrivelse, ''), '<[^>]*>', ' ', 'g'), 100000)), 'C')
        ) STORED;

CREATE INDEX idx_rekrutteringstreff_sok_tsv
    ON rekrutteringstreff USING GIN (sok_tsv) WITH (fastupdate = off);

ALTER TABLE arbeidsgiver
    ADD COLUMN sok_tsv tsvector GENERATED ALWAYS AS (
        to_tsvector('norwegian'::regconfig,
                    coalesce(orgnavn, '') || ' ' || coalesce(orgnr, '') || ' ' || coalesce(poststed, ''))
        ) STORED;

CREATE INDEX idx_arbeidsgiver_sok_tsv
    ON arbeidsgiver USING GIN (sok_tsv) WITH (fastupdate = off)
    WHERE status = 'AKTIV';