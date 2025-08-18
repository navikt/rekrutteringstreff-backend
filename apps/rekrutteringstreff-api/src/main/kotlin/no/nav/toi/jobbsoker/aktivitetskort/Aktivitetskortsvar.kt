package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.TreffId

class Aktivitetskortsvar(
    private val fnr: String,
    private val rekrutteringstreffId: TreffId,
    private val endretAv: String,
    private val svartJa: Boolean
) {
    fun publiserTilRapids(rapidsConnection: RapidsConnection) {
        val message = JsonMessage.newMessage(
            eventName = "rekrutteringstreffsvar",
            map = mapOf<String, Any>(
                "fnr" to fnr,
                "rekrutteringstreffId" to rekrutteringstreffId.somUuid,
                "endretAv" to endretAv,
                "endretAvPersonbruker" to true,
                "svartJa" to svartJa
            )
        )

        rapidsConnection.publish(fnr, message.toJson())
    }
}