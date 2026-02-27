package no.nav.toi.jobbsoker.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.openapi.OneOf
import io.javalin.openapi.OpenApiName
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.rekrutteringstreff.Endringsfelttype

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes(
    JsonSubTypes.Type(MinsideVarselSvarDataDto::class),
    JsonSubTypes.Type(RekrutteringstreffendringerDto::class),
)
@OneOf(MinsideVarselSvarDataDto::class, RekrutteringstreffendringerDto::class)
sealed interface HendelseDataDto

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@OpenApiName("MinsideVarselSvarData")
data class MinsideVarselSvarDataDto(
    val varselId: String? = null,
    val avsenderReferanseId: String? = null,
    val fnr: String? = null,
    val eksternStatus: String? = null,
    val minsideStatus: String? = null,
    val opprettet: String? = null,
    val avsenderNavident: String? = null,
    val eksternFeilmelding: String? = null,
    val eksternKanal: String? = null,
    val mal: String? = null,
    val flettedata: List<String>? = null,
    val svar: String? = null,
) : HendelseDataDto

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@OpenApiName("Rekrutteringstreffendringer")
data class RekrutteringstreffendringerDto(
    val endredeFelter: Set<Endringsfelttype>
) : HendelseDataDto

fun parseHendelseData(mapper: ObjectMapper, hendelsestype: JobbsøkerHendelsestype, node: JsonNode?): HendelseDataDto? {
    if (node == null || node.isNull) return null
    return when (hendelsestype) {
        JobbsøkerHendelsestype.MOTTATT_SVAR_FRA_MINSIDE ->
            mapper.treeToValue(node, MinsideVarselSvarDataDto::class.java)
        JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON ->
            mapper.treeToValue(node, RekrutteringstreffendringerDto::class.java)
        else -> null
    }
}

fun parseHendelseData(mapper: ObjectMapper, hendelsestype: JobbsøkerHendelsestype, json: String?): HendelseDataDto? {
    if (json == null) return null
    return when (hendelsestype) {
        JobbsøkerHendelsestype.MOTTATT_SVAR_FRA_MINSIDE ->
            mapper.readValue(json, MinsideVarselSvarDataDto::class.java)
        JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON ->
            mapper.readValue(json, RekrutteringstreffendringerDto::class.java)
        else -> null
    }
}
