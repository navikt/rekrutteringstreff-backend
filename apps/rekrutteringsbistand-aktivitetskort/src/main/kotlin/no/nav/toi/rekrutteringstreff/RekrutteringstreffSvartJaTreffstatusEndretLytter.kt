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

class RekrutteringstreffSvartJaTreffstatusEndretLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "svartJaTreffstatusEndret")
                it.forbid("aktørId")
            }
            validate {
                it.requireKey("fnr", "rekrutteringstreffId", "endretAv", "endretAvPersonbruker", "treffstatus")
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

        val treffstatus = packet["treffstatus"].asText().lowercase()
        val aktivitetsStatus = when (treffstatus) {
            "fullført", "fullfort" -> AktivitetsStatus.FULLFORT
            "avlyst" -> AktivitetsStatus.AVBRUTT
            else -> {
                log.error("Ukjent treffstatus '$treffstatus' for rekrutteringstreffId=$rekrutteringstreffId (se secure log)")
                secure(log).error("Ukjent treffstatus '$treffstatus' for rekrutteringstreffId=$rekrutteringstreffId, fnr=$fnr. Hopper over oppdatering.")
                return
            }
        }

        repository.oppdaterAktivitetsstatus(
            aktivitetskortId = aktivitetskortId,
            aktivitetsStatus = aktivitetsStatus,
            endretAv = packet["endretAv"].asText(),
            endretAvType = if (packet["endretAvPersonbruker"].asBoolean()) EndretAvType.PERSONBRUKERIDENT else EndretAvType.NAVIDENT
        )
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av svartJaTreffstatusEndret: $problems")
        secure(log).error("Feil ved behandling av svartJaTreffstatusEndret: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}
