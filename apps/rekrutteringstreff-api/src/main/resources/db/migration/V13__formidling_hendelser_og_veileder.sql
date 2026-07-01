create table formidling_hendelse
(
    formidling_hendelse_id bigserial primary key,
    id                     uuid                     unique not null,
    formidling_id          bigint                   not null,
    hendelsestype          text                     not null,
    tidspunkt              timestamp with time zone not null,
    opprettet_av_aktortype text                     NOT NULL,
    aktøridentifikasjon    text,
    hendelse_data          jsonb,
    CONSTRAINT formidling_hendelse_formidling_fk
        FOREIGN KEY (formidling_id) REFERENCES formidling (formidling_id)
);

CREATE INDEX idx_formidling_hendelse_formidling_id ON formidling_hendelse (formidling_id);

alter table formidling
  add column opprettet_av_veileder_navident text,
  add column opprettet_av_veileder_navn text;
