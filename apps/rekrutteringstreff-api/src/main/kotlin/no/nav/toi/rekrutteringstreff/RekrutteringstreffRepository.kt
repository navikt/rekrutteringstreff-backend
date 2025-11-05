package no.nav.toi.rekrutteringstreff

import com.fasterxml.jackson.core.type.TypeReference
import io.javalin.http.NotFoundResponse
import no.nav.toi.*
import no.nav.toi.rekrutteringstreff.dto.FellesHendelseOutboundDto
import no.nav.toi.rekrutteringstreff.dto.OppdaterRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.dto.OpprettRekrutteringstreffInternalDto
import no.nav.toi.rekrutteringstreff.dto.RekrutteringstreffDto
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.time.ZonedDateTime
import java.util.*
import javax.sql.DataSource

data class RekrutteringstreffHendelse(
    val id: UUID,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: RekrutteringstreffHendelsestype,
    val opprettetAvAktørType: AktørType,
    val aktørIdentifikasjon: String?
)

data class RekrutteringstreffHendelseOutboundDto(
    val id: String,
    val tidspunkt: ZonedDateTime,
    val hendelsestype: String,
    val opprettetAvAktørType: String,
    val aktørIdentifikasjon: String?
)

data class RekrutteringstreffDetaljOutboundDto(
    val rekrutteringstreff: RekrutteringstreffDto,
    val hendelser: List<RekrutteringstreffHendelseOutboundDto>
)

enum class HendelseRessurs {
    REKRUTTERINGSTREFF, JOBBSØKER, ARBEIDSGIVER
}

