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
import java.time.format.DateTimeFormatter
import java.util.Locale

private val klokkeslettFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val datoMedMånedFormatter = DateTimeFormatter.ofPattern("dd.\u00A0MMMM\u00A0yyyy", Locale.forLanguageTag("no-NO"))

class RekrutteringstreffBrukerSvartJaLytter(rapidsConnection: RapidsConnection, private val repository: Repository): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition{
                it.requireValue("@event_name", "rekrutteringstreffsvar")
                it.forbid("aktørId")    // Identmapper populerer meldinger med aktørId, men vi bruker ikke det i denne sammenhengen
                it.requireKey("svartJa")
            }
            validate {
                it.requireKey("fnr", "rekrutteringstreffId", "endretAv", "endretAvSluttbruker")
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

        val aktivitetskortId = repository.hentAktivitetskortId(
            fnr = fnr,
            rekrutteringstreffId = packet["rekrutteringstreffId"].asText().toUUID()
        )
        repository.oppdaterAktivitetsstatus(
            aktivitetskortId = aktivitetskortId,
            aktivitetsStatus = if(packet["svartJa"].asBoolean()) AktivitetsStatus.GJENNOMFORES else AktivitetsStatus.AVBRUTT,
            endretAv = packet["endretAv"].asText(),
            endretAvType = if(packet["endretAvSluttbruker"].asBoolean()) EndretAvType.PERSONBRUKER else EndretAvType.NAVIDENT
        )
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av rekrutteringstreff der bruker har svart ja: $problems")
        secure(log).error("Feil ved behandling av rekrutteringstreff der bruker har svart ja: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }

    override fun onPreconditionError(error: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        super.onPreconditionError(error, context, metadata)
    }

    override fun onSevere(error: MessageProblems.MessageException, context: MessageContext) {
        super.onSevere(error, context)
    }
}