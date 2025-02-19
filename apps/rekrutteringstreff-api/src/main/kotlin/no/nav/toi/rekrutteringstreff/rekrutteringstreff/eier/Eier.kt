package no.nav.toi.rekrutteringstreff.rekrutteringstreff.eier

class Eier(private val navIdent: String) {
    companion object {
        fun List<Eier>.tilJson() = map(Eier::navIdent).joinToString(prefix = "[", postfix = "]") { """"$it""""}
    }
}