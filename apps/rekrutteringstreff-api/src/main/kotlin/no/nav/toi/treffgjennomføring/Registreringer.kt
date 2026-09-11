package no.nav.toi.treffgjennomføring

data class Registreringer(
    val interesser: Int,
    val intervjufordelinger: Int,
    val vurderinger: Int,
) {
    fun finnesRegistreringer() = interesser > 0 || intervjufordelinger > 0 || vurderinger > 0

    fun lagHint(ekstraHandlinger: List<String> = emptyList()): String {
        val handlinger = ekstraHandlinger + buildList {
            if (interesser > 0) add("fjern registrerte interesser")
            if (intervjufordelinger > 0) add("fjern registrerte intervjufordelinger")
            if (vurderinger > 0) add("nullstill registrerte vurderinger")
        }
        return if (handlinger.isEmpty()) "" else handlinger.joinToString(" og ").replaceFirstChar { it.uppercase() } + " først."
    }
}
