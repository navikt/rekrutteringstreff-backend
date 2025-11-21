package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.executeInTransaction
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class AktivitetskortRepository(private val dataSource: DataSource) {

    fun hentUsendteHendelse(hendelsestype: JobbsøkerHendelsestype): List<UsendtHendelse> = dataSource.connection.use { connection ->
        val statement = connection.prepareStatement(
            """
            select jh.jobbsoker_hendelse_id as db_id, jh.id as hendelse_id, j.fodselsnummer, rt.id, jh.hendelse_data, jh.aktøridentifikasjon from jobbsoker_hendelse jh
            left join aktivitetskort_polling p on jh.jobbsoker_hendelse_id = p.jobbsoker_hendelse_id
            left join jobbsoker j on jh.jobbsoker_id = j.jobbsoker_id
            left join rekrutteringstreff rt on j.rekrutteringstreff_id = rt.rekrutteringstreff_id
            where p.aktivitetskort_polling_id is null and jh.hendelsestype = ?
            order by jh.tidspunkt
            """
        )
        statement.use {
            it.setString(1, hendelsestype.name)
            val resultSet = it.executeQuery()
            return@use generateSequence {
                if (resultSet.next()) {
                    UsendtHendelse(
                        jobbsokerHendelseDbId = resultSet.getLong("db_id"),
                        hendelseId = resultSet.getObject("hendelse_id", UUID::class.java),
                        fnr = resultSet.getString("fodselsnummer"),
                        rekrutteringstreffUuid = resultSet.getString("id"),
                        hendelseData = resultSet.getString("hendelse_data"),
                        aktøridentifikasjon = resultSet.getString("aktøridentifikasjon")
                    )
                } else null
            }.toList()
        }
    }

    fun lagrePollingstatus(jobbsokerHendelseDbId: Long) {
        dataSource.connection.use { connection ->
            lagrePollingstatus(jobbsokerHendelseDbId, connection)
        }
    }

    fun lagrePollingstatus(jobbsokerHendelseDbId: Long, connection: Connection) {
        val statement = connection.prepareStatement(
            "insert into aktivitetskort_polling(jobbsoker_hendelse_id, sendt_tidspunkt) values (?, ?)"
        )
        statement.use {
            it.setLong(1, jobbsokerHendelseDbId)
            it.setTimestamp(2, Timestamp.from(ZonedDateTime.now().toInstant()))
            it.executeUpdate()
        }
    }
}

data class UsendtHendelse(
    val jobbsokerHendelseDbId: Long,
    val hendelseId: UUID,
    val fnr: String,
    val rekrutteringstreffUuid: String,
    val hendelseData: String? = null,
    val aktøridentifikasjon: String?
)
