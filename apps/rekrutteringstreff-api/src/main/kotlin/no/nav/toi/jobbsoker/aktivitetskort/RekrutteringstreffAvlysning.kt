package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.rekrutteringstreff.TreffId
import java.util.UUID

/**
 * Representerer en avlysning av et rekrutteringstreff som sendes på rapid.
 * Brukes av:
 * - rekrutteringsbistand-kandidatvarsel-api: Sender MinSide-varsling til jobbsøkere som har svart ja
 */
class RekrutteringstreffAvlysning(
    private val fnr: String,
    private val rekrutteringstreffId: TreffId,
    private val hendelseId: UUID
) {

    fun publiserTilRapids(rapidsConnection: RapidsConnection) {
        val message = JsonMessage.newMessage(
            eventName = "rekrutteringstreffavlysning",
            map = mapOf(
                "fnr" to fnr,
                "rekrutteringstreffId" to rekrutteringstreffId.somUuid,
                "hendelseId" to hendelseId
            )
        )

        rapidsConnection.publish(fnr, message.toJson())
    }
}
