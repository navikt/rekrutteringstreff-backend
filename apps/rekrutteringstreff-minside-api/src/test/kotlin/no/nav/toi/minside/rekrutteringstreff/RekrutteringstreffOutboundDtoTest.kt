package no.nav.toi.minside.rekrutteringstreff

import no.nav.toi.minside.arbeidsgiver.ArbeidsgiverOutboundDto
import no.nav.toi.minside.innlegg.InnleggOutboundDto
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class RekrutteringstreffOutboundDtoTest {

    @Test
    fun `json er gyldig`() {
        val treff = RekrutteringstreffOutboundDto(
            id = UUID.fromString("d6a587cd-8797-4b9a-a68b-575373f16d65"),
            tittel = "Sommerjobbtreff",
            beskrivelse = null,
            fraTid = ZonedDateTime.of(2026, 6, 1, 12, 0, 0, 0, ZoneId.of("UTC")),
            tilTid = null,
            svarfrist = null,
            gateadresse = null,
            postnummer = null,
            poststed = null,
            status = null,
            innlegg = listOf(
                InnleggOutboundDto("En tittel", "<p>Tekst med \"anførselstegn\"</p>")
            ),
            arbeidsgivere = listOf(
                ArbeidsgiverOutboundDto("123456789", "Testbedrift")
            )
        )

        val json = treff.json()
        assertThat(json).isEqualTo("""{"id":"d6a587cd-8797-4b9a-a68b-575373f16d65","tittel":"Sommerjobbtreff","beskrivelse":null,"fraTid":"2026-06-01T12:00:00Z","tilTid":null,"svarfrist":null,"gateadresse":null,"postnummer":null,"poststed":null,"status":null,"innlegg":[{"tittel":"En tittel","htmlContent":"<p>Tekst med \"anførselstegn\"</p>"}],"arbeidsgivere":[{}]}""")
    }
}
