package no.nav.toi.jobbsoker.aktivitetskort

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AktivitetskortOppmøteScheduler(
    private val aktivitetskortRepository: AktivitetskortRepository,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val rapidsConnection: RapidsConnection
) {

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)

    fun start() {
        log.info("Starter AktivitetskortSvarScheduler")

        val now = LocalDateTime.now()
        val initialDelay = Duration.between(now, now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).toSeconds()

        scheduler.scheduleAtFixedRate(::behandleSvar, initialDelay, 60, TimeUnit.SECONDS)
    }

    fun stop() {
        log.info("Stopper AktivitetskortSvarScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

     fun behandleSvar() {
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av AktivitetskortSvarScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        try {
            TODO()
            /*val usendteSvarJa = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVAR_JA_TIL_INVITASJON)
            val usendteSvarNei = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVAR_NEI_TIL_INVITASJON)


            if (usendteSvarNei.isEmpty() && usendteSvarJa.isEmpty()) {
                log.info("Ingen usendte svar å behandle.")
                return
            }

            log.info("Starter behandling av ${usendteSvarJa.size + usendteSvarNei.size} usendte svar for aktivitetskort")

            usendteSvarJa.forEach { usendtSvar ->
                val treff = rekrutteringstreffRepository.hent(TreffId(usendtSvar.rekrutteringstreffUuid)) ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${usendtSvar.rekrutteringstreffUuid}")
                treff.aktivitetskortSvarFor(fnr = usendtSvar.fnr, svar = true)
                    .publiserTilRapids(rapidsConnection)
                aktivitetskortRepository.lagrePollingstatus(usendtSvar.jobbsokerHendelseDbId)
            }
            log.info("Ferdig med behandling av usendte svar ja for aktivitetskort")

            usendteSvarNei.forEach { usendtSvar ->
                val treff = rekrutteringstreffRepository.hent(TreffId(usendtSvar.rekrutteringstreffUuid)) ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${usendtSvar.rekrutteringstreffUuid}")
                treff.aktivitetskortSvarFor(fnr = usendtSvar.fnr, svar = false)
                    .publiserTilRapids(rapidsConnection)
                aktivitetskortRepository.lagrePollingstatus(usendtSvar.jobbsokerHendelseDbId)
            }*/
        } catch (e: Exception) {
            log.error("Feil under kjøring av AktivitetskortSvarScheduler", e)
            throw e
        } finally {
            isRunning.set(false)
        }
    }
}