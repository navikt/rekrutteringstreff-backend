create table aktivitetskort_polling (
   db_id  BIGSERIAL PRIMARY KEY,
   jobbsoker_hendelse_db_id    bigint  NOT NULL,
   sendt_tidspunkt  timestamp with time zone not null,
   FOREIGN KEY (jobbsoker_hendelse_db_id) REFERENCES jobbsoker_hendelse (db_id) ON DELETE CASCADE
);