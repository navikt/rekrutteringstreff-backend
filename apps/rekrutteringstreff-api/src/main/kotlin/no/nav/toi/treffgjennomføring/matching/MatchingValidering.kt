package no.nav.toi.treffgjennomføring.matching

import io.javalin.http.BadRequestResponse

object MatchingValidering {

    fun intervjufordeling(inkluderte: List<String>, ekskluderte: List<String>) {
        if (inkluderte.harDuplikater() || ekskluderte.harDuplikater()) {
            throw BadRequestResponse("En jobbsøker kan bare forekomme én gang i hver liste")
        }
        if (inkluderte.any { it in ekskluderte }) {
            throw BadRequestResponse("En jobbsøker kan ikke være både inkludert og ekskludert")
        }
    }

    private fun List<String>.harDuplikater() = size != toSet().size
}
