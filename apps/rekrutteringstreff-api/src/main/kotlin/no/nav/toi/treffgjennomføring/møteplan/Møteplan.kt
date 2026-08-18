package no.nav.toi.treffgjennomføring.møteplan

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import java.time.LocalTime

data class Møteoppsett(val starttidspunkt: LocalTime, val varighetPerMøteMinutter: Int) {
    companion object {
        val STANDARD_STARTTIDSPUNKT: LocalTime = LocalTime.of(10, 0)
        const val STANDARD_VARIGHET_MINUTTER = 10

        fun standard() = Møteoppsett(STANDARD_STARTTIDSPUNKT, STANDARD_VARIGHET_MINUTTER)
    }
}

data class Rom(val romnummer: Int, val jobbsøkere: List<PersonTreffId>)

data class ArbeidsgiverRotasjon(val arbeidsgiverTreffId: ArbeidsgiverTreffId, val startposisjon: Int)

data class Møteplan(
    val møteoppsett: Møteoppsett,
    val rom: List<Rom>,
    val arbeidsgiverRekkefølge: List<ArbeidsgiverRotasjon>,
)
