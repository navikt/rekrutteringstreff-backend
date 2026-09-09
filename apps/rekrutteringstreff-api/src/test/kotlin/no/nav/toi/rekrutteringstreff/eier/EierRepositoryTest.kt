package no.nav.toi.rekrutteringstreff.eier

import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import io.mockk.every
import io.mockk.spyk
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.executeInTransaction
import no.nav.toi.nowOslo
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilNavIdenter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.SQLException
import java.time.temporal.ChronoUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EierRepositoryTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var repository: EierRepository

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure().dataSource(db.dataSource).load().migrate()
            repository = EierRepository(db.dataSource)
        }
    }

    @AfterEach
    fun slettAlt() {
        db.slettAlt()
    }

    @Test
    fun `hent returnerer eiere for et treff`() {
        val navIdent = "A123456"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")

        val eiere = repository.hent(treffId)

        assertThat(eiere).isNotNull
        assertThat(eiere).hasSize(1)
        assertThat(eiere!!.tilNavIdenter().first()).isEqualTo(navIdent)
    }

    @Test
    fun `hent returnerer null for treff som ikke finnes`() {
        val ikkeEksisterendeTreff = TreffId("00000000-0000-0000-0000-000000000000")

        val eiere = repository.hent(ikkeEksisterendeTreff)

        assertThat(eiere).isNull()
    }

    @Test
    fun `leggTil legger til en ny eier`() {
        val navIdent = "A123456"
        val eierNavIdent = "B654321"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")

        repository.leggTil(treffId, eierNavIdent, "0315")

        val eiere = repository.hent(treffId)
        assertThat(eiere).isNotNull
        assertThat(eiere!!.tilNavIdenter()).containsExactlyInAnyOrder(navIdent, eierNavIdent)
        assertThat(db.hentEierrader(treffId).filter { it.navIdent == eierNavIdent })
            .hasSize(1)
            .allSatisfy {
                assertThat(it.kontorEnhetId).isEqualTo("0315")
                assertThat(it.lagtTilAv).isEqualTo(it.navIdent)
                assertThat(it.eierNavn).isNull()
            }
    }

    @Test
    fun `blank Nav-ident avvises uten å skrive`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        val før = db.hentEierrader(treffId)

        listOf("", " ").forEach { eierNavIdent ->
            assertThatThrownBy { repository.leggTil(treffId, eierNavIdent, "0315") }
                .isInstanceOf(IllegalArgumentException::class.java)
                .hasMessage("Eier må ha Nav-ident")
        }

        assertThat(repository.hent(treffId)!!.tilNavIdenter()).containsExactly("A123456")
        assertThat(db.hentEierrader(treffId)).isEqualTo(før)
    }

    @Test
    fun `leggTil avviser treff som ikke finnes`() {
        val treffId = TreffId("00000000-0000-0000-0000-000000000000")

        assertThatThrownBy { repository.leggTil(treffId, "B654321", "0315") }
            .isInstanceOf(NotFoundResponse::class.java)

        assertThat(repository.hent(treffId)).isNull()
        assertThat(db.hentEierrader(treffId)).isEmpty()
    }

    @Test
    fun `leggTil legger ikke til duplikater`() {
        val navIdent = "A123456"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")

        // Legg til samme eier to ganger
        repository.leggTil(treffId, navIdent, "0315")
        repository.leggTil(treffId, navIdent, "0315")

        val eiere = repository.hent(treffId)
        assertThat(eiere).isNotNull
        assertThat(eiere!!.tilNavIdenter()).containsExactly(navIdent)
        assertThat(db.hentEierrader(treffId)).hasSize(1)
    }

    @Test
    fun `slett fjerner en eier`() {
        val navIdent = "A123456"
        val andreEier = "B654321"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")
        repository.leggTil(treffId, andreEier, "0315")

        val eiereFør = repository.hent(treffId)
        assertThat(eiereFør!!.tilNavIdenter()).contains(andreEier)

        repository.slett(treffId, andreEier)

        val eiereEtter = repository.hent(treffId)
        assertThat(eiereEtter!!.tilNavIdenter()).doesNotContain(andreEier)
        assertThat(eiereEtter.tilNavIdenter()).contains(navIdent)
        assertThat(db.hentEierrader(treffId).map { it.navIdent }).containsExactly(navIdent)
    }

    @Test
    fun `slett gjør ingenting når eier ikke finnes`() {
        val navIdent = "A123456"
        val ikkeEksisterendeEier = "Z999999"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")

        val eiereFør = repository.hent(treffId)
        assertThat(eiereFør).hasSize(1)

        repository.slett(treffId, ikkeEksisterendeEier)

        val eiereEtter = repository.hent(treffId)
        assertThat(eiereEtter).hasSize(1)
    }

    @Test
    fun `tilNavIdenter mapper liste av Eiere til liste av navIdenter`() {
        val navIdent = "A123456"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")
        repository.leggTil(treffId, "B654321", "0315")

        val eiere = repository.hent(treffId)

        val navIdenter = eiere!!.tilNavIdenter()

        assertThat(navIdenter).containsExactlyInAnyOrder("A123456", "B654321")
    }

    @Test
    fun `opprett skriver oppretter med kontor og metadata i eiertabellen`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(
            navIdent = "A123456", opprettetAvNavkontorEnhetId = "0315"
        )
        val treff = RekrutteringstreffRepository(db.dataSource).hent(treffId)!!
        val eier = db.hentEierrader(treffId).single()

        assertThat(eier.navIdent).isEqualTo("A123456")
        assertThat(eier.kontorEnhetId).isEqualTo("0315")
        assertThat(eier.lagtTilAv).isEqualTo("A123456")
        assertThat(eier.eierNavn).isNull()
        assertThat(eier.lagtTilTidspunkt.truncatedTo(ChronoUnit.MILLIS)).isEqualTo(treff.opprettetAvTidspunkt.toInstant())
        assertThat(treff.eiere).containsExactly("A123456")
        assertThat(treff.kontorer).containsExactly("0315")
    }

    @Test
    fun `siste eier beholdes i begge lagringsformer`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        val før = db.hentEierrader(treffId)

        assertThat(repository.slett(treffId, "A123456")).isFalse()

        assertThat(repository.hent(treffId)!!.tilNavIdenter()).containsExactly("A123456")
        assertThat(db.hentEierrader(treffId)).isEqualTo(før)
    }

    @Test
    fun `eier kan slettes og legges til igjen uten å endre andre treff`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        val annetTreff = db.opprettRekrutteringstreffIDatabase(navIdent = "B654321")
        val annenEierrad = db.hentEierrader(annetTreff).single()
        repository.leggTil(treffId, "B654321", "0315")
        val gammelId = db.hentEierrader(treffId).single { it.navIdent == "B654321" }.id

        assertThat(repository.slett(treffId, "B654321")).isTrue()
        repository.leggTil(treffId, "B654321", "1201")

        assertThat(repository.hent(treffId)!!.tilNavIdenter()).containsExactlyInAnyOrder("A123456", "B654321")
        val nyEierrad = db.hentEierrader(treffId).single { it.navIdent == "B654321" }
        assertThat(nyEierrad.id).isNotEqualTo(gammelId)
        assertThat(nyEierrad.kontorEnhetId).isEqualTo("1201")
        assertThat(db.hentEierrader(annetTreff)).containsExactly(annenEierrad)
    }

    @Test
    fun `historisk eier uten eierrad kan slettes`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        db.oppdaterRekrutteringstreff(listOf("A123456", "B654321"), treffId)
        val treffRepository = RekrutteringstreffRepository(db.dataSource)

        EierService(repository, treffRepository, db.dataSource).slettEier(treffId, "B654321", "A123456")

        assertThat(repository.hent(treffId)!!.tilNavIdenter()).containsExactly("A123456")
        assertThat(db.hentEierrader(treffId).map { it.navIdent }).containsExactly("A123456")
        assertThat(treffRepository.hentAlleHendelser(treffId)).anyMatch {
            it.hendelsestype == "EIER_FJERNET" && it.subjektId == "B654321"
        }
    }

    @Test
    fun `gjentatt tillegg fyller historisk eierrad og oppdaterer kontor uten ny eierhendelse`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        db.oppdaterRekrutteringstreff(listOf("A123456", "B654321"), treffId)
        val treffRepository = RekrutteringstreffRepository(db.dataSource)
        val service = EierService(repository, treffRepository, db.dataSource)

        service.leggTilEierMedKontor(treffId, "B654321", "0315")
        val før = db.hentEierrader(treffId).single { it.navIdent == "B654321" }
        service.leggTilEierMedKontor(treffId, "B654321", "1201")
        service.leggTilEierMedKontor(treffId, "B654321", "1201")

        assertThat(db.hentEierrader(treffId).single { it.navIdent == "B654321" })
            .isEqualTo(før.copy(kontorEnhetId = "1201"))
        assertThat(repository.hent(treffId)!!.tilNavIdenter()).containsExactlyInAnyOrder("A123456", "B654321")
        assertThat(treffRepository.hent(treffId)!!.kontorer).contains("0315", "1201")
        val hendelser = treffRepository.hentAlleHendelser(treffId)
        assertThat(hendelser.filter { it.hendelsestype == "EIER_LAGT_TIL" }).isEmpty()
        assertThat(hendelser.filter { it.hendelsestype == "KONTOR_LAGT_TIL" }).hasSize(2)
    }

    @Test
    fun `sletting beholder kontorarray og kontorbasert tilgang i fase 2`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        val treffRepository = RekrutteringstreffRepository(db.dataSource)
        val service = EierService(repository, treffRepository, db.dataSource)
        service.leggTilEierMedKontor(treffId, "B654321", "0315")

        service.slettEier(treffId, "B654321", "A123456")

        assertThat(db.hentEierrader(treffId).map { it.navIdent }).containsExactly("A123456")
        assertThat(treffRepository.hent(treffId)!!.kontorer).contains("0315")
        assertThat(service.harTilgangViaTreffkontor(treffId, listOf("0315"))).isTrue()
    }

    @Test
    fun `feil ved kontorhendelse ruller tilbake eiere kontorer eierrader og hendelser`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        val treffRepository = spyk(RekrutteringstreffRepository(db.dataSource))
        val treffFør = treffRepository.hent(treffId)
        val eierraderFør = db.hentEierrader(treffId)
        val hendelserFør = treffRepository.hentAlleHendelser(treffId)
        every {
            treffRepository.leggTilHendelseForTreff(
                any(), any(), RekrutteringstreffHendelsestype.KONTOR_LAGT_TIL, any(), any(), any(), any()
            )
        } throws IllegalStateException("Simulert skrivefeil")

        assertThatThrownBy {
            EierService(repository, treffRepository, db.dataSource).leggTilEierMedKontor(treffId, "B654321", "0315")
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(treffRepository.hent(treffId)).usingRecursiveComparison().isEqualTo(treffFør)
        assertThat(db.hentEierrader(treffId)).isEqualTo(eierraderFør)
        assertThat(treffRepository.hentAlleHendelser(treffId)).isEqualTo(hendelserFør)
    }

    @Test
    fun `feil ved slettehendelse ruller tilbake begge lagringsformer`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        repository.leggTil(treffId, "B654321", "0315")
        val eierraderFør = db.hentEierrader(treffId)
        val treffRepository = spyk(RekrutteringstreffRepository(db.dataSource))
        val hendelserFør = treffRepository.hentAlleHendelser(treffId)
        every {
            treffRepository.leggTilHendelseForTreff(
                any(), any(), RekrutteringstreffHendelsestype.EIER_FJERNET, any(), any(), any(), any()
            )
        } throws IllegalStateException("Simulert skrivefeil")

        assertThatThrownBy {
            EierService(repository, treffRepository, db.dataSource).slettEier(treffId, "B654321", "A123456")
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(repository.hent(treffId)!!.tilNavIdenter()).containsExactlyInAnyOrder("A123456", "B654321")
        assertThat(db.hentEierrader(treffId)).isEqualTo(eierraderFør)
        assertThat(treffRepository.hentAlleHendelser(treffId)).isEqualTo(hendelserFør)
    }

    @Test
    fun `rollback ved opprettelse fjerner også eierraden`() {
        val treffRepository = RekrutteringstreffRepository(db.dataSource)
        lateinit var treffId: TreffId

        assertThatThrownBy {
            db.dataSource.executeInTransaction { connection ->
                treffId = treffRepository.opprett(connection, OpprettRekrutteringstreffInternalDto(
                    "Test", RekrutteringstreffKategori.REKRUTTERINGSTREFF, "A123456", "0315", nowOslo()
                )).first
                error("Simulert skrivefeil")
            }
        }.isInstanceOf(IllegalStateException::class.java)

        assertThat(treffRepository.hent(treffId)).isNull()
        assertThat(db.hentEierrader(treffId)).isEmpty()
    }

    @Test
    fun `blankt kontor avvises uten å skrive`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        val før = db.hentEierrader(treffId)

        assertThatThrownBy { repository.leggTil(treffId, "B654321", " ") }
            .isInstanceOf(IllegalArgumentException::class.java)

        assertThat(repository.hent(treffId)!!.tilNavIdenter()).containsExactly("A123456")
        assertThat(db.hentEierrader(treffId)).isEqualTo(før)
    }

    @Test
    fun `feil i eiertabellen ruller tilbake opprettelse og endring av treffraden`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        val treffRepository = RekrutteringstreffRepository(db.dataSource)
        val før = db.hentEierrader(treffId)
        db.dataSource.connection.use { connection ->
            connection.createStatement().use {
                it.execute("ALTER TABLE rekrutteringstreff_eier ADD CONSTRAINT test_avvist_kontor CHECK (kontor_enhetid <> 'avvist')")
            }
            try {
                assertThatThrownBy {
                    treffRepository.opprett(connection, OpprettRekrutteringstreffInternalDto(
                        "Test", RekrutteringstreffKategori.REKRUTTERINGSTREFF, "B654321", "avvist", nowOslo()
                    ))
                }.isInstanceOf(SQLException::class.java)
                assertThatThrownBy {
                    repository.leggTil(connection, treffId, "B654321", "avvist")
                }.isInstanceOf(SQLException::class.java)
            } finally {
                connection.createStatement().use {
                    it.execute("ALTER TABLE rekrutteringstreff_eier DROP CONSTRAINT test_avvist_kontor")
                }
            }
        }

        assertThat(treffRepository.hentAlle().map { it.id }).containsExactly(treffId)
        assertThat(repository.hent(treffId)!!.tilNavIdenter()).containsExactly("A123456")
        assertThat(db.hentEierrader(treffId)).isEqualTo(før)
    }

    @Test
    fun `samtidige tillegg av samme eier lager én eierrad og én eierhendelse`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        val treffRepository = RekrutteringstreffRepository(db.dataSource)
        val service = EierService(repository, treffRepository, db.dataSource)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(4)
        try {
            val kall = (1..4).map {
                pool.submit {
                    check(start.await(10, TimeUnit.SECONDS))
                    service.leggTilEierMedKontor(treffId, "B654321", "0315")
                }
            }
            start.countDown()
            kall.forEach { it.get(15, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }

        assertThat(repository.hent(treffId)!!.tilNavIdenter()).containsExactlyInAnyOrder("A123456", "B654321")
        assertThat(db.hentEierrader(treffId).filter { it.navIdent == "B654321" }).hasSize(1)
        assertThat(treffRepository.hentAlleHendelser(treffId).filter { it.hendelsestype == "EIER_LAGT_TIL" }).hasSize(1)
        assertThat(treffRepository.hentAlleHendelser(treffId).filter { it.hendelsestype == "KONTOR_LAGT_TIL" }).hasSize(1)
    }

    @Test
    fun `samtidige slettinger beholder siste eier i begge lagringsformer`() {
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = "A123456")
        repository.leggTil(treffId, "B654321", "0315")
        val treffRepository = RekrutteringstreffRepository(db.dataSource)
        val service = EierService(repository, treffRepository, db.dataSource)
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val kall = listOf("A123456", "B654321").map { navIdent ->
                pool.submit<Boolean> {
                    check(start.await(10, TimeUnit.SECONDS))
                    try {
                        service.slettEier(treffId, navIdent, "A123456")
                        true
                    } catch (_: BadRequestResponse) {
                        false
                    }
                }
            }
            start.countDown()
            assertThat(kall.map { it.get(15, TimeUnit.SECONDS) }).containsExactlyInAnyOrder(true, false)
        } finally {
            pool.shutdownNow()
        }

        val gjenværende = repository.hent(treffId)!!.tilNavIdenter()
        assertThat(gjenværende).hasSize(1)
        assertThat(db.hentEierrader(treffId).map { it.navIdent }).containsExactlyElementsOf(gjenværende)
        assertThat(treffRepository.hentAlleHendelser(treffId).filter { it.hendelsestype == "EIER_FJERNET" }).hasSize(1)
    }
}
