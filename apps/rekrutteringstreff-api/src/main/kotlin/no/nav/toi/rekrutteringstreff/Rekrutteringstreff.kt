package no.nav.toi.rekrutteringstreff

import java.time.ZonedDateTime
import java.util.UUID

class Rekrutteringstreff(
    val id: UUID,
    val tittel: String,
    val fraTid: ZonedDateTime,
    val tilTid: ZonedDateTime,
    val sted: String,
    val status: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String
) {
    fun tilRekrutteringstreffDTO() = RekrutteringstreffDTO(
        id = id,
        tittel = tittel,
        fraTid = fraTid,
        tilTid = tilTid,
        sted = sted,
        status = status,
        opprettetAvPersonNavident = opprettetAvPersonNavident,
        opprettetAvNavkontorEnhetId = opprettetAvNavkontorEnhetId
    )
}
