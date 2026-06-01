package no.nav.toi.jobbsoker.dto

import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.LeggTilJobbsøker

data class JobbsøkerDto(
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String,
    val lagtTilAvNavn: String? = null,
) {
    fun domene() = LeggTilJobbsøker(
        Fødselsnummer(fødselsnummer),
        Fornavn(fornavn),
        Etternavn(etternavn),
    )
}