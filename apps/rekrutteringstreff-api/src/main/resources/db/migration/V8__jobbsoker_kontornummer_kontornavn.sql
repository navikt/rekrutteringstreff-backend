alter table jobbsoker
  add column kontornummer text;

alter table jobbsoker
  rename column navkontor to kontornavn;
