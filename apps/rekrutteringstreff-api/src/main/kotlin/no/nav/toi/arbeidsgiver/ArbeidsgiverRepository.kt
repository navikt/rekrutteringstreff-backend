package no.nav.toi.arbeidsgiver

import no.nav.toi.rekrutteringstreff.TreffId
import javax.sql.DataSource

class ArbeidsgiverRepository(
    private val dataSource: DataSource,
){

    fun leggTil(arbeidsgiver: LeggTilArbeidsgiver, treff: TreffId) {
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


