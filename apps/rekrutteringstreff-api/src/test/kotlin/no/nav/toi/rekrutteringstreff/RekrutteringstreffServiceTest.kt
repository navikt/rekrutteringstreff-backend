package no.nav.toi.rekrutteringstreff

import no.nav.toi.JacksonConfig
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.Arbeidsgiver
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverStatus
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.exception.UlovligOppdateringException
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.Navkontor
import no.nav.toi.jobbsoker.VeilederNavIdent
import no.nav.toi.jobbsoker.VeilederNavn
import no.nav.toi.nowOslo
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import java.util.UUID

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffServiceTest {
    companion object {
        private val db = TestDatabase()
        private val mapper = JacksonConfig.mapper
        private lateinit var rekrutteringstreffRepository: RekrutteringstreffRepository
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private lateinit var arbeidsgiverRepository: ArbeidsgiverRepository
        private lateinit var jobbsøkerService: JobbsøkerService
        private lateinit var rekrutteringstreffService: RekrutteringstreffService

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()

            rekrutteringstreffRepository = RekrutteringstreffRepository(db.dataSource)
            jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
            arbeidsgiverRepository = ArbeidsgiverRepository(db.dataSource, mapper)
            jobbsøkerService = JobbsøkerService(db.dataSource, jobbsøkerRepository)
            rekrutteringstreffService = RekrutteringstreffService(
                db.dataSource,
                rekrutteringstreffRepository,
                jobbsøkerRepository,
                arbeidsgiverRepository,
                jobbsøkerService
            )
        }
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
    fun `Skal kunne hente alle rekrutteringstreff`() {
        val rekrutteringstreff1 = OpprettRekrutteringstreffInternalDto(
            tittel = "Treff 1",
            opprettetAvPersonNavident = "NAV1234",
            opprettetAvNavkontorEnhetId = "0605",
            opprettetAvTidspunkt = nowOslo(),
        )
        val rekrutteringstreff2 = OpprettRekrutteringstreffInternalDto(
            tittel = "Treff 2",
            opprettetAvPersonNavident = "NAV1234",
            opprettetAvNavkontorEnhetId = "0605",
            opprettetAvTidspunkt = nowOslo(),
        )
        val rekrutteringstreff3 = OpprettRekrutteringstreffInternalDto(
            tittel = "Treff 3",
            opprettetAvPersonNavident = "NAV1234",
            opprettetAvNavkontorEnhetId = "0605",
            opprettetAvTidspunkt = nowOslo(),
        )
        val treffId1 = rekrutteringstreffService.opprett(rekrutteringstreff1)
        val treffId2 = rekrutteringstreffService.opprett(rekrutteringstreff2)
        val treffId3 = rekrutteringstreffService.opprett(rekrutteringstreff3)

        val rekrutteringstreff = rekrutteringstreffService.hentAlleRekrutteringstreff()

        assertThat(rekrutteringstreff.any { it.id == treffId1.somUuid }).isTrue
        assertThat(rekrutteringstreff.any { it.id == treffId2.somUuid }).isTrue
        assertThat(rekrutteringstreff.any { it.id == treffId3.somUuid }).isTrue
    }

    @Test
    fun `Skal kunne hente et rekrutteringstreff`() {
        val treffId1 = opprettTreff()
        val rekrutteringstreff = rekrutteringstreffService.hentRekrutteringstreff(treffId1)

        assertThat(rekrutteringstreff.id == treffId1.somUuid).isTrue
    }

    @Test
    fun `Skal kunne publisere et treff`() {
        val treffId = opprettTreff()
        rekrutteringstreffService.publiser(treffId, "NAV1234")

        val rekrutteringstreff = rekrutteringstreffService.hentRekrutteringstreffMedHendelser(treffId)
        assertThat(!rekrutteringstreff.hendelser.isEmpty())
        assertThat(rekrutteringstreff.hendelser.any { it.hendelsestype == RekrutteringstreffHendelsestype.PUBLISERT.name }).isTrue
        assertThat(rekrutteringstreff.rekrutteringstreff.status).isEqualTo(RekrutteringstreffStatus.PUBLISERT)
    }

    @Test
    fun `Skal kunne hente et rekrutteringstreff med hendelser`() {
        val treffId1 = opprettTreff()

        rekrutteringstreffService.publiser(treffId1, "NAV1234")

        val rekrutteringstreff = rekrutteringstreffService.hentRekrutteringstreffMedHendelser(treffId1)

        assertThat(!rekrutteringstreff.hendelser.isEmpty())
        assertThat(rekrutteringstreff.hendelser.any { it.hendelsestype == RekrutteringstreffHendelsestype.PUBLISERT.name }).isTrue
    }

    @Test
    fun `Skal kunne avlyse et rekrutteringstreff`() {
        val treffId1 = opprettTreff()
        val rekrutteringstreff = rekrutteringstreffService.hentRekrutteringstreff(treffId1)

        assertThat(rekrutteringstreff.status == RekrutteringstreffStatus.UTKAST).isTrue

        rekrutteringstreffService.avlys(treffId1, "NAV1234")

        val rekrutteringstreffEtterAvlys = rekrutteringstreffService.hentRekrutteringstreff(treffId1)

        assertThat(rekrutteringstreffEtterAvlys.status == RekrutteringstreffStatus.AVLYST).isTrue
    }

    @Test
    fun `Skal kunne fullføre et rekrutteringstreff`() {
        val treffId = opprettTreff()
        val rekrutteringstreff = rekrutteringstreffService.hentRekrutteringstreff(treffId)
        assertThat(rekrutteringstreff.status == RekrutteringstreffStatus.UTKAST).isTrue

        db.endreTilTidTilPassert(treffId, "NAV1234")

        rekrutteringstreffService.publiser(treffId, "NAV1234")
        rekrutteringstreffService.fullfør(treffId, "NAV1234")

        val rekrutteringstreffEtterFullfør = rekrutteringstreffService.hentRekrutteringstreff(treffId)

        assertThat(rekrutteringstreffEtterFullfør.status == RekrutteringstreffStatus.FULLFØRT).isTrue
    }

    @Test
    fun `Skal ikke kunne fullføre et treff hvor tilTid ikke er passert`() {
        val treffId = opprettTreff()
        val treff = rekrutteringstreffService.hentRekrutteringstreff(treffId)
        rekrutteringstreffService.publiser(treffId, "NAV1234")
        rekrutteringstreffService.oppdater(
            treffId = treffId,
            dto = OppdaterRekrutteringstreffDto.opprettFra(treff).copy(tilTid = nowOslo().plusDays(1)),
            navIdent = "NAV1234"
        )
        assertThrows<UlovligOppdateringException> {
            rekrutteringstreffService.fullfør(treffId, "NAV1234")
        }
    }

    @Test
    fun `skal committe transaksjon når avlys fullføres uten feil`() {
        // Arrange
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fnr = Fødselsnummer("12345678901")
        val navIdent = "Z123456"

        leggTilOgInviterJobbsøker(treffId, fnr, navIdent)
        jobbsøkerService.svarJaTilInvitasjon(fnr, treffId, navIdent)

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
        jobbsøkerService.svarJaTilInvitasjon(fnr, treffId, navIdent)

        // Act
        db.endreTilTidTilPassert(treffId, navIdent)
        rekrutteringstreffService.publiser(treffId, navIdent)
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
        jobbsøkerService.svarJaTilInvitasjon(fnr, treffId, navIdent)

        // Publiser treffet først
        publiserTreff(treffId, navIdent)

        val endringer = """{"tittel": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Ny tittel"}}"""

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
        jobbsøkerService.svarJaTilInvitasjon(fnr, treffId, navIdent)

        // Publiser treffet først
        publiserTreff(treffId, navIdent)

        val endringer =
            """{"navn": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Ny tittel"}, "sted": {"gammelVerdi": "Gammel sted", "nyVerdi": "Ny sted"}}"""

        // Act
        rekrutteringstreffService.registrerEndring(treffId, endringer, navIdent)

        // Assert - verifiser at hendelse_data er lagret og kan deserialiseres
        val hendelseData = hentRekrutteringstreffHendelseData(
            treffId,
            RekrutteringstreffHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING
        )
        assertThat(hendelseData).isNotNull()

        // Deserialiser Rekrutteringstreffendringer
        val deserializedDto = mapper.readValue(hendelseData, Rekrutteringstreffendringer::class.java)
        assertThat(deserializedDto.navn).isNotNull()
        assertThat(deserializedDto.navn!!.gammelVerdi).isEqualTo("Gammel tittel")
        assertThat(deserializedDto.navn!!.nyVerdi).isEqualTo("Ny tittel")
        assertThat(deserializedDto.tidspunkt).isNull() // Ikke endret

        val jobbsøkerHendelseData = hentJobbsøkerHendelseData(
            treffId,
            fnr,
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        )
        assertThat(jobbsøkerHendelseData).isNotNull()

        val deserializedJobbsøker = mapper.readValue(jobbsøkerHendelseData, Rekrutteringstreffendringer::class.java)
        assertThat(deserializedJobbsøker.navn!!.gammelVerdi).isEqualTo("Gammel tittel")
        assertThat(deserializedJobbsøker.navn!!.nyVerdi).isEqualTo("Ny tittel")
    }

    @Test
    fun `skal kunne deserialisere JSON med kun noen felt`() {
        // Test at vi kan håndtere JSON fra databasen hvor kun noen felt er satt
        // Dette sikrer bakoverkompatibilitet

        val jsonMedNoenFelt = """{"navn": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Ny tittel"}}"""

        // Act
        val deserialized = mapper.readValue(jsonMedNoenFelt, Rekrutteringstreffendringer::class.java)

        // Assert - verifiser at eksisterende felt fungerer
        assertThat(deserialized.navn).isNotNull()
        assertThat(deserialized.navn!!.gammelVerdi).isEqualTo("Gammel tittel")
        assertThat(deserialized.navn!!.nyVerdi).isEqualTo("Ny tittel")

        // Verifiser at manglende felt er null
        assertThat(deserialized.tidspunkt).isNull()
        assertThat(deserialized.introduksjon).isNull()
    }

    @Test
    fun `skal ignorere ukjente felt i JSON fra databasen`() {
        // Test at vi kan ignorere felt som ikke lenger eksisterer i DTOen
        // Dette sikrer bakoverkompatibilitet hvis vi fjerner felt i framtiden

        val jsonMedEkstraFelt = """{
            "navn": {"gammelVerdi": "Test", "nyVerdi": "Ny Test"},
            "ukjentFelt": {"gammelVerdi": "Dette skal ignoreres", "nyVerdi": "Også ignoreres"}
        }"""

        // Act - deserialiserer JSON med ukjent felt (skal ikke kaste exception)
        val deserialized = mapper.readValue(jsonMedEkstraFelt, Rekrutteringstreffendringer::class.java)

        // Assert - verifiser at kjente felt fungerer
        assertThat(deserialized.navn).isNotNull()
        assertThat(deserialized.navn!!.gammelVerdi).isEqualTo("Test")
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

        publiserTreff(treffId, navIdent)

        val endringer = """{"tittel": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Endret tittel"}}"""

        // Act
        rekrutteringstreffService.registrerEndring(treffId, endringer, navIdent)

        // Assert - jobbsøker med INVITERT skal varsles
        val jobbsøkerHendelser = hentJobbsøkerHendelser(treffId, fnr)
        assertThat(jobbsøkerHendelser).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
    }

    @Test
    fun `skal varsle jobbsøker med SVART_JA_TREFF_AVLYST som siste hendelse om endring`() {
        // Arrange
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val fnr = Fødselsnummer("12345678901")
        val navIdent = "Z123456"

        leggTilOgInviterJobbsøker(treffId, fnr, navIdent)
        jobbsøkerService.svarJaTilInvitasjon(fnr, treffId, navIdent)

        // Publiser før avlysning
        publiserTreff(treffId, navIdent)
        rekrutteringstreffService.avlys(treffId, navIdent)

        // Nå har jobbsøker SVART_JA_TREFF_AVLYST som siste hendelse
        val endringer = """{"tittel": {"gammelVerdi": "Gammel", "nyVerdi": "Gjenåpnet og endret"}}"""

        // Act
        rekrutteringstreffService.registrerEndring(treffId, endringer, navIdent)

        // Assert - jobbsøker skal varsles fordi siste INVITASJONS-hendelse er SVART_JA_TIL_INVITASJON
        // SVART_JA_TREFF_AVLYST ignoreres da den ikke er en invitasjons/svar-hendelse
        val jobbsøkerHendelser = hentJobbsøkerHendelser(treffId, fnr)
        assertThat(jobbsøkerHendelser).contains(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
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
        jobbsøkerService.svarJaTilInvitasjon(fnr1, treffId, navIdent)

        // Jobbsøker 2: Invitert, svart ja, ombestemt seg til nei
        leggTilOgInviterJobbsøker(treffId, fnr2, navIdent)
        jobbsøkerService.svarJaTilInvitasjon(fnr2, treffId, navIdent)
        jobbsøkerService.svarNeiTilInvitasjon(fnr2, treffId, navIdent)

        // Jobbsøker 3: Kun invitert
        leggTilOgInviterJobbsøker(treffId, fnr3, navIdent)

        // Publiser treffet først
        publiserTreff(treffId, navIdent)

        val endringer = """{"tittel": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Endret tittel"}}"""

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
        jobbsøkerService.svarJaTilInvitasjon(fnr2, treffId, navIdent)

        // Jobbsøker 3: Svart nei
        leggTilOgInviterJobbsøker(treffId, fnr3, navIdent)
        jobbsøkerService.svarNeiTilInvitasjon(fnr3, treffId, navIdent)

        // Publiser treffet først
        publiserTreff(treffId, navIdent)

        val endringer = """{"tittel": {"gammelVerdi": "Gammel", "nyVerdi": "Endret for alle"}}"""

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

    @Test
    fun `skal kunne sende flere endringsnotifikasjoner til samme person`() {
        // Arrange
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val navIdent = "Z123456"
        val fnr = Fødselsnummer("11111111111")

        // Legg til og inviter jobbsøker
        leggTilOgInviterJobbsøker(treffId, fnr, navIdent)
        jobbsøkerService.svarJaTilInvitasjon(fnr, treffId, navIdent)

        // Publiser treffet først
        publiserTreff(treffId, navIdent)

        // Act - Registrer første endring
        val endring1 = """{"tittel": {"gammelVerdi": "Gammel tittel", "nyVerdi": "Endret tittel 1"}}"""
        rekrutteringstreffService.registrerEndring(treffId, endring1, navIdent)

        // Verifiser første notifikasjon
        val hendelserEtterForsteEndring = hentJobbsøkerHendelser(treffId, fnr)
        val forsteNotifikasjoner = hendelserEtterForsteEndring.filter {
            it == JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        }
        assertThat(forsteNotifikasjoner).hasSize(1)

        // Act - Registrer andre endring
        val endring2 =
            """{"beskrivelse": {"gammelVerdi": "Gammel beskrivelse", "nyVerdi": "Endret beskrivelse 2"}}"""
        rekrutteringstreffService.registrerEndring(treffId, endring2, navIdent)

        // Assert - Jobbsøker skal ha fått to notifikasjoner
        val hendelserEtterAndreEndring = hentJobbsøkerHendelser(treffId, fnr)
        val andreNotifikasjoner = hendelserEtterAndreEndring.filter {
            it == JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
        }
        assertThat(andreNotifikasjoner).hasSize(2)
    }

    @Test
    fun `skal ikke kunne slette treff som har jobbsøkere`() {
        val treffId = db.opprettRekrutteringstreffMedAlleFelter()
        val navIdent = "Z123456"
        val fnr = Fødselsnummer("11111111111")

        leggTilOgInviterJobbsøker(treffId, fnr, navIdent)

        assertThrows<UlovligOppdateringException> {
            rekrutteringstreffService.slett(treffId, navIdent)
        }
    }

    @Test
    fun `slett treff skal sette riktig status på treffet og arbeidsgivere`() {
        val treffId = db.opprettRekrutteringstreffMedAlleFelter(status = RekrutteringstreffStatus.UTKAST)

        val navIdent = "Z123456"

        db.leggTilArbeidsgivere(
            listOf(
                Arbeidsgiver(
                    arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.randomUUID()),
                    treffId = treffId,
                    orgnr = Orgnr("999888777"),
                    orgnavn = Orgnavn("Testbedrift AS"),
                    status = ArbeidsgiverStatus.AKTIV,
                    gateadresse = "Fyrstikkalleen 1",
                    postnummer = "0661",
                    poststed = "Oslo",
                )
            )
        )

        assertThat(rekrutteringstreffRepository.hent(treffId)!!.status).isEqualTo(RekrutteringstreffStatus.UTKAST)
        assertThat(arbeidsgiverRepository.hentArbeidsgivere(treffId).all { it.status == ArbeidsgiverStatus.AKTIV }).isTrue()

        rekrutteringstreffService.slett(treffId, navIdent)

        assertThat(rekrutteringstreffRepository.hent(treffId)!!.status).isEqualTo(RekrutteringstreffStatus.SLETTET)
        assertThat(arbeidsgiverRepository.hentArbeidsgivere(treffId).all { it.status == ArbeidsgiverStatus.SLETTET }).isTrue()

        // Sjekk at treffet ikke returneres i hentAlleSomIkkeErSlettet
        assertThat(rekrutteringstreffRepository.hentAlleSomIkkeErSlettet().any { it.id == treffId }).isFalse()
    }

    private fun leggTilOgInviterJobbsøker(treffId: TreffId, fnr: Fødselsnummer, navIdent: String) {
        jobbsøkerRepository.leggTilMedHendelse(
            listOf(
                LeggTilJobbsøker(
                    fødselsnummer = fnr,
                    fornavn = Fornavn("Ola"),
                    etternavn = Etternavn("Nordmann"),
                    navkontor = Navkontor("Oslo"),
                    veilederNavn = VeilederNavn("Kari Veileder"),
                    veilederNavIdent = VeilederNavIdent(navIdent),
                )
            ), treffId, navIdent
        )
        val personTreffId = jobbsøkerRepository.hentJobbsøker(treffId, fnr)!!.personTreffId
        jobbsøkerService.inviter(listOf(personTreffId), treffId, navIdent)
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

    private fun hentRekrutteringstreffHendelseData(
        treffId: TreffId,
        hendelsestype: RekrutteringstreffHendelsestype
    ): String? {
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

    private fun hentJobbsøkerHendelseData(
        treffId: TreffId,
        fnr: Fødselsnummer,
        hendelsestype: JobbsøkerHendelsestype
    ): String? {
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

    private fun opprettTreff(): TreffId {
        val rekrutteringstreff = OpprettRekrutteringstreffInternalDto(
            tittel = "Treff",
            opprettetAvPersonNavident = "NAV1234",
            opprettetAvNavkontorEnhetId = "0605",
            opprettetAvTidspunkt = nowOslo(),
        )
        return rekrutteringstreffService.opprett(rekrutteringstreff)
    }

    private fun publiserTreff(treffId: TreffId, navIdent: String) {
        db.leggTilRekrutteringstreffHendelse(
            treffId,
            RekrutteringstreffHendelsestype.PUBLISERT,
            navIdent
        )
    }
}
