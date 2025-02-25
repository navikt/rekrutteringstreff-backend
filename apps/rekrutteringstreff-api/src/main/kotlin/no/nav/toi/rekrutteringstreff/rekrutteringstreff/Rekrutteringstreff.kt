package no.nav.toi.rekrutteringstreff.rekrutteringstreff

import java.time.ZonedDateTime
import java.util.UUID

class Rekrutteringstreff(
    val id: TreffId,
    val tittel: String,
    val fraTid: ZonedDateTime,
    val tilTid: ZonedDateTime,
    val sted: String,
    val status: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
) {
    fun tilRekrutteringstreffDTO() = RekrutteringstreffDTO(
        tittel = tittel,
        fraTid = fraTid,
        tilTid = tilTid,
        sted = sted,
        status = status,
        opprettetAvPersonNavident = opprettetAvPersonNavident,
        opprettetAvNavkontorEnhetId = opprettetAvNavkontorEnhetId,
        opprettetAvTidspunkt = opprettetAvTidspunkt,
        id = id.somUuid
    )
}

data class TreffId(private val id: UUID) {
    constructor(id : String): this(UUID.fromString(id))
    val somUuid = id
    override fun toString() = id.toString()
}