package no.nav.toi.jobbsoker.aktivitetskort

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
        rapidsConnection.publish(fnr, """
            {
                "@event_name": "rekrutteringstreffinvitasjon",
                "fnr": "$fnr",
                "rekrutteringstreffId": "${rekrutteringstreffId.somUuid}",
                "tittel": "$tittel",
                "beskrivelse": ${beskrivelse?.let { "\"$it\"" } ?: "TODO" },
                "fraTid": ${fraTid?.let { "\"$it\"" } ?: throw IllegalArgumentException("fraTid er required") },
                "tilTid": ${tilTid?.let { "\"$it\"" } ?: throw IllegalArgumentException("tilTid er required") },
                "opprettetAv": "$opprettetAv",
                "opprettetTidspunkt": "$opprettetTidspunkt",
                "svarfrist": "$svarfrist",
                "gateadresse": ${gateadresse?.let { "\"$it\"" } ?: throw IllegalArgumentException("gateadresseer er required") },
                "postnummer": ${postnummer?.let { "\"$it\"" } ?: throw IllegalArgumentException("postnummer er required") },
                "poststed": ${poststed?.let { "\"$it\"" } ?: throw IllegalArgumentException("poststed er required") }
            }
        """.trimIndent())
    }
}