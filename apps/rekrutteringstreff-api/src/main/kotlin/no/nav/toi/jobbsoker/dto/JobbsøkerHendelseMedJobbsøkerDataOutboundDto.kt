package no.nav.toi.jobbsoker.dto

import io.javalin.openapi.OpenApiNullable
import io.javalin.openapi.OneOf
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
    @get:OpenApiNullable
    @get:OneOf(MinsideVarselSvarDataDto::class, RekrutteringstreffendringerDto::class)
    val hendelseData: Any? = null
)