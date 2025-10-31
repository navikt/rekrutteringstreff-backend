package no.nav.toi.kandidatsok

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.jackson.responseObject
import no.nav.toi.AccessTokenClient
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Kandidatnummer
import no.nav.toi.log

private data class KandidatKandidatnrRequestDto(val fodselsnummer: String)
private data class KandidatKandidatnrResponsDto(val arenaKandidatnr: String)

class KandidatsøkKlient(
    private val kandidatsokApiUrl: String,
    private val kandidatsokScope: String,
    private val accessTokenClient: AccessTokenClient,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {
    fun hentKandidatnummer(fødselsnummer: Fødselsnummer, userToken: String): Kandidatnummer? {
        log.info("Henter kandidatnummer fra kandidatsøkApi")
        val url = "$kandidatsokApiUrl/api/arena-kandidatnr"
        val requestBody = KandidatKandidatnrRequestDto(fødselsnummer.asString)
        val requestBodyJson = objectMapper.writeValueAsString(requestBody)

        try {
            val onBehalfOfToken = accessTokenClient.hentAccessToken(innkommendeToken = userToken, scope = kandidatsokScope)

            val (_, response, result) = Fuel.post(url)
                .header(Headers.CONTENT_TYPE, "application/json")
                .jsonBody(requestBodyJson)
                .authentication().bearer(onBehalfOfToken)
                .responseObject<KandidatKandidatnrResponsDto>(objectMapper)


            return when (response.statusCode) {
                200 -> result.get().arenaKandidatnr.let(::Kandidatnummer)
                404 -> null
                else -> {
                    log.error("Det skjedde en feil ved henting av kandidatnummer fra kandidatsøk-api. status: ${response.statusCode}")
                    throw RuntimeException("Kall mot kandidatsok-api feilet med status ${response.statusCode}")
                }
            }
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved henting av kandidatnummer fra kandidatsøk-api", e)
            throw e
        }
    }
}