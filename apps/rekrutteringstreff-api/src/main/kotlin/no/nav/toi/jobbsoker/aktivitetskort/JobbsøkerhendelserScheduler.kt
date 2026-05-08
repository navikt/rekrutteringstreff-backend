package no.nav.toi.jobbsoker.aktivitetskort

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.toi.JobbsøkerHendelseskontekst
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.tilKontekst
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.executeInTransaction
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.Rekrutteringstreff
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.Rekrutteringstreffendringer
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.sql.DataSource

class JobbsøkerhendelserScheduler(
    private val dataSource: DataSource,
    private val aktivitetskortRepository: AktivitetskortRepository,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val rapidsConnection: RapidsConnection,
    private val objectMapper: ObjectMapper,
    private val leaderElection: LeaderElectionInterface,
) {

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)

    fun start() {
        log.info("Starter JobbsøkerhendelserScheduler")

        val now = LocalDateTime.now()
        val initialDelay = Duration.between(now, now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).toSeconds()

        scheduler.scheduleAtFixedRate(::behandleJobbsøkerHendelser, initialDelay, 10, TimeUnit.SECONDS)
    }

    fun stop() {
        log.info("Stopper JobbsøkerhendelserScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

    @WithSpan
    fun behandleJobbsøkerHendelser() {
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av JobbsøkerhendelserScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        if (leaderElection.isLeader().not()) {
            log.info("Kjøring av JobbsøkerhendelserScheduler skippes, instansen er ikke leader.")
            isRunning.set(false)
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
            log.error("Feil under kjøring av JobbsøkerhendelserScheduler", e)
        } finally {
            isRunning.set(false)
        }
    }

    private fun hentAlleUsendteHendelser(): List<JobbsøkerHendelseForAktivitetskort> {
        val hendelsestyper = JobbsøkerHendelsestype.entries.filter { it.tilKontekst() != null } + listOf(
            JobbsøkerHendelsestype.INVITERT,
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING,
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON,
        )

        return hendelsestyper.flatMap { type ->
            aktivitetskortRepository.hentUsendteHendelse(type)
                .map {
                    JobbsøkerHendelseForAktivitetskort(
                        it.jobbsokerHendelseDbId,
                        it.hendelseId,
                        it.fnr,
                        it.rekrutteringstreffUuid,
                        type,
                        it.hendelseData,
                        it.aktøridentifikasjon
                    )
                }
        }.sortedBy { it.jobbsokerHendelseDbId }
    }

    private fun behandleHendelse(hendelse: JobbsøkerHendelseForAktivitetskort) {
        val type = hendelse.hendelsestype
        val kontekst = type.tilKontekst()
        when {
            kontekst != null ->
                behandleSvarOgStatus(hendelse, kontekst)

            type == JobbsøkerHendelsestype.INVITERT ->
                behandleInvitasjon(hendelse)

            type == JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING ->
                behandleTreffEndret(hendelse)

            type == JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON ->
                behandleTreffEndretNotifikasjon(hendelse)

            else ->
                log.warn("Ukjent hendelsestype $type for jobbsøker-hendelse ${hendelse.jobbsokerHendelseDbId}")
        }
    }

    private fun behandleInvitasjon(hendelse: JobbsøkerHendelseForAktivitetskort) {
        dataSource.executeInTransaction { connection ->
            aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId, connection)

            val treff = hentTreff(hendelse.rekrutteringstreffUuid)

            sendAktivitetskortInvitasjon(treff, hendelse.fnr, hendelse.hendelseId, hendelse.aktøridentifikasjon)
        }
    }

    private fun behandleSvarOgStatus(hendelse: JobbsøkerHendelseForAktivitetskort, kontekst: JobbsøkerHendelseskontekst) {
        val treff = hentTreff(hendelse.rekrutteringstreffUuid)

        treff.aktivitetskortSvarOgStatusFor(
            fnr = hendelse.fnr,
            hendelseId = hendelse.hendelseId,
            endretAvPersonbruker = !kontekst.svarAvgittAvEier && kontekst.treffstatus == null,
            svar = kontekst.svar,
            treffstatus = kontekst.treffstatus,
            endretAv = when {
                kontekst.svarAvgittAvEier -> hendelse.aktøridentifikasjon
                kontekst.svar != null -> hendelse.fnr
                else -> null
            },
        ).publiserTilRapids(rapidsConnection)

        aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId)
    }

    private fun behandleTreffEndret(hendelse: JobbsøkerHendelseForAktivitetskort) {
        dataSource.executeInTransaction { connection ->
            aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId, connection)

            val treff = hentTreff(hendelse.rekrutteringstreffUuid)

            treff.aktivitetskortOppdateringFor(
                fnr = hendelse.fnr,
                hendelseId = hendelse.hendelseId,
                avsenderNavident = hendelse.aktøridentifikasjon,
            ).publiserTilRapids(rapidsConnection)

            log.info("Sendt rekrutteringstreffoppdatering for treff ${hendelse.rekrutteringstreffUuid} for jobbsøker")
        }
    }

    private fun behandleTreffEndretNotifikasjon(hendelse: JobbsøkerHendelseForAktivitetskort) {
        dataSource.executeInTransaction { connection ->
            aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId, connection)

            val treff = hentTreff(hendelse.rekrutteringstreffUuid)

            val endringer = objectMapper.readValue(
                hendelse.hendelseData,
                Rekrutteringstreffendringer::class.java
            ).endredeFelter.toList()

            treff.aktivitetskortOppdateringFor(
                fnr = hendelse.fnr,
                hendelseId = hendelse.hendelseId,
                avsenderNavident = hendelse.aktøridentifikasjon,
                endredeFelter = endringer
            ).publiserTilRapids(rapidsConnection)

            log.info("Sendt melding om varsel for oppdatering av treff ${hendelse.rekrutteringstreffUuid} for jobbsøker")
        }
    }

    private fun hentTreff(rekrutteringstreffUuid: String): Rekrutteringstreff =
        rekrutteringstreffRepository.hent(TreffId(rekrutteringstreffUuid))
            ?: throw RekrutteringstreffIkkeFunnetException("Fant ikke rekrutteringstreff med UUID $rekrutteringstreffUuid")

    private fun sendAktivitetskortInvitasjon(
        treff: Rekrutteringstreff,
        fnr: String,
        hendelseId: UUID,
        avsenderNavident: String?
    ) {
        treff.aktivitetskortInvitasjonFor(fnr, hendelseId, avsenderNavident)
            .publiserTilRapids(rapidsConnection)
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

