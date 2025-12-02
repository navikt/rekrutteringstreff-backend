package no.nav.toi.jobbsoker

import java.time.ZonedDateTime

/**
 * Data-klasse som representerer meldingen fra rekrutteringstreff-kandidatvarsel-api
 * om svar på minside-varsler for rekrutteringstreff.
 */
data class MinsideVarselSvarData(
    val varselId: String,
    val avsenderReferanseId: String,
    val fnr: String,
    val eksternStatus: String?,
    val minsideStatus: String?,
    val opprettet: ZonedDateTime?,
    val avsenderNavident: String?,
    val eksternFeilmelding: String?,
    val eksternKanal: String?,
    val mal: String?
)
