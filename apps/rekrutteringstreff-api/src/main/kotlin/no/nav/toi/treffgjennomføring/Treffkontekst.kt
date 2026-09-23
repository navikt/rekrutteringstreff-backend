package no.nav.toi.treffgjennomføring

import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import no.nav.toi.Miljø
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import java.sql.ResultSet

data class Treffkontekst(
    val treffId: TreffId,
    val treffDbId: Long,
    val erWorkOp: Boolean,
    val jobbsøkere: Map<PersonTreffId, Long>,
    val arbeidsgivere: Map<ArbeidsgiverTreffId, Long>,
) {
    val antallRom: Int = Treffgjennomføring.beregnAntallRom(arbeidsgivere.size)

    val arbeidsgiverTreffIder: List<ArbeidsgiverTreffId> = arbeidsgivere.keys.toList()

    fun jobbsøkerId(personTreffId: PersonTreffId): Long? = jobbsøkere[personTreffId]

    fun arbeidsgiverId(arbeidsgiverTreffId: ArbeidsgiverTreffId): Long? = arbeidsgivere[arbeidsgiverTreffId]

    fun krevJobbsøkerId(personTreffId: PersonTreffId): Long =
        jobbsøkerId(personTreffId) ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")

    fun krevArbeidsgiverId(arbeidsgiverTreffId: ArbeidsgiverTreffId): Long =
        arbeidsgiverId(arbeidsgiverTreffId) ?: throw BadRequestResponse("Arbeidsgiveren finnes ikke på treffet")

    fun krevWorkOp() {
        if (!erWorkOp) throw BadRequestResponse("Steget finnes bare på treff av kategorien WORKOP")
    }

    fun krevWorkOpEllerLokalUtvikling(miljø: Miljø) {
        when (miljø) {
            Miljø.PROD_GCP -> throw BadRequestResponse("Steget er ikke tilgjengelig i produksjon")
            Miljø.DEV_GCP -> krevWorkOp()
            Miljø.LOKALT -> {}
        }
    }
}

fun TreffkontekstRepository.krevKontekst(connection: Connection, treffId: TreffId): Treffkontekst =
    hentTreffkontekst(connection, treffId)
        ?: throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")

class TreffkontekstRepository {

    fun hentTreffkontekst(connection: Connection, treffId: TreffId): Treffkontekst? {
        val (treffDbId, erWorkOp) = hentTreff(connection, treffId) ?: return null
        return Treffkontekst(
            treffId = treffId,
            treffDbId = treffDbId,
            erWorkOp = erWorkOp,
            jobbsøkere = hentIdKart(connection, JOBBSØKERE_SQL, treffDbId) { PersonTreffId(it) },
            arbeidsgivere = hentIdKart(connection, ARBEIDSGIVERE_SQL, treffDbId) { ArbeidsgiverTreffId(it) },
        )
    }

    private fun hentTreff(connection: Connection, treffId: TreffId): Pair<Long, Boolean>? {
        val sql = "SELECT rekrutteringstreff_id, kategori FROM rekrutteringstreff WHERE id = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setObject(1, treffId.somUuid)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getLong(1) to (rs.getString(2) == WORKOP) else null
            }
        }
    }

    private fun <K> hentIdKart(
        connection: Connection,
        sql: String,
        treffDbId: Long,
        tilNøkkel: (String) -> K,
    ): Map<K, Long> = connection.prepareStatement(sql).use { stmt ->
        stmt.setLong(1, treffDbId)
        stmt.executeQuery().use { rs -> rs.tilIdKart(tilNøkkel) }
    }

    private fun <K> ResultSet.tilIdKart(tilNøkkel: (String) -> K): Map<K, Long> {
        val kart = LinkedHashMap<K, Long>()
        while (next()) kart[tilNøkkel(getString(1))] = getLong(2)
        return kart
    }

    private companion object {
        const val WORKOP = "WORKOP"

        val JOBBSØKERE_SQL = """
            SELECT id::text, jobbsoker_id
            FROM jobbsoker
            WHERE rekrutteringstreff_id = ? AND status != 'SLETTET'
            ORDER BY jobbsoker_id
        """.trimIndent()

        val ARBEIDSGIVERE_SQL = """
            SELECT id::text, arbeidsgiver_id
            FROM arbeidsgiver
            WHERE rekrutteringstreff_id = ? AND status = 'AKTIV'
            ORDER BY arbeidsgiver_id
        """.trimIndent()
    }
}
