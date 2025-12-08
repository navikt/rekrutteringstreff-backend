package no.nav.toi.rekrutteringstreff.eier

import io.javalin.http.Context
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilNavIdenter
import java.lang.IllegalStateException

class EierService(
    private val eierRepository: EierRepository,
) {
    fun erEierEllerUtvikler(treffId: TreffId, navIdent: String, context: Context): Boolean {
        val eiere = eierRepository.hent(treffId)?.tilNavIdenter()
            ?: throw IllegalStateException("Rekrutteringstreff med id ${treffId.somString} har ingen eiere")

        return context.authenticatedUser().erUtvikler() || eiere.contains(navIdent)
    }
}