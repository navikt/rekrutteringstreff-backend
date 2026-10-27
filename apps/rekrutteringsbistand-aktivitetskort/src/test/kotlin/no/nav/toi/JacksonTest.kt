package no.nav.toi

import no.nav.toi.aktivitetskort.*
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

    @Test
    fun `should read stored details in order and ignore unknown fields`() {
        val details = AktivitetskortDetalj.fraAkaasJson(
            """[
                {"label":"Sted","verdi":"Teststed","additionalField":"ignored"},
                {"label":"Tid","verdi":"10:00"}
            ]"""
        )

        assertThat(details).hasSize(2)
        assertThat(details.map { it.label }).containsExactly("Sted", "Tid")
        assertThat(details.map { it.verdi }).containsExactly("Teststed", "10:00")
    }

    @Test
    fun `should read stored actions and link types`() {
        val actions = AktivitetskortHandling.fraAkaasJson(
            """[
                {"tekst":"First","subtekst":"First detail","url":"https://example.com/1","lenkeType":"FELLES"},
                {"tekst":"Second","subtekst":"Second detail","url":"https://example.com/2","lenkeType":"INTERN"},
                {"tekst":"Third","subtekst":"Third detail","url":"https://example.com/3","lenkeType":"EKSTERN"}
            ]"""
        )

        assertThat(actions).hasSize(3)
        assertThat(actions.map { it.tekst }).containsExactly("First", "Second", "Third")
        assertThat(actions.map { it.subtekst }).containsExactly("First detail", "Second detail", "Third detail")
        assertThat(actions.map { it.url }).containsExactly(
            "https://example.com/1", "https://example.com/2", "https://example.com/3"
        )
        assertThat(actions.map { it.lenkeType }).containsExactly(LenkeType.FELLES, LenkeType.INTERN, LenkeType.EKSTERN)
    }

    @Test
    fun `should read stored labels with present missing and null code`() {
        val labels = AktivitetskortEtikett.fraAkaasJson(
            """[
                {"tekst":"Waiting","sentiment":"NEUTRAL"},
                {"tekst":"Accepted","sentiment":"POSITIVE","kode":"ACCEPTED"},
                {"tekst":"Declined","sentiment":"NEGATIVE","kode":null}
            ]"""
        )

        assertThat(labels).hasSize(3)
        assertThat(labels.map { it.tekst }).containsExactly("Waiting", "Accepted", "Declined")
        assertThat(labels.map { it.sentiment }).containsExactly(Sentiment.NEUTRAL, Sentiment.POSITIVE, Sentiment.NEGATIVE)
        assertThat(labels.map { it.kode }).containsExactly(null, "ACCEPTED", null)
    }

    @Test
    fun `should read stored task sides and ignore unknown fields`() {
        val task = AktivitetskortOppgave.fraAkaasJson(
            """{
                "ekstern":{"tekst":"External","subtekst":"External detail","url":"https://example.com/external"},
                "intern":{"tekst":"Internal","subtekst":"Internal detail","url":"https://example.com/internal"},
                "additionalField":"ignored"
            }"""
        )

        val external = requireNotNull(task.ekstern)
        assertThat(external.tekst).isEqualTo("External")
        assertThat(external.subtekst).isEqualTo("External detail")
        assertThat(external.url).isEqualTo("https://example.com/external")
        val internal = requireNotNull(task.intern)
        assertThat(internal.tekst).isEqualTo("Internal")
        assertThat(internal.subtekst).isEqualTo("Internal detail")
        assertThat(internal.url).isEqualTo("https://example.com/internal")
    }

    @Test
    fun `should read stored task with missing or null optional side`() {
        val missingInternal = AktivitetskortOppgave.fraAkaasJson(
            """{"ekstern":{"tekst":"External","subtekst":"Detail","url":"https://example.com"}}"""
        )
        val nullExternal = AktivitetskortOppgave.fraAkaasJson(
            """{"ekstern":null,"intern":{"tekst":"Internal","subtekst":"Detail","url":"https://example.com"}}"""
        )

        assertThat(missingInternal.intern).isNull()
        assertThat(requireNotNull(missingInternal.ekstern).tekst).isEqualTo("External")
        assertThat(nullExternal.ekstern).isNull()
        assertThat(requireNotNull(nullExternal.intern).tekst).isEqualTo("Internal")
    }

    @Test
    fun `should read empty stored lists`() {
        assertThat(AktivitetskortDetalj.fraAkaasJson("[]")).isEmpty()
        assertThat(AktivitetskortHandling.fraAkaasJson("[]")).isEmpty()
        assertThat(AktivitetskortEtikett.fraAkaasJson("[]")).isEmpty()
    }

    @Test
    fun `should write stored details with escaped text and preserve order`() {
        val details = listOf(
            AktivitetskortDetalj("Tid", "10:00"),
            AktivitetskortDetalj("Sted", "Test \"location\"\nEntrance\\A")
        )
        val expected = jackson3ObjectMapper.readTree(
            """[
                {"label":"Tid","verdi":"10:00"},
                {"label":"Sted","verdi":"Test \"location\"\nEntrance\\A"}
            ]"""
        )

        val json = jackson3ObjectMapper.writeValueAsString(details)

        assertThat(jackson3ObjectMapper.readTree(json)).isEqualTo(expected)
    }

    @Test
    fun `should write stored actions with enum names`() {
        val actions = listOf(
            AktivitetskortHandling("Open", "View details", "https://example.com/card", LenkeType.FELLES)
        )
        val expected = jackson3ObjectMapper.readTree(
            """[{"tekst":"Open","subtekst":"View details","url":"https://example.com/card","lenkeType":"FELLES"}]"""
        )

        val json = jackson3ObjectMapper.writeValueAsString(actions)

        assertThat(jackson3ObjectMapper.readTree(json)).isEqualTo(expected)
    }

    @Test
    fun `should write stored labels with enum names and omit null code`() {
        val labels = listOf(
            AktivitetskortEtikett("Waiting", Sentiment.NEUTRAL),
            AktivitetskortEtikett("Accepted", Sentiment.POSITIVE, "ACCEPTED"),
            AktivitetskortEtikett("Declined", Sentiment.NEGATIVE)
        )
        val expected = jackson3ObjectMapper.readTree(
            """[
                {"tekst":"Waiting","sentiment":"NEUTRAL"},
                {"tekst":"Accepted","sentiment":"POSITIVE","kode":"ACCEPTED"},
                {"tekst":"Declined","sentiment":"NEGATIVE"}
            ]"""
        )

        val json = jackson3ObjectMapper.writeValueAsString(labels)

        assertThat(jackson3ObjectMapper.readTree(json)).isEqualTo(expected)
    }

    @Test
    fun `should write empty stored label list`() {
        val json = jackson3ObjectMapper.writeValueAsString(emptyList<AktivitetskortEtikett>())

        assertThat(jackson3ObjectMapper.readTree(json)).isEqualTo(jackson3ObjectMapper.readTree("[]"))
    }

    private fun assertKnownFields(event: FeilKøHendelse) {
        assertThat(event.source).isEqualTo("REKRUTTERINGSBISTAND")
        assertThat(event.failingMessage).isEqualTo("""{"messageId":"00000000-0000-0000-0000-000000000001"}""")
        assertThat(event.errorMessage).isEqualTo("Activity not found")
        assertThat(event.errorType).isEqualTo(ErrorType.AKTIVITET_IKKE_FUNNET)
    }
}
