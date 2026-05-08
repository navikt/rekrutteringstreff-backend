package no.nav.toi

enum class JobbsøkerHendelsestype {
    OPPRETTET,
    OPPDATERT,
    SLETTET,
    INVITERT,
    SVAR_FJERNET_AV_EIER,
    SVART_JA_TIL_INVITASJON,
    SVART_JA_TIL_INVITASJON_AV_EIER,
    SVART_NEI_TIL_INVITASJON,
    SVART_NEI_TIL_INVITASJON_AV_EIER,
    AKTIVITETSKORT_OPPRETTELSE_FEIL,
    SVART_JA_TREFF_AVLYST,
    SVART_JA_TREFF_FULLFØRT,
    IKKE_SVART_TREFF_AVLYST,
    IKKE_SVART_TREFF_FULLFØRT,
    TREFF_ENDRET_ETTER_PUBLISERING,
    TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON,
    MOTTATT_SVAR_FRA_MINSIDE
}

data class JobbsøkerHendelseskontekst(
    val svar: Boolean?,
    val svarAvgittAvEier: Boolean,
    val treffstatus: String? = null,
)

fun JobbsøkerHendelsestype.tilKontekst(): JobbsøkerHendelseskontekst? = when (this) {
    JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON ->
        JobbsøkerHendelseskontekst(svar = true,  svarAvgittAvEier = false)
    JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON_AV_EIER ->
        JobbsøkerHendelseskontekst(svar = true,  svarAvgittAvEier = true)
    JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON ->
        JobbsøkerHendelseskontekst(svar = false, svarAvgittAvEier = false)
    JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON_AV_EIER ->
        JobbsøkerHendelseskontekst(svar = false, svarAvgittAvEier = true)
    JobbsøkerHendelsestype.SVAR_FJERNET_AV_EIER ->
        JobbsøkerHendelseskontekst(svar = null,  svarAvgittAvEier = true)
    JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST ->
        JobbsøkerHendelseskontekst(svar = true,  svarAvgittAvEier = false, treffstatus = "avlyst")
    JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT ->
        JobbsøkerHendelseskontekst(svar = true,  svarAvgittAvEier = false, treffstatus = "fullført")
    JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST ->
        JobbsøkerHendelseskontekst(svar = null,  svarAvgittAvEier = false, treffstatus = "avlyst")
    JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT ->
        JobbsøkerHendelseskontekst(svar = null,  svarAvgittAvEier = false, treffstatus = "fullført")
    else -> null
}

enum class ArbeidsgiverHendelsestype {
    OPPRETTET, OPPDATERT, SLETTET, REAKTIVERT, BEHOV_ENDRET
}

enum class RekrutteringstreffHendelsestype {
    OPPRETTET, OPPDATERT, SLETTET, PUBLISERT, GJENÅPNET, FULLFØRT, AVLYST, AVPUBLISERT, TREFF_ENDRET_ETTER_PUBLISERING, EIER_LAGT_TIL, EIER_FJERNET, KONTOR_LAGT_TIL
}

enum class AktørType { ARRANGØR, JOBBSØKER, ARBEIDSGIVER, SYSTEM }
