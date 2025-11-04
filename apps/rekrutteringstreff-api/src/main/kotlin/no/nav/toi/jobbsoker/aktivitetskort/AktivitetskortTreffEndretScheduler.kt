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

class AktivitetskortTreffEndretScheduler(
    private val aktivitetskortRepository: AktivitetskortRepository,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val rapidsConnection: RapidsConnection,
    private val objectMapper: ObjectMapper
) {

    private val scheduler = Executors.newScheduledThreadPool(1)
    private val isRunning = AtomicBoolean(false)


    fun start() {
        log.info("Starter AktivitetskortTreffEndretScheduler")

        val now = LocalDateTime.now()
        val initialDelay = Duration.between(now, now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)).toSeconds()

        scheduler.scheduleAtFixedRate(::behandleTreffEndringer, initialDelay, 60, TimeUnit.SECONDS)
    }

    fun stop() {
        log.info("Stopper AktivitetskortTreffEndretScheduler")
        scheduler.shutdown()
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow()
            }
        } catch (e: InterruptedException) {
            scheduler.shutdownNow()
        }
    }

    fun behandleTreffEndringer() {
        if (isRunning.getAndSet(true)) {
            log.info("Forrige kjøring av AktivitetskortTreffEndretScheduler er ikke ferdig, skipper denne kjøringen.")
            return
        }

        try {
            val usendteEndringer = aktivitetskortRepository.hentUsendteHendelse(
                JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON
            )

            if (usendteEndringer.isEmpty()) {
                log.info("Ingen usendte treff-endringer å behandle for aktivitetskort.")
                return
            }

            log.info("Starter behandling av ${usendteEndringer.size} usendte treff-endringer for aktivitetskort")

            usendteEndringer.forEach { usendtEndring ->
                try {
                    behandleEnkeltEndring(usendtEndring)
                } catch (e: Exception) {
                    log.error("Feil under behandling av treff-endring for hendelse ${usendtEndring.jobbsokerHendelseDbId}", e)
                    throw e
                }
            }

            log.info("Ferdig med behandling av usendte treff-endringer for aktivitetskort")
        } catch (e: Exception) {
            log.error("Feil under kjøring av AktivitetskortTreffEndretScheduler", e)
            throw e
        } finally {
            isRunning.set(false)
        }
    }

    private fun behandleEnkeltEndring(usendtEndring: UsendtHendelse) {
        // Hent gjeldende treff fra databasen først
        val treff = rekrutteringstreffRepository.hent(TreffId(usendtEndring.rekrutteringstreffUuid))
            ?: throw IllegalStateException("Fant ikke rekrutteringstreff med UUID ${usendtEndring.rekrutteringstreffUuid}")

        // Parse hendelse_data for å få endringene som ble gjort
        val endringer = if (usendtEndring.hendelseData != null) {
            try {
                objectMapper.readValue(usendtEndring.hendelseData, EndringerDto::class.java)
            } catch (e: Exception) {
                log.warn("Kunne ikke parse hendelse_data for hendelse ${usendtEndring.jobbsokerHendelseDbId}, skipper", e)
                return
            }
        } else {
            log.warn("Ingen hendelse_data funnet for jobbsøker hendelse ${usendtEndring.jobbsokerHendelseDbId}, skipper")
            return
        }

        // Sjekk om noen relevante felt ble endret
        val harRelevanteEndringer = harRelevanteEndringer(endringer)
        if (!harRelevanteEndringer) {
            log.info("Ingen relevante endringer for aktivitetskort i hendelse ${usendtEndring.jobbsokerHendelseDbId}, markerer som behandlet")
            aktivitetskortRepository.lagrePollingstatus(usendtEndring.jobbsokerHendelseDbId)
            return
        }

        // Verifiser at nyVerdi fra endringer matcher gjeldende verdier i databasen
        // Dette sikrer at treffet faktisk ble oppdatert før vi sender til aktivitetskort
        val verificationResult = verifiserEndringer(endringer, treff)
        if (!verificationResult.erGyldig) {
            log.warn("Endringer i hendelse ${usendtEndring.jobbsokerHendelseDbId} matcher ikke treff-data i databasen: ${verificationResult.feilmelding}. Skipper oppdatering av aktivitetskort.")
            return
        }

        // Send oppdatering via rapids ved å bruke faktiske verdier fra rekrutteringstreff-tabellen
        treff.aktivitetskortOppdateringFor(usendtEndring.fnr)
            .publiserTilRapids(rapidsConnection)

        aktivitetskortRepository.lagrePollingstatus(usendtEndring.jobbsokerHendelseDbId)
        log.info("Sendt aktivitetskort-oppdatering for treff ${usendtEndring.rekrutteringstreffUuid} til jobbsøker")
    }

    private fun harRelevanteEndringer(endringer: EndringerDto): Boolean {
        return endringer.tittel != null ||
               endringer.fraTid != null ||
               endringer.tilTid != null ||
               endringer.postnummer != null ||
               endringer.poststed != null ||
               endringer.gateadresse != null
    }

    private fun verifiserEndringer(endringer: EndringerDto, treff: no.nav.toi.rekrutteringstreff.Rekrutteringstreff): VerificationResult {
        val feil = mutableListOf<String>()

        endringer.tittel?.let {
            if (it.nyVerdi != treff.tittel) {
                feil.add("tittel: forventet '${it.nyVerdi}', faktisk '${treff.tittel}'")
            }
        }

        endringer.fraTid?.let {
            val nyVerdiParsed = it.nyVerdi?.let { v -> java.time.ZonedDateTime.parse(v) }
            if (nyVerdiParsed != null && treff.fraTid != null) {
                // Sammenlign med trunkering til millisekunder for å unngå database presisjonsproblemer
                if (nyVerdiParsed.truncatedTo(java.time.temporal.ChronoUnit.MILLIS) !=
                    treff.fraTid.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)) {
                    feil.add("fraTid: forventet '${it.nyVerdi}', faktisk '${treff.fraTid}'")
                }
            }
        }

        endringer.tilTid?.let {
            val nyVerdiParsed = it.nyVerdi?.let { v -> java.time.ZonedDateTime.parse(v) }
            if (nyVerdiParsed != null && treff.tilTid != null) {
                // Sammenlign med trunkering til millisekunder for å unngå database presisjonsproblemer
                if (nyVerdiParsed.truncatedTo(java.time.temporal.ChronoUnit.MILLIS) !=
                    treff.tilTid.truncatedTo(java.time.temporal.ChronoUnit.MILLIS)) {
                    feil.add("tilTid: forventet '${it.nyVerdi}', faktisk '${treff.tilTid}'")
                }
            }
        }

        endringer.postnummer?.let {
            if (it.nyVerdi != treff.postnummer) {
                feil.add("postnummer: forventet '${it.nyVerdi}', faktisk '${treff.postnummer}'")
            }
        }

        endringer.poststed?.let {
            if (it.nyVerdi != treff.poststed) {
                feil.add("poststed: forventet '${it.nyVerdi}', faktisk '${treff.poststed}'")
            }
        }

        endringer.gateadresse?.let {
            if (it.nyVerdi != treff.gateadresse) {
                feil.add("gateadresse: forventet '${it.nyVerdi}', faktisk '${treff.gateadresse}'")
            }
        }

        return if (feil.isEmpty()) {
            VerificationResult(erGyldig = true)
        } else {
            VerificationResult(erGyldig = false, feilmelding = feil.joinToString(", "))
        }
    }

    private data class VerificationResult(
        val erGyldig: Boolean,
        val feilmelding: String? = null
    )
}
