package no.nav.toi.jobbsoker.oppmøte

data class Registreringer(val interesser: Int, val vurderinger: Int) {
    fun finnesRegistreringer() = interesser + vurderinger > 0
}

class OppmøteKanIkkeFjernesException(val registreringer: Registreringer) : RuntimeException(
    "Jobbsøkeren har registreringer som må ryddes før oppmøtet kan fjernes"
)
