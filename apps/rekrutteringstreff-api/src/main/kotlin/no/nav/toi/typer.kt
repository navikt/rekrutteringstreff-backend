package no.nav.toi


enum class JobbsøkerHendelsestype {
    OPPRETT, OPPDATER, SLETT, INVITER, SVAR_JA_TIL_INVITASJON, SVAR_NEI_TIL_INVITASJON
}

enum class ArbeidsgiverHendelsestype {
    OPPRETT, OPPDATER, SLETT
}

enum class RekrutteringstreffHendelsestype {
    OPPRETT, OPPDATER, SLETT, PUBLISER, AVSLUTT, AVSLUTT_ARRANGEMENT, AVSLUTT_INVITASJON, AVSLUTT_OPPFØLGING
}

enum class AktørType { ARRANGØR, JOBBSØKER, ARBEIDSGIVER }