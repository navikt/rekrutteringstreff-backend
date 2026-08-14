package no.nav.toi.treffgjennomføring

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.Oppmøte
import no.nav.toi.jobbsoker.PersonTreffId
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.time.LocalTime

data class Treffgjennomføringsrad(val id: Long, val fase: TreffgjennomføringFase)

class TreffgjennomføringRepository {

    fun hentAggregat(connection: Connection, kontekst: Treffkontekst): Treffgjennomføring {
        val treffDbId = kontekst.treffDbId
        val oppmøte = hentOppmøte(connection, treffDbId)
        val lagretRom = hentRom(connection, treffDbId)

        return Treffgjennomføring(
            fase = hentFase(connection, treffDbId) ?: TreffgjennomføringFase.OPPMØTE,
            antallRom = kontekst.antallRom,
            møteoppsett = hentMøteoppsett(connection, treffDbId) ?: standardMøteoppsett(),
            oppmøte = oppmøte,
            deltakernummer = hentDeltakernummer(connection, treffDbId),
            rom = normaliserRom(lagretRom, oppmøte, kontekst.antallRom),
            arbeidsgiverRekkefølge = hentRotasjon(connection, treffDbId, kontekst),
            interesser = hentInteresser(connection, treffDbId),
            intervjufordelinger = hentIntervjufordelinger(connection, treffDbId, kontekst),
            vurderinger = hentVurderinger(connection, treffDbId),
        )
    }

    private fun standardMøteoppsett() = Møteoppsett(
        Treffgjennomføring.STANDARD_STARTTIDSPUNKT,
        Treffgjennomføring.STANDARD_VARIGHET_MINUTTER,
    )

    private fun normaliserRom(rom: List<Rom>, oppmøte: List<PersonTreffId>, antallRom: Int): List<Rom> =
        if (rom.isEmpty()) emptyList()
        else Romfordeler.oppdaterEtterOppmøte(Romfordeler.normaliser(rom, antallRom), oppmøte)

