package no.nav.toi.rekrutteringsbistand

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.arbeidsgiver.toi.logging.TeamLogLogger.Companion.teamlog
import no.nav.arbeidsgiver.toi.logging.log
import no.nav.toi.Repository
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.EndretAvType

private enum class DelCvSvar(
    val eventName: String,
    val svar: Boolean,
    val aktivitetsStatus: AktivitetsStatus,
) {
    JA(
        eventName = "rekrutteringsbistandstilling-bruker-svarer-ja-til-deling-av-cv",
        svar = true,
        aktivitetsStatus = AktivitetsStatus.GJENNOMFORES,
    ),
    NEI(
        eventName = "rekrutteringsbistandstilling-bruker-svarer-nei-til-deling-av-cv",
        svar = false,
        aktivitetsStatus = AktivitetsStatus.AVBRUTT,
    ),
}

class RekrutteringsbistandDelCvSvarLytter private constructor(
    rapidsConnection: RapidsConnection,
    private val repository: Repository,
    private val delCvSvar: DelCvSvar,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", delCvSvar.eventName)
                it.requireKey("aktørId")
            }
            validate {
                it.requireKey("fnr", "stillingId", "svar")
                it.require("stillingId") { node -> node.asText().toUUID() }
                it.requireValue("svar", delCvSvar.svar)
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val fnr = packet["fnr"].asText()
        val stillingId = packet["stillingId"].asText()
        val aktivitetskortId = repository.hentAktivitetskortIdForDeltStilling(fnr, stillingId.toUUID())

        if (aktivitetskortId == null) {
            log.error("Fant ikke aktivitetskort for delt stilling med id $stillingId (se secure log)")
            teamlog(log).error("Fant ikke aktivitetskort for delt stilling med id $stillingId for personbruker $fnr")
            return
        }

        teamlog(log).info(
            "Oppdaterer aktivitetsstatus for delt stilling med id $stillingId for personbruker $fnr " +
                "til ${delCvSvar.aktivitetsStatus} (svar=${delCvSvar.svar})"
        )
        repository.oppdaterAktivitetsstatus(
            aktivitetskortId = aktivitetskortId,
            aktivitetsStatus = delCvSvar.aktivitetsStatus,
            endretAv = fnr,
            endretAvType = EndretAvType.PERSONBRUKERIDENT,
        )
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av ${delCvSvar.eventName}: $problems")
        teamlog(log).error("Feil ved behandling av ${delCvSvar.eventName}: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }

    companion object {
        fun registrer(rapidsConnection: RapidsConnection, repository: Repository) {
            DelCvSvar.entries.forEach { svar ->
                RekrutteringsbistandDelCvSvarLytter(rapidsConnection, repository, svar)
            }
        }
    }
}
