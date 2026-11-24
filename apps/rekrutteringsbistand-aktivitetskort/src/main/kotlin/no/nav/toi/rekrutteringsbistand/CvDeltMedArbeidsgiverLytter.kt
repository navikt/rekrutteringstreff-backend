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
import no.nav.toi.aktivitetskort.AktivitetskortEtikett
import no.nav.toi.aktivitetskort.EndretAvType
import no.nav.toi.aktivitetskort.Sentiment

class CvDeltMedArbeidsgiverLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "kandidat_v2.DelCvMedArbeidsgiver")
                it.requireKey("stillingsinfo.stillingsid", "fodselsnummerliste")
            }
            validate {
                it.require("stillingsId") { node -> node.asString().toUUID() }
                it.requireKey("utførtAvNavIdent", "tidspunkt")
                it.require("kandidater") { node -> require(node.isObject) }
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val stillingsId = packet["stillingsId"].asString().toUUID()
        val fødselsnumre = packet["kandidater"].propertyNames().map { aktørId ->
            val fnr = packet["fodselsnummerliste"].path(aktørId)
            require(fnr.isString && fnr.asString().matches(Regex("\\d{11}"))) {
                "Fødselsnummerlistebehov for DelCvMedArbeidsgiver mangler gyldig løsning for kandidat"
            }
            fnr.asString()
        }

        fødselsnumre.distinct().forEach { fnr ->
            val aktivitetskortId = repository.hentAktivitetskortIdForDeltStilling(fnr, stillingsId)
            if (aktivitetskortId != null) {
                repository.oppdaterAktivitetsstatus(
                    aktivitetskortId = aktivitetskortId,
                    aktivitetsStatus = AktivitetsStatus.GJENNOMFORES,
                    endretAv = packet["utførtAvNavIdent"].asString(),
                    endretAvType = EndretAvType.NAVIDENT,
                    forventetSisteAktivitetsstatus = listOf(AktivitetsStatus.GJENNOMFORES),
                    etiketter = listOf(AktivitetskortEtikett("CV er delt med arbeidsgiver", Sentiment.NEUTRAL)),
                )
            }
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av DelCvMedArbeidsgiver: $problems")
        teamlog(log).error("Feil ved behandling av DelCvMedArbeidsgiver: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}
