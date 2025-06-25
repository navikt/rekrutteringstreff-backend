package no.nav.toi.rekrutteringstreff

import no.nav.toi.Hendelsestype
import no.nav.toi.JacksonConfig
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
        assertThat(opprett.first().hendelsestype).isEqualTo(Hendelsestype.OPPRETT)

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

        assertThat(hendelser[0].hendelsestype).isEqualTo(Hendelsestype.OPPDATER)
        assertThat(hendelser[1].hendelsestype).isEqualTo(Hendelsestype.OPPDATER)
        assertThat(hendelser[2].hendelsestype).isEqualTo(Hendelsestype.OPPRETT)
        assertThat(hendelser.first().tidspunkt)
            .isAfterOrEqualTo(hendelser.last().tidspunkt)
            .isCloseTo(nowOslo(), within(5, ChronoUnit.SECONDS))
    }

    @Test
    fun `avslutt-metoder registrerer hendelser`() {
        val navIdent = "A123456"
        val id = repository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Test-treff",
                opprettetAvPersonNavident = navIdent,
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )

        repository.avsluttInvitasjon(id, navIdent)
        repository.avsluttArrangement(id, navIdent)
        repository.avsluttOppfolging(id, navIdent)
        repository.avslutt(id, navIdent)

        val hendelser = repository.hentHendelser(id)
        assertThat(hendelser).hasSize(5)
        assertThat(hendelser.map { it.hendelsestype }).containsExactly(
            Hendelsestype.AVSLUTT,
            Hendelsestype.AVSLUTT_OPPFØLGING,
            Hendelsestype.AVSLUTT_ARRANGEMENT,
            Hendelsestype.AVSLUTT_INVITASJON,
            Hendelsestype.OPPRETT
        )
    }
}