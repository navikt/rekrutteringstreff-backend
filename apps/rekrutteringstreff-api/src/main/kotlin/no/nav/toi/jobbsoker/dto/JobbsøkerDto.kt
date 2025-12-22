package no.nav.toi.jobbsoker.dto

import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.Navkontor
import no.nav.toi.jobbsoker.VeilederNavIdent
import no.nav.toi.jobbsoker.VeilederNavn

data class JobbsøkerDto(
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavIdent: String?
) {
    fun domene() = LeggTilJobbsøker(
        Fødselsnummer(fødselsnummer),
        Fornavn(fornavn),
        Etternavn(etternavn),
        navkontor?.let(::Navkontor),
        veilederNavn?.let(::VeilederNavn),
        veilederNavIdent?.let(::VeilederNavIdent)
    )
}