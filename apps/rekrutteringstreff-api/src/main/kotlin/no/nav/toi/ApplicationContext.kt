package no.nav.toi

import no.nav.arbeidsgiver.toi.logging.log
import no.nav.toi.arbeidsgiver.ArbeidsgiverController
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverService
import no.nav.toi.formidling.FormidlingController
import no.nav.toi.formidling.FormidlingRepository
import no.nav.toi.formidling.FormidlingService
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
import no.nav.toi.rekrutteringstreff.ki.OpenAiService
import no.nav.toi.rekrutteringstreff.opprydning.RekrutteringstreffOpprydningScheduler
import no.nav.toi.rekrutteringstreff.sok.RekrutteringstreffSokController
import no.nav.toi.rekrutteringstreff.sok.RekrutteringstreffSokRepository
import no.nav.toi.rekrutteringstreff.sok.RekrutteringstreffSokService
import no.nav.toi.statistikk.StatistikkController
import no.nav.toi.statistikk.StatistikkRepository
import no.nav.toi.statistikk.StatistikkService
import no.nav.toi.treffgjennomføring.TreffgjennomføringController
import no.nav.toi.oppfølging.OppfølgingController
import no.nav.toi.oppfølging.OppfølgingRepository
import no.nav.toi.oppfølging.OppfølgingService
import no.nav.toi.treffgjennomføring.TreffgjennomføringReader
import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.jobbsoker.oppmøte.OppmøteService
import no.nav.toi.treffgjennomføring.StegRepository
import no.nav.toi.treffgjennomføring.TreffgjennomføringWriter
import no.nav.toi.treffgjennomføring.matching.MatchingRepository
import no.nav.toi.treffgjennomføring.matching.MatchingService
import no.nav.toi.treffgjennomføring.møteplan.MøteplanRepository
import no.nav.toi.treffgjennomføring.møteplan.MøteplanService
import no.nav.toi.treffgjennomføring.TreffgjennomføringService
import no.nav.toi.treffgjennomføring.TreffkontekstRepository

@Suppress("MemberVisibilityCanBePrivate")
class ApplicationContext(val infra: InfrastructureContext = InfrastructureContext()) {

    val dataSource get() = infra.dataSource
    val rapidsConnection get() = infra.rapidsConnection
    val authConfigs get() = infra.authConfigs
    val httpClient get() = infra.httpClient
    val openAiProperties get() = infra.openAiProperties
    val rolleUuidSpesifikasjon get() = infra.rolleUuidSpesifikasjon
    val leaderElection get() = infra.leaderElection
    val modiaKlient get() = infra.modiaKlient
    val kandidatsøkKlient get() = infra.kandidatsøkKlient
    val openAiKlient get() = infra.openAiKlient
    val stillingKlient get() = infra.stillingKlient
    val kandidatKlient get() = infra.kandidatKlient

    val jobbsøkerRepository = JobbsøkerRepository(infra.dataSource, JacksonConfig.mapper)
    val arbeidsgiverRepository = ArbeidsgiverRepository(infra.dataSource, JacksonConfig.mapper)
    val rekrutteringstreffRepository = RekrutteringstreffRepository(infra.dataSource)
    val eierRepository = EierRepository(infra.dataSource)
    val innleggRepository = InnleggRepository(infra.dataSource)
    val aktivitetskortRepository = AktivitetskortRepository(infra.dataSource)
    val kiLoggRepository = KiLoggRepository(infra.dataSource)
    val sokRepository = RekrutteringstreffSokRepository(infra.dataSource)
    val healthRepository = HealthRepository(infra.dataSource)
    val formidlingRepository = FormidlingRepository(infra.dataSource)
    val statistikkRepository = StatistikkRepository(infra.dataSource)
    val treffkontekstRepository = TreffkontekstRepository()
    val stegRepository = StegRepository()
    val oppmøteRepository = OppmøteRepository()
    val møteplanRepository = MøteplanRepository()
    val matchingRepository = MatchingRepository()
    val oppfølgingRepository = OppfølgingRepository()
    val treffgjennomføringReader = TreffgjennomføringReader(
        stegRepository, oppmøteRepository, møteplanRepository, matchingRepository, oppfølgingRepository,
    )
    val treffgjennomføringWriter = TreffgjennomføringWriter(
        infra.dataSource, treffkontekstRepository, stegRepository, treffgjennomføringReader,
    )
    val hendelseWriter = HendelseWriter(jobbsøkerRepository, arbeidsgiverRepository, rekrutteringstreffRepository, JacksonConfig.mapper)

