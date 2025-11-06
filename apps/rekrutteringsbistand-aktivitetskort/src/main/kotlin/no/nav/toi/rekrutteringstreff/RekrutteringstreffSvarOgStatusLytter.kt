package no.nav.toi.rekrutteringstreff

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.Repository
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.EndretAvType
import no.nav.toi.log
private const val TREFFSTATUS_FULLFØRT = "fullført"
private const val TREFFSTATUS_AVLYST = "avlyst"
private val TREFFSTATUS_UENDRET = null

private const val SVART_JA = true
private const val SVART_NEI = false
private val IKKE_SVART = null

class RekrutteringstreffSvarOgStatusLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "rekrutteringstreffSvarOgStatus")
                it.forbid("aktørId")
            }
            validate {
                it.requireKey("fnr", "rekrutteringstreffId", "endretAv", "endretAvPersonbruker")
                it.interestedIn("svar", "treffstatus")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val fnr = packet["fnr"].asText()
        val rekrutteringstreffId = packet["rekrutteringstreffId"].asText()

        val aktivitetskortId = repository.hentAktivitetskortId(
            fnr = fnr,
            rekrutteringstreffId = rekrutteringstreffId.toUUID()
        )

        if (aktivitetskortId == null) {
            log.error("Fant ikke aktivitetskort for rekrutteringstreff med id $rekrutteringstreffId (se secure log)")
            secure(log).error("Fant ikke aktivitetskort for rekrutteringstreff med id $rekrutteringstreffId for personbruker $fnr")
            return
        }

        val svar = packet["svar"].takeIf { !it.isMissingNode }?.asBoolean()
        val treffstatus = packet["treffstatus"].takeIf { !it.isMissingNode }?.asText()

        val aktivitetsStatus = beregnAktivitetsStatus(svar, treffstatus, rekrutteringstreffId, fnr) ?: return

        val endretAvPersonbruker = packet["endretAvPersonbruker"].asBoolean()
        secure(log).info("Oppdaterer aktivitetsstatus for rekrutteringstreff med id $rekrutteringstreffId for personbruker $fnr til $aktivitetsStatus (svar=$svar, treffstatus=$treffstatus)")

        repository.oppdaterAktivitetsstatus(
            aktivitetskortId = aktivitetskortId,
            aktivitetsStatus = aktivitetsStatus,
            endretAv = packet["endretAv"].asText(),
            endretAvType = if (endretAvPersonbruker) EndretAvType.PERSONBRUKERIDENT else EndretAvType.NAVIDENT
        )
    }

    private fun beregnAktivitetsStatus(
        svar: Boolean?,
        treffstatus: String?,
        rekrutteringstreffId: String,
        fnr: String
    ): AktivitetsStatus? {
        return when {
            svar == SVART_JA && treffstatus == TREFFSTATUS_FULLFØRT -> AktivitetsStatus.FULLFORT
            svar == SVART_JA && treffstatus == TREFFSTATUS_AVLYST -> AktivitetsStatus.AVBRUTT
            svar == SVART_JA && treffstatus == TREFFSTATUS_UENDRET -> AktivitetsStatus.GJENNOMFORES
            svar == SVART_JA -> {
                log.error("Ukjent treffstatus '$treffstatus' for bruker som har svart ja, rekrutteringstreffId=$rekrutteringstreffId (se secure log)")
                secure(log).error("Ukjent treffstatus '$treffstatus' for bruker som har svart ja, rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr. Hopper over oppdatering.")
                null
            }

            svar == SVART_NEI -> AktivitetsStatus.AVBRUTT

            svar == IKKE_SVART && treffstatus == TREFFSTATUS_FULLFØRT -> AktivitetsStatus.AVBRUTT
            svar == IKKE_SVART && treffstatus == TREFFSTATUS_AVLYST -> AktivitetsStatus.AVBRUTT
            svar == IKKE_SVART && treffstatus != TREFFSTATUS_UENDRET -> {
                log.error("Ukjent treffstatus '$treffstatus' for bruker som ikke har svart, rekrutteringstreffId=$rekrutteringstreffId (se secure log)")
                secure(log).error("Ukjent treffstatus '$treffstatus' for bruker som ikke har svart, rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr. Hopper over oppdatering.")
                null
            }
            else -> {
                log.error("Melding mangler både svar og treffstatus, rekrutteringstreffId=$rekrutteringstreffId (se secure log)")
                secure(log).error("Melding mangler både svar og treffstatus, rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr. Hopper over oppdatering.")
                null
            }
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av rekrutteringstreffSvarOgStatus: $problems")
        secure(log).error("Feil ved behandling av rekrutteringstreffSvarOgStatus: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}

