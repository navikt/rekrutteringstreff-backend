package no.nav.toi.arbeidsgiver

import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import javax.sql.DataSource

class ArbeidsgiverRepository(
    private val dataSource: DataSource,
) {

    private fun hentTreffDbId(connection: Connection, treff: TreffId): Long? {
        connection.prepareStatement(
            "SELECT db_id FROM rekrutteringstreff WHERE id = ?"
        ).use { stmt ->
            stmt.setObject(1, treff.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) {
                    return rs.getLong("db_id")
                } else return null
            }
        }
    }

    private fun finnesIDb(connection: Connection, treffId: TreffId): Boolean =
        hentTreffDbId(connection, treffId) != null


    fun leggTil(arbeidsgiver: LeggTilArbeidsgiver, treff: TreffId) {

        fun leggTilArbeidsgiver(connection: Connection, arbeidsgiver: LeggTilArbeidsgiver, treffDbId: Long) {
            connection.prepareStatement(
                "INSERT INTO arbeidsgiver (treff_db_id, orgnr, orgnavn) VALUES (?, ?, ?)"
            ).use { stmt ->
                stmt.setLong(1, treffDbId)
                stmt.setString(2, arbeidsgiver.orgnr.toString())
                stmt.setString(3, arbeidsgiver.orgnavn.toString())
                stmt.executeUpdate()
            }
        }

        dataSource.connection.use { connection ->
            val treffDbId: Long = hentTreffDbId(connection, treff)
                ?: throw IllegalArgumentException("Kan ikke legge til arbeidsgiver på treffet fordi det ikke finnes noe treff med id ${treff.somUuid}. arbeidsgiver=$arbeidsgiver")
            leggTilArbeidsgiver(connection, arbeidsgiver, treffDbId)
        }
    }

    fun hentArbeidsgivere(treff: TreffId): List<Arbeidsgiver> {
        dataSource.connection.use { connection ->
            if (!finnesIDb(connection, treff))
                throw IllegalArgumentException("Kan ikke hente arbeidsgivere fordi det ikke finnes noe rekrutteringstreff med id $treff.")

            connection.prepareStatement(
                """
                SELECT ag.orgnr, ag.orgnavn, rt.id as treff_id
                FROM arbeidsgiver ag
                JOIN rekrutteringstreff rt ON ag.treff_db_id = rt.db_id
                WHERE rt.id = ?
                ORDER BY ag.db_id ASC;
            """.trimIndent()
            ).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.executeQuery().use { rs ->
                    val arbeidsgivere = mutableListOf<Arbeidsgiver>()
                    while (rs.next()) {
                        arbeidsgivere.add(
                            Arbeidsgiver(
                                treffId = TreffId(rs.getString("treff_id")),
                                orgnr = Orgnr(rs.getString("orgnr")),
                                orgnavn = Orgnavn(rs.getString("orgnavn"))
                            )
                        )
                    }
                    return arbeidsgivere
                }
            }
        }
    }

}
