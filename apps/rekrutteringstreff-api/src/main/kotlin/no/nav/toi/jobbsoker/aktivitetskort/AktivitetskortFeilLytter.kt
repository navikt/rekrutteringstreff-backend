package no.nav.toi.jobbsoker

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.TreffId

class AktivitetskortFeilLytter(
    rapidsConnection: RapidsConnection,
    private val jobbsøkerRepository: JobbsøkerRepository
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "aktivitetskort-feil")
                it.requireKey("rekrutteringstreffId", "fnr", "endretAv")
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

        log.info("Mottok feilmelding for aktivitetskort for rekrutteringstreffId: $rekrutteringstreffId")

        try {
            jobbsøkerRepository.registrerAktivitetskortOpprettelseFeilet(fødselsnummer, rekrutteringstreffId, endretAv)
        } catch (e: Exception) {
            log.error(
                "Klarte ikke å registrere feil-hendelse for aktivitetskort for rekrutteringstreffId $rekrutteringstreffId",
                e
            )
            throw e
        }
    }
}