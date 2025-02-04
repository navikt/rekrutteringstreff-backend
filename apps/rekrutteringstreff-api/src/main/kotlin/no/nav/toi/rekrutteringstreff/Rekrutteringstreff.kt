package no.nav.toi.rekrutteringstreff

import java.time.ZonedDateTime

class Rekrutteringstreff(
    val tittel: String,
    val fraTid: ZonedDateTime,
    val tilTid: ZonedDateTime,
    val sted: String,
    val status: String,
    val opprettetAvPerson: String,
    val opprettetAvKontor: String
)
