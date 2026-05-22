package no.nav.toi.rekrutteringstreff.tilgangsstyring

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.AccessTokenClient
import no.nav.toi.exception.ModiaOppslagFeiletException
import no.nav.toi.httpClient
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@WireMockTest
class ModiaKlientTest {

    @Test
    fun `hentMineEnheter henter enheter fra Modia decorator med OBO-token`(wireMock: WireMockRuntimeInfo) {
        stubAccessToken()
        stubFor(
            get(urlPathEqualTo("/api/decorator"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {
                              "ident": "A000001",
                              "fornavn": "Test",
                              "etternavn": "Veileder",
                              "enheter": [
                                { "enhetId": "0314", "navn": "Nav Testkontor" },
                                { "enhetId": "9999", "navn": "Nav Annet Testkontor" }
                              ]
                            }
                            """.trimIndent()
                        )
                )
        )

        val klient = modiaKlient(wireMock)

        val resultat = klient.hentMineEnheter("innkommende-token")

        assertThat(resultat).containsExactly("0314", "9999")
        verify(1, postRequestedFor(urlPathEqualTo("/token")))
        verify(
            1,
            getRequestedFor(urlPathEqualTo("/api/decorator"))
                .withHeader("Authorization", equalTo("Bearer obo-token"))
        )
    }

    @Test
    fun `hentMineEnheter returnerer tom liste når Modia decorator svarer 404`(wireMock: WireMockRuntimeInfo) {
        stubAccessToken()
        stubFor(
            get(urlPathEqualTo("/api/decorator"))
                .willReturn(aResponse().withStatus(404))
        )

        val klient = modiaKlient(wireMock)

        val resultat = klient.hentMineEnheter("innkommende-token")

        assertThat(resultat).isEmpty()
        verify(1, postRequestedFor(urlPathEqualTo("/token")))
    }

    @Test
    fun `hentMineEnheter kaster når Modia decorator feiler teknisk`(wireMock: WireMockRuntimeInfo) {
        stubAccessToken()
        stubFor(
            get(urlPathEqualTo("/api/decorator"))
                .willReturn(aResponse().withStatus(500).withBody("Modia ute"))
        )

        val klient = modiaKlient(wireMock)

        assertThatThrownBy { klient.hentMineEnheter("innkommende-token") }
            .isInstanceOf(ModiaOppslagFeiletException::class.java)
            .hasMessage("Klarte ikke å hente Modia-enheter. Prøv igjen senere.")
    }

    private fun stubAccessToken() {
        stubFor(
            post(urlPathEqualTo("/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"obo-token","expires_in":3600}""")
                )
        )
    }

    private fun modiaKlient(wireMock: WireMockRuntimeInfo) = ModiaKlient(
        modiaContextHolderUrl = wireMock.httpBaseUrl,
        modiaContextHolderScope = "api://modia/.default",
        accessTokenClient = AccessTokenClient(
            secret = "secret",
            clientId = "client-id",
            azureUrl = "${wireMock.httpBaseUrl}/token",
            httpClient = httpClient,
        ),
        httpClient = httpClient,
    )
}