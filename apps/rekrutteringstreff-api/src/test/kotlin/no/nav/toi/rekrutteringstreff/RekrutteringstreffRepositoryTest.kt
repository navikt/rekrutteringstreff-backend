package no.nav.toi.rekrutteringstreff
import no.nav.toi.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
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
        val id = db.opprettRekrutteringstreffIDatabase(navIdent = "A1", tittel = "Initielt")

        val opprett = repository.hentHendelser(id)
        assertThat(opprett).hasSize(1)
        assertThat(opprett.first().hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPRETTET)

        db.oppdaterRekrutteringstreff(id, tittel = "Ny tittel")
        db.oppdaterRekrutteringstreff(
            id,
            tittel = "Ny tittel",
            fraTid = nowOslo(),
            tilTid = nowOslo().plusHours(1),
            gateadresse = "Karl Johans gate 1",
            postnummer = "0154",
            poststed = "Oslo"
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
        val id = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "Test-treff")

        // Legg til ulike hendelsestyper via executeInTransaction
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
        val id = db.opprettRekrutteringstreffIDatabase(navIdent = "A1", tittel = "Initielt")

        val initieltTreff = repository.hent(id)
        assertThat(initieltTreff?.status).isEqualTo(RekrutteringstreffStatus.UTKAST)

        repository.endreStatus(id, RekrutteringstreffStatus.PUBLISERT)

        val oppdatertTreff = repository.hent(id)
        assertThat(oppdatertTreff?.status).isEqualTo(RekrutteringstreffStatus.PUBLISERT)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Synlighets-tester
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `hentAlleHendelser skal filtrere ut jobbsøker-hendelser for ikke-synlige jobbsøkere`() {
        val navIdent = "testperson"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")

        // Legg til to jobbsøkere
        val synligJobbsøker = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Synlig"), Etternavn("Person"), null, null, null)
        val ikkeSynligJobbsøker = LeggTilJobbsøker(Fødselsnummer("22222222222"), Fornavn("IkkeSynlig"), Etternavn("Person"), null, null, null)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(synligJobbsøker, ikkeSynligJobbsøker), treffId, navIdent)

        // Hent alle hendelser - skal inkludere rekrutteringstreff OPPRETTET + 2 jobbsøker OPPRETTET
        val alleHendelser = repository.hentAlleHendelser(treffId)
        val jobbsøkerHendelser = alleHendelser.filter { it.ressurs == HendelseRessurs.JOBBSØKER }
        assertThat(jobbsøkerHendelser).hasSize(2)

        // Sett én jobbsøker som ikke-synlig
        db.settSynlighet(personTreffIder[1], false)

        // Hent igjen - skal nå bare inkludere hendelse for synlig jobbsøker
        val filtrerteHendelser = repository.hentAlleHendelser(treffId)
        val filtrerteJobbsøkerHendelser = filtrerteHendelser.filter { it.ressurs == HendelseRessurs.JOBBSØKER }
        assertThat(filtrerteJobbsøkerHendelser).hasSize(1)
        assertThat(filtrerteJobbsøkerHendelser.first().subjektNavn).isEqualTo("Synlig Person")
    }

    @Test
    fun `hentAlleHendelser skal fortsatt inkludere rekrutteringstreff- og arbeidsgiver-hendelser`() {
        val navIdent = "testperson"
        val treffId = db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, tittel = "TestTreff")

        // Legg til en jobbsøker og sett som ikke-synlig
        val jobbsøker = LeggTilJobbsøker(Fødselsnummer("11111111111"), Fornavn("Test"), Etternavn("Person"), null, null, null)
        val personTreffIder = db.leggTilJobbsøkereMedHendelse(listOf(jobbsøker), treffId, navIdent)
        db.settSynlighet(personTreffIder[0], false)

        // Hent alle hendelser - skal fortsatt inkludere rekrutteringstreff OPPRETTET
        val alleHendelser = repository.hentAlleHendelser(treffId)
        val treffHendelser = alleHendelser.filter { it.ressurs == HendelseRessurs.REKRUTTERINGSTREFF }
        val jobbsøkerHendelser = alleHendelser.filter { it.ressurs == HendelseRessurs.JOBBSØKER }

        assertThat(treffHendelser).hasSize(1)
        assertThat(treffHendelser.first().hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPRETTET.name)
        assertThat(jobbsøkerHendelser).isEmpty() // Ikke-synlig jobbsøker filtreres ut
    }
}
