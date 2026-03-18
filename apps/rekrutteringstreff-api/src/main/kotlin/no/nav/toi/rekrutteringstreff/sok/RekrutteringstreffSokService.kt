package no.nav.toi.rekrutteringstreff.sok

class RekrutteringstreffSokService(
    private val repository: RekrutteringstreffSokRepository,
) {
    fun sok(
        request: RekrutteringstreffSokRequest,
        navIdent: String,
        kontorId: String?,
    ): RekrutteringstreffSokRespons {
        val (treff, totaltAntall) = repository.sok(
            navIdent = navIdent,
            kontorId = kontorId,
            visningsstatuser = request.visningsstatuser,
            kontorer = request.kontorer,
            visning = request.visning,
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
            totaltAntall = totaltAntall,
            side = request.side,
            antallPerSide = request.antallPerSide,
            statusaggregering = statusaggregering,
        )
    }
}
