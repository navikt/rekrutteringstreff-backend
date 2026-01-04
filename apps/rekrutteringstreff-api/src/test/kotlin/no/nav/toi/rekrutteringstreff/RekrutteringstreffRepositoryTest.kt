package no.nav.toi.rekrutteringstreff
import no.nav.toi.*
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.temporal.ChronoUnit
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffRepositoryTest {
    companion object {
        private val db = TestDatabase()
        private lateinit var repository: RekrutteringstreffRepository
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var arbeidsgiverRepository: ArbeidsgiverRepository
        private lateinit var jobbsøkerService: JobbsøkerService
        private lateinit var service: RekrutteringstreffService
        private val mapper = JacksonConfig.mapper
        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()
            jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
            arbeidsgiverRepository = ArbeidsgiverRepository(db.dataSource, mapper)
            jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)
            repository = RekrutteringstreffRepository(db.dataSource)
            service = RekrutteringstreffService(db.dataSource, repository, jobbsøkerRepository, arbeidsgiverRepository, jobbsøkerService)
        }
    }
    @AfterEach
    fun tearDown() {
        db.slettAlt()
    }
    @Test
    fun `opprett og oppdater registrerer hendelser`() {
        val id = service.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Initielt",
                opprettetAvPersonNavident = "A1",
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )
        val opprett = repository.hentHendelser(id)
        assertThat(opprett).hasSize(1)
        assertThat(opprett.first().hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPRETTET)
        service.oppdater(
            id,
            OppdaterRekrutteringstreffDto(
                tittel = "Ny tittel",
                beskrivelse = null,
                fraTid = null,
                tilTid = null,
                svarfrist = null,
                gateadresse = null,
                postnummer = null,
                poststed = null,
                kommune = null,
                kommunenummer = null,
                fylke = null,
                fylkesnummer = null,
            ),
            navIdent = "A1"
        )
        service.oppdater(
            id,
            OppdaterRekrutteringstreffDto(
                tittel = "Ny tittel",
                beskrivelse = null,
                fraTid = nowOslo(),
                tilTid = nowOslo().plusHours(1),
                svarfrist = nowOslo().minusDays(1),
                gateadresse = "Karl Johans gate 1",
                postnummer = "0154",
                poststed ="Oslo",
                kommune = "Oslo",
                kommunenummer = "0301",
                fylke = "Oslo",
                fylkesnummer = "03",
            ),
            navIdent = "A1"
        )
        val hendelser = repository.hentHendelser(id)
        assertThat(hendelser).hasSize(3)
        assertThat(hendelser[0].hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPDATERT)
        assertThat(hendelser[1].hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPDATERT)
        assertThat(hendelser[2].hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPRETTET)
        assertThat(hendelser.first().tidspunkt)
            .isAfterOrEqualTo(hendelser.last().tidspunkt)
            .isCloseTo(nowOslo(), within(5, ChronoUnit.SECONDS))
    }
    @Test
    fun `registrerer ulike hendelsestyper i repo`() {
        val navIdent = "A123456"
        val id = service.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Test-treff",
                opprettetAvPersonNavident = navIdent,
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )
        // Legg til ulike hendelsestyper via executeInTransaction (siden gjenåpne/avpubliser er flyttet til service)
        db.dataSource.executeInTransaction { connection ->
            repository.leggTilHendelseForTreff(connection, id, RekrutteringstreffHendelsestype.GJENÅPNET, navIdent)
            repository.leggTilHendelseForTreff(connection, id, RekrutteringstreffHendelsestype.AVPUBLISERT, navIdent)
        }
        val hendelser = repository.hentHendelser(id)
        assertThat(hendelser).hasSize(3)
        assertThat(hendelser.map { it.hendelsestype }).containsExactlyInAnyOrder(
            RekrutteringstreffHendelsestype.AVPUBLISERT,
            RekrutteringstreffHendelsestype.GJENÅPNET,
            RekrutteringstreffHendelsestype.OPPRETTET
        )
    }
    @Test
    fun `Endre status gjør det den skal`() {
        val id = service.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Initielt",
                opprettetAvPersonNavident = "A1",
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )
        val initieltTreff = repository.hent(id)
        assertThat(initieltTreff?.status).isEqualTo(RekrutteringstreffStatus.UTKAST)
        repository.endreStatus(
            id,
            RekrutteringstreffStatus.PUBLISERT,
        )
        val oppdatertTreff = repository.hent(id)
        assertThat(oppdatertTreff?.status).isEqualTo(RekrutteringstreffStatus.PUBLISERT)
    }
}
