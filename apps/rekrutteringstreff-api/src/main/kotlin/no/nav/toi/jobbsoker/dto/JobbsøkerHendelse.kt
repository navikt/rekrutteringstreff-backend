package no.nav.toi.jobbsoker.dto

import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import java.time.ZonedDateTime
import java.util.UUID

data class JobbsøkerHendelse(
    val id: UUID,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: JobbsøkerHendelsestype,
    val opprettetAvAktørType: AktørType,
    val aktørIdentifikasjon: String?
)

fun JobbsøkerHendelse.toOutboundDto() = JobbsøkerHendelseOutboundDto(
    id = id.toString(),
    tidspunkt = tidspunkt,
    hendelsestype = hendelsestype.name,
    opprettetAvAktørType = opprettetAvAktørType.name,
    aktørIdentifikasjon = aktørIdentifikasjon
)