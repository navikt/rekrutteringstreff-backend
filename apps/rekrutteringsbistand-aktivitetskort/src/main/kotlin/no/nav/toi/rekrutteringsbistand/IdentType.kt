package no.nav.toi.rekrutteringsbistand

import no.nav.toi.aktivitetskort.EndretAvType

enum class IdentType {
    FNR,
    NAV_IDENT,
    AKTOR_ID,
    ;

    fun tilEndretAvType() = when (this) {
        FNR, AKTOR_ID -> EndretAvType.PERSONBRUKERIDENT
        NAV_IDENT -> EndretAvType.NAVIDENT
    }
}
