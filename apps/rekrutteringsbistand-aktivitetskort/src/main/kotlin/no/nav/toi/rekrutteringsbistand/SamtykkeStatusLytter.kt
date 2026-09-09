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
import java.time.ZonedDateTime

private sealed interface SamtykkeStatusHendelse {
    val eventName: String
    fun registrerValidering(river: River)
    fun aktivitetsStatus(packet: JsonMessage): AktivitetsStatus
    fun endretAv(packet: JsonMessage, fnr: String): String
    fun endretAvType(packet: JsonMessage): EndretAvType
}

class SamtykkeStatusLytter private constructor(
    rapidsConnection: RapidsConnection,
    private val repository: Repository,
    private val hendelse: SamtykkeStatusHendelse,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", hendelse.eventName)
            }
            validate {
                it.requireKey("fnr", "stillingsId", "stillingsTittel")
                it.require("stillingsId") { node -> node.asText().toUUID() }
            }
            hendelse.registrerValidering(this)
        }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry,
    ) {
        val fnr = packet["fnr"].asText()
        val stillingId = packet["stillingsId"].asText().toUUID()
        val aktivitetskortId = repository.hentAktivitetskortIdForDeltStilling(fnr, stillingId)

        if (aktivitetskortId == null) {
            log.error("Fant ikke aktivitetskort for delt stilling med id $stillingId (se secure log)")
            teamlog(log).error("Fant ikke aktivitetskort for delt stilling med id $stillingId for personbruker $fnr")
            return
        }

        repository.oppdaterAktivitetsstatus(
            aktivitetskortId = aktivitetskortId,
            aktivitetsStatus = hendelse.aktivitetsStatus(packet),
            endretAv = hendelse.endretAv(packet, fnr),
            endretAvType = hendelse.endretAvType(packet),
        )
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av ${hendelse.eventName}: $problems")
        teamlog(log).error("Feil ved behandling av ${hendelse.eventName}: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }

    companion object {
        fun registrer(rapidsConnection: RapidsConnection, repository: Repository) {
            listOf(SamtykkeBesvartHendelse, SelvbetjentSamtykkeGittHendelse, SamtykkeTrukketHendelse,)
                .forEach { hendelse ->
                    SamtykkeStatusLytter(rapidsConnection, repository, hendelse)
                }
        }
    }
}

private object SamtykkeBesvartHendelse : SamtykkeStatusHendelse {
    override val eventName = "samtykke-forespørsel-om-deling-av-cv-besvart"

    override fun registrerValidering(river: River) {
        river.validate {
            it.requireKey(
                "samtykkeGitt",
                "besvartAvIdent",
                "besvartAvIdentType",
                "besvartTidspunkt",
            )
            it.require("samtykkeGitt") { node -> node.asText().toBooleanStrict() }
            it.require("besvartAvIdentType") { node -> IdentType.valueOf(node.asText()) }
            it.require("besvartTidspunkt") { node -> ZonedDateTime.parse(node.asText()) }
        }
    }

    override fun aktivitetsStatus(packet: JsonMessage) =
        if (packet["samtykkeGitt"].asText().toBooleanStrict()) {
            AktivitetsStatus.GJENNOMFORES
        } else {
            AktivitetsStatus.AVBRUTT
        }

    override fun endretAv(packet: JsonMessage, fnr: String) = packet["besvartAvIdent"].asText()

    override fun endretAvType(packet: JsonMessage) =
        IdentType.valueOf(packet["besvartAvIdentType"].asText()).tilEndretAvType()
}

private object SelvbetjentSamtykkeGittHendelse : SamtykkeStatusHendelse {
    override val eventName = "selvbetjent-samtykke-til-deling-av-cv-gitt"

    override fun registrerValidering(river: River) {
        river.validate {
            it.requireKey("samtykkeGittTidspunkt")
            it.require("samtykkeGittTidspunkt") { node -> ZonedDateTime.parse(node.asText()) }
        }
    }

    override fun aktivitetsStatus(packet: JsonMessage) = AktivitetsStatus.GJENNOMFORES

    override fun endretAv(packet: JsonMessage, fnr: String) = fnr

    override fun endretAvType(packet: JsonMessage) = EndretAvType.PERSONBRUKERIDENT
}

private object SamtykkeTrukketHendelse : SamtykkeStatusHendelse {
    override val eventName = "samtykke-til-deling-av-cv-trukket"

    override fun registrerValidering(river: River) {
        river.validate {
            it.requireKey("trukketAvIdent", "trukketAvIdentType", "trukketTidspunkt")
            it.require("trukketAvIdentType") { node -> IdentType.valueOf(node.asText()) }
            it.require("trukketTidspunkt") { node -> ZonedDateTime.parse(node.asText()) }
        }
    }

    override fun aktivitetsStatus(packet: JsonMessage) = AktivitetsStatus.AVBRUTT

    override fun endretAv(packet: JsonMessage, fnr: String) = packet["trukketAvIdent"].asText()

    override fun endretAvType(packet: JsonMessage) =
        IdentType.valueOf(packet["trukketAvIdentType"].asText()).tilEndretAvType()
}
