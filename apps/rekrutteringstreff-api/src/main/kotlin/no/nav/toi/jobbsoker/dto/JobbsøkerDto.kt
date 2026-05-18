package no.nav.toi.jobbsoker.dto

import no.nav.toi.jobbsoker.*

data class JobbsøkerDto(
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavIdent: String?,
    val lagtTilAvNavn: String? = null,
) {
    fun domene() = LeggTilJobbsøker(
        Fødselsnummer(fødselsnummer),
        Fornavn(fornavn),
        Etternavn(etternavn),
        navkontor?.let(::Navkontor),
        veilederNavn?.let(::VeilederNavn),
        veilederNavIdent?.let(::VeilederNavIdent),
    )
}