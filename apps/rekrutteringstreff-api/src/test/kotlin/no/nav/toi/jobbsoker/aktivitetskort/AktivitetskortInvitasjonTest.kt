package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AktivitetskortInvitasjonSchedulerTest {

    private val localPostgres = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
        .waitingFor(Wait.forListeningPort())
        .apply { start() }

    private val dataSource = TestDatabase(localPostgres).dataSource

    private val testRepository = TestRepository(dataSource)
    private val aktivitetskortInvitasjonRepository = AktivitetskortInvitasjonRepository(dataSource)
    private val rekrutteringstreffRepository = no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository(dataSource)

    @BeforeEach
    fun setup() {
        testRepository.slettAlt()
    }

    @AfterAll
    fun teardown() {
        localPostgres.close()
    }

    @Test
    fun `skal sende invitasjoner på rapid og markere dem som pollet`() {
        // Arrange
        val rapid = TestRapid()
        val scheduler = AktivitetskortInvitasjonScheduler(aktivitetskortInvitasjonRepository, rekrutteringstreffRepository, rapid)
        val (treffId, _, hendelseId) = testRepository.opprettUsendtInvitasjon()

        // Act
        scheduler.behandleInvitasjoner()

        // Assert
        assertThat(rapid.inspektør.size).isEqualTo(1)
        val melding = rapid.inspektør.message(0)
        assertThat(melding["@event_name"].asText()).isEqualTo("rekrutteringstreffinvitasjon")
        assertThat(melding["rekrutteringstreffId"].asText()).isEqualTo(treffId.toString())

        val usendteEtterpå = aktivitetskortInvitasjonRepository.hentUsendteInvitasjoner()
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal ikke sende samme invitasjon to ganger`() {
        // Arrange
        val rapid = TestRapid()
        val scheduler = AktivitetskortInvitasjonScheduler(aktivitetskortInvitasjonRepository, rekrutteringstreffRepository, rapid)
        testRepository.opprettUsendtInvitasjon()

        // Act
        scheduler.behandleInvitasjoner()
        scheduler.behandleInvitasjoner()

        // Assert
        assertThat(rapid.inspektør.size).isEqualTo(1)
    }

    @Test
    fun `skal ikke gjøre noe hvis det ikke er noen usendte invitasjoner`() {
        // Arrange
        val rapid = TestRapid()
        val scheduler = AktivitetskortInvitasjonScheduler(aktivitetskortInvitasjonRepository, rekrutteringstreffRepository, rapid)

        // Act
        scheduler.behandleInvitasjoner()

        // Assert
        assertThat(rapid.inspektør.size).isEqualTo(0)
    }
}