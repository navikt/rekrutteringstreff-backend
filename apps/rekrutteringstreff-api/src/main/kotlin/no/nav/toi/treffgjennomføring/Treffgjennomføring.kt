package no.nav.toi.treffgjennomføring

import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.oppmøte.Deltakernummer
import no.nav.toi.treffgjennomføring.matching.Matching
import no.nav.toi.treffgjennomføring.møteplan.Møteplan

enum class TreffgjennomføringSteg {
    OPPMØTE, ROM, INTERESSE, FORDELING, VURDERING, OPPSUMMERING
}

data class Treffgjennomføring(
    val gjeldendeSteg: TreffgjennomføringSteg,
    val antallRom: Int,
    val oppmøte: List<PersonTreffId>,
    val deltakernumre: List<Deltakernummer>,
    val møteplan: Møteplan,
    val matching: Matching,
) {
    companion object {
        fun beregnAntallRom(antallArbeidsgivere: Int) = maxOf(antallArbeidsgivere, 1)
    }
}
