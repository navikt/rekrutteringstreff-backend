package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.ZonedDateTime

class Aktivitetskortinvitasjon(
    private val fnr: String,
    private val rekrutteringstreffId: TreffId,
    private val tittel: String,
    private val beskrivelse: String?,
    private val fraTid: ZonedDateTime?,
    private val tilTid: ZonedDateTime?,
    private val opprettetAv: String,
    private val opprettetTidspunkt: ZonedDateTime,
    private val gateadresse: String?,
    private val postnummer: String?,
    private val poststed: String?,
    private val svarfrist: ZonedDateTime?
) {
    fun publiserTilRapids(rapidsConnection: RapidsConnection) {
        rapidsConnection.publish(fnr, JsonMessage.newMessage("rekrutteringstreffinvitasjon",
            mapOf(
                "fnr" to fnr,
                "rekrutteringstreffId" to "${rekrutteringstreffId.somUuid}",
                "tittel" to tittel,
                "beskrivelse" to "TODO",
                "fraTid" to (fraTid?.let { "\"$it\"" } ?: throw IllegalArgumentException("fraTid er required")),
                "tilTid" to (tilTid?.let { "\"$it\"" } ?: throw IllegalArgumentException("tilTid er required")),
                "opprettetAv" to opprettetAv,
                "opprettetTidspunkt" to "$opprettetTidspunkt",
                "svarfrist" to "$svarfrist",
                "gateadresse" to (gateadresse?.let { "\"$it\"" } ?: throw IllegalArgumentException("gateadresseer er required")),
                "postnummer" to (postnummer?.let { "\"$it\"" } ?: throw IllegalArgumentException("postnummer er required")),
                "poststed" to (poststed?.let { "\"$it\"" } ?: throw IllegalArgumentException("poststed er required"))
            )
        ).toJson())
    }
}