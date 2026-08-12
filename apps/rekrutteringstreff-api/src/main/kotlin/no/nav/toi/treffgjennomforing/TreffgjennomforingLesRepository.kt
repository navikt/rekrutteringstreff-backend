package no.nav.toi.treffgjennomforing

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import java.sql.Connection
import java.sql.ResultSet
import java.time.LocalTime

class TreffgjennomforingLesRepository {

    /**
     * Rent lesende. Finnes ingen lagret rad, er svaret tomtilstanden — det
     * opprettes ingenting av å åpne fanen.
     */
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

    /**
     * Rommene utledes på nytt ved lesing. Antall rom følger arbeidsgiverne, og
     * oppmøtet kan ha endret seg siden fordelingen ble lagret.
     */
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

    /**
     * Oppmøte har ingen egen tabell. Den siste av MØTT_OPP / ANGRE_MØTT_OPP
     * bestemmer tilstanden, med hendelses-ID som tie-breaker ved likt tidspunkt.
     */
    private fun hentOppmøte(connection: Connection, treffDbId: Long): List<PersonTreffId> {
        val sql = """
            SELECT j.id::text
            FROM jobbsoker j
            JOIN LATERAL (
                SELECT jh.hendelsestype
                FROM jobbsoker_hendelse jh
                WHERE jh.jobbsoker_id = j.jobbsoker_id
                  AND jh.hendelsestype IN ('MØTT_OPP', 'ANGRE_MØTT_OPP')
                ORDER BY jh.tidspunkt DESC, jh.jobbsoker_hendelse_id DESC
                LIMIT 1
            ) siste ON TRUE
            LEFT JOIN deltakernummer d ON d.jobbsoker_id = j.jobbsoker_id
            WHERE j.rekrutteringstreff_id = ?
              AND j.status != 'SLETTET'
              AND siste.hendelsestype = 'MØTT_OPP'
            ORDER BY d.nummer NULLS LAST, j.jobbsoker_id
        """.trimIndent()
        return connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, treffDbId)
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

    /**
     * En arbeidsgiver lagt til etter at møteplanen ble laget har ingen lagret
     * posisjon. Den får første ledige ved lesing framfor å falle ut av rotasjonen.
     */
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

    /** Plasseringen er tidsluka, så rekkefølgen på de inkluderte er data — ikke pynt. */
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
}

internal fun <T> ResultSet.tilListe(les: (ResultSet) -> T): List<T> =
    generateSequence { if (next()) les(this) else null }.toList()

internal fun LocalTime.tilSqlTime(): java.sql.Time = java.sql.Time.valueOf(this)
