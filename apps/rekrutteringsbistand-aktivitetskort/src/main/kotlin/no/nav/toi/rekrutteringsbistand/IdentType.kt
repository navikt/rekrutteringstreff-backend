package no.nav.toi.rekrutteringsbistand

import no.nav.toi.aktivitetskort.EndretAvType

enum class IdentType {
    FNR,
    NAV_IDENT,

    ;

    fun tilEndretAvType() = when (this) {
        FNR -> EndretAvType.PERSONBRUKERIDENT
        NAV_IDENT -> EndretAvType.NAVIDENT
    }
}
