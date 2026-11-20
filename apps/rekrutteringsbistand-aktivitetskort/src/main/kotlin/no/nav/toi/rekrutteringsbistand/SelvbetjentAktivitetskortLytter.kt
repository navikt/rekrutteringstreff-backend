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
import java.time.ZonedDateTime
import java.util.UUID

class SelvbetjentAktivitetskortLytter (
    rapidsConnection: RapidsConnection,
    private val repository: Repository,
) : River.PacketListener {

    private companion object {
        const val EVENT_NAME = "selvbetjent-samtykke-til-deling-av-cv-gitt"
    }

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.requireKey("stillingsinfo.stillingsid")
            }
            validate {
                it.requireKey("fnr", "stillingsId", "stillingsTittel", "samtykkeGittTidspunkt")
                it.require("stillingsId") { node -> node.asString().toUUID() }
                it.requireZonedDateTime("samtykkeGittTidspunkt")
            }
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val fnr = packet["fnr"].asString()
        val stillingId = packet["stillingsId"].asString()

        val hendelseId = UUID.randomUUID()

        val opprettetAktivitetskortId = repository.opprettDeltStillingForSelvbetjent(
            fnr = fnr,
            stillingId = stillingId,
            tittel = packet["stillingsTittel"].asString(),
            opprettetAv = fnr,
            messageId = hendelseId
        )

        if (opprettetAktivitetskortId == null) {
            flyttEksisterendeAktivitetskortTilGjennomføres(fnr, stillingId.toUUID())
        }
    }

    private fun flyttEksisterendeAktivitetskortTilGjennomføres(fnr: String, stillingId: UUID) {
        val aktivitetskortId = repository.hentAktivitetskortIdForDeltStilling(fnr, stillingId)
        if (aktivitetskortId == null) {
            log.error("Fant ikke eksisterende aktivitetskort for delt stilling med id $stillingId (se secure log)")
            teamlog(log).error("Fant ikke eksisterende aktivitetskort for delt stilling med id $stillingId for personbruker $fnr")
            return
        }

        // Kort som venter på svar eller er avbrutt flyttes. Fullførte kort skal ikke gjenåpnes.
        repository.oppdaterAktivitetsstatus(
            aktivitetskortId = aktivitetskortId,
            aktivitetsStatus = AktivitetsStatus.GJENNOMFORES,
            endretAv = fnr,
            endretAvType = EndretAvType.PERSONBRUKERIDENT,
            forventetSisteAktivitetsstatus = listOf(AktivitetsStatus.FORSLAG, AktivitetsStatus.AVBRUTT),
            etiketter = listOf(AktivitetskortEtikett("Venter på å bli kontaktet", Sentiment.POSITIVE)),
            handlinger = repository.selvbetjentDeltStillingHandlinger(stillingId.toString()),
        )
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av ${EVENT_NAME}: $problems")
        teamlog(log).error("Feil ved behandling av ${EVENT_NAME}: ${problems.toExtendedReport()}")
    }
}

private fun JsonMessage.requireZonedDateTime(key: String) {
    require(key) { node -> ZonedDateTime.parse(node.asString()) }
}