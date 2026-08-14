package no.nav.toi.treffgjennomføring

class OppmøteHarRegistreringerException(val registreringer: Registreringer) : RuntimeException(
    "Jobbsøkeren har registreringer som slettes hvis oppmøtet fjernes"
)
