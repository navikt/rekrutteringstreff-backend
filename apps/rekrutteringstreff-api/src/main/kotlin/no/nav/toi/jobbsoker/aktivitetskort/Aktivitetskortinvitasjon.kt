package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.ZonedDateTime
import java.util.UUID

class Aktivitetskortinvitasjon private constructor(
    private val fnr: String,
    private val rekrutteringstreffId: TreffId,
    private val hendelseId: UUID,
    private val tittel: String,
    private val fraTid: ZonedDateTime,
    private val tilTid: ZonedDateTime,
    private val opprettetAv: String,
    private val opprettetTidspunkt: ZonedDateTime,
    private val gateadresse: String,
    private val postnummer: String,
    private val poststed: String,
    private val svarfrist: ZonedDateTime
) {
    companion object {
        fun opprett(
            fnr: String,
            rekrutteringstreffId: TreffId,
            hendelseId: UUID,
            tittel: String,
            fraTid: ZonedDateTime?,
            tilTid: ZonedDateTime?,
            opprettetAv: String,
            opprettetTidspunkt: ZonedDateTime,
            gateadresse: String?,
            postnummer: String?,
            poststed: String?,
            svarfrist: ZonedDateTime?
        ) = Aktivitetskortinvitasjon(
            fnr = fnr,
            rekrutteringstreffId = rekrutteringstreffId,
            hendelseId = hendelseId,
            tittel = tittel,
            opprettetAv = opprettetAv,
            opprettetTidspunkt = opprettetTidspunkt,
            fraTid = requireNotNull(fraTid) { "FraTid kan ikke være null når vi inviterer" },
            tilTid = requireNotNull(tilTid) { "TilTid kan ikke være null når vi inviterer" },
            gateadresse = requireNotNull(gateadresse) { "Gateadresse kan ikke være null når vi inviterer" },
            postnummer = requireNotNull(postnummer) { "Postnummer kan ikke være null når vi inviterer" },
            poststed = requireNotNull(poststed) { "Poststed kan ikke være null når vi inviterer" },
            svarfrist = requireNotNull(svarfrist) { "Svarfrist kan ikke være null når vi inviterer" }
        )
    }

    fun publiserTilRapids(rapidsConnection: RapidsConnection) {
        val message = JsonMessage.newMessage(
            eventName = "rekrutteringstreffinvitasjon",
            map = mapOf<String, Any>(
                "fnr" to fnr,
                "rekrutteringstreffId" to rekrutteringstreffId.somUuid,
                "hendelseId" to hendelseId,
                "tittel" to tittel,
                "beskrivelse" to "TODO",
                "fraTid" to fraTid,
                "tilTid" to tilTid,
                "opprettetAv" to opprettetAv,
                "opprettetTidspunkt" to opprettetTidspunkt,
                "svarfrist" to svarfrist,
                "gateadresse" to gateadresse,
                "postnummer" to postnummer,
                "poststed" to poststed
            )
        )

        rapidsConnection.publish(fnr, message.toJson())
    }
}