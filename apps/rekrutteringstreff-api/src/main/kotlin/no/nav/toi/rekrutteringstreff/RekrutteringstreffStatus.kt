package no.nav.toi.rekrutteringstreff

enum class RekrutteringstreffStatus {
    UTKAST, // Før treffet publiseres
    PUBLISERT, // Treffet er publisert og åpent for påmelding. Treffet kan også være under gjennomføring
    FULLFØRT, // Treffet er gjennomført
    AVLYST,
    SLETTET,
}
