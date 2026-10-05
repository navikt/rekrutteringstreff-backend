package no.nav.toi.jobbsoker

import no.nav.toi.JobbsøkerHendelsestype

/**
 * Felles regler for jobbsøkerstatus og svar. Begge utledes fra hendelsesloggen, slik at svar,
 * oppmøte og formidling kan registreres og angres i hvilken som helst rekkefølge.
 *
 * Statuskolonnen i `jobbsoker` lagrer resultatet av [utledStatus], så søk og filtrering kan
 * fortsatt bruke kolonnen.
 */
object Jobbsøkerstatusregler {

    /**
     * Det gjeldende svaret: true for ja, false for nei og null hvis jobbsøkeren ikke har svart,
     * eller svaret er fjernet. Det nyeste svaret gjelder, uavhengig av status.
     *
     * @param hendelser hendelsestypene i kronologisk rekkefølge, eldste først
     */
    fun sisteSvar(hendelser: List<JobbsøkerHendelsestype>): Boolean? =
        hendelser.lastOrNull { it in svarhendelser }?.let(::svarFra)

    /**
     * Statusen er den første gruppen ovenfra som gjelder. Svaret er én gruppe: det nyeste svaret
     * gir SVART_JA eller SVART_NEI. En jobbsøker som har møtt opp og deretter svart nei, har altså
     * status MØTT_OPP, mens [sisteSvar] gir nei.
     *
     * @param hendelser hendelsestypene i kronologisk rekkefølge, eldste først
     */
    fun utledStatus(hendelser: List<JobbsøkerHendelsestype>): JobbsøkerStatus {
        val gyldigSvar = gyldigSvarstatus(hendelser)
        return when {
            erSlettet(hendelser) -> JobbsøkerStatus.SLETTET
            harFåttJobb(hendelser) -> JobbsøkerStatus.FÅTT_JOBB
            harMøttOpp(hendelser) -> JobbsøkerStatus.MØTT_OPP
            gyldigSvar != null -> gyldigSvar
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

    private fun gyldigSvarstatus(hendelser: List<JobbsøkerHendelsestype>): JobbsøkerStatus? =
        when (sisteSvar(hendelser)) {
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

    // INVITERT og SVAR_FJERNET_AV_EIER nullstiller svaret.
    private val svarhendelser = setOf(
        JobbsøkerHendelsestype.INVITERT,
        JobbsøkerHendelsestype.SVAR_FJERNET_AV_EIER,
        JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON,
        JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON_AV_EIER,
        JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON,
        JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON_AV_EIER,
    )

    private fun svarFra(hendelsestype: JobbsøkerHendelsestype): Boolean? = when (hendelsestype) {
        JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON,
        JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON_AV_EIER -> true
        JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON,
        JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON_AV_EIER -> false
        else -> null
    }
}
