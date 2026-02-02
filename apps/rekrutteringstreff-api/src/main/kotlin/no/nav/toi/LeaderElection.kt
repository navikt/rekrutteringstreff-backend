package no.nav.toi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.lang.System.getenv
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


class LeaderElection() {
    private val hostname = InetAddress.getLocalHost().hostName
    private var leader = ""
    private val electorUrl = getenv("ELECTOR_GET_URL")
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()
    private val objectMapper = jacksonObjectMapper()

    init {
        log.info("Leader election initialized, hostname is $hostname")
        log.info("electorUrl? $electorUrl")
        log.info("isLeader? ${isLeader()}")
    }

    fun isLeader(): Boolean = hostname == getLeader()

    private fun getLeader(): String {
        leader = try {
            val request = HttpRequest.newBuilder().uri(URI.create(electorUrl)).GET().build()
            val json = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
            log.info("json: $json")
            objectMapper.readValue(json, Elector::class.java).name
        } catch (e: Exception) {
            log.error("Feil under leader election", e)
            ""
        }
        log.info("Running leader election, leader is $leader, hostname is $hostname")
        return leader
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class Elector(val name: String)
