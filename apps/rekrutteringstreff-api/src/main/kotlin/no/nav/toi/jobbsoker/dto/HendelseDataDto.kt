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
    JsonSubTypes.Type(OppmøteRegistrertDataDto::class),
    JsonSubTypes.Type(OppmøteFjernetDataDto::class),
    JsonSubTypes.Type(VurderingHendelseDataDto::class),
    JsonSubTypes.Type(NotatHendelseDataDto::class),
    JsonSubTypes.Type(AndregangsintervjuHendelseDataDto::class),
    JsonSubTypes.Type(ArbeidsgiverkontekstDataDto::class),
)
@OneOf(
    MinsideVarselSvarDataDto::class,
    RekrutteringstreffendringerDto::class,
    OppmøteRegistrertDataDto::class,
    OppmøteFjernetDataDto::class,
    VurderingHendelseDataDto::class,
    NotatHendelseDataDto::class,
    AndregangsintervjuHendelseDataDto::class,
    ArbeidsgiverkontekstDataDto::class,
)
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
@OpenApiName("RekrutteringstreffendringerHendelseData")
data class RekrutteringstreffendringerDto(
    val endredeFelter: Set<Endringsfelttype>
) : HendelseDataDto

@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@OpenApiName("OppmøteRegistrertHendelseData")
data class OppmøteRegistrertDataDto(
    val deltakernummer: Int? = null,
) : HendelseDataDto

/** Tellingen av hva kaskaden slettet. Eneste spor av registreringer som forsvant med oppmøtet. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@OpenApiName("OppmøteFjernetHendelseData")
data class OppmøteFjernetDataDto(
    val interesser: Int? = null,
    val intervjuplasser: Int? = null,
    val vurderinger: Int? = null,
) : HendelseDataDto

/** `forrigeVurdering` er det tidslinja trenger for å vise «Aktuell → Ikke aktuell». */
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@OpenApiName("VurderingHendelseData")
data class VurderingHendelseDataDto(
    val arbeidsgiverTreffId: String? = null,
    val vurdering: String? = null,
    val forrigeVurdering: String? = null,
) : HendelseDataDto

/**
 * `notat` er kodeverdien fra `Vurderingsnotat`, aldri fritekst. Prefikset `AG_`/`JS_` sier hvem
 * uttalelsen kom fra; hendelsen handler uansett om jobbsøkeren.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@OpenApiName("NotatHendelseData")
data class NotatHendelseDataDto(
    val arbeidsgiverTreffId: String? = null,
    val notat: String? = null,
) : HendelseDataDto

/** `dato` er datoen slik den var da avtalen ble inngått. Gjeldende dato leses fra vurderingsraden. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@OpenApiName("AndregangsintervjuHendelseData")
data class AndregangsintervjuHendelseDataDto(
    val arbeidsgiverTreffId: String? = null,
    val dato: String? = null,
) : HendelseDataDto

/** For hendelser der arbeidsgiveren er hele konteksten, som jobbtilbud og angring. */
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)
@OpenApiName("ArbeidsgiverkontekstHendelseData")
data class ArbeidsgiverkontekstDataDto(
    val arbeidsgiverTreffId: String? = null,
) : HendelseDataDto

private fun målklasse(hendelsestype: JobbsøkerHendelsestype): Class<out HendelseDataDto>? =
    when (hendelsestype) {
        JobbsøkerHendelsestype.MOTTATT_SVAR_FRA_MINSIDE -> MinsideVarselSvarDataDto::class.java
        JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON -> RekrutteringstreffendringerDto::class.java
        JobbsøkerHendelsestype.REGISTRERT_OPPMØTE -> OppmøteRegistrertDataDto::class.java
        JobbsøkerHendelsestype.REGISTRERT_OPPMØTE_FJERNET -> OppmøteFjernetDataDto::class.java
        JobbsøkerHendelsestype.VURDERT -> VurderingHendelseDataDto::class.java
        JobbsøkerHendelsestype.NOTAT_LAGT_TIL,
        JobbsøkerHendelsestype.NOTAT_FJERNET -> NotatHendelseDataDto::class.java
        JobbsøkerHendelsestype.ANDREGANGSINTERVJU_AVTALT -> AndregangsintervjuHendelseDataDto::class.java
        JobbsøkerHendelsestype.ANGRE_ANDREGANGSINTERVJU_AVTALT,
        JobbsøkerHendelsestype.JOBBTILBUD_GITT,
        JobbsøkerHendelsestype.ANGRE_JOBBTILBUD_GITT -> ArbeidsgiverkontekstDataDto::class.java
        else -> null
    }

fun parseHendelseData(mapper: ObjectMapper, hendelsestype: JobbsøkerHendelsestype, node: JsonNode?): HendelseDataDto? {
    if (node == null || node.isNull) return null
    val målklasse = målklasse(hendelsestype) ?: return null
    return mapper.treeToValue(node, målklasse)
}

fun parseHendelseData(mapper: ObjectMapper, hendelsestype: JobbsøkerHendelsestype, json: String?): HendelseDataDto? {
    if (json == null) return null
    val målklasse = målklasse(hendelsestype) ?: return null
    return mapper.readValue(json, målklasse)
}
