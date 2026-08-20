package no.nav.toi.treffgjennomføring

import no.nav.toi.HendelseWriter
import no.nav.toi.JacksonConfig
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.jobbsoker.oppmøte.OppmøteService
import no.nav.toi.oppfølging.OppfølgingRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.treffgjennomføring.matching.MatchingRepository
import no.nav.toi.treffgjennomføring.møteplan.MøteplanRepository
import no.nav.toi.arbeidsgiver.LeggTilArbeidsgiver
import no.nav.toi.arbeidsgiver.Orgnavn
import no.nav.toi.arbeidsgiver.Orgnr
import no.nav.toi.jobbsoker.Etternavn
import no.nav.toi.jobbsoker.Fornavn
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.JobbsøkerRepository
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
    private val faseRepository = FaseRepository()
    private val oppmøteRepository = OppmøteRepository()
    private val møteplanRepository = MøteplanRepository()
    private val matchingRepository = MatchingRepository()
    private val oppfølgingRepository = OppfølgingRepository()
    private val reader = TreffgjennomføringReader(
        faseRepository, oppmøteRepository, møteplanRepository, matchingRepository, oppfølgingRepository,
    )
    private val jobbsøkerRepository = JobbsøkerRepository(db.dataSource, mapper)

    private val writer = TreffgjennomføringWriter(db.dataSource, kontekstRepository, faseRepository, reader)
    private val hendelser = HendelseWriter(
        jobbsøkerRepository,
        ArbeidsgiverRepository(db.dataSource, mapper),
        RekrutteringstreffRepository(db.dataSource),
        mapper,
    )
    private val oppmøteService = OppmøteService(
        treffgjennomføringWriter = writer,
        oppmøteRepository = oppmøteRepository,
        matchingRepository = matchingRepository,
        møteplanRepository = møteplanRepository,
        oppfølgingRepository = oppfølgingRepository,
        hendelseWriter = hendelser,
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
    fun `readeren setter sammen delene fra hvert subdomene`() {
        val treff = treffMedData()
        val kontekst = hentKontekst(treff)

        val dto = db.dataSource.connection.use { conn -> reader.les(conn, kontekst) }
        val oppmøte = db.dataSource.connection.use { conn ->
            oppmøteRepository.hentFremmøtteJobbsøkere(conn, kontekst.treffDbId)
        }

        assertThat(dto.rekrutteringstreffId).isEqualTo(treff.somString)
        assertThat(dto.oppmøte).containsExactlyElementsOf(oppmøte.map { it.somString })
        assertThat(dto.antallRom).isEqualTo(kontekst.antallRom)
        assertThat(dto.starttidspunkt).isEqualTo("10:00")
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
        oppmøteService.oppdaterOppmøte(treffId, OppmøteRequestDto(person.somString, true), navIdent)

    private fun hentKontekst(treffId: TreffId): Treffkontekst = db.dataSource.connection.use { conn ->
        kontekstRepository.hentTreffkontekst(conn, treffId)!!
    }
}
