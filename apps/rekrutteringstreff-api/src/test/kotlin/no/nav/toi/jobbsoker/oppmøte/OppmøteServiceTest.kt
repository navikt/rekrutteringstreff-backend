package no.nav.toi.jobbsoker.oppmøte

import no.nav.toi.ApplicationContext
import no.nav.toi.TestInfrastructureContext
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.jobbsoker.LeggTilJobbsøker
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.OppmøteRequestDto
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Oppmøtet lagres som jobbsøkerstatusen MØTT_OPP, på samme akse som svarene. Testene låser at
 * svaret kommer tilbake når oppmøtet angres, og at deltakernummer og hendelser følger oppmøtet.
 * Resten av treffgjennomføringen testes over HTTP i `TreffgjennomføringKomponentTest`.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OppmøteServiceTest {

    private val db = TestDatabase()
    private val ctx = ApplicationContext(TestInfrastructureContext(dataSource = db.dataSource))
    private val navIdent = "Z999999"
    private val fnr = "12345678901"

    @BeforeAll
    fun migrer() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
    }

    @AfterEach
    fun reset() {
        db.slettAlt()
    }

    @Test
    fun `statusen settes ved registrering og tilbakestilles ved angring`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId)
        assertThat(status(person)).isEqualTo(JobbsøkerStatus.LAGT_TIL)

        møtt(treffId, person)
        assertThat(status(person)).isEqualTo(JobbsøkerStatus.MØTT_OPP)

        ikkeMøtt(treffId, person)
        assertThat(status(person)).isEqualTo(JobbsøkerStatus.LAGT_TIL)

        møtt(treffId, person)
        assertThat(status(person)).isEqualTo(JobbsøkerStatus.MØTT_OPP)
    }

    /**
     * Tellingene på Om treffet leser `antallPerStatus`, så angringa må gi svaret tilbake,
     * ikke bare LAGT_TIL.
     */
    @Test
    fun `svaret kommer tilbake når oppmøtet fjernes`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId)
        db.inviterJobbsøkere(listOf(person), treffId)
        db.svarJaTilInvitasjon(Fødselsnummer(fnr), treffId, "testperson")
        assertThat(status(person)).isEqualTo(JobbsøkerStatus.SVART_JA)

        møtt(treffId, person)
        assertThat(status(person)).isEqualTo(JobbsøkerStatus.MØTT_OPP)

        ikkeMøtt(treffId, person)
        assertThat(status(person)).isEqualTo(JobbsøkerStatus.SVART_JA)
    }

    @Test
    fun `invitasjonen kommer tilbake når oppmøtet fjernes for en som ikke har svart`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId)
        db.inviterJobbsøkere(listOf(person), treffId)

        møtt(treffId, person)
        ikkeMøtt(treffId, person)

        assertThat(status(person)).isEqualTo(JobbsøkerStatus.INVITERT)
    }

    /** Rekonstruksjonen skal ikke plukke et tidligere oppmøte når jobbsøkeren har møtt flere ganger. */
    @Test
    fun `gjentatt registrering og angring lander fortsatt på svaret`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId)
        db.inviterJobbsøkere(listOf(person), treffId)
        db.svarJaTilInvitasjon(Fødselsnummer(fnr), treffId, "testperson")

        repeat(3) {
            møtt(treffId, person)
            ikkeMøtt(treffId, person)
        }

        assertThat(status(person)).isEqualTo(JobbsøkerStatus.SVART_JA)
    }

    @Test
    fun `svart nei blir ikke til svart ja av et oppmøte som angres`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId)
        db.inviterJobbsøkere(listOf(person), treffId)
        db.svarNeiTilInvitasjon(Fødselsnummer(fnr), treffId, "testperson")

        møtt(treffId, person)
        ikkeMøtt(treffId, person)

        assertThat(status(person)).isEqualTo(JobbsøkerStatus.SVART_NEI)
    }

    /**
     * MØTT_OPP overskriver svaret så lenge oppmøtet står. Svaret er ikke tapt, det ligger i
     * hendelsesloggen og kommer tilbake ved angring, men «svart ja» telles ikke imens.
     */
    @Test
    fun `svart ja telles ikke lenger mens jobbsøkeren er registrert møtt`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId)
        db.inviterJobbsøkere(listOf(person), treffId)
        db.svarJaTilInvitasjon(Fødselsnummer(fnr), treffId, "testperson")
        assertThat(antallMedStatus(treffId, JobbsøkerStatus.SVART_JA)).isEqualTo(1)

        møtt(treffId, person)

        assertThat(antallMedStatus(treffId, JobbsøkerStatus.SVART_JA)).isZero()
        assertThat(antallMedStatus(treffId, JobbsøkerStatus.MØTT_OPP)).isEqualTo(1)

        ikkeMøtt(treffId, person)

        assertThat(antallMedStatus(treffId, JobbsøkerStatus.SVART_JA)).isEqualTo(1)
        assertThat(antallMedStatus(treffId, JobbsøkerStatus.MØTT_OPP)).isZero()
    }

    @Test
    fun `statusen og hendelsene gir samme svar for alle jobbsøkere`() {
        val treffId = workOpTreff()
        val møtt = jobbsøker(treffId, "11111111111")
        val angret = jobbsøker(treffId, "22222222222")
        jobbsøker(treffId, "33333333333")

        møtt(treffId, møtt)
        møtt(treffId, angret)
        ikkeMøtt(treffId, angret)

        assertThat(antallAvvikMellomStatusOgHendelser()).isZero()
    }

    @Test
    fun `jobbsøker uten oppmøteregistrering beholder statusen sin og er ikke fremmøtt`() {
        val treffId = workOpTreff()
        val registrert = jobbsøker(treffId, "11111111111")
        val urørt = jobbsøker(treffId, "22222222222")

        møtt(treffId, registrert)

        assertThat(status(urørt)).isEqualTo(JobbsøkerStatus.LAGT_TIL)
        assertThat(ctx.treffgjennomføringService.hent(treffId).oppmøte).containsExactly(registrert.somString)
    }

    @Test
    fun `oppmøte og angring skriver én hendelse hver, med deltakernummer i dataene`() {
        val treffId = workOpTreff()
        val person = jobbsøker(treffId)

        møtt(treffId, person)
        assertThat(hendelsedata("REGISTRERT_OPPMØTE").single()).contains("\"deltakernummer\": 1")

        ikkeMøtt(treffId, person)
        assertThat(hendelsedata("REGISTRERT_OPPMØTE_FJERNET")).containsExactly("null")
    }

    private fun workOpTreff(): TreffId =
        db.opprettRekrutteringstreffIDatabase(navIdent = navIdent, kategori = RekrutteringstreffKategori.WORKOP)

    private fun jobbsøker(treffId: TreffId, fødselsnummer: String = fnr): PersonTreffId =
        db.leggTilJobbsøkereMedHendelse(
            listOf(LeggTilJobbsøker(Fødselsnummer(fødselsnummer), Fornavn("Test"), Etternavn("Testesen"))),
            treffId,
        ).first()

    private fun møtt(treffId: TreffId, person: PersonTreffId) =
        ctx.oppmøteService.oppdaterOppmøte(treffId, OppmøteRequestDto(person.somString, true), navIdent)

    private fun ikkeMøtt(treffId: TreffId, person: PersonTreffId) =
        ctx.oppmøteService.oppdaterOppmøte(treffId, OppmøteRequestDto(person.somString, false), navIdent)

    private fun status(person: PersonTreffId): JobbsøkerStatus = db.dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT status FROM jobbsoker WHERE id = ?").use { stmt ->
            stmt.setObject(1, person.somUuid)
            stmt.executeQuery().use { rs ->
                rs.next()
                JobbsøkerStatus.valueOf(rs.getString(1))
            }
        }
    }

    /** Samme kolonne som jobbsøkersøket aggregerer til `antallPerStatus`. */
    private fun antallMedStatus(treffId: TreffId, status: JobbsøkerStatus): Int =
        db.dataSource.connection.use { conn ->
            val sql = """
                SELECT COUNT(*)
                FROM jobbsoker j
                JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
                WHERE rt.id = ? AND j.status = ?
            """.trimIndent()
            conn.prepareStatement(sql).use { stmt ->
                stmt.setObject(1, treffId.somUuid)
                stmt.setString(2, status.name)
                stmt.executeQuery().use { it.next(); it.getInt(1) }
            }
        }

    private fun antallAvvikMellomStatusOgHendelser(): Int = db.dataSource.connection.use { conn ->
        val sql = """
            SELECT COUNT(*)
            FROM jobbsoker j
            LEFT JOIN LATERAL (
                SELECT jh.hendelsestype
                FROM jobbsoker_hendelse jh
                WHERE jh.jobbsoker_id = j.jobbsoker_id
                  AND jh.hendelsestype IN ('REGISTRERT_OPPMØTE', 'REGISTRERT_OPPMØTE_FJERNET')
                ORDER BY jh.tidspunkt DESC, jh.jobbsoker_hendelse_id DESC
                LIMIT 1
            ) h ON TRUE
            WHERE COALESCE(h.hendelsestype = 'REGISTRERT_OPPMØTE', FALSE) IS DISTINCT FROM (j.status = ?)
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, JobbsøkerStatus.MØTT_OPP.name)
            stmt.executeQuery().use { it.next(); it.getInt(1) }
        }
    }

    private fun hendelsedata(hendelsestype: String): List<String> = db.dataSource.connection.use { conn ->
        val sql = """
            SELECT hendelse_data::text
            FROM jobbsoker_hendelse
            WHERE hendelsestype = ?
            ORDER BY jobbsoker_hendelse_id
        """.trimIndent()
        conn.prepareStatement(sql).use { stmt ->
            stmt.setString(1, hendelsestype)
            stmt.executeQuery().use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) ?: "null" else null }.toList()
            }
        }
    }
}
