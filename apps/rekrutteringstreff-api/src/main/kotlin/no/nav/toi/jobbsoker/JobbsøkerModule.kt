package no.nav.toi.jobbsoker

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.javalin.Javalin
import no.nav.toi.JacksonConfig
import no.nav.toi.LeaderElectionInterface
import no.nav.toi.config.AppConfig
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortFeilLytter
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortRepository
import no.nav.toi.jobbsoker.aktivitetskort.JobbsøkerhendelserScheduler
import no.nav.toi.jobbsoker.synlighet.SynlighetsBehovLytter
import no.nav.toi.jobbsoker.synlighet.SynlighetsBehovScheduler
import no.nav.toi.jobbsoker.synlighet.SynlighetsLytter
import no.nav.toi.kandidatsok.KandidatsøkKlient
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.eier.EierService
import no.nav.toi.jobbsoker.sok.JobbsøkerSokRepository
import org.koin.dsl.module
import javax.sql.DataSource

val jobbsøkerModule get() = module {
    single { JobbsøkerRepository(get<DataSource>(), JacksonConfig.mapper) }
    single { JobbsøkerSokRepository(get<DataSource>()) }
    single { JobbsøkerService(get<DataSource>(), get<JobbsøkerRepository>(), get<JobbsøkerSokRepository>()) }
    single { AktivitetskortRepository(get<DataSource>()) }

    single {
        val config = get<AppConfig>()
        KandidatsøkKlient(
            kandidatsokApiUrl = config.kandidatsokApiUrl,
            kandidatsokScope = config.kandidatsokScope,
            accessTokenClient = get(),
            httpClient = get(),
        )
    }

    // Kontrollere — registrerer ruter i init-blokken
    single { JobbsøkerController(get<JobbsøkerService>(), get<EierService>(), get<Javalin>()) }
    single { JobbsøkerInnloggetBorgerController(get<JobbsøkerService>(), get<Javalin>()) }
    single { JobbsøkerOutboundController(get<JobbsøkerRepository>(), get<KandidatsøkKlient>(), get<EierService>(), get<Javalin>()) }

    // Schedulere
    single {
        JobbsøkerhendelserScheduler(
            dataSource = get(),
            aktivitetskortRepository = get(),
            rekrutteringstreffRepository = get<RekrutteringstreffRepository>(),
            rapidsConnection = get<RapidsConnection>(),
            objectMapper = JacksonConfig.mapper,
            leaderElection = get<LeaderElectionInterface>(),
        )
    }

    single {
        SynlighetsBehovScheduler(
            jobbsøkerService = get(),
            rapidsConnection = get<RapidsConnection>(),
            leaderElection = get<LeaderElectionInterface>(),
        )
    }

    // Rapids & Rivers-lyttere — registrerer seg med rapidsConnection i init-blokken
    single { AktivitetskortFeilLytter(get<RapidsConnection>(), get<JobbsøkerService>()) }
    single { MinsideVarselSvarLytter(get<RapidsConnection>(), get<JobbsøkerService>(), JacksonConfig.mapper) }
    single { SynlighetsLytter(get<RapidsConnection>(), get<JobbsøkerService>()) }
    single { SynlighetsBehovLytter(get<RapidsConnection>(), get<JobbsøkerService>()) }
}
