package no.nav.toi.rekrutteringsbistand

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.Repository
import no.nav.toi.SecureLog
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.EndretAvType
import no.nav.toi.log

class RegistrertFattJobbenLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository,
) : River.PacketListener {
    private val secureLog = SecureLog(log)

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "RegistrertFåttJobben")
                it.forbidValue("@slutt_av_hendelseskjede", true)
            }
            validate {
                it.requireKey("stillingsId", "utførtAvNavIdent", "tidspunkt", "fnr")
                it.require("stillingsId") { node -> node.asText().toUUID() }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val stillingsId = packet["stillingsId"].asText().toUUID()
        val navIdent = packet["utførtAvNavIdent"].asText()
        val fnr = packet["fnr"].asText()

        val aktivitetskortId = repository.hentAktivitetskortIdForDeltStilling(fnr, stillingsId)
        if (aktivitetskortId != null && repository.hentSisteAktivitetsstatus(aktivitetskortId) == AktivitetsStatus.GJENNOMFORES) {
            repository.oppdaterAktivitetsstatus(
                aktivitetskortId = aktivitetskortId,
                aktivitetsStatus = AktivitetsStatus.FULLFORT,
                endretAv = navIdent,
                endretAvType = EndretAvType.NAVIDENT,
                forventetSisteAktivitetsstatus = AktivitetsStatus.GJENNOMFORES,
            )
        }

        packet["@slutt_av_hendelseskjede"] = true
        context.publish(packet.toJson())
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av RegistrertFåttJobben: $problems")
        secureLog.error("Feil ved behandling av RegistrertFåttJobben: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}
