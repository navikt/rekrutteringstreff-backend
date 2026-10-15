package no.nav.toi

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

private enum class InnlesingFarge { RØD, GRØNN }

private data class InnlesingDto(
    val navn: String,
    val farge: InnlesingFarge?,
    val liste: List<String>,
    val ønsket: String?,
    val årsak: String?,
)

private data class DatoDto(
    val dato: LocalDate,
    val lokal: LocalDateTime,
    val sone: ZonedDateTime,
    val offset: OffsetDateTime,
    val instant: Instant,
)

class JacksonInnlesingKarakteriseringTest {
    private val mapper = JacksonConfig.mapper

    @Test
    fun `ukjente felter ignoreres, enkeltverdi blir liste og ukjent enum blir null`() {
        val dto = mapper.readValue(
            """{"navn":"a","farge":"LILLA","liste":"én","ukjent":{"x":1},"ønsket":"ja","årsak":"fordi"}""",
            InnlesingDto::class.java,
        )
        assertEquals(InnlesingDto("a", null, listOf("én"), "ja", "fordi"), dto)
    }

    @Test
    fun `felter som starter med æøå skrives og leses`() {
        val json = mapper.writeValueAsString(InnlesingDto("a", InnlesingFarge.RØD, listOf("x"), "ja", "fordi"))
        assertEquals("""{"navn":"a","farge":"RØD","liste":["x"],"ønsket":"ja","årsak":"fordi"}""", json)
        assertEquals(InnlesingDto("a", InnlesingFarge.RØD, listOf("x"), "ja", "fordi"), mapper.readValue(json, InnlesingDto::class.java))
    }

    @Test
    fun `datoformater vi tar imot og skriver`() {
        val inn = """{"dato":"2026-03-04","lokal":"2026-03-04T10:11:12.123","sone":"2026-03-04T10:11:12.123+01:00","offset":"2026-03-04T10:11:12.123+01:00","instant":"2026-03-04T09:11:12.123Z"}"""
        val dto = mapper.readValue(inn, DatoDto::class.java)
        assertEquals(
            """{"dato":"2026-03-04","lokal":"2026-03-04T10:11:12.123","sone":"2026-03-04T09:11:12.123Z","offset":"2026-03-04T09:11:12.123Z","instant":"2026-03-04T09:11:12.123Z"}""",
            mapper.writeValueAsString(dto),
        )
        assertEquals(dto, mapper.readValue(mapper.writeValueAsString(dto), DatoDto::class.java))
    }

    @Test
    fun `ZonedDateTime med Oslo-sone skrives med offset`() {
        val tid = ZonedDateTime.of(2026, 7, 1, 12, 0, 0, 5_000_000, java.time.ZoneId.of("Europe/Oslo"))
        assertEquals("\"2026-07-01T12:00:00.005+02:00\"", mapper.writeValueAsString(tid))
    }

    @Test
    fun `avsluttende tegn etter JSON-verdien godtas`() {
        assertEquals(InnlesingDto("a", null, emptyList(), null, null), mapper.readValue("""{"navn":"a","liste":[]} x""", InnlesingDto::class.java))
    }

    @Test
    fun `null for ikke-nullbar Kotlin-verdi feiler`() {
        assertThrows(Exception::class.java) { mapper.readValue("""{"navn":null,"liste":[]}""", InnlesingDto::class.java) }
    }
}
