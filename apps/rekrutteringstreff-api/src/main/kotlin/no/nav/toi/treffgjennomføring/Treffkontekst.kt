package no.nav.toi.treffgjennomføring

import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection
import no.nav.toi.Miljø

data class Treffkontekst(
    val treffId: TreffId,
    val treffDbId: Long,
    val erWorkOp: Boolean,
    val jobbsøkere: Map<PersonTreffId, Long>,
    val arbeidsgivere: Map<ArbeidsgiverTreffId, Long>,
) {
    val antallRom: Int = Treffgjennomføring.beregnAntallRom(arbeidsgivere.size)

    fun jobbsøkerId(personTreffId: PersonTreffId): Long? = jobbsøkere[personTreffId]

    fun arbeidsgiverId(arbeidsgiverTreffId: ArbeidsgiverTreffId): Long? = arbeidsgivere[arbeidsgiverTreffId]

    fun erPersonPåTreff(personTreffId: PersonTreffId) = jobbsøkere.containsKey(personTreffId)

    fun erArbeidsgiverPåTreff(arbeidsgiverTreffId: ArbeidsgiverTreffId) = arbeidsgivere.containsKey(arbeidsgiverTreffId)

    val arbeidsgiverTreffIder: List<ArbeidsgiverTreffId> get() = arbeidsgivere.keys.toList()

    fun krevWorkOp() {
        if (!erWorkOp) throw BadRequestResponse("Steget finnes bare på treff av kategorien WORKOP")
    }

    /**
     * Krever at treffet er et WorkOp.
     * For ordinære treff er dette kun tilgjengelig under lokal utvikling/testing.
     */
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
            jobbsøkere = hentJobbsøkere(connection, treffDbId),
            arbeidsgivere = hentArbeidsgivere(connection, treffDbId),
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

    private fun hentJobbsøkere(connection: Connection, treffDbId: Long): Map<PersonTreffId, Long> {
        val sql = """
            SELECT id::text, jobbsoker_id
            FROM jobbsoker
            WHERE rekrutteringstreff_id = ? AND status != 'SLETTET'
            ORDER BY jobbsoker_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                val kart = LinkedHashMap<PersonTreffId, Long>()
                while (rs.next()) kart[PersonTreffId(rs.getString(1))] = rs.getLong(2)
                kart
            }
        }
    }

    private fun hentArbeidsgivere(connection: Connection, treffDbId: Long): Map<ArbeidsgiverTreffId, Long> {
        val sql = """
            SELECT id::text, arbeidsgiver_id
            FROM arbeidsgiver
            WHERE rekrutteringstreff_id = ? AND status = 'AKTIV'
            ORDER BY arbeidsgiver_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                val kart = LinkedHashMap<ArbeidsgiverTreffId, Long>()
                while (rs.next()) kart[ArbeidsgiverTreffId(rs.getString(1))] = rs.getLong(2)
                kart
            }
        }
    }

    private companion object {
        const val WORKOP = "WORKOP"
    }
}
