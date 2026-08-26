package no.nav.toi.rekrutteringstreff.eier

import io.javalin.http.Context
import io.javalin.http.ForbiddenResponse
import no.nav.toi.AuthenticatedUser.Companion.extractNavIdent
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.TreffId

fun Context.krevEierEllerUtvikler(eierService: EierService, treffId: TreffId): String {
    authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET)
    val navIdent = extractNavIdent()
    if (!eierService.erEierEllerUtvikler(treffId = treffId, navIdent = navIdent, context = this)) {
        throw ForbiddenResponse("Personen har ikke tilgang til treffgjennomføringen for rekrutteringstreffet")
    }
    return navIdent
}
