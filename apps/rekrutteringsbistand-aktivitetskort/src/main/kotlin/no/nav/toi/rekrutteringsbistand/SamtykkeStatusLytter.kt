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

private sealed interface SamtykkeStatusHendelse {
    val eventName: String
    fun registrerValidering(river: River)
    fun aktivitetsStatus(packet: JsonMessage): AktivitetsStatus
    fun endretAv(packet: JsonMessage, fnr: String): String
    fun endretAvType(packet: JsonMessage): EndretAvType
    fun etiketter(packet: JsonMessage): List<AktivitetskortEtikett>
    fun forventetSisteAktivitetsstatus(): List<AktivitetsStatus>? = null
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
                it.requireKey("stillingsinfo.stillingsid")
            }
            validate {
                it.requireKey("fnr", "stillingsId")
                it.require("stillingsId") { node -> node.asString().toUUID() }
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
        val fnr = packet["fnr"].asString()
        val stillingId = packet["stillingsId"].asString().toUUID()
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
            etiketter = hendelse.etiketter(packet),
            detaljer = emptyList(),
            forventetSisteAktivitetsstatus = hendelse.forventetSisteAktivitetsstatus(),
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
            listOf(
                SamtykkeBesvartHendelse,
                SamtykkeTrukketHendelse,
                SamtykkeSvarfristUtløptHendelse,
                SamtykkeUtløptHendelse
            )
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
                "stillingsTittel",
                "samtykkeGitt",
                "besvartAvIdent",
                "besvartAvIdentType",
                "besvartTidspunkt",
            )
            it.require("samtykkeGitt") { node -> node.asString().toBooleanStrict() }
            it.require("besvartAvIdentType") { node -> IdentType.valueOf(node.asString()) }
            it.requireZonedDateTime("besvartTidspunkt")
        }
    }

    override fun aktivitetsStatus(packet: JsonMessage) =
        if (packet["samtykkeGitt"].asString().toBooleanStrict()) {
            AktivitetsStatus.GJENNOMFORES
        } else {
            AktivitetsStatus.AVBRUTT
        }

    override fun endretAv(packet: JsonMessage, fnr: String) = packet["besvartAvIdent"].asString()

    override fun endretAvType(packet: JsonMessage) =
        IdentType.valueOf(packet["besvartAvIdentType"].asString()).tilEndretAvType()

    override fun etiketter(packet: JsonMessage) =
        if (packet["samtykkeGitt"].asString().toBooleanStrict()) {
            listOf(AktivitetskortEtikett("Venter på å bli kontaktet", Sentiment.POSITIVE))
        } else {
            emptyList()
        }
}

private object SamtykkeTrukketHendelse : SamtykkeStatusHendelse {
    override val eventName = "samtykke-til-deling-av-cv-trukket"

    override fun registrerValidering(river: River) {
        river.validate {
            it.requireKey("stillingsTittel", "trukketAvIdent", "trukketAvIdentType", "trukketTidspunkt")
            it.require("trukketAvIdentType") { node -> IdentType.valueOf(node.asString()) }
            it.requireZonedDateTime("trukketTidspunkt")
        }
    }

    override fun aktivitetsStatus(packet: JsonMessage) = AktivitetsStatus.AVBRUTT

    override fun endretAv(packet: JsonMessage, fnr: String) = packet["trukketAvIdent"].asString()

    override fun endretAvType(packet: JsonMessage) =
        IdentType.valueOf(packet["trukketAvIdentType"].asString()).tilEndretAvType()

    override fun etiketter(packet: JsonMessage) =
        listOf(AktivitetskortEtikett("samtykke til deling av CV er trukket", Sentiment.NEGATIVE))
}

private object SamtykkeSvarfristUtløptHendelse : SamtykkeStatusHendelse {
    override val eventName = "samtykke-forespørsel-deling-av-cv-svarfrist-utløpt"

    override fun registrerValidering(river: River) {
        river.validate {
            it.requireZonedDateTime("svarfrist")
        }
    }

    override fun aktivitetsStatus(packet: JsonMessage) = AktivitetsStatus.AVBRUTT

    override fun endretAv(packet: JsonMessage, fnr: String) = EndretAvType.SYSTEM.name

    override fun endretAvType(packet: JsonMessage) = EndretAvType.SYSTEM

    override fun etiketter(packet: JsonMessage) =
        emptyList<AktivitetskortEtikett>()
}

private object SamtykkeUtløptHendelse : SamtykkeStatusHendelse {
    override val eventName: String = "samtykke-til-deling-av-cv-utlopt"

    override fun registrerValidering(river: River) {
        river.validate {
            it.requireKey(
                "stillingsId",
                "fnr",
                "aktørId",
            )
        }
    }

    override fun aktivitetsStatus(packet: JsonMessage) = AktivitetsStatus.FULLFORT

    override fun endretAv(packet: JsonMessage, fnr: String): String = EndretAvType.SYSTEM.name

    override fun endretAvType(packet: JsonMessage) = EndretAvType.SYSTEM

    override fun etiketter(packet: JsonMessage) = emptyList<AktivitetskortEtikett>()

    // FORSLAG skal i praksis ikke forekomme, men tillates for å rydde opp i kort som aldri ble besvart.
    override fun forventetSisteAktivitetsstatus() =
        listOf(AktivitetsStatus.GJENNOMFORES, AktivitetsStatus.FORSLAG)
}

private fun JsonMessage.requireZonedDateTime(key: String) {
    require(key) { node -> ZonedDateTime.parse(node.asString()) }
}