package no.nav.toi.rekrutteringstreff.sok

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration

class RekrutteringstreffSokService(
    private val repository: RekrutteringstreffSokRepository,
) {
    companion object {
        private const val TREGT_SOK_TERSKEL_MS = 500L
    }

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun sok(
        request: RekrutteringstreffSokRequest,
        navIdent: String,
        kontorId: String?,
    ): RekrutteringstreffSokRespons {
        val startTidNanos = System.nanoTime()
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

        val respons = RekrutteringstreffSokRespons(
            treff = treff,
            antallTotalt = antallTotalt,
            side = request.side,
            antallPerSide = request.antallPerSide,
            statusaggregering = statusaggregering,
        )

        val varighetMs = Duration.ofNanos(System.nanoTime() - startTidNanos).toMillis()
        if (varighetMs > TREGT_SOK_TERSKEL_MS) {
            logger.warn(
                "Rekrutteringstreff-søk brukte {} ms: visning={}, sortering={}, side={}, antallPerSide={}, antallStatuser={}, antallKontorer={}, antallTotalt={}",
                varighetMs,
                request.visning.jsonVerdi,
                request.sortering.jsonVerdi,
                request.side,
                request.antallPerSide,
                request.statuser?.size ?: 0,
                request.kontorer?.size ?: 0,
                antallTotalt,
            )
        }

        return respons
    }
}
