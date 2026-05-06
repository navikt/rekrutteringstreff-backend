package no.nav.toi.arbeidsgiver.dto

import no.nav.toi.arbeidsgiver.Ansettelsesform
import no.nav.toi.arbeidsgiver.ArbeidsgiversBehov
import no.nav.toi.arbeidsgiver.Arbeidssprak
import no.nav.toi.arbeidsgiver.BehovTag

data class BehovTagDto(
    val label: String,
    val kategori: String,
    val konseptId: Long?,
) {
    fun somBehovTag() = BehovTag(label = label, kategori = kategori, konseptId = konseptId)

    companion object {
        fun fra(tag: BehovTag) = BehovTagDto(
            label = tag.label,
            kategori = tag.kategori,
            konseptId = tag.konseptId,
        )
    }
}

data class ArbeidsgiversBehovDto(
    val samledeKvalifikasjoner: List<BehovTagDto>,
    val arbeidssprak: List<String>,
    val antall: Int,
    val ansettelsesformer: List<String>,
    val personligeEgenskaper: List<BehovTagDto> = emptyList(),
) {
    fun somArbeidsgiversBehov(): ArbeidsgiversBehov = ArbeidsgiversBehov(
        samledeKvalifikasjoner = samledeKvalifikasjoner.map { it.somBehovTag() },
        arbeidssprak = Arbeidssprak.validerOgFiltrer(arbeidssprak),
        antall = antall,
        ansettelsesformer = ansettelsesformer.map { Ansettelsesform.fraApiNavn(it) },
        personligeEgenskaper = personligeEgenskaper.map { it.somBehovTag() },
    )

    companion object {
        fun fra(behov: ArbeidsgiversBehov) = ArbeidsgiversBehovDto(
            samledeKvalifikasjoner = behov.samledeKvalifikasjoner.map { BehovTagDto.fra(it) },
            arbeidssprak = behov.arbeidssprak,
            antall = behov.antall,
            ansettelsesformer = behov.ansettelsesformer.map { it.apiNavn },
            personligeEgenskaper = behov.personligeEgenskaper.map { BehovTagDto.fra(it) },
        )
    }
}
