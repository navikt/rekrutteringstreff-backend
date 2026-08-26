package no.nav.toi.treffgjennomføring.møteplan

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.tilListe
import no.nav.toi.treffgjennomføring.Treffkontekst
import java.sql.Connection
import java.time.LocalTime

class MøteplanRepository {

    fun hentMøteplan(connection: Connection, treffkontekst: Treffkontekst, oppmøte: List<PersonTreffId>): Møteplan {
        val lagretRom = hentRom(connection, treffkontekst.treffDbId)
        return Møteplan(
            møteoppsett = hentMøteoppsett(connection, treffkontekst.treffDbId) ?: Møteoppsett.standard(),
            rom = normaliserRom(lagretRom, oppmøte, treffkontekst.antallRom),
            arbeidsgiverRekkefølge = hentArbeidsgiverRotasjon(connection, treffkontekst),
        )
    }

    private fun normaliserRom(rom: List<Rom>, oppmøte: List<PersonTreffId>, antallRom: Int): List<Rom> =
        if (rom.isEmpty()) emptyList()
        else Romfordeler.oppdaterEtterOppmøte(Romfordeler.normaliser(rom, antallRom), oppmøte)

    private fun hentMøteoppsett(connection: Connection, treffDbId: Long): Møteoppsett? {
        val sql = """
            SELECT m.starttidspunkt, m.varighet_min
            FROM moteoppsett m
            JOIN treffgjennomforing t ON t.treffgjennomforing_id = m.treffgjennomforing_id
            WHERE t.rekrutteringstreff_id = ?
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) Møteoppsett(rs.getTime(1).toLocalTime(), rs.getInt(2)) else null
            }
        }
    }

    private fun hentRom(connection: Connection, treffDbId: Long): List<Rom> {
        val sql = """
            SELECT r.romnummer, j.id::text
            FROM jobbsoker_romtildeling r
            JOIN jobbsoker j ON j.jobbsoker_id = r.jobbsoker_id
            LEFT JOIN deltakernummer d
                ON d.jobbsoker_id = r.jobbsoker_id AND d.rekrutteringstreff_id = r.rekrutteringstreff_id
            WHERE r.rekrutteringstreff_id = ? AND j.status != 'SLETTET'
            ORDER BY r.romnummer, d.deltakernummer NULLS LAST, j.jobbsoker_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe { it.getInt(1) to PersonTreffId(it.getString(2)) }
                    .groupBy({ it.first }, { it.second })
                    .map { (romnummer, jobbsøkere) -> Rom(romnummer, jobbsøkere) }
                    .sortedBy { it.romnummer }
            }
        }
    }

    private fun hentArbeidsgiverRotasjon(connection: Connection, kontekst: Treffkontekst): List<ArbeidsgiverRotasjon> {
        val sql = """
            SELECT a.id::text, r.forste_romnummer
            FROM arbeidsgiver_rotasjon r
            JOIN arbeidsgiver a ON a.arbeidsgiver_id = r.arbeidsgiver_id
            WHERE a.rekrutteringstreff_id = ? AND a.status = 'AKTIV'
        """.trimIndent()
        val rotasjon = connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, kontekst.treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe { ArbeidsgiverTreffId(it.getString(1)) to it.getInt(2) }.toMap()
            }
        }
        if (rotasjon.isEmpty()) return emptyList()

        val brukteRomnumre = rotasjon.values.toMutableSet()
        return kontekst.arbeidsgiverTreffIder.map { arbeidsgiver ->
            val romnummer = rotasjon[arbeidsgiver] ?: generateSequence(1) { it + 1 }.first { it !in brukteRomnumre }
            brukteRomnumre.add(romnummer)
            ArbeidsgiverRotasjon(arbeidsgiver, romnummer)
        }
    }

    fun harMøteoppsett(connection: Connection, treffgjennomføringId: Long): Boolean =
        connection.prepareStatement("SELECT 1 FROM moteoppsett WHERE treffgjennomforing_id = ?").use { stmt ->
            stmt.setLong(1, treffgjennomføringId)
            stmt.executeQuery().use { it.next() }
        }

    fun lagreMøteoppsett(connection: Connection, treffgjennomføringId: Long, møteoppsett: Møteoppsett) {
        val sql = """
            INSERT INTO moteoppsett (treffgjennomforing_id, starttidspunkt, varighet_min)
            VALUES (?, ?, ?)
            ON CONFLICT (treffgjennomforing_id)
            DO UPDATE SET starttidspunkt = EXCLUDED.starttidspunkt, varighet_min = EXCLUDED.varighet_min
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffgjennomføringId)
            stmt.setTime(2, møteoppsett.starttidspunkt.tilSqlTime())
            stmt.setInt(3, møteoppsett.varighetPerMøteMinutter)
            stmt.executeUpdate()
        }
    }

    fun erstattRomfordeling(connection: Connection, treffDbId: Long, rom: List<Rom>, kontekst: Treffkontekst) {
        connection.prepareStatement("DELETE FROM jobbsoker_romtildeling WHERE rekrutteringstreff_id = ?").use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeUpdate()
        }
        val sql = """
            INSERT INTO jobbsoker_romtildeling (rekrutteringstreff_id, jobbsoker_id, romnummer)
            VALUES (?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            rom.forEach { r ->
                r.jobbsøkere.forEach { person ->
                    val jobbsøkerId = kontekst.jobbsøkerId(person) ?: return@forEach
                    stmt.setLong(1, treffDbId)
                    stmt.setLong(2, jobbsøkerId)
                    stmt.setInt(3, r.romnummer)
                    stmt.addBatch()
                }
            }
            stmt.executeBatch()
        }
    }

    fun lagreArbeidsgiverRotasjon(connection: Connection, rotasjoner: List<ArbeidsgiverRotasjon>, kontekst: Treffkontekst) {
        val sql = """
            INSERT INTO arbeidsgiver_rotasjon (arbeidsgiver_id, forste_romnummer)
            VALUES (?, ?)
            ON CONFLICT (arbeidsgiver_id) DO UPDATE SET forste_romnummer = EXCLUDED.forste_romnummer
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            rotasjoner.forEach { rotasjon ->
                val arbeidsgiverId = kontekst.arbeidsgiverId(rotasjon.arbeidsgiverTreffId) ?: return@forEach
                stmt.setLong(1, arbeidsgiverId)
                stmt.setInt(2, rotasjon.førsteRomnummer)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    fun slettRomForJobbsøker(connection: Connection, jobbsøkerId: Long) {
        connection.prepareStatement("DELETE FROM jobbsoker_romtildeling WHERE jobbsoker_id = ?").use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.executeUpdate()
        }
    }
}

private fun LocalTime.tilSqlTime(): java.sql.Time = java.sql.Time.valueOf(this)
