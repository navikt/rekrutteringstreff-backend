package no.nav.toi.rekrutteringstreff.dto

import no.nav.toi.rekrutteringstreff.HendelseRessurs
import java.time.ZonedDateTime

data class FellesHendelseOutboundDto(
    val id: String,
    val ressurs: HendelseRessurs,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktørIdentifikasjon: String?,
    // For jobbsøker: fødselsnummer, for arbeidsgiver: orgnr, for treff: null
    val subjektId: String? = null,
    // For jobbsøker: "fornavn etternavn", for arbeidsgiver: orgnavn, for treff: null
    val subjektNavn: String? = null
)
