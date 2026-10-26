package no.nav.toi

import no.nav.toi.aktivitetskort.ErrorType
import no.nav.toi.aktivitetskort.FeilKøHendelse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JacksonTest {

    @Test
    fun `should deserialize an error queue message`() {
        val json = """
            {
                "source": "REKRUTTERINGSBISTAND",
                "failingMessage": "{\"messageId\":\"00000000-0000-0000-0000-000000000001\"}",
                "errorMessage": "Activity not found",
                "errorType": "AKTIVITET_IKKE_FUNNET"
            }
        """.trimIndent()

        val event = jackson3ObjectMapper.readValue(json, FeilKøHendelse::class.java)

        assertKnownFields(event)
    }

    @Test
    fun `should ignore unknown fields in an error queue message`() {
        val json = """
            {
                "source": "REKRUTTERINGSBISTAND",
                "failingMessage": "{\"messageId\":\"00000000-0000-0000-0000-000000000001\"}",
                "errorMessage": "Activity not found",
                "errorType": "AKTIVITET_IKKE_FUNNET",
                "additionalField": "ignored"
            }
        """.trimIndent()

        val event = jackson3ObjectMapper.readValue(json, FeilKøHendelse::class.java)

        assertKnownFields(event)
    }

    @Test
    fun `should read elector name and ignore unknown fields`() {
        val json = """
            {
                "name": "activity-card-0",
                "additionalField": "ignored"
            }
        """.trimIndent()

        val name = parseElectorName(json)

        assertThat(name).isEqualTo("activity-card-0")
    }

    private fun assertKnownFields(event: FeilKøHendelse) {
        assertThat(event.source).isEqualTo("REKRUTTERINGSBISTAND")
        assertThat(event.failingMessage).isEqualTo("""{"messageId":"00000000-0000-0000-0000-000000000001"}""")
        assertThat(event.errorMessage).isEqualTo("Activity not found")
        assertThat(event.errorType).isEqualTo(ErrorType.AKTIVITET_IKKE_FUNNET)
    }
}
