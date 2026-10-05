package no.nav.toi.jobbsoker

import no.nav.toi.JobbsøkerHendelsestype

/**
 * Felles regler for jobbsøkerstatus og svar. Begge utledes fra hendelsesloggen, slik at svar,
 * oppmøte og formidling kan registreres og angres i hvilken som helst rekkefølge.
 *
 * Statuskolonnen i `jobbsoker` lagrer resultatet av [utledStatus], så søk og filtrering kan
 * fortsatt bruke kolonnen.
 *
 * Alle funksjonene tar hendelsestypene sortert med eldste først.
 */
object Jobbsøkerstatusregler {

    private val jaSvar = setOf(
        JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON,
        JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON_AV_EIER,
    )

    private val neiSvar = setOf(
        JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON,
        JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON_AV_EIER,
    )

    private val nullstillerSvar = setOf(
        JobbsøkerHendelsestype.INVITERT,
        JobbsøkerHendelsestype.SVAR_FJERNET_AV_EIER,
    )

    /** true for ja, false for nei, null for ingen svar. Det nyeste svaret gjelder, uavhengig av status. */
    fun gjeldendeSvar(hendelser: List<JobbsøkerHendelsestype>): Boolean? =
        when (hendelser.lastOrNull { it in jaSvar || it in neiSvar || it in nullstillerSvar }) {
            in jaSvar -> true
            in neiSvar -> false
            else -> null
        }

    /**
     * Den første regelen ovenfra som gjelder, bestemmer statusen. En jobbsøker som har møtt opp
     * og deretter svart nei, har altså status MØTT_OPP, mens [gjeldendeSvar] gir nei.
     */
    fun utledStatus(hendelser: List<JobbsøkerHendelsestype>): JobbsøkerStatus {
        val svarstatus = svarstatus(hendelser)
        return when {
            erSlettet(hendelser) -> JobbsøkerStatus.SLETTET
            harFåttJobb(hendelser) -> JobbsøkerStatus.FÅTT_JOBB
            harMøttOpp(hendelser) -> JobbsøkerStatus.MØTT_OPP
            svarstatus != null -> svarstatus
            erInvitert(hendelser) -> JobbsøkerStatus.INVITERT
            else -> JobbsøkerStatus.LAGT_TIL
        }
    }

    private fun erSlettet(hendelser: List<JobbsøkerHendelsestype>) =
        erGjeldende(hendelser, JobbsøkerHendelsestype.SLETTET, JobbsøkerHendelsestype.OPPRETTET)

    private fun harFåttJobb(hendelser: List<JobbsøkerHendelsestype>) =
        erGjeldende(hendelser, JobbsøkerHendelsestype.FÅTT_JOBB, JobbsøkerHendelsestype.ANGRE_FÅTT_JOBB)

    private fun harMøttOpp(hendelser: List<JobbsøkerHendelsestype>) =
        erGjeldende(hendelser, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE_FJERNET)

    private fun svarstatus(hendelser: List<JobbsøkerHendelsestype>): JobbsøkerStatus? =
        when (gjeldendeSvar(hendelser)) {
            true -> JobbsøkerStatus.SVART_JA
            false -> JobbsøkerStatus.SVART_NEI
            null -> null
        }

    private fun erInvitert(hendelser: List<JobbsøkerHendelsestype>) =
        JobbsøkerHendelsestype.INVITERT in hendelser

    private fun erGjeldende(
        hendelser: List<JobbsøkerHendelsestype>,
        registrert: JobbsøkerHendelsestype,
        opphevet: JobbsøkerHendelsestype,
    ): Boolean = hendelser.lastOrNull { it == registrert || it == opphevet } == registrert
}
