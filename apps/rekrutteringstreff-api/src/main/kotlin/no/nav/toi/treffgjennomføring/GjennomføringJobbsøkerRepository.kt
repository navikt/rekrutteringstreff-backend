package no.nav.toi.treffgjennomføring

import no.nav.toi.jobbsoker.JobbsøkerStatus
import no.nav.toi.jobbsoker.sok.JobbsøkerSorteringsfelt
import no.nav.toi.jobbsoker.sok.JobbsøkerSorteringsretning
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.GjennomføringJobbsøkerDto
import no.nav.toi.treffgjennomføring.dto.GjennomføringJobbsøkereRequestDto
import no.nav.toi.treffgjennomføring.dto.GjennomføringJobbsøkersideDto
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet

class GjennomføringJobbsøkerRepository {

    companion object {
        private const val QUERY_TIMEOUT_SECONDS = 10

        // Slettede er alltid utelatt i viewet. Usynlige tas bare med på WorkOp, og sperrede
        // (adressebeskyttelse) aldri. Vanlige treff er uendret til regelen er avklart i ROS (WO-14).
        private const val VISES =
            "v.treff_id = ? AND (v.er_synlig = true OR (v.treff_kategori = 'WORKOP' AND v.sperret = false))"
    }

    fun hentSide(
        connection: Connection,
        treffId: TreffId,
        request: GjennomføringJobbsøkereRequestDto,
    ): GjennomføringJobbsøkersideDto {
        val statuser = request.status.orEmpty()
        val statusfilter =
            if (statuser.isEmpty()) "" else " AND v.status IN (${statuser.joinToString(",") { "?" }})"
        val totalt = connection.spør("SELECT count(*) FROM jobbsoker_sok_view v WHERE $VISES$statusfilter", treffId, statuser) { rs ->
            rs.next()
            rs.getLong(1)
        }
        val side = beregnSide(request.side, request.antallPerSide, totalt)
        return GjennomføringJobbsøkersideDto(
            totalt = totalt,
            side = side,
            antallPerStatus = hentAntallPerStatus(connection, treffId),
            jobbsøkere = if (totalt == 0L) emptyList() else hentJobbsøkere(connection, treffId, statusfilter, statuser, side, request.antallPerSide),
        )
    }

    private fun hentAntallPerStatus(connection: Connection, treffId: TreffId): Map<JobbsøkerStatus, Int> =
        connection.spør("SELECT v.status, count(*) AS antall FROM jobbsoker_sok_view v WHERE $VISES GROUP BY v.status", treffId) { rs ->
            buildMap {
                while (rs.next()) put(JobbsøkerStatus.valueOf(rs.getString("status")), rs.getInt("antall"))
            }
        }

    private fun hentJobbsøkere(
        connection: Connection,
        treffId: TreffId,
        statusfilter: String,
        statuser: List<JobbsøkerStatus>,
        side: Int,
        antallPerSide: Int,
    ): List<GjennomføringJobbsøkerDto> {
        val sql = """
            SELECT v.person_treff_id::text, v.fornavn, v.etternavn, v.status, v.er_synlig, v.fodselsnummer
            FROM jobbsoker_sok_view v
            WHERE $VISES$statusfilter
            ORDER BY ${JobbsøkerSorteringsfelt.NAVN.sql(JobbsøkerSorteringsretning.ASC)}
            LIMIT ? OFFSET ?
        """.trimIndent()
        return connection.spør(sql, treffId, statuser, antallPerSide.toLong(), (side - 1).toLong() * antallPerSide) { rs ->
            generateSequence { if (rs.next()) rs.tilJobbsøker() else null }.toList()
        }
    }

    private fun ResultSet.tilJobbsøker() = GjennomføringJobbsøkerDto(
        personTreffId = getString("person_treff_id"),
        fornavn = getString("fornavn"),
        etternavn = getString("etternavn"),
        status = JobbsøkerStatus.valueOf(getString("status")),
        fødselsnummer = if (getBoolean("er_synlig")) getString("fodselsnummer") else null,
    )

    private fun beregnSide(forespurtSide: Int, antallPerSide: Int, totalt: Long): Int {
        if (forespurtSide <= 1 || totalt <= 0L) return 1
        val sisteSide = (((totalt - 1) / antallPerSide) + 1).toInt()
        return minOf(forespurtSide, sisteSide)
    }

    private fun <T> Connection.spør(
        sql: String,
        treffId: TreffId,
        statuser: List<JobbsøkerStatus> = emptyList(),
        vararg sideparametre: Long,
        les: (ResultSet) -> T,
    ): T = prepareStatement(sql).use { stmt: PreparedStatement ->
        stmt.queryTimeout = QUERY_TIMEOUT_SECONDS
        var indeks = 1
        stmt.setObject(indeks++, treffId.somUuid)
        statuser.forEach { stmt.setString(indeks++, it.name) }
        sideparametre.forEach { stmt.setLong(indeks++, it) }
        stmt.executeQuery().use(les)
    }
}
