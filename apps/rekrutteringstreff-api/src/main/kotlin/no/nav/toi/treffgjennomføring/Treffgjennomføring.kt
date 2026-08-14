package no.nav.toi.treffgjennomføring

import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.oppmøte.Deltakernummer
import no.nav.toi.treffgjennomføring.matching.Matching
import no.nav.toi.treffgjennomføring.møteplan.Møteplan

enum class TreffgjennomføringFase {
    OPPMØTE, ROM, INTERESSE, FORDELING, VURDERING;

    fun senesteAv(annen: TreffgjennomføringFase) = if (ordinal >= annen.ordinal) this else annen
}

data class Treffgjennomføring(
    val fase: TreffgjennomføringFase,
    val antallRom: Int,
    val oppmøte: List<PersonTreffId>,
    val deltakernummer: List<Deltakernummer>,
    val møteplan: Møteplan,
    val matching: Matching,
) {
    companion object {
        fun beregnAntallRom(antallArbeidsgivere: Int) = maxOf(antallArbeidsgivere, 1)
    }
}
