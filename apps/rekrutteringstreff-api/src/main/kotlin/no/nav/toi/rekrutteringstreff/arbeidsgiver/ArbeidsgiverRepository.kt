package no.nav.toi.rekrutteringstreff.arbeidsgiver

import no.nav.toi.rekrutteringstreff.Kolonnenavn
import no.nav.toi.rekrutteringstreff.Tabellnavn
import no.nav.toi.rekrutteringstreff.TreffId
import javax.sql.DataSource
import kotlin.use

class ArbeidsgiverRepository(
    private val dataSource: DataSource,
){

    fun leggTil(arbeidsgiver: LeggTilArbeidsgiverDto, treff: TreffId) {
//        dataSource.connection.use { connection ->
//            connection.prepareStatement(
//                """
//                INSERT INTO $tabell
//        }
    }


    companion object {
        private const val tabellnavn = "arbeidsgiver"
        private const val fratid = "orgnr"
        private const val tiltid = "orgnavn"
    }


}


