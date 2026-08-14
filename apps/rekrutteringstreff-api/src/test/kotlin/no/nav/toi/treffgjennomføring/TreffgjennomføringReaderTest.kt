package no.nav.toi.treffgjennomføring

import no.nav.toi.JacksonConfig
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
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
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TreffgjennomføringReaderTest {

    private val forventetAntallSpørringer = 10

    private val db = TestDatabase()
    private val mapper = JacksonConfig.mapper
    private val navIdent = "Z999999"

    private val kontekstRepository = TreffkontekstRepository()
    private val repository = TreffgjennomføringRepository()
    private val reader = TreffgjennomføringReader(repository)
    private val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)

    private val jobbsøkerService = JobbsøkerService(
        dataSource = db.dataSource,
        jobbsøkerRepository = jobbsøkerRepository,
        treffkontekstRepository = kontekstRepository,
        treffgjennomføringRepository = repository,
        treffgjennomføringReader = reader,
        mapper = mapper,
    )

    @BeforeAll
    fun migrer() {
        Flyway.configure().dataSource(db.dataSource).load().migrate()
    }

    @AfterEach
    fun reset() {
        db.slettAlt()
    }

    @Test
    fun `readeren henter hele aggregatet i et fast antall spørringer`() {
        val treff = treffMedData()
        val kontekst = hentKontekst(treff)

        val antall = tellSpørringer { connection -> reader.les(connection, kontekst) }

        assertThat(antall).isEqualTo(forventetAntallSpørringer)
    }

    @Test
    fun `antall spørringer vokser ikke med antall jobbsøkere og arbeidsgivere`() {
        val lite = hentKontekst(treffMedData(antallJobbsøkere = 1, antallArbeidsgivere = 1))
        val antallLite = tellSpørringer { connection -> reader.les(connection, lite) }

        val stort = hentKontekst(treffMedData(antallJobbsøkere = 8, antallArbeidsgivere = 4, fnrPrefiks = "9"))
        val antallStort = tellSpørringer { connection -> reader.les(connection, stort) }

        assertThat(antallStort).isEqualTo(antallLite)
    }

    @Test
    fun `readeren gir samme svar som en direkte lesing av aggregatet`() {
        val treff = treffMedData()

        val fraReader = db.dataSource.connection.use { conn ->
            reader.les(conn, kontekstRepository.hent(conn, treff)!!)
        }
        val fraRepository = db.dataSource.connection.use { conn ->
            repository.hentAggregat(conn, kontekstRepository.hent(conn, treff)!!)
        }

        assertThat(fraReader.rekrutteringstreffId).isEqualTo(treff.somString)
        assertThat(fraReader.oppmøte).containsExactlyElementsOf(fraRepository.oppmøte.map { it.somString })
        assertThat(fraReader.fase).isEqualTo(fraRepository.fase)
        assertThat(fraReader.antallRom).isEqualTo(fraRepository.antallRom)
    }

    private fun tellSpørringer(block: (Connection) -> Unit): Int = db.dataSource.connection.use { ekte ->
        var antall = 0
        val teller = Proxy.newProxyInstance(
            Connection::class.java.classLoader,
            arrayOf(Connection::class.java),
            InvocationHandler { _, metode: Method, args: Array<Any?>? ->
                if (metode.name == "prepareStatement") antall++
                if (args == null) metode.invoke(ekte) else metode.invoke(ekte, *args)
            },
        ) as Connection

        block(teller)
        antall
    }

    private fun treffMedData(
        antallJobbsøkere: Int = 2,
        antallArbeidsgivere: Int = 2,
        fnrPrefiks: String = "1",
    ): TreffId {
        val treff = db.opprettRekrutteringstreffIDatabase(
            navIdent = navIdent,
            kategori = RekrutteringstreffKategori.WORKOP,
        )
        repeat(antallArbeidsgivere) { nr ->
            db.leggTilArbeidsgiverMedHendelse(
                LeggTilArbeidsgiver(
                    Orgnr("99999999${nr + 1}"),
                    Orgnavn("Testbedrift ${nr + 1}"),
                    emptyList(), null, null, null,
                ),
                treff,
            )
        }
        val personer = (1..antallJobbsøkere).map { nr ->
            db.leggTilJobbsøkereMedHendelse(
                listOf(
                    LeggTilJobbsøker(
                        Fødselsnummer("$fnrPrefiks${nr.toString().padStart(10, '0')}"),
                        Fornavn("Test$nr"),
                        Etternavn("Testesen"),
                    )
                ),
                treff,
            ).first()
        }
        personer.forEach { møtt(treff, it) }
        return treff
    }

    private fun møtt(treffId: TreffId, person: PersonTreffId) =
        jobbsøkerService.oppdaterOppmøte(treffId, OppmøteRequestDto(person.somString, true), navIdent)

    private fun hentKontekst(treffId: TreffId): Treffkontekst = db.dataSource.connection.use { conn ->
        kontekstRepository.hent(conn, treffId)!!
    }
}
