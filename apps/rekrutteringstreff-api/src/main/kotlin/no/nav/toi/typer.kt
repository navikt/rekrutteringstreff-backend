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
    MOTTATT_SVAR_FRA_MINSIDE;

    companion object {
        val svarJa: Set<JobbsøkerHendelsestype> = setOf(
            SVART_JA_TIL_INVITASJON,
            SVART_JA_TIL_INVITASJON_AV_EIER,
        )
        val svarNei: Set<JobbsøkerHendelsestype> = setOf(
            SVART_NEI_TIL_INVITASJON,
            SVART_NEI_TIL_INVITASJON_AV_EIER,
        )
        val svarFjernet: Set<JobbsøkerHendelsestype> = setOf(
            SVAR_FJERNET_AV_EIER,
        )
        val avgittAvEier: Set<JobbsøkerHendelsestype> = setOf(
            SVART_JA_TIL_INVITASJON_AV_EIER,
            SVART_NEI_TIL_INVITASJON_AV_EIER,
            SVAR_FJERNET_AV_EIER,
        )
        val svar: Set<JobbsøkerHendelsestype> = svarJa + svarNei + svarFjernet

        fun svarSomBoolean(type: JobbsøkerHendelsestype): Boolean? = when (type) {
            in svarJa -> true
            in svarNei -> false
            in svarFjernet -> null
            else -> error("$type er ikke en svar-hendelse")
        }
    }
}

enum class ArbeidsgiverHendelsestype {
    OPPRETTET, OPPDATERT, SLETTET, REAKTIVERT, BEHOV_ENDRET
}

enum class RekrutteringstreffHendelsestype {
    OPPRETTET, OPPDATERT, SLETTET, PUBLISERT, GJENÅPNET, FULLFØRT, AVLYST, AVPUBLISERT, TREFF_ENDRET_ETTER_PUBLISERING, EIER_LAGT_TIL, EIER_FJERNET, KONTOR_LAGT_TIL
}

enum class AktørType { ARRANGØR, JOBBSØKER, ARBEIDSGIVER, SYSTEM }
