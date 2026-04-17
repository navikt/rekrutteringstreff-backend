package no.nav.toi.jobbsoker.dto

import no.nav.toi.jobbsoker.PersonTreffId

data class SvarForJobbsøkerDto(
    val personTreffId: PersonTreffId,
    val svar: Boolean?,
)
