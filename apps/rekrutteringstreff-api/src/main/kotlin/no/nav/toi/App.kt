package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.zaxxer.hikari.HikariDataSource
import io.javalin.Javalin
import no.nav.toi.arbeidsgiver.ArbeidsgiverController
import no.nav.toi.arbeidsgiver.arbeidsgiverModule
import no.nav.toi.config.AppConfig
import no.nav.toi.config.infrastrukturModule
import no.nav.toi.config.javalinModule
import no.nav.toi.jobbsoker.JobbsøkerController
import no.nav.toi.jobbsoker.JobbsøkerInnloggetBorgerController
import no.nav.toi.jobbsoker.JobbsøkerOutboundController
import no.nav.toi.jobbsoker.MinsideVarselSvarLytter
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortFeilLytter
import no.nav.toi.jobbsoker.aktivitetskort.JobbsøkerhendelserScheduler
import no.nav.toi.jobbsoker.jobbsøkerModule
import no.nav.toi.jobbsoker.synlighet.SynlighetsBehovLytter
import no.nav.toi.jobbsoker.synlighet.SynlighetsBehovScheduler
import no.nav.toi.jobbsoker.synlighet.SynlighetsLytter
import no.nav.toi.rekrutteringstreff.RekrutteringstreffController
import no.nav.toi.rekrutteringstreff.RekrutteringstreffScheduler
import no.nav.toi.rekrutteringstreff.eier.EierController
import no.nav.toi.rekrutteringstreff.innlegg.InnleggController
import no.nav.toi.rekrutteringstreff.ki.KiController
import no.nav.toi.rekrutteringstreff.opprydning.RekrutteringstreffOpprydningScheduler
import no.nav.toi.rekrutteringstreff.rekrutteringstreffModule
import no.nav.toi.rekrutteringstreff.sok.RekrutteringstreffSokController
import org.flywaydb.core.Flyway
import org.koin.core.Koin
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import java.time.Instant
import java.time.ZoneId.of
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit.MILLIS
import javax.sql.DataSource

class App(private val koin: Koin) {
    private val config: AppConfig by lazy { koin.get() }

    private var jobbsøkerhendelserScheduler: JobbsøkerhendelserScheduler? = null
    private var synlighetsBehovScheduler: SynlighetsBehovScheduler? = null
    private var rekrutteringstreffOpprydningScheduler: RekrutteringstreffOpprydningScheduler? = null
    private var rekrutteringstreffScheduler: RekrutteringstreffScheduler? = null

    fun start() {
        kjørFlywayMigreringer(koin.get())

        koin.get<Javalin>()
        initControllere()
        koin.get<Javalin>().start(config.port)

        jobbsøkerhendelserScheduler = koin.get<JobbsøkerhendelserScheduler>().also { it.start() }
        synlighetsBehovScheduler = koin.get<SynlighetsBehovScheduler>().also { it.start() }
        rekrutteringstreffOpprydningScheduler = koin.get<RekrutteringstreffOpprydningScheduler>().also { it.start() }
        rekrutteringstreffScheduler = koin.get<RekrutteringstreffScheduler>().also { it.start() }

        startRapids()
        log.info("Hele applikasjonen er startet og klar til å motta forespørsler.")
    }

    private fun initControllere() {
        koin.get<HealthController>()
        koin.get<JobbsøkerController>()
        koin.get<JobbsøkerInnloggetBorgerController>()
        koin.get<JobbsøkerOutboundController>()
        // RekrutteringstreffSokController må registreres FØR RekrutteringstreffController fordi
        // Javalin 6 matcher /api/rekrutteringstreff/{id} før /api/rekrutteringstreff/sok
        // dersom {id}-ruten registreres først.
        koin.get<RekrutteringstreffSokController>()
        koin.get<RekrutteringstreffController>()
        koin.get<EierController>()
        koin.get<InnleggController>()
        koin.get<ArbeidsgiverController>()
        koin.get<KiController>()
    }

    private fun startRapids() {
        koin.get<AktivitetskortFeilLytter>()
        koin.get<MinsideVarselSvarLytter>()
        koin.get<SynlighetsLytter>()
        koin.get<SynlighetsBehovLytter>()
        Thread {
            try {
                koin.get<RapidsConnection>().start()
            } catch (e: Exception) {
                log.error("RapidsConnection feilet, avslutter applikasjonen", e)
                System.exit(1)
            }
        }.start()
    }

    fun close() {
        log.info("Shutting down application")
        jobbsøkerhendelserScheduler?.stop()
        synlighetsBehovScheduler?.stop()
        rekrutteringstreffOpprydningScheduler?.stop()
        rekrutteringstreffScheduler?.stop()
        runCatching { koin.get<Javalin>().stop() }
        runCatching { koin.get<RapidsConnection>().stop() }
        runCatching { (koin.get<DataSource>() as? HikariDataSource)?.close() }
        log.info("Application shutdown complete")
    }
}

fun main() {
    val config = AppConfig.fromEnv()
    val koinApp = koinApplication {
        modules(
            module { single { config } },
            infrastrukturModule,
            javalinModule,
            jobbsøkerModule,
            rekrutteringstreffModule,
            arbeidsgiverModule,
        )
    }
    val app = App(koinApp.koin)
    Runtime.getRuntime().addShutdownHook(Thread { app.close() })
    app.start()
}

private fun kjørFlywayMigreringer(dataSource: DataSource) {
    Flyway.configure()
        .dataSource(dataSource)
        .load()
        .migrate()
}

/**
 * Tidspunkt uten nanosekunder, for å unngå at to like tidspunkter blir ulike pga at database og Microsoft Windows håndterer nanos annerledes enn Mac og Linux.
 */
fun nowOslo(): ZonedDateTime = ZonedDateTime.now().atOslo()

fun ZonedDateTime.atOslo(): ZonedDateTime = this.withZoneSameInstant(of("Europe/Oslo")).truncatedTo(MILLIS)

fun Instant.atOslo(): ZonedDateTime = this.atZone(of("Europe/Oslo")).truncatedTo(MILLIS)
