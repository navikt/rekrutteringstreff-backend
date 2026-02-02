package no.nav.toi

import java.lang.System.getenv
import java.net.InetAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse


class LeaderElection() {
    private val hostname = InetAddress.getLocalHost().hostName
    private var leader = ""
    private val electorPath = getenv("ELECTOR_GET_URL")
    private val electorUrl = "http://$electorPath"
    private val httpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.ALWAYS)
        .build()

    init {
        log.info("Leader election initialized, hostname is $hostname")
        log.info("electorUrl? $electorUrl")
        log.info("isLeader? ${isLeader()}")
    }

    fun isLeader(): Boolean = hostname == getLeader()

    private fun getLeader(): String {
        leader = try {
            val request = HttpRequest.newBuilder().uri(URI.create(electorUrl)).GET().build()
            httpClient.send(request, HttpResponse.BodyHandlers.ofString()).toString()
        } catch (e: Exception) {
            log.error("Feil under leader election", e)
            ""
        }
        log.debug("Running leader election, leader is $leader")
        return leader
    }
}
