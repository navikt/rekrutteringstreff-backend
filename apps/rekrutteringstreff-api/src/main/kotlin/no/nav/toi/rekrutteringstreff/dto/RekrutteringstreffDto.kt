package no.nav.toi.rekrutteringstreff.dto

import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import java.time.ZonedDateTime
import java.util.UUID

data class RekrutteringstreffDto(
    val id: UUID,
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
    val status: RekrutteringstreffStatus,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
    val antallArbeidsgivere: Int?,
    val antallJobbsøkere: Int?,
    val eiere: List<String>,
    val sistEndret: ZonedDateTime,
    val sistEndretAv: String,
)
