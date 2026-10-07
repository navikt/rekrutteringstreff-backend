package no.nav.toi.jobbsoker

import no.nav.toi.JobbsøkerHendelsestype

/** Prioriteringen mellom statusene står i [Jobbsøkerstatusregler.utledStatus]. */
enum class JobbsøkerStatus {
    LAGT_TIL, INVITERT, SVART_JA, SVART_NEI, MØTT_OPP, FÅTT_JOBB, SLETTET
}

/**
 * Felles regler for jobbsøkerstatus og svar. Begge utledes fra hendelsesloggen, slik at svar,
 * oppmøte og formidling kan registreres og angres i hvilken som helst rekkefølge.
 *
 * Statuskolonnen i `jobbsoker` lagrer resultatet av [utledStatus], så søk og filtrering kan
 * fortsatt bruke kolonnen.
 *
 * Funksjonene som tar hendelser, tar hendelsestypene sortert med eldste først.
 */
object Jobbsøkerstatusregler {

    /**
     * Den første regelen ovenfra som gjelder, bestemmer statusen. En jobbsøker som har møtt opp
     * og deretter svart nei, har altså status MØTT_OPP, mens [gjeldendeSvar] gir nei.
     *
     * SLETTET ligger rett over LAGT_TIL fordi bare LAGT_TIL kan slettes, så ingen regel over er
     * aktiv når personen slettes. At slettet er en endestasjon, sikres av guardene hos de som
     * skriver hendelser: oppmøte, svar, formidling og invitasjon avviser slettede jobbsøkere.
     * Bare OPPRETTET (personen legges til på nytt) opphever slettingen.
     */
    fun utledStatus(hendelser: List<JobbsøkerHendelsestype>): JobbsøkerStatus {
        val svarstatus = svarstatus(hendelser)
        return when {
            harFåttJobb(hendelser) -> JobbsøkerStatus.FÅTT_JOBB
            harMøttOpp(hendelser) -> JobbsøkerStatus.MØTT_OPP
            svarstatus != null -> svarstatus
            erInvitert(hendelser) -> JobbsøkerStatus.INVITERT
            erSlettet(hendelser) -> JobbsøkerStatus.SLETTET
            else -> JobbsøkerStatus.LAGT_TIL
        }
    }

    /** true for ja, false for nei, null for ingen svar. Det nyeste svaret gjelder, uavhengig av status. */
    fun gjeldendeSvar(hendelser: List<JobbsøkerHendelsestype>): Boolean? =
        when (hendelser.lastOrNull { it in jaSvar || it in neiSvar || it in nullstillerSvar }) {
            in jaSvar -> true
            in neiSvar -> false
            else -> null
        }

    /**
     * Den som har svart ja, får aktivitetskortet avbrutt og et varsel om avlysningen, uansett
     * status. Den som er invitert uten å ha svart, får kortet avbrutt uten varsel. Alle andre får
     * ingen hendelse, og kortet blir stående: et nei har allerede avbrutt det, og den som ikke er
     * invitert, har ikke kort.
     */
    fun hendelseNårTreffetAvlyses(status: JobbsøkerStatus, harSvartJa: Boolean): JobbsøkerHendelsestype? {
        val avbrytKortetOgVarsle = JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST
        val avbrytKortet = JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST
        return when {
            harSvartJa -> avbrytKortetOgVarsle
            status == JobbsøkerStatus.INVITERT -> avbrytKortet
            else -> null
        }
    }

    /**
     * Den som har møtt opp, fått jobb eller svart ja, får aktivitetskortet fullført, uansett svar.
     * Den som er invitert uten å ha svart, får kortet avbrutt, som ved et nei. Ingen hendelse
     * betyr at kortet blir stående: et nei har allerede avbrutt det, og den som ikke er
     * invitert, har ikke kort.
     *
     * Aktivitetskortet fullføres bare med svar ja i meldingen, så hendelsen heter
     * SVART_JA_TREFF_FULLFØRT også for den som møtte opp uten å svare ja.
     */
    fun hendelseNårTreffetFullføres(status: JobbsøkerStatus, erInvitert: Boolean): JobbsøkerHendelsestype? {
        val fullførKortet = JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT
        val avbrytKortet = JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT
        if (!erInvitert) return null
        return when (status) {
            JobbsøkerStatus.FÅTT_JOBB, JobbsøkerStatus.MØTT_OPP, JobbsøkerStatus.SVART_JA -> fullførKortet
            JobbsøkerStatus.INVITERT -> avbrytKortet
            JobbsøkerStatus.SVART_NEI -> null
            JobbsøkerStatus.SLETTET, JobbsøkerStatus.LAGT_TIL -> null
        }
    }

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

    private fun erSlettet(hendelser: List<JobbsøkerHendelsestype>) =
        erGjeldende(hendelser, JobbsøkerHendelsestype.SLETTET, JobbsøkerHendelsestype.OPPRETTET)

    private fun harFåttJobb(hendelser: List<JobbsøkerHendelsestype>) =
        erGjeldende(hendelser, JobbsøkerHendelsestype.FÅTT_JOBB, JobbsøkerHendelsestype.ANGRE_FÅTT_JOBB)

    private fun harMøttOpp(hendelser: List<JobbsøkerHendelsestype>) =
        erGjeldende(
            hendelser,
            JobbsøkerHendelsestype.REGISTRERT_OPPMØTE,
            JobbsøkerHendelsestype.REGISTRERT_OPPMØTE_FJERNET
        )

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
