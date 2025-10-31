package no.nav.toi.jobbsoker.dto

import java.time.ZonedDateTime

data class JobbsøkerHendelseMedJobbsøkerDataOutboundDto(
    val id: String,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktørIdentifikasjon: String?,
    val fødselsnummer: String,
    val kandidatnummer: String?,
    val fornavn: String,
    val etternavn: String,
    val personTreffId: String
)