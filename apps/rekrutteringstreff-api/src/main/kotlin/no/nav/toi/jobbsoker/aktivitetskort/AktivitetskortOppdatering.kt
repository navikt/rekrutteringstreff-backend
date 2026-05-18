package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.Endringsfelttype
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Representerer en oppdatering av et rekrutteringstreff som sendes på rapid.
 * Brukes av:
 * - rekrutteringsbistand-aktivitetskort: Oppdaterer aktivitetskortet i aktivitetsplanen
 * - rekrutteringsbistand-kandidatvarsel-api: Sender MinSide-varsling (kun hvis endredeFelter er satt)
 */
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
    private val endretAv: String?,
    /** Hvilke felt som er endret og som mottakere kan velge å varsle om. Null betyr ingen varslings-relevante endringer. */
    private val endredeFelter: List<Endringsfelttype>? = null
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
        endredeFelter?.let { messageMap["endredeFelter"] = it.map { felt -> felt.name } }

        val message = JsonMessage.newMessage(
            eventName = "rekrutteringstreffoppdatering",
            map = messageMap
        )

        rapidsConnection.publish(fnr, message.toJson())
    }
}

