package no.nav.toi.jobbsoker.oppmøte

import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.tilListe
import java.sql.Connection

data class Deltakernummer(val personTreffId: PersonTreffId, val deltakernummer: Int)

class OppmøteRepository {

    /**
     * Fremmøtte er de som har registrert oppmøte som ikke er fjernet. Det leses fra hendelsene, ikke
     * statusen, fordi statusen viser «Fått jobb» foran «Møtt opp». «Fått jobb» alene regnes ikke som
     * fremmøtt, siden oppmøte ikke er obligatorisk.
     */
    fun hentFremmøtteJobbsøkere(connection: Connection, treffDbId: Long): List<PersonTreffId> {
        val sql = """
            SELECT j.id::text
            FROM jobbsoker j
            LEFT JOIN deltakernummer d
                ON d.jobbsoker_id = j.jobbsoker_id AND d.rekrutteringstreff_id = j.rekrutteringstreff_id
            WHERE j.rekrutteringstreff_id = ?
              AND j.status != ?
              AND (
                  SELECT jh.hendelsestype
                  FROM jobbsoker_hendelse jh
                  WHERE jh.jobbsoker_id = j.jobbsoker_id
                    AND jh.hendelsestype IN (?, ?)
                  ORDER BY jh.tidspunkt DESC, jh.jobbsoker_hendelse_id DESC
                  LIMIT 1
              ) = ?
            ORDER BY d.deltakernummer NULLS LAST, j.jobbsoker_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setString(2, JobbsøkerStatus.SLETTET.name)
            stmt.setString(3, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE.name)
            stmt.setString(4, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE_FJERNET.name)
            stmt.setString(5, JobbsøkerHendelsestype.REGISTRERT_OPPMØTE.name)
            stmt.executeQuery().use { rs -> rs.tilListe { PersonTreffId(it.getString(1)) } }
        }
    }

    fun hentDeltakernumre(connection: Connection, treffDbId: Long): List<Deltakernummer> {
        val sql = """
            SELECT j.id::text, d.deltakernummer
            FROM deltakernummer d
            JOIN jobbsoker j ON j.jobbsoker_id = d.jobbsoker_id
            WHERE d.rekrutteringstreff_id = ? AND j.status != 'SLETTET'
            ORDER BY d.deltakernummer
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe { Deltakernummer(PersonTreffId(it.getString(1)), it.getInt(2)) }
            }
        }
    }

    fun tildelDeltakernummer(connection: Connection, treffDbId: Long, jobbsøkerId: Long): Int {
        val sql = """
            INSERT INTO deltakernummer (rekrutteringstreff_id, jobbsoker_id, deltakernummer)
            SELECT ?, ?, COALESCE(MAX(deltakernummer), 0) + 1 FROM deltakernummer WHERE rekrutteringstreff_id = ?
            ON CONFLICT (rekrutteringstreff_id, jobbsoker_id) DO NOTHING
            RETURNING deltakernummer
        """.trimIndent()
        val nytt = connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setLong(2, jobbsøkerId)
            stmt.setLong(3, treffDbId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }
        return nytt ?: hentDeltakernummerFor(connection, treffDbId, jobbsøkerId)
    }

    private fun hentDeltakernummerFor(connection: Connection, treffDbId: Long, jobbsøkerId: Long): Int {
        val sql = "SELECT deltakernummer FROM deltakernummer WHERE rekrutteringstreff_id = ? AND jobbsoker_id = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setLong(2, jobbsøkerId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }
}
