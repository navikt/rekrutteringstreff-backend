ALTER TABLE jobbsoker
    ADD COLUMN id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE jobbsoker
    ALTER COLUMN id DROP DEFAULT;

ALTER TABLE arbeidsgiver
    ADD COLUMN id uuid NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE arbeidsgiver
    ALTER COLUMN id DROP DEFAULT;
