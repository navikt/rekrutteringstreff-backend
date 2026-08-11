CREATE TABLE delt_stilling(
                                   db_id                   bigserial               PRIMARY KEY,
                                   aktivitetskort_id       UUID                    NOT NULL,
                                   fnr                     TEXT                    NOT NULL,
                                   stilling_id             UUID                    NOT NULL,
                                   UNIQUE (stilling_id, fnr)
);
CREATE INDEX idx_delt_stilling_aktivitetskort_id ON delt_stilling (aktivitetskort_id);
