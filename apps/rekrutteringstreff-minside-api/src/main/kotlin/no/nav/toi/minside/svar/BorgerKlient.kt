package no.nav.toi.minside.svar

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import io.javalin.http.Header
import no.nav.toi.minside.JacksonConfig
import no.nav.toi.minside.TokenXKlient

class BorgerKlient(private val url: String, private val tokenXKlient: TokenXKlient, private val rekrutteringstreffAudience: String) {

    fun jobbsøkerPath(treffId: String) = "$url/api/rekrutteringstreff/$treffId/jobbsoker"

    fun hentJobbsøkerMedStatuser(id: String, innkommendeToken: String): JobbsøkerMedStatuserOutboundDto? {
        val (_, response, result) = "${jobbsøkerPath(id)}/borger".httpGet()
            .header(Header.AUTHORIZATION, "Bearer ${tokenXKlient.onBehalfOfTokenX(innkommendeToken, rekrutteringstreffAudience)}")
            .responseObject<JobbsøkerMedStatuserOutboundDto>(JacksonConfig.mapper)

        return when (result) {
            is Failure -> throw result.error
            is Success -> {
                if (response.statusCode == 404) return null
                else result.value
            }
        }
    }
}

data class JobbsøkerMedStatuserOutboundDto(
    val id: String,
    val treffId: String,
    val fødselsnummer: String,
    val kandidatnummer: String?,
    val fornavn: String,
    val etternavn: String,
    val navkontor: String?,
    val veilederNavn: String?,
    val veilederNavIdent: String?,
    val statuser: StatuserOutboundDto,
)

data class StatuserOutboundDto(
    val erPåmeldt: Boolean,
    val erInvitert: Boolean,
    val harSvart: Boolean
)
