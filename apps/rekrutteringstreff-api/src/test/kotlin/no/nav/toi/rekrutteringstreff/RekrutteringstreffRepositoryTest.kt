package no.nav.toi.rekrutteringstreff

import no.nav.toi.*
import no.nav.toi.arbeidsgiver.Arbeidsgiver
import no.nav.toi.arbeidsgiver.ArbeidsgiverStatus
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.*
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.innlegg.InnleggRepository
import no.nav.toi.rekrutteringstreff.innlegg.OpprettInnleggRequestDto
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.sql.SQLException
import java.time.temporal.ChronoUnit
import java.util.*

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffRepositoryTest {

    companion object {
        private val db = TestDatabase()
        private lateinit var repository: RekrutteringstreffRepository
        private lateinit var jobbsøkerRepository: JobbsøkerRepository
        private val mapper = JacksonConfig.mapper

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()

            jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)
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
        assertThat(opprett.first().hendelsestype).isEqualTo(RekrutteringstreffHendelsestype.OPPRETTET)

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
                poststed = null,
                kommune = null,
                kommunenummer = null,
                fylke = null,
                fylkesnummer = null,
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
                poststed ="Oslo",
                kommune = "Oslo",
                kommunenummer = "0301",
                fylke = "Oslo",
                fylkesnummer = "03",
            ),
            oppdatertAv = "A1"
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
        val id = repository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Test-treff",
                opprettetAvPersonNavident = navIdent,
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )

        // Legg til ulike hendelsestyper
        repository.gjenåpne(id, navIdent)
        repository.avpubliser(id, navIdent)

        val hendelser = repository.hentHendelser(id)
        assertThat(hendelser).hasSize(3)
        assertThat(hendelser.map { it.hendelsestype }).containsExactly(
            RekrutteringstreffHendelsestype.AVPUBLISERT,
            RekrutteringstreffHendelsestype.GJENÅPNET,
            RekrutteringstreffHendelsestype.OPPRETTET
        )
    }

    @Test
    fun `slett feiler når jobbsoker finnes (før publisering)`() {
        val navIdent = "A123456"
        val treffId = repository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Treff med jobbsøker",
                opprettetAvPersonNavident = navIdent,
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )

        // Legg til arbeidsgiver (som ville blitt slettet)
        val arbeidsgiver = Arbeidsgiver(
            arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.randomUUID()),
            treffId = treffId,
            orgnr = Orgnr("888888888"),
            orgnavn = Orgnavn("Test AS"),
            status = ArbeidsgiverStatus.AKTIV,
        )
        db.leggTilArbeidsgivere(listOf(arbeidsgiver))

        // Legg til innlegg (som ville blitt slettet)
        val innleggRepo = InnleggRepository(db.dataSource)
        val innlegg = innleggRepo.opprett(
            treffId = treffId,
            dto = OpprettInnleggRequestDto(
                tittel = "Test innlegg",
                opprettetAvPersonNavn = "Test Testesen",
                opprettetAvPersonBeskrivelse = "Veileder",
                sendesTilJobbsokerTidspunkt = null,
                htmlContent = "<p>Test</p>"
            ),
            navIdent = navIdent
        )

        // Legg til en jobbsøker (blokkerende)
        val jobbsøker = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678901"),
            kandidatnummer = Kandidatnummer("PAM123"),
            fornavn = Fornavn("Test"),
            etternavn = Etternavn("Testesen"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder Veiledersen"),
            veilederNavIdent = VeilederNavIdent("V123456"),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        db.leggTilJobbsøkere(listOf(jobbsøker))

        // Forsøk å slette skal feile pga FK constraint på jobbsoker
        assertThatThrownBy { repository.slett(treffId) }
            .isInstanceOf(SQLException::class.java)

        // Verifiser at INGEN data ble slettet (god transaksjonshåndtering)
        assertThat(repository.hent(treffId)).isNotNull
        assertThat(db.hentAlleArbeidsgivere()).hasSize(1)
        assertThat(db.hentArbeidsgiverHendelser(treffId)).isNotEmpty
        assertThat(innleggRepo.hentById(innlegg.id)).isNotNull
        assertThat(db.hentHendelser(treffId)).isNotEmpty
        assertThat(db.hentJobbsøkereForTreff(treffId)).hasSize(1)
    }

    @Test
    fun `slett feiler når jobbsoker_hendelse finnes (før publisering)`() {
        val navIdent = "A123456"
        val treffId = repository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Treff med jobbsøkerhendelse",
                opprettetAvPersonNavident = navIdent,
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )

        // Legg til arbeidsgiver (som ville blitt slettet)
        val arbeidsgiver = Arbeidsgiver(
            arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.randomUUID()),
            treffId = treffId,
            orgnr = Orgnr("777777777"),
            orgnavn = Orgnavn("Test2 AS"),
            status = ArbeidsgiverStatus.AKTIV,
        )
        db.leggTilArbeidsgivere(listOf(arbeidsgiver))

        // Legg til innlegg (som ville blitt slettet)
        val innleggRepo = InnleggRepository(db.dataSource)
        val innlegg = innleggRepo.opprett(
            treffId = treffId,
            dto = OpprettInnleggRequestDto(
                tittel = "Test innlegg 2",
                opprettetAvPersonNavn = "Test Testesen",
                opprettetAvPersonBeskrivelse = "Veileder",
                sendesTilJobbsokerTidspunkt = null,
                htmlContent = "<p>Test2</p>"
            ),
            navIdent = navIdent
        )

        // Legg til en jobbsøker (dette lager automatisk en OPPRETTET-hendelse - blokkerende)
        val jobbsøker = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678902"),
            kandidatnummer = Kandidatnummer("PAM124"),
            fornavn = Fornavn("Test2"),
            etternavn = Etternavn("Testesen2"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder Veiledersen"),
            veilederNavIdent = VeilederNavIdent("V123456"),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        db.leggTilJobbsøkere(listOf(jobbsøker))

        // Forsøk å slette skal feile pga jobbsoker_hendelse
        assertThatThrownBy { repository.slett(treffId) }
            .isInstanceOf(SQLException::class.java)

        // Verifiser at INGEN data ble slettet (god transaksjonshåndtering)
        assertThat(repository.hent(treffId)).isNotNull
        assertThat(db.hentAlleArbeidsgivere()).hasSize(1)
        assertThat(db.hentArbeidsgiverHendelser(treffId)).isNotEmpty
        assertThat(innleggRepo.hentById(innlegg.id)).isNotNull
        assertThat(db.hentHendelser(treffId)).isNotEmpty
        assertThat(db.hentAlleJobbsøkere()).hasSize(1)
        assertThat(db.hentJobbsøkerHendelser(treffId)).isNotEmpty
    }

    @Test
    fun `slett feiler når aktivitetskort_polling finnes (før publisering)`() {
        val navIdent = "A123456"
        val treffId = repository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Treff med aktivitetskort_polling",
                opprettetAvPersonNavident = navIdent,
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )

        // Legg til arbeidsgiver (som ville blitt slettet)
        val arbeidsgiver = Arbeidsgiver(
            arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.randomUUID()),
            treffId = treffId,
            orgnr = Orgnr("666666666"),
            orgnavn = Orgnavn("Test3 AS"),
            status = ArbeidsgiverStatus.AKTIV,
        )
        db.leggTilArbeidsgivere(listOf(arbeidsgiver))

        // Legg til innlegg (som ville blitt slettet)
        val innleggRepo = InnleggRepository(db.dataSource)
        val innlegg = innleggRepo.opprett(
            treffId = treffId,
            dto = OpprettInnleggRequestDto(
                tittel = "Test innlegg 3",
                opprettetAvPersonNavn = "Test Testesen",
                opprettetAvPersonBeskrivelse = "Veileder",
                sendesTilJobbsokerTidspunkt = null,
                htmlContent = "<p>Test3</p>"
            ),
            navIdent = navIdent
        )

        // Legg til jobbsøker og hendelse (blokkerende pga aktivitetskort_polling)
        val jobbsøker = Jobbsøker(
            personTreffId = PersonTreffId(UUID.randomUUID()),
            treffId = treffId,
            fødselsnummer = Fødselsnummer("12345678903"),
            kandidatnummer = Kandidatnummer("PAM125"),
            fornavn = Fornavn("Test3"),
            etternavn = Etternavn("Testesen3"),
            navkontor = Navkontor("0318"),
            veilederNavn = VeilederNavn("Veileder Veiledersen"),
            veilederNavIdent = VeilederNavIdent("V123456"),
            status = JobbsøkerStatus.LAGT_TIL,
        )
        db.leggTilJobbsøkere(listOf(jobbsøker))

        // Hent jobbsøker_hendelse_db_id for å kunne lage aktivitetskort_polling
        val hendelser = db.hentJobbsøkerHendelser(treffId)
        assertThat(hendelser).isNotEmpty

        // Legg til aktivitetskort_polling manuelt (blokkerende)
        db.dataSource.connection.use { c ->
            c.prepareStatement(
                """
                INSERT INTO aktivitetskort_polling (jobbsoker_hendelse_id, sendt_tidspunkt)
                SELECT jh.jobbsoker_hendelse_id, now()
                FROM jobbsoker_hendelse jh
                JOIN jobbsoker js ON jh.jobbsoker_id = js.jobbsoker_id
                JOIN rekrutteringstreff rt ON js.rekrutteringstreff_id = rt.rekrutteringstreff_id
                WHERE rt.id = ?
                LIMIT 1
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, treffId.somUuid)
                ps.executeUpdate()
            }
        }

        // Forsøk å slette skal feile pga aktivitetskort_polling
        assertThatThrownBy { repository.slett(treffId) }
            .isInstanceOf(SQLException::class.java)

        // Verifiser at INGEN data ble slettet (god transaksjonshåndtering)
        assertThat(repository.hent(treffId)).isNotNull
        assertThat(db.hentAlleArbeidsgivere()).hasSize(1)
        assertThat(db.hentArbeidsgiverHendelser(treffId)).isNotEmpty
        assertThat(innleggRepo.hentById(innlegg.id)).isNotNull
        assertThat(db.hentHendelser(treffId)).isNotEmpty
        assertThat(db.hentAlleJobbsøkere()).hasSize(1)
        assertThat(db.hentJobbsøkerHendelser(treffId)).isNotEmpty
    }

    @Test
    fun `slett lykkes med alle lovlige tabeller (før publisering)`() {
        val navIdent = "A123456"
        val treffId = repository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Treff med alle lovlige tabeller",
                opprettetAvPersonNavident = navIdent,
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )

        // Legg til arbeidsgiver (lager automatisk arbeidsgiver_hendelse)
        val arbeidsgiver = Arbeidsgiver(
            arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.randomUUID()),
            treffId = treffId,
            orgnr = Orgnr("999999999"),
            orgnavn = Orgnavn("Test AS"),
            status = ArbeidsgiverStatus.AKTIV,
        )
        db.leggTilArbeidsgivere(listOf(arbeidsgiver))

        // Legg til rekrutteringstreff_hendelse (OPPDATERT)
        db.leggTilRekrutteringstreffHendelse(treffId, RekrutteringstreffHendelsestype.OPPDATERT, navIdent)

        // Legg til næringskode via SQL
        db.dataSource.connection.use { c ->
            c.prepareStatement(
                """
                INSERT INTO naringskode (arbeidsgiver_id, kode, beskrivelse)
                SELECT ag.arbeidsgiver_id, '62.010', 'Programmeringstjenester'
                FROM arbeidsgiver ag
                JOIN rekrutteringstreff rt ON ag.rekrutteringstreff_id = rt.rekrutteringstreff_id
                WHERE rt.id = ?
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, treffId.somUuid)
                ps.executeUpdate()
            }
        }

        // Legg til innlegg
        val innleggRepo = InnleggRepository(db.dataSource)
        innleggRepo.opprett(
            treffId = treffId,
            dto = OpprettInnleggRequestDto(
                tittel = "Test innlegg",
                opprettetAvPersonNavn = "Test Testesen",
                opprettetAvPersonBeskrivelse = "Veileder",
                sendesTilJobbsokerTidspunkt = null,
                htmlContent = "<p>Test</p>"
            ),
            navIdent = navIdent
        )

        // Legg til ki_spørring_logg via SQL
        db.dataSource.connection.use { c ->
            c.prepareStatement(
                """
                INSERT INTO ki_spørring_logg 
                (id, treff_id, felt_type, spørring_fra_frontend, spørring_filtrert, 
                 bryter_retningslinjer, ki_navn, ki_versjon, svartid_ms)
                VALUES (?, ?, 'TITTEL', 'test', 'test', false, 'gpt-4', '1.0', 100)
                """.trimIndent()
            ).use { ps ->
                ps.setObject(1, UUID.randomUUID())
                ps.setObject(2, treffId.somUuid)
                ps.executeUpdate()
            }
        }

        // Verifiser at alle data er opprettet
        assertThat(db.hentAlleArbeidsgivere()).hasSize(1)
        assertThat(db.hentArbeidsgiverHendelser(treffId)).isNotEmpty
        assertThat(db.hentHendelser(treffId)).hasSizeGreaterThan(1)
        assertThat(db.hentAlleNæringskoder()).hasSize(1)

        // Slett skal lykkes (alle disse tabellene er lovlige å slette)
        repository.slett(treffId)

        // Verifiser at treffet er slettet
        assertThat(repository.hent(treffId)).isNull()
    }

    @Test
    fun `slett feiler etter publisering (selv uten andre data)`() {
        val navIdent = "A123456"
        val treffId = repository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Publisert treff",
                opprettetAvPersonNavident = navIdent,
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )

        // Legg til arbeidsgiver (som ville blitt slettet)
        val arbeidsgiver = Arbeidsgiver(
            arbeidsgiverTreffId = ArbeidsgiverTreffId(UUID.randomUUID()),
            treffId = treffId,
            orgnr = Orgnr("555555555"),
            orgnavn = Orgnavn("Test4 AS"),
            status = ArbeidsgiverStatus.AKTIV,
        )
        db.leggTilArbeidsgivere(listOf(arbeidsgiver))

        // Legg til innlegg (som ville blitt slettet)
        val innleggRepo = InnleggRepository(db.dataSource)
        val innlegg = innleggRepo.opprett(
            treffId = treffId,
            dto = OpprettInnleggRequestDto(
                tittel = "Test innlegg 4",
                opprettetAvPersonNavn = "Test Testesen",
                opprettetAvPersonBeskrivelse = "Veileder",
                sendesTilJobbsokerTidspunkt = null,
                htmlContent = "<p>Test4</p>"
            ),
            navIdent = navIdent
        )

        // Publiser treffet (blokkerende)
        repository.publiser(treffId, navIdent)

        // Forsøk å slette skal feile med UlovligSlettingException
        assertThatThrownBy { repository.slett(treffId) }
            .isInstanceOf(UlovligSlettingException::class.java)
            .hasMessageContaining("publisering")

        // Verifiser at INGEN data ble slettet (god transaksjonshåndtering)
        assertThat(repository.hent(treffId)).isNotNull
        assertThat(db.hentAlleArbeidsgivere()).hasSize(1)
        assertThat(db.hentArbeidsgiverHendelser(treffId)).isNotEmpty
        assertThat(innleggRepo.hentById(innlegg.id)).isNotNull
        assertThat(db.hentHendelser(treffId)).hasSizeGreaterThan(1) // OPPRETTET + PUBLISERT
    }

    @Test
    fun `Endre status gjør det den skal`() {
        val id = repository.opprett(
            OpprettRekrutteringstreffInternalDto(
                tittel = "Initielt",
                opprettetAvPersonNavident = "A1",
                opprettetAvNavkontorEnhetId = "0318",
                opprettetAvTidspunkt = nowOslo()
            )
        )

        val initieltTreff = repository.hent(id)
        assertThat(initieltTreff?.status).isEqualTo(RekrutteringstreffStatus.UTKAST)

        repository.endreStatus(
            id,
            RekrutteringstreffStatus.PUBLISERT,
        )

        val oppdatertTreff = repository.hent(id)
        assertThat(oppdatertTreff?.status).isEqualTo(RekrutteringstreffStatus.PUBLISERT)
    }
}
