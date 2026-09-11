package no.nav.toi.jobbsoker.oppmøte

import no.nav.toi.treffgjennomføring.Registreringer

class OppmøteKanIkkeFjernesException(val registreringer: Registreringer) : RuntimeException(
    "Jobbsøkeren har registreringer som må ryddes før oppmøtet kan fjernes"
)
