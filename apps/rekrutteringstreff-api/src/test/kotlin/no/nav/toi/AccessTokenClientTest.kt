package no.nav.toi

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

@WireMockTest
class AccessTokenClientTest {

    @Test
    fun `gjenbruker token fra cache for samme innkommende token og scope`(wireMock: WireMockRuntimeInfo) {
        stubToken(expiresIn = 3600)
        val klient = accessTokenClient(wireMock)

        val første = klient.hentAccessToken("innkommende-token", "api://a/.default")
        val andre = klient.hentAccessToken("innkommende-token", "api://a/.default")

        assertThat(første).isEqualTo("obo-token")
        assertThat(andre).isEqualTo("obo-token")
        verify(1, postRequestedFor(urlPathEqualTo("/token")))
    }

    @Test
    fun `henter nytt token for ulik scope`(wireMock: WireMockRuntimeInfo) {
        stubToken(expiresIn = 3600)
        val klient = accessTokenClient(wireMock)

        klient.hentAccessToken("innkommende-token", "api://a/.default")
        klient.hentAccessToken("innkommende-token", "api://b/.default")

        verify(2, postRequestedFor(urlPathEqualTo("/token")))
        verify(1, postRequestedFor(urlPathEqualTo("/token")).withRequestBody(containing("api%3A%2F%2Fa%2F.default")))
        verify(1, postRequestedFor(urlPathEqualTo("/token")).withRequestBody(containing("api%3A%2F%2Fb%2F.default")))
    }

    @Test
    fun `henter nytt token for ulikt innkommende token`(wireMock: WireMockRuntimeInfo) {
        stubToken(expiresIn = 3600)
        val klient = accessTokenClient(wireMock)

        klient.hentAccessToken("innkommende-token-1", "api://a/.default")
        klient.hentAccessToken("innkommende-token-2", "api://a/.default")

        verify(2, postRequestedFor(urlPathEqualTo("/token")))
    }

    @Test
    fun `henter nytt token når cachet token er utløpt`(wireMock: WireMockRuntimeInfo) {
        // Klienten trekker fra 10 sekunder, så expires_in = 5 gir en oppføring som allerede er utløpt.
        stubToken(expiresIn = 5)
        val klient = accessTokenClient(wireMock)

        klient.hentAccessToken("innkommende-token", "api://a/.default")
        klient.hentAccessToken("innkommende-token", "api://a/.default")

        verify(2, postRequestedFor(urlPathEqualTo("/token")))
    }

    @Test
    fun `prøver på nytt og kaster feil når token-endepunktet svarer med feilstatus`(wireMock: WireMockRuntimeInfo) {
        stubFor(post(urlPathEqualTo("/token")).willReturn(aResponse().withStatus(500)))
        val klient = accessTokenClient(wireMock)

        assertThatThrownBy { klient.hentAccessToken("innkommende-token", "api://a/.default") }
            .isInstanceOf(RuntimeException::class.java)
            .hasMessageContaining("Noe feil skjedde ved henting av access_token")

        verify(3, postRequestedFor(urlPathEqualTo("/token")))
    }

    private fun stubToken(expiresIn: Long) {
        stubFor(
            post(urlPathEqualTo("/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""{"access_token":"obo-token","expires_in":$expiresIn}""")
                )
        )
    }

    private fun accessTokenClient(wireMock: WireMockRuntimeInfo) = AccessTokenClient(
        secret = "secret",
        clientId = "client-id",
        azureUrl = "${wireMock.httpBaseUrl}/token",
        httpClient = httpClient,
    )
}
