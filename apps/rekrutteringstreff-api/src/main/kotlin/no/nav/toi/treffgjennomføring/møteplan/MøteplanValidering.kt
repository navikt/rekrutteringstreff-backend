package no.nav.toi.treffgjennomføring.møteplan

import io.javalin.http.BadRequestResponse
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import java.time.LocalTime

object MøteplanValidering {

    private val KLOKKESLETT = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")

    fun møteoppsett(dto: MøteoppsettRequestDto): Møteoppsett {
        if (!KLOKKESLETT.matches(dto.starttidspunkt)) {
            throw BadRequestResponse("starttidspunkt må være på formatet HH:mm i 24-timers format")
        }
        if (dto.varighetPerMøteMinutter < 1) {
            throw BadRequestResponse("varighetPerMøteMinutter må være minst 1")
        }
        return Møteoppsett(LocalTime.parse(dto.starttidspunkt), dto.varighetPerMøteMinutter)
    }
}
