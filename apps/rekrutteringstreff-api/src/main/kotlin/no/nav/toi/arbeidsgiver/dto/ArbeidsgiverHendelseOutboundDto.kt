package no.nav.toi.arbeidsgiver.dto

import java.time.ZonedDateTime

data class ArbeidsgiverHendelseOutboundDto(
    val id: String,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktøridentifikasjon: String?,
)
