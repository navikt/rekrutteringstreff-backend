package no.nav.toi.jobbsoker

import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.JobbsøkerHendelsestype.ANGRE_FÅTT_JOBB
import no.nav.toi.JobbsøkerHendelsestype.INVITERT
import no.nav.toi.JobbsøkerHendelsestype.OPPRETTET
import no.nav.toi.JobbsøkerHendelsestype.REGISTRERT_OPPMØTE
import no.nav.toi.JobbsøkerHendelsestype.REGISTRERT_OPPMØTE_FJERNET
import no.nav.toi.JobbsøkerHendelsestype.SVAR_FJERNET_AV_EIER
import no.nav.toi.JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON
import no.nav.toi.JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON_AV_EIER
import no.nav.toi.JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON
import no.nav.toi.JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON_AV_EIER
import no.nav.toi.JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING
import no.nav.toi.JobbsøkerHendelsestype.VURDERT
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JobbsøkerstatusreglerTest {

    private fun status(vararg hendelser: JobbsøkerHendelsestype) = Jobbsøkerstatusregler.utledStatus(hendelser.toList())
    private fun svar(vararg hendelser: JobbsøkerHendelsestype) = Jobbsøkerstatusregler.sisteSvar(hendelser.toList())

    @Test
    fun `alle statusene kan utledes`() {
        val utledet = setOf(
            status(),
            status(OPPRETTET, INVITERT),
            status(OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON),
            status(OPPRETTET, INVITERT, SVART_NEI_TIL_INVITASJON),
            status(OPPRETTET, REGISTRERT_OPPMØTE),
            status(OPPRETTET, JobbsøkerHendelsestype.FÅTT_JOBB),
            status(OPPRETTET, JobbsøkerHendelsestype.SLETTET),
        )

        assertThat(utledet).containsExactlyInAnyOrder(*JobbsøkerStatus.entries.toTypedArray())
    }

    @Test
    fun `slettet går foran fått jobb, møtt opp og svar`() {
        assertThat(
            status(OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON, REGISTRERT_OPPMØTE, JobbsøkerHendelsestype.FÅTT_JOBB, JobbsøkerHendelsestype.SLETTET)
        ).isEqualTo(JobbsøkerStatus.SLETTET)
    }

    @Test
    fun `uten hendelser er statusen lagt til og svaret tomt`() {
        assertThat(status()).isEqualTo(JobbsøkerStatus.LAGT_TIL)
        assertThat(svar()).isNull()
    }

    @Test
    fun `det nyeste svaret gjelder, uansett hvem som svarte`() {
        assertThat(svar(OPPRETTET, INVITERT)).isNull()
        assertThat(svar(OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON)).isTrue()
        assertThat(svar(OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON, SVART_NEI_TIL_INVITASJON_AV_EIER)).isFalse()
        assertThat(svar(OPPRETTET, INVITERT, SVART_NEI_TIL_INVITASJON, SVART_JA_TIL_INVITASJON_AV_EIER)).isTrue()
        assertThat(svar(OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON, SVAR_FJERNET_AV_EIER)).isNull()
        assertThat(svar(OPPRETTET, INVITERT, SVART_NEI_TIL_INVITASJON, TREFF_ENDRET_ETTER_PUBLISERING, VURDERT)).isFalse()
    }

    @Test
    fun `svarstatus følger det nyeste svaret`() {
        assertThat(status(OPPRETTET, INVITERT)).isEqualTo(JobbsøkerStatus.INVITERT)
        assertThat(status(OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON)).isEqualTo(JobbsøkerStatus.SVART_JA)
        assertThat(status(OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON, SVART_NEI_TIL_INVITASJON)).isEqualTo(JobbsøkerStatus.SVART_NEI)
        assertThat(status(OPPRETTET, INVITERT, SVART_NEI_TIL_INVITASJON, SVAR_FJERNET_AV_EIER)).isEqualTo(JobbsøkerStatus.INVITERT)
    }

    @Test
    fun `svar etter oppmøte endrer svaret, men statusen er fortsatt møtt opp`() {
        val hendelser = arrayOf(OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON, REGISTRERT_OPPMØTE, SVART_NEI_TIL_INVITASJON)

        assertThat(status(*hendelser)).isEqualTo(JobbsøkerStatus.MØTT_OPP)
        assertThat(svar(*hendelser)).isFalse()
    }

    @Test
    fun `når oppmøtet fjernes etter et nytt svar, blir statusen det nyeste svaret`() {
        assertThat(
            status(OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON, REGISTRERT_OPPMØTE, SVART_NEI_TIL_INVITASJON, REGISTRERT_OPPMØTE_FJERNET)
        ).isEqualTo(JobbsøkerStatus.SVART_NEI)
    }

    @Test
    fun `svar etter at oppmøtet er fjernet og registrert på nytt gir møtt opp og nyeste svar`() {
        val hendelser = arrayOf(
            OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON,
            REGISTRERT_OPPMØTE, REGISTRERT_OPPMØTE_FJERNET,
            SVART_NEI_TIL_INVITASJON_AV_EIER,
            REGISTRERT_OPPMØTE,
        )

        assertThat(status(*hendelser)).isEqualTo(JobbsøkerStatus.MØTT_OPP)
        assertThat(svar(*hendelser)).isFalse()
    }

    @Test
    fun `fått jobb går foran møtt opp, og angring gir møtt opp tilbake`() {
        assertThat(status(OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON, REGISTRERT_OPPMØTE, JobbsøkerHendelsestype.FÅTT_JOBB))
            .isEqualTo(JobbsøkerStatus.FÅTT_JOBB)
        assertThat(status(OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON, REGISTRERT_OPPMØTE, JobbsøkerHendelsestype.FÅTT_JOBB, ANGRE_FÅTT_JOBB))
            .isEqualTo(JobbsøkerStatus.MØTT_OPP)
    }

    @Test
    fun `angret formidling etter fjernet oppmøte og nytt svar gir det nyeste svaret`() {
        assertThat(
            status(
                OPPRETTET, INVITERT, SVART_JA_TIL_INVITASJON,
                REGISTRERT_OPPMØTE, JobbsøkerHendelsestype.FÅTT_JOBB,
                REGISTRERT_OPPMØTE_FJERNET, SVART_NEI_TIL_INVITASJON,
                ANGRE_FÅTT_JOBB,
            )
        ).isEqualTo(JobbsøkerStatus.SVART_NEI)
    }

    @Test
    fun `oppmøte uten invitasjon gir møtt opp, og lagt til når oppmøtet fjernes`() {
        assertThat(status(OPPRETTET, REGISTRERT_OPPMØTE)).isEqualTo(JobbsøkerStatus.MØTT_OPP)
        assertThat(status(OPPRETTET, REGISTRERT_OPPMØTE, REGISTRERT_OPPMØTE_FJERNET)).isEqualTo(JobbsøkerStatus.LAGT_TIL)
    }

    @Test
    fun `slettet gjelder til jobbsøkeren legges til på nytt`() {
        assertThat(status(OPPRETTET, JobbsøkerHendelsestype.SLETTET)).isEqualTo(JobbsøkerStatus.SLETTET)
        assertThat(status(OPPRETTET, JobbsøkerHendelsestype.SLETTET, OPPRETTET)).isEqualTo(JobbsøkerStatus.LAGT_TIL)
        assertThat(status(OPPRETTET, JobbsøkerHendelsestype.SLETTET, OPPRETTET, INVITERT)).isEqualTo(JobbsøkerStatus.INVITERT)
    }
}
