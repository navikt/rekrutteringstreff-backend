package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.Rekrutteringstreff
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
    private val poststed: String?
) {
    fun publiserTilRapids(rapidsConnection: RapidsConnection) {
        rapidsConnection.publish(fnr, """
            {
                "@event_name": "rekrutteringstreffinvitasjon",
                "fnr": "$fnr",
                "rekrutteringstreffId": "${rekrutteringstreffId.somUuid}",
                "tittel": "$tittel",
                "beskrivelse": ${beskrivelse?.let { "\"$it\"" } ?: "null"},
                "fraTid": ${fraTid?.let { "\"$it" } ?: "null"},
                "tilTid": ${tilTid?.let { "\"$it\"" } ?: "null"},
                "opprettetAv": "$opprettetAv",
                "opprettetAvType": "NAVIDENT",
                "opprettetTidspunkt": "$opprettetTidspunkt",
                "antallPlasser": ${TODO("Hvor finnes antall plasser?")},
                "sted": ${TODO("Skal sted formateres før avsending, eller etter?")},
            }
        """.trimIndent())   // TODO: Må opprettetAvType settes en plass.. men kan kanskje settes i toi-aktivitetskort. At man gjør en antagelse på at alle rekrutteringstreffinvitasjon-hendelser kommer fra veiledere?
    }
}