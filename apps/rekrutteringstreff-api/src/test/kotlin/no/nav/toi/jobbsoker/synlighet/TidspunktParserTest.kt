package no.nav.toi.jobbsoker.synlighet

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TidspunktParserTest {

    private val osloZone = ZoneId.of("Europe/Oslo")

    @Test
    fun `skal parse ISO instant med Z suffix`() {
        val tidspunktString = "2026-01-13T09:07:38.955620324Z"
        
        val result = parseOpprettetTidspunkt(tidspunktString)
        
        // UTC 09:07 = Oslo 10:07 (vintertid +1)
        assertThat(result.zone).isEqualTo(osloZone)
        assertThat(result.hour).isEqualTo(10)
        assertThat(result.minute).isEqualTo(7)
    }

    @Test
    fun `skal parse LocalDateTime uten tidssone og anta Oslo-tid`() {
        val tidspunktString = "2026-01-13T10:07:38.955620324"
        
        val result = parseOpprettetTidspunkt(tidspunktString)
        
        // Uten tidssone antar vi Oslo, så 10:07 Oslo forblir 10:07 Oslo
        assertThat(result.zone).isEqualTo(osloZone)
        assertThat(result.hour).isEqualTo(10)
        assertThat(result.minute).isEqualTo(7)
    }

    @Test
    fun `skal parse ZonedDateTime med positiv offset`() {
        val tidspunktString = "2026-01-13T10:07:38.955620324+01:00"
        
        val result = parseOpprettetTidspunkt(tidspunktString)
        
        // +01:00 = Oslo vintertid
        assertThat(result.zone).isEqualTo(osloZone)
        assertThat(result.hour).isEqualTo(10)
    }

    @Test
    fun `skal parse ZonedDateTime med negativ offset`() {
        val tidspunktString = "2026-01-13T04:07:38.955-05:00"
        
        val result = parseOpprettetTidspunkt(tidspunktString)
        
        // -05:00 kl 04:07 = UTC 09:07 = Oslo 10:07 (vintertid)
        assertThat(result.zone).isEqualTo(osloZone)
        assertThat(result.hour).isEqualTo(10)
    }

    @Test
    fun `parseOpprettetTidspunktAsInstant returnerer Instant`() {
        val tidspunktString = "2026-01-13T10:07:38.955620324"
        
        val result = parseOpprettetTidspunktAsInstant(tidspunktString)
        
        // Instant skal representere samme tidspunkt
        val expectedZdt = ZonedDateTime.of(2026, 1, 13, 10, 7, 38, 955620324, osloZone)
        assertThat(result).isEqualTo(expectedZdt.toInstant())
    }

    @Test
    fun `skal håndtere tidspunkt med kort nanosekund-presisjon`() {
        val tidspunktString = "2026-01-13T10:07:38.9Z"
        
        val result = parseOpprettetTidspunkt(tidspunktString)
        
        assertThat(result.zone).isEqualTo(osloZone)
        assertThat(result.hour).isEqualTo(11) // UTC 10:07 = Oslo 11:07
    }

    @Test
    fun `skal håndtere tidspunkt uten millisekunder`() {
        val tidspunktString = "2026-01-13T10:07:38Z"
        
        val result = parseOpprettetTidspunkt(tidspunktString)
        
        assertThat(result.zone).isEqualTo(osloZone)
    }
}
