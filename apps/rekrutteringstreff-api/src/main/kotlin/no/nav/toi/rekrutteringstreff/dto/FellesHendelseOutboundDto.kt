package no.nav.toi.rekrutteringstreff.dto

import no.nav.toi.rekrutteringstreff.HendelseRessurs
import java.time.ZonedDateTime

data class FellesHendelseOutboundDto(
    val id: String,
    val ressurs: HendelseRessurs,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktørIdentifikasjon: String?
)