    private fun hentFase(connection: Connection, treffDbId: Long): TreffgjennomføringFase? {
        val sql = "SELECT fase FROM treffgjennomforing WHERE rekrutteringstreff_id = ?"
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) tilFase(rs.getString(1)) else null
            }
        }
    }

    private fun tilFase(verdi: String?) =
        TreffgjennomføringFase.entries.firstOrNull { it.name == verdi } ?: TreffgjennomføringFase.OPPMØTE

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

    private fun hentOppmøte(connection: Connection, treffDbId: Long): List<PersonTreffId> {
        val sql = """
            SELECT j.id::text
            FROM jobbsoker j
            LEFT JOIN deltakernummer d ON d.jobbsoker_id = j.jobbsoker_id
            WHERE j.rekrutteringstreff_id = ?
              AND j.status != 'SLETTET'
              AND j.oppmote = ?
            ORDER BY d.nummer NULLS LAST, j.jobbsoker_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.setString(2, Oppmøte.REGISTRERT_OPPMØTE.name)
            stmt.executeQuery().use { rs -> rs.tilListe { PersonTreffId(it.getString(1)) } }
        }
    }

    private fun hentDeltakernummer(connection: Connection, treffDbId: Long): List<Deltakernummer> {
        val sql = """
            SELECT j.id::text, d.nummer
            FROM deltakernummer d
            JOIN jobbsoker j ON j.jobbsoker_id = d.jobbsoker_id
            WHERE d.rekrutteringstreff_id = ? AND j.status != 'SLETTET'
            ORDER BY d.nummer
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe { Deltakernummer(PersonTreffId(it.getString(1)), it.getInt(2)) }
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

    private fun hentRotasjon(
        connection: Connection,
        treffDbId: Long,
        kontekst: Treffkontekst,
    ): List<ArbeidsgiverRotasjon> {
        val sql = """
            SELECT a.id::text, r.start_posisjon
            FROM arbeidsgiver_rotasjon r
            JOIN arbeidsgiver a ON a.arbeidsgiver_id = r.arbeidsgiver_id
            WHERE a.rekrutteringstreff_id = ? AND a.status = 'AKTIV'
        """.trimIndent()
        val lagret = connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
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

    private fun hentInteresser(connection: Connection, treffDbId: Long): List<Interesse> {
        val sql = """
            SELECT j.id::text, a.id::text
            FROM interesse i
            JOIN jobbsoker j ON j.jobbsoker_id = i.jobbsoker_id
            JOIN arbeidsgiver a ON a.arbeidsgiver_id = i.arbeidsgiver_id
            WHERE j.rekrutteringstreff_id = ? AND j.status != 'SLETTET' AND a.status = 'AKTIV'
            ORDER BY a.arbeidsgiver_id, j.jobbsoker_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe { Interesse(PersonTreffId(it.getString(1)), ArbeidsgiverTreffId(it.getString(2))) }
            }
        }
    }

    private fun hentIntervjufordelinger(
        connection: Connection,
        treffDbId: Long,
        kontekst: Treffkontekst,
    ): List<ArbeidsgiverIntervjufordeling> {
        val sql = """
            SELECT a.id::text, j.id::text, f.inkludert
            FROM intervju_fordeling f
            JOIN jobbsoker j ON j.jobbsoker_id = f.jobbsoker_id
            JOIN arbeidsgiver a ON a.arbeidsgiver_id = f.arbeidsgiver_id
            WHERE j.rekrutteringstreff_id = ? AND j.status != 'SLETTET' AND a.status = 'AKTIV'
            ORDER BY a.arbeidsgiver_id, f.plassering
        """.trimIndent()
        val rader = connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe {
                    Triple(ArbeidsgiverTreffId(it.getString(1)), PersonTreffId(it.getString(2)), it.getBoolean(3))
                }
            }
        }
        val perArbeidsgiver = rader.groupBy { it.first }
        return kontekst.arbeidsgiverIder.mapNotNull { arbeidsgiver ->
            val egne = perArbeidsgiver[arbeidsgiver] ?: return@mapNotNull null
            ArbeidsgiverIntervjufordeling(
                arbeidsgiverTreffId = arbeidsgiver,
                inkludertePersonTreffIder = egne.filter { it.third }.map { it.second },
                ekskludertePersonTreffIder = egne.filterNot { it.third }.map { it.second },
            )
        }
    }

    private fun hentVurderinger(connection: Connection, treffDbId: Long): List<Vurdering> {
        val notater = hentNotater(connection, treffDbId)
        val sql = """
            SELECT v.vurdering_id, j.id::text, a.id::text, v.vurdering,
                   v.andregangsintervju, v.andregangsintervju_dato, v.jobbtilbud
            FROM vurdering v
            JOIN jobbsoker j ON j.jobbsoker_id = v.jobbsoker_id
            JOIN arbeidsgiver a ON a.arbeidsgiver_id = v.arbeidsgiver_id
            WHERE j.rekrutteringstreff_id = ? AND j.status != 'SLETTET' AND a.status = 'AKTIV'
            ORDER BY a.arbeidsgiver_id, j.jobbsoker_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe {
                    Vurdering(
                        personTreffId = PersonTreffId(it.getString(2)),
                        arbeidsgiverTreffId = ArbeidsgiverTreffId(it.getString(3)),
                        vurdering = Vurderingsvalg.entries.firstOrNull { valg -> valg.name == it.getString(4) },
                        notater = notater[it.getLong(1)].orEmpty(),
                        andregangsintervju = it.getBoolean(5),
                        andregangsintervjuDato = it.getDate(6)?.toLocalDate(),
                        jobbtilbud = it.getBoolean(7),
                    )
                }
            }
        }
    }

    private fun hentNotater(connection: Connection, treffDbId: Long): Map<Long, List<Vurderingsnotat>> {
        val sql = """
            SELECT n.vurdering_id, n.notat
            FROM vurdering_notat n
            JOIN vurdering v ON v.vurdering_id = n.vurdering_id
            JOIN jobbsoker j ON j.jobbsoker_id = v.jobbsoker_id
            WHERE j.rekrutteringstreff_id = ?
            ORDER BY n.vurdering_notat_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
            stmt.executeQuery().use { rs ->
                rs.tilListe { it.getLong(1) to it.getString(2) }
                    .mapNotNull { (id, navn) ->
                        Vurderingsnotat.entries.firstOrNull { it.name == navn }?.let { id to it }
                    }
                    .groupBy({ it.first }, { it.second })
            }
        }
    }

    fun sikreRad(connection: Connection, treffDbId: Long): Treffgjennomføringsrad {
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
                Treffgjennomføringsrad(
                    rs.getLong(1),
                    TreffgjennomføringFase.entries.firstOrNull { it.name == rs.getString(2) }
                        ?: TreffgjennomføringFase.OPPMØTE,
                )
            }
        }
    }

    fun settFase(connection: Connection, treffDbId: Long, nåværende: TreffgjennomføringFase, ny: TreffgjennomføringFase) {
        if (ny.ordinal <= nåværende.ordinal) return
        connection.prepareStatement("UPDATE treffgjennomforing SET fase = ? WHERE rekrutteringstreff_id = ?").use { stmt ->
            stmt.setString(1, ny.name)
            stmt.setLong(2, treffDbId)
            stmt.executeUpdate()
        }
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

    fun harMøteoppsett(connection: Connection, treffgjennomføringId: Long): Boolean =
        connection.prepareStatement("SELECT 1 FROM moteoppsett WHERE treffgjennomforing_id = ?").use { stmt ->
            stmt.setLong(1, treffgjennomføringId)
            stmt.executeQuery().use { it.next() }
        }

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

    fun slettVurdering(connection: Connection, jobbsøkerId: Long, arbeidsgiverId: Long) {
        connection.prepareStatement("DELETE FROM vurdering WHERE jobbsoker_id = ? AND arbeidsgiver_id = ?").use { stmt ->
            stmt.setLong(1, jobbsøkerId)
            stmt.setLong(2, arbeidsgiverId)
            stmt.executeUpdate()
        }
    }
}

internal fun <T> ResultSet.tilListe(les: (ResultSet) -> T): List<T> =
    generateSequence { if (next()) les(this) else null }.toList()

internal fun LocalTime.tilSqlTime(): java.sql.Time = java.sql.Time.valueOf(this)
