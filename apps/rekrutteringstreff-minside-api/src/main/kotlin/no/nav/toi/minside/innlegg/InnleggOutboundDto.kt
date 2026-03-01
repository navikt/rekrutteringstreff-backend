package no.nav.toi.minside.innlegg

import no.nav.toi.minside.JacksonConfig

data class InnleggOutboundDto(
    private val tittel: String,
    private val htmlContent: String
) {
    fun json(): String = JacksonConfig.mapper.writeValueAsString(this)
}
