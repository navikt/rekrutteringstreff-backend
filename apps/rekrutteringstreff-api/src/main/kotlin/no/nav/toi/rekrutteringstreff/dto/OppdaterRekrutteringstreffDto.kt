package no.nav.toi.rekrutteringstreff.dto

import no.nav.toi.rekrutteringstreff.Rekrutteringstreff
import java.time.ZonedDateTime

data class OppdaterRekrutteringstreffDto(
    val tittel: String,
    val beskrivelse: String?,
    val fraTid: ZonedDateTime?,
    val tilTid: ZonedDateTime?,
    val svarfrist: ZonedDateTime?,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val kommune: String?,
    val kommunenummer: String?,
    val fylke: String?,
    val fylkesnummer: String?,
) {
    companion object {
        fun opprettFra(rekrutteringstreff: RekrutteringstreffDto): OppdaterRekrutteringstreffDto {
            return OppdaterRekrutteringstreffDto(
                tittel = rekrutteringstreff.tittel,
                beskrivelse = rekrutteringstreff.beskrivelse,
                fraTid = rekrutteringstreff.fraTid,
                tilTid = rekrutteringstreff.tilTid,
                svarfrist = rekrutteringstreff.svarfrist,
                gateadresse = rekrutteringstreff.gateadresse,
                postnummer = rekrutteringstreff.postnummer,
                poststed = rekrutteringstreff.poststed,
                kommune = rekrutteringstreff.kommune,
                kommunenummer = rekrutteringstreff.kommunenummer,
                fylke = rekrutteringstreff.fylke,
                fylkesnummer = rekrutteringstreff.fylkesnummer,
            )
        }
    }
}
