package no.nav.toi.kandidatsok

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AccessTokenClient
import no.nav.toi.JacksonConfig
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Kandidatnummer
import no.nav.toi.log
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private data class KandidatKandidatnrRequestDto(val fodselsnummer: String)
private data class KandidatKandidatnrResponsDto(val arenaKandidatnr: String)

class KandidatsøkKlient(
    private val kandidatsokApiUrl: String,
    private val kandidatsokScope: String,
    private val accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val objectMapper: ObjectMapper = JacksonConfig.mapper
) {
    fun hentKandidatnummer(fødselsnummer: Fødselsnummer, userToken: String): Kandidatnummer? {
        log.info("Henter kandidatnummer fra kandidatsøk-api")
        val url = "$kandidatsokApiUrl/api/arena-kandidatnr"
        val requestBody = KandidatKandidatnrRequestDto(fødselsnummer.asString)
        val requestBodyJson = objectMapper.writeValueAsString(requestBody)

        try {
            val onBehalfOfToken = accessTokenClient.hentAccessToken(innkommendeToken = userToken, scope = kandidatsokScope)

            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $onBehalfOfToken")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            return when (response.statusCode()) {
                200 -> objectMapper.readValue(response.body(), KandidatKandidatnrResponsDto::class.java).arenaKandidatnr.let(::Kandidatnummer)
                404 -> null
                else -> {
                    log.error("Det skjedde en feil ved henting av kandidatnummer fra kandidatsøk-api. status: ${response.statusCode()}")
                    throw RuntimeException("Kall mot kandidatsok-api feilet med status ${response.statusCode()}")
                }
            }
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved henting av kandidatnummer fra kandidatsøk-api", e)
            throw e
        }
    }
}