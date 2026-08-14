package no.nav.toi.jobbsoker.oppmøte

data class Registreringer(val interesser: Int, val intervjuplasser: Int, val vurderinger: Int) {
    fun finnesNoen() = interesser + intervjuplasser + vurderinger > 0
}

class OppmøteHarRegistreringerException(val registreringer: Registreringer) : RuntimeException(
    "Jobbsøkeren har registreringer som slettes hvis oppmøtet fjernes"
)
