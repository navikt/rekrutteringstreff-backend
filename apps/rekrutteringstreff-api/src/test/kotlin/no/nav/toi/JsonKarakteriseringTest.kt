package no.nav.toi

import no.nav.toi.jsonkarakterisering.JsonKarakterisering
import java.nio.file.Path
import org.junit.jupiter.api.Test

class JsonKarakteriseringTest {
    @Test
    fun `JacksonConfig JSON-format er karakterisert`() {
        JsonKarakterisering.verifiser(
            golden = Path.of("src/test/resources/jsonkarakterisering/api-jacksonconfig.tsv"),
            klasser = JsonKarakterisering.finnKlasser(JacksonConfig::class.java, "no.nav.toi"),
            skriv = JacksonConfig.mapper::writeValueAsString,
            les = { json, klasse -> JacksonConfig.mapper.readValue(json, klasse) },
            lagJsonNode = JacksonConfig.mapper::readTree,
        )
    }
}
