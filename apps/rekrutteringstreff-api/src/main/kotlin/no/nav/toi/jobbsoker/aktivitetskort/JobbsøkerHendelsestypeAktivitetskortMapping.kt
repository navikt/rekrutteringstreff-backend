package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.JobbsøkerHendelsestype

sealed interface AktivitetskortHendelseskontekst {
    data object Invitasjon : AktivitetskortHendelseskontekst

    data class SvarOgTreffstatus(
        val svar: Boolean?,
        val svarAvgittAvEier: Boolean,
        val treffstatus: AktivitetskortTreffstatus? = null,
    ) : AktivitetskortHendelseskontekst

    data class Treffoppdatering(
        val inkluderEndringsnotifikasjon: Boolean,
    ) : AktivitetskortHendelseskontekst
}

fun JobbsøkerHendelsestype.tilAktivitetskortHendelseskontekst(): AktivitetskortHendelseskontekst? = when (this) {
    JobbsøkerHendelsestype.INVITERT ->
        AktivitetskortHendelseskontekst.Invitasjon
    JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON ->
        AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = true, svarAvgittAvEier = false)
    JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON_AV_EIER ->
        AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = true, svarAvgittAvEier = true)
    JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON ->
        AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = false, svarAvgittAvEier = false)
    JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON_AV_EIER ->
        AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = false, svarAvgittAvEier = true)
    JobbsøkerHendelsestype.SVAR_FJERNET_AV_EIER ->
        AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = null, svarAvgittAvEier = true)
    JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST ->
        AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = true, svarAvgittAvEier = false, treffstatus = AktivitetskortTreffstatus.AVLYST)
    JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT ->
        AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = true, svarAvgittAvEier = false, treffstatus = AktivitetskortTreffstatus.FULLFØRT)
    JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST ->
        AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = null, svarAvgittAvEier = false, treffstatus = AktivitetskortTreffstatus.AVLYST)
    JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT ->
        AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = null, svarAvgittAvEier = false, treffstatus = AktivitetskortTreffstatus.FULLFØRT)
    JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING ->
        AktivitetskortHendelseskontekst.Treffoppdatering(inkluderEndringsnotifikasjon = false)
    JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON ->
        AktivitetskortHendelseskontekst.Treffoppdatering(inkluderEndringsnotifikasjon = true)
    else -> null
}
