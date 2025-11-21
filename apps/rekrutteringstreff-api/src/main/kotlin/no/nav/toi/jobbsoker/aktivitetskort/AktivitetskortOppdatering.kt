package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.ZonedDateTime
import java.util.UUID

class AktivitetskortOppdatering(
    private val fnr: String,
    private val rekrutteringstreffId: TreffId,
    private val hendelseId: UUID,
    private val tittel: String,
    private val fraTid: ZonedDateTime,
    private val tilTid: ZonedDateTime,
    private val gateadresse: String,
    private val postnummer: String,
    private val poststed: String,
    private val endretAv: String?
) {

    fun publiserTilRapids(rapidsConnection: RapidsConnection) {
        val messageMap = mutableMapOf<String, Any>(
            "fnr" to fnr,
            "rekrutteringstreffId" to rekrutteringstreffId.somUuid,
            "hendelseId" to hendelseId,
            "tittel" to tittel,
            "fraTid" to fraTid,
            "tilTid" to tilTid,
            "gateadresse" to gateadresse,
            "postnummer" to postnummer,
            "poststed" to poststed
        )

        endretAv?.let { messageMap["endretAv"] = it }

        val message = JsonMessage.newMessage(
            eventName = "rekrutteringstreffoppdatering",
            map = messageMap
        )

        rapidsConnection.publish(fnr, message.toJson())
    }
}

