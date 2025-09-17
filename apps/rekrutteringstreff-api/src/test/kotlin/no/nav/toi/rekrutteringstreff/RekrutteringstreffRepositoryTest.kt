package no.nav.toi.rekrutteringstreff

import no.nav.toi.JacksonConfig
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.nowOslo
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
        private val mapper = JacksonConfig.mapper

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()

            repository = RekrutteringstreffRepository(db.dataSource)
        }
    }

    @AfterEach
    fun tearDown() {
        db.slettAlt()
    }

    @Test
    fun `opprett og oppdater registrerer hendelser`() {
        val id = repository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Initielt",
                opprettetAvPersonNavident = "A1",
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )

        val opprett = repository.hentHendelser(id)
        assertThat(opprett).hasSize(1)
        assertThat(opprett.first().hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPRETT)

        repository.oppdater(
            id,
            OppdaterRekrutteringstreffDto(
                tittel = "Ny tittel",
                beskrivelse = null,
                fraTid = null,
                tilTid = null,
                svarfrist = null,
                gateadresse = null,
                postnummer = null,
                poststed = null
            ),
            oppdatertAv = "A1"
        )

        repository.oppdater(
            id,
            OppdaterRekrutteringstreffDto(
                tittel = "Ny tittel",
                beskrivelse = null,
                fraTid = nowOslo(),
                tilTid = nowOslo().plusHours(1),
                svarfrist = nowOslo().minusDays(1),
                gateadresse = "Karl Johans gate 1",
                postnummer = "0154",
                poststed ="Oslo"
            ),
            oppdatertAv = "A1"
        )

        val hendelser = repository.hentHendelser(id)
        assertThat(hendelser).hasSize(3)

        assertThat(hendelser[0].hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPDATER)
        assertThat(hendelser[1].hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPDATER)
        assertThat(hendelser[2].hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPRETT)
        assertThat(hendelser.first().tidspunkt)
            .isAfterOrEqualTo(hendelser.last().tidspunkt)
            .isCloseTo(nowOslo(), within(5, ChronoUnit.SECONDS))
    }

    @Test
    fun `registrerer ulike hendelsestyper i repo`() {
        val navIdent = "A123456"
        val id = repository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Test-treff",
                opprettetAvPersonNavident = navIdent,
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )

        // Legg til ulike hendelsestyper
        repository.gjenapn(id, navIdent)
        repository.fullfor(id, navIdent)
        repository.avlys(id, navIdent)
        repository.avpubliser(id, navIdent)

        val hendelser = repository.hentHendelser(id)
        assertThat(hendelser).hasSize(5)
        assertThat(hendelser.map { it.hendelsestype }).containsExactly(
            RekrutteringstreffHendelsestype.AVPUBLISER,
            RekrutteringstreffHendelsestype.AVLYS,
            RekrutteringstreffHendelsestype.FULLFØR,
            RekrutteringstreffHendelsestype.GJENÅPN,
            RekrutteringstreffHendelsestype.OPPRETT
        )
    }
}