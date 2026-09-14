package no.nav.toi.arbeidsgiver

import no.nav.toi.treffgjennomføring.Registreringer

data class ArbeidsgiverRegistreringer(
    val personerIRom: Int,
    val treffregistreringer: Registreringer,
) {
    fun finnesRegistreringer() = personerIRom > 0 || treffregistreringer.finnesRegistreringer()
}

class ArbeidsgiverKanIkkeSlettesException(val registreringer: ArbeidsgiverRegistreringer) : RuntimeException(
    "Arbeidsgiveren har registreringer i treffgjennomføringen og kan derfor ikke slettes."
) {
    fun lagHint(): String {
        val romHandling = if (registreringer.personerIRom > 0) listOf("flytt personene ut av arbeidsgiverens rom") else emptyList()
        return registreringer.treffregistreringer.lagHint(romHandling)
    }
}
