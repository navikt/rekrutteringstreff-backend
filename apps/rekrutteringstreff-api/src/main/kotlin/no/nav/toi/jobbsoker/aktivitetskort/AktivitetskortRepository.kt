package no.nav.toi.jobbsoker.aktivitetskort

import java.sql.ResultSet
import java.time.ZonedDateTime
import javax.sql.DataSource

class AktivitetskortRepository(private val dataSource: DataSource) {

    fun hentUsendteInvitasjoner(): List<UsendtInvitasjon> = dataSource.connection.use { connection ->
        val statement = connection.prepareStatement(
            """
            select j.db_id, j.jobbsoker_aktor_id, j.rekrutteringstreff_uuid from jobbsoker_hendelse j
            left join aktivitetskort_polling p on j.db_id = p.jobbsoker_hendelse_db_id
            where p.db_id is null and j.hendelse_type = 'INVITASJON'
            order by j.tidspunkt
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
                it.setObject(2, ZonedDateTime.now())
                it.executeUpdate()
            }
        }
    }

    private fun ResultSet.tilUsendtInvitasjon() = UsendtInvitasjon(
        jobbsokerHendelseDbId = getLong("db_id"),
        aktørId = getString("jobbsoker_aktor_id"),
        rekrutteringstreffUuid = getString("rekrutteringstreff_uuid")
    )
}

data class UsendtInvitasjon(
    val jobbsokerHendelseDbId: Long,
    val aktørId: String,
    val rekrutteringstreffUuid: String
)