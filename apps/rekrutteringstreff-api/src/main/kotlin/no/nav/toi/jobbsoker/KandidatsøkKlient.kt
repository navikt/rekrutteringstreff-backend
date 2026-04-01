package no.nav.toi.kandidatsok

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AccessTokenClient
import no.nav.toi.JacksonConfig
import no.nav.toi.exception.KandidatsokOppslagFeiletException
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Kandidatnummer
import no.nav.toi.log
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private data class KandidatKandidatnrRequestDto(val fodselsnummer: String)
private data class KandidatKandidatnrResponsDto(val arenaKandidatnr: String)
private data class MultipleLookupCvRequestDto(val fodselsnummer: List<String>)

data class KandidatBerikelse(
    val navkontor: String? = null,
    val veilederNavn: String? = null,
    val veilederNavIdent: String? = null,
    val innsatsgruppe: String? = null,
    val fylke: String? = null,
    val kommune: String? = null,
    val poststed: String? = null,
    val telefonnummer: String? = null,
)

class KandidatsøkKlient(
    private val kandidatsokApiUrl: String,
    private val kandidatsokScope: String,
    private val accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val objectMapper: ObjectMapper = JacksonConfig.mapper
) {
    fun erKonfigurert(): Boolean = kandidatsokApiUrl.isNotBlank() && kandidatsokScope.isNotBlank()

    fun hentKandidatnummer(fødselsnummer: Fødselsnummer, userToken: String): Kandidatnummer? {
        val onBehalfOfToken = hentOnBehalfOfToken(userToken)
        return hentKandidatnummerMedAccessToken(fødselsnummer, onBehalfOfToken)
    }

    fun hentKandidatdata(
        fødselsnumre: List<Fødselsnummer>,
        userToken: String,
    ): Map<String, KandidatBerikelse> {
        if (fødselsnumre.isEmpty()) {
            return emptyMap()
        }

        val onBehalfOfToken = try {
            hentOnBehalfOfToken(userToken)
        } catch (e: Exception) {
            throw KandidatsokOppslagFeiletException(
                "Klarte ikke å hente data fra kandidatsøk.",
                e,
            )
        }

        val unikeFødselsnumre = fødselsnumre.distinct()
        return hentCvData(unikeFødselsnumre, onBehalfOfToken)
    }

    private fun hentKandidatnummerMedAccessToken(
        fødselsnummer: Fødselsnummer,
        onBehalfOfToken: String,
    ): Kandidatnummer? {
        log.info("Henter kandidatnummer fra kandidatsøk-api")
        val url = "$kandidatsokApiUrl/api/arena-kandidatnr"
        val requestBody = KandidatKandidatnrRequestDto(fødselsnummer.asString)
        val requestBodyJson = objectMapper.writeValueAsString(requestBody)

        try {
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
                    throw KandidatsokOppslagFeiletException("Klarte ikke å hente kandidatnummer fra kandidatsøk.")
                }
            }
        } catch (e: KandidatsokOppslagFeiletException) {
            throw e
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved henting av kandidatnummer fra kandidatsøk-api", e)
            throw KandidatsokOppslagFeiletException("Klarte ikke å hente kandidatnummer fra kandidatsøk.", e)
        }
    }

    private fun hentCvData(
        fødselsnumre: List<Fødselsnummer>,
        onBehalfOfToken: String,
    ): Map<String, KandidatBerikelse> {
        log.info("Henter kandidatdata fra kandidatsøk-api for ${fødselsnumre.size} kandidater")
        val url = "$kandidatsokApiUrl/api/multiple-lookup-cv"
        val requestBodyJson = objectMapper.writeValueAsString(
            MultipleLookupCvRequestDto(fødselsnumre.map { it.asString })
        )

        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $onBehalfOfToken")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

            return when (response.statusCode()) {
                200 -> parseKandidatdata(response.body())
                404 -> emptyMap()
                else -> {
                    log.error("Det skjedde en feil ved henting av kandidatdata fra kandidatsøk-api. status: ${response.statusCode()}")
                    throw KandidatsokOppslagFeiletException("Klarte ikke å hente kandidatdata fra kandidatsøk.")
                }
            }
        } catch (e: KandidatsokOppslagFeiletException) {
            throw e
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved henting av kandidatdata fra kandidatsøk-api", e)
            throw KandidatsokOppslagFeiletException("Klarte ikke å hente kandidatdata fra kandidatsøk.", e)
        }
    }

    private fun parseKandidatdata(responseBody: String): Map<String, KandidatBerikelse> {
        val responseJson = objectMapper.readTree(responseBody)
        val hits = responseJson.path("hits").path("hits")

        if (!hits.isArray) {
            return emptyMap()
        }

        return hits.mapNotNull { hit ->
            val source = hit.path("_source")
            val fødselsnummer = source.textOrNull("fodselsnummer") ?: return@mapNotNull null

            fødselsnummer to KandidatBerikelse(
                navkontor = source.textOrNull("navkontor"),
                veilederNavn = source.textOrNull("veilederVisningsnavn"),
                veilederNavIdent = source.textOrNull("veilederIdent"),
                innsatsgruppe = source.textOrNull("innsatsgruppe"),
                fylke = source.textOrNull("fylkeNavn"),
                kommune = source.textOrNull("kommuneNavn"),
                poststed = source.textOrNull("poststed"),
                telefonnummer = source.textOrNull("mobiltelefon") ?: source.textOrNull("telefon"),
            )
        }.toMap()
    }

    private fun hentOnBehalfOfToken(userToken: String): String =
        accessTokenClient.hentAccessToken(innkommendeToken = userToken, scope = kandidatsokScope)

    private fun JsonNode.textOrNull(field: String): String? =
        get(field)
            ?.takeUnless { it.isNull || it.isMissingNode }
            ?.asText()
            ?.takeIf { it.isNotBlank() }
}
