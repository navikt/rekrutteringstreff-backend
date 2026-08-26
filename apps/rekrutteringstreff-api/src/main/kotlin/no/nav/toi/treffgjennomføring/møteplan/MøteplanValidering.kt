package no.nav.toi.treffgjennomføring.møteplan

import io.javalin.http.BadRequestResponse
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomføring.dto.RomDto
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

    fun romfordeling(rom: List<RomDto>, antallRom: Int, oppmøte: List<PersonTreffId>): List<Rom> {
        if (rom.size != antallRom) {
            throw BadRequestResponse("Romfordelingen må inneholde nøyaktig $antallRom rom, fikk ${rom.size}")
        }
        val romnumre = rom.map { it.romnummer }.toSet()
        if (romnumre != (1..antallRom).toSet()) {
            throw BadRequestResponse("Romnumrene må være unike og dekke 1..$antallRom")
        }

        val personTreffIder = rom.flatMap { it.jobbsøkere }.map { PersonTreffId(it) }
        if (personTreffIder.size != personTreffIder.toSet().size) {
            throw BadRequestResponse("En jobbsøker kan bare stå i ett rom")
        }
        val fremmøtte = oppmøte.toSet()
        personTreffIder.firstOrNull { it !in fremmøtte }?.let {
            throw BadRequestResponse("Bare fremmøtte jobbsøkere kan plasseres i rom")
        }
        if (personTreffIder.size != fremmøtte.size) {
            throw BadRequestResponse("Alle fremmøtte jobbsøkere må plasseres i et rom")
        }

        return rom.sortedBy { it.romnummer }
            .map { Rom(it.romnummer, it.jobbsøkere.map(::PersonTreffId)) }
    }
}
