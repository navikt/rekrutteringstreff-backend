package no.nav.toi.exception

class KandidatIkkeFunnetIKandidatsokException(
    override val message: String,
) : RuntimeException(message)