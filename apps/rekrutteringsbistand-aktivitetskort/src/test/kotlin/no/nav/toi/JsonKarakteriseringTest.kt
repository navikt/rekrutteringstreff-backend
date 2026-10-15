package no.nav.toi

import no.nav.toi.jsonkarakterisering.JsonKarakterisering
import org.junit.jupiter.api.Test
import java.nio.file.Path

class JsonKarakteriseringTest {
    @Test
    fun `objectMapper JSON-format er karakterisert`() {
        JsonKarakterisering.verifiser(
            golden = Path.of("src/test/resources/jsonkarakterisering/aktivitetskort-objectmapper.tsv"),
            klasser = JsonKarakterisering.finnKlasser(Repository::class.java, "no.nav.toi"),
            skriv = objectMapper::writeValueAsString,
            les = { json, klasse -> objectMapper.readValue(json, klasse) },
            lagJsonNode = objectMapper::readTree,
        )
    }
}
