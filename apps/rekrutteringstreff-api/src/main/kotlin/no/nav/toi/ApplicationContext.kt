package no.nav.toi

import no.nav.toi.arbeidsgiver.ArbeidsgiverController
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverService
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortFeilLytter
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortRepository
import no.nav.toi.jobbsoker.aktivitetskort.JobbsøkerhendelserScheduler
import no.nav.toi.jobbsoker.synlighet.SynlighetsBehovLytter
import no.nav.toi.jobbsoker.synlighet.SynlighetsBehovScheduler
import no.nav.toi.jobbsoker.synlighet.SynlighetsLytter
import no.nav.toi.rekrutteringstreff.RekrutteringstreffController
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.RekrutteringstreffScheduler
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
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

@Suppress("MemberVisibilityCanBePrivate")
class ApplicationContext(val infra: InfrastructureContext = InfrastructureContext()) {

    // Infrastruktur (delegert fra InfrastructureContext)
    val dataSource get() = infra.dataSource
    val rapidsConnection get() = infra.rapidsConnection
    val authConfigs get() = infra.authConfigs
    val rolleUuidSpesifikasjon get() = infra.rolleUuidSpesifikasjon
    val pilotkontorer get() = infra.pilotkontorer
    val leaderElection get() = infra.leaderElection
    val modiaKlient get() = infra.modiaKlient
    val kandidatsøkKlient get() = infra.kandidatsøkKlient

    // Repositories
    val jobbsøkerRepository = JobbsøkerRepository(infra.dataSource, JacksonConfig.mapper)
    val arbeidsgiverRepository = ArbeidsgiverRepository(infra.dataSource, JacksonConfig.mapper)
    val rekrutteringstreffRepository = RekrutteringstreffRepository(infra.dataSource)
    val eierRepository = EierRepository(infra.dataSource)
    val innleggRepository = InnleggRepository(infra.dataSource)
    val aktivitetskortRepository = AktivitetskortRepository(infra.dataSource)
    val kiLoggRepository = KiLoggRepository(infra.dataSource)
    val sokRepository = RekrutteringstreffSokRepository(infra.dataSource)
    val healthRepository = HealthRepository(infra.dataSource)

    // Services
    val jobbsøkerService = JobbsøkerService(infra.dataSource, jobbsøkerRepository)
    val arbeidsgiverService = ArbeidsgiverService(infra.dataSource, arbeidsgiverRepository, JacksonConfig.mapper)
    val eierService = EierService(eierRepository, rekrutteringstreffRepository, infra.dataSource)
    val rekrutteringstreffService = RekrutteringstreffService(
        infra.dataSource,
        rekrutteringstreffRepository,
        jobbsøkerRepository,
        arbeidsgiverRepository,
        jobbsøkerService,
        eierService
    )
    val innleggService = InnleggService(innleggRepository, rekrutteringstreffService)
    val kiLoggService = KiLoggService(kiLoggRepository)
    val sokService = RekrutteringstreffSokService(sokRepository)

    // Controllere
    val arbeidsgiverController = ArbeidsgiverController(arbeidsgiverService, eierService)
    val rekrutteringstreffController = RekrutteringstreffController(rekrutteringstreffService, eierService, kiLoggService)
    val eierController = EierController(eierService)
    val jobbsøkerController = JobbsøkerController(jobbsøkerService, eierService)
    val jobbsøkerInnloggetBorgerController = JobbsøkerInnloggetBorgerController(jobbsøkerService)
    val jobbsøkerOutboundController = JobbsøkerOutboundController(jobbsøkerRepository, infra.kandidatsøkKlient, eierService)
    val innleggController = InnleggController(innleggService, kiLoggService, eierService)
    val kiController = KiController(kiLoggRepository, OpenAiClient(repo = kiLoggRepository))
    val sokController = RekrutteringstreffSokController(sokService)
    val healthController = HealthController(healthRepository)

    // Schedulere (lazy — har sideeffekter ved start, brukes kun i produksjon)
    val jobbsøkerhendelserScheduler by lazy {
        JobbsøkerhendelserScheduler(
            dataSource = infra.dataSource,
            aktivitetskortRepository = aktivitetskortRepository,
            rekrutteringstreffRepository = rekrutteringstreffRepository,
            rapidsConnection = infra.rapidsConnection,
            objectMapper = JacksonConfig.mapper,
            leaderElection = infra.leaderElection,
        )
    }
    val synlighetsBehovScheduler by lazy {
        SynlighetsBehovScheduler(
            jobbsøkerService = jobbsøkerService,
            rapidsConnection = infra.rapidsConnection,
            leaderElection = infra.leaderElection,
        )
    }
    val rekrutteringstreffOpprydningScheduler by lazy {
        RekrutteringstreffOpprydningScheduler(
            kiLoggService = kiLoggService,
            leaderElection = infra.leaderElection,
        )
    }
    val rekrutteringstreffScheduler by lazy {
        RekrutteringstreffScheduler(rekrutteringstreffService, infra.leaderElection)
    }

    // Rapids & Rivers-lyttere (lazy — registrerer seg selv mot rapidsConnection ved opprettelse)
    private val aktivitetskortFeilLytter by lazy { AktivitetskortFeilLytter(infra.rapidsConnection, jobbsøkerService) }
    private val minsideVarselSvarLytter by lazy { MinsideVarselSvarLytter(infra.rapidsConnection, jobbsøkerService, JacksonConfig.mapper) }
    private val synlighetsLytter by lazy { SynlighetsLytter(infra.rapidsConnection, jobbsøkerService) }
    private val synlighetsBehovLytter by lazy { SynlighetsBehovLytter(infra.rapidsConnection, jobbsøkerService) }

    fun registerLyttere() {
        aktivitetskortFeilLytter
        minsideVarselSvarLytter
        synlighetsLytter
        synlighetsBehovLytter
    }
}
