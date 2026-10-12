package no.nav.toi

import no.nav.toi.exception.JobbsøkerIkkeFunnetException
import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import javax.sql.DataSource

/*
 * Alle radlåser i appen står i denne fila, og bare her bruker vi FOR NO KEY UPDATE.
 *
 * - Funksjonen som starter transaksjonen, låser før den leser det den bestemmer ut fra.
 * - Funksjoner som får en Connection, og lesefunksjoner, låser ikke.
 * - Rekkefølgen er treff, så jobbsøkere sortert på id. Da kan ikke to transaksjoner låse hverandre fast.
 * - medLåstTreff og medLåsteJobbsøkere starter transaksjonen og låser. executeInTransaction låser ikke.
 * - FOR NO KEY UPDATE stopper andre låser og endringer på raden, men ikke FK-sjekkene når andre
 *   legger til rader under den, for eksempel hendelser.
 *
 * Reglene og begrunnelsen står i docs/2-arkitektur/transaksjoner.md.
 */

/** Starter en transaksjon og låser treffet før [block] kjører. */
fun <T> DataSource.medLåstTreff(treffId: TreffId, block: (Connection) -> T): T =
    executeInTransaction { connection ->
        connection.låsTreff(treffId)
        block(connection)
    }

/**
 * Starter en transaksjon og låser jobbsøkerne før [block] kjører. Se [låsJobbsøkere].
 * Trenger operasjonen også treffet, bruk [medLåstTreff] og kall [låsJobbsøkere] i blokken.
 */
fun <T> DataSource.medLåsteJobbsøkere(
    treffId: TreffId,
    personTreffIder: Collection<PersonTreffId>,
    block: (Connection) -> T,
): T =
    executeInTransaction { connection ->
        connection.låsJobbsøkere(treffId, personTreffIder)
        block(connection)
    }

/** Låser treffraden og returnerer databaseid-en. */
fun Connection.låsTreff(treffId: TreffId): Long =
    prepareStatement("SELECT rekrutteringstreff_id FROM rekrutteringstreff WHERE id = ? FOR NO KEY UPDATE").use { stmt ->
        stmt.setObject(1, treffId.somUuid)
        stmt.executeQuery().use { rs ->
            if (rs.next()) rs.getLong(1)
            else throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id ${treffId.somString} finnes ikke")
        }
    }

/**
 * Låser jobbsøkerne på treffet sortert på id, også slettede, så kalleren kan sjekke status selv.
 * Kaster [JobbsøkerIkkeFunnetException] hvis en av dem ikke hører til treffet.
 */
fun Connection.låsJobbsøkere(treffId: TreffId, personTreffIder: Collection<PersonTreffId>) {
    val ønskede = personTreffIder.toSet()
    if (ønskede.isEmpty()) return
    val sql = """
        SELECT j.id
        FROM jobbsoker j
        JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
        WHERE rt.id = ? AND j.id = ANY(?)
        ORDER BY j.id
        FOR NO KEY UPDATE OF j
    """.trimIndent()
    val antallLåst = prepareStatement(sql).use { stmt ->
        stmt.setObject(1, treffId.somUuid)
        stmt.setArray(2, createArrayOf("uuid", ønskede.map { it.somUuid }.toTypedArray()))
        stmt.executeQuery().use { rs -> generateSequence { if (rs.next()) Unit else null }.count() }
    }
    if (antallLåst != ønskede.size) throw JobbsøkerIkkeFunnetException("Jobbsøker finnes ikke for dette treffet.")
}

/** Låser alle jobbsøkerne på treffet sortert på id, også slettede. Ta trefflåsen først. */
fun Connection.låsAlleJobbsøkerePåTreff(treffId: TreffId) {
    val sql = """
        SELECT j.id
        FROM jobbsoker j
        JOIN rekrutteringstreff rt ON rt.rekrutteringstreff_id = j.rekrutteringstreff_id
        WHERE rt.id = ?
        ORDER BY j.id
        FOR NO KEY UPDATE OF j
    """.trimIndent()
    prepareStatement(sql).use { stmt ->
        stmt.setObject(1, treffId.somUuid)
        stmt.executeQuery().use { rs -> while (rs.next()) Unit }
    }
}
