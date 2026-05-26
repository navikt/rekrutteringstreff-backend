package no.nav.toi.kandidatsok

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.AccessTokenClient
import no.nav.toi.exception.KandidatsokTilgangAvvistException
import no.nav.toi.httpClient
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Innsatsgruppe
import no.nav.toi.jobbsoker.Kontor
import no.nav.toi.jobbsoker.VeilederNavIdent
import no.nav.toi.jobbsoker.VeilederNavn
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@WireMockTest
class KandidatsøkKlientTest {

    @Test
    fun `henter jobbsøkerinfo og mapper manglende rader til tom info`(wireMock: WireMockRuntimeInfo) {
        val fødselsnumre = (1..2).map { Fødselsnummer(it.toString().padStart(11, '0')) }
        val sisteFnr = fødselsnumre.last()
        val innkommendeToken = "innkommende-token"

        stubFor(
            post(urlPathEqualTo("/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"obo-token","expires_in":3600}""")
                )
        )
        stubFor(
            post(urlPathEqualTo("/api/jobbsoker-info"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {"jobbsokerInfo":[
                              {
                                                                "fodselsnummer": "${sisteFnr.asString}",
                                "kontornavn": "Nav Oslo",
                                "kontornummer": "1000",
                                "veilederNavn": "Test Veileder",
                                "veilederNavIdent": "Z000001",
                                "alder": 35,
                                "innsatsgruppe": "SITUASJONSBESTEMT_INNSATS"
                              }
                            ]}
                            """.trimIndent()
                        )
                )
        )

        val klient = KandidatsøkKlient(
            kandidatsokApiUrl = wireMock.httpBaseUrl,
            kandidatsokScope = "api://kandidatsok/.default",
            accessTokenClient = AccessTokenClient(
                secret = "secret",
                clientId = "client-id",
                azureUrl = "${wireMock.httpBaseUrl}/token",
                httpClient = httpClient,
            ),
            httpClient = httpClient,
        )

        val resultat = klient.hentJobbsokerInfo(fødselsnumre, innkommendeToken)

        assertThat(resultat).hasSize(1)
        assertThat(resultat[fødselsnumre.first()]).isNull()
        assertThat(resultat[sisteFnr]).isEqualTo(
            JobbsokerInfo(
                kontor = Kontor(kontornummer = "1000", kontornavn = "Nav Oslo"),
                veilederNavn = VeilederNavn("Test Veileder"),
                veilederNavIdent = VeilederNavIdent("Z000001"),
                alder = 35,
                innsatsgruppe = Innsatsgruppe("SITUASJONSBESTEMT_INNSATS"),
            )
        )
        verify(1, postRequestedFor(urlPathEqualTo("/api/jobbsoker-info")))
        verify(1, postRequestedFor(urlPathEqualTo("/token")))
    }

    @Test
    fun `henter jobbsøkerinfo når kandidatsøk returnerer lowercase veilederident`(wireMock: WireMockRuntimeInfo) {
        val fødselsnummer = Fødselsnummer("11111111111")
        val innkommendeToken = "innkommende-token"

        stubFor(
            post(urlPathEqualTo("/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"obo-token","expires_in":3600}""")
                )
        )
        stubFor(
            post(urlPathEqualTo("/api/jobbsoker-info"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {"jobbsokerInfo":[
                              {
                                "fodselsnummer": "${fødselsnummer.asString}",
                                "kontornavn": "Nav Oslo",
                                "kontornummer": "1000",
                                "veilederNavn": "Test Veileder",
                                "veilederNavIdent": "z993798",
                                "alder": 35,
                                "innsatsgruppe": "SITUASJONSBESTEMT_INNSATS"
                              }
                            ]}
                            """.trimIndent()
                        )
                )
        )

        val klient = KandidatsøkKlient(
            kandidatsokApiUrl = wireMock.httpBaseUrl,
            kandidatsokScope = "api://kandidatsok/.default",
            accessTokenClient = AccessTokenClient(
                secret = "secret",
                clientId = "client-id",
                azureUrl = "${wireMock.httpBaseUrl}/token",
                httpClient = httpClient,
            ),
            httpClient = httpClient,
        )

        val resultat = klient.hentJobbsokerInfo(listOf(fødselsnummer), innkommendeToken)

        assertThat(resultat[fødselsnummer]?.veilederNavIdent).isEqualTo(VeilederNavIdent("Z993798"))
    }

    @Test
    fun `henting av jobbsøkerinfo kaster tilgangsavvist ved 403 fra kandidatsøk`(wireMock: WireMockRuntimeInfo) {
        val fødselsnumre = listOf(Fødselsnummer("11111111111"))
        val innkommendeToken = "innkommende-token"

        stubFor(
            post(urlPathEqualTo("/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"obo-token","expires_in":3600}""")
                )
        )
        stubFor(
            post(urlPathEqualTo("/api/jobbsoker-info"))
                .willReturn(
                    aResponse()
                        .withStatus(403)
                )
        )

        val klient = KandidatsøkKlient(
            kandidatsokApiUrl = wireMock.httpBaseUrl,
            kandidatsokScope = "api://kandidatsok/.default",
            accessTokenClient = AccessTokenClient(
                secret = "secret",
                clientId = "client-id",
                azureUrl = "${wireMock.httpBaseUrl}/token",
                httpClient = httpClient,
            ),
            httpClient = httpClient,
        )

        assertThatThrownBy { klient.hentJobbsokerInfo(fødselsnumre, innkommendeToken) }
            .isInstanceOf(KandidatsokTilgangAvvistException::class.java)

        verify(1, postRequestedFor(urlPathEqualTo("/api/jobbsoker-info")))
        verify(1, postRequestedFor(urlPathEqualTo("/token")))
    }
}
