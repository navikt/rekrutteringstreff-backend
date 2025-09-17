package no.nav.toi


enum class JobbsøkerHendelsestype {
    OPPRETT, OPPDATER, SLETT, INVITER, MØT_OPP, IKKE_MØT_OPP, SVAR_JA_TIL_INVITASJON, SVAR_NEI_TIL_INVITASJON, AKTIVITETSKORT_OPPRETTELSE_FEIL
}

enum class ArbeidsgiverHendelsestype {
    OPPRETT, OPPDATER, SLETT
}

enum class RekrutteringstreffHendelsestype {
    OPPRETT, OPPDATER, SLETT, PUBLISER, GJENÅPN, FULLFØR, AVLYS
}

enum class AktørType { ARRANGØR, JOBBSØKER, ARBEIDSGIVER }