package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.TreffId

class RekrutteringstreffSvarOgStatus(
    private val fnr: String,
    private val rekrutteringstreffId: TreffId,
    private val endretAv: String,
    private val endretAvPersonbruker: Boolean,
    private val svar: Boolean?,
    private val treffstatus: String?,
) {
    fun publiserTilRapids(rapidsConnection: RapidsConnection) {
        val messageMap = mutableMapOf<String, Any>(
            "fnr" to fnr,
            "rekrutteringstreffId" to rekrutteringstreffId.somUuid,
            "endretAv" to endretAv,
            "endretAvPersonbruker" to endretAvPersonbruker,
        )

        svar?.let { messageMap["svar"] = it }
        treffstatus?.let { messageMap["treffstatus"] = it }

        val message = JsonMessage.newMessage(
            eventName = "rekrutteringstreffSvarOgStatus",
            map = messageMap
        )

        rapidsConnection.publish(fnr, message.toJson())
    }
}

