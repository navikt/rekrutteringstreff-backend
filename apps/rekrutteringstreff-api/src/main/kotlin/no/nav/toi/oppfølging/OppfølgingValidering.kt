package no.nav.toi.oppfølging

import io.javalin.http.BadRequestResponse
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.treffgjennomføring.dto.VurderingDto
import java.time.LocalDate
import java.time.format.DateTimeParseException

object OppfølgingValidering {

    fun vurdering(dto: VurderingDto): Vurdering {
        val dato = dto.avtaltIntervjuDato?.let {
            try {
                LocalDate.parse(it)
            } catch (_: DateTimeParseException) {
                throw BadRequestResponse("avtaltIntervjuDato må være på formatet yyyy-MM-dd")
            }
        }
        if (dato != null && !dto.avtaltIntervju) {
            throw BadRequestResponse("avtaltIntervjuDato kan ikke settes uten avtaltIntervju")
        }
        val notater = dto.vurderingsnotat.map { navn ->
            Vurderingsnotat.entries.firstOrNull { it.name == navn }
                ?: throw BadRequestResponse("Ukjent notat: $navn")
        }
        return Vurdering(
            personTreffId = PersonTreffId(dto.personTreffId),
            arbeidsgiverTreffId = ArbeidsgiverTreffId(dto.arbeidsgiverTreffId),
            vurderingsstatus = dto.vurderingsstatus,
            vurderingsnotat = notater,
            avtaltIntervju = dto.avtaltIntervju,
            avtaltIntervjuDato = dato,
            jobbtilbud = dto.jobbtilbud,
        )
    }
}
