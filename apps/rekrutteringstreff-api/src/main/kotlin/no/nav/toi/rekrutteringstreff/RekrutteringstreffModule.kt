package no.nav.toi.rekrutteringstreff

import io.javalin.Javalin
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.JobbsøkerService
import no.nav.toi.rekrutteringstreff.eier.EierController
import no.nav.toi.rekrutteringstreff.eier.EierRepository
import no.nav.toi.rekrutteringstreff.eier.EierService
import no.nav.toi.rekrutteringstreff.innlegg.InnleggController
import no.nav.toi.rekrutteringstreff.innlegg.InnleggRepository
import no.nav.toi.rekrutteringstreff.innlegg.InnleggService
import no.nav.toi.rekrutteringstreff.ki.KiController
import no.nav.toi.rekrutteringstreff.ki.KiLoggRepository
import no.nav.toi.rekrutteringstreff.ki.KiLoggService
import no.nav.toi.rekrutteringstreff.ki.OpenAiClient
import no.nav.toi.rekrutteringstreff.opprydning.RekrutteringstreffOpprydningScheduler
import no.nav.toi.rekrutteringstreff.sok.RekrutteringstreffSokController
import no.nav.toi.rekrutteringstreff.sok.RekrutteringstreffSokRepository
import no.nav.toi.rekrutteringstreff.sok.RekrutteringstreffSokService
import org.koin.dsl.module
import javax.sql.DataSource

val rekrutteringstreffModule get() = module {
    // Repositories
    single { RekrutteringstreffRepository(get<DataSource>()) }
    single { EierRepository(get<DataSource>()) }
    single { InnleggRepository(get<DataSource>()) }
    single { KiLoggRepository(get<DataSource>()) }
    single { RekrutteringstreffSokRepository(get<DataSource>()) }

    // Services
    single {
        EierService(
            eierRepository = get(),
            rekrutteringstreffRepository = get(),
            dataSource = get(),
        )
    }
    single {
        RekrutteringstreffService(
            dataSource = get(),
            rekrutteringstreffRepository = get(),
            jobbsøkerRepository = get<JobbsøkerRepository>(),
            arbeidsgiverRepository = get<ArbeidsgiverRepository>(),
            jobbsøkerService = get<JobbsøkerService>(),
            eierService = get(),
        )
    }
    single { InnleggService(get<InnleggRepository>(), get<RekrutteringstreffService>()) }
    single { KiLoggService(get<KiLoggRepository>()) }
    single { RekrutteringstreffSokService(get<RekrutteringstreffSokRepository>()) }

    // Kontrollere — registrerer ruter i init-blokken
    single {
        RekrutteringstreffController(
            rekrutteringstreffService = get(),
            eierService = get(),
            kiLoggService = get(),
            javalin = get<Javalin>(),
        )
    }
    single { EierController(get<EierService>(), get<Javalin>()) }
    single {
        InnleggController(
            innleggService = get(),
            kiLoggService = get(),
            eierService = get(),
            javalin = get<Javalin>(),
        )
    }
    single { RekrutteringstreffSokController(get<RekrutteringstreffSokService>(), get<Javalin>()) }
    single {
        KiController(
            kiLoggRepository = get(),
            openAiClient = OpenAiClient(repo = get()),
            javalin = get<Javalin>(),
        )
    }

    // Schedulere
    single {
        RekrutteringstreffOpprydningScheduler(
            kiLoggService = get(),
            leaderElection = get<LeaderElectionInterface>(),
        )
    }
    single {
        RekrutteringstreffScheduler(
            rekrutteringstreffService = get(),
            leaderElection = get<LeaderElectionInterface>(),
        )
    }
}
