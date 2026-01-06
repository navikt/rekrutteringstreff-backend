package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.TreffId

class AktivitetskortOppdateringLytter(
    rapidsConnection: RapidsConnection,
    private val jobbsøkerService: JobbsøkerService
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "aktivitetskort-oppdatering")
                it.requireKey("rekrutteringstreffId", "fnr", "endretAv", "aktivitetsStatus")
                it.forbid("aktørId")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val rekrutteringstreffId = TreffId(packet["rekrutteringstreffId"].asText())
        val fødselsnummer = Fødselsnummer(packet["fnr"].asText())
        val endretAv = packet["endretAv"].asText()
        val aktivitetsStatus = packet["aktivitetsStatus"].asText()

        log.info("Mottok aktivitetskort-oppdatering med status $aktivitetsStatus for rekrutteringstreffId: $rekrutteringstreffId")

        try {
            jobbsøkerService.registrerAktivitetskortOppdatering(fødselsnummer, rekrutteringstreffId, endretAv, aktivitetsStatus)
        } catch (e: Exception) {
            log.error(
                "Klarte ikke å registrere aktivitetskort-oppdatering hendelse for rekrutteringstreffId $rekrutteringstreffId",
                e
            )
            throw e
        }
    }
}
