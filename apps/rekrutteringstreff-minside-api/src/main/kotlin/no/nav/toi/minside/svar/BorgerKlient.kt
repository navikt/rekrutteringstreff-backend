package no.nav.toi.minside.svar

import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.fuel.jackson.responseObject
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import io.javalin.http.Header
import no.nav.toi.minside.JacksonConfig
import no.nav.toi.minside.TokenXKlient
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class BorgerKlient(private val url: String, private val tokenXKlient: TokenXKlient, private val rekrutteringstreffAudience: String) {

     companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    fun jobbsøkerPath(treffId: String) = "$url/api/rekrutteringstreff/$treffId/jobbsoker"

    fun hentJobbsøkerMedStatuser(id: String, innkommendeToken: String): JobbsøkerMedStatuserOutboundDto {
        val (_, response, result) = "${jobbsøkerPath(id)}/borger".httpGet()
            .header(Header.AUTHORIZATION, "Bearer ${tokenXKlient.onBehalfOfTokenX(innkommendeToken, rekrutteringstreffAudience)}")
            .responseObject<JobbsøkerMedStatuserOutboundDto>(JacksonConfig.mapper)

        log.info("Hentet jobbsøker med statuser for treffId: $id, status: ${response.statusCode}")

        // TODO: Kun inviterte jobbsøkere finnes i databasen. Vurder om dette skal håndteres i rekrutteringstreff-api
        if (response.statusCode == 404) {
            return JobbsøkerMedStatuserOutboundDto(
                id = "ukjent",
                treffId = id,
                fødselsnummer = "",
                kandidatnummer = null,
                fornavn = "",
                etternavn = "",
                navkontor = null,
                veilederNavn = null,
                veilederNavIdent = null,
                statuser = StatuserOutboundDto(
                    erPåmeldt = false,
                    erInvitert = false,
                    harSvart = false
                )
            )
        }

        return when (result) {
            is Failure -> throw result.error
            is Success -> result.value
        }
    }

    fun svarPåTreff(rekrutterinstreffId: String, innkommendeToken: String, erPåmeldt: Boolean) {
        val påmeldtSomStreng = if (erPåmeldt) "ja" else "nei"

        "${jobbsøkerPath(rekrutterinstreffId)}/borger/svar-ja".httpPost()
            .header(
                Header.AUTHORIZATION,
                "Bearer ${tokenXKlient.onBehalfOfTokenX(innkommendeToken, rekrutteringstreffAudience)}"
            )
            .responseString { _, response, result ->
                log.info("Svarte ${påmeldtSomStreng} på jobbtreff: $rekrutterinstreffId, status: ${response.statusCode}")
                when (result) {
                    is Failure -> throw result.error
                    is Success -> {
                        log.info("Jobbsøker har svart ${påmeldtSomStreng} på rekrutteringstreff med id: $rekrutterinstreffId")
                    }
                }
            }
    }
}

data class JobbsøkerMedStatuserOutboundDto(
    val id: String?,
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
