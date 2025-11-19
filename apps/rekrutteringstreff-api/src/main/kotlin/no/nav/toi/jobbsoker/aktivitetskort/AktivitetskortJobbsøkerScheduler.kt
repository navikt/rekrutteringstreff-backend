package no.nav.toi.jobbsoker.aktivitetskort

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.log
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
                try {
                    behandleHendelse(hendelse)
                } catch (e: Exception) {
                    log.error("Feil under behandling av jobbsøker-hendelse ${hendelse.jobbsokerHendelseDbId} av type ${hendelse.hendelsestype}", e)
                    throw e
                }
            }

            log.info("Ferdig med behandling av usendte jobbsøker-hendelser for aktivitetskort")
        } catch (e: Exception) {
            log.error("Feil under kjøring av AktivitetskortJobbsøkerScheduler", e)
            throw e
        } finally {
            isRunning.set(false)
        }
    }

    private fun hentAlleUsendteHendelser(): List<JobbsøkerHendelseForAktivitetskort> {
        val invitasjoner = aktivitetskortRepository.hentUsendteInvitasjoner()
            .map { JobbsøkerHendelseForAktivitetskort(it.jobbsokerHendelseDbId, it.fnr, it.rekrutteringstreffUuid, JobbsøkerHendelsestype.INVITERT, null) }

        val svarJa = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON)
            .map { JobbsøkerHendelseForAktivitetskort(it.jobbsokerHendelseDbId, it.fnr, it.rekrutteringstreffUuid, JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON, it.hendelseData) }

        val svarNei = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON)
            .map { JobbsøkerHendelseForAktivitetskort(it.jobbsokerHendelseDbId, it.fnr, it.rekrutteringstreffUuid, JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON, it.hendelseData) }

        val treffEndret = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON)
            .map { JobbsøkerHendelseForAktivitetskort(it.jobbsokerHendelseDbId, it.fnr, it.rekrutteringstreffUuid, JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON, it.hendelseData) }

        val avlyst = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST)
            .map { JobbsøkerHendelseForAktivitetskort(it.jobbsokerHendelseDbId, it.fnr, it.rekrutteringstreffUuid, JobbsøkerHendelsestype.SVART_JA_TREFF_AVLYST, it.hendelseData) }

        val fullført = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT)
            .map { JobbsøkerHendelseForAktivitetskort(it.jobbsokerHendelseDbId, it.fnr, it.rekrutteringstreffUuid, JobbsøkerHendelsestype.SVART_JA_TREFF_FULLFØRT, it.hendelseData) }

        val ikkeSvartAvlyst = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST)
            .map { JobbsøkerHendelseForAktivitetskort(it.jobbsokerHendelseDbId, it.fnr, it.rekrutteringstreffUuid, JobbsøkerHendelsestype.IKKE_SVART_TREFF_AVLYST, it.hendelseData) }

        val ikkeSvartFullført = aktivitetskortRepository.hentUsendteHendelse(JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT)
            .map { JobbsøkerHendelseForAktivitetskort(it.jobbsokerHendelseDbId, it.fnr, it.rekrutteringstreffUuid, JobbsøkerHendelsestype.IKKE_SVART_TREFF_FULLFØRT, it.hendelseData) }

        // Slå sammen alle hendelser og sorter etter ID (som representerer tidspunkt)
        return (invitasjoner + svarJa + svarNei + treffEndret + avlyst + fullført + ikkeSvartAvlyst + ikkeSvartFullført)
            .sortedBy { it.jobbsokerHendelseDbId }
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
        val treff = rekrutteringstreffRepository.hent(TreffId(hendelse.rekrutteringstreffUuid))
            ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${hendelse.rekrutteringstreffUuid}")

        treff.aktivitetskortInvitasjonFor(hendelse.fnr)
            .publiserTilRapids(rapidsConnection)

        aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId)
    }

    private fun behandleSvar(hendelse: JobbsøkerHendelseForAktivitetskort, svar: Boolean) {
        val treff = rekrutteringstreffRepository.hent(TreffId(hendelse.rekrutteringstreffUuid))
            ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${hendelse.rekrutteringstreffUuid}")

        treff.aktivitetskortSvarOgStatusFor(fnr = hendelse.fnr, svar = svar, endretAvPersonbruker = true)
            .publiserTilRapids(rapidsConnection)

        aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId)
    }

    private fun behandleTreffEndret(hendelse: JobbsøkerHendelseForAktivitetskort) {
        val treff = rekrutteringstreffRepository.hent(TreffId(hendelse.rekrutteringstreffUuid))
            ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${hendelse.rekrutteringstreffUuid}")

        // Parse hendelse_data for å få endringene som ble gjort
        val endringer = if (hendelse.hendelseData != null) {
            try {
                objectMapper.readValue(hendelse.hendelseData, EndringerDto::class.java)
            } catch (e: Exception) {
                log.error("Kunne ikke parse hendelse_data for hendelse ${hendelse.jobbsokerHendelseDbId}, kaster exception for retry/alert", e)
                throw IllegalStateException("Parse-feil i hendelse_data for hendelse ${hendelse.jobbsokerHendelseDbId}", e)
            }
        } else {
            log.error("Ingen hendelse_data funnet for jobbsøker hendelse ${hendelse.jobbsokerHendelseDbId}, kaster exception")
            throw IllegalStateException("Manglende hendelse_data for hendelse ${hendelse.jobbsokerHendelseDbId}")
        }

        // Sjekk om noen relevante felt ble endret
        if (!treff.harRelevanteEndringerForAktivitetskort(endringer)) {
            log.info("Ingen relevante endringer for aktivitetskort i hendelse ${hendelse.jobbsokerHendelseDbId}, markerer som behandlet")
            aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId)
            return
        }

        // Verifiser at nyVerdi fra endringer matcher gjeldende verdier i databasen (kun til logging)
        val verificationResult = treff.verifiserEndringerMotDatabase(endringer)
        if (!verificationResult.erGyldig) {
            log.warn("Endringer i hendelse ${hendelse.jobbsokerHendelseDbId} matcher ikke treff-data i databasen: ${verificationResult.feilmelding}. Sender likevel oppdatering med faktiske verdier fra database.")
        }

        // Send oppdatering via rapids ved å bruke faktiske verdier fra rekrutteringstreff-tabellen
        treff.aktivitetskortOppdateringFor(hendelse.fnr)
            .publiserTilRapids(rapidsConnection)

        aktivitetskortRepository.lagrePollingstatus(hendelse.jobbsokerHendelseDbId)
        log.info("Sendt aktivitetskort-oppdatering for treff ${hendelse.rekrutteringstreffUuid} til jobbsøker")
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
        val hendelseData: String?
    )
}

