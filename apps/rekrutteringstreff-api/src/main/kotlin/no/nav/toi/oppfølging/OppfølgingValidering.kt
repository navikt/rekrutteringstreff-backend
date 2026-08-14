package no.nav.toi.oppfølging

import io.javalin.http.BadRequestResponse
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.treffgjennomføring.dto.VurderingDto
import java.time.LocalDate
import java.time.format.DateTimeParseException

object OppfølgingValidering {

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
