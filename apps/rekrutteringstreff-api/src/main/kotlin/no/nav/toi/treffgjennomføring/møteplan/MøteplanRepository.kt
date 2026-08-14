package no.nav.toi.treffgjennomføring.møteplan

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.tilListe
import no.nav.toi.treffgjennomføring.Treffkontekst
import java.sql.Connection
import java.time.LocalTime

class MøteplanRepository {

    fun hentFor(connection: Connection, kontekst: Treffkontekst, oppmøte: List<PersonTreffId>): Møteplan {
        val lagretRom = hentRom(connection, kontekst.treffDbId)
        return Møteplan(
            møteoppsett = hentMøteoppsett(connection, kontekst.treffDbId) ?: Møteoppsett.standard(),
            rom = normaliserRom(lagretRom, oppmøte, kontekst.antallRom),
            arbeidsgiverRekkefølge = hentRotasjon(connection, kontekst),
        )
    }

    private fun normaliserRom(rom: List<Rom>, oppmøte: List<PersonTreffId>, antallRom: Int): List<Rom> =
        if (rom.isEmpty()) emptyList()
        else Romfordeler.oppdaterEtterOppmøte(Romfordeler.normaliser(rom, antallRom), oppmøte)

    private fun hentMøteoppsett(connection: Connection, treffDbId: Long): Møteoppsett? {
        val sql = """
            SELECT m.start_tidspunkt, m.varighet_min
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
            FROM jobbsoker_rom_tildeling r
            JOIN jobbsoker j ON j.jobbsoker_id = r.jobbsoker_id
            WHERE r.rekrutteringstreff_id = ? AND j.status != 'SLETTET'
            ORDER BY r.romnummer, r.plassering
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

    private fun hentRotasjon(connection: Connection, kontekst: Treffkontekst): List<ArbeidsgiverRotasjon> {
        val sql = """
            SELECT a.id::text, r.start_posisjon
            FROM arbeidsgiver_rotasjon r
            JOIN arbeidsgiver a ON a.arbeidsgiver_id = r.arbeidsgiver_id
            WHERE a.rekrutteringstreff_id = ? AND a.status = 'AKTIV'
        """.trimIndent()
        val lagret = connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, kontekst.treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe { ArbeidsgiverTreffId(it.getString(1)) to it.getInt(2) }.toMap()
            }
        }
        if (lagret.isEmpty()) return emptyList()

        val brukte = lagret.values.toMutableSet()
        return kontekst.arbeidsgiverIder.map { arbeidsgiver ->
            val posisjon = lagret[arbeidsgiver] ?: generateSequence(0) { it + 1 }.first { it !in brukte }
            brukte.add(posisjon)
            ArbeidsgiverRotasjon(arbeidsgiver, posisjon)
        }
    }

    fun harMøteoppsett(connection: Connection, treffgjennomføringId: Long): Boolean =
        connection.prepareStatement("SELECT 1 FROM moteoppsett WHERE treffgjennomforing_id = ?").use { stmt ->
            stmt.setLong(1, treffgjennomføringId)
            stmt.executeQuery().use { it.next() }
        }

    fun lagreMøteoppsett(connection: Connection, treffgjennomføringId: Long, møteoppsett: Møteoppsett) {
        val sql = """
            INSERT INTO moteoppsett (treffgjennomforing_id, start_tidspunkt, varighet_min)
            VALUES (?, ?, ?)
            ON CONFLICT (treffgjennomforing_id)
            DO UPDATE SET start_tidspunkt = EXCLUDED.start_tidspunkt, varighet_min = EXCLUDED.varighet_min
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffgjennomføringId)
            stmt.setTime(2, møteoppsett.starttidspunkt.tilSqlTime())
            stmt.setInt(3, møteoppsett.varighetPerMøteMinutter)
            stmt.executeUpdate()
        }
    }

    fun erstattRomfordeling(connection: Connection, treffDbId: Long, rom: List<Rom>, kontekst: Treffkontekst) {
        connection.prepareStatement("DELETE FROM jobbsoker_rom_tildeling WHERE rekrutteringstreff_id = ?").use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeUpdate()
        }
        val sql = """
            INSERT INTO jobbsoker_rom_tildeling (rekrutteringstreff_id, jobbsoker_id, romnummer, plassering)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            rom.forEach { r ->
                r.jobbsøkere.forEachIndexed { plassering, person ->
                    val jobbsøkerId = kontekst.jobbsøkerId(person) ?: return@forEachIndexed
                    stmt.setLong(1, treffDbId)
                    stmt.setLong(2, jobbsøkerId)
                    stmt.setInt(3, r.romnummer)
                    stmt.setInt(4, plassering)
                    stmt.addBatch()
                }
            }
            stmt.executeBatch()
        }
    }

    fun lagreRotasjon(connection: Connection, rotasjoner: List<ArbeidsgiverRotasjon>, kontekst: Treffkontekst) {
        val sql = """
            INSERT INTO arbeidsgiver_rotasjon (arbeidsgiver_id, start_posisjon)
            VALUES (?, ?)
            ON CONFLICT (arbeidsgiver_id) DO UPDATE SET start_posisjon = EXCLUDED.start_posisjon
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            rotasjoner.forEach { rotasjon ->
                val arbeidsgiverId = kontekst.arbeidsgiverId(rotasjon.arbeidsgiverTreffId) ?: return@forEach
                stmt.setLong(1, arbeidsgiverId)
                stmt.setInt(2, rotasjon.startPosisjon)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    fun slettRomForJobbsøker(connection: Connection, jobbsøkerId: Long) {
        connection.prepareStatement("DELETE FROM jobbsoker_rom_tildeling WHERE jobbsoker_id = ?").use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.executeUpdate()
        }
    }
}

private fun LocalTime.tilSqlTime(): java.sql.Time = java.sql.Time.valueOf(this)
