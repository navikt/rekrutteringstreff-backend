package no.nav.toi.exception

class ModiaOppslagFeiletException(
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)