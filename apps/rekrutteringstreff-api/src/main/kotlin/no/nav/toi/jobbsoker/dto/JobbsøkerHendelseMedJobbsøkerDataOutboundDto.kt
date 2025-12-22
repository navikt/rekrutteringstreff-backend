package no.nav.toi.jobbsoker.dto

import com.fasterxml.jackson.databind.JsonNode
import java.time.ZonedDateTime

data class JobbsøkerHendelseMedJobbsøkerDataOutboundDto(
    val id: String,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktørIdentifikasjon: String?,
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String,
    val personTreffId: String,
    val hendelseData: JsonNode? = null
)