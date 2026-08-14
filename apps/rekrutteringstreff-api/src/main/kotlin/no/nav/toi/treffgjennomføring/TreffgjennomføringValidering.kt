package no.nav.toi.treffgjennomføring

import io.javalin.http.BadRequestResponse
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomføring.dto.RomDto
import no.nav.toi.treffgjennomføring.dto.VurderingDto
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

object TreffgjennomføringValidering {

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

        val personer = rom.flatMap { it.jobbsøkere }.map { PersonTreffId(it) }
        if (personer.size != personer.toSet().size) {
            throw BadRequestResponse("En jobbsøker kan bare stå i ett rom")
        }
        val fremmøtte = oppmøte.toSet()
        personer.firstOrNull { it !in fremmøtte }?.let {
            throw BadRequestResponse("Bare fremmøtte jobbsøkere kan plasseres i rom")
        }
        if (personer.size != fremmøtte.size) {
            throw BadRequestResponse("Alle fremmøtte jobbsøkere må plasseres i et rom")
        }

        return rom.sortedBy { it.romnummer }
            .map { Rom(it.romnummer, it.jobbsøkere.map(::PersonTreffId)) }
    }

    fun intervjufordeling(inkluderte: List<String>, ekskluderte: List<String>) {
        if (inkluderte.size != inkluderte.toSet().size || ekskluderte.size != ekskluderte.toSet().size) {
            throw BadRequestResponse("En jobbsøker kan bare forekomme én gang i hver liste")
        }
        if (inkluderte.toSet().intersect(ekskluderte.toSet()).isNotEmpty()) {
            throw BadRequestResponse("En jobbsøker kan ikke være både inkludert og ekskludert")
        }
    }

    fun vurdering(dto: VurderingDto): Vurdering {
        val dato = dto.andregangsintervjuDato?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                throw BadRequestResponse("andregangsintervjuDato må være på formatet yyyy-MM-dd")
            }
        }
        if (dato != null && !dto.andregangsintervju) {
            throw BadRequestResponse("andregangsintervjuDato kan ikke settes uten andregangsintervju")
        }
        val notater = dto.notater.map { navn ->
            Vurderingsnotat.entries.firstOrNull { it.name == navn }
                ?: throw BadRequestResponse("Ukjent notat: $navn")
        }
        return Vurdering(
            personTreffId = PersonTreffId(dto.personTreffId),
            arbeidsgiverTreffId = ArbeidsgiverTreffId(dto.arbeidsgiverTreffId),
            vurdering = dto.vurdering,
            notater = notater,
            andregangsintervju = dto.andregangsintervju,
            andregangsintervjuDato = dato,
            jobbtilbud = dto.jobbtilbud,
        )
    }
}
