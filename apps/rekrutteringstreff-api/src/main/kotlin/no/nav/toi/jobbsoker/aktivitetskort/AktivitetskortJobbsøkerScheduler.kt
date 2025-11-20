package no.nav.toi.jobbsoker.aktivitetskort

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.Rekrutteringstreff
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.dto.EndringerDto
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AktivitetskortJobbsøkerScheduler(
    private val aktivitetskortRepository: AktivitetskortRepository,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val rapidsConnection: RapidsConnection,
    private val objectMapper: ObjectMapper
) {

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)

    fun start() {
        log.info("Starter AktivitetskortJobbsøkerScheduler")

        val now = LocalDateTime.now()
        val initialDelay = Duration.between(now, now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).toSeconds()

        scheduler.scheduleAtFixedRate(::behandleJobbsøkerHendelser, initialDelay, 10, TimeUnit.SECONDS)
    }

    fun stop() {
        log.info("Stopper AktivitetskortJobbsøkerScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

    fun behandleJobbsøkerHendelser() {
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av AktivitetskortJobbsøkerScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        try {
            val alleUsendteHendelser = hentAlleUsendteHendelser()

            if (alleUsendteHendelser.isEmpty()) {
                log.info("Ingen usendte jobbsøker-hendelser å behandle for aktivitetskort.")
                return
            }

            log.info("Starter behandling av ${alleUsendteHendelser.size} usendte jobbsøker-hendelser for aktivitetskort")

            alleUsendteHendelser.forEach { hendelse ->
                behandleHendelse(hendelse)
            }

            log.info("Ferdig med behandling av usendte jobbsøker-hendelser for aktivitetskort")
        } catch (e: Exception) {
            log.error("Feil under kjøring av AktivitetskortJobbsøkerScheduler", e)
        } finally {
            isRunning.set(false)
        }
    }

    private fun hentAlleUsendteHendelser(): List<JobbsøkerHendelseForAktivitetskort> {
        val hendelsestyper = listOf(
            JobbsøkerHendelsestype.INVITERT,
            JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON,
            JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON,
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON,
            JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST,
            JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT,
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST,
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT
        )

        return hendelsestyper.flatMap { type ->
            aktivitetskortRepository.hentUsendteHendelse(type)
                .map { JobbsøkerHendelseForAktivitetskort(it.jobbsokerHendelseDbId, it.fnr, it.rekrutteringstreffUuid, type, it.hendelseData, it.aktøridentifikasjon) }
        }.sortedBy { it.jobbsokerHendelseDbId }
    }

    private fun behandleHendelse(hendelse: JobbsøkerHendelseForAktivitetskort) {
        when (hendelse.hendelsestype) {
            JobbsøkerHendelsestype.INVITERT -> behandleInvitasjon(hendelse)
            JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON -> behandleSvar(hendelse, true)
            JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON -> behandleSvar(hendelse, false)
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON -> behandleTreffEndret(hendelse)
            JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST -> behandleSvartJaTreffstatus(hendelse, "avlyst")
            JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT -> behandleSvartJaTreffstatus(hendelse, "fullført")
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST -> behandleIkkeSvartTreffstatus(hendelse, "avlyst")
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT -> behandleIkkeSvartTreffstatus(hendelse, "fullført")
            else -> {
                log.warn("Ukjent hendelsestype ${hendelse.hendelsestype} for jobbsøker-hendelse ${hendelse.jobbsokerHendelseDbId}")
            }
        }
    }

    private fun behandleInvitasjon(hendelse: JobbsøkerHendelseForAktivitetskort) {
        aktivitetskortRepository.runInTransaction { connection ->
            aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId, connection)

            val treff = hentTreff(hendelse.rekrutteringstreffUuid)

            sendAktivitetskortInvitasjon(treff, hendelse.fnr)
            sendKandidatInvitertHendelse(hendelse)
        }
    }

    private fun behandleSvar(hendelse: JobbsøkerHendelseForAktivitetskort, svar: Boolean) {
        val treff = hentTreff(hendelse.rekrutteringstreffUuid)

        treff.aktivitetskortSvarOgStatusFor(fnr = hendelse.fnr, svar = svar, endretAvPersonbruker = true)
            .publiserTilRapids(rapidsConnection)

        aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId)
    }

    private fun behandleTreffEndret(hendelse: JobbsøkerHendelseForAktivitetskort) {
        aktivitetskortRepository.runInTransaction { connection ->
            aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId, connection)

            val treff = hentTreff(hendelse.rekrutteringstreffUuid)
            val endringer = parseEndringer(hendelse)

            if (skalSendeAktivitetskortOppdatering(treff, endringer, hendelse.jobbsokerHendelseDbId)) {
                sendAktivitetskortOppdatering(treff, hendelse.fnr, hendelse.rekrutteringstreffUuid)
            } else {
                log.info("Ingen relevante endringer for aktivitetskort i hendelse ${hendelse.jobbsokerHendelseDbId}, sender kun minside-varsel")
            }

            sendKandidatInvitertTreffEndretHendelse(hendelse)
        }
    }

    private fun hentTreff(rekrutteringstreffUuid: String): Rekrutteringstreff =
        rekrutteringstreffRepository.hent(TreffId(rekrutteringstreffUuid))
            ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID $rekrutteringstreffUuid")

    private fun sendAktivitetskortInvitasjon(treff: Rekrutteringstreff, fnr: String) {
        treff.aktivitetskortInvitasjonFor(fnr)
            .publiserTilRapids(rapidsConnection)
    }

    private fun parseEndringer(hendelse: JobbsøkerHendelseForAktivitetskort): EndringerDto {
        if (hendelse.hendelseData == null) {
            log.error("Ingen hendelse_data funnet for jobbsøker hendelse ${hendelse.jobbsokerHendelseDbId}, kaster exception")
            throw IllegalStateException("Manglende hendelse_data for hendelse ${hendelse.jobbsokerHendelseDbId}")
        }

        return try {
            objectMapper.readValue(hendelse.hendelseData, EndringerDto::class.java)
        } catch (e: Exception) {
            log.error("Kunne ikke parse hendelse_data for hendelse ${hendelse.jobbsokerHendelseDbId}, kaster exception for retry/alert", e)
            throw IllegalStateException("Parse-feil i hendelse_data for hendelse ${hendelse.jobbsokerHendelseDbId}", e)
        }
    }

    private fun skalSendeAktivitetskortOppdatering(treff: Rekrutteringstreff, endringer: EndringerDto, hendelseDbId: Long): Boolean {
        if (!treff.harRelevanteEndringerForAktivitetskort(endringer)) {
            return false
        }

        // Verifiser at endringer matcher database (kun til logging)
        val verificationResult = treff.verifiserEndringerMotDatabase(endringer)
        if (!verificationResult.erGyldig) {
            log.warn("Endringer i hendelse $hendelseDbId matcher ikke treff-data i databasen: ${verificationResult.feilmelding}. Sender likevel oppdatering med faktiske verdier fra database.")
        }

        return true
    }

    private fun sendAktivitetskortOppdatering(treff: Rekrutteringstreff, fnr: String, rekrutteringstreffUuid: String) {
        treff.aktivitetskortOppdateringFor(fnr)
            .publiserTilRapids(rapidsConnection)

        log.info("Sendt aktivitetskort-oppdatering for treff $rekrutteringstreffUuid til jobbsøker")
    }

    private fun sendKandidatInvitertHendelse(hendelse: JobbsøkerHendelseForAktivitetskort) {
        val message = JsonMessage.newMessage(
            eventName = "kandidatInvitert",
            map = mapOf(
                "rekrutteringstreffId" to hendelse.rekrutteringstreffUuid,
                "fnr" to hendelse.fnr,
                "avsenderNavident" to (hendelse.aktøridentifikasjon ?: "UKJENT")
            )
        )

        rapidsConnection.publish(hendelse.fnr, message.toJson())

        log.info("Sendt kandidatInvitert-hendelse for rekrutteringstreffId=${hendelse.rekrutteringstreffUuid}")
    }

    private fun sendKandidatInvitertTreffEndretHendelse(hendelse: JobbsøkerHendelseForAktivitetskort) {
        val message = JsonMessage.newMessage(
            eventName = "kandidatInvitertTreffEndret",
            map = mapOf(
                "rekrutteringstreffId" to hendelse.rekrutteringstreffUuid,
                "fnr" to hendelse.fnr,
                "avsenderNavident" to (hendelse.aktøridentifikasjon ?: "UKJENT")
            )
        )

        rapidsConnection.publish(hendelse.fnr, message.toJson())

        log.info("Sendt kandidatInvitertTreffEndret-hendelse for rekrutteringstreffId=${hendelse.rekrutteringstreffUuid}")
    }

    private fun behandleSvartJaTreffstatus(hendelse: JobbsøkerHendelseForAktivitetskort, treffstatus: String) {
        val treff = rekrutteringstreffRepository.hent(TreffId(hendelse.rekrutteringstreffUuid))
            ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${hendelse.rekrutteringstreffUuid}")

        treff.aktivitetskortSvarOgStatusFor(fnr = hendelse.fnr, svar = true, treffstatus = treffstatus, endretAvPersonbruker = false, endretAv = hendelse.fnr)
            .publiserTilRapids(rapidsConnection)

        aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId)
    }

    private fun behandleIkkeSvartTreffstatus(hendelse: JobbsøkerHendelseForAktivitetskort, treffstatus: String) {
        val treff = rekrutteringstreffRepository.hent(TreffId(hendelse.rekrutteringstreffUuid))
            ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${hendelse.rekrutteringstreffUuid}")

        treff.aktivitetskortSvarOgStatusFor(fnr = hendelse.fnr, treffstatus = treffstatus, endretAvPersonbruker = false)
            .publiserTilRapids(rapidsConnection)

        aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId)
    }

    private data class JobbsøkerHendelseForAktivitetskort(
        val jobbsokerHendelseDbId: Long,
        val fnr: String,
        val rekrutteringstreffUuid: String,
        val hendelsestype: JobbsøkerHendelsestype,
        val hendelseData: String?,
        val aktøridentifikasjon: String?
    )
}

