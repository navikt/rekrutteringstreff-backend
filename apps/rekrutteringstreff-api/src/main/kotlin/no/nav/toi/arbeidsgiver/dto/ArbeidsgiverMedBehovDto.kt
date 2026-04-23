package no.nav.toi.arbeidsgiver.dto

import no.nav.toi.arbeidsgiver.ArbeidsgiverMedBehov

data class ArbeidsgiverMedBehovDto(
    val arbeidsgiverTreffId: String,
    val organisasjonsnummer: String,
    val navn: String,
    val behov: ArbeidsgiverBehovDto?,
) {
    companion object {
        fun fra(medBehov: ArbeidsgiverMedBehov) = ArbeidsgiverMedBehovDto(
            arbeidsgiverTreffId = medBehov.arbeidsgiver.arbeidsgiverTreffId.somString,
            organisasjonsnummer = medBehov.arbeidsgiver.orgnr.asString,
            navn = medBehov.arbeidsgiver.orgnavn.asString,
            behov = medBehov.behov?.let { ArbeidsgiverBehovDto.fra(it) },
        )
    }
}

data class LeggTilArbeidsgiverMedBehovDto(
    val organisasjonsnummer: String,
    val navn: String,
    val næringskoder: List<no.nav.toi.arbeidsgiver.Næringskode>? = null,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val behov: ArbeidsgiverBehovDto,
) {
    fun somLeggTilArbeidsgiver() = no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver(
        orgnr = no.nav.toi.arbeidsgiver.Orgnr(organisasjonsnummer),
        orgnavn = no.nav.toi.arbeidsgiver.Orgnavn(navn),
        næringskoder = næringskoder.orEmpty().map {
            no.nav.toi.arbeidsgiver.Næringskode(it.kode, it.beskrivelse)
        },
        gateadresse = gateadresse,
        postnummer = postnummer,
        poststed = poststed,
    )
}
