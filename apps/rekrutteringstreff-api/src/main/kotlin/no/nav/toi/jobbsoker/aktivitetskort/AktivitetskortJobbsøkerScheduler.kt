package no.nav.toi.jobbsoker.aktivitetskort

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.executeInTransaction
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.Rekrutteringstreff
import no.nav.toi.rekrutteringstreff.Rekrutteringstreffendringer
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

class AktivitetskortJobbsøkerScheduler(
    private val dataSource: DataSource,
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
                .map { JobbsøkerHendelseForAktivitetskort(it.jobbsokerHendelseDbId, it.hendelseId, it.fnr, it.rekrutteringstreffUuid, type, it.hendelseData, it.aktøridentifikasjon) }
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
        dataSource.executeInTransaction { connection ->
            aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId, connection)

            val treff = hentTreff(hendelse.rekrutteringstreffUuid)

            sendAktivitetskortInvitasjon(treff, hendelse.fnr, hendelse.hendelseId, hendelse.aktøridentifikasjon)
        }
    }

    private fun behandleSvar(hendelse: JobbsøkerHendelseForAktivitetskort, svar: Boolean) {
        val treff = hentTreff(hendelse.rekrutteringstreffUuid)

        treff.aktivitetskortSvarOgStatusFor(fnr = hendelse.fnr, svar = svar, endretAvPersonbruker = true)
            .publiserTilRapids(rapidsConnection)

        aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId)
    }

    private fun behandleTreffEndret(hendelse: JobbsøkerHendelseForAktivitetskort) {
        dataSource.executeInTransaction { connection ->
            aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId, connection)

            val treff = hentTreff(hendelse.rekrutteringstreffUuid)

            // Hent ut hvilke felt som er endret og skal varsles om
            val endredeFelter = hendelse.hendelseData?.let { data ->
                val endringer = objectMapper.readValue(data, Rekrutteringstreffendringer::class.java)
                endringer.hentFelterSomSkalVarsles().takeIf { it.isNotEmpty() }
            }

            // Send én samlet melding som brukes av både aktivitetskort-appen og kandidatvarsel-api
            treff.aktivitetskortOppdateringFor(
                fnr = hendelse.fnr,
                hendelseId = hendelse.hendelseId,
                avsenderNavident = hendelse.aktøridentifikasjon,
                endredeFelter = endredeFelter
            ).publiserTilRapids(rapidsConnection)

            log.info("Sendt rekrutteringstreffoppdatering for treff ${hendelse.rekrutteringstreffUuid}${endredeFelter?.let { " med endredeFelter=$it" } ?: ""}")
        }
    }

    private fun hentTreff(rekrutteringstreffUuid: String): Rekrutteringstreff =
        rekrutteringstreffRepository.hent(TreffId(rekrutteringstreffUuid))
            ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID $rekrutteringstreffUuid")

    private fun sendAktivitetskortInvitasjon(treff: Rekrutteringstreff, fnr: String, hendelseId: UUID, avsenderNavident: String?) {
        treff.aktivitetskortInvitasjonFor(fnr, hendelseId, avsenderNavident)
            .publiserTilRapids(rapidsConnection)
    }

    private fun behandleSvartJaTreffstatus(hendelse: JobbsøkerHendelseForAktivitetskort, treffstatus: String) {
        val treff = rekrutteringstreffRepository.hent(TreffId(hendelse.rekrutteringstreffUuid))
            ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${hendelse.rekrutteringstreffUuid}")

        // Send aktivitetskort-oppdatering (svar og status)
        treff.aktivitetskortSvarOgStatusFor(fnr = hendelse.fnr, svar = true, treffstatus = treffstatus, endretAvPersonbruker = false, endretAv = hendelse.fnr)
            .publiserTilRapids(rapidsConnection)

        // Send avlysningsvarsel til kandidatvarsel-api (kun ved avlysning)
        if (treffstatus == "avlyst") {
            treff.aktivitetskortAvlysningFor(fnr = hendelse.fnr, hendelseId = hendelse.hendelseId)
                .publiserTilRapids(rapidsConnection)
            log.info("Sendt rekrutteringstreffavlysning for treff ${hendelse.rekrutteringstreffUuid}")
        }

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
        val hendelseId: UUID,
        val fnr: String,
        val rekrutteringstreffUuid: String,
        val hendelsestype: JobbsøkerHendelsestype,
        val hendelseData: String?,
        val aktøridentifikasjon: String?
    )
}

