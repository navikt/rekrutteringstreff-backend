package no.nav.toi.jobbsoker.aktivitetskort

import no.nav.toi.JobbsøkerHendelsestype
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.ZonedDateTime
import javax.sql.DataSource

class AktivitetskortInvitasjonRepository(private val dataSource: DataSource) {

    fun hentUsendteInvitasjoner(): List<UsendtInvitasjon> = dataSource.connection.use { connection ->
        val statement = connection.prepareStatement(
            """
            select jh.db_id, j.fodselsnummer, rt.id from jobbsoker_hendelse jh
            left join aktivitetskort_polling p on jh.db_id = p.jobbsoker_hendelse_db_id
            left join jobbsoker j on jh.jobbsoker_db_id = j.db_id
            left join rekrutteringstreff rt on j.treff_db_id = rt.db_id
            where p.db_id is null and jh.hendelsestype = '${JobbsøkerHendelsestype.INVITER.name}'
            order by jh.tidspunkt
            """
        )
        statement.use {
            val resultSet = it.executeQuery()
            return@use generateSequence {
                if (resultSet.next()) resultSet.tilUsendtInvitasjon() else null
            }.toList()
        }
    }

    fun lagrePollingstatus(jobbsokerHendelseDbId: Long) {
        dataSource.connection.use { connection ->
            val statement = connection.prepareStatement(
                "insert into aktivitetskort_polling(jobbsoker_hendelse_db_id, sendt_tidspunkt) values (?, ?)"
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
}

data class UsendtInvitasjon(
    val jobbsokerHendelseDbId: Long,
    val fnr: String,
    val rekrutteringstreffUuid: String
)