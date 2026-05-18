package no.nav.toi.jobbsoker.dto

import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.PersonTreffId
import java.time.ZonedDateTime
import java.util.UUID

data class JobbsøkerHendelseMedJobbsøkerData(
    val id: UUID,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: JobbsøkerHendelsestype,
    val opprettetAvAktørType: AktørType,
    val aktørIdentifikasjon: String?,
    val fødselsnummer: Fødselsnummer,
    val fornavn: Fornavn,
    val etternavn: Etternavn,
    val personTreffId: PersonTreffId,
    val hendelseData: HendelseDataDto? = null
)