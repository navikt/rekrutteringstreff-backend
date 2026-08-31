package no.nav.arbeidsgiver.toi.logging

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private val filklassenavnForDenneFilen = Throwable().stackTrace[0].className
private val loggerFraToplevelKallsted = noClassLogger()

class NoClassLoggerTest {
    @Test
    fun `noClassLogger gir en Logger med navnet til Kotlin-filen du står i`() {
        val loggerNavn = loggerFraToplevelKallsted.name
        assertThat(loggerNavn).isEqualTo(filklassenavnForDenneFilen)
        assertThat(loggerNavn).isEqualTo("no.nav.arbeidsgiver.toi.logging.NoClassLoggerTestKt")
    }
}
