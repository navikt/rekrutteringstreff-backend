package no.nav.toi.arbeidsgiver.dto

import no.nav.toi.arbeidsgiver.ArbeidsgiverMedBehov
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Næringskode

data class ArbeidsgiverMedBehovDto(
    val arbeidsgiverTreffId: String,
    val organisasjonsnummer: String,
    val navn: String,
    val behov: ArbeidsgiversBehovDto?,
) {
    companion object {
        fun fra(medBehov: ArbeidsgiverMedBehov) = ArbeidsgiverMedBehovDto(
            arbeidsgiverTreffId = medBehov.arbeidsgiver.arbeidsgiverTreffId.somString,
            organisasjonsnummer = medBehov.arbeidsgiver.orgnr.asString,
            navn = medBehov.arbeidsgiver.orgnavn.asString,
            behov = medBehov.behov?.let { ArbeidsgiversBehovDto.fra(it) },
        )
    }
}

data class LeggTilArbeidsgiverMedBehovDto(
    val organisasjonsnummer: String,
    val navn: String,
    val næringskoder: List<Næringskode>? = null,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val behov: ArbeidsgiversBehovDto,
) {
    fun somLeggTilArbeidsgiver() = LeggTilArbeidsgiver(
        orgnr = Orgnr(organisasjonsnummer),
        orgnavn = Orgnavn(navn),
        næringskoder = næringskoder.orEmpty().map {
            Næringskode(it.kode, it.beskrivelse)
        },
        gateadresse = gateadresse,
        postnummer = postnummer,
        poststed = poststed,
    )
}
