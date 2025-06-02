package no.nav.toi.rekrutteringstreff.innlegg

import no.nav.toi.nowOslo
import no.nav.toi.rekrutteringstreff.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.time.temporal.ChronoUnit

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
        // Ensure innlegg are deleted before rekrutteringstreff due to foreign key
        db.dataSource.connection.use {
            it.prepareStatement("DELETE FROM innlegg").executeUpdate()
        }
        db.slettAlt()
    }

    private fun opprettTestTreff(suffix: String = ""): TreffId {
        return rekrutteringstreffRepository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Test Treff for Innlegg $suffix",
                opprettetAvPersonNavident = "Z999999",
                opprettetAvNavkontorEnhetId = "0000",
                opprettetAvTidspunkt = nowOslo()
            )
        )
    }

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

        val opprettetInnlegg = innleggRepository.opprett(treffId, dto)

        assertThat(opprettetInnlegg.id).isNotNull()
        assertThat(opprettetInnlegg.rekrutteringstreffId).isEqualTo(treffId.somUuid)
        assertThat(opprettetInnlegg.tittel).isEqualTo(dto.tittel)
        assertThat(opprettetInnlegg.opprettetAvPersonNavident).isEqualTo(dto.opprettetAvPersonNavident)
        assertThat(opprettetInnlegg.opprettetAvPersonNavn).isEqualTo(dto.opprettetAvPersonNavn)
        assertThat(opprettetInnlegg.opprettetAvPersonBeskrivelse).isEqualTo(dto.opprettetAvPersonBeskrivelse)
        assertThat(opprettetInnlegg.sendesTilJobbsokerTidspunkt).isEqualTo(dto.sendesTilJobbsokerTidspunkt)
        assertThat(opprettetInnlegg.htmlContent).isEqualTo(dto.htmlContent)
        assertThat(opprettetInnlegg.opprettetTidspunkt).isCloseTo(nowOslo(), within(5, ChronoUnit.SECONDS))
        assertThat(opprettetInnlegg.sistOppdatertTidspunkt).isEqualTo(opprettetInnlegg.opprettetTidspunkt)

        val hentetInnlegg = innleggRepository.hentById(opprettetInnlegg.id)
        assertThat(hentetInnlegg).isNotNull
        assertThat(hentetInnlegg).isEqualTo(opprettetInnlegg)
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

        val opprettetInnlegg = innleggRepository.opprett(treffId, dto)

        assertThat(opprettetInnlegg.sendesTilJobbsokerTidspunkt).isNull()
        val hentetInnlegg = innleggRepository.hentById(opprettetInnlegg.id)
        assertThat(hentetInnlegg?.sendesTilJobbsokerTidspunkt).isNull()
    }

    @Test
    fun `hentForTreff skal returnere alle innlegg for et gitt treffId sortert etter opprettelsestidspunkt`() {
        val treffId1 = opprettTestTreff("1")
        val treffId2 = opprettTestTreff("2")

        val innlegg1Dto = OpprettInnleggRequestDto("Innlegg 1", "N1", "Navn1", "Besk1", null, "html1")
        val innlegg2Dto = OpprettInnleggRequestDto("Innlegg 2", "N2", "Navn2", "Besk2", nowOslo().plusHours(1), "html2")
        val innlegg3Dto = OpprettInnleggRequestDto("Innlegg 3 For Annet Treff", "N3", "Navn3", "Besk3", null, "html3")

        val innlegg1 = innleggRepository.opprett(treffId1, innlegg1Dto)
        Thread.sleep(20) // Ensure different timestamps if tests run very fast
        val innlegg2 = innleggRepository.opprett(treffId1, innlegg2Dto)
        innleggRepository.opprett(treffId2, innlegg3Dto)


        val innleggForTreff1 = innleggRepository.hentForTreff(treffId1)

        assertThat(innleggForTreff1).hasSize(2)
        assertThat(innleggForTreff1.map { it.id }).containsExactly(innlegg1.id, innlegg2.id)
        assertThat(innleggForTreff1[0].tittel).isEqualTo("Innlegg 1")
        assertThat(innleggForTreff1[1].tittel).isEqualTo("Innlegg 2")
        assertThat(innleggForTreff1[0].opprettetTidspunkt.toInstant()).isBefore(innleggForTreff1[1].opprettetTidspunkt.toInstant())
    }

    @Test
    fun `hentForTreff skal returnere tom liste hvis ingen innlegg finnes for treffId`() {
        val treffId = opprettTestTreff()
        val innleggForTreff = innleggRepository.hentForTreff(treffId)
        assertThat(innleggForTreff).isEmpty()
    }

    @Test
    fun `hentById skal returnere korrekt innlegg hvis det finnes`() {
        val treffId = opprettTestTreff()
        val dto = OpprettInnleggRequestDto("Tittel", "N", "Navn", "B", null, "html")
        val opprettetInnlegg = innleggRepository.opprett(treffId, dto)

        val hentetInnlegg = innleggRepository.hentById(opprettetInnlegg.id)
        assertThat(hentetInnlegg).isNotNull
        assertThat(hentetInnlegg).isEqualTo(opprettetInnlegg)
    }

    @Test
    fun `hentById skal returnere null hvis innlegg ikke finnes`() {
        val ikkeEksisterendeId = 9999L
        val hentetInnlegg = innleggRepository.hentById(ikkeEksisterendeId)
        assertThat(hentetInnlegg).isNull()
    }

    @Test
    fun `oppdater skal modifisere et eksisterende innlegg og oppdatere sistOppdatertTidspunkt`() {
        val treffId = opprettTestTreff()
        val opprinneligDto = OpprettInnleggRequestDto(
            tittel = "Opprinnelig Tittel",
            opprettetAvPersonNavident = "NAV001",
            opprettetAvPersonNavn = "Kari Veileder",
            opprettetAvPersonBeskrivelse = "Veileder",
            sendesTilJobbsokerTidspunkt = nowOslo().plusDays(2).truncatedTo(ChronoUnit.SECONDS),
            htmlContent = "<p>Opprinnelig innhold.</p>"
        )
        val innlegg = innleggRepository.opprett(treffId, opprinneligDto)
        val opprinneligOpprettetTidspunkt = innlegg.opprettetTidspunkt
        val opprinneligSistOppdatertTidspunkt = innlegg.sistOppdatertTidspunkt

        Thread.sleep(50) // Ensure sistOppdatertTidspunkt will be different

        val oppdateringsDto = OpprettInnleggRequestDto(
            tittel = "Oppdatert Tittel",
            opprettetAvPersonNavident = "NAV002",
            opprettetAvPersonNavn = "Ola Oppdaterer",
            opprettetAvPersonBeskrivelse = "Oppdatert Beskrivelse",
            sendesTilJobbsokerTidspunkt = null,
            htmlContent = "<p>Oppdatert innhold.</p>"
        )

        val oppdatertInnlegg = innleggRepository.oppdater(innlegg.id, oppdateringsDto)

        assertThat(oppdatertInnlegg).isNotNull
        oppdatertInnlegg?.let {
            assertThat(it.id).isEqualTo(innlegg.id)
            assertThat(it.rekrutteringstreffId).isEqualTo(treffId.somUuid)
            assertThat(it.tittel).isEqualTo(oppdateringsDto.tittel)
            assertThat(it.opprettetAvPersonNavident).isEqualTo(oppdateringsDto.opprettetAvPersonNavident)
            assertThat(it.opprettetAvPersonNavn).isEqualTo(oppdateringsDto.opprettetAvPersonNavn)
            assertThat(it.opprettetAvPersonBeskrivelse).isEqualTo(oppdateringsDto.opprettetAvPersonBeskrivelse)
            assertThat(it.sendesTilJobbsokerTidspunkt).isNull()
            assertThat(it.htmlContent).isEqualTo(oppdateringsDto.htmlContent)
            assertThat(it.opprettetTidspunkt.toInstant()).isEqualTo(opprinneligOpprettetTidspunkt.toInstant())
            assertThat(it.sistOppdatertTidspunkt.toInstant()).isAfter(opprinneligSistOppdatertTidspunkt.toInstant())
            assertThat(it.sistOppdatertTidspunkt).isCloseTo(nowOslo(), within(5, ChronoUnit.SECONDS))
        }

        val hentetEtterOppdatering = innleggRepository.hentById(innlegg.id)
        assertThat(hentetEtterOppdatering).isEqualTo(oppdatertInnlegg)
    }

    @Test
    fun `oppdater skal returnere null hvis innlegg ikke finnes`() {
        val ikkeEksisterendeId = 8888L
        val dto = OpprettInnleggRequestDto("T", "N", "N", "B", null, "html")
        val resultat = innleggRepository.oppdater(ikkeEksisterendeId, dto)
        assertThat(resultat).isNull()
    }

    @Test
    fun `slett skal fjerne innlegget fra databasen og returnere true`() {
        val treffId = opprettTestTreff()
        val dto = OpprettInnleggRequestDto("Skal slettes", "N", "N", "B", null, "html")
        val innlegg = innleggRepository.opprett(treffId, dto)

        val slettet = innleggRepository.slett(innlegg.id)
        assertThat(slettet).isTrue()

        val hentetEtterSletting = innleggRepository.hentById(innlegg.id)
        assertThat(hentetEtterSletting).isNull()
    }

    @Test
    fun `slett skal returnere false hvis innlegg ikke finnes`() {
        val ikkeEksisterendeId = 7777L
        val slettet = innleggRepository.slett(ikkeEksisterendeId)
        assertThat(slettet).isFalse()
    }
}