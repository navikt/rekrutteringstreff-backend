package no.nav.toi.arbeidsgiver.dto

import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Næringskode
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr

data class LeggTilArbeidsgiverDto(
    val organisasjonsnummer: String,
    val navn: String,
    val næringskoder: List<Næringskode> = emptyList()
) {
    fun somLeggTilArbeidsgiver() = LeggTilArbeidsgiver(Orgnr(organisasjonsnummer), Orgnavn(navn), næringskoder.map {
        Næringskode(it.kode, it.beskrivelse)
    })
}