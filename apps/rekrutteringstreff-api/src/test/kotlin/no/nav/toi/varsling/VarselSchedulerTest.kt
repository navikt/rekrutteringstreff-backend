package no.nav.toi.varsling

import no.nav.toi.*
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.TestDatabase
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VarselSchedulerTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var varselRepository: VarselRepository
        private val mapper = JacksonConfig.mapper
    }

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        varselRepository = VarselRepository(db.dataSource)
    }

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    // ==================== INVITERT TESTER ====================

    @Test
    fun `skal sende kandidat-invitert hendelse på rapid og markere som sendt`() {
        val rapid = TestRapid()
        val scheduler = VarselScheduler(db.dataSource, rapid)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        val fødselsnummer = Fødselsnummer("12345678901")
        jobbsøkerRepository.leggTil(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    kandidatnummer = Kandidatnummer("ABC123"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        jobbsøkerRepository.inviter(listOf(personTreffId), treffId, "Z123456")

        scheduler.behandleVarselHendelser()
        
        assertThat(rapid.inspektør.size).isEqualTo(1)
        val melding = rapid.inspektør.message(0)
        assertThat(melding["@event_name"].asText()).isEqualTo("kandidat.invitert")
        assertThat(melding["fnr"].asText()).isEqualTo(fødselsnummer.asString)
        assertThat(melding["avsenderNavident"].asText()).isEqualTo("Z123456")
        assertThat(melding["varselId"].asText()).isNotEmpty()

        val usendteEtterpå = varselRepository.hentUsendteVarselHendelser()
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal ikke sende samme invitasjon to ganger`() {
        val rapid = TestRapid()
        val scheduler = VarselScheduler(db.dataSource, rapid)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fødselsnummer = Fødselsnummer("12345678901")
        
        jobbsøkerRepository.leggTil(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    kandidatnummer = Kandidatnummer("ABC123"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        jobbsøkerRepository.inviter(listOf(personTreffId), treffId, "Z123456")

        scheduler.behandleVarselHendelser()
        scheduler.behandleVarselHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(1)
    }

    @Test
    fun `skal sende invitasjoner for flere kandidater`() {
        val rapid = TestRapid()
        val scheduler = VarselScheduler(db.dataSource, rapid)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        val fnr1 = Fødselsnummer("12345678901")
        val fnr2 = Fødselsnummer("98765432109")
        
        jobbsøkerRepository.leggTil(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fnr1,
                    kandidatnummer = Kandidatnummer("ABC123"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                ),
                LeggTilJobbsøker(
                    fødselsnummer = fnr2,
                    kandidatnummer = Kandidatnummer("DEF456"),
                    fornavn = Fornavn("Kari"),
                    etternavn = Etternavn("Normann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Per Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z789012"),
                )
            ), treffId, "Z123456"
        )
        
        val personTreffId1 = jobbsøkerRepository.hentJobbsøker(treffId, fnr1)!!.personTreffId
        val personTreffId2 = jobbsøkerRepository.hentJobbsøker(treffId, fnr2)!!.personTreffId
        jobbsøkerRepository.inviter(listOf(personTreffId1, personTreffId2), treffId, "Z123456")

        scheduler.behandleVarselHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)
        
        val melding1 = rapid.inspektør.message(0)
        assertThat(melding1["@event_name"].asText()).isEqualTo("kandidat.invitert")
        assertThat(melding1["fnr"].asText()).isEqualTo(fnr1.asString)
        
        val melding2 = rapid.inspektør.message(1)
        assertThat(melding2["@event_name"].asText()).isEqualTo("kandidat.invitert")
        assertThat(melding2["fnr"].asText()).isEqualTo(fnr2.asString)

        val usendteEtterpå = varselRepository.hentUsendteVarselHendelser()
        assertThat(usendteEtterpå).isEmpty()
    }

    // ==================== TREFF ENDRET TESTER ====================

    @Test
    fun `skal sende invitert-kandidat-endret hendelse på rapid og markere som sendt`() {
        val rapid = TestRapid()
        val scheduler = VarselScheduler(db.dataSource, rapid)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        val fødselsnummer = Fødselsnummer("12345678901")
        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        
        // Send invitasjon først
        scheduler.behandleVarselHendelser()
        
        // Registrer treff endret notifikasjon
        db.registrerTreffEndretNotifikasjon(
            treffId, 
            fødselsnummer, 
            no.nav.toi.rekrutteringstreff.dto.EndringerDto(
                tittel = no.nav.toi.rekrutteringstreff.dto.Endringsfelt("Gammel", "Ny")
            )
        )

        scheduler.behandleVarselHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon + 1 endret
        val melding = rapid.inspektør.message(1)
        assertThat(melding["@event_name"].asText()).isEqualTo("invitert.kandidat.endret")
        assertThat(melding["fnr"].asText()).isEqualTo(fødselsnummer.asString)
        assertThat(melding["avsenderNavident"].asText()).isEqualTo("Z123456")
        assertThat(melding["varselId"].asText()).isNotEmpty()

        val usendteEtterpå = varselRepository.hentUsendteVarselHendelser()
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal ikke sende samme treff-endret hendelse to ganger`() {
        val rapid = TestRapid()
        val scheduler = VarselScheduler(db.dataSource, rapid)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        val fødselsnummer = Fødselsnummer("12345678901")
        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        
        scheduler.behandleVarselHendelser()
        
        db.registrerTreffEndretNotifikasjon(
            treffId, 
            fødselsnummer, 
            no.nav.toi.rekrutteringstreff.dto.EndringerDto(
                tittel = no.nav.toi.rekrutteringstreff.dto.Endringsfelt("Gammel", "Ny")
            )
        )

        scheduler.behandleVarselHendelser()
        scheduler.behandleVarselHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)  // 1 invitasjon + 1 endret (ikke duplikater)
    }

    @Test
    fun `skal sende både invitasjon og treff-endret for samme person`() {
        val rapid = TestRapid()
        val scheduler = VarselScheduler(db.dataSource, rapid)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        val fødselsnummer = Fødselsnummer("12345678901")
        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        
        db.registrerTreffEndretNotifikasjon(
            treffId, 
            fødselsnummer, 
            no.nav.toi.rekrutteringstreff.dto.EndringerDto(
                tittel = no.nav.toi.rekrutteringstreff.dto.Endringsfelt("Gammel", "Ny")
            )
        )

        // Behandle begge hendelsene samtidig
        scheduler.behandleVarselHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(2)
        
        val invitasjonMelding = rapid.inspektør.message(0)
        assertThat(invitasjonMelding["@event_name"].asText()).isEqualTo("kandidat.invitert")
        
        val endretMelding = rapid.inspektør.message(1)
        assertThat(endretMelding["@event_name"].asText()).isEqualTo("invitert.kandidat.endret")

        val usendteEtterpå = varselRepository.hentUsendteVarselHendelser()
        assertThat(usendteEtterpå).isEmpty()
    }

    @Test
    fun `skal håndtere manglende aktøridentifikasjon ved å sende UKJENT`() {
        val rapid = TestRapid()
        val scheduler = VarselScheduler(db.dataSource, rapid)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        val fødselsnummer = Fødselsnummer("12345678901")
        
        // Legg til jobbsøker uten å invitere (manuell hendelse)
        jobbsøkerRepository.leggTil(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    kandidatnummer = Kandidatnummer("ABC123"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        
        // Opprett hendelse manuelt uten aktøridentifikasjon
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        db.dataSource.connection.use { conn ->
            // Hent jobbsoker_id for å kunne opprette hendelse
            val jobbsokerId = conn.prepareStatement(
                "SELECT jobbsoker_id FROM jobbsoker WHERE id = ?::uuid"
            ).use { stmt ->
                stmt.setString(1, personTreffId.toString())
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getLong("jobbsoker_id") else throw IllegalStateException("Fant ikke jobbsøker")
                }
            }
            
            conn.prepareStatement(
                """
                INSERT INTO jobbsoker_hendelse 
                (id, jobbsoker_id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon) 
                VALUES (gen_random_uuid(), ?, NOW(), ?, 'SYSTEM', NULL)
                """
            ).use { stmt ->
                stmt.setLong(1, jobbsokerId)
                stmt.setString(2, JobbsøkerHendelsestype.INVITERT.name)
                stmt.executeUpdate()
            }
        }

        scheduler.behandleVarselHendelser()

        assertThat(rapid.inspektør.size).isEqualTo(1)
        val melding = rapid.inspektør.message(0)
        assertThat(melding["@event_name"].asText()).isEqualTo("kandidat.invitert")
        assertThat(melding["avsenderNavident"].asText()).isEqualTo("UKJENT")
    }

    @Test
    fun `skal ikke behandle hendelser som ikke er INVITERT eller TREFF_ENDRET`() {
        val rapid = TestRapid()
        val scheduler = VarselScheduler(db.dataSource, rapid)
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()

        val fødselsnummer = Fødselsnummer("12345678901")
        opprettOgInviterJobbsøker(treffId, fødselsnummer)
        
        // Svar på invitasjonen (skal ikke sendes til varsling)
        jobbsøkerRepository.svarJaTilInvitasjon(fødselsnummer, treffId, fødselsnummer.asString)

        scheduler.behandleVarselHendelser()

        // Kun invitasjonen skal sendes, ikke svaret
        assertThat(rapid.inspektør.size).isEqualTo(1)
        val melding = rapid.inspektør.message(0)
        assertThat(melding["@event_name"].asText()).isEqualTo("kandidat.invitert")
    }

    // ==================== HJELPEMETODER ====================

    private fun opprettOgInviterJobbsøker(treffId: no.nav.toi.rekrutteringstreff.TreffId, fødselsnummer: Fødselsnummer) {
        jobbsøkerRepository.leggTil(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fødselsnummer,
                    kandidatnummer = Kandidatnummer("ABC123"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent("Z123456"),
                )
            ), treffId, "Z123456"
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fødselsnummer)!!.personTreffId
        jobbsøkerRepository.inviter(listOf(personTreffId), treffId, "Z123456")
    }
}
