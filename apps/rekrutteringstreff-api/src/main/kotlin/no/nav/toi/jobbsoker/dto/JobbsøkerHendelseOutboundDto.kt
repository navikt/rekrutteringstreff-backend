package no.nav.toi.jobbsoker.dto

import com.fasterxml.jackson.databind.JsonNode
import java.time.ZonedDateTime

data class JobbsøkerHendelseOutboundDto(
    val id: String,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktørIdentifikasjon: String?,
    val hendelseData: JsonNode? = null
)
