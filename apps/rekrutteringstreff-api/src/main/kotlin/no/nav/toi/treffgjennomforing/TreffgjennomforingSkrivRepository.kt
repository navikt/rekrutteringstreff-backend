package no.nav.toi.treffgjennomforing

import no.nav.toi.jobbsoker.PersonTreffId
import java.sql.Connection
import java.sql.PreparedStatement

data class Treffgjennomforingsrad(val id: Long, val fase: TreffgjennomføringFase)

class TreffgjennomforingSkrivRepository {

    /**
     * Oppretter raden hvis den mangler, og låser den uansett. Låsen serialiserer
     * skriving på samme treff, slik at to samtidige oppmøteregistreringer ikke
     * kan lese samme MAX(nummer) og dele ut samme kortnummer.
     */
    fun sikreOgLås(connection: Connection, treffDbId: Long): Treffgjennomforingsrad {
        val sql = """
            INSERT INTO treffgjennomforing (rekrutteringstreff_id, fase)
            VALUES (?, ?)
            ON CONFLICT (rekrutteringstreff_id) DO UPDATE SET fase = treffgjennomforing.fase
            RETURNING treffgjennomforing_id, fase
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setString(2, TreffgjennomføringFase.OPPMØTE.name)
            stmt.executeQuery().use { rs ->
                rs.next()
                Treffgjennomforingsrad(
                    rs.getLong(1),
                    TreffgjennomføringFase.entries.firstOrNull { it.name == rs.getString(2) }
                        ?: TreffgjennomføringFase.OPPMØTE,
                )
            }
        }
    }

    /** Fasen går bare framover. Et angret oppmøte lukker ikke et steg brukeren har vært innom. */
    fun settFase(connection: Connection, treffDbId: Long, nåværende: TreffgjennomføringFase, ny: TreffgjennomføringFase) {
        if (ny.ordinal <= nåværende.ordinal) return
        connection.prepareStatement("UPDATE treffgjennomforing SET fase = ? WHERE rekrutteringstreff_id = ?").use { stmt ->
            stmt.setString(1, ny.name)
            stmt.setLong(2, treffDbId)
            stmt.executeUpdate()
        }
    }

    fun lagreMøteoppsett(connection: Connection, treffgjennomforingId: Long, møteoppsett: Møteoppsett) {
        val sql = """
            INSERT INTO moteoppsett (treffgjennomforing_id, start_tidspunkt, varighet_min)
            VALUES (?, ?, ?)
            ON CONFLICT (treffgjennomforing_id)
            DO UPDATE SET start_tidspunkt = EXCLUDED.start_tidspunkt, varighet_min = EXCLUDED.varighet_min
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffgjennomforingId)
            stmt.setTime(2, møteoppsett.starttidspunkt.tilSqlTime())
            stmt.setInt(3, møteoppsett.varighetPerMøteMinutter)
            stmt.executeUpdate()
        }
    }

    fun harMøteoppsett(connection: Connection, treffgjennomforingId: Long): Boolean =
        connection.prepareStatement("SELECT 1 FROM moteoppsett WHERE treffgjennomforing_id = ?").use { stmt ->
            stmt.setLong(1, treffgjennomforingId)
            stmt.executeQuery().use { it.next() }
        }

    /**
     * Nummeret gjenbrukes aldri, og en person som registreres møtt på nytt får
     * tilbake sitt opprinnelige. Hull i rekka er derfor forventet: nummeret står
     * på et fysisk kort som allerede er delt ut.
     */
    fun tildelDeltakernummer(connection: Connection, treffDbId: Long, jobbsøkerId: Long): Int {
        val sql = """
            INSERT INTO deltakernummer (rekrutteringstreff_id, jobbsoker_id, nummer)
            SELECT ?, ?, COALESCE(MAX(nummer), 0) + 1 FROM deltakernummer WHERE rekrutteringstreff_id = ?
            ON CONFLICT (rekrutteringstreff_id, jobbsoker_id) DO NOTHING
            RETURNING nummer
        """.trimIndent()
        val nytt = connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setLong(2, jobbsøkerId)
            stmt.setLong(3, treffDbId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else null }
        }
        return nytt ?: hentDeltakernummer(connection, treffDbId, jobbsøkerId)
    }

    private fun hentDeltakernummer(connection: Connection, treffDbId: Long, jobbsøkerId: Long): Int {
        val sql = "SELECT nummer FROM deltakernummer WHERE rekrutteringstreff_id = ? AND jobbsoker_id = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setLong(2, jobbsøkerId)
            stmt.executeQuery().use { rs -> if (rs.next()) rs.getInt(1) else 0 }
        }
    }

    fun tellRegistreringer(connection: Connection, jobbsøkerId: Long): Registreringer {
        val sql = """
            SELECT
                (SELECT COUNT(*) FROM interesse WHERE jobbsoker_id = ?),
                (SELECT COUNT(*) FROM intervju_fordeling WHERE jobbsoker_id = ?),
                (SELECT COUNT(*) FROM vurdering WHERE jobbsoker_id = ?)
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            (1..3).forEach { stmt.setLong(it, jobbsøkerId) }
            stmt.executeQuery().use { rs ->
                rs.next()
                Registreringer(rs.getInt(1), rs.getInt(2), rs.getInt(3))
            }
        }
    }

    /** Kaskaden er systemets slutning, ikke brukerens avgjørelse — derfor ingen hendelse per slettet rad. */
    fun slettRegistreringerFor(connection: Connection, jobbsøkerId: Long) {
        listOf(
            "DELETE FROM interesse WHERE jobbsoker_id = ?",
            "DELETE FROM intervju_fordeling WHERE jobbsoker_id = ?",
            "DELETE FROM vurdering WHERE jobbsoker_id = ?",
            "DELETE FROM jobbsoker_rom_tildeling WHERE jobbsoker_id = ?",
        ).forEach { sql ->
            connection.prepareStatement(sql).use { stmt ->
                stmt.setLong(1, jobbsøkerId)
                stmt.executeUpdate()
            }
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

    /** Idempotent. Returnerer om noe faktisk endret seg, slik at hendelsen bare skrives ved reell endring. */
    fun settInteresse(connection: Connection, jobbsøkerId: Long, arbeidsgiverId: Long, interessert: Boolean): Boolean {
        val sql = if (interessert) {
            "INSERT INTO interesse (jobbsoker_id, arbeidsgiver_id) VALUES (?, ?) ON CONFLICT DO NOTHING"
        } else {
            "DELETE FROM interesse WHERE jobbsoker_id = ? AND arbeidsgiver_id = ?"
        }
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.setLong(2, arbeidsgiverId)
            stmt.executeUpdate() > 0
        }
    }

    fun erstattIntervjufordelinger(
        connection: Connection,
        fordelinger: List<ArbeidsgiverIntervjufordeling>,
        kontekst: Treffkontekst,
    ) {
        val arbeidsgiverIder = fordelinger.mapNotNull { kontekst.arbeidsgiverId(it.arbeidsgiverTreffId) }
        if (arbeidsgiverIder.isEmpty()) return

        connection.prepareStatement("DELETE FROM intervju_fordeling WHERE arbeidsgiver_id = ?").use { stmt ->
            arbeidsgiverIder.forEach { stmt.setLong(1, it); stmt.addBatch() }
            stmt.executeBatch()
        }

        val sql = """
            INSERT INTO intervju_fordeling (jobbsoker_id, arbeidsgiver_id, plassering, inkludert)
            VALUES (?, ?, ?, ?)
        """.trimIndent()
        connection.prepareStatement(sql).use { stmt ->
            fordelinger.forEach { fordeling ->
                val arbeidsgiverId = kontekst.arbeidsgiverId(fordeling.arbeidsgiverTreffId) ?: return@forEach
                fordeling.inkludertePersonTreffIder.forEachIndexed { plassering, person ->
                    stmt.leggTilFordeling(kontekst, person, arbeidsgiverId, plassering, inkludert = true)
                }
                fordeling.ekskludertePersonTreffIder.forEachIndexed { plassering, person ->
                    stmt.leggTilFordeling(kontekst, person, arbeidsgiverId, plassering, inkludert = false)
                }
            }
            stmt.executeBatch()
        }
    }

    private fun PreparedStatement.leggTilFordeling(
        kontekst: Treffkontekst,
        person: PersonTreffId,
        arbeidsgiverId: Long,
        plassering: Int,
        inkludert: Boolean,
    ) {
        val jobbsøkerId = kontekst.jobbsøkerId(person) ?: return
        setLong(1, jobbsøkerId)
        setLong(2, arbeidsgiverId)
        setInt(3, plassering)
        setBoolean(4, inkludert)
        addBatch()
    }

    fun lagreVurdering(connection: Connection, jobbsøkerId: Long, arbeidsgiverId: Long, vurdering: Vurdering) {
        val sql = """
            INSERT INTO vurdering (jobbsoker_id, arbeidsgiver_id, vurdering, andregangsintervju, andregangsintervju_dato, jobbtilbud)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT (jobbsoker_id, arbeidsgiver_id) DO UPDATE SET
                vurdering = EXCLUDED.vurdering,
                andregangsintervju = EXCLUDED.andregangsintervju,
                andregangsintervju_dato = EXCLUDED.andregangsintervju_dato,
                jobbtilbud = EXCLUDED.jobbtilbud
            RETURNING vurdering_id
        """.trimIndent()
        val vurderingId = connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.setLong(2, arbeidsgiverId)
            stmt.setString(3, vurdering.vurdering?.name)
            stmt.setBoolean(4, vurdering.andregangsintervju)
            stmt.setDate(5, vurdering.andregangsintervjuDato?.let { java.sql.Date.valueOf(it) })
            stmt.setBoolean(6, vurdering.jobbtilbud)
            stmt.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
        }
        erstattNotater(connection, vurderingId, vurdering.notater)
    }

    private fun erstattNotater(connection: Connection, vurderingId: Long, notater: List<Vurderingsnotat>) {
        connection.prepareStatement("DELETE FROM vurdering_notat WHERE vurdering_id = ?").use { stmt ->
            stmt.setLong(1, vurderingId)
            stmt.executeUpdate()
        }
        if (notater.isEmpty()) return
        connection.prepareStatement("INSERT INTO vurdering_notat (vurdering_id, notat) VALUES (?, ?)").use { stmt ->
            notater.distinct().forEach { notat ->
                stmt.setLong(1, vurderingId)
                stmt.setString(2, notat.name)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
    }

    /** En rad uten innhold er ikke en tom registrering, den er fravær av registrering. */
    fun slettVurdering(connection: Connection, jobbsøkerId: Long, arbeidsgiverId: Long) {
        connection.prepareStatement("DELETE FROM vurdering WHERE jobbsoker_id = ? AND arbeidsgiver_id = ?").use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.setLong(2, arbeidsgiverId)
            stmt.executeUpdate()
        }
    }
}
