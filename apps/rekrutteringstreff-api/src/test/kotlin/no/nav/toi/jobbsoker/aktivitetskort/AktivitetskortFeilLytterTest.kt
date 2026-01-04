package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.assertj.core.api.Assertions.within

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktivitetskortFeilLytterTest {

    private lateinit var jobbsøkerRepository: JobbsøkerRepository
    private lateinit var jobbsøkerService: JobbsøkerService
    private val db = TestDatabase()
    private val objectMapper = JacksonConfig.mapper

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
    fun `skal lagre feil-hendelse når aktivitetskort-opprettelse feiler`() {
        val rapid = TestRapid()
        AktivitetskortFeilLytter(rapid, jobbsøkerService)

        val treffId = db.opprettRekrutteringstreffIDatabase()
        val fødselsnummer = Fødselsnummer("12345678901")
        val endretAvIdent = "Z123456"

        db.leggTilJobbsøkereMedService(
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
                "@event_name": "aktivitetskort-feil",
                "fnr": "${fødselsnummer.asString}",
                "rekrutteringstreffId": "$treffId",
                "endretAv": "$endretAvIdent"
            }
            """.trimIndent()
        )

        val jobbsøker = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)
        assertThat(jobbsøker).isNotNull
        assertThat(jobbsøker!!.hendelser).hasSize(2)

        val opprettHendelse = jobbsøker.hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.OPPRETTET }
        assertThat(opprettHendelse).isNotNull
        opprettHendelse!!.apply {
            assertThat(id).isNotNull()
            assertThat(opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
            assertThat(aktørIdentifikasjon).isEqualTo(endretAvIdent)
        }

        val feilHendelse = jobbsøker.hendelser.find { it.hendelsestype == JobbsøkerHendelsestype.AKTIVITETSKORT_OPPRETTELSE_FEIL }
        assertThat(feilHendelse).isNotNull
        feilHendelse!!.apply {
            assertThat(id).isNotNull()
            assertThat(tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
            assertThat(opprettetAvAktørType).isEqualTo(AktørType.ARRANGØR)
            assertThat(aktørIdentifikasjon).isEqualTo(endretAvIdent)
        }
    }
}