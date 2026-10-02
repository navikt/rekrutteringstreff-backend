update jobbsoker
set aktuell_for_treff_status = 'VURDERES'
where aktuell_for_treff_status is null;

alter table jobbsoker
    alter column aktuell_for_treff_status set default 'VURDERES',
    alter column aktuell_for_treff_status set not null;