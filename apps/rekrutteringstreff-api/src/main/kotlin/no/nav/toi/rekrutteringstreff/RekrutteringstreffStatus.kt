package no.nav.toi.rekrutteringstreff

enum class RekrutteringstreffStatus {
    UTKAST, // Før treffet publiseres
    PUBLISERT, // Treffet er publisert og åpent for påmelding. Treffet kan også være under gjennomføring
    FULLFØRT, // Treffet er gjennomført
    AVLYST,
    SLETTET,
    SKJULT // Treffet er skjult fra frontend, men ikke slettet fra backend
}
