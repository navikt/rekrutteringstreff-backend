package no.nav.toi.minside.rekrutteringstreff

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import io.javalin.http.Header
import no.nav.toi.minside.JacksonConfig
import no.nav.toi.minside.TokenXKlient
import no.nav.toi.minside.arbeidsgiver.ArbeidsgiverOutboundDto
import no.nav.toi.minside.innlegg.InnleggOutboundDto
import java.time.ZonedDateTime
import java.util.UUID

class RekrutteringstreffKlient(private val url: String, private val tokenXKlient: TokenXKlient, private val rekrutteringstreffAudience: String) {
    fun hent(id: String, innkommendeToken: String): RekrutteringstreffDetaljDto? {
        val (_, response, result) = "$url/api/rekrutteringstreff/$id".httpGet()
            .header(Header.AUTHORIZATION, "Bearer ${tokenXKlient.onBehalfOfTokenX(innkommendeToken, rekrutteringstreffAudience)}")
            .responseObject<RekrutteringstreffDetaljDto>(JacksonConfig.mapper)

        return when (result) {
            is Failure -> throw result.error
            is Success -> {
                if (response.statusCode == 404) return null
                else result.value
            }
        }
    }

    fun hentArbeidsgivere(id: String, innkommendeToken: String): List<Arbeidsgiver>? {
        val (_, response, result) = "$url/api/rekrutteringstreff/$id/arbeidsgiver".httpGet()
            .header(Header.AUTHORIZATION, "Bearer ${tokenXKlient.onBehalfOfTokenX(innkommendeToken, rekrutteringstreffAudience)}")
            .responseObject<List<Arbeidsgiver>>()

        return when (result) {
            is Failure -> throw result.error
            is Success -> {
                if (response.statusCode == 404) return null
                else result.value
            }
        }
    }

     fun hentInnlegg(id: String, innkommendeToken: String): List<Innlegg>? {
        val (_, response, result) = "$url/api/rekrutteringstreff/$id/innlegg".httpGet()
            .header(Header.AUTHORIZATION, "Bearer ${tokenXKlient.onBehalfOfTokenX(innkommendeToken, rekrutteringstreffAudience)}")
            .responseObject<List<Innlegg>>()

        return when (result) {
            is Failure -> throw result.error
            is Success -> {
                if (response.statusCode == 404) return null
                else result.value
            }
        }
    }

}

data class RekrutteringstreffDetaljDto(
    val rekrutteringstreff: Rekrutteringstreff
)

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
    fun tilDTOForBruker() = RekrutteringstreffOutboundDto(id, tittel, beskrivelse, fraTid, tilTid, svarfrist, gateadresse, postnummer, poststed, status)
}

data class Arbeidsgiver(
    private val organisasjonsnummer: String,
    private val navn: String,
) {
    fun tilDTOForBruker() = ArbeidsgiverOutboundDto(organisasjonsnummer, navn)
}

data class Innlegg(
    val tittel: String,
    val htmlContent: String) {
    fun tilDTOForBruker() = InnleggOutboundDto(tittel, htmlContent)
}