    val jobbsøkerService = JobbsøkerService(
        dataSource = infra.dataSource,
        jobbsøkerRepository = jobbsøkerRepository,
        kandidatsøkKlient = infra.kandidatsøkKlient,
    )
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
    val openAiService = OpenAiService(openAiKlient, kiLoggRepository, openAiProperties)
    val kiLoggService = KiLoggService(kiLoggRepository)
    val sokService = RekrutteringstreffSokService(sokRepository)
    val formidlingService = FormidlingService(infra.dataSource, formidlingRepository, arbeidsgiverService, jobbsøkerService, rekrutteringstreffRepository, stillingKlient, kandidatKlient)
    val statistikkService = StatistikkService(statistikkRepository)
    val treffgjennomføringService = TreffgjennomføringService(
        dataSource = infra.dataSource,
        kontekstRepository = treffkontekstRepository,
        reader = treffgjennomføringReader,
        writer = treffgjennomføringWriter,
        stegRepository = stegRepository,
    )
    val oppmøteService = OppmøteService(
        treffgjennomføringWriter = treffgjennomføringWriter,
        oppmøteRepository = oppmøteRepository,
        matchingRepository = matchingRepository,
        møteplanRepository = møteplanRepository,
        oppfølgingRepository = oppfølgingRepository,
        jobbsøkerService = jobbsøkerService,
        hendelseWriter = hendelseWriter,
    )
    val møteplanService = MøteplanService(
        writer = treffgjennomføringWriter,
        repository = møteplanRepository,
        oppmøteRepository = oppmøteRepository,
        stegRepository = stegRepository,
        hendelseWriter = hendelseWriter,
    )
    val matchingService = MatchingService(
        writer = treffgjennomføringWriter,
        repository = matchingRepository,
        oppmøteRepository = oppmøteRepository,
        oppfølgingRepository = oppfølgingRepository,
        stegRepository = stegRepository,
        hendelseWriter = hendelseWriter,
    )
    val oppfølgingService = OppfølgingService(
        writer = treffgjennomføringWriter,
        repository = oppfølgingRepository,
        oppmøteRepository = oppmøteRepository,
        stegRepository = stegRepository,
        hendelser = hendelseWriter,
    )

    val arbeidsgiverController = ArbeidsgiverController(arbeidsgiverService, eierService)
    val rekrutteringstreffController = RekrutteringstreffController(rekrutteringstreffService, eierService, kiLoggService)
    val eierController = EierController(eierService)
    val jobbsøkerController = JobbsøkerController(jobbsøkerService, eierService, infra.modiaKlient)
    val jobbsøkerInnloggetBorgerController = JobbsøkerInnloggetBorgerController(jobbsøkerService)
    val jobbsøkerOutboundController = JobbsøkerOutboundController(jobbsøkerRepository, infra.kandidatsøkKlient, eierService)
    val innleggController = InnleggController(innleggService, kiLoggService, eierService)
    val kiController = KiController(kiLoggRepository, openAiService)
    val sokController = RekrutteringstreffSokController(sokService)
    val healthController = HealthController(healthRepository)
    val formidlingController = FormidlingController(formidlingService, eierService, infra.modiaKlient)
    val statistikkController = StatistikkController(statistikkService)
    val treffgjennomføringController = TreffgjennomføringController(treffgjennomføringService, møteplanService, matchingService, oppmøteService, eierService)
    val oppfølgingController = OppfølgingController(oppfølgingService, eierService)

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

    fun startSchedulere() {
        log.info("Starting schedulers")
        jobbsøkerhendelserScheduler.start()
        synlighetsBehovScheduler.start()
        rekrutteringstreffOpprydningScheduler.start()
        rekrutteringstreffScheduler.start()
    }

    fun stopSchedulere() {
        jobbsøkerhendelserScheduler.stop()
        synlighetsBehovScheduler.stop()
        rekrutteringstreffOpprydningScheduler.stop()
        rekrutteringstreffScheduler.stop()
    }
}
