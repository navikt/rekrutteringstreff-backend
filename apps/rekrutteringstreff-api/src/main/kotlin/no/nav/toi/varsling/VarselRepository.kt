package no.nav.toi.varsling

import no.nav.toi.JobbsøkerHendelsestype
import javax.sql.DataSource

class VarselRepository(private val dataSource: DataSource) {

    fun hentUsendteVarselHendelser(): List<UsendtVarselHendelse> {
        val sql = """
            SELECT 
                jh.jobbsoker_hendelse_id,
                j.fodselsnummer,
                rt.id as rekrutteringstreff_uuid,
                jh.hendelsestype,
                jh.aktøridentifikasjon
            FROM jobbsoker_hendelse jh
            LEFT JOIN varsling_polling vp ON jh.jobbsoker_hendelse_id = vp.jobbsoker_hendelse_id
            LEFT JOIN jobbsoker j ON jh.jobbsoker_id = j.jobbsoker_id
            LEFT JOIN rekrutteringstreff rt ON j.rekrutteringstreff_id = rt.rekrutteringstreff_id
            WHERE jh.hendelsestype IN (?, ?)
            AND vp.sendt_tidspunkt IS NULL
            ORDER BY jh.jobbsoker_hendelse_id
        """

        return dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, JobbsøkerHendelsestype.INVITERT.name)
                stmt.setString(2, JobbsøkerHendelsestype.TREFF_ENDRET_ETTER_PUBLISERING_NOTIFIKASJON.name)
                stmt.executeQuery().use { rs ->
                    val resultat = mutableListOf<UsendtVarselHendelse>()
                    while (rs.next()) {
                        resultat.add(
                            UsendtVarselHendelse(
                                jobbsokerHendelseDbId = rs.getLong("jobbsoker_hendelse_id"),
                                fnr = rs.getString("fodselsnummer"),
                                rekrutteringstreffUuid = rs.getString("rekrutteringstreff_uuid"),
                                hendelsestype = JobbsøkerHendelsestype.valueOf(rs.getString("hendelsestype")),
                                aktøridentifikasjon = rs.getString("aktøridentifikasjon")
                            )
                        )
                    }
                    resultat
                }
            }
        }
    }

    fun lagreSendtStatus(jobbsokerHendelseDbId: Long) {
        val sql = """
            INSERT INTO varsling_polling (jobbsoker_hendelse_id, sendt_tidspunkt)
            VALUES (?, NOW())
        """

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, jobbsokerHendelseDbId)
                stmt.executeUpdate()
            }
        }
    }
}

data class UsendtVarselHendelse(
    val jobbsokerHendelseDbId: Long,
    val fnr: String,
    val rekrutteringstreffUuid: String,
    val hendelsestype: JobbsøkerHendelsestype,
    val aktøridentifikasjon: String?
)
