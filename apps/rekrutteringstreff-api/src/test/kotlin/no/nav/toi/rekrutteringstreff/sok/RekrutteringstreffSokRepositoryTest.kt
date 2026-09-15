package no.nav.toi.rekrutteringstreff.sok

import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.eier.EierService
import org.assertj.core.api.Assertions.assertThat
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RekrutteringstreffSokRepositoryTest {
    companion object {
        private val db = TestDatabase()
        private lateinit var repository: RekrutteringstreffSokRepository

        @BeforeAll
        @JvmStatic
        fun setup() {
            Flyway.configure()
                .dataSource(db.dataSource)
                .load()
                .migrate()
            repository = RekrutteringstreffSokRepository(db.dataSource)
        }
    }

    @AfterEach
    fun tearDown() {
        db.slettAlt()
    }

    private fun opprettTreff(
        navIdent: String = "A123456",
        tittel: String = "TestTreff",
        status: RekrutteringstreffStatus = RekrutteringstreffStatus.PUBLISERT,
        kategori: RekrutteringstreffKategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF,
        kontorId: String = "0315",
        fylkesnummer: String = "03",
        kommunenummer: String = "0301",
    ): TreffId =
        db.opprettRekrutteringstreffMedEierOgKontor(
            navIdent = navIdent,
            tittel = tittel,
            kategori = kategori,
            status = status,
            kontorId = kontorId,
            fylkesnummer = fylkesnummer,
            kommunenummer = kommunenummer,
        )

    @Test
    fun `søk bruker eiertabellen for eiere kontorer og aggregeringer`() {
        val treffId = opprettTreff()
        val eierRepository = EierRepository(db.dataSource)
        val service = EierService(eierRepository, RekrutteringstreffRepository(db.dataSource), db.dataSource)
        service.leggTilEierMedKontor(treffId, "B654321", "1201")
        service.leggTilEierMedKontor(treffId, "C987654", "0315")
        db.oppdaterEierarrays(listOf("gammel eier"), listOf("gammelt kontor"), treffId)

        val treff = sokEierskap(Visning.MINE, navIdent = "B654321").treff.single()
        assertThat(treff.eiere).containsExactlyInAnyOrder("A123456", "B654321", "C987654")
        assertThat(treff.kontorer).containsExactlyInAnyOrder("0315", "1201")
        assertThat(sokEierskap(Visning.MINE, navIdent = "gammel eier").antallTotalt).isZero()
        assertThat(sokEierskap(Visning.MITT_KONTOR, kontorId = "gammelt kontor").antallTotalt).isZero()
        assertThat(sokEierskap(Visning.MITT_KONTOR, kontorId = "1201").antallTotalt).isEqualTo(1)
        assertThat(sokEierskap(Visning.VALGTE_KONTORER, kontorer = listOf("1201")).antallTotalt).isEqualTo(1)

        service.slettEier(treffId, "B654321", "A123456")
        db.oppdaterEierarrays(listOf("A123456", "B654321"), listOf("0315", "1201"), treffId)

        listOf(
            sokEierskap(Visning.MINE, navIdent = "B654321"),
            sokEierskap(Visning.MITT_KONTOR, kontorId = "1201"),
            sokEierskap(Visning.VALGTE_KONTORER, kontorer = listOf("1201")),
        ).forEach {
            assertThat(it.treff).isEmpty()
            assertThat(it.antallTotalt).isZero()
            assertThat(it.kategoriaggregering).isEmpty()
            assertThat(it.statusaggregering).isEmpty()
            assertThat(it.publisertstatusaggregering).isEmpty()
            assertThat(it.geografiaggregering.fylkesnummeraggregering).isEmpty()
            assertThat(it.geografiaggregering.kommunenummeraggregering).isEmpty()
        }
    }

    @Test
    fun `gammelt eierarray gir ikke innsyn i utkast eller workop`() {
        val utkast = opprettTreff(status = RekrutteringstreffStatus.UTKAST)
        val workop = opprettTreff(kategori = RekrutteringstreffKategori.WORKOP)
        listOf(utkast, workop).forEach {
            db.oppdaterEierarrays(listOf("B654321"), listOf("0315"), it)
        }

        assertThat(sokEierskap(Visning.ALLE, navIdent = "B654321").antallTotalt).isZero()
        assertThat(sokEierskap(Visning.ALLE, navIdent = "A123456").antallTotalt).isEqualTo(2)
    }

    @Test
    fun `view returnerer tomme arrays uten eierrader og filtrene gir ingen eier eller kontormatch`() {
        val treffId = opprettTreff()
        db.dataSource.connection.use { connection ->
            connection.createStatement().use { stmt ->
                stmt.executeUpdate("DELETE FROM rekrutteringstreff_eier")
                stmt.executeQuery("SELECT eiere, kontorer FROM rekrutteringstreff_sok_view").use { rs ->
                    check(rs.next())
                    assertThat(rs.getArray("eiere")).isNotNull()
                    assertThat(rs.getArray("kontorer")).isNotNull()
                    assertThat(rs.getArray("eiere").array as Array<*>).isEmpty()
                    assertThat(rs.getArray("kontorer").array as Array<*>).isEmpty()
                }
            }
        }
        val treff = sokEierskap(Visning.ALLE).treff.single()
        assertThat(treff.id).isEqualTo(treffId.somString)
        assertThat(treff.eiere).isEmpty()
        assertThat(treff.kontorer).isEmpty()
        assertThat(sokEierskap(Visning.MINE).antallTotalt).isZero()
        assertThat(sokEierskap(Visning.MITT_KONTOR).antallTotalt).isZero()
        assertThat(sokEierskap(Visning.VALGTE_KONTORER, kontorer = listOf("0315")).antallTotalt).isZero()
    }

    private fun sokEierskap(
        visning: Visning,
        navIdent: String = "A123456",
        kontorId: String = "0315",
        kontorer: List<String>? = null,
    ) = repository.sokMedAggregering(
        navIdent = navIdent, kontorId = kontorId, kategorier = null, statuser = null, publisertStatuser = null,
        kontorer = kontorer, fylkesnumre = null, kommunenumre = null, visning = visning, side = 1, antallPerSide = 25,
    )

    @Test
    fun `sok returnerer tomme resultater når ingen treff finnes`() {
        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null,
            statuser = null,
            publisertStatuser = null,
            kontorer = null,
            fylkesnumre = null,
            kommunenumre = null,
            visning = Visning.ALLE,
            side = 1,
            antallPerSide = 25
        )
        assertThat(resultat.treff).isEmpty()
        assertThat(resultat.antallTotalt).isEqualTo(0)
    }

    @Test
    fun `sok returnerer alle treff uten filtre`() {
        opprettTreff(tittel = "Treff 1")
        opprettTreff(tittel = "Treff 2")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null,
            statuser = null,
            publisertStatuser = null,
            kontorer = null,
            fylkesnumre = null,
            kommunenumre = null,
            visning = Visning.ALLE,
            side = 1,
            antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(2)
        assertThat(resultat.antallTotalt).isEqualTo(2)
    }

    @Test
    fun `sok med visning MINE returnerer kun egne treff`() {
        opprettTreff(navIdent = "A123456", tittel = "Mitt")
        opprettTreff(navIdent = "B654321", tittel = "Andres")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null,
            statuser = null,
            publisertStatuser = null,
            kontorer = null,
            fylkesnumre = null,
            kommunenumre = null,
            visning = Visning.MINE,
            side = 1,
            antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Mitt")
    }

    @Test
    fun `sok med visning MITT_KONTOR returnerer kun treff fra eget kontor`() {
        opprettTreff(kontorId = "0315", tittel = "Mitt kontor")
        opprettTreff(kontorId = "1201", tittel = "Annet kontor")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null,
            statuser = null,
            publisertStatuser = null,
            kontorer = null,
            fylkesnumre = null,
            kommunenumre = null,
            visning = Visning.MITT_KONTOR,
            side = 1,
            antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Mitt kontor")
    }

    @Test
    fun `sok filtrerer på status`() {
        opprettTreff(tittel = "Pub", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(tittel = "Utkast", status = RekrutteringstreffStatus.UTKAST)

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null,
            statuser = listOf(SokStatus.PUBLISERT), publisertStatuser = null,
            kontorer = null,
            fylkesnumre = null,
            kommunenumre = null,
            visning = Visning.ALLE,
            side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Pub")
    }

    @Test
    fun `sok filtrerer på flere statuser`() {
        opprettTreff(tittel = "Pub", status = RekrutteringstreffStatus.PUBLISERT)
        opprettTreff(tittel = "Utkast", status = RekrutteringstreffStatus.UTKAST)
        opprettTreff(tittel = "Avlyst", status = RekrutteringstreffStatus.AVLYST)

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null,
            statuser = listOf(SokStatus.PUBLISERT, SokStatus.UTKAST), publisertStatuser = null,
            kontorer = null,
            fylkesnumre = null,
            kommunenumre = null,
            visning = Visning.ALLE,
            side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(2)
    }

    @Test
    fun `sok filtrerer på status PUBLISERT samt publisertStatuser`() {
        opprettTreff(tittel = "Pub", status = RekrutteringstreffStatus.PUBLISERT)

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456",
            kontorId = "0315",
            kategorier = null,
            statuser = listOf(SokStatus.PUBLISERT, SokStatus.UTKAST),
            publisertStatuser = listOf(PublisertStatus.SVARFRIST_PASSERT, PublisertStatus.ÅPEN_FOR_SØKERE),
            kontorer = null,
            fylkesnumre = null,
            kommunenumre = null,
            visning = Visning.ALLE,
            side = 1,
            antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.statusaggregering).hasSize(1)
        val publisert = resultat.statusaggregering.filter { it.verdi == SokStatus.PUBLISERT.name }
        assertThat(publisert.size).isEqualTo(1)

        assertThat(resultat.publisertstatusaggregering).hasSize(1)
        val åpenForSøkere =
            resultat.publisertstatusaggregering.filter { it.verdi == PublisertStatus.ÅPEN_FOR_SØKERE.name }
        assertThat(åpenForSøkere.size).isEqualTo(1)
    }

    @Test
    fun `sok filtrerer på kontorer`() {
        opprettTreff(tittel = "Oslo", kontorId = "0315")
        opprettTreff(tittel = "Bergen", kontorId = "1201")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null,
            statuser = null,
            publisertStatuser = null,
            kontorer = listOf("0315"),
            fylkesnumre = null,
            kommunenumre = null,
            visning = Visning.ALLE,
            side = 1,
            antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Oslo")
    }


    @Test
    fun `sok filtrerer på fylke`() {
        opprettTreff(tittel = "Oslo", fylkesnummer = "03", kommunenummer = "0301")
        opprettTreff(tittel = "Bergen", fylkesnummer = "46", kommunenummer = "4601")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null, statuser = null, publisertStatuser = null, kontorer = null,
            fylkesnumre = listOf("03"), kommunenumre = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Oslo")
        assertThat(resultat.antallTotalt).isEqualTo(1)
    }

    @Test
    fun `sok filtrerer på kommune`() {
        opprettTreff(tittel = "Oslo", fylkesnummer = "03", kommunenummer = "0301")
        opprettTreff(tittel = "Bergen", fylkesnummer = "46", kommunenummer = "4601")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null, statuser = null, publisertStatuser = null, kontorer = null,
            fylkesnumre = null, kommunenumre = listOf("4601"),
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Bergen")
    }

    @Test
    fun `sok filtrerer på flere fylker`() {
        opprettTreff(tittel = "Oslo", fylkesnummer = "03")
        opprettTreff(tittel = "Bergen", fylkesnummer = "46")
        opprettTreff(tittel = "Trondheim", fylkesnummer = "50")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null, statuser = null, publisertStatuser = null, kontorer = null,
            fylkesnumre = listOf("03", "46"), kommunenumre = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(2)
        assertThat(resultat.treff).extracting("tittel").containsExactlyInAnyOrder("Oslo", "Bergen")
    }

    @Test
    fun `sok filtrerer på både fylke og kommune`() {
        opprettTreff(tittel = "Oslo", fylkesnummer = "03", kommunenummer = "0301")
        opprettTreff(tittel = "Bergen", fylkesnummer = "46", kommunenummer = "4601")
        opprettTreff(tittel = "Trondheim", fylkesnummer = "50", kommunenummer = "5001")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null, statuser = null, publisertStatuser = null, kontorer = null,
            fylkesnumre = listOf("03"), kommunenumre = listOf("0301"),
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.treff.first().tittel).isEqualTo("Oslo")
    }

    @Test
    fun `fylkesnummeraggregering teller per fylke`() {
        opprettTreff(fylkesnummer = "03")
        opprettTreff(fylkesnummer = "03")
        opprettTreff(fylkesnummer = "46")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null, statuser = null, publisertStatuser = null, kontorer = null,
            fylkesnumre = null, kommunenumre = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        val oslo = resultat.geografiaggregering.fylkesnummeraggregering.find { it.verdi == "03" }
        val vestland = resultat.geografiaggregering.fylkesnummeraggregering.find { it.verdi == "46" }
        assertThat(oslo?.antall).isEqualTo(2)
        assertThat(vestland?.antall).isEqualTo(1)
    }

    @Test
    fun `kommunenummeraggregering teller per kommune`() {
        opprettTreff(fylkesnummer = "03", kommunenummer = "0301")
        opprettTreff(fylkesnummer = "03", kommunenummer = "0301")
        opprettTreff(fylkesnummer = "46", kommunenummer = "4601")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null, statuser = null, publisertStatuser = null, kontorer = null,
            fylkesnumre = null, kommunenumre = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        val oslo = resultat.geografiaggregering.kommunenummeraggregering.find { it.verdi == "0301" }
        val bergen = resultat.geografiaggregering.kommunenummeraggregering.find { it.verdi == "4601" }
        assertThat(oslo?.antall).isEqualTo(2)
        assertThat(bergen?.antall).isEqualTo(1)
    }

    @Test
    fun `fylkesnummeraggregering ekskluderer fylkefilter`() {
        opprettTreff(fylkesnummer = "03")
        opprettTreff(fylkesnummer = "46")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null, statuser = null, publisertStatuser = null, kontorer = null,
            fylkesnumre = listOf("03"), kommunenumre = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.treff).hasSize(1)
        assertThat(resultat.geografiaggregering.fylkesnummeraggregering).extracting("verdi").containsExactlyInAnyOrder("03", "46")
    }

    @Test
    fun `kommunenummeraggregering respekterer fylkefilter`() {
        opprettTreff(fylkesnummer = "03", kommunenummer = "0301")
        opprettTreff(fylkesnummer = "46", kommunenummer = "4601")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null, statuser = null, publisertStatuser = null, kontorer = null,
            fylkesnumre = listOf("03"), kommunenumre = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.geografiaggregering.kommunenummeraggregering).extracting("verdi").containsExactly("0301")
        assertThat(resultat.geografiaggregering.kommunenummeraggregering.find { it.verdi == "0301" }?.antall).isEqualTo(1)
    }

    @Test
    fun `fylkesnummeraggregering respekterer kontorfilter`() {
        opprettTreff(fylkesnummer = "03", kontorId = "0315")
        opprettTreff(fylkesnummer = "03", kontorId = "1201")
        opprettTreff(fylkesnummer = "46", kontorId = "0315")

        val resultat = repository.sokMedAggregering(
            navIdent = "A123456", kontorId = "0315",
            kategorier = null, statuser = null, publisertStatuser = null, kontorer = listOf("0315"),
            fylkesnumre = null, kommunenumre = null,
            visning = Visning.ALLE, side = 1, antallPerSide = 25
        )
        assertThat(resultat.geografiaggregering.fylkesnummeraggregering.find { it.verdi == "03" }?.antall).isEqualTo(1)
        assertThat(resultat.geografiaggregering.fylkesnummeraggregering.find { it.verdi == "46" }?.antall).isEqualTo(1)
    }

        @Test
        fun `sok ekskluderer slettede treff`() {
            opprettTreff(tittel = "Synlig", status = RekrutteringstreffStatus.PUBLISERT)
            opprettTreff(tittel = "Slettet", status = RekrutteringstreffStatus.SLETTET)

            val resultat = repository.sokMedAggregering(
                navIdent = "A123456", kontorId = "0315",
                kategorier = null,
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 1,
                antallPerSide = 25
            )
            assertThat(resultat.treff).hasSize(1)
            assertThat(resultat.antallTotalt).isEqualTo(1)
            assertThat(resultat.treff.first().tittel).isEqualTo("Synlig")
        }

        @Test
        fun `sok paginerer korrekt`() {
            repeat(5) { opprettTreff(tittel = "Treff $it") }

            val resultat1 = repository.sokMedAggregering(
                navIdent = "A123456", kontorId = "0315",
                kategorier = null,
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 1,
                antallPerSide = 2
            )
            assertThat(resultat1.treff).hasSize(2)
            assertThat(resultat1.antallTotalt).isEqualTo(5)

            val resultat3 = repository.sokMedAggregering(
                navIdent = "A123456",
                kontorId = "0315",
                kategorier = null,
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 3,
                antallPerSide = 2
            )
            assertThat(resultat3.treff).hasSize(1)
        }

        @Test
        fun `sok handterer store sidetall uten overflow`() {
            opprettTreff(tittel = "Treff 1")

            val resultat = repository.sokMedAggregering(
                navIdent = "A123456", kontorId = "0315",
                kategorier = null,
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = Int.MAX_VALUE,
                antallPerSide = 100
            )

            assertThat(resultat.treff).isEmpty()
            assertThat(resultat.antallTotalt).isEqualTo(1)
        }

        @Test
        fun `sok mapper alle felter korrekt`() {
            opprettTreff(tittel = "Fullt treff", navIdent = "A123456", kontorId = "0315")

            val resultat = repository.sokMedAggregering(
                navIdent = "A123456", kontorId = "0315",
                kategorier = null,
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 1,
                antallPerSide = 25
            )
            assertThat(resultat.treff).hasSize(1)
            val t = resultat.treff.first()
            assertThat(t.tittel).isEqualTo("Fullt treff")
            assertThat(t.beskrivelse).isNotNull()
            assertThat(t.kategori).isEqualTo(RekrutteringstreffKategori.REKRUTTERINGSTREFF)
            assertThat(t.status).isEqualTo(RekrutteringstreffStatus.PUBLISERT)
            assertThat(t.fraTid).isNotNull()
            assertThat(t.tilTid).isNotNull()
            assertThat(t.gateadresse).isEqualTo("Testgata 123")
            assertThat(t.postnummer).isEqualTo("0484")
            assertThat(t.poststed).isEqualTo("OSLO")
            assertThat(t.eiere).contains("A123456")
            assertThat(t.kontorer).contains("0315")
            assertThat(t.opprettetAv).isEqualTo("A123456")
            assertThat(t.opprettetAvTidspunkt).isNotNull()
            assertThat(t.sistEndret).isNotNull()
        }

        @Test
        fun `statusaggregering teller per status`() {
            opprettTreff(status = RekrutteringstreffStatus.PUBLISERT)
            opprettTreff(status = RekrutteringstreffStatus.PUBLISERT)
            opprettTreff(status = RekrutteringstreffStatus.UTKAST)

            val resultat = repository.sokMedAggregering(
                navIdent = "A123456",
                kontorId = "0315",
                kategorier = null,
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 1,
                antallPerSide = 25
            )
            val publisert = resultat.statusaggregering.find { it.verdi == SokStatus.PUBLISERT.name }
            val utkast = resultat.statusaggregering.find { it.verdi == SokStatus.UTKAST.name }
            assertThat(publisert?.antall).isEqualTo(2)
            assertThat(utkast?.antall).isEqualTo(1)
        }

        @Test
        fun `statusaggregering respekterer kontorfilter`() {
            opprettTreff(status = RekrutteringstreffStatus.PUBLISERT, kontorId = "0315")
            opprettTreff(status = RekrutteringstreffStatus.PUBLISERT, kontorId = "1201")
            opprettTreff(status = RekrutteringstreffStatus.UTKAST, kontorId = "0315")

            val resultat = repository.sokMedAggregering(
                navIdent = "A123456",
                kontorId = "0315",
                kategorier = null,
                statuser = null,
                publisertStatuser = null,
                kontorer = listOf("0315"),
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 1,
                antallPerSide = 25
            )
            val publisert = resultat.statusaggregering.find { it.verdi == SokStatus.PUBLISERT.name }
            val utkast = resultat.statusaggregering.find { it.verdi == SokStatus.UTKAST.name }
            assertThat(publisert?.antall).isEqualTo(1)
            assertThat(utkast?.antall).isEqualTo(1)
        }

        @Test
        fun `statusaggregering ekskluderer statusfilter`() {
            opprettTreff(status = RekrutteringstreffStatus.PUBLISERT)
            opprettTreff(status = RekrutteringstreffStatus.UTKAST)

            val resultat = repository.sokMedAggregering(
                navIdent = "A123456",
                kontorId = "0315",
                kategorier = null,
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 1,
                antallPerSide = 25
            )
            assertThat(resultat.statusaggregering).hasSize(2)
        }

        @Test
        fun `sok filtrerer på kategori`() {
            opprettTreff(tittel = "Rekrutteringstreff", kategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF)
            opprettTreff(tittel = "WorkOp", kategori = RekrutteringstreffKategori.WORKOP)

            val resultat = repository.sokMedAggregering(
                navIdent = "A123456",
                kontorId = "0315",
                kategorier = listOf(SokKategori.WORKOP),
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 1,
                antallPerSide = 25
            )
            assertThat(resultat.treff).hasSize(1)
            assertThat(resultat.treff.first().tittel).isEqualTo("WorkOp")
        }

        @Test
        fun `kategoriaggregering teller per kategori og ekskluderer kategorifilter`() {
            opprettTreff(kategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF)
            opprettTreff(kategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF)
            opprettTreff(kategori = RekrutteringstreffKategori.WORKOP)

            val resultat = repository.sokMedAggregering(
                navIdent = "A123456",
                kontorId = "0315",
                kategorier = listOf(SokKategori.WORKOP),
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 1,
                antallPerSide = 25
            )
            val rekrutteringstreff =
                resultat.kategoriaggregering.find { it.verdi == SokKategori.REKRUTTERINGSTREFF.name }
            val workOp = resultat.kategoriaggregering.find { it.verdi == SokKategori.WORKOP.name }
            assertThat(rekrutteringstreff?.antall).isEqualTo(2)
            assertThat(workOp?.antall).isEqualTo(1)
        }

        @Test
        fun `skal kun returnere egne utkast og skjule andres utkast fra aggregeringen`() {
            val egetUtkast =
                opprettTreff(navIdent = "A123456", tittel = "Mitt utkast", status = RekrutteringstreffStatus.UTKAST)
            val egetPublisert = opprettTreff(
                navIdent = "A123456",
                tittel = "Mitt publiserte treff",
                status = RekrutteringstreffStatus.PUBLISERT
            )
            val noenAndresUtkastId = opprettTreff(
                navIdent = "B654321",
                tittel = "Noen andres utkast",
                status = RekrutteringstreffStatus.UTKAST
            )
            val noenAndresPublisert = opprettTreff(
                navIdent = "B654321",
                tittel = "Noen andres publiserte treff",
                status = RekrutteringstreffStatus.PUBLISERT
            )

            val resultat = repository.sokMedAggregering(
                navIdent = "A123456",
                kontorId = "0315",
                kategorier = null,
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 1,
                antallPerSide = 25
            )

            assertThat(resultat.treff).extracting("id").doesNotContain(noenAndresUtkastId.toString())
            assertThat(resultat.treff).extracting("id")
                .contains(egetUtkast.toString(), egetPublisert.toString(), noenAndresPublisert.toString())

            val utkastAggregering = resultat.statusaggregering.find { it.verdi == SokStatus.UTKAST.name }
            val publisertAggregering = resultat.statusaggregering.find { it.verdi == SokStatus.PUBLISERT.name }

            assertThat(utkastAggregering?.antall).isEqualTo(1)
            assertThat(publisertAggregering?.antall).isEqualTo(2)
        }

        @Test
        fun `skal kun returnere egne WorkOp og skjule andres WorkOp fra aggregeringen`() {
            val egetWorkOp = opprettTreff(
                navIdent = "A123456", tittel = "Mitt WorkOp",
                kategori = RekrutteringstreffKategori.WORKOP
            )
            val egetRekrutteringstreff = opprettTreff(
                navIdent = "A123456", tittel = "Mitt rekrutteringstreff",
                kategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF
            )
            val andresWorkOp = opprettTreff(
                navIdent = "B654321", tittel = "Andres WorkOp",
                kategori = RekrutteringstreffKategori.WORKOP
            )
            val andresRekrutteringstreff = opprettTreff(
                navIdent = "B654321", tittel = "Andres rekrutteringstreff",
                kategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF
            )

            val resultat = repository.sokMedAggregering(
                navIdent = "A123456", kontorId = "0315",
                kategorier = null,
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 1,
                antallPerSide = 25
            )

            assertThat(resultat.treff).extracting("id").doesNotContain(andresWorkOp.toString())
            assertThat(resultat.treff).extracting("id")
                .contains(egetWorkOp.toString(), egetRekrutteringstreff.toString(), andresRekrutteringstreff.toString())

            val workOpAggregering = resultat.kategoriaggregering.find { it.verdi == SokKategori.WORKOP.name }
            val rekrutteringstreffAggregering =
                resultat.kategoriaggregering.find { it.verdi == SokKategori.REKRUTTERINGSTREFF.name }

            assertThat(workOpAggregering?.antall).isEqualTo(1)
            assertThat(rekrutteringstreffAggregering?.antall).isEqualTo(2)
        }

        @Test
        fun `skal returnere andres WorkOp og inkludere i aggregering nar bruker er utvikler`() {
            val egetWorkOp = opprettTreff(
                navIdent = "A123456", tittel = "Mitt WorkOp",
                kategori = RekrutteringstreffKategori.WORKOP
            )
            val egetRekrutteringstreff = opprettTreff(
                navIdent = "A123456", tittel = "Mitt rekrutteringstreff",
                kategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF
            )
            val andresWorkOp = opprettTreff(
                navIdent = "B654321", tittel = "Andres WorkOp",
                kategori = RekrutteringstreffKategori.WORKOP
            )
            val andresRekrutteringstreff = opprettTreff(
                navIdent = "B654321", tittel = "Andres rekrutteringstreff",
                kategori = RekrutteringstreffKategori.REKRUTTERINGSTREFF
            )

            val resultat = repository.sokMedAggregering(
                navIdent = "A123456", kontorId = "0315",
                kategorier = null,
                statuser = null,
                publisertStatuser = null,
                kontorer = null,
                fylkesnumre = null,
                kommunenumre = null,
                visning = Visning.ALLE,
                side = 1,
                antallPerSide = 25,
                erUtvikler = true,
            )

            assertThat(resultat.treff).extracting("id")
                .contains(egetWorkOp.toString(), andresWorkOp.toString(), egetRekrutteringstreff.toString(), andresRekrutteringstreff.toString())

            val workOpAggregering = resultat.kategoriaggregering.find { it.verdi == SokKategori.WORKOP.name }
            val rekrutteringstreffAggregering =
                resultat.kategoriaggregering.find { it.verdi == SokKategori.REKRUTTERINGSTREFF.name }

            assertThat(workOpAggregering?.antall).isEqualTo(2)
            assertThat(rekrutteringstreffAggregering?.antall).isEqualTo(2)
        }
    }
