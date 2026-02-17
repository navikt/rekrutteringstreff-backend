package no.nav.toi.minside.rekrutteringstreff

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.toi.minside.JacksonConfig
import no.nav.toi.minside.TokenXKlient
import no.nav.toi.minside.arbeidsgiver.ArbeidsgiverOutboundDto
import no.nav.toi.minside.innlegg.InnleggOutboundDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.ZonedDateTime
import java.util.UUID

class RekrutteringstreffKlient(
    private val url: String,
    private val tokenXKlient: TokenXKlient,
    private val rekrutteringstreffAudience: String,
    private val httpClient: HttpClient
) {

    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    fun hent(id: String, innkommendeToken: String): Rekrutteringstreff? {
        log.info("url : $url/api/rekrutteringstreff/$id")
        val request = HttpRequest.newBuilder()
            .uri(URI("$url/api/rekrutteringstreff/$id"))
            .header(
                "Authorization",
                "Bearer ${tokenXKlient.onBehalfOfTokenX(innkommendeToken, rekrutteringstreffAudience)}"
            )
            .header("Content-Type", "application/json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return when (response.statusCode()) {
            200 -> JacksonConfig.mapper.readValue(response.body(), Rekrutteringstreff::class.java)
            404 -> null
            else -> throw RuntimeException("Feil ved henting av rekrutteringstreff med id $id. Status: ${response.statusCode()}, body: ${response.body()}")
        }
    }

    fun hentArbeidsgivere(id: String, innkommendeToken: String): List<Arbeidsgiver>? {
        val request = HttpRequest.newBuilder()
            .uri(URI("$url/api/rekrutteringstreff/$id/arbeidsgiver"))
            .header(
                "Authorization",
                "Bearer ${tokenXKlient.onBehalfOfTokenX(innkommendeToken, rekrutteringstreffAudience)}"
            )
            .header("Content-Type", "application/json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return when (response.statusCode()) {
            200 -> JacksonConfig.mapper.readValue(response.body(), object : TypeReference<List<Arbeidsgiver>>() {})
            404 -> null
            else -> throw RuntimeException("Feil ved henting av arbeidsgivere for rekrutteringstreff med id $id. Status: ${response.statusCode()}, body: ${response.body()}")
        }
    }

    fun hentInnlegg(id: String, innkommendeToken: String): List<Innlegg>? {
        val request = HttpRequest.newBuilder()
            .uri(URI("$url/api/rekrutteringstreff/$id/innlegg"))
            .header(
                "Authorization",
                "Bearer ${tokenXKlient.onBehalfOfTokenX(innkommendeToken, rekrutteringstreffAudience)}"
            )
            .header("Content-Type", "application/json")
            .GET()
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        return when (response.statusCode()) {
            200 -> JacksonConfig.mapper.readValue(response.body(), object : TypeReference<List<Innlegg>>() {})
            404 -> null
            else -> throw RuntimeException("Feil ved henting av innlegg for rekrutteringstreff med id $id. Status: ${response.statusCode()}, body: ${response.body()}")
        }
    }

}

data class Rekrutteringstreff(
    private val id: UUID,
    private val tittel: String,
    private val beskrivelse: String?,
    private val fraTid: ZonedDateTime?,
    private val tilTid: ZonedDateTime?,
    private val svarfrist: ZonedDateTime?,
    private val gateadresse: String?,
    private val postnummer: String?,
    private val poststed: String?,
    private val status: String?,
) {
    fun tilDTOForBruker() = RekrutteringstreffOutboundDto(
        id,
        tittel,
        beskrivelse,
        fraTid,
        tilTid,
        svarfrist,
        gateadresse,
        postnummer,
        poststed,
        status
    )
}

data class Arbeidsgiver(
    private val organisasjonsnummer: String,
    private val navn: String,
) {
    fun tilDTOForBruker() = ArbeidsgiverOutboundDto(organisasjonsnummer, navn)
}

data class Innlegg(
    val tittel: String,
    val htmlContent: String
) {
    fun tilDTOForBruker() = InnleggOutboundDto(tittel, htmlContent)
}

