package no.nav.toi

import org.flywaydb.core.Flyway
import java.sql.Timestamp
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

class Repository(databaseConfig: DatabaseConfig) {
    private val dataSource = databaseConfig.lagDatasource()
    fun lagreRekrutteringstreffInvitasjon(
        fnr: String,
        rekrutteringstreffId: UUID,
        tittel: String,
        beskrivelse: String,
        startDato: LocalDate,
        sluttDato: LocalDate,
        endretAv: String,
        endretAvType: String,
        endretTidspunkt: ZonedDateTime
    ): UUID {
        val aktivitietskortId = UUID.randomUUID()
        dataSource.connection.use {
            it.prepareStatement(
                """
                INSERT INTO aktivitetskort (
                    fnr, tittel, beskrivelse, start_dato, slutt_dato, 
                    aktivitetskort_id, rekrutteringstreff_id, 
                    aktivitets_status, endret_av, endret_av_type, endret_tidspunkt
                ) VALUES (?, ?, ?, ?, ?, ?, ?, null, ?, ?, ?)
                """.trimIndent()
            ).apply {
                setString(1, fnr)
                setString(2, tittel)
                setString(3, beskrivelse)
                setObject(4, startDato)
                setObject(5, sluttDato)
                setObject(6, aktivitietskortId)
                setObject(7, rekrutteringstreffId)
                setString(8, endretAv)
                setString(9, endretAvType)
                setTimestamp(10, Timestamp.valueOf(endretTidspunkt.toLocalDateTime()))
            }.executeUpdate()
        }
        return aktivitietskortId
    }

    init {
        Flyway.configure()
            .loggers("slf4j")
            .dataSource(databaseConfig.lagDatasource())
            .load()
            .migrate()
    }
}