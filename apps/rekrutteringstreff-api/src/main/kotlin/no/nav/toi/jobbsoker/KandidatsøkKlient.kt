package no.nav.toi.kandidatsok

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AccessTokenClient
import no.nav.toi.JacksonConfig
import no.nav.toi.exception.KandidatsokOppslagFeiletException
import no.nav.toi.exception.KandidatsokTilgangAvvistException
import no.nav.toi.jobbsoker.Fødselsnummer
import no.nav.toi.jobbsoker.Innsatsgruppe
import no.nav.toi.jobbsoker.Kandidatnummer
import no.nav.toi.jobbsoker.Kontor
import no.nav.toi.jobbsoker.VeilederNavIdent
import no.nav.toi.jobbsoker.VeilederNavn
import no.nav.toi.log
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private data class KandidatKandidatnrRequestDto(val fodselsnummer: String)
private data class KandidatKandidatnrResponsDto(val arenaKandidatnr: String)

private data class JobbsokerInfoRequestDto(val fodselsnumre: List<String>)
private data class JobbsokerInfoDto(
    val fodselsnummer: String,
    val navkontor: String? = null,
    val veilederNavn: String?,
    val veilederNavIdent: String?,
    val alder: Int?,
    val innsatsgruppe: String?,
    val orgenhet: String? = null,
)
private data class JobbsokerInfoResponsDto(val jobbsokerInfo: List<JobbsokerInfoDto>)

data class JobbsokerInfo(
    val kontor: Kontor?,
    val veilederNavn: VeilederNavn?,
    val veilederNavIdent: VeilederNavIdent?,
    val alder: Int?,
    val innsatsgruppe: Innsatsgruppe?,
)
class KandidatsøkKlient(
    private val kandidatsokApiUrl: String,
    private val kandidatsokScope: String,
    private val accessTokenClient: AccessTokenClient,
    private val httpClient: HttpClient = HttpClient.newBuilder().build(),
    private val objectMapper: ObjectMapper = JacksonConfig.mapper
) {
    fun erKonfigurert(): Boolean = kandidatsokApiUrl.isNotBlank() && kandidatsokScope.isNotBlank()

    fun hentKandidatnummer(fødselsnummer: Fødselsnummer, innkommendeToken: String): Kandidatnummer? {
        val onBehalfOfToken = hentOnBehalfOfToken(innkommendeToken)
        return hentKandidatnummerMedAccessToken(fødselsnummer, onBehalfOfToken)
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

    private fun hentOnBehalfOfToken(innkommendeToken: String): String =
        accessTokenClient.hentAccessToken(innkommendeToken = innkommendeToken, scope = kandidatsokScope)

    fun hentJobbsokerInfo(
        fødselsnumre: List<Fødselsnummer>,
        innkommendeToken: String,
    ): Map<Fødselsnummer, JobbsokerInfo> {
        val unikeFødselsnumre = fødselsnumre.distinct()
        if (unikeFødselsnumre.isEmpty()) return emptyMap()

        val onBehalfOfToken = hentOnBehalfOfToken(innkommendeToken)
        return hentJobbsokerInfoMedAccessToken(unikeFødselsnumre, onBehalfOfToken)
    }

    private fun hentJobbsokerInfoMedAccessToken(
        fødselsnumre: List<Fødselsnummer>,
        onBehalfOfToken: String,
    ): Map<Fødselsnummer, JobbsokerInfo> {
        log.info("Henter jobbsøkerinfo fra kandidatsøk-api for ${fødselsnumre.size} fødselsnummer")
        val url = "$kandidatsokApiUrl/api/jobbsoker-info"
        val requestBodyJson = objectMapper.writeValueAsString(
            JobbsokerInfoRequestDto(fødselsnumre.map { it.asString })
        )

        try {
            val request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $onBehalfOfToken")
                .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
                .build()

            val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) {
                if (response.statusCode() == 403) {
                    throw KandidatsokTilgangAvvistException("Bruker har ikke tilgang til en eller flere jobbsøkere i kandidatsøk.")
                }
                log.error("Feil ved henting av jobbsøkerinfo fra kandidatsøk-api. status: ${response.statusCode()}")
                throw KandidatsokOppslagFeiletException("Klarte ikke å hente jobbsøkerinfo fra kandidatsøk.")
            }

            val respons = objectMapper.readValue(response.body(), JobbsokerInfoResponsDto::class.java)
            return respons.jobbsokerInfo.associate { dto ->
                Fødselsnummer(dto.fodselsnummer) to JobbsokerInfo(
                    kontor = dto.orgenhet?.takeIf(String::isNotBlank)?.let { nr ->
                        Kontor(
                            kontornummer = nr,
                            kontornavn = dto.navkontor?.takeIf(String::isNotBlank),
                        )
                    },
                    veilederNavn = dto.veilederNavn?.takeIf(String::isNotBlank)?.let(::VeilederNavn),
                    veilederNavIdent = dto.veilederNavIdent
                        ?.takeIf(String::isNotBlank)
                        ?.let(::VeilederNavIdent),
                    alder = dto.alder,
                    innsatsgruppe = dto.innsatsgruppe?.takeIf(String::isNotBlank)?.let(::Innsatsgruppe),
                )
            }
        } catch (e: KandidatsokTilgangAvvistException) {
            throw e
        } catch (e: KandidatsokOppslagFeiletException) {
            throw e
        } catch (e: Exception) {
            log.error("Det skjedde en feil ved henting av jobbsøkerinfo fra kandidatsøk-api", e)
            throw KandidatsokOppslagFeiletException("Klarte ikke å hente jobbsøkerinfo fra kandidatsøk.", e)
        }
    }
}
