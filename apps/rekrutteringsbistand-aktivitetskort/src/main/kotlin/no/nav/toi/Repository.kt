package no.nav.toi

import org.flywaydb.core.Flyway

class Repository(databaseConfig: DatabaseConfig) {
    init {
        Flyway.configure()
            .loggers("slf4j")
            .dataSource(databaseConfig.lagDatasource())
            .load()
            .migrate()
    }
}