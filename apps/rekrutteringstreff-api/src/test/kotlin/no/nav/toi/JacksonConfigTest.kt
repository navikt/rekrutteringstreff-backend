package no.nav.toi

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import tools.jackson.module.kotlin.readValue
import java.time.LocalDate

class JacksonConfigTest {

    private data class MedNorskeTegn(
        val årsak: String,
        val ønsket: Boolean,
        val ærend: String,
        val dato: LocalDate,
    )

    @Test
    fun `properties som starter med æ, ø eller å går tur-retur`() {
        val original = MedNorskeTegn(årsak = "flyttet", ønsket = true, ærend = "avklaring", dato = LocalDate.of(2026, 1, 15))

        val json = JacksonConfig.mapper.writeValueAsString(original)
        val node = JacksonConfig.mapper.readTree(json)

        assertThat(node.propertyNames()).containsExactlyInAnyOrder("årsak", "ønsket", "ærend", "dato")
        assertThat(node["dato"].asString()).isEqualTo("2026-01-15")
        assertThat(JacksonConfig.mapper.readValue<MedNorskeTegn>(json)).isEqualTo(original)
    }

    private data class MedPrimitiver(val antall: Int, val flagg: Boolean)
    private data class MedSamlinger(val liste: List<String>, val map: Map<String, String>)

    @Test
    fun `null eller manglende Int og Boolean blir 0 og false som i Jackson 2`() {
        assertThat(JacksonConfig.mapper.readValue<MedPrimitiver>("""{"antall": null, "flagg": null}""")).isEqualTo(MedPrimitiver(0, false))
        assertThat(JacksonConfig.mapper.readValue<MedPrimitiver>("{}")).isEqualTo(MedPrimitiver(0, false))
    }

    @Test
    fun `null i liste og map godtas som i Jackson 2`() {
        val verdi = JacksonConfig.mapper.readValue<MedSamlinger>("""{"liste": ["a", null], "map": {"a": null}}""")

        assertThat(verdi.liste).containsExactly("a", null)
        assertThat(verdi.map).containsEntry("a", null)
    }

    @Test
    fun `innhold etter JSON-verdien ignoreres som i Jackson 2`() {
        assertThat(JacksonConfig.mapper.readTree("""{"a": 1} ekstra""")["a"].asInt()).isEqualTo(1)
    }
}
