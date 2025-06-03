package no.nav.toi.rekrutteringstreff.innlegg

import no.nav.toi.nowOslo
import no.nav.toi.rekrutteringstreff.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.*
import java.time.temporal.ChronoUnit
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InnleggRepositoryTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var innleggRepository: InnleggRepository
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()

            innleggRepository = InnleggRepository(db.dataSource)
            rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
        }
    }

    @AfterEach
    fun tearDown() {
        db.dataSource.connection.use {
            it.prepareStatement("DELETE FROM innlegg").executeUpdate()
        }
        db.slettAlt()
    }

    private fun opprettTestTreff(suffix: String = ""): TreffId =
        rekrutteringstreffRepository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Test Treff for Innlegg $suffix",
                opprettetAvPersonNavident = "Z999999",
                opprettetAvNavkontorEnhetId = "0000",
                opprettetAvTidspunkt = nowOslo()
            )
        )

    @Test
    fun `opprett skal lagre innlegg og returnere det med generert id og tidspunkter`() {
        val treffId = opprettTestTreff()
        val dto = OpprettInnleggRequestDto(
            tittel = "Test Innlegg Tittel",
            opprettetAvPersonNavident = "X123456",
            opprettetAvPersonNavn = "Ola Nordmann",
            opprettetAvPersonBeskrivelse = "Veileder",
            sendesTilJobbsokerTidspunkt = nowOslo().plusDays(1).truncatedTo(ChronoUnit.SECONDS),
            htmlContent = "<p>Dette er testinnhold.</p>"
        )

        val opprettet = innleggRepository.opprett(treffId, dto)

        assertThat(opprettet.id).isNotNull
        assertThat(opprettet.treffId).isEqualTo(treffId.somUuid)
        assertThat(opprettet.tittel).isEqualTo(dto.tittel)
        assertThat(opprettet.opprettetAvPersonNavident).isEqualTo(dto.opprettetAvPersonNavident)
        assertThat(opprettet.opprettetAvPersonNavn).isEqualTo(dto.opprettetAvPersonNavn)
        assertThat(opprettet.opprettetAvPersonBeskrivelse).isEqualTo(dto.opprettetAvPersonBeskrivelse)
        assertThat(opprettet.sendesTilJobbsokerTidspunkt).isEqualTo(dto.sendesTilJobbsokerTidspunkt)
        assertThat(opprettet.htmlContent).isEqualTo(dto.htmlContent)
        assertThat(opprettet.opprettetTidspunkt).isCloseTo(nowOslo(), within(5, ChronoUnit.SECONDS))
        assertThat(opprettet.sistOppdatertTidspunkt).isEqualTo(opprettet.opprettetTidspunkt)

        val hentet = innleggRepository.hentById(opprettet.id)
        assertThat(hentet).isEqualTo(opprettet)
    }

    @Test
    fun `opprett skal håndtere null sendesTilJobbsokerTidspunkt`() {
        val treffId = opprettTestTreff()
        val dto = OpprettInnleggRequestDto(
            tittel = "Test Innlegg Uten Sendetidspunkt",
            opprettetAvPersonNavident = "X123456",
            opprettetAvPersonNavn = "Ola Nordmann",
            opprettetAvPersonBeskrivelse = "Veileder",
            sendesTilJobbsokerTidspunkt = null,
            htmlContent = "<p>Innhold.</p>"
        )

        val opprettet = innleggRepository.opprett(treffId, dto)

        assertThat(opprettet.sendesTilJobbsokerTidspunkt).isNull()
        val hentet = innleggRepository.hentById(opprettet.id)
        assertThat(hentet?.sendesTilJobbsokerTidspunkt).isNull()
    }

    @Test
    fun `hentForTreff skal returnere alle innlegg for et gitt treffId sortert etter opprettelsestidspunkt`() {
        val treff1 = opprettTestTreff("1")
        val treff2 = opprettTestTreff("2")

        val i1 = innleggRepository.opprett(
            treff1,
            OpprettInnleggRequestDto("Innlegg 1", "N1", "Navn1", "B1", null, "html1")
        )
        val i2 = innleggRepository.opprett(
            treff1,
            OpprettInnleggRequestDto("Innlegg 2", "N2", "Navn2", "B2", nowOslo().plusHours(1), "html2")
        )
        innleggRepository.opprett(
            treff2,
            OpprettInnleggRequestDto("Innlegg 3", "N3", "Navn3", "B3", null, "html3")
        )

        val innlegg = innleggRepository.hentForTreff(treff1)

        assertThat(innlegg.map { it.id }).containsExactly(i1.id, i2.id)
    }

    @Test
    fun `hentById skal returnere korrekt innlegg hvis det finnes`() {
        val treff = opprettTestTreff()
        val opprettet = innleggRepository.opprett(
            treff,
            OpprettInnleggRequestDto("T", "N", "Navn", "B", null, "html")
        )
        assertThat(innleggRepository.hentById(opprettet.id)).isEqualTo(opprettet)
    }

    @Test
    fun `oppdater skal modifisere et eksisterende innlegg og oppdatere sistOppdatertTidspunkt`() {
        val treff = opprettTestTreff()
        val original = innleggRepository.opprett(
            treff,
            OpprettInnleggRequestDto(
                "Old",
                "NAV001",
                "Kari",
                "Veileder",
                nowOslo().plusDays(2).truncatedTo(ChronoUnit.SECONDS),
                "<p>Old</p>"
            )
        )
        val updated = innleggRepository.oppdater(
            original.id,
            OpprettInnleggRequestDto("New", "NAV002", "Ola", "Oppdatert", null, "<p>New</p>")
        )

        assertThat(updated?.treffId).isEqualTo(treff.somUuid)
        assertThat(updated?.sistOppdatertTidspunkt?.toInstant())
            .isAfter(original.sistOppdatertTidspunkt.toInstant())
    }

    @Test
    fun `slett skal fjerne innlegget fra databasen`() {
        val treff = opprettTestTreff()
        val innlegg = innleggRepository.opprett(
            treff,
            OpprettInnleggRequestDto("Del", "N", "", "", null, "html")
        )
        assertThat(innleggRepository.slett(innlegg.id)).isTrue()
        assertThat(innleggRepository.hentById(innlegg.id)).isNull()
    }
}
