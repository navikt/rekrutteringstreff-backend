package no.nav.toi

import java.util.*

enum class Rolle {
    ARBEIDSGIVER_RETTET,
    UTVIKLER,
    BORGER
}

/*
    Holder på UUID-ene som brukes for å identifisere roller i Azure AD.
    Det er ulik spesifikasjon for dev og prod.
 */
data class RolleUuidSpesifikasjon(
    private val arbeidsgiverrettet: UUID,
    private val utvikler: UUID,
) {
    private fun rolleForUuid(uuid: UUID): Rolle? = when (uuid) {
        arbeidsgiverrettet -> Rolle.ARBEIDSGIVER_RETTET
        utvikler -> Rolle.UTVIKLER
        else -> { log.warn("Ukjent rolle-UUID: $uuid"); null }
    }

    fun rollerForUuider(uuider: Collection<UUID>): Set<Rolle> = uuider.mapNotNull(::rolleForUuid).toSet()
}

