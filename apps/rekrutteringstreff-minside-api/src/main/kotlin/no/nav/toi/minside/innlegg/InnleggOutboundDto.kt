package no.nav.toi.minside.innlegg

data class InnleggOutboundDto(
    private val tittel: String,
    private val htmlContent: String
) {
    fun json() = """
        {
            "tittel": "$tittel",
            "htmlContent": "$htmlContent"
        }
    """.trimIndent()
}
