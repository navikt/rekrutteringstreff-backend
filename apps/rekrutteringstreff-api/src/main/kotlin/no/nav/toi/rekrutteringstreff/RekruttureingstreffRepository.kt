package no.nav.toi.rekrutteringstreff

import java.sql.Timestamp
import java.time.ZonedDateTime
import javax.sql.DataSource

class RekruttureingstreffRepository(private val dataSource: DataSource) {
    fun opprett(dto: OpprettRekrutteringstreffDto) {
        dataSource.connection.use {
            it.prepareStatement(
                """INSERT INTO $tabellnavn (
                               $tittel,
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
            ).apply {
                // TODO Bruke kolonnenavn istedenfor indeks?
                setString(1, kandidatutfall.aktørId)
                setString(2, kandidatutfall.utfall.name)
                setString(3, kandidatutfall.navIdent)
                setString(4, kandidatutfall.navKontor)
                setString(5, kandidatutfall.kandidatlisteId)
                setString(6, kandidatutfall.stillingsId)
                setBoolean(7, kandidatutfall.synligKandidat)
                setTimestamp(8, Timestamp.valueOf(kandidatutfall.tidspunktForHendelsen.toLocalDateTime()))
                if (kandidatutfall.harHullICv != null) setBoolean(9, kandidatutfall.harHullICv) else setNull(9, 0)
                if (kandidatutfall.alder != null) setInt(10, kandidatutfall.alder) else setNull(10, 0)
                setString(11, kandidatutfall.innsatsbehov)
                setString(12, kandidatutfall.hovedmål)
                executeUpdate()
            }
        }

    }

    private companion object {
        const val tabellnavn = "rekrutteringstreff"
        const val tittel = "tittel"
//        const val kontor = "kontor"
        const val fraTid = "fratid"
        val tilTid = "tiltid"
        val sted: String

    }

}
