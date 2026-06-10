package no.nav.toi.formidling

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import no.nav.toi.AccessTokenClient
import no.nav.toi.JacksonConfig
import no.nav.toi.jobbsoker.Jobbsøker
import no.nav.toi.log
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.*

class KandidatKlient(
    private val kandidatApiUrl: String,
    private val kandidatApiScope: String,
    private val accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient,
    private val objectMapper: ObjectMapper = JacksonConfig.mapper,
) {

    fun leggTilPersonerPåKandidatliste(kandidatlisteId: UUID, stillingId: UUID, jobbsøker: Jobbsøker, navKontorVeileder: String, userToken: String) {
        val onBehalfOfToken = accessTokenClient.hentAccessToken(
            innkommendeToken = userToken,
            scope = kandidatApiScope
        )

        leggTilPersonerPåKandidatlisteMedAccessToken(
            kandidatlisteId = kandidatlisteId,
            stillingId = stillingId,
            jobbsøker = jobbsøker,
            navKontorVeileder = navKontorVeileder,
            accessToken = onBehalfOfToken
        )
    }

    private fun leggTilPersonerPåKandidatlisteMedAccessToken(
        kandidatlisteId: UUID,
        stillingId: UUID,
        jobbsøker: Jobbsøker,
        navKontorVeileder: String,
        accessToken: String
    ) {
        val url = "$kandidatApiUrl/kandidatlister/${
            URLEncoder.encode(
                kandidatlisteId.toString(),
                Charsets.UTF_8.name()
            )
        }/formidlingeravusynligkandidat"

        fun post(requestBodyJson: String): HttpResponse<String> {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $accessToken")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build()
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        val kandidatTilFormidling = FormidlingAvKandidatDto(
            fnr = jobbsøker.fødselsnummer.asString,
            navKontor = navKontorVeileder,
            stillingsId = stillingId.toString(),
        )

        val requestBodyJson = objectMapper.writeValueAsString(kandidatTilFormidling)

        try {
            val response = withRetry { (::post)(requestBodyJson) }
            return when (response.statusCode()) {
                201 -> {
                    log.info("Kandidaten ble lagt til på kandidatliste")
                }

                409 -> {
                    log.info("Kandidaten er allerede lagt til på kandidatlisten")
                }

                else -> {
                    log.error("Kandidaten kunne ikke legges til på kandidatliste ${response.statusCode()}")
                    throw KandidatKlientException("Kandidaten kunne ikke legges til på kandidatliste ${response.statusCode()}")
                }
            }
        } catch (e: KandidatKlientException) {
            throw e
        } catch (e: Exception) {
            log.error("Feil ved kall mot kandidat-api for å legge kandidat på kandidatliste", e)
            throw KandidatKlientException("Feil ved kall mot kandidat-api for å legge kandidat på kandidatliste", e)
        }
    }

    companion object {
        private fun withRetry(fetch: () -> HttpResponse<String>): HttpResponse<String> {
            fun isFailure(response: HttpResponse<String>) = response.statusCode() !in 200..299
            val retryConfig = RetryConfig.custom<HttpResponse<String>>()
                .retryOnResult(::isFailure)
                .build()
            val retry = Retry.of("legg kandidat på kandidatliste", retryConfig)
            val fetchWithRetry = Retry.decorateSupplier(retry, fetch)
            return fetchWithRetry.get()
        }
    }

}

data class FormidlingAvKandidatDto(
    val fnr: String,
    val presentert: Boolean = true,
    val fåttJobb: Boolean = true,
    val navKontor: String,
    val stillingsId: String
)

class KandidatKlientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)