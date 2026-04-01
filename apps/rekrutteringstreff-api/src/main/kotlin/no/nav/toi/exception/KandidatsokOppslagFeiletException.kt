package no.nav.toi.exception

class KandidatsokOppslagFeiletException(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)