class RekrutteringstreffRepository(
    private val dataSource: DataSource,
) {
    companion object {
        private const val tabellnavn = "rekrutteringstreff"
        private const val id = "id"
        private const val tittel = "tittel"
        private const val beskrivelse = "beskrivelse"
        private const val status = "status"
        private const val opprettetAvPersonNavident = "opprettet_av_person_navident"
        private const val opprettetAvKontorEnhetid = "opprettet_av_kontor_enhetid"
        private const val opprettetAvTidspunkt = "opprettet_av_tidspunkt"
        private const val fratid = "fratid"
        private const val tiltid = "tiltid"
        private const val svarfrist = "svarfrist"
        private const val eiere = "eiere"
        private const val innlegg = "innlegg"
        private const val gateadresse = "gateadresse"
        private const val postnummer = "postnummer"
        private const val poststed = "poststed"
    }

    fun opprett(dto: OpprettRekrutteringstreffInternalDto): TreffId {
        val nyTreffId = TreffId(UUID.randomUUID())
        dataSource.executeInTransaction { connection ->
            val dbId = connection.prepareStatement(
                """
                INSERT INTO $tabellnavn($id,$tittel,$status,$opprettetAvPersonNavident,
                                         $opprettetAvKontorEnhetid,$opprettetAvTidspunkt,$eiere)
                VALUES (?,?,?,?,?,?,?)
                RETURNING rekrutteringstreff_id
                """
            ).apply {
                var i = 0
                setObject(++i, nyTreffId.somUuid)
                setString(++i, dto.tittel)
                setString(++i, RekrutteringstreffStatus.UTKAST.name)
                setString(++i, dto.opprettetAvPersonNavident)
                setString(++i, dto.opprettetAvNavkontorEnhetId)
                setTimestamp(++i, Timestamp.from(Instant.now()))
                setArray(++i, connection.createArrayOf("text", arrayOf(dto.opprettetAvPersonNavident)))
            }.executeQuery().run { next(); getLong(1) }

            leggTilHendelse(connection, dbId, RekrutteringstreffHendelsestype.OPPRETTET, AktørType.ARRANGØR, dto.opprettetAvPersonNavident)
        }
        return nyTreffId
    }

    fun oppdater(treff: TreffId, dto: OppdaterRekrutteringstreffDto, oppdatertAv: String) {
        dataSource.connection.use { connection ->
            val dbId = connection.prepareStatement("SELECT rekrutteringstreff_id FROM $tabellnavn WHERE $id=?")
                .apply { setObject(1, treff.somUuid) }
                .executeQuery()
                .run { next(); getLong(1) }

            connection.prepareStatement(
                """
                UPDATE $tabellnavn
                SET $tittel=?, $beskrivelse=?, $fratid=?, $tiltid=?, $svarfrist=?, $gateadresse=?, $postnummer=?, poststed=?
                WHERE $id=?
                """
            ).apply {
                var i = 0
                setString(++i, dto.tittel)
                setString(++i, dto.beskrivelse)
                setTimestamp(++i, if(dto.fraTid != null)  Timestamp.from(dto.fraTid.toInstant()) else null)
                setTimestamp(++i, if(dto.tilTid != null) Timestamp.from(dto.tilTid.toInstant()) else null)
                setTimestamp(++i, if(dto.svarfrist != null) Timestamp.from(dto.svarfrist.toInstant()) else null)
                setString(++i, dto.gateadresse)
                setString(++i, dto.postnummer)
                setString(++i, dto.poststed)
                setObject(++i, treff.somUuid)
            }.executeUpdate()

            leggTilHendelse(connection, dbId, RekrutteringstreffHendelsestype.OPPDATERT, AktørType.ARRANGØR, oppdatertAv)
        }
    }

    fun slett(treff: TreffId) {
        // TODO: Skal vi faktisk slette alt, eller kun endre til status DELETED?
        dataSource.executeInTransaction { connection ->
            val dbIdRs = connection.prepareStatement("SELECT rekrutteringstreff_id FROM $tabellnavn WHERE $id = ?").apply {
                setObject(1, treff.somUuid)
            }.executeQuery()
            if (!dbIdRs.next()) throw NotFoundResponse("Rekrutteringstreff ikke funnet")
            val treffDbId = dbIdRs.getLong("rekrutteringstreff_id")

            // Ikke lov å slette etter publisering
            connection.prepareStatement(
                """
                SELECT 1
                FROM rekrutteringstreff_hendelse h
                WHERE h.rekrutteringstreff_id = ? AND h.hendelsestype = 'PUBLISERT'
                LIMIT 1
                """.trimIndent()
            ).use { s ->
                s.setLong(1, treffDbId)
                val rs = s.executeQuery()
                if (rs.next()) throw UlovligSlettingException("Kan ikke slette etter publisering.")
            }

            // Slett i riktig rekkefølge - FK constraints vil feile dersom det finnes blokkerende data
            // arbeidsgiver_hendelse
            connection.prepareStatement(
                """
                DELETE FROM arbeidsgiver_hendelse ah
                USING arbeidsgiver ag
                WHERE ah.arbeidsgiver_id = ag.arbeidsgiver_id AND ag.rekrutteringstreff_id = ?
                """.trimIndent()
            ).use { s -> s.setLong(1, treffDbId); s.executeUpdate() }

            connection.prepareStatement(
                """
                DELETE FROM rekrutteringstreff_hendelse WHERE rekrutteringstreff_id = ?
                """.trimIndent()
            ).use { s -> s.setLong(1, treffDbId); s.executeUpdate() }

            connection.prepareStatement(
                """
                DELETE FROM naringskode nk
                USING arbeidsgiver ag
                WHERE nk.arbeidsgiver_id = ag.arbeidsgiver_id AND ag.rekrutteringstreff_id = ?
                """.trimIndent()
            ).use { s -> s.setLong(1, treffDbId); s.executeUpdate() }

            connection.prepareStatement(
                """
                DELETE FROM innlegg WHERE rekrutteringstreff_id = ?
                """.trimIndent()
            ).use { s -> s.setLong(1, treffDbId); s.executeUpdate() }

            connection.prepareStatement(
                """
                DELETE FROM arbeidsgiver WHERE rekrutteringstreff_id = ?
                """.trimIndent()
            ).use { s -> s.setLong(1, treffDbId); s.executeUpdate() }

            connection.prepareStatement(
                """
                DELETE FROM ki_spørring_logg WHERE treff_id = ?
                """.trimIndent()
            ).use { s -> s.setObject(1, treff.somUuid); s.executeUpdate() }

            // rekrutteringstreff - vil feile med FK constraint dersom jobbsoker finnes
            connection.prepareStatement("DELETE FROM $tabellnavn WHERE $id = ?").use {
                it.setObject(1, treff.somUuid)
                it.executeUpdate()
            }
        }
    }

    fun hentAlle(): List<Rekrutteringstreff> =
        dataSource.connection.use { c ->
            c.prepareStatement("SELECT * FROM $tabellnavn").use { s ->
                s.executeQuery().let { rs ->
                    generateSequence {
                        if (rs.next()) rs.tilRekrutteringstreff() else null
                    }.toList()
                }
            }
        }

    fun hent(treff: TreffId): Rekrutteringstreff? =
        dataSource.connection.use { c ->
            c.prepareStatement("SELECT * FROM $tabellnavn WHERE $id = ?").use { s ->
                s.setObject(1, treff.somUuid)
                s.executeQuery().let { rs -> if (rs.next()) rs.tilRekrutteringstreff() else null }
            }
        }

// Fjerne og heller håndtere i Service?
    fun hentMedHendelser(treff: TreffId): RekrutteringstreffDetaljOutboundDto? =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                SELECT r.*,
                       COALESCE(
                           json_agg(
                               json_build_object(
                                   'id', h.id,
                                   'tidspunkt', to_char(h.tidspunkt,'YYYY-MM-DD"T"HH24:MI:SSOF'),
                                   'hendelsestype', h.hendelsestype,
                                   'opprettetAvAktørType', h.opprettet_av_aktortype,
                                   'aktørIdentifikasjon', h.aktøridentifikasjon
                               )
                           ) FILTER (WHERE h.id IS NOT NULL),
                           '[]'
                       ) AS hendelser
                FROM   rekrutteringstreff r
                LEFT JOIN rekrutteringstreff_hendelse h
                   ON r.rekrutteringstreff_id = h.rekrutteringstreff_id
                WHERE  r.id = ?
                GROUP BY r.rekrutteringstreff_id
                """
            ).use { s ->
                s.setObject(1, treff.somUuid)
                s.executeQuery().let { rs ->
                    if (!rs.next()) return null
                    val hendelserJson = rs.getString("hendelser")
                    val hendelser = JacksonConfig.mapper.readValue(
                        hendelserJson,
                        object : TypeReference<List<RekrutteringstreffHendelseOutboundDto>>() {}
                    )
                    RekrutteringstreffDetaljOutboundDto(
                        rekrutteringstreff = RekrutteringstreffDto(
                            id = rs.getObject("id", UUID::class.java),
                            tittel = rs.getString("tittel"),
                            beskrivelse = rs.getString("beskrivelse"),
                            fraTid = rs.getTimestamp("fratid")?.toInstant()?.atOslo(),
                            tilTid = rs.getTimestamp("tiltid")?.toInstant()?.atOslo(),
                            svarfrist = rs.getTimestamp("svarfrist")?.toInstant()?.atOslo(),
                            gateadresse = rs.getString("gateadresse"),
                            postnummer = rs.getString("postnummer"),
                            poststed = rs.getString("poststed"),
                            status = RekrutteringstreffStatus.valueOf(rs.getString("status")),
                            opprettetAvPersonNavident = rs.getString("opprettet_av_person_navident"),
                            opprettetAvNavkontorEnhetId = rs.getString("opprettet_av_kontor_enhetid"),
                            opprettetAvTidspunkt = rs.getTimestamp("opprettet_av_tidspunkt").toInstant().atOslo(),
                            antallArbeidsgivere = null,
                            antallJobbsøkere = null
                        ),
                        hendelser = hendelser
                    )
                }
            }
        }

    /**
     * Liste over hendelser for gitt treffId – nyeste først
     */
    fun hentHendelser(treff: TreffId): List<RekrutteringstreffHendelse> =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
                SELECT  h.id                  AS hendelse_id,
                        h.tidspunkt           AS tidspunkt,
                        h.hendelsestype       AS hendelsestype,
                        h.opprettet_av_aktortype AS aktørtype,
                        h.aktøridentifikasjon AS ident
                FROM    rekrutteringstreff_hendelse h
                JOIN    rekrutteringstreff r ON h.rekrutteringstreff_id = r.rekrutteringstreff_id
                WHERE   r.id = ?
                ORDER BY h.tidspunkt DESC
                """
            ).use { s ->
                s.setObject(1, treff.somUuid)
                s.executeQuery().let { rs ->
                    generateSequence {
                        if (rs.next()) RekrutteringstreffHendelse(
                            id = UUID.fromString(rs.getString("hendelse_id")),
                            tidspunkt = rs.getTimestamp("tidspunkt").toInstant().atOslo(),
                            hendelsestype = RekrutteringstreffHendelsestype.valueOf(rs.getString("hendelsestype")),
                            opprettetAvAktørType = AktørType.valueOf(rs.getString("aktørtype")),
                            aktørIdentifikasjon = rs.getString("ident")
                        ) else null
                    }.toList()
                }
            }
        }

    fun hentAlleHendelser(treff: TreffId): List<FellesHendelseOutboundDto> =
        dataSource.connection.use { c ->
            c.prepareStatement(
                """
            SELECT id, tidspunkt, hendelsestype, opprettet_av_aktortype, aktøridentifikasjon, ressurs
            FROM (
                SELECT h.id,
                       '${HendelseRessurs.REKRUTTERINGSTREFF.name}' AS ressurs,
                       h.tidspunkt,
                       h.hendelsestype,
                       h.opprettet_av_aktortype,
                       h.aktøridentifikasjon
                FROM   rekrutteringstreff_hendelse h
                JOIN   rekrutteringstreff r ON r.rekrutteringstreff_id = h.rekrutteringstreff_id
                WHERE  r.id = ?

                UNION ALL

                SELECT jh.id,
                       '${HendelseRessurs.JOBBSØKER.name}' AS ressurs,
                       jh.tidspunkt,
                       jh.hendelsestype,
                       jh.opprettet_av_aktortype,
                       jh.aktøridentifikasjon
                FROM   jobbsoker_hendelse jh
                JOIN   jobbsoker js        ON js.jobbsoker_id = jh.jobbsoker_id
                JOIN   rekrutteringstreff r ON r.rekrutteringstreff_id = js.rekrutteringstreff_id
                WHERE  r.id = ?

                UNION ALL

                SELECT ah.id,
                       '${HendelseRessurs.ARBEIDSGIVER.name}' AS ressurs,
                       ah.tidspunkt,
                       ah.hendelsestype,
                       ah.opprettet_av_aktortype,
                       ah.aktøridentifikasjon
                FROM   arbeidsgiver_hendelse ah
                JOIN   arbeidsgiver ag      ON ag.arbeidsgiver_id = ah.arbeidsgiver_id
                JOIN   rekrutteringstreff r ON r.rekrutteringstreff_id = ag.rekrutteringstreff_id
                WHERE  r.id = ?
            ) AS union_hendelser
            ORDER BY tidspunkt DESC
            """
            ).use { s ->
                repeat(3) { idx -> s.setObject(idx + 1, treff.somUuid) }
                s.executeQuery().let { rs ->
                    generateSequence {
                        if (rs.next()) FellesHendelseOutboundDto(
                            id = rs.getString("id"),
                            tidspunkt = rs.getTimestamp("tidspunkt").toInstant().atOslo(),
                            hendelsestype = rs.getString("hendelsestype"),
                            opprettetAvAktørType = rs.getString("opprettet_av_aktortype"),
                            aktørIdentifikasjon = rs.getString("aktøridentifikasjon"),
                            ressurs = HendelseRessurs.valueOf(rs.getString("ressurs"))
                        ) else null
                    }.toList()
                }
            }
        }

    fun publiser(treff: TreffId, publisertAv: String) {
        dataSource.executeInTransaction { connection ->
            leggTilHendelseForTreff(connection, treff, RekrutteringstreffHendelsestype.PUBLISERT, publisertAv)
            endreStatus(connection, treff, RekrutteringstreffStatus.PUBLISERT)
        }
    }

    fun gjenåpne(treff: TreffId, gjenapnetAv: String) {
        dataSource.executeInTransaction { connection ->
            leggTilHendelseForTreff(connection, treff, RekrutteringstreffHendelsestype.GJENÅPNET, gjenapnetAv)
            endreStatus(connection, treff, RekrutteringstreffStatus.PUBLISERT) // TODO: sjekk om status skal være UTKAST eller PUBLISERT
        }
    }

    fun avpubliser(treff: TreffId, avpublisertAv: String) {
        dataSource.executeInTransaction { connection ->
            leggTilHendelseForTreff(connection, treff, RekrutteringstreffHendelsestype.AVPUBLISERT, avpublisertAv)
            endreStatus(connection, treff, RekrutteringstreffStatus.UTKAST)
        }
    }

    private fun leggTilHendelseForTreff(connection: Connection, treff: TreffId, hendelsestype: RekrutteringstreffHendelsestype, ident: String) {
        val dbId = connection.prepareStatement("SELECT rekrutteringstreff_id FROM $tabellnavn WHERE $id=?")
            .apply { setObject(1, treff.somUuid) }
            .executeQuery()
            .let { rs -> if (rs.next()) rs.getLong(1) else throw NotFoundResponse("Treff med id ${treff.somUuid} finnes ikke") }

        leggTilHendelse(connection, dbId, hendelsestype, AktørType.ARRANGØR, ident)
    }

    fun hentRekrutteringstreffDbId(c: Connection, treff: TreffId): Long {
        return c.prepareStatement("SELECT rekrutteringstreff_id FROM $tabellnavn WHERE $id=?")
            .apply { setObject(1, treff.somUuid) }
            .executeQuery()
            .let { rs ->
                if (rs.next()) rs.getLong(1)
                else throw NotFoundResponse("Treff med id ${treff.somUuid} finnes ikke")
            }
    }

    fun leggTilHendelse(
        c: Connection,
        treffDbId: Long,
        type: RekrutteringstreffHendelsestype,
        aktørType: AktørType,
        ident: String
    ) {
        c.prepareStatement(
            """
            INSERT INTO rekrutteringstreff_hendelse
                   (id, rekrutteringstreff_id, tidspunkt,
                    hendelsestype, opprettet_av_aktortype, aktøridentifikasjon)
            VALUES (?, ?, now(), ?, ?, ?)
            """
        ).use { s ->
            s.setObject(1, UUID.randomUUID())
            s.setLong(2, treffDbId)
            s.setString(3, type.name)
            s.setString(4, aktørType.name)
            s.setString(5, ident)
            s.executeUpdate()
        }
    }

    fun endreStatus(treffId: TreffId, rekrutteringstreffStatus: RekrutteringstreffStatus) {
        dataSource.connection.use { connection ->
            endreStatus(connection, treffId, rekrutteringstreffStatus)
        }
    }

    fun endreStatus(connection: Connection, treffId: TreffId, rekrutteringstreffStatus: RekrutteringstreffStatus) {
            connection.prepareStatement(
                """
                UPDATE $tabellnavn
                SET $status=?
                WHERE $id=?
                """
            ).apply {
                var i = 0
                setString(++i, rekrutteringstreffStatus.name)
                setObject(++i, treffId.somUuid)
            }.executeUpdate()
    }

    private fun ResultSet.tilRekrutteringstreff() = Rekrutteringstreff(
        id = TreffId(getObject(id, UUID::class.java)),
        tittel = getString(tittel),
        beskrivelse = getString(beskrivelse),
        fraTid = getTimestamp(fratid)?.toInstant()?.atOslo(),
        tilTid = getTimestamp(tiltid)?.toInstant()?.atOslo(),
        svarfrist = getTimestamp(svarfrist)?.toInstant()?.atOslo(),
        gateadresse = getString(gateadresse),
        postnummer = getString(postnummer),
        poststed = getString(poststed),
        status = RekrutteringstreffStatus.valueOf(getString(status)),
        opprettetAvPersonNavident = getString(opprettetAvPersonNavident),
        opprettetAvNavkontorEnhetId = getString(opprettetAvKontorEnhetid),
        opprettetAvTidspunkt = getTimestamp(opprettetAvTidspunkt).toInstant().atOslo()
    )
}
