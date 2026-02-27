package no.nav.toi.minside.innlegg

import no.nav.toi.minside.JacksonConfig

data class InnleggOutboundDto(
    val tittel: String,
    val htmlContent: String
)
