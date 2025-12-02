package no.nav.toi.rekrutteringstreff.eier

class Eier(private val navIdent: String) {
    companion object {
        fun List<Eier>.tilJson() = map(Eier::navIdent).joinToString(prefix = "[", postfix = "]") { """"$it""""}
        fun List<Eier>.tilNavIdenter() = map(Eier::navIdent)
    }
}
