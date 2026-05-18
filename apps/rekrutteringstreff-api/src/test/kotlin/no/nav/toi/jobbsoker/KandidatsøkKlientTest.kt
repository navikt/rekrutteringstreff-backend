package no.nav.toi.kandidatsok

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.containing
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.verify
import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo
import com.github.tomakehurst.wiremock.junit5.WireMockTest
import no.nav.toi.AccessTokenClient
import no.nav.toi.httpClient
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Innsatsgruppe
import no.nav.toi.jobbsoker.Navkontor
import no.nav.toi.jobbsoker.VeilederNavIdent
import no.nav.toi.jobbsoker.VeilederNavn
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@WireMockTest
class KandidatsøkKlientTest {

    @Test
    fun `henter jobbsøkerinfo i batcher på maks 200 fødselsnumre`(wireMock: WireMockRuntimeInfo) {
        val fødselsnumre = (1..201).map { Fødselsnummer(it.toString().padStart(11, '0')) }
        val sisteFnrIFørsteBatch = fødselsnumre[199]
        val førsteFnrIAndreBatch = fødselsnumre[200]
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
                .withRequestBody(containing(sisteFnrIFørsteBatch.asString))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {"jobbsokerInfo":[
                              {
                                "fodselsnummer": "${sisteFnrIFørsteBatch.asString}",
                                "navkontor": "Nav Oslo",
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
        stubFor(
            post(urlPathEqualTo("/api/jobbsoker-info"))
                .withRequestBody(containing(førsteFnrIAndreBatch.asString))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """
                            {"jobbsokerInfo":[
                              {
                                "fodselsnummer": "${førsteFnrIAndreBatch.asString}",
                                "navkontor": "Nav Bergen",
                                "veilederNavn": "Annen Veileder",
                                "veilederNavIdent": "Z000002",
                                "alder": 42,
                                "innsatsgruppe": "STANDARD_INNSATS"
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

        assertThat(resultat[sisteFnrIFørsteBatch]).isEqualTo(
            JobbsokerInfo(
                navkontor = Navkontor("Nav Oslo"),
                veilederNavn = VeilederNavn("Test Veileder"),
                veilederNavIdent = VeilederNavIdent("Z000001"),
                alder = 35,
                innsatsgruppe = Innsatsgruppe("SITUASJONSBESTEMT_INNSATS"),
            )
        )
        assertThat(resultat[førsteFnrIAndreBatch]).isEqualTo(
            JobbsokerInfo(
                navkontor = Navkontor("Nav Bergen"),
                veilederNavn = VeilederNavn("Annen Veileder"),
                veilederNavIdent = VeilederNavIdent("Z000002"),
                alder = 42,
                innsatsgruppe = Innsatsgruppe("STANDARD_INNSATS"),
            )
        )
        verify(2, postRequestedFor(urlPathEqualTo("/api/jobbsoker-info")))
        verify(1, postRequestedFor(urlPathEqualTo("/token")))
        verify(
            1,
            postRequestedFor(urlPathEqualTo("/token"))
                .withRequestBody(containing("grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Ajwt-bearer"))
                .withRequestBody(containing("client_id=client-id"))
                .withRequestBody(containing("assertion=$innkommendeToken"))
                .withRequestBody(containing("scope=api%3A%2F%2Fkandidatsok%2F.default"))
                .withRequestBody(containing("requested_token_use=on_behalf_of"))
        )
    }
}
