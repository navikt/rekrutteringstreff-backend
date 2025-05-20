package no.nav.toi.minside.rekrutteringstreff

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import io.javalin.http.Header
import no.nav.arbeid.cv.felles.token.AzureKlient
import no.nav.toi.minside.arbeidsgiver.ArbeidsgiverOutboundDto
import java.time.ZonedDateTime
import java.util.UUID

class RekrutteringstreffKlient(private val url: String, private val azureKlient: AzureKlient) {
    fun hent(id: String, innkommendeToken: String): Rekrutteringstreff? {
        val (_, response, result) = "$url/api/rekrutteringstreff/$id".httpGet()
            .header(Header.AUTHORIZATION, "Bearer ${azureKlient.onBehalfOfToken(innkommendeToken)}")
            .responseObject<Rekrutteringstreff>()

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
            .header(Header.AUTHORIZATION, "Bearer ${azureKlient.onBehalfOfToken(innkommendeToken)}")
            .responseObject<List<Arbeidsgiver>>()

        return when (result) {
            is Failure -> throw result.error
            is Success -> {
                if (response.statusCode == 404) return null
                else result.value
            }
        }
    }
}

class Rekrutteringstreff(
    private val id: UUID,
    private val tittel: String,
    private val beskrivelse: String?,
    private val fraTid: ZonedDateTime?,
    private val tilTid: ZonedDateTime?,
    private val sted: String?,
) {
    fun tilDTOForBruker() = RekrutteringstreffOutboundDto(id, tittel, beskrivelse, fraTid, tilTid, sted)
}

class Arbeidsgiver(
    private val organisasjonsnummer: String,
    private val navn: String,
) {
    fun tilDTOForBruker() = ArbeidsgiverOutboundDto(organisasjonsnummer, navn)
}
