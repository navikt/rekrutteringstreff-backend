package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.TreffId

class AktivitetskortSvartJaTreffstatusEndret(
    private val fnr: String,
    private val rekrutteringstreffId: TreffId,
    private val endretAv: String,
    private val treffstatus: String,
) {
    fun publiserTilRapids(rapidsConnection: RapidsConnection) {
        val message = JsonMessage.newMessage(
            eventName = "svartJaTreffstatusEndret",
            map = mapOf<String, Any>(
                "fnr" to fnr,
                "rekrutteringstreffId" to rekrutteringstreffId.somUuid,
                "endretAv" to endretAv,
                "endretAvPersonbruker" to false,
                "treffstatus" to treffstatus,
            )
        )

        rapidsConnection.publish(fnr, message.toJson())
    }
}
