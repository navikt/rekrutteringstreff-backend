package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.AktivitetskortType
import no.nav.toi.aktivitetskort.EndretAvType
import no.nav.toi.ubruktPortnrFra11000.ubruktPortnr
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.internals.AutoOffsetResetStrategy.StrategyType
import org.apache.kafka.clients.producer.MockProducer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringsbistandStillingDelingAvCvTest {
    private val localEnv = mutableMapOf<String, String>(
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_DATABASE" to "test",
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_USERNAME" to "test",
        "NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PASSWORD" to "test"
    )
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    private val localPostgres = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
        .waitingFor(Wait.forListeningPort())
        .apply { start() }
        .also { localConfig ->
            localEnv["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_HOST"] = localConfig.host
            localEnv["NAIS_DATABASE_REKRUTTERINGSBISTAND_AKTIVITETSKORT_AKTIVITETSKORT_DB_PORT"] = localConfig.getMappedPort(5432).toString()
        }

    private val appPort = ubruktPortnr()
    private val rapid = TestRapid()
    private val databaseConfig = DatabaseConfig(localEnv, meterRegistry)
    private val testRepository = TestRepository(databaseConfig)
    private val repository = Repository(databaseConfig, "http://url", "topic")
    private val app = App(
        port = appPort,
        rapidsConnection = rapid,
        repository = repository,
        producer = MockProducer(),
        consumer = MockConsumer(StrategyType.EARLIEST.toString()),
        dabAktivitetskortFeilTopic = "topic",
        leaderElection = LeaderElectionMock(),
        meterRegistry = meterRegistry,
        isRunning = {true},
        isReady = {true},
    )

    @BeforeAll
    fun oppstart() {
        app.start()
    }

    @BeforeEach
    fun setup() {
        rapid.reset()
        testRepository.slettAlt()
    }

    @AfterAll
    fun teardown() {
        localPostgres.close()
        app.stop()
    }

    @Test
    fun `lesing av rekrutteringsbistand-deling-av-cv fra rapid skal lagres i database`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val tittel = "Test Stilling"
        val opprettetAv = "testuser"
        val opprettetTidspunkt = ZonedDateTime.now()
        val arbeidsGiver = "LINE LOTTE LIFESTYLE LINE LOTTE TANGEN"
        val arbeidssted = "Viken"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr,
                stillingId,
                tittel,
                opprettetAv,
                arbeidsGiver,
                arbeidssted
            )
        )
        val rekrutteringsbistandStillinger = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(rekrutteringsbistandStillinger).hasSize(1)
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)

        val expectedDetaljer = """[{"label":"Arbeidsgiver","verdi":"$arbeidsGiver"},{"label":"Arbeidssted","verdi":"$arbeidssted"}]"""
        rekrutteringsbistandStillinger.apply {
            assertThat(this[0].tittel).isEqualTo(tittel)
            assertThat(this[0].stillingId).isEqualTo(stillingId)
            assertThat(this[0].beskrivelse).isEqualTo("Nav hjelper en arbeidsgiver med å finne kandidater til en stilling, og tror den kan passe for deg.")
            assertThat(this[0].detaljer).isEqualToIgnoringWhitespace( expectedDetaljer)
            assertThat(this[0].aktivitetskortId).isEqualTo(inspektør.message(0)["aktivitetskortuuid"].asText().toUUID())
            assertThat(this[0].aktivitetsStatus).isEqualTo(AktivitetsStatus.FORSLAG.name)
            assertThat(this[0].aktivitetsType).isEqualTo(AktivitetskortType.DELTSTILLING.name)
            assertThat(this[0].opprettetAv).isEqualTo(opprettetAv)
            assertThat(this[0].opprettetTidspunkt).isBetween(opprettetTidspunkt, ZonedDateTime.now())
        }
    }

    @Test
    fun `lesing av rekrutteringsbistand-deling-av-cv fra rapid med samme kandidat og stilling skal ignoreres`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val tittel = "Test Stilling"
        val opprettetAv = "testuser"
        val arbeidsGiver = "LINE LOTTE LIFESTYLE LINE LOTTE TANGEN"
        val arbeidssted = "Viken"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr,
                stillingId,
                tittel,
                opprettetAv,
                arbeidsGiver,
                arbeidssted
            )
        )
        val expectedRekrutteringsbistandStillinger = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(expectedRekrutteringsbistandStillinger).hasSize(1)
        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr,
                stillingId,
                tittel,
                opprettetAv,
                arbeidsGiver,
                arbeidssted
            )
        )

        val actualRekrutteringsbistandStillinger = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(actualRekrutteringsbistandStillinger).hasSize(1)
        assertThat(actualRekrutteringsbistandStillinger.first()).usingRecursiveComparison().isEqualTo(expectedRekrutteringsbistandStillinger.first())
        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)
    }

    @Test
    fun `lesing av rekrutteringsbistand-deling-av-cv skal returnere aktivitetskortuuid på rapid`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val tittel = "Test Stilling"
        val opprettetAv = "testuser"
        val arbeidsgiver = "LINE LOTTE LIFESTYLE LINE LOTTE TANGEN"
        val arbeidssted = "Viken"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr,
                stillingId,
                tittel,
                opprettetAv,
                arbeidsgiver,
                arbeidssted
            )
        )

        val inspektør = rapid.inspektør
        assertThat(inspektør.size).isEqualTo(1)
        inspektør.message(0).also { message ->
            assertThat(message["@event_name"].asText()).isEqualTo("rekrutteringsbistandstilling-deling-av-cv")
            assertThat(message["fnr"].asText()).isEqualTo(fnr)
            assertThat(message["stillingId"].asText()).isEqualTo(stillingId.toString())
            assertThat(message["tittel"].asText()).isEqualTo(tittel)
            assertThat(message["opprettetAv"].asText()).isEqualTo(opprettetAv)
            assertThat(message["aktivitetskortuuid"].isMissingOrNull()).isFalse
            assertThat(message["arbeidssted"].asText()).isEqualTo(arbeidssted)
            assertThat(message["arbeidsgiver"].asText()).isEqualTo(arbeidsgiver)
        }
    }

    @Test
    fun `svar ja til deling av CV skal flytte aktivitetskort til gjennomføres`() {
        testSvarPåDelingAvCv(
            eventName = "rekrutteringsbistandstilling-bruker-svarer-ja-til-deling-av-cv",
            svar = true,
            forventetAktivitetsStatus = AktivitetsStatus.GJENNOMFORES,
        )
    }

    @Test
    fun `svar nei til deling av CV skal flytte aktivitetskort til avbrutt`() {
        testSvarPåDelingAvCv(
            eventName = "rekrutteringsbistandstilling-bruker-svarer-nei-til-deling-av-cv",
            svar = false,
            forventetAktivitetsStatus = AktivitetsStatus.AVBRUTT,
        )
    }

    @Test
    fun `samme svar skal bare lagres én gang`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        repository.opprettDeltStilling(
            fnr = fnr,
            stillingId = stillingId.toString(),
            tittel = "Test Stilling",
            opprettetAv = "Z123456",
            arbeidsgiver = "Test Arbeidsgiver",
            arbeidssted = "Oslo",
        )
        val melding = svarMelding(
            eventName = "rekrutteringsbistandstilling-bruker-svarer-ja-til-deling-av-cv",
            fnr = fnr,
            stillingId = stillingId,
            svar = true,
        )
        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(1)

        rapid.sendTestMessage(melding)

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(2)

        rapid.sendTestMessage(melding)

        assertThat(testRepository.hentAlleRekrutteringsbistandStillinger()).hasSize(2)
    }

    @Test
    fun `nytt svar skal lagres når aktivitetsstatus er endret`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val navIdent = "Z123456"
        repository.opprettDeltStilling(
            fnr = fnr,
            stillingId = stillingId.toString(),
            tittel = "Test Stilling",
            opprettetAv = navIdent,
            arbeidsgiver = "Test Arbeidsgiver",
            arbeidssted = "Oslo",
        )

        rapid.sendTestMessage(
            svarMelding(
                eventName = "rekrutteringsbistandstilling-bruker-svarer-ja-til-deling-av-cv",
                fnr = fnr,
                stillingId = stillingId,
                svar = true,
            )
        )
        rapid.sendTestMessage(
            svarMelding(
                eventName = "rekrutteringsbistandstilling-bruker-svarer-nei-til-deling-av-cv",
                fnr = fnr,
                stillingId = stillingId,
                svar = false,
            )
        )

        val statusendringer = testRepository.hentAlleRekrutteringsbistandStillinger().map {
            Triple(it.aktivitetsStatus, it.opprettetAv, it.opprettetAvType)
        }
        assertThat(statusendringer).containsExactlyInAnyOrder(
            Triple(AktivitetsStatus.FORSLAG.name, navIdent, EndretAvType.NAVIDENT.name),
            Triple(AktivitetsStatus.GJENNOMFORES.name, fnr, EndretAvType.PERSONBRUKERIDENT.name),
            Triple(AktivitetsStatus.AVBRUTT.name, fnr, EndretAvType.PERSONBRUKERIDENT.name),
        )
    }

    @Test
    fun `registrert fått jobben skal flytte aktivitetskort til fullført når kandidat har svart ja`() {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        val navIdent = "Z123456"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr = fnr,
                stillingId = stillingId,
                tittel = "Test Stilling",
                opprettetAv = navIdent,
                arbeidsgiver = "Test Arbeidsgiver",
                arbeidssted = "Oslo",
            )
        )
        rapid.sendTestMessage(
            svarMelding(
                eventName = "rekrutteringsbistandstilling-bruker-svarer-ja-til-deling-av-cv",
                fnr = fnr,
                stillingId = stillingId,
                svar = true,
            )
        )
        rapid.sendTestMessage(
            registrertFattJobbenMelding(
                stillingId = stillingId,
                fnr = fnr,
                navIdent = navIdent,
            )
        )

        val hendelser = testRepository.hentAlleRekrutteringsbistandStillinger().filter { it.fnr == fnr }
        assertThat(hendelser).hasSize(3)
        hendelser.last().also { hendelse ->
            assertThat(hendelse.aktivitetsStatus).isEqualTo(AktivitetsStatus.FULLFORT.name)
            assertThat(hendelse.opprettetAv).isEqualTo(navIdent)
            assertThat(hendelse.opprettetAvType).isEqualTo(EndretAvType.NAVIDENT.name)
        }
    }

    @Test
    fun `lukket kandidatliste skal avbryte bare kandidater som har svart ja`() {
        val stillingId = UUID.randomUUID()
        val navIdent = "Z999999"
        val kandidatSomHarSvartJa = "01010012345"
        val kandidatSomHarSvartNei = "02020012345"

        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr = kandidatSomHarSvartJa,
                stillingId = stillingId,
                tittel = "Test Stilling",
                opprettetAv = navIdent,
                arbeidsgiver = "Test Arbeidsgiver",
                arbeidssted = "Oslo",
            )
        )
        rapid.sendTestMessage(
            rapidPeriodeMelding(
                fnr = kandidatSomHarSvartNei,
                stillingId = stillingId,
                tittel = "Test Stilling",
                opprettetAv = navIdent,
                arbeidsgiver = "Test Arbeidsgiver",
                arbeidssted = "Oslo",
            )
        )

        rapid.sendTestMessage(
            svarMelding(
                eventName = "rekrutteringsbistandstilling-bruker-svarer-ja-til-deling-av-cv",
                fnr = kandidatSomHarSvartJa,
                stillingId = stillingId,
                svar = true,
            )
        )
        rapid.sendTestMessage(
            svarMelding(
                eventName = "rekrutteringsbistandstilling-bruker-svarer-nei-til-deling-av-cv",
                fnr = kandidatSomHarSvartNei,
                stillingId = stillingId,
                svar = false,
            )
        )

        rapid.sendTestMessage(
            lukketKandidatlisteMelding(
                stillingId = stillingId,
                navIdent = navIdent,
                fnrFikkJobben = listOf("9999"),
                fnrFikkIkkeJobben = listOf(kandidatSomHarSvartJa, kandidatSomHarSvartNei),
            )
        )

        val jaKandidatHendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
            .filter { it.fnr == kandidatSomHarSvartJa }
        val neiKandidatHendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
            .filter { it.fnr == kandidatSomHarSvartNei }

        assertThat(jaKandidatHendelser).hasSize(3)
        assertThat(jaKandidatHendelser.last().aktivitetsStatus).isEqualTo(AktivitetsStatus.FULLFORT.name)
        assertThat(jaKandidatHendelser.last().opprettetAv).isEqualTo(navIdent)
        assertThat(jaKandidatHendelser.last().opprettetAvType).isEqualTo(EndretAvType.NAVIDENT.name)

        // Kandidat med NEI skal ikke oppdateres pa nytt ved lukking av liste.
        assertThat(neiKandidatHendelser).hasSize(2)
    }

    private fun testSvarPåDelingAvCv(
        eventName: String,
        svar: Boolean,
        forventetAktivitetsStatus: AktivitetsStatus,
    ) {
        val fnr = "01010012345"
        val stillingId = UUID.randomUUID()
        repository.opprettDeltStilling(
            fnr = fnr,
            stillingId = stillingId.toString(),
            tittel = "Test Stilling",
            opprettetAv = "Z123456",
            arbeidsgiver = "Test Arbeidsgiver",
            arbeidssted = "Oslo",
        )
        rapid.sendTestMessage(
            svarMelding(
                eventName = eventName,
                fnr = fnr,
                stillingId = stillingId,
                svar = svar,
            )
        )

        val aktivitetskortHendelser = testRepository.hentAlleRekrutteringsbistandStillinger()
        assertThat(aktivitetskortHendelser).hasSize(2)
        aktivitetskortHendelser.last().also { hendelse ->
            assertThat(hendelse.aktivitetsStatus).isEqualTo(forventetAktivitetsStatus.name)
            assertThat(hendelse.opprettetAv).isEqualTo(fnr)
            assertThat(hendelse.opprettetAvType).isEqualTo(EndretAvType.PERSONBRUKERIDENT.name)
        }
        assertThat(rapid.inspektør.size).isEqualTo(0)
    }
    private fun svarMelding(
        eventName: String,
        fnr: String,
        stillingId: UUID,
        svar: Boolean,
    ) = """
        {
            "@event_name": "$eventName",
            "fnr": "$fnr",
            "stillingId": "$stillingId",
            "svar": $svar,
            "aktørId": "Dummy aktørId"
        }
    """.trimIndent()

    private fun rapidPeriodeMelding(
        fnr: String,
        stillingId: UUID,
        tittel: String,
        opprettetAv: String,
        arbeidsgiver: String,
        arbeidssted: String,
    ): String = """
        {
            "@event_name": "rekrutteringsbistandstilling-deling-av-cv",
            "fnr":"$fnr",
            "stillingId":"$stillingId",
            "tittel": "$tittel",
            "opprettetAv": "$opprettetAv",
            "arbeidsgiver":"$arbeidsgiver",
            "arbeidssted":"$arbeidssted",
            "aktørId": "Dummy aktørId"
        }
        """.trimIndent()

    private fun registrertFattJobbenMelding(
        stillingId: UUID,
        fnr: String,
        navIdent: String,
    ) = """
        {
            "@event_name": "RegistrertFåttJobben",
            "stillingsId": "$stillingId",
            "fnr": "$fnr",
            "utførtAvNavIdent": "$navIdent",
            "tidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}"
        }
    """.trimIndent()

    private fun lukketKandidatlisteMelding(
        stillingId: UUID,
        navIdent: String,
        fnrFikkJobben: List<String>,
        fnrFikkIkkeJobben: List<String>,
    ) = """
        {
            "@event_name": "LukketKandidatliste",
            "stillingsId": "$stillingId",
            "utførtAvNavIdent": "$navIdent",
            "tidspunkt": "${ZonedDateTime.now().truncatedTo(ChronoUnit.MILLIS)}",
            "fnrFikkJobben": [${fnrFikkJobben.joinToString(",") { "\"$it\"" }}],
            "fnrFikkIkkeJobben": [${fnrFikkIkkeJobben.joinToString(",") { "\"$it\"" }}]
        }
    """.trimIndent()
}
