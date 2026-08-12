package no.nav.toi.rekrutteringstreff

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.toi.Repository
import no.nav.toi.SecureLog
import no.nav.toi.log

class RekrutteringsbistandDelCvLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository
): River.PacketListener {
    private val secureLog = SecureLog(log)

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "rekrutteringsbistandstilling-deling-av-cv")
                it.forbid("aktivitetskortuuid")
                it.forbid("aktørId")    // Identmapper populerer meldinger med aktørId, men vi bruker ikke det i denne sammenhengen
            }
            validate {
                it.requireKey(
                    "fnr", "stillingId", "tittel",
                    "opprettetAv", "opprettetTidspunkt", "arbeidsgiver", "arbeidssted"
                )
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
        val stillingId = packet["stillingId"].asText()
        val tittel = packet["tittel"].asText()
        val opprettetAv = packet["opprettetAv"].asText()
        val opprettetTidspunkt = packet["opprettetTidspunkt"].asText()
        val arbeidsgiver = packet["arbeidsgiver"].asText()
        val arbeidssted = packet["arbeidssted"].asText()

        repository.opprettDeltStilling(
            fnr,
            stillingId,
            tittel,
            opprettetAv,
            opprettetTidspunkt,
            arbeidsgiver,
            arbeidssted
        )?.let { aktivitetskortId ->
            packet["aktivitetskortuuid"] = aktivitetskortId
            context.publish(fnr, packet.toJson())
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av rekrutteringsbistandstilling-deling-av-cv: $problems")
        secureLog.error("Feil ved behandling av rekrutteringsbistandstilling-deling-av-cv: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}
