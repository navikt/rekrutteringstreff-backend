package no.nav.toi

import no.nav.toi.aktivitetskort.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class AktivitetskortJsonKarakteriseringTest {
    @Test
    fun `skrivemønstre i aktivitetskort kan karakteriseres uten database`() {
        val outputs = linkedMapOf<String, String>()

        val kort = Aktivitetskort(
            dabAktivitetskortTopic = "aktivitetskort-topic",
            repository = utenKonstruktør(Repository::class.java),
            aktivitetskortId = "kort-123",
            messageId = "melding-456",
            fnr = "12345678901",
            tittel = "Jobbtreff",
            aktivitetsStatus = AktivitetsStatus.PLANLAGT,
            beskrivelse = "Et beskrivende treff",
            startDato = LocalDate.of(2026, 3, 4),
            sluttDato = LocalDate.of(2026, 3, 5),
            detaljer = listOf(AktivitetskortDetalj("Tid", "10:30")),
            handlinger = listOf(AktivitetskortHandling("Svar", "Svar på treffet", "https://example.test/svar", LenkeType.FELLES)),
            etiketter = listOf(AktivitetskortEtikett("Venter svar", Sentiment.NEUTRAL)),
            oppgave = AktivitetskortOppgave(
                ekstern = AktivitetskortSubOppgave("Les mer", "Åpne lenken", "https://example.test/ekstern"),
                intern = null,
            ),
            actionType = ActionType.UPSERT_AKTIVITETSKORT_V1,
            avtaltMedNav = true,
            endretAv = "Z123456",
            endretAvType = EndretAvType.NAVIDENT,
            endretTidspunkt = ZonedDateTime.of(2026, 3, 4, 9, 8, 7, 123_000_000, ZoneId.of("Europe/Oslo")),
            sendtTidspunkt = null,
        )
        val tilAkaasJson = Aktivitetskort::class.java.getDeclaredMethod("tilAkaasJson").apply {
            isAccessible = true
        }
        outputs["Aktivitetskort.tilAkaasJson"] = tilAkaasJson.invoke(kort) as String

        outputs["AktivitetskortDetalj.fraAkaasJson"] = objectMapper.writeValueAsString(
            AktivitetskortDetalj.fraAkaasJson("""[{"label":"Tid","verdi":"10:30"}]"""),
        )
        outputs["AktivitetskortHandling.fraAkaasJson"] = objectMapper.writeValueAsString(
            AktivitetskortHandling.fraAkaasJson(
                """[{"tekst":"Svar","subtekst":"Svar på treffet","url":"https://example.test/svar","lenkeType":"FELLES"}]""",
            ),
        )
        outputs["AktivitetskortEtikett.fraAkaasJson"] = objectMapper.writeValueAsString(
            AktivitetskortEtikett.fraAkaasJson(
                """[{"tekst":"Venter svar","sentiment":"NEUTRAL","kode":null}]""",
            ),
        )
        outputs["AktivitetskortOppgave.fraAkaasJson"] = objectMapper.writeValueAsString(
            AktivitetskortOppgave.fraAkaasJson(
                """{"ekstern":{"tekst":"Les mer","subtekst":"Åpne lenken","url":"https://example.test/ekstern"},"intern":null}""",
            ),
        )

        val fellesfelter = FellesMeldingsfelter(
            fnr = "12345678901",
            aktivitetskortId = "kort-123",
            aktivitetskortType = RekrutteringstreffType.akaasType,
            endretAv = "Z123456",
            messageId = "melding-456",
            errorMessage = "Ugyldig rekrutteringstreff",
            errorType = ErrorType.UGYLDIG_IDENT.name,
            timestamp = "2026-03-04T09:08:07.123+01:00",
        )
        val publiserte = mutableListOf<Pair<String, String>>()
        val markerte = mutableListOf<String>()
        RekrutteringstreffFeilMelding(fellesfelter, "treff-789").sendTilRapid(
            rapidPublish = { key, json -> publiserte += key to json },
            markerFeilkøhendelseSomSendt = { markerte += it },
        )
        outputs["AktivitetskortFeil.sendTilRapid"] = publiserte.single().second
        outputs["AktivitetskortFeil.rapidKey"] = publiserte.single().first
        outputs["AktivitetskortFeil.markertMessageId"] = markerte.single()

        val detaljerFraRepository = listOf(
            AktivitetskortDetalj("Tid", "10:30"),
            AktivitetskortDetalj("Sted", "Gate 1, 0123 Oslo"),
        )
        outputs["Repository:85"] = objectMapper.writeValueAsString(detaljerFraRepository)
        outputs["Repository:552"] = objectMapper.writeValueAsString(detaljerFraRepository)
        outputs["Repository:94"] = objectMapper.writeValueAsString(
            listOf(
                AktivitetskortHandling(
                    RekrutteringstreffType.handlingTittel,
                    RekrutteringstreffType.handlingSubtekst,
                    "https://minside.example/rekrutteringstreff/treff-789",
                    LenkeType.FELLES,
                ),
            ),
        )
        outputs["Repository:107"] = objectMapper.writeValueAsString(emptyList<AktivitetskortEtikett>())
        outputs["Repository:477"] = objectMapper.writeValueAsString(
            listOf(AktivitetskortEtikett("Relevant", Sentiment.POSITIVE, "RELEVANT")),
        )
        outputs["Repository:630"] = objectMapper.writeValueAsString(
            listOf(
                AktivitetskortHandling(
                    DeleCvMedArbeidsgiverType.handlingTittel,
                    DeleCvMedArbeidsgiverType.handlingSubtekst,
                    "https://stilling.example/123e4567-e89b-12d3-a456-426614174000",
                    LenkeType.FELLES,
                ),
            ),
        )
        outputs["Repository:641"] = objectMapper.writeValueAsString(
            listOf(AktivitetskortEtikett("Venter svar", Sentiment.NEUTRAL, null)),
        )

        val feilKøJson =
            """{"source":"REKRUTTERINGSBISTAND","failingMessage":"{\"messageId\":\"melding-456\"}","errorMessage":"Ugyldig ident","errorType":"UGYLDIG_IDENT"}"""
        outputs["FeilKøHendelse.read-write"] = objectMapper.writeValueAsString(
            objectMapper.readValue(feilKøJson, FeilKøHendelse::class.java),
        )

        val electorClass = Class.forName("no.nav.toi.Elector")
        val elector = objectMapper.readValue("""{"name":"leader-1","ignored":"field"}""", electorClass)
        outputs["LeaderElection.Elector.read-write"] = objectMapper.writeValueAsString(elector)

        val expected = linkedMapOf(
            "Aktivitetskort.tilAkaasJson" to """{"messageId":"melding-456","source":"REKRUTTERINGSBISTAND","aktivitetskortType":"REKRUTTERINGSTREFF","actionType":"UPSERT_AKTIVITETSKORT_V1","aktivitetskort":{"id":"kort-123","personIdent":"12345678901","tittel":"Jobbtreff","aktivitetStatus":"PLANLAGT","startDato":"2026-03-04","sluttDato":"2026-03-05","beskrivelse":"Et beskrivende treff","endretAv":{"ident":"Z123456","identType":"NAVIDENT"},"endretTidspunkt":"2026-03-04T09:08:07.123+01:00[Europe/Oslo]","avtaltMedNav":false,"detaljer":[{"label":"Tid","verdi":"10:30"}],"handlinger":[{"tekst":"Svar","subtekst":"Svar på treffet","url":"https://example.test/svar","lenkeType":"FELLES"}],"etiketter":[{"tekst":"Venter svar","sentiment":"NEUTRAL"}],"oppgave":{"ekstern":{"tekst":"Les mer","subtekst":"Åpne lenken","url":"https://example.test/ekstern"}}}}""",
            "AktivitetskortDetalj.fraAkaasJson" to """[{"label":"Tid","verdi":"10:30"}]""",
            "AktivitetskortHandling.fraAkaasJson" to """[{"tekst":"Svar","subtekst":"Svar på treffet","url":"https://example.test/svar","lenkeType":"FELLES"}]""",
            "AktivitetskortEtikett.fraAkaasJson" to """[{"tekst":"Venter svar","sentiment":"NEUTRAL"}]""",
            "AktivitetskortOppgave.fraAkaasJson" to """{"ekstern":{"tekst":"Les mer","subtekst":"Åpne lenken","url":"https://example.test/ekstern"}}""",
            "AktivitetskortFeil.sendTilRapid" to """{"rekrutteringstreffId":"treff-789","fnr":"12345678901","aktivitetskortId":"kort-123","aktivitetskortType":"REKRUTTERINGSTREFF","endretAv":"Z123456","messageId":"melding-456","errorMessage":"Ugyldig rekrutteringstreff","errorType":"UGYLDIG_IDENT","timestamp":"2026-03-04T09:08:07.123+01:00","@event_name":"aktivitetskort-feil-rekrutteringstreff"}""",
            "AktivitetskortFeil.rapidKey" to "12345678901",
            "AktivitetskortFeil.markertMessageId" to "melding-456",
            "Repository:85" to """[{"label":"Tid","verdi":"10:30"},{"label":"Sted","verdi":"Gate 1, 0123 Oslo"}]""",
            "Repository:552" to """[{"label":"Tid","verdi":"10:30"},{"label":"Sted","verdi":"Gate 1, 0123 Oslo"}]""",
            "Repository:94" to """[{"tekst":"Sjekk ut treffet","subtekst":"Sjekk ut treffet og svar","url":"https://minside.example/rekrutteringstreff/treff-789","lenkeType":"FELLES"}]""",
            "Repository:107" to "[]",
            "Repository:477" to """[{"tekst":"Relevant","sentiment":"POSITIVE","kode":"RELEVANT"}]""",
            "Repository:630" to """[{"tekst":"Sjekk ut stillingen","subtekst":"Sjekk ut stillingen og svar","url":"https://stilling.example/123e4567-e89b-12d3-a456-426614174000","lenkeType":"FELLES"}]""",
            "Repository:641" to """[{"tekst":"Venter svar","sentiment":"NEUTRAL"}]""",
            "FeilKøHendelse.read-write" to """{"source":"REKRUTTERINGSBISTAND","failingMessage":"{\"messageId\":\"melding-456\"}","errorMessage":"Ugyldig ident","errorType":"UGYLDIG_IDENT"}""",
            "LeaderElection.Elector.read-write" to """{"name":"leader-1"}""",
        )
        assertEquals(expected.keys, outputs.keys)
        expected.forEach { (name, json) -> assertEquals(json, outputs.getValue(name), name) }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> utenKonstruktør(type: Class<T>): T {
        val unsafeFelt = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe").apply {
            isAccessible = true
        }
        val unsafe = unsafeFelt.get(null)
        return unsafe.javaClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type) as T
    }
}
