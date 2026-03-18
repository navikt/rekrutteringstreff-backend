package no.nav.toi.rekrutteringstreff.sok

class RekrutteringstreffSokService(
    private val repository: RekrutteringstreffSokRepository,
) {
    fun sok(
        request: RekrutteringstreffSokRequest,
        navIdent: String,
        kontorId: String?,
    ): RekrutteringstreffSokRespons {
        val (treff, antallTotalt) = repository.sok(
            navIdent = navIdent,
            kontorId = kontorId,
            statuser = request.statuser,
            kontorer = request.kontorer,
            visning = request.visning,
            sortering = request.sortering,
            side = request.side,
            antallPerSide = request.antallPerSide,
        )

        val statusaggregering = repository.statusaggregering(
            navIdent = navIdent,
            kontorId = kontorId,
            kontorer = request.kontorer,
            visning = request.visning,
        )

        return RekrutteringstreffSokRespons(
            treff = treff,
            antallTotalt = antallTotalt,
            side = request.side,
            antallPerSide = request.antallPerSide,
            statusaggregering = statusaggregering,
        )
    }
}
