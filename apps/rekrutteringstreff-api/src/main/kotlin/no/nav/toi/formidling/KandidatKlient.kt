package no.nav.toi.formidling

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import no.nav.toi.AccessTokenClient
import no.nav.toi.JacksonConfig
import no.nav.toi.jobbsoker.Fødselsnummer
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
        leggTilPersonerPåKandidatlisteMedAccessToken(
            kandidatlisteId = kandidatlisteId,
            stillingId = stillingId,
            jobbsøker = jobbsøker,
            navKontorVeileder = navKontorVeileder,
            accessToken = hentOnBehalfOfToken(userToken)
        )
    }

    fun endreUtfall(
        kandidatlisteId: UUID,
        fødselsnummer: Fødselsnummer,
        utfall: KandidatUtfall,
        navKontorVeileder: String,
        userToken: String
    ) {
        val url = "${kandidatlisteFormidlingUrl(kandidatlisteId)}/utfall"
        val requestBody = FormidlingUtfallDto(
            fnr = fødselsnummer.asString,
            utfall = utfall,
            navKontor = navKontorVeileder,
        )

        sendTilKandidatApi(
            url = url,
            requestBody = requestBody,
            accessToken = hentOnBehalfOfToken(userToken),
            feilmelding = "Feil ved kall mot kandidat-api for å endre utfall for kandidat",
            httpMetode = HttpMetode.PUT,
        ) { response ->
            when (response.statusCode()) {
                200, 201, 204 -> log.info("Utfall ble endret for kandidat på kandidatliste")
                else -> {
                    log.error("Utfall kunne ikke endres for kandidat ${response.statusCode()}")
                    throw KandidatKlientException("Utfall kunne ikke endres for kandidat ${response.statusCode()}")
                }
            }
        }
    }

    private fun leggTilPersonerPåKandidatlisteMedAccessToken(
        kandidatlisteId: UUID,
        stillingId: UUID,
        jobbsøker: Jobbsøker,
        navKontorVeileder: String,
        accessToken: String
    ) {
        val url = kandidatlisteFormidlingUrl(kandidatlisteId)

        val kandidatTilFormidling = FormidlingAvKandidatDto(
            fnr = jobbsøker.fødselsnummer.asString,
            navKontor = navKontorVeileder,
            stillingsId = stillingId.toString(),
        )

        sendTilKandidatApi(
            url = url,
            requestBody = kandidatTilFormidling,
            accessToken = accessToken,
            feilmelding = "Feil ved kall mot kandidat-api for å legge kandidat på kandidatliste",
        ) { response ->
            when (response.statusCode()) {
                201 -> log.info("Kandidaten ble lagt til på kandidatliste")
                409 -> log.info("Kandidaten er allerede lagt til på kandidatlisten")
                else -> {
                    log.error("Kandidaten kunne ikke legges til på kandidatliste ${response.statusCode()}")
                    throw KandidatKlientException("Kandidaten kunne ikke legges til på kandidatliste ${response.statusCode()}")
                }
            }
        }
    }

    private fun hentOnBehalfOfToken(userToken: String): String =
        accessTokenClient.hentAccessToken(
            innkommendeToken = userToken,
            scope = kandidatApiScope
        )

    private fun kandidatlisteFormidlingUrl(kandidatlisteId: UUID): String =
        "$kandidatApiUrl/kandidatlister/${
            URLEncoder.encode(
                kandidatlisteId.toString(),
                Charsets.UTF_8.name()
            )
        }/formidlingeravusynligkandidat"

    enum class HttpMetode { POST, PUT }

    /**
     * Sender en POST/PUT mot kandidat-api med retry og felles feilhåndtering.
     */
    private fun sendTilKandidatApi(
        url: String,
        requestBody: Any,
        accessToken: String,
        feilmelding: String,
        httpMetode: HttpMetode = HttpMetode.POST,
        håndterRespons: (HttpResponse<String>) -> Unit,
    ) {
        val requestBodyJson = objectMapper.writeValueAsString(requestBody)
        val bodyPublisher = HttpRequest.BodyPublishers.ofString(requestBodyJson)

        fun send(): HttpResponse<String> {
            val builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $accessToken")
            val request = when (httpMetode) {
                HttpMetode.POST -> builder.POST(bodyPublisher)
                HttpMetode.PUT -> builder.PUT(bodyPublisher)
            }.build()
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        }

        try {
            val response = withRetry(::send)
            håndterRespons(response)
        } catch (e: KandidatKlientException) {
            throw e
        } catch (e: Exception) {
            log.error(feilmelding, e)
            throw KandidatKlientException(feilmelding, e)
        }
    }

    companion object {
        private fun withRetry(fetch: () -> HttpResponse<String>): HttpResponse<String> {
            fun børRekjøres(response: HttpResponse<String>): Boolean {
                val status = response.statusCode()
                return status == 429 || status in 500..599
            }
            val retryConfig = RetryConfig.custom<HttpResponse<String>>()
                .retryOnResult(::børRekjøres)
                .build()
            val retry = Retry.of("kall mot kandidat-api", retryConfig)
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

data class FormidlingUtfallDto(
    val fnr: String,
    val utfall: KandidatUtfall,
    val navKontor: String,
)

enum class KandidatUtfall {
    IKKE_PRESENTERT, PRESENTERT, FATT_JOBBEN
}

class KandidatKlientException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
