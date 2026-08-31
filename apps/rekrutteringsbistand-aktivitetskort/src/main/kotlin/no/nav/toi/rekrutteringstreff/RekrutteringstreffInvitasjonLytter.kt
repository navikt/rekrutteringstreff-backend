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
import no.nav.toi.SecureLog
import no.nav.toi.log

import no.nav.toi.aktivitetskort.AktivitetskortType

class RekrutteringstreffInvitasjonLytter(
    rapidsConnection: RapidsConnection,
    private val repository: Repository,
    private val aktivitetskortType: AktivitetskortType = AktivitetskortType.REKRUTTERINGSTREFF,
) : River.PacketListener {
    private val secureLog = SecureLog(log)
    private val eventName = aktivitetskortType.eventName

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", eventName)
                it.forbid("aktivitetskortuuid")
                it.forbid("aktørId")    // Identmapper populerer meldinger med aktørId, men vi bruker ikke det i denne sammenhengen
            }
            validate {
                it.requireKey(
                    "fnr", "rekrutteringstreffId", "tittel", "fraTid", "tilTid",
                    "opprettetAv", "opprettetTidspunkt", "gateadresse", "postnummer", "poststed"
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
        log.info("Mottok $eventName")
        val fnr = packet["fnr"].asText()

        val startDato = packet["fraTid"].asZonedDateTime()
        val sluttDato = packet["tilTid"].asZonedDateTime()

        val aktivitetskortId = repository.opprettRekrutteringstreffInvitasjon(
            fnr = fnr,
            rekrutteringstreffId = packet["rekrutteringstreffId"].asText().toUUID(),
            tittel = packet["tittel"].asText(),
            beskrivelse = aktivitetskortType.beskrivelse,
            startDato = startDato.toLocalDate(),
            sluttDato = sluttDato.toLocalDate(),
            tid = formaterTidsperiode(startDato, sluttDato),
            endretAv = packet["opprettetAv"].asText(),
            gateAdresse = packet["gateadresse"].asText(),
            postnummer = packet["postnummer"].asText(),
            poststed = packet["poststed"].asText(),
            aktivitetskortType = aktivitetskortType,
        )
        if (aktivitetskortId != null) {
            packet["aktivitetskortuuid"] = aktivitetskortId
            context.publish(fnr, packet.toJson())
        }
    }

    override fun onError(
        problems: MessageProblems,
        context: MessageContext,
        metadata: MessageMetadata,
    ) {
        log.error("Feil ved behandling av $eventName: $problems")
        secureLog.error("Feil ved behandling av $eventName: ${problems.toExtendedReport()}")
        throw Exception(problems.toString())
    }
}
