package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.JobbsøkerHendelsestype
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZonedDateTime
import javax.sql.DataSource

class AktivitetskortRepository(private val dataSource: DataSource) {

    fun hentUsendteInvitasjoner(): List<UsendtInvitasjon> = dataSource.connection.use { connection ->
        val statement = connection.prepareStatement(
            """
            select jh.jobbsoker_hendelse_id as db_id, j.fodselsnummer, rt.id from jobbsoker_hendelse jh
            left join aktivitetskort_polling p on jh.jobbsoker_hendelse_id = p.jobbsoker_hendelse_id
            left join jobbsoker j on jh.jobbsoker_id = j.jobbsoker_id
            left join rekrutteringstreff rt on j.rekrutteringstreff_id = rt.rekrutteringstreff_id
            where p.aktivitetskort_polling_id is null and jh.hendelsestype = ?
            order by jh.tidspunkt
            """
        )
        statement.use {
            it.setString(1, JobbsøkerHendelsestype.INVITERT.name)
            val resultSet = it.executeQuery()
            return@use generateSequence {
                if (resultSet.next()) resultSet.tilUsendtInvitasjon() else null
            }.toList()
        }
    }

    fun hentUsendteHendelse(hendelsestype: JobbsøkerHendelsestype): List<UsendtHendelse> = dataSource.connection.use { connection ->
        val statement = connection.prepareStatement(
            """
            select jh.jobbsoker_hendelse_id as db_id, j.fodselsnummer, rt.id from jobbsoker_hendelse jh
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
                if (resultSet.next()) resultSet.tilUsendtHendelse() else null
            }.toList()
        }
    }

    fun lagrePollingstatus(jobbsokerHendelseDbId: Long) {
        dataSource.connection.use { connection ->
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

    private fun ResultSet.tilUsendtInvitasjon() = UsendtInvitasjon(
        jobbsokerHendelseDbId = getLong("db_id"),
        fnr = getString("fodselsnummer"),
        rekrutteringstreffUuid = getString("id")
    )

    private fun ResultSet.tilUsendtHendelse() = UsendtHendelse(
        jobbsokerHendelseDbId = getLong("db_id"),
        fnr = getString("fodselsnummer"),
        rekrutteringstreffUuid = getString("id")
    )
}

data class UsendtInvitasjon(
    val jobbsokerHendelseDbId: Long,
    val fnr: String,
    val rekrutteringstreffUuid: String
)

data class UsendtHendelse(
    val jobbsokerHendelseDbId: Long,
    val fnr: String,
    val rekrutteringstreffUuid: String
)