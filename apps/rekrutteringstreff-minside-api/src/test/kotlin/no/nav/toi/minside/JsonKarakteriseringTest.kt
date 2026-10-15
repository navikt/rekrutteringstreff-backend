package no.nav.toi.minside

import no.nav.toi.jsonkarakterisering.JsonKarakterisering
import org.junit.jupiter.api.Test
import java.nio.file.Path

class JsonKarakteriseringTest {
    @Test
    fun `JacksonConfig JSON-format er karakterisert`() {
        JsonKarakterisering.verifiser(
            golden = Path.of("src/test/resources/jsonkarakterisering/minside-jacksonconfig.tsv"),
            klasser = JsonKarakterisering.finnKlasser(JacksonConfig::class.java, "no.nav.toi.minside"),
            skriv = JacksonConfig.mapper::writeValueAsString,
            les = { json, klasse -> JacksonConfig.mapper.readValue(json, klasse) },
            lagJsonNode = JacksonConfig.mapper::readTree,
        )
    }
}
