package no.nav.toi

import no.nav.toi.rekrutteringstreff.TestDatabase
import no.nav.toi.ubruktPortnrFra10000.ubruktPortnr
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.net.HttpURLConnection.HTTP_UNAUTHORIZED
import java.util.UUID
import java.util.stream.Stream

/**
 * Verifiserer at ALLE publiserte endepunkt returnerer 401 når forespørselen
 * mangler gyldig token. Testen bruker dummy-verdier i path-parametre fordi
 * autentiserings-filteret avviser forespørselen før handleren kjøres.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AutentiseringAlleEndepunkterTest {

    private val appPort = ubruktPortnr()
    private val database = TestDatabase()
    private val infra = TestInfrastructureContext(dataSource = database.dataSource)
    private lateinit var app: App

    @BeforeAll
    fun setUp() {
        infra.start()
        app = App(ctx = ApplicationContext(infra), port = appPort).also { it.start() }
    }

    @AfterAll
    fun tearDown() {
        infra.stop()
        app.close()
    }

    private val dummyId = UUID.randomUUID().toString()
    private val dummyId2 = UUID.randomUUID().toString()

    enum class Metode { GET, POST, PUT, DELETE }

    enum class Endepunkt(val metode: Metode, val path: String) {
        // RekrutteringstreffController
        OpprettRekrutteringstreff(Metode.POST, "/api/rekrutteringstreff"),
        HentRekrutteringstreff(Metode.GET, "/api/rekrutteringstreff/{id}"),
        OppdaterRekrutteringstreff(Metode.PUT, "/api/rekrutteringstreff/{id}"),
        SlettRekrutteringstreff(Metode.DELETE, "/api/rekrutteringstreff/{id}"),
        HentHendelser(Metode.GET, "/api/rekrutteringstreff/{id}/hendelser"),
        HentAlleHendelser(Metode.GET, "/api/rekrutteringstreff/{id}/allehendelser"),
        PubliserRekrutteringstreff(Metode.POST, "/api/rekrutteringstreff/{id}/publiser"),
        GjenåpneRekrutteringstreff(Metode.POST, "/api/rekrutteringstreff/{id}/gjenapn"),
        AvlysRekrutteringstreff(Metode.POST, "/api/rekrutteringstreff/{id}/avlys"),
        AvpubliserRekrutteringstreff(Metode.POST, "/api/rekrutteringstreff/{id}/avpubliser"),
        FullførRekrutteringstreff(Metode.POST, "/api/rekrutteringstreff/{id}/fullfor"),
        RegistrerEndring(Metode.POST, "/api/rekrutteringstreff/{id}/endringer"),

        // RekrutteringstreffSokController
        SøkRekrutteringstreff(Metode.GET, "/api/rekrutteringstreff/sok"),

        // ArbeidsgiverController
        LeggTilArbeidsgiver(Metode.POST, "/api/rekrutteringstreff/{id}/arbeidsgiver"),
        LeggTilArbeidsgiverMedBehov(Metode.POST, "/api/rekrutteringstreff/{id}/arbeidsgiver-med-behov"),
        HentArbeidsgivere(Metode.GET, "/api/rekrutteringstreff/{id}/arbeidsgiver"),
        HentArbeidsgivereMedBehov(Metode.GET, "/api/rekrutteringstreff/{id}/arbeidsgiver-med-behov"),
        HentArbeidsgiverHendelser(Metode.GET, "/api/rekrutteringstreff/{id}/arbeidsgiver/hendelser"),
        SlettArbeidsgiver(Metode.DELETE, "/api/rekrutteringstreff/{id}/arbeidsgiver/{arbeidsgiverId}"),
        OppdaterBehov(Metode.PUT, "/api/rekrutteringstreff/{id}/arbeidsgiver/{arbeidsgiverId}/behov"),
        HentBehovMetadata(Metode.GET, "/api/rekrutteringstreff/arbeidsgiver-behov-metadata"),

        // InnleggController
        HentAlleInnlegg(Metode.GET, "/api/rekrutteringstreff/{id}/innlegg"),
        HentEttInnlegg(Metode.GET, "/api/rekrutteringstreff/{id}/innlegg/{innleggId}"),
        OpprettInnlegg(Metode.POST, "/api/rekrutteringstreff/{id}/innlegg"),
        OppdaterInnlegg(Metode.PUT, "/api/rekrutteringstreff/{id}/innlegg/{innleggId}"),
        SlettInnlegg(Metode.DELETE, "/api/rekrutteringstreff/{id}/innlegg/{innleggId}"),

        // EierController
        HentEiere(Metode.GET, "/api/rekrutteringstreff/{id}/eiere"),
        LeggTilMeg(Metode.PUT, "/api/rekrutteringstreff/{id}/eiere/meg"),
        SlettEier(Metode.DELETE, "/api/rekrutteringstreff/{id}/eiere/{navIdent}"),

        // JobbsøkerController
        LeggTilJobbsøkere(Metode.POST, "/api/rekrutteringstreff/{id}/jobbsoker"),
        SøkJobbsøkere(Metode.POST, "/api/rekrutteringstreff/{id}/jobbsoker/sok"),
        SlettJobbsøker(Metode.DELETE, "/api/rekrutteringstreff/{id}/jobbsoker/{jobbsokerid}/slett"),
        SvarForJobbsøker(Metode.POST, "/api/rekrutteringstreff/{id}/jobbsoker/{jobbsokerid}/svar"),
        HentJobbsøkerHendelser(Metode.GET, "/api/rekrutteringstreff/{id}/jobbsoker/hendelser"),
        InviterJobbsøkere(Metode.POST, "/api/rekrutteringstreff/{id}/jobbsoker/inviter"),
        FormidlingEgne(Metode.POST, "/api/rekrutteringstreff/{id}/jobbsoker/formidling/egne"),
        FormidlingAlle(Metode.POST, "/api/rekrutteringstreff/{id}/jobbsoker/formidling/alle"),

        // JobbsøkerInnloggetBorgerController
        BorgerSvarJa(Metode.POST, "/api/rekrutteringstreff/{id}/jobbsoker/borger/svar-ja"),
        BorgerSvarNei(Metode.POST, "/api/rekrutteringstreff/{id}/jobbsoker/borger/svar-nei"),
        HentBorgerJobbsøker(Metode.GET, "/api/rekrutteringstreff/{id}/jobbsoker/borger"),

        // JobbsøkerOutboundController
        HentKandidatnummer(Metode.GET, "/api/rekrutteringstreff/{id}/jobbsoker/{jobbsokerid}/kandidatnummer"),

        // KiController
        KiValider(Metode.POST, "/api/rekrutteringstreff/{id}/ki/valider"),
        KiOppdaterLagret(Metode.PUT, "/api/rekrutteringstreff/{id}/ki/logg/{loggId}/lagret"),
        KiOppdaterManuell(Metode.PUT, "/api/rekrutteringstreff/{id}/ki/logg/{loggId}/manuell"),
        KiListLogg(Metode.GET, "/api/rekrutteringstreff/{id}/ki/logg"),
        KiGetLogg(Metode.GET, "/api/rekrutteringstreff/{id}/ki/logg/{loggId}"),

        // Formidling controller
        OpprettFormidling(Metode.POST, "/api/rekrutteringstreff/{id}/formidling"),
        HentAlleFormidlinger(Metode.GET, "/api/rekrutteringstreff/{id}/formidling/liste/alle"),
        HentEgneFormidlinger(Metode.GET, "/api/rekrutteringstreff/{id}/formidling/liste/egne"),
        SlettFormidling(Metode.DELETE, "/api/rekrutteringstreff/{id}/formidling/{formidlingId}"),
    }

    private fun url(endepunkt: Endepunkt): String {
        val path = endepunkt.path
            .replace("{id}", dummyId)
            .replace("{treffId}", dummyId)
            .replace("{arbeidsgiverId}", dummyId2)
            .replace("{innleggId}", dummyId2)
            .replace("{formidlingId}", dummyId2)
            .replace("{jobbsokerid}", dummyId2)
            .replace("{personTreffId}", dummyId2)
            .replace("{navIdent}", "A999999")
            .replace("{loggId}", dummyId2)
        return "http://localhost:$appPort$path"
    }

    private fun uautentifiserteCases(): Stream<Arguments> =
        Endepunkt.entries.flatMap { endepunkt ->
            UautentifiserendeTestCase.entries.map { tokenCase ->
                Arguments.of(endepunkt, tokenCase)
            }
        }.stream()

    @ParameterizedTest(name = "{0} med {1} → 401")
    @MethodSource("uautentifiserteCases")
    fun `endepunkt returnerer 401 uten gyldig token`(endepunkt: Endepunkt, tokenCase: UautentifiserendeTestCase) {
        val fullUrl = url(endepunkt)

        val response = when (endepunkt.metode) {
            Metode.GET -> tokenCase.utførGet(fullUrl, infra.authServer, infra.authPort)
            Metode.POST -> tokenCase.utførPost(fullUrl, "{}", infra.authServer, infra.authPort)
            Metode.PUT -> tokenCase.utførPut(fullUrl, "{}", infra.authServer, infra.authPort)
            Metode.DELETE -> tokenCase.utførDelete(fullUrl, infra.authServer, infra.authPort)
        }

        assertThat(response.statusCode())
            .withFailMessage("Forventet 401 for ${endepunkt.name} (${endepunkt.metode} ${endepunkt.path}) med $tokenCase, men fikk ${response.statusCode()}")
            .isEqualTo(HTTP_UNAUTHORIZED)
    }
}
