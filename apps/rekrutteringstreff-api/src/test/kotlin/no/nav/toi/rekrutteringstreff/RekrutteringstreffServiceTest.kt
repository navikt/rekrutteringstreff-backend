package no.nav.toi.rekrutteringstreff

import no.nav.toi.JacksonConfig
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.dto.EndringerDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffServiceTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private lateinit var rekrutteringstreffService: RekrutteringstreffService
        private val mapper = JacksonConfig.mapper
    }

    @BeforeAll
    fun beforeAll() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
        jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
        rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
        rekrutteringstreffService = RekrutteringstreffService(
            db.dataSource,
            rekrutteringstreffRepository,
            jobbsøkerRepository
        )
    }

    @BeforeEach
    fun beforeEach() {
        db.slettAlt()
    }

    @AfterEach
    fun afterEach() {
        db.slettAlt()
    }

    @Test
    fun `skal committe transaksjon når avlys fullføres uten feil`() {
        // Arrange
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fnr = Fødselsnummer("12345678901")
        val navIdent = "Z123456"

        leggTilOgInviterJobbsøker(treffId, fnr, navIdent)
        jobbsøkerRepository.svarJaTilInvitasjon(fnr, treffId, navIdent)

        // Act
        rekrutteringstreffService.avlys(treffId, navIdent)

        // Assert - verifiser at BÅDE rekrutteringstreff-hendelse OG jobbsøker-hendelse er lagret
        val treffHendelser = hentRekrutteringstreffHendelser(treffId)
        assertThat(treffHendelser).contains(RekrutteringstreffHendelsestype.AVLYST)

        val jobbsøkerHendelser = hentJobbsøkerHendelser(treffId, fnr)
        assertThat(jobbsøkerHendelser).contains(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
    }

    @Test
    fun `skal committe transaksjon når fullfor fullføres uten feil`() {
        // Arrange
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fnr = Fødselsnummer("12345678901")
        val navIdent = "Z123456"

        leggTilOgInviterJobbsøker(treffId, fnr, navIdent)
        jobbsøkerRepository.svarJaTilInvitasjon(fnr, treffId, navIdent)

        // Act
        rekrutteringstreffService.fullfør(treffId, navIdent)

        // Assert - verifiser at BÅDE rekrutteringstreff-hendelse OG jobbsøker-hendelse er lagret
        val treffHendelser = hentRekrutteringstreffHendelser(treffId)
        assertThat(treffHendelser).contains(RekrutteringstreffHendelsestype.FULLFØRT)

        val jobbsøkerHendelser = hentJobbsøkerHendelser(treffId, fnr)
        assertThat(jobbsøkerHendelser).contains(JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT)
    }

    @Test
    fun `skal committe transaksjon når registrerEndring fullføres uten feil`() {
        // Arrange
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fnr = Fødselsnummer("12345678901")
        val navIdent = "Z123456"

        leggTilOgInviterJobbsøker(treffId, fnr, navIdent)
        jobbsøkerRepository.svarJaTilInvitasjon(fnr, treffId, navIdent)

        // Publiser treffet først
        publiserTreff(treffId, navIdent)

        val endringer = """{"tittel": {"value": "Ny tittel", "endret": true}, "beskrivelse": {"value": null, "endret": false}, "fraTid": {"value": null, "endret": false}, "tilTid": {"value": null, "endret": false}, "svarfrist": {"value": null, "endret": false}, "gateadresse": {"value": null, "endret": false}, "postnummer": {"value": null, "endret": false}, "poststed": {"value": null, "endret": false}, "htmlContent": {"value": null, "endret": false}}"""

        // Act
        rekrutteringstreffService.registrerEndring(treffId, endringer, navIdent)

        // Assert - verifiser at BÅDE rekrutteringstreff-hendelse OG jobbsøker-hendelse er lagret
        val treffHendelser = hentRekrutteringstreffHendelser(treffId)
        assertThat(treffHendelser).contains(RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING)

        val jobbsøkerHendelser = hentJobbsøkerHendelser(treffId, fnr)
        assertThat(jobbsøkerHendelser).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    @Test
    fun `skal rulle tilbake transaksjon ved feil i database-operasjon`() {
        // Arrange
        val ugyldigTreffId = TreffId("00000000-0000-0000-0000-000000000000")
        val navIdent = "Z123456"

        // Act & Assert - skal kaste exception og ikke lagre noe
        assertThatThrownBy {
            rekrutteringstreffService.avlys(ugyldigTreffId, navIdent)
        }.isInstanceOf(Exception::class.java)

        // Verifiser at ingen hendelser er lagret (rollback fungerte)
        // Siden treffet ikke eksisterer, skal vi ikke kunne finne noen hendelser
        val hendelser = hentAlleRekrutteringstreffHendelser()
        assertThat(hendelser).isEmpty()
    }

    @Test
    fun `skal lagre hendelse_data for registrerEndring`() {
        // Arrange
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fnr = Fødselsnummer("12345678901")
        val navIdent = "Z123456"

        leggTilOgInviterJobbsøker(treffId, fnr, navIdent)
        jobbsøkerRepository.svarJaTilInvitasjon(fnr, treffId, navIdent)

        // Publiser treffet først
        publiserTreff(treffId, navIdent)

        val endringer = """{"tittel": {"value": "Ny tittel", "endret": true}, "beskrivelse": {"value": "Ny beskrivelse", "endret": true}, "fraTid": {"value": null, "endret": false}, "tilTid": {"value": null, "endret": false}, "svarfrist": {"value": null, "endret": false}, "gateadresse": {"value": null, "endret": false}, "postnummer": {"value": null, "endret": false}, "poststed": {"value": null, "endret": false}, "htmlContent": {"value": null, "endret": false}}"""

        // Act
        rekrutteringstreffService.registrerEndring(treffId, endringer, navIdent)

        // Assert - verifiser at hendelse_data er lagret og kan deserialiseres
        val hendelseData = hentRekrutteringstreffHendelseData(treffId, RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING)
        assertThat(hendelseData).isNotNull()

        // Deserialiser og verifiser feltene
        val deserializedTreff = mapper.readValue(hendelseData, EndringerDto::class.java)
        assertThat(deserializedTreff.tittel.value).isEqualTo("Ny tittel")
        assertThat(deserializedTreff.tittel.endret).isTrue()
        assertThat(deserializedTreff.beskrivelse.value).isEqualTo("Ny beskrivelse")
        assertThat(deserializedTreff.beskrivelse.endret).isTrue()

        val jobbsøkerHendelseData = hentJobbsøkerHendelseData(treffId, fnr, JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
        assertThat(jobbsøkerHendelseData).isNotNull()

        val deserializedJobbsøker = mapper.readValue(jobbsøkerHendelseData, EndringerDto::class.java)
        assertThat(deserializedJobbsøker.tittel.value).isEqualTo("Ny tittel")
        assertThat(deserializedJobbsøker.tittel.endret).isTrue()
    }

    @Test
    fun `skal kunne deserialisere gammel JSON uten alle felt`() {
        // Test at EndringerDto er robust og kan håndtere gamle JSON-strukturer fra databasen
        // Dette sikrer at gamle hendelser fortsatt kan leses selv om EndringerDto får nye felt

        // Gammel JSON med kun noen felt (som om vi hadde færre felt før)
        val gammelJson = """{"tittel": {"value": "Gammel tittel", "endret": true}}"""
        
        // Act - deserialiserer gammel JSON
        val deserialized = mapper.readValue(gammelJson, EndringerDto::class.java)

        // Assert - verifiser at eksisterende felt fungerer
        assertThat(deserialized.tittel.value).isEqualTo("Gammel tittel")
        assertThat(deserialized.tittel.endret).isTrue()

        // Verifiser at manglende felt får default-verdier
        assertThat(deserialized.beskrivelse.value).isNull()
        assertThat(deserialized.beskrivelse.endret).isFalse()
        assertThat(deserialized.fraTid.endret).isFalse()
        assertThat(deserialized.htmlContent.endret).isFalse()
    }

    @Test
    fun `skal ignorere ukjente felt i JSON fra databasen`() {
        // Test at EndringerDto kan ignorere felt som ikke lenger eksisterer i DTOen
        // Dette sikrer bakoverkompatibilitet hvis vi fjerner felt i framtiden

        // JSON med et ekstra felt som ikke finnes i EndringerDto
        val jsonMedEkstraFelt = """{
            "tittel": {"value": "Test", "endret": true},
            "beskrivelse": {"value": "Test beskrivelse", "endret": true},
            "ukjentFelt": {"value": "Dette skal ignoreres", "endret": true}
        }"""

        // Act - deserialiserer JSON med ukjent felt (skal ikke kaste exception)
        val deserialized = mapper.readValue(jsonMedEkstraFelt, EndringerDto::class.java)

        // Assert - verifiser at kjente felt fungerer
        assertThat(deserialized.tittel.value).isEqualTo("Test")
        assertThat(deserialized.beskrivelse.value).isEqualTo("Test beskrivelse")
    }

    @Test
    fun `skal ikke lagre jobbsøker-hendelser når ingen jobbsøkere har svart ja`() {
        // Arrange
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fnr = Fødselsnummer("12345678901")
        val navIdent = "Z123456"

        leggTilOgInviterJobbsøker(treffId, fnr, navIdent)
        // MERK: Jobbsøkeren svarer IKKE ja

        // Act
        rekrutteringstreffService.avlys(treffId, navIdent)

        // Assert - rekrutteringstreff-hendelse skal være lagret, men IKKE jobbsøker-hendelse
        val treffHendelser = hentRekrutteringstreffHendelser(treffId)
        assertThat(treffHendelser).contains(RekrutteringstreffHendelsestype.AVLYST)

        val jobbsøkerHendelser = hentJobbsøkerHendelser(treffId, fnr)
        assertThat(jobbsøkerHendelser).doesNotContain(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
    }

    @Test
    fun `skal varsle jobbsøker med INVITERT som siste hendelse om endring`() {
        // Arrange
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fnr = Fødselsnummer("12345678901")
        val navIdent = "Z123456"

        leggTilOgInviterJobbsøker(treffId, fnr, navIdent)
        // MERK: Jobbsøker har kun INVITERT, ikke svart ja

        // Publiser treffet først
        publiserTreff(treffId, navIdent)

        val endringer = """{"tittel": {"value": "Endret tittel", "endret": true}, "beskrivelse": {"value": null, "endret": false}, "fraTid": {"value": null, "endret": false}, "tilTid": {"value": null, "endret": false}, "svarfrist": {"value": null, "endret": false}, "gateadresse": {"value": null, "endret": false}, "postnummer": {"value": null, "endret": false}, "poststed": {"value": null, "endret": false}, "htmlContent": {"value": null, "endret": false}}"""

        // Act
        rekrutteringstreffService.registrerEndring(treffId, endringer, navIdent)

        // Assert - jobbsøker med INVITERT skal varsles
        val jobbsøkerHendelser = hentJobbsøkerHendelser(treffId, fnr)
        assertThat(jobbsøkerHendelser).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    @Test
    fun `skal ikke varsle jobbsøker med SVART_JA_TREFF_AVLYST som siste hendelse om endring`() {
        // Arrange
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fnr = Fødselsnummer("12345678901")
        val navIdent = "Z123456"

        leggTilOgInviterJobbsøker(treffId, fnr, navIdent)
        jobbsøkerRepository.svarJaTilInvitasjon(fnr, treffId, navIdent)

        // Publiser før avlysning
        publiserTreff(treffId, navIdent)
        rekrutteringstreffService.avlys(treffId, navIdent)

        // Nå har jobbsøker SVART_JA_TREFF_AVLYST som siste hendelse
        val endringer = """{"tittel": {"value": "Gjenåpnet og endret", "endret": true}, "beskrivelse": {"value": null, "endret": false}, "fraTid": {"value": null, "endret": false}, "tilTid": {"value": null, "endret": false}, "svarfrist": {"value": null, "endret": false}, "gateadresse": {"value": null, "endret": false}, "postnummer": {"value": null, "endret": false}, "poststed": {"value": null, "endret": false}, "htmlContent": {"value": null, "endret": false}}"""

        // Act
        rekrutteringstreffService.registrerEndring(treffId, endringer, navIdent)

        // Assert - jobbsøker skal varsles fordi nest-siste var SVART_JA
        val jobbsøkerHendelser = hentJobbsøkerHendelser(treffId, fnr)
        assertThat(jobbsøkerHendelser).doesNotContain(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    @Test
    fun `skal ikke varsle jobbsøker som har svart nei om endring`() {
        // Arrange - Opprett tre jobbsøkere med ulike statuser
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val navIdent = "Z123456"

        val fnr1 = Fødselsnummer("11111111111") // Skal varsles: INVITERT → SVART_JA
        val fnr2 = Fødselsnummer("22222222222") // Skal IKKE varsles: INVITERT → SVART_JA → SVART_NEI
        val fnr3 = Fødselsnummer("33333333333") // Skal varsles: INVITERT

        // Jobbsøker 1: Invitert og svart ja
        leggTilOgInviterJobbsøker(treffId, fnr1, navIdent)
        jobbsøkerRepository.svarJaTilInvitasjon(fnr1, treffId, navIdent)

        // Jobbsøker 2: Invitert, svart ja, ombestemt seg til nei
        leggTilOgInviterJobbsøker(treffId, fnr2, navIdent)
        jobbsøkerRepository.svarJaTilInvitasjon(fnr2, treffId, navIdent)
        jobbsøkerRepository.svarNeiTilInvitasjon(fnr2, treffId, navIdent)

        // Jobbsøker 3: Kun invitert
        leggTilOgInviterJobbsøker(treffId, fnr3, navIdent)

        // Publiser treffet først
        publiserTreff(treffId, navIdent)

        val endringer = """{"tittel": {"value": "Endret tittel", "endret": true}, "beskrivelse": {"value": null, "endret": false}, "fraTid": {"value": null, "endret": false}, "tilTid": {"value": null, "endret": false}, "svarfrist": {"value": null, "endret": false}, "gateadresse": {"value": null, "endret": false}, "postnummer": {"value": null, "endret": false}, "poststed": {"value": null, "endret": false}, "htmlContent": {"value": null, "endret": false}}"""

        // Act
        rekrutteringstreffService.registrerEndring(treffId, endringer, navIdent)

        // Assert - verifiser at kun de med INVITERT eller SVART_JA får notifikasjon
        val hendelser1 = hentJobbsøkerHendelser(treffId, fnr1)
        assertThat(hendelser1).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)

        val hendelser2 = hentJobbsøkerHendelser(treffId, fnr2)
        assertThat(hendelser2).doesNotContain(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)

        val hendelser3 = hentJobbsøkerHendelser(treffId, fnr3)
        assertThat(hendelser3).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    @Test
    fun `skal håndtere flere jobbsøkere med ulike statuser ved endring`() {
        // Arrange
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val navIdent = "Z123456"

        val fnr1 = Fødselsnummer("11111111111")
        val fnr2 = Fødselsnummer("22222222222")
        val fnr3 = Fødselsnummer("33333333333")

        // Jobbsøker 1: Invitert
        leggTilOgInviterJobbsøker(treffId, fnr1, navIdent)

        // Jobbsøker 2: Svart ja
        leggTilOgInviterJobbsøker(treffId, fnr2, navIdent)
        jobbsøkerRepository.svarJaTilInvitasjon(fnr2, treffId, navIdent)

        // Jobbsøker 3: Svart nei
        leggTilOgInviterJobbsøker(treffId, fnr3, navIdent)
        jobbsøkerRepository.svarNeiTilInvitasjon(fnr3, treffId, navIdent)

        // Publiser treffet først
        publiserTreff(treffId, navIdent)

        val endringer = """{"tittel": {"value": "Endret for alle", "endret": true}, "beskrivelse": {"value": null, "endret": false}, "fraTid": {"value": null, "endret": false}, "tilTid": {"value": null, "endret": false}, "svarfrist": {"value": null, "endret": false}, "gateadresse": {"value": null, "endret": false}, "postnummer": {"value": null, "endret": false}, "poststed": {"value": null, "endret": false}, "htmlContent": {"value": null, "endret": false}}"""

        // Act
        rekrutteringstreffService.registrerEndring(treffId, endringer, navIdent)

        // Assert
        val hendelser1 = hentJobbsøkerHendelser(treffId, fnr1)
        assertThat(hendelser1).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)

        val hendelser2 = hentJobbsøkerHendelser(treffId, fnr2)
        assertThat(hendelser2).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)

        val hendelser3 = hentJobbsøkerHendelser(treffId, fnr3)
        assertThat(hendelser3).doesNotContain(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    // Helper methods

    private fun leggTilOgInviterJobbsøker(treffId: TreffId, fnr: Fødselsnummer, navIdent: String) {
        jobbsøkerRepository.leggTil(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fnr,
                    kandidatnummer = Kandidatnummer("ABC123"),
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent(navIdent),
                )
            ), treffId, navIdent
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fnr)!!.personTreffId
        jobbsøkerRepository.inviter(listOf(personTreffId), treffId, navIdent)
    }

    private fun hentRekrutteringstreffHendelser(treffId: TreffId): List<RekrutteringstreffHendelsestype> {
        return db.dataSource.connection.use { conn ->
            val sql = """
                SELECT hendelsestype FROM rekrutteringstreff_hendelse
                WHERE rekrutteringstreff_id = (SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?)
            """
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treffId.somUuid)
                stmt.executeQuery().use { rs ->
                    val hendelser = mutableListOf<RekrutteringstreffHendelsestype>()
                    while (rs.next()) {
                        hendelser.add(RekrutteringstreffHendelsestype.valueOf(rs.getString("hendelsestype")))
                    }
                    hendelser
                }
            }
        }
    }

    private fun hentJobbsøkerHendelser(treffId: TreffId, fnr: Fødselsnummer): List<JobbsøkerHendelsestype> {
        return db.dataSource.connection.use { conn ->
            val sql = """
                SELECT jh.hendelsestype FROM jobbsoker_hendelse jh
                JOIN jobbsoker j ON jh.jobbsoker_id = j.jobbsoker_id
                WHERE j.rekrutteringstreff_id = (SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?)
                AND j.fodselsnummer = ?
            """
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treffId.somUuid)
                stmt.setString(2, fnr.asString)
                stmt.executeQuery().use { rs ->
                    val hendelser = mutableListOf<JobbsøkerHendelsestype>()
                    while (rs.next()) {
                        hendelser.add(JobbsøkerHendelsestype.valueOf(rs.getString("hendelsestype")))
                    }
                    hendelser
                }
            }
        }
    }

    private fun hentAlleRekrutteringstreffHendelser(): List<RekrutteringstreffHendelsestype> {
        return db.dataSource.connection.use { conn ->
            val sql = "SELECT hendelsestype FROM rekrutteringstreff_hendelse"
            conn.prepareStatement(sql).use { stmt ->
                stmt.executeQuery().use { rs ->
                    val hendelser = mutableListOf<RekrutteringstreffHendelsestype>()
                    while (rs.next()) {
                        hendelser.add(RekrutteringstreffHendelsestype.valueOf(rs.getString("hendelsestype")))
                    }
                    hendelser
                }
            }
        }
    }

    private fun hentRekrutteringstreffHendelseData(treffId: TreffId, hendelsestype: RekrutteringstreffHendelsestype): String? {
        return db.dataSource.connection.use { conn ->
            val sql = """
                SELECT hendelse_data::text FROM rekrutteringstreff_hendelse
                WHERE rekrutteringstreff_id = (SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?)
                AND hendelsestype = ?
            """
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treffId.somUuid)
                stmt.setString(2, hendelsestype.name)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString(1) else null
                }
            }
        }
    }

    private fun hentJobbsøkerHendelseData(treffId: TreffId, fnr: Fødselsnummer, hendelsestype: JobbsøkerHendelsestype): String? {
        return db.dataSource.connection.use { conn ->
            val sql = """
                SELECT jh.hendelse_data::text FROM jobbsoker_hendelse jh
                JOIN jobbsoker j ON jh.jobbsoker_id = j.jobbsoker_id
                WHERE j.rekrutteringstreff_id = (SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ?)
                AND j.fodselsnummer = ?
                AND jh.hendelsestype = ?
            """
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treffId.somUuid)
                stmt.setString(2, fnr.asString)
                stmt.setString(3, hendelsestype.name)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) rs.getString(1) else null
                }
            }
        }
    }

    private fun publiserTreff(treffId: TreffId, navIdent: String) {
        db.leggTilRekrutteringstreffHendelse(
            treffId,
            RekrutteringstreffHendelsestype.PUBLISERT,
            navIdent
        )
    }
}

