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
     * Høyeste prioritet først. Når flere tilstander gjelder samtidig, er det den øverste som blir
     * statusen. En jobbsøker som har møtt opp og deretter svart nei, har altså status MØTT_OPP,
     * mens [sisteSvar] gir nei.
     */
    val prioritet: List<JobbsøkerStatus> = listOf(
        JobbsøkerStatus.SLETTET,
        JobbsøkerStatus.FÅTT_JOBB,
        JobbsøkerStatus.MØTT_OPP,
        JobbsøkerStatus.SVART_JA,
        JobbsøkerStatus.SVART_NEI,
        JobbsøkerStatus.INVITERT,
        JobbsøkerStatus.LAGT_TIL,
    )

    /**
     * Det gjeldende svaret: true for ja, false for nei og null hvis jobbsøkeren ikke har svart,
     * eller svaret er fjernet. Det nyeste svaret gjelder, uavhengig av status.
     *
     * @param hendelser hendelsestypene i kronologisk rekkefølge, eldste først
     */
    fun sisteSvar(hendelser: List<JobbsøkerHendelsestype>): Boolean? =
        hendelser.lastOrNull { it in svarhendelser }?.let(::svarFra)

    /**
     * @param hendelser hendelsestypene i kronologisk rekkefølge, eldste først
     */
    fun utledStatus(hendelser: List<JobbsøkerHendelsestype>): JobbsøkerStatus {
        val gjeldende = buildSet {
            add(JobbsøkerStatus.LAGT_TIL)
            if (JobbsøkerHendelsestype.INVITERT in hendelser) add(JobbsøkerStatus.INVITERT)
            when (sisteSvar(hendelser)) {
                true -> add(JobbsøkerStatus.SVART_JA)
                false -> add(JobbsøkerStatus.SVART_NEI)
                null -> Unit
            }
            if (erGjeldende(hendelser, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE_FJERNET)) {
                add(JobbsøkerStatus.MØTT_OPP)
            }
            if (erGjeldende(hendelser, JobbsøkerHendelsestype.FÅTT_JOBB, JobbsøkerHendelsestype.ANGRE_FÅTT_JOBB)) {
                add(JobbsøkerStatus.FÅTT_JOBB)
            }
            if (erGjeldende(hendelser, JobbsøkerHendelsestype.SLETTET, JobbsøkerHendelsestype.OPPRETTET)) {
                add(JobbsøkerStatus.SLETTET)
            }
        }
        return prioritet.first { it in gjeldende }
    }

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
