package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TreffId
import java.util.*

enum class AktivitetskortTreffstatus(val rapidVerdi: String) {
    AVLYST("avlyst"),
    FULLFØRT("fullført")
}

/**
 * Meldinger med svar=true og treffstatus=avlyst utløser Minside-varsel via KandidatTreffAvlystLytter
 * i rekrutteringsbistand-kandidatvarsel-api.
 */
class RekrutteringstreffSvarOgStatus(
    private val fnr: String,
    private val rekrutteringstreffId: TreffId,
    private val endretAv: String,
    private val endretAvPersonbruker: Boolean,
    private val hendelseId: UUID,
    private val kategori: RekrutteringstreffKategori,
    private val svar: Boolean? = null,
    private val treffstatus: AktivitetskortTreffstatus? = null,
) {
    fun publiserTilRapids(rapidsConnection: RapidsConnection) {
        val eventName = if (kategori == RekrutteringstreffKategori.WORKOP) "workopSvarOgStatus" else "rekrutteringstreffSvarOgStatus"
        val messageMap = mutableMapOf<String, Any>(
            "fnr" to fnr,
            "rekrutteringstreffId" to rekrutteringstreffId.somUuid,
            "endretAv" to endretAv,
            "endretAvPersonbruker" to endretAvPersonbruker,
            "hendelseId" to hendelseId,
            "kategori" to kategori.name,
        )

        svar?.let { messageMap["svar"] = it }
        treffstatus?.let { messageMap["treffstatus"] = it.rapidVerdi }

        val message = JsonMessage.newMessage(
            eventName = eventName,
            map = messageMap
        )

        rapidsConnection.publish(fnr, message.toJson())
    }
}

