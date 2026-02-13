package no.nav.toi.jobbsoker.dto

import io.javalin.openapi.OpenApiNullable
import java.time.ZonedDateTime

data class JobbsøkerHendelseOutboundDto(
    val id: String,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktørIdentifikasjon: String?,
    @get:OpenApiNullable
    val hendelseData: HendelseDataDto? = null
)
