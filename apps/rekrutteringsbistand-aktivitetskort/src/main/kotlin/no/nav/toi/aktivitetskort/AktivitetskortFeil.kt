package no.nav.toi.aktivitetskort

import com.fasterxml.jackson.annotation.JsonProperty
import no.nav.toi.objectMapper

class FellesMeldingsfelter(
    val fnr: String,
    val aktivitetskortId: String,
    val aktivitetskortType: String,
    val endretAv: String,
    val messageId: String,
    val errorMessage: String,
    val errorType: String,
    val timestamp: String,
)

abstract class AktivitetskortFeil(
    fellesMeldingsfelter: FellesMeldingsfelter,
) {
    @get:JsonProperty("@event_name")
    abstract val eventName: String
    val fnr = fellesMeldingsfelter.fnr
    val aktivitetskortId = fellesMeldingsfelter.aktivitetskortId
    val aktivitetskortType = fellesMeldingsfelter.aktivitetskortType
    val endretAv = fellesMeldingsfelter.endretAv
    val messageId = fellesMeldingsfelter.messageId
    val errorMessage = fellesMeldingsfelter.errorMessage
    val errorType = fellesMeldingsfelter.errorType
    val timestamp = fellesMeldingsfelter.timestamp

    fun sendTilRapid(rapidPublish: (String, String) -> Unit, markerFeilkøhendelseSomSendt: (String) -> Unit) {
        rapidPublish(fnr, objectMapper.writeValueAsString(this))
        markerFeilkøhendelseSomSendt(messageId)
    }
}

internal class RekrutteringstreffFeilMelding(
    fellesMeldingsfelter: FellesMeldingsfelter,
    val rekrutteringstreffId: String,
) : AktivitetskortFeil(fellesMeldingsfelter) {
    override val eventName = "aktivitetskort-feil-rekrutteringstreff"
}
internal class DeltStillingFeilMelding(
    fellesMeldingsfelter: FellesMeldingsfelter,
    val stillingId: String,
) : AktivitetskortFeil(fellesMeldingsfelter) {
    override val eventName = "aktivitetskort-feil-deltstilling"
}
