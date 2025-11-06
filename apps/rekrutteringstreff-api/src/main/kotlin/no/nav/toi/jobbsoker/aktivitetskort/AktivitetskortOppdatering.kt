package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.ZonedDateTime

class AktivitetskortOppdatering(
    private val fnr: String,
    private val rekrutteringstreffId: TreffId,
    private val tittel: String,
    private val fraTid: ZonedDateTime,
    private val tilTid: ZonedDateTime,
    private val gateadresse: String,
    private val postnummer: String,
    private val poststed: String
) {

    fun publiserTilRapids(rapidsConnection: RapidsConnection) {
        val message = JsonMessage.newMessage(
            eventName = "rekrutteringstreffoppdatering",
            map = mapOf<String, Any>(
                "fnr" to fnr,
                "rekrutteringstreffId" to rekrutteringstreffId.somUuid,
                "tittel" to tittel,
                "fraTid" to fraTid,
                "tilTid" to tilTid,
                "gateadresse" to gateadresse,
                "postnummer" to postnummer,
                "poststed" to poststed
            )
        )

        rapidsConnection.publish(fnr, message.toJson())
    }
}

