package no.nav.toi.rekrutteringstreff

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object DataSourceFactory {
    fun createDataSource(): DataSource {
        val config = HikariConfig().apply {
            jdbcUrl = "jdbc:postgresql://localhost:5432/rekrrutteringstreffdb"
            username = "todo"
            password = "todo"
            maximumPoolSize = 3
            isAutoCommit = true
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
        }
        return HikariDataSource(config)
    }
}
