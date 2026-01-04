package no.nav.toi.jobbsoker

import com.fasterxml.jackson.databind.JsonNode
import no.nav.toi.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.flywaydb.core.Flyway
import org.junit.Ignore
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.temporal.ChronoUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinsideVarselSvarLytterTest {

    private lateinit var jobbsøkerRepository: JobbsøkerRepository
    private lateinit var jobbsøkerService: JobbsøkerService
    private val db = TestDatabase()
    private val objectMapper = JacksonConfig.mapper
    
    private fun JsonNode?.toMinsideVarselSvarData(): MinsideVarselSvarData {
        return objectMapper.treeToValue(this, MinsideVarselSvarData::class.java)
    }

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, objectMapper)
        jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)
    }

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    @Test
    fun `skal lagre MOTTATT_SVAR_FRA_MINSIDE-hendelse med alle felter`() {
        val rapid = TestRapid()
        MinsideVarselSvarLytter(rapid, jobbsøkerService, objectMapper)

        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("12345678901")
        val endretAvIdent = "Z123456"

        jobbsøkerRepository.leggTilMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent(endretAvIdent),
                )
            ), treffId, endretAvIdent
        )

        rapid.sendTestMessage(
            """
            {
                "@event_name": "minsideVarselSvar",
                "varselId": "varsel-123",
                "avsenderReferanseId": "$treffId",
                "fnr": "${fødselsnummer.asString}",
                "eksternStatus": "sendt",
                "minsideStatus": "ferdigstilt",
                "opprettet": "2024-01-15T10:30:00+01:00",
                "avsenderNavident": "$endretAvIdent",
                "eksternFeilmelding": null,
                "eksternKanal": "SMS",
                "mal": "rekrutteringstreff-invitasjon"
            }
            """.trimIndent()
        )

        val jobbsøker = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)
        assertThat(jobbsøker).isNotNull
        assertThat(jobbsøker!!.hendelser).hasSize(2)

        val opprettHendelse = jobbsøker.hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.OPPRETTET }
        assertThat(opprettHendelse).isNotNull

        val minsideSvarHendelse = jobbsøker.hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.MOTTATT_SVAR_FRA_MINSIDE }
        assertThat(minsideSvarHendelse).isNotNull
        minsideSvarHendelse!!.apply {
            assertThat(id).isNotNull()
            assertThat(tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
            assertThat(opprettetAvAktørType).isEqualTo(AktørType.SYSTEM)
            assertThat(aktørIdentifikasjon).isEqualTo("MIN_SIDE")
            assertThat(hendelseData).isNotNull()
        }

        // Verifiser at hendelse_data inneholder riktig informasjon
        val hendelseDataMap = minsideSvarHendelse.hendelseData.toMinsideVarselSvarData()
        assertThat(hendelseDataMap.varselId).isEqualTo("varsel-123")
        assertThat(hendelseDataMap.avsenderReferanseId).isEqualTo(treffId.somString)
        assertThat(hendelseDataMap.fnr).isEqualTo(fødselsnummer.asString)
        assertThat(hendelseDataMap.eksternStatus).isEqualTo("sendt")
        assertThat(hendelseDataMap.minsideStatus).isEqualTo("ferdigstilt")
        assertThat(hendelseDataMap.avsenderNavident).isEqualTo(endretAvIdent)
        assertThat(hendelseDataMap.eksternKanal).isEqualTo("SMS")
        assertThat(hendelseDataMap.mal).isEqualTo("rekrutteringstreff-invitasjon")
    }

    @Test
    fun `skal lagre hendelse med kun påkrevde felter - håndterer manglende valgfrie felter`() {
        val rapid = TestRapid()
        MinsideVarselSvarLytter(rapid, jobbsøkerService, objectMapper)

        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("12345678901")
        val endretAvIdent = "Z123456"

        jobbsøkerRepository.leggTilMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent(endretAvIdent),
                )
            ), treffId, endretAvIdent
        )

        // Melding med kun påkrevde felter
        rapid.sendTestMessage(
            """
            {
                "@event_name": "minsideVarselSvar",
                "varselId": "varsel-456",
                "avsenderReferanseId": "$treffId",
                "fnr": "${fødselsnummer.asString}"
            }
            """.trimIndent()
        )

        val jobbsøker = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)
        assertThat(jobbsøker).isNotNull
        assertThat(jobbsøker!!.hendelser).hasSize(2)

        val minsideSvarHendelse = jobbsøker.hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.MOTTATT_SVAR_FRA_MINSIDE }
        assertThat(minsideSvarHendelse).isNotNull

        // Verifiser at valgfrie felter er null
        val hendelseDataMap = minsideSvarHendelse!!.hendelseData.toMinsideVarselSvarData()
        assertThat(hendelseDataMap.varselId).isEqualTo("varsel-456")
        assertThat(hendelseDataMap.eksternStatus).isNull()
        assertThat(hendelseDataMap.minsideStatus).isNull()
        assertThat(hendelseDataMap.opprettet).isNull()
        assertThat(hendelseDataMap.avsenderNavident).isNull()
        assertThat(hendelseDataMap.eksternFeilmelding).isNull()
        assertThat(hendelseDataMap.eksternKanal).isNull()
        assertThat(hendelseDataMap.mal).isNull()
    }

    @Test
    fun `skal ignorere melding med aktørId felt - feil type melding`() {
        val rapid = TestRapid()
        MinsideVarselSvarLytter(rapid, jobbsøkerService, objectMapper)

        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("12345678901")
        val endretAvIdent = "Z123456"

        jobbsøkerRepository.leggTilMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent(endretAvIdent),
                )
            ), treffId, endretAvIdent
        )

        // Melding med aktørId skal ignoreres (forbid("aktørId"))
        rapid.sendTestMessage(
            """
            {
                "@event_name": "minsideVarselSvar",
                "varselId": "varsel-789",
                "avsenderReferanseId": "$treffId",
                "fnr": "${fødselsnummer.asString}",
                "aktørId": "1234567890123"
            }
            """.trimIndent()
        )

        val jobbsøker = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)
        assertThat(jobbsøker).isNotNull
        // Skal bare ha OPPRETTET-hendelse, ikke MOTTATT_SVAR_FRA_MINSIDE
        assertThat(jobbsøker!!.hendelser).hasSize(1)
        assertThat(jobbsøker.hendelser.first().hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET)
    }

    @Test
    fun `skal ignorere melding med feil event_name`() {
        val rapid = TestRapid()
        MinsideVarselSvarLytter(rapid, jobbsøkerService, objectMapper)

        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("12345678901")
        val endretAvIdent = "Z123456"

        jobbsøkerRepository.leggTilMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent(endretAvIdent),
                )
            ), treffId, endretAvIdent
        )

        // Melding med annen event_name skal ignoreres
        rapid.sendTestMessage(
            """
            {
                "@event_name": "annenHendelse",
                "varselId": "varsel-999",
                "avsenderReferanseId": "$treffId",
                "fnr": "${fødselsnummer.asString}"
            }
            """.trimIndent()
        )

        val jobbsøker = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)
        assertThat(jobbsøker).isNotNull
        // Skal bare ha OPPRETTET-hendelse
        assertThat(jobbsøker!!.hendelser).hasSize(1)
        assertThat(jobbsøker.hendelser.first().hendelsestype).isEqualTo(JobbsøkerHendelsestype.OPPRETTET)
    }

    @Test
    fun `skal ikke lagre hendelse når jobbsøker ikke finnes for treffet`() {
        val rapid = TestRapid()
        MinsideVarselSvarLytter(rapid, jobbsøkerService, objectMapper)

        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("12345678901")

        // Ikke legg til jobbsøker - send bare melding
        rapid.sendTestMessage(
            """
            {
                "@event_name": "minsideVarselSvar",
                "varselId": "varsel-111",
                "avsenderReferanseId": "$treffId",
                "fnr": "${fødselsnummer.asString}"
            }
            """.trimIndent()
        )

        // Jobbsøker finnes ikke, så ingen hendelse skal lagres
        val jobbsøker = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)
        assertThat(jobbsøker).isNull()
    }

    @Test
    fun `skal lagre hendelse med ekstern feilmelding`() {
        val rapid = TestRapid()
        MinsideVarselSvarLytter(rapid, jobbsøkerService, objectMapper)

        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("12345678901")
        val endretAvIdent = "Z123456"

        jobbsøkerRepository.leggTilMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent(endretAvIdent),
                )
            ), treffId, endretAvIdent
        )

        rapid.sendTestMessage(
            """
            {
                "@event_name": "minsideVarselSvar",
                "varselId": "varsel-feil",
                "avsenderReferanseId": "$treffId",
                "fnr": "${fødselsnummer.asString}",
                "eksternStatus": "feilet",
                "minsideStatus": "feilet",
                "eksternFeilmelding": "Kunne ikke levere varsel: ugyldig telefonnummer"
            }
            """.trimIndent()
        )

        val jobbsøker = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)
        assertThat(jobbsøker).isNotNull

        val minsideSvarHendelse = jobbsøker!!.hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.MOTTATT_SVAR_FRA_MINSIDE }
        assertThat(minsideSvarHendelse).isNotNull

        val hendelseDataMap = minsideSvarHendelse!!.hendelseData.toMinsideVarselSvarData()
        assertThat(hendelseDataMap.eksternStatus).isEqualTo("feilet")
        assertThat(hendelseDataMap.minsideStatus).isEqualTo("feilet")
        assertThat(hendelseDataMap.eksternFeilmelding).isEqualTo("Kunne ikke levere varsel: ugyldig telefonnummer")
    }

    @Test
    fun `skal håndtere flere meldinger for samme jobbsøker`() {
        val rapid = TestRapid()
        MinsideVarselSvarLytter(rapid, jobbsøkerService, objectMapper)

        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("12345678901")
        val endretAvIdent = "Z123456"

        jobbsøkerRepository.leggTilMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent(endretAvIdent),
                )
            ), treffId, endretAvIdent
        )

        // Første melding
        rapid.sendTestMessage(
            """
            {
                "@event_name": "minsideVarselSvar",
                "varselId": "varsel-1",
                "avsenderReferanseId": "$treffId",
                "fnr": "${fødselsnummer.asString}",
                "minsideStatus": "sendt"
            }
            """.trimIndent()
        )

        // Andre melding - oppdatert status
        rapid.sendTestMessage(
            """
            {
                "@event_name": "minsideVarselSvar",
                "varselId": "varsel-1",
                "avsenderReferanseId": "$treffId",
                "fnr": "${fødselsnummer.asString}",
                "minsideStatus": "ferdigstilt"
            }
            """.trimIndent()
        )

        val jobbsøker = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)
        assertThat(jobbsøker).isNotNull
        
        // Skal ha OPPRETTET + 2 MOTTATT_SVAR_FRA_MINSIDE hendelser
        assertThat(jobbsøker!!.hendelser).hasSize(3)
        
        val minsideSvarHendelser = jobbsøker.hendelser.filter { it.hendelsestype == JobbsøkerHendelsestype.MOTTATT_SVAR_FRA_MINSIDE }
        assertThat(minsideSvarHendelser).hasSize(2)
    }

    @Test
    fun `skal håndtere opprettet-dato med tidssone`() {
        val rapid = TestRapid()
        MinsideVarselSvarLytter(rapid, jobbsøkerService, objectMapper)

        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("12345678901")
        val endretAvIdent = "Z123456"

        jobbsøkerRepository.leggTilMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent(endretAvIdent),
                )
            ), treffId, endretAvIdent
        )

        rapid.sendTestMessage(
            """
            {
                "@event_name": "minsideVarselSvar",
                "varselId": "varsel-123",
                "avsenderReferanseId": "$treffId",
                "fnr": "${fødselsnummer.asString}",
                "opprettet": "2025-12-01T14:53:29.201417+01:00"
            }
            """.trimIndent()
        )

        val jobbsøker = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)
        assertThat(jobbsøker).isNotNull
        val minsideSvarHendelse = jobbsøker!!.hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.MOTTATT_SVAR_FRA_MINSIDE }
        assertThat(minsideSvarHendelse).isNotNull

        val hendelseDataMap = minsideSvarHendelse!!.hendelseData.toMinsideVarselSvarData()
        assertThat(hendelseDataMap.opprettet).isNotNull
        val expected = java.time.ZonedDateTime.parse("2025-12-01T14:53:29.201417+01:00")
        assertThat(hendelseDataMap.opprettet!!.toInstant()).isEqualTo(expected.toInstant())
    }
}
