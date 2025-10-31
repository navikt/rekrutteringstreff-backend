package no.nav.toi.rekrutteringstreff.ki.dto

import java.time.ZonedDateTime

data class KiLoggOutboundDto(
    val id: String,
    val opprettetTidspunkt: ZonedDateTime,
    val treffId: String,
    val tittel: String?,
    val feltType: String,
    val spørringFraFrontend: String,
    val spørringFiltrert: String,
    val systemprompt: String?,
    val bryterRetningslinjer: Boolean,
    val begrunnelse: String?,
    val kiNavn: String,
    val kiVersjon: String,
    val svartidMs: Int,
    val lagret: Boolean,
    val manuellKontrollBryterRetningslinjer: Boolean?,
    val manuellKontrollUtfortAv: String?,
    val manuellKontrollTidspunkt: ZonedDateTime?,
    val promptVersjonsnummer: Int?,
    val promptEndretTidspunkt: ZonedDateTime?,
    val promptHash: String?
)