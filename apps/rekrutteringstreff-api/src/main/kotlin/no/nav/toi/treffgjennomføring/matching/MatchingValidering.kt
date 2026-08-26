package no.nav.toi.treffgjennomføring.matching

import io.javalin.http.BadRequestResponse

object MatchingValidering {

    fun intervjufordeling(inkluderte: List<String>, ekskluderte: List<String>) {
        if (inkluderte.size != inkluderte.toSet().size || ekskluderte.size != ekskluderte.toSet().size) {
            throw BadRequestResponse("En jobbsøker kan bare forekomme én gang i hver liste")
        }
        if (inkluderte.toSet().intersect(ekskluderte.toSet()).isNotEmpty()) {
            throw BadRequestResponse("En jobbsøker kan ikke være både inkludert og ekskludert")
        }
    }
}
