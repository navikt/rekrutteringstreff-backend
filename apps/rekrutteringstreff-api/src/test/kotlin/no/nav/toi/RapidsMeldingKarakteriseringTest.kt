package no.nav.toi

import io.mockk.every
import io.mockk.mockk
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortOppdatering
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortTreffstatus
import no.nav.toi.jobbsoker.aktivitetskort.Aktivitetskortinvitasjon
import no.nav.toi.jobbsoker.aktivitetskort.RekrutteringstreffSvarOgStatus
import no.nav.toi.jobbsoker.synlighet.SynlighetsBehovScheduler
import no.nav.toi.rekrutteringstreff.Endringsfelttype
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TreffId
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class RapidsMeldingKarakteriseringTest {
    @Test
    fun `utgående R og R meldinger kan karakteriseres uten database`() {
        val outputs = linkedMapOf<String, String>()
        val fnr = "12345678901"
        val treffId = TreffId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
        val hendelseId = UUID.fromString("234e5678-e89b-12d3-a456-426614174001")
        val kategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF
        val fraTid = ZonedDateTime.of(2026, 3, 4, 10, 11, 12, 123_000_000, ZoneId.of("Europe/Oslo"))
        val tilTid = ZonedDateTime.of(2026, 3, 4, 12, 13, 14, 456_000_000, ZoneId.of("Europe/Oslo"))

        val invitasjonsRapid = TestRapid()
        Aktivitetskortinvitasjon.opprett(
            fnr = fnr,
            rekrutteringstreffId = treffId,
            hendelseId = hendelseId,
            kategori = kategori,
            tittel = "Treff i Oslo",
            fraTid = fraTid,
            tilTid = tilTid,
            opprettetAv = "Z123456",
            opprettetTidspunkt = ZonedDateTime.of(2026, 3, 1, 8, 9, 10, 111_000_000, ZoneId.of("Europe/Oslo")),
            gateadresse = "Gate 1",
            postnummer = "0123",
            poststed = "Oslo",
            svarfrist = ZonedDateTime.of(2026, 3, 3, 15, 16, 17, 789_000_000, ZoneId.of("Europe/Oslo")),
        ).publiserTilRapids(invitasjonsRapid)
        outputs["Aktivitetskortinvitasjon"] = mottattMelding(invitasjonsRapid, fnr)

        val oppdateringsRapid = TestRapid()
        AktivitetskortOppdatering(
            fnr = fnr,
            rekrutteringstreffId = treffId,
            hendelseId = hendelseId,
            kategori = kategori,
            tittel = "Oppdatert treff",
            fraTid = fraTid,
            tilTid = tilTid,
            gateadresse = "Gate 1",
            postnummer = "0123",
            poststed = "Oslo",
            endretAv = null,
            endredeFelter = listOf(Endringsfelttype.NAVN, Endringsfelttype.TIDSPUNKT),
        ).publiserTilRapids(oppdateringsRapid)
        outputs["AktivitetskortOppdatering"] = mottattMelding(oppdateringsRapid, fnr)

        val svarRapid = TestRapid()
        RekrutteringstreffSvarOgStatus(
            fnr = fnr,
            rekrutteringstreffId = treffId,
            endretAv = "Z123456",
            endretAvPersonbruker = false,
            hendelseId = hendelseId,
            kategori = kategori,
            svar = false,
            treffstatus = AktivitetskortTreffstatus.AVLYST,
        ).publiserTilRapids(svarRapid)
        outputs["RekrutteringstreffSvarOgStatus"] = mottattMelding(svarRapid, fnr)

        val service = mockk<JobbsøkerService>()
        every { service.hentFødselsnumreUtenEvaluertSynlighet() } returns listOf(fnr)
        val synlighetsRapid = TestRapid()
        val scheduler = SynlighetsBehovScheduler(
            jobbsøkerService = service,
            rapidsConnection = synlighetsRapid,
            leaderElection = object : LeaderElectionInterface {
                override fun isLeader() = true
            },
        )
        scheduler.kjørJobb()
        outputs["SynlighetsBehovScheduler"] = mottattMelding(synlighetsRapid, fnr)

        val expected = linkedMapOf(
            "Aktivitetskortinvitasjon" to """{"@event_name":"rekrutteringstreffinvitasjon","fnr":"12345678901","rekrutteringstreffId":"123e4567-e89b-12d3-a456-426614174000","hendelseId":"234e5678-e89b-12d3-a456-426614174001","kategori":"REKRUTTERINGSTREFF","tittel":"Treff i Oslo","beskrivelse":"TODO","fraTid":"2026-03-04T10:11:12.123+01:00","tilTid":"2026-03-04T12:13:14.456+01:00","opprettetAv":"Z123456","opprettetTidspunkt":"2026-03-01T08:09:10.111+01:00","svarfrist":"2026-03-03T15:16:17.789+01:00","gateadresse":"Gate 1","postnummer":"0123","poststed":"Oslo","@id":"MASKERT","@opprettet":"MASKERT","system_read_count":"MASKERT","system_participating_services":"MASKERT"}""",
            "AktivitetskortOppdatering" to """{"@event_name":"rekrutteringstreffoppdatering","fnr":"12345678901","rekrutteringstreffId":"123e4567-e89b-12d3-a456-426614174000","hendelseId":"234e5678-e89b-12d3-a456-426614174001","kategori":"REKRUTTERINGSTREFF","tittel":"Oppdatert treff","fraTid":"2026-03-04T10:11:12.123+01:00","tilTid":"2026-03-04T12:13:14.456+01:00","gateadresse":"Gate 1","postnummer":"0123","poststed":"Oslo","endredeFelter":["NAVN","TIDSPUNKT"],"@id":"MASKERT","@opprettet":"MASKERT","system_read_count":"MASKERT","system_participating_services":"MASKERT"}""",
            "RekrutteringstreffSvarOgStatus" to """{"@event_name":"rekrutteringstreffSvarOgStatus","fnr":"12345678901","rekrutteringstreffId":"123e4567-e89b-12d3-a456-426614174000","endretAv":"Z123456","endretAvPersonbruker":false,"hendelseId":"234e5678-e89b-12d3-a456-426614174001","kategori":"REKRUTTERINGSTREFF","svar":false,"treffstatus":"avlyst","@id":"MASKERT","@opprettet":"MASKERT","system_read_count":"MASKERT","system_participating_services":"MASKERT"}""",
            "SynlighetsBehovScheduler" to """{"@event_name":"behov","@behov":["synlighetRekrutteringstreff"],"fodselsnummer":"12345678901","@id":"MASKERT","@opprettet":"MASKERT","system_read_count":"MASKERT","system_participating_services":"MASKERT"}""",
        )
        assertEquals(expected.keys, outputs.keys)
        expected.forEach { (name, json) -> assertEquals(json, outputs.getValue(name), name) }
    }

    private fun mottattMelding(rapid: TestRapid, expectedKey: String): String {
        assertEquals(1, rapid.inspektør.size)
        assertEquals(expectedKey, rapid.inspektør.key(0))
        return masker(rapid.inspektør.rawMessage(0))
    }

    private fun masker(json: String): String {
        val generatedFields = Regex(
            """("(?:@id|@opprettet|system_read_count|system_participating_services)"\s*:\s*)(?:"(?:\\.|[^"\\])*"|\d+|\[[^\]]*])""",
        )
        return generatedFields.replace(json) { match -> "${match.groupValues[1]}\"MASKERT\"" }
    }
}
