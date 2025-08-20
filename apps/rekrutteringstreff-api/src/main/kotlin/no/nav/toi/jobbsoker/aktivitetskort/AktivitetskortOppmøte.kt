package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.TreffId

class AktivitetskortOppmøte(
    private val fnr: String,
    private val rekrutteringstreffId: TreffId,
    private val endretAv: String,
    private val møttOpp: Boolean
) {
    fun publiserTilRapids(rapidsConnection: RapidsConnection) {
        val message = JsonMessage.newMessage(
            eventName = "rekrutteringstreffoppmøte",
            map = mapOf<String, Any>(
                "fnr" to fnr,
                "rekrutteringstreffId" to rekrutteringstreffId.somUuid,
                "endretAv" to endretAv,
                "endretAvPersonbruker" to false,
                "møttOpp" to møttOpp,
            )
        )

        rapidsConnection.publish(fnr, message.toJson())
    }
}