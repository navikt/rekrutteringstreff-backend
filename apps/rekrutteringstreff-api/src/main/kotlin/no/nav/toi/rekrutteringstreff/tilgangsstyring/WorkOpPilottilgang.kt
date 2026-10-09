package no.nav.toi.rekrutteringstreff.tilgangsstyring

import io.javalin.http.ForbiddenResponse
import io.javalin.router.JavalinDefaultRoutingApi
import no.nav.toi.AuthenticatedUser
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId

class WorkOpPilottilgang(workOpPilotkontorer: List<String>) {
    private val workOpPilotkontorer = workOpPilotkontorer.map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun harPilottilgang(bruker: AuthenticatedUser): Boolean {
        if (bruker.erBorger || bruker.erUtvikler()) return true
        val kontor = bruker.extractKontorId()?.trim() ?: return false
        return kontor in workOpPilotkontorer
    }

    fun krevPilottilgang(bruker: AuthenticatedUser) {
        if (!harPilottilgang(bruker)) throw ForbiddenResponse("WorkOp er kun tilgjengelig for pilotkontorer")
    }
}

private val treffIdParameternavn = listOf("id", "treffId", "rekrutteringstreffId")

fun JavalinDefaultRoutingApi.leggTilWorkOpPilotkontorsjekk(
    workOpPilottilgang: WorkOpPilottilgang,
    rekrutteringstreffRepository: RekrutteringstreffRepository,
): JavalinDefaultRoutingApi {
    beforeMatched { ctx ->
        if (!ctx.path().startsWith("/api/rekrutteringstreff")) return@beforeMatched
        val bruker = ctx.attribute<AuthenticatedUser>("authenticatedUser") ?: return@beforeMatched
        val treffId = treffIdParameternavn
            .firstNotNullOfOrNull { ctx.pathParamMap()[it] }
            ?.takeIf { TreffId.erGyldigId(it) }
            ?.let { TreffId(it) }
            ?: return@beforeMatched
        if (rekrutteringstreffRepository.hentKategori(treffId) == RekrutteringstreffKategori.WORKOP) {
            workOpPilottilgang.krevPilottilgang(bruker)
        }
    }
    return this
}