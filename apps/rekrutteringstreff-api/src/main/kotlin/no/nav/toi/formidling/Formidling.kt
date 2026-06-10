package no.nav.toi.formidling

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.ZonedDateTime
import java.util.UUID

data class Formidling(
    val formidlingId: Long,
    val id: UUID,
    val treffId: TreffId,
    val jobbsøkerPersonTreffId: PersonTreffId,
    val arbeidsgiverTreffId: ArbeidsgiverTreffId,
    val stillingId: UUID,
    val kandidatlisteId: UUID?,
    val utfallSendtTidspunkt: ZonedDateTime?,
    val opprettetTidspunkt: ZonedDateTime,
)
