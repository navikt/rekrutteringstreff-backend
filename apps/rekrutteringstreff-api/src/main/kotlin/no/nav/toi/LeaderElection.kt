package no.nav.toi

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.lang.System.getenv
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

interface LeaderElectionInterface {
    fun isLeader(): Boolean
}

class LeaderElection(): LeaderElectionInterface {
    private val hostname = InetAddress.getLocalHost().hostName
    private val electorUrl = getenv("ELECTOR_GET_URL")
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()
    private val objectMapper = jacksonObjectMapper()

    init {
        log.info("Leader election, hostname: $hostname, leader: ${getLeader()}, isLeader? ${isLeader()}")
    }

    override fun isLeader(): Boolean = hostname == getLeader()

    private fun getLeader(): String {
        return try {
            val request = HttpRequest.newBuilder().uri(URI.create(electorUrl)).GET().build()
            val json = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
            objectMapper.readValue(json, Elector::class.java).name
        } catch (e: Exception) {
            log.error("Feil under leader election", e)
            ""
        }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class Elector(val name: String)
