package no.nav.toi

import java.sql.ResultSet

fun <T> ResultSet.tilListe(les: (ResultSet) -> T): List<T> =
    generateSequence { if (next()) les(this) else null }.toList()
