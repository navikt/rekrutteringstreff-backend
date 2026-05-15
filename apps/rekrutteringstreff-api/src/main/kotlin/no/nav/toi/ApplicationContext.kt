package no.nav.toi

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.toi.arbeidsgiver.ArbeidsgiverController
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverService
import no.nav.toi.jobbsoker.*
import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortRepository
import no.nav.toi.jobbsoker.aktivitetskort.JobbsøkerhendelserScheduler
import no.nav.toi.jobbsoker.synlighet.SynlighetsBehovScheduler
import no.nav.toi.kandidatsok.KandidatsøkKlient
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
import no.nav.toi.rekrutteringstreff.tilgangsstyring.ModiaKlient
import java.net.http.HttpClient
import java.util.*
import javax.sql.DataSource

@Suppress("MemberVisibilityCanBePrivate")
open class ApplicationContext(
    val dataSource: DataSource,
    val rapidsConnection: RapidsConnection,
    private val env: Map<String, String> = System.getenv()
) {
    private fun getenv(key: String): String =
        env[key] ?: throw NullPointerException("Det finnes ingen miljøvariabel med navn [$key]")

    // Konfigurasjon (lazy slik at tester kan override uten at env-oppslag trigges)
    open val authConfigs: List<AuthenticationConfiguration> by lazy {
        listOfNotNull(
            AuthenticationConfiguration(
                audience = getenv("AZURE_APP_CLIENT_ID"),
                issuer = getenv("AZURE_OPENID_CONFIG_ISSUER"),
                jwksUri = getenv("AZURE_OPENID_CONFIG_JWKS_URI")
            ),
            AuthenticationConfiguration(
                audience = getenv("TOKEN_X_CLIENT_ID"),
                issuer = getenv("TOKEN_X_ISSUER"),
                jwksUri = getenv("TOKEN_X_JWKS_URI")
            ),
            if (env["NAIS_CLUSTER_NAME"] == "dev-gcp")
                AuthenticationConfiguration(
                    audience = "dev-gcp:toi:rekrutteringstreff-api",
                    issuer = "https://fakedings.intern.dev.nav.no/fake",
                    jwksUri = "https://fakedings.intern.dev.nav.no/fake/jwks",
                ) else null
        )
    }

    open val rolleUuidSpesifikasjon: RolleUuidSpesifikasjon by lazy {
        RolleUuidSpesifikasjon(
            jobbsøkerrettet = UUID.fromString(getenv("REKRUTTERINGSBISTAND_JOBBSOKERRETTET")),
            arbeidsgiverrettet = UUID.fromString(getenv("REKRUTTERINGSBISTAND_ARBEIDSGIVERRETTET")),
            utvikler = UUID.fromString(getenv("REKRUTTERINGSBISTAND_UTVIKLER"))
        )
    }

    open val pilotkontorer: List<String> by lazy { getenv("PILOTKONTORER").split(",").map { it.trim() } }

    open val leaderElection: LeaderElectionInterface by lazy { LeaderElection() }

    // Infrastruktur (lazy for å unngå env-oppslag i tester)
    open val httpClient: HttpClient by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build()
    }

    open val accessTokenClient: AccessTokenClient by lazy {
        AccessTokenClient(
            secret = getenv("AZURE_APP_CLIENT_SECRET"),
            clientId = getenv("AZURE_APP_CLIENT_ID"),
            azureUrl = getenv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
            httpClient = httpClient
        )
    }

    // Klienter
    open val modiaKlient: ModiaKlient by lazy {
        ModiaKlient(
            modiaContextHolderUrl = getenv("MODIACONTEXTHOLDER_URL"),
            modiaContextHolderScope = getenv("MODIACONTEXTHOLDER_SCOPE"),
            accessTokenClient = accessTokenClient,
            httpClient = httpClient,
        )
    }

    open val kandidatsøkKlient: KandidatsøkKlient by lazy {
        KandidatsøkKlient(
            kandidatsokApiUrl = getenv("KANDIDATSOK_API_URL"),
            kandidatsokScope = getenv("KANDIDATSOK_API_SCOPE"),
            accessTokenClient = accessTokenClient,
            httpClient = httpClient
        )
    }

    // Repositories
    open val jobbsøkerRepository: JobbsøkerRepository = JobbsøkerRepository(dataSource, JacksonConfig.mapper)
    open val arbeidsgiverRepository: ArbeidsgiverRepository = ArbeidsgiverRepository(dataSource, JacksonConfig.mapper)
    open val rekrutteringstreffRepository: RekrutteringstreffRepository = RekrutteringstreffRepository(dataSource)
    open val eierRepository: EierRepository = EierRepository(dataSource)
    open val innleggRepository: InnleggRepository = InnleggRepository(dataSource)
    open val aktivitetskortRepository: AktivitetskortRepository = AktivitetskortRepository(dataSource)
    open val kiLoggRepository: KiLoggRepository = KiLoggRepository(dataSource)
    open val sokRepository: RekrutteringstreffSokRepository = RekrutteringstreffSokRepository(dataSource)
    open val healthRepository: HealthRepository = HealthRepository(dataSource)

    // Services
    open val jobbsøkerService: JobbsøkerService = JobbsøkerService(dataSource, jobbsøkerRepository)
    open val arbeidsgiverService: ArbeidsgiverService = ArbeidsgiverService(dataSource, arbeidsgiverRepository, JacksonConfig.mapper)
    open val eierService: EierService = EierService(eierRepository, rekrutteringstreffRepository, dataSource)
    open val rekrutteringstreffService: RekrutteringstreffService = RekrutteringstreffService(
        dataSource,
        rekrutteringstreffRepository,
        jobbsøkerRepository,
        arbeidsgiverRepository,
        jobbsøkerService,
        eierService
    )
    open val innleggService: InnleggService = InnleggService(innleggRepository, rekrutteringstreffService)
    open val kiLoggService: KiLoggService = KiLoggService(kiLoggRepository)
    open val sokService: RekrutteringstreffSokService = RekrutteringstreffSokService(sokRepository)

    // Controllere (lazy fordi de avhenger av services)
    open val arbeidsgiverController by lazy { ArbeidsgiverController(arbeidsgiverService, eierService) }
    open val rekrutteringstreffController by lazy { RekrutteringstreffController(rekrutteringstreffService, eierService, kiLoggService) }
    open val eierController by lazy { EierController(eierService) }
    open val jobbsøkerController by lazy { JobbsøkerController(jobbsøkerService, eierService) }
    open val jobbsøkerInnloggetBorgerController by lazy { JobbsøkerInnloggetBorgerController(jobbsøkerService) }
    open val jobbsøkerOutboundController by lazy { JobbsøkerOutboundController(jobbsøkerRepository, kandidatsøkKlient, eierService) }
    open val innleggController by lazy { InnleggController(innleggService, kiLoggService, eierService) }
    open val kiController by lazy { KiController(kiLoggRepository, OpenAiClient(repo = kiLoggRepository)) }
    open val sokController by lazy { RekrutteringstreffSokController(sokService) }
    open val healthController by lazy { HealthController(healthRepository) }

    // Schedulere (lazy fordi de avhenger av services og infrastruktur)
    open val jobbsøkerhendelserScheduler by lazy {
        JobbsøkerhendelserScheduler(
            dataSource = dataSource,
            aktivitetskortRepository = aktivitetskortRepository,
            rekrutteringstreffRepository = rekrutteringstreffRepository,
            rapidsConnection = rapidsConnection,
            objectMapper = JacksonConfig.mapper,
            leaderElection = leaderElection,
        )
    }
    open val synlighetsBehovScheduler by lazy {
        SynlighetsBehovScheduler(
            jobbsøkerService = jobbsøkerService,
            rapidsConnection = rapidsConnection,
            leaderElection = leaderElection,
        )
    }
    open val rekrutteringstreffOpprydningScheduler by lazy {
        RekrutteringstreffOpprydningScheduler(
            kiLoggService = kiLoggService,
            leaderElection = leaderElection,
        )
    }
    open val rekrutteringstreffScheduler by lazy {
        RekrutteringstreffScheduler(rekrutteringstreffService, leaderElection)
    }
}
