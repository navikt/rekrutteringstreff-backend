package no.nav.toi.jobbsoker

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.AktørType
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.TreffId

/**
 * Lytter på meldinger fra rekrutteringstreff-kandidatvarsel-api
 * som publiserer svar fra minside for rekrutteringstreffmaler.
 */
class MinsideVarselSvarLytter(
    rapidsConnection: RapidsConnection,
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val objectMapper: ObjectMapper
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "minsideVarselSvar")
            }
            validate {
                it.requireKey("varselId", "avsenderReferanseId", "fnr")
                it.interestedIn("eksternStatus", "minsideStatus", "opprettet", "avsenderNavident", "eksternFeilmelding", "eksternKanal", "mal")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val avsenderReferanseId = packet["avsenderReferanseId"].asText()
        val fnr = packet["fnr"].asText()
        val avsenderNavident = packet["avsenderNavident"].takeIf { !it.isNull && !it.isMissingNode }?.asText()

        log.info("Mottok minsideVarselSvar for rekrutteringstreffId: $avsenderReferanseId")

        val treffId = TreffId(avsenderReferanseId)
        val fødselsnummer = Fødselsnummer(fnr)

        val minsideVarselSvarData = MinsideVarselSvarData(
            varselId = packet["varselId"].asText(),
            avsenderReferanseId = avsenderReferanseId,
            fnr = fnr,
            eksternStatus = packet["eksternStatus"].takeIf { !it.isNull && !it.isMissingNode }?.asText(),
            minsideStatus = packet["minsideStatus"].takeIf { !it.isNull && !it.isMissingNode }?.asText(),
            opprettet = packet["opprettet"].takeIf { !it.isNull && !it.isMissingNode }?.asLocalDateTime()?.atZone(java.time.ZoneId.of("Europe/Oslo")),
            avsenderNavident = avsenderNavident,
            eksternFeilmelding = packet["eksternFeilmelding"].takeIf { !it.isNull && !it.isMissingNode }?.asText(),
            eksternKanal = packet["eksternKanal"].takeIf { !it.isNull && !it.isMissingNode }?.asText(),
            mal = packet["mal"].takeIf { !it.isNull && !it.isMissingNode }?.asText()
        )

        val hendelseDataJson = objectMapper.writeValueAsString(minsideVarselSvarData)

        try {
            jobbsøkerRepository.registrerMinsideVarselSvar(
                fødselsnummer = fødselsnummer,
                treff = treffId,
                opprettetAv = "MIN_SIDE",
                hendelseData = hendelseDataJson
            )
            log.info("Registrerte MOTTATT_SVAR_FRA_MINSIDE-hendelse for rekrutteringstreffId: $avsenderReferanseId")
        } catch (e: Exception) {
            log.error("Klarte ikke å registrere MOTTATT_SVAR_FRA_MINSIDE-hendelse for rekrutteringstreffId: $avsenderReferanseId", e)
            secure(log).error("Feil ved registrering av minside varsel svar for fnr: $fnr, treffId: $avsenderReferanseId", e)
            throw e
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av minsideVarselSvar: $problems")
        secure(log).error("Feil ved behandling av minsideVarselSvar: ${problems.toExtendedReport()}")
    }

    override fun onSevere(
        error: MessageProblems.MessageException,
        context: MessageContext
    ) {
        log.error("Alvorlig feil ved behandling av minsideVarselSvar", error)
        secure(log).error("Alvorlig feil ved behandling av minsideVarselSvar: ${error.problems.toExtendedReport()}", error)
    }

    override fun onPreconditionError(error: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        log.error("Feil ved validering av preconditions behandling av minsideVarselSvar", error)
        secure(log).error("Feil ved validering av preconditions ved behandling av minsideVarselSvar: ${error.toExtendedReport()}")
    }
}
