package no.nav.toi.arbeidsgiver

import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import javax.sql.DataSource

class ArbeidsgiverRepository(
    private val dataSource: DataSource,
) {

    fun leggTil(arbeidsgiver: LeggTilArbeidsgiver, treff: TreffId) {
        fun hentTreffDbId(connection: Connection, treff: TreffId): Long {
            connection.prepareStatement(
                "SELECT db_id FROM rekrutteringstreff WHERE id = ?"
            ).use { stmt ->
                stmt.setObject(1, treff.somUuid)
                stmt.executeQuery().use { rs ->
                    if (rs.next()) {
                        return rs.getLong("db_id")
                    } else {
                        throw IllegalArgumentException("Kan ikke legge til arbeidsgiver på treffet fordi det ikke finnesn noe treff med id ${treff.somUuid}. arbeidsgiver=$arbeidsgiver")
                    }
                }
            }
        }

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
            val treffDbId = hentTreffDbId(connection, treff)
            leggTilArbeidsgiver(connection, arbeidsgiver, treffDbId)
        }
    }


}
