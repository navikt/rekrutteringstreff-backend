package no.nav.toi.treffgjennomforing

/**
 * Fjerning av oppmøte for en som har registreringer krever eksplisitt bekreftelse.
 * Data må aldri bli hengende igjen inkonsistent, og brukeren skal se konsekvensen
 * før noe kjøres.
 */
class OppmøteHarRegistreringerException(val registreringer: Registreringer) : RuntimeException(
    "Jobbsøkeren har registreringer som slettes hvis oppmøtet fjernes"
)
