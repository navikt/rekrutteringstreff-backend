package no.nav.toi.jobbsoker.aktivitetskort

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.toi.JobbsøkerHendelsestype
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
        val hendelsestyper = JobbsøkerHendelsestype.entries.filter { it.tilAktivitetskortHendelseskontekst() != null }

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
        val kontekst = type.tilAktivitetskortHendelseskontekst()
        when (kontekst) {
            AktivitetskortHendelseskontekst.Invitasjon -> behandleInvitasjon(hendelse)
            is AktivitetskortHendelseskontekst.SvarOgTreffstatus -> behandleSvarOgTreffstatus(hendelse, kontekst)
            is AktivitetskortHendelseskontekst.Treffoppdatering -> behandleTreffoppdatering(hendelse, kontekst)

            null ->
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

    private fun behandleSvarOgTreffstatus(
        hendelse: JobbsøkerHendelseForAktivitetskort,
        kontekst: AktivitetskortHendelseskontekst.SvarOgTreffstatus
    ) {
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

    private fun behandleTreffoppdatering(
        hendelse: JobbsøkerHendelseForAktivitetskort,
        kontekst: AktivitetskortHendelseskontekst.Treffoppdatering
    ) {
        dataSource.executeInTransaction { connection ->
            aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId, connection)

            val treff = hentTreff(hendelse.rekrutteringstreffUuid)

            val endringer = if (kontekst.inkluderEndringsnotifikasjon) {
                objectMapper.readValue(
                    hendelse.hendelseData,
                    Rekrutteringstreffendringer::class.java
                ).endredeFelter.toList()
            } else {
                null
            }

            treff.aktivitetskortOppdateringFor(
                fnr = hendelse.fnr,
                hendelseId = hendelse.hendelseId,
                avsenderNavident = hendelse.aktøridentifikasjon,
                endredeFelter = endringer
            ).publiserTilRapids(rapidsConnection)

            if (kontekst.inkluderEndringsnotifikasjon) {
                log.info("Sendt melding om varsel for oppdatering av treff ${hendelse.rekrutteringstreffUuid} for jobbsøker")
            } else {
                log.info("Sendt rekrutteringstreffoppdatering for treff ${hendelse.rekrutteringstreffUuid} for jobbsøker")
            }
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

    private fun JobbsøkerHendelsestype.tilAktivitetskortHendelseskontekst(): AktivitetskortHendelseskontekst? =
        when (this) {
            JobbsøkerHendelsestype.INVITERT ->
                AktivitetskortHendelseskontekst.Invitasjon
            JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON ->
                AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = true, svarAvgittAvEier = false)
            JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON_AV_EIER ->
                AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = true, svarAvgittAvEier = true)
            JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON ->
                AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = false, svarAvgittAvEier = false)
            JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON_AV_EIER ->
                AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = false, svarAvgittAvEier = true)
            JobbsøkerHendelsestype.SVAR_FJERNET_AV_EIER ->
                AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = null, svarAvgittAvEier = true)
            JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST ->
                AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = true, svarAvgittAvEier = false, treffstatus = AktivitetskortTreffstatus.AVLYST)
            JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT ->
                AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = true, svarAvgittAvEier = false, treffstatus = AktivitetskortTreffstatus.FULLFØRT)
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST ->
                AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = null, svarAvgittAvEier = false, treffstatus = AktivitetskortTreffstatus.AVLYST)
            JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT ->
                AktivitetskortHendelseskontekst.SvarOgTreffstatus(svar = null, svarAvgittAvEier = false, treffstatus = AktivitetskortTreffstatus.FULLFØRT)
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING ->
                AktivitetskortHendelseskontekst.Treffoppdatering(inkluderEndringsnotifikasjon = false)
            JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON ->
                AktivitetskortHendelseskontekst.Treffoppdatering(inkluderEndringsnotifikasjon = true)
            else -> null
        }

    private sealed interface AktivitetskortHendelseskontekst {
        data object Invitasjon : AktivitetskortHendelseskontekst

        data class SvarOgTreffstatus(
            val svar: Boolean?,
            val svarAvgittAvEier: Boolean,
            val treffstatus: AktivitetskortTreffstatus? = null,
        ) : AktivitetskortHendelseskontekst

        data class Treffoppdatering(
            val inkluderEndringsnotifikasjon: Boolean,
        ) : AktivitetskortHendelseskontekst
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

