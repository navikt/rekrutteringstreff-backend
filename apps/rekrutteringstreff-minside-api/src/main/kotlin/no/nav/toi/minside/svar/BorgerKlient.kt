package no.nav.toi.minside.svar

import io.javalin.http.Header
import no.nav.toi.minside.JacksonConfig
import no.nav.toi.minside.TokenXKlient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class BorgerKlient(
    private val url: String,
    private val tokenXKlient: TokenXKlient,
    private val rekrutteringstreffAudience: String,
    private val httpClient: HttpClient,
) {

     companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
        private val timeout: Duration = Duration.ofSeconds(15)
    }

    fun jobbsøkerPath(treffId: String) = "$url/api/rekrutteringstreff/$treffId/jobbsoker"

    fun hentJobbsøkerMedStatuser(id: String, innkommendeToken: String): JobbsøkerMedStatuserOutboundDto {
        val request = HttpRequest.newBuilder()
            .uri(URI("${jobbsøkerPath(id)}/borger"))
            .header(Header.AUTHORIZATION, "Bearer ${tokenXKlient.onBehalfOfTokenX(innkommendeToken, rekrutteringstreffAudience)}")
            .timeout(timeout)
            .GET()
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())

        log.info("Hentet jobbsøker med statuser for treffId: $id, status: ${response.statusCode()}")

        // TODO: Kun inviterte jobbsøkere finnes i databasen. Vurder om dette skal håndteres i rekrutteringstreff-api
        if (response.statusCode() == 404) {
            return JobbsøkerMedStatuserOutboundDto(
                id = "ukjent",
                treffId = id,
                fødselsnummer = "",
                fornavn = "",
                etternavn = "",
                kontor = null,
                veilederNavn = null,
                veilederNavIdent = null,
                statuser = StatuserOutboundDto(
                    erPåmeldt = false,
                    erInvitert = false,
                    harSvart = false
                )
            )
        }

        if (response.statusCode() !in 200..299) {
            throw RuntimeException("Henting av jobbsøker med statuser for treffId $id feilet med status ${response.statusCode()}")
        }

        return JacksonConfig.mapper.readValue(response.body(), JobbsøkerMedStatuserOutboundDto::class.java)
    }

    fun svarPåTreff(rekrutterinstreffId: String, innkommendeToken: String, erPåmeldt: Boolean) {
        val påmeldtSomStreng = if (erPåmeldt) "ja" else "nei"

        val request = HttpRequest.newBuilder()
            .uri(URI("${jobbsøkerPath(rekrutterinstreffId)}/borger/svar-${påmeldtSomStreng}"))
            .header(Header.AUTHORIZATION, "Bearer ${tokenXKlient.onBehalfOfTokenX(innkommendeToken, rekrutteringstreffAudience)}")
            .timeout(timeout)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()
        val response = httpClient.send(request, HttpResponse.BodyHandlers.discarding())

        if (response.statusCode() !in 200..299) {
            log.error("Svar på treff $rekrutterinstreffId feilet med status ${response.statusCode()}")
            throw RuntimeException("Svar på treff $rekrutterinstreffId feilet med status ${response.statusCode()}")
        } else {
            log.info("Jobbsøker har svart ${påmeldtSomStreng} på rekrutteringstreff med id: $rekrutterinstreffId")
        }
    }
}

data class JobbsøkerMedStatuserOutboundDto(
    val id: String?,
    val treffId: String,
    val fødselsnummer: String,
    val fornavn: String,
    val etternavn: String,
    val kontor: KontorOutboundDto?,
    val veilederNavn: String?,
    val veilederNavIdent: String?,
    val statuser: StatuserOutboundDto,
)

data class KontorOutboundDto(
    val kontornummer: String,
    val kontornavn: String?,
)

data class StatuserOutboundDto(
    val erPåmeldt: Boolean,
    val erInvitert: Boolean,
    val harSvart: Boolean
)
