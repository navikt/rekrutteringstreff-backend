package no.nav.toi.kandidatsok

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.core.Headers
import com.github.kittinunf.fuel.core.extensions.authentication
import com.github.kittinunf.fuel.core.extensions.jsonBody
import com.github.kittinunf.fuel.jackson.responseObject
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Kandidatnummer

private data class KandidatKandidatnrRequestDto(val fodselsnummer: String)
private data class KandidatKandidatnrResponsDto(val arenaKandidatnr: String)

class KandidatsøkKlient(
    private val kandidatsokApiUrl: String,
    private val tokenProvider: () -> String,
    private val objectMapper: ObjectMapper = jacksonObjectMapper()
) {
    fun hentKandidatnummer(fødselsnummer: Fødselsnummer): Kandidatnummer? {
        val url = "$kandidatsokApiUrl/api/arena-kandidatnr"
        val requestBody = KandidatKandidatnrRequestDto(fødselsnummer.asString)
        val requestBodyJson = objectMapper.writeValueAsString(requestBody)

        val (_, response, result) = Fuel.post(url)
            .header(Headers.CONTENT_TYPE, "application/json")
            .jsonBody(requestBodyJson)
            .authentication().bearer(tokenProvider())
            .responseObject<KandidatKandidatnrResponsDto>(objectMapper)

        return when (response.statusCode) {
            200 -> result.get().arenaKandidatnr.let(::Kandidatnummer)
            404 -> null
            else -> throw RuntimeException("Kall mot kandidatsok-api feilet med status ${response.statusCode}")
        }
    }
}