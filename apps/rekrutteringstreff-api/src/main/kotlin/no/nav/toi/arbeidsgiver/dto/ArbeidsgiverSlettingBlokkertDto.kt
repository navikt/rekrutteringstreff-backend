package no.nav.toi.arbeidsgiver.dto

data class ArbeidsgiverSlettingBlokkertDto(
    val feil: String,
    val hint: String,
    val personerIRom: Int,
    val interesser: Int,
    val vurderinger: Int,
)
