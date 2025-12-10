package no.nav.toi.rekrutteringstreff

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
        private const val kommune = "kommune"
        private const val kommunenummer = "kommunenummer"
        private const val fylke = "fylke"
        private const val fylkesnummer = "fylkesnummer"
        private const val sistEndret = "sist_endret"
        private const val sistEndretAv = "sist_endret_av"
    }

    fun opprett(dto: OpprettRekrutteringstreffInternalDto): TreffId {
        val nyTreffId = TreffId(UUID.randomUUID())
        dataSource.executeInTransaction { connection ->
            val dbId = connection.prepareStatement(
                """
                INSERT INTO $tabellnavn($id,$tittel,$status,$opprettetAvPersonNavident,
                                         $opprettetAvKontorEnhetid,$opprettetAvTidspunkt,$eiere, $sistEndret, $sistEndretAv)
                VALUES (?,?,?,?,?,?,?,?,?)
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
                setTimestamp(++i, Timestamp.from(Instant.now()))
                setString(++i, dto.opprettetAvPersonNavident)
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
                SET $tittel=?, $beskrivelse=?, $fratid=?, $tiltid=?, $svarfrist=?, $gateadresse=?, $postnummer=?, $poststed=?, $kommune=?, $kommunenummer=?, $fylke=?, $fylkesnummer=?, $sistEndret=?, $sistEndretAv=?
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
                setString(++i, dto.kommune)
                setString(++i, dto.kommunenummer)
                setString(++i, dto.fylke)
                setString(++i, dto.fylkesnummer)
                setTimestamp(++i, Timestamp.from(Instant.now()))
                setString(++i, oppdatertAv)
                setObject(++i, treff.somUuid)
            }.executeUpdate()

            leggTilHendelse(connection, dbId, RekrutteringstreffHendelsestype.OPPDATERT, AktørType.ARRANGØR, oppdatertAv)
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

    fun leggTilHendelseForTreff(connection: Connection, treff: TreffId, hendelsestype: RekrutteringstreffHendelsestype, ident: String) {
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
        ident: String,
        hendelseData: String? = null
    ) {
        c.prepareStatement(
            """
            INSERT INTO rekrutteringstreff_hendelse
                   (id, rekrutteringstreff_id, tidspunkt,
                    hendelsestype, opprettet_av_aktortype, aktøridentifikasjon, hendelse_data)
            VALUES (?, ?, now(), ?, ?, ?, ?::jsonb)
            """
        ).use { s ->
            s.setObject(1, UUID.randomUUID())
            s.setLong(2, treffDbId)
            s.setString(3, type.name)
            s.setString(4, aktørType.name)
            s.setString(5, ident)
            s.setString(6, hendelseData)
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
        kommune = getString(kommune),
        kommunenummer = getString(kommunenummer),
        fylke = getString(fylke),
        fylkesnummer = getString(fylkesnummer),
        status = RekrutteringstreffStatus.valueOf(getString(status)),
        opprettetAvPersonNavident = getString(opprettetAvPersonNavident),
        opprettetAvNavkontorEnhetId = getString(opprettetAvKontorEnhetid),
        opprettetAvTidspunkt = getTimestamp(opprettetAvTidspunkt).toInstant().atOslo(),
        eiere = (getArray(eiere).array as Array<String>).toList(),
        sistEndret = getTimestamp(sistEndret).toInstant().atOslo(),
        sistEndretAv = getString(sistEndretAv) ?: "Ukjent",
    )
}
