package no.nav.toi.arbeidsgiver

import io.javalin.Javalin
import no.nav.toi.JacksonConfig
import no.nav.toi.rekrutteringstreff.eier.EierService
import org.koin.dsl.module
import javax.sql.DataSource

val arbeidsgiverModule get() = module {
    single { ArbeidsgiverRepository(get<DataSource>(), JacksonConfig.mapper) }
    single { ArbeidsgiverService(get<DataSource>(), get<ArbeidsgiverRepository>()) }
    single { ArbeidsgiverController(get<ArbeidsgiverService>(), get<EierService>(), get<Javalin>()) }
}
