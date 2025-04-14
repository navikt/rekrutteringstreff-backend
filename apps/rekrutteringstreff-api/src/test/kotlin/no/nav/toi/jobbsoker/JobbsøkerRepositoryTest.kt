package no.nav.toi.jobbsoker

import no.nav.toi.JacksonConfig
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.*
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.sql.Statement
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*

class JobbsøkerRepositoryTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var repository: JobbsøkerRepository
        private val mapper = JacksonConfig.mapper

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure().dataSource(db.dataSource).load().migrate()
            repository = JobbsøkerRepository(db.dataSource, mapper)
        }
    }

    @AfterEach
    fun slettAlt() {
        db.slettAlt()
    }

    @Test
    fun leggTilJobbsøkerTest() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val input = LeggTilJobbsøker(
            Fødselsnummer("12345678901"),
            Kandidatnummer("K123456"),
            Fornavn("Ola"),
            Etternavn("Nordmann"),
            Navkontor("NAV Oslo"),
            VeilederNavn("Kari Nordmann"),
            VeilederNavIdent("NAV123")
        )
        repository.leggTil(input, treffId, "testperson")
        val jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere).hasSize(1)
        val js = jobbsøkere.first()
        assertThat(js.fødselsnummer.asString).isEqualTo("12345678901")
        assertThat(js.kandidatnummer?.asString).isEqualTo("K123456")
        assertThat(js.fornavn.asString).isEqualTo("Ola")
        assertThat(js.etternavn.asString).isEqualTo("Nordmann")
        assertThat(js.navkontor?.asString).isEqualTo("NAV Oslo")
        assertThat(js.veilederNavn?.asString).isEqualTo("Kari Nordmann")
        assertThat(js.veilederNavIdent?.asString).isEqualTo("NAV123")
        assertThat(js.hendelser).hasSize(1)
        val h = js.hendelser.first()
        assertThatCode { UUID.fromString(h.id.toString()) }.doesNotThrowAnyException()
        assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(h.hendelsestype.toString()).isEqualTo(Hendelsestype.LEGG_TIL.toString())
        assertThat(h.opprettetAvAktørType.toString()).isEqualTo("ARRANGØR")
        assertThat(h.aktørIdentifikasjon).isEqualTo("testperson")
    }

    @Test
    fun leggTilJobbsøkerMedKunObligatoriskeFelterTest() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase(navIdent = "testperson", tittel = "TestTreff")
        val input = LeggTilJobbsøker(
            Fødselsnummer("98765432109"),
            null,
            Fornavn("Knut"),
            Etternavn("Hansen"),
            null,
            null,
            null
        )
        repository.leggTil(input, treffId, "testperson")
        val jobbsøkere = repository.hentJobbsøkere(treffId)
        assertThat(jobbsøkere).hasSize(1)
        val js = jobbsøkere.first()
        assertThat(js.fødselsnummer.asString).isEqualTo("98765432109")
        assertThat(js.kandidatnummer).isNull()
        assertThat(js.fornavn.asString).isEqualTo("Knut")
        assertThat(js.etternavn.asString).isEqualTo("Hansen")
        assertThat(js.navkontor).isNull()
        assertThat(js.veilederNavn).isNull()
        assertThat(js.veilederNavIdent).isNull()
        assertThat(js.hendelser).hasSize(1)
        val h = js.hendelser.first()
        assertThatCode { UUID.fromString(h.id.toString()) }.doesNotThrowAnyException()
        assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
        assertThat(h.hendelsestype.toString()).isEqualTo(Hendelsestype.LEGG_TIL.toString())
        assertThat(h.opprettetAvAktørType.toString()).isEqualTo("ARRANGØR")
        assertThat(h.aktørIdentifikasjon).isEqualTo("testperson")
    }

    @Test
    fun hentJobbsøkereTest() {
        val treffId1 = db.opprettRekrutteringstreffIDatabase()
        val treffId2 = db.opprettRekrutteringstreffIDatabase()
        val js1 = listOf(
            Jobbsøker(
                treffId1,
                Fødselsnummer("11111111111"),
                Kandidatnummer("K1"),
                Fornavn("Fornavn1"),
                Etternavn("Etternavn1"),
                Navkontor("Oslo"),
                VeilederNavn("Veileder1"),
                VeilederNavIdent("NAV1")
            )
        )
        val js2 = listOf(
            Jobbsøker(
                treffId2,
                Fødselsnummer("22222222222"),
                Kandidatnummer("K2"),
                Fornavn("Fornavn2"),
                Etternavn("Etternavn2"),
                Navkontor("Oslo"),
                VeilederNavn("Veileder1"),
                VeilederNavIdent("NAV1")
            ),
            Jobbsøker(
                treffId2,
                Fødselsnummer("33333333333"),
                Kandidatnummer("K3"),
                Fornavn("Fornavn3"),
                Etternavn("Etternavn3"),
                Navkontor("Bergen"),
                VeilederNavn("Veileder2"),
                VeilederNavIdent("NAV2")
            )
        )
        db.leggTilJobbsøkere(js1)
        db.leggTilJobbsøkere(js2)
        val hentet = repository.hentJobbsøkere(treffId2)
        assertThat(hentet).hasSize(2)
        hentet.forEach { js ->
            assertThat(js.hendelser).hasSize(1)
            val h = js.hendelser.first()
            assertThatCode { UUID.fromString(h.id.toString()) }.doesNotThrowAnyException()
            assertThat(h.tidspunkt.toInstant()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS))
            assertThat(h.hendelsestype.toString()).isEqualTo(Hendelsestype.LEGG_TIL.toString())
            assertThat(h.opprettetAvAktørType.toString()).isEqualTo("ARRANGØR")
            assertThat(h.aktørIdentifikasjon).isEqualTo("testperson")
        }
    }


    @Test
    fun hentJobbsøkerUtenHendelserTest() {
        val treffId: TreffId = db.opprettRekrutteringstreffIDatabase()
        val id = leggTilDirekteJobbsøkerUtenHendelse(
            treffId, LeggTilJobbsøker(
                Fødselsnummer("55555555555"),
                Kandidatnummer("K999999"),
                Fornavn("Per"),
                Etternavn("Olsen"),
                Navkontor("Trondheim"),
                VeilederNavn("Navn"),
                VeilederNavIdent("NAV999")
            )
        )
        val jobbsøkere = repository.hentJobbsøkere(treffId)
        val js = jobbsøkere.find { it.fødselsnummer.asString == "55555555555" }
        assertThat(js).isNotNull
        assertThat(js!!.hendelser).isEmpty()
    }

    private fun leggTilDirekteJobbsøkerUtenHendelse(treff: TreffId, input: LeggTilJobbsøker): Long {
        db.dataSource.connection.use { connection ->
            val treffDbId =
                connection.prepareStatement("SELECT db_id FROM rekrutteringstreff WHERE id = ?").use { stmt ->
                    stmt.setObject(1, treff.somUuid)
                    stmt.executeQuery().use { rs ->
                        if (rs.next()) rs.getLong("db_id") else throw IllegalArgumentException("Treff ikke funnet")
                    }
                }
            return connection.prepareStatement(
                "INSERT INTO jobbsoker (treff_db_id, fodselsnummer, kandidatnummer, fornavn, etternavn, navkontor, veileder_navn, veileder_navident) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS
            ).use { stmt ->
                stmt.setLong(1, treffDbId)
                stmt.setString(2, input.fødselsnummer.asString)
                stmt.setString(3, input.kandidatnummer?.asString)
                stmt.setString(4, input.fornavn.asString)
                stmt.setString(5, input.etternavn.asString)
                stmt.setString(6, input.navkontor?.asString)
                stmt.setString(7, input.veilederNavn?.asString)
                stmt.setString(8, input.veilederNavIdent?.asString)
                stmt.executeUpdate()
                stmt.generatedKeys.use { keys ->
                    if (keys.next()) keys.getLong(1) else throw IllegalStateException("Ingen nøkkel returnert")
                }
            }
        }
    }
}
