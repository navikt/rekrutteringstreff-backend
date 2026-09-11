package no.nav.toi.arbeidsgiver

data class ArbeidsgiverRegistreringer(
    val personerIRom: Int,
    val interesser: Int,
    val vurderinger: Int,
) {
    fun finnesRegistreringer() = personerIRom > 0 || interesser > 0 || vurderinger > 0
}

class ArbeidsgiverKanIkkeSlettesException(val registreringer: ArbeidsgiverRegistreringer) : RuntimeException(
    "Arbeidsgiveren har registreringer i treffgjennomføringen og kan derfor ikke slettes."
) {
    fun lagHint(): String {
        val grunner = mutableListOf<String>()
        if (registreringer.personerIRom > 0) grunner.add("flytt personene ut av arbeidsgiverens rom")
        if (registreringer.interesser > 0) grunner.add("fjern registrerte interesser")
        if (registreringer.vurderinger > 0) grunner.add("nullstill registrerte vurderinger")
        return grunner.joinToString(" og ").replaceFirstChar { it.uppercase() } + " først."
    }
}
