package no.nav.toi.arbeidsgiver.dto

import java.time.ZonedDateTime

data class ArbeidsgiverHendelseMedArbeidsgiverDataOutboundDto(
    val id: String,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktøridentifikasjon: String?,
    val orgnr: String,
    val orgnavn: String,
)
