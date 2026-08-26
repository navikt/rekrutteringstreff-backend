package no.nav.toi.treffgjennomføring.matching

class InteresseKanIkkeFjernesException : RuntimeException(
    "Jobbsøkeren har en registrert status hos arbeidsgiveren, og den må nullstilles før interessen kan fjernes"
)
