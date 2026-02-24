package no.nav.toi

import no.nav.toi.aktivitetskort.*
import org.flywaydb.core.Flyway
import java.sql.Timestamp
import java.sql.Types.VARCHAR
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*

class Repository(databaseConfig: DatabaseConfig, private val minsideUrl: String, private val dabAktivitetskortTopic: String) {
    private val dataSource = databaseConfig.lagDatasource()
    private val secureLog = SecureLog(log)

    fun opprettRekrutteringstreffInvitasjon(
        fnr: String,
        rekrutteringstreffId: UUID,
        tittel: String,
        beskrivelse: String,
        startDato: LocalDate,
        sluttDato: LocalDate,
        tid: String,
        endretAv: String,
        gateAdresse: String,
        postnummer: String,
        poststed: String
    ): UUID? {
        val aktivitietskortId = UUID.randomUUID()
        dataSource.connection.use { connection ->
            try {
                connection.autoCommit = false

                val endredeLinjer = connection.prepareStatement(
                    """
                    INSERT INTO rekrutteringstreff (
                        aktivitetskort_id, fnr, rekrutteringstreff_id
                    ) VALUES (?, ?, ?)
                    ON CONFLICT (fnr, rekrutteringstreff_id) DO NOTHING
                    """.trimIndent()
                ).apply {
                    setObject(1, aktivitietskortId)
                    setString(2, fnr)
                    setObject(3, rekrutteringstreffId)
                }.executeUpdate()

                if (endredeLinjer == 0) {
                    log.error("Prøvde å opprette aktivitetskort for person på treff som allerede har aktivitetskort: $rekrutteringstreffId")
                    return null
                } else {
                    val messageId = UUID.randomUUID()

                    val endredeLinjer = connection.prepareStatement(
                        """
                    INSERT INTO aktivitetskort (
                        fnr, tittel, beskrivelse, start_dato, slutt_dato, 
                        message_id, aktivitetskort_id, aktivitets_status,
                        endret_av, endret_av_type, endret_tidspunkt,
                        detaljer, handlinger, etiketter, oppgave, action_type, avtalt_med_nav
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, '${AktivitetsStatus.FORSLAG.name}', ?, '${EndretAvType.NAVIDENT.name}', ?, ?::json, ?::json, ?::json, ?::json, '${ActionType.UPSERT_AKTIVITETSKORT_V1.name}', false)
                    """.trimIndent()
                    ).apply {
                        setString(1, fnr)
                        setString(2, tittel)
                        setString(3, beskrivelse)
                        setObject(4, startDato)
                        setObject(5, sluttDato)
                        setObject(6, messageId)
                        setObject(7, aktivitietskortId)
                        setString(8, endretAv)
                        setTimestamp(9, Timestamp.valueOf(LocalDateTime.now()))
                        setString(
                            10, listOf(
                                AktivitetskortDetalj("Tid", tid),
                                AktivitetskortDetalj("Sted", "$gateAdresse, $postnummer $poststed"),
                            ).joinToJson(AktivitetskortDetalj::tilAkaasJson)
                        )
                        setString(
                            11,
                            listOf(
                                AktivitetskortHandling(
                                    "Sjekk ut treffet",
                                    "Sjekk ut treffet og svar",
                                    "$minsideUrl/$rekrutteringstreffId",
                                    LenkeType.FELLES
                                )
                            ).joinToJson(AktivitetskortHandling::tilAkaasJson)
                        )
                        setString(12, "[]")
                        setNull(13, VARCHAR)
                    }.executeUpdate()

                    connection.commit()
                }
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
        return aktivitietskortId
    }

    fun hentUsendteAktivitetskortHendelser() = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT *
            FROM aktivitetskort
            WHERE sendt_tidspunkt IS NULL
            """.trimIndent()
        ).executeQuery().use { resultSet ->
            generateSequence {
                if (resultSet.next()) {
                    Aktivitetskort(
                        dabAktivitetskortTopic = dabAktivitetskortTopic,
                        repository = this,
                        messageId = resultSet.getObject("message_id", UUID::class.java).toString(),
                        aktivitetskortId = resultSet.getObject("aktivitetskort_id", UUID::class.java).toString(),
                        fnr = resultSet.getString("fnr"),
                        tittel = resultSet.getString("tittel"),
                        beskrivelse = resultSet.getString("beskrivelse"),
                        startDato = resultSet.getTimestamp("start_dato")?.toLocalDateTime()?.toLocalDate(),
                        sluttDato = resultSet.getTimestamp("slutt_dato")?.toLocalDateTime()?.toLocalDate(),
                        actionType = resultSet.getString("action_type").let(::enumValueOf),
                        endretAv = resultSet.getString("endret_av"),
                        endretAvType = resultSet.getString("endret_av_type").let(::enumValueOf),
                        endretTidspunkt = resultSet.getTimestamp("endret_tidspunkt").toInstant().atOslo(),
                        aktivitetsStatus = resultSet.getString("aktivitets_status").let(::enumValueOf),
                        detaljer = AktivitetskortDetalj.fraAkaasJson(resultSet.getString("detaljer")),
                        handlinger = AktivitetskortHandling.fraAkaasJson(resultSet.getString("handlinger")),
                        etiketter = AktivitetskortEtikett.fraAkaasJson(resultSet.getString("etiketter")),
                        oppgave = resultSet.getString("oppgave")?.let { AktivitetskortOppgave.fraAkaasJson(it) },
                        avtaltMedNav = resultSet.getBoolean("avtalt_med_nav"),
                        sendtTidspunkt = null
                    )
                } else {
                    null
                }
            }.toList()
        }
    }

    fun markerAktivitetskorthendelseSomSendt(messageId: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE aktivitetskort 
                SET sendt_tidspunkt = CURRENT_TIMESTAMP
                WHERE message_id = ?
                """.trimIndent()
            ).apply {
                setObject(1, UUID.fromString(messageId))
            }.executeUpdate()
        }
    }

    fun markerFeilkøhendelseSomSendt(messageId: String) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                UPDATE aktivitetskort_hendelse_feil 
                SET sendt_tidspunkt = CURRENT_TIMESTAMP
                WHERE message_id = ?
                """.trimIndent()
            ).apply {
                setObject(1, UUID.fromString(messageId))
            }.executeUpdate()
        }
    }

    fun hentUsendteFeilkøHendelser(): List<Aktivitetskort.AktivitetskortFeil> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
            SELECT af.*, a.*, rt.rekrutteringstreff_id
            FROM aktivitetskort_hendelse_feil af
            JOIN aktivitetskort a ON af.message_id = a.message_id
            JOIN rekrutteringstreff rt ON a.aktivitetskort_id = rt.aktivitetskort_id
            WHERE af.sendt_tidspunkt IS NULL
            """.trimIndent()
            ).executeQuery().use { resultSet ->
                generateSequence {
                    if (resultSet.next()) {
                        Aktivitetskort.AktivitetskortFeil(
                            Aktivitetskort(
                                dabAktivitetskortTopic = dabAktivitetskortTopic,
                                repository = this,
                                messageId = resultSet.getObject("message_id", UUID::class.java).toString(),
                                aktivitetskortId = resultSet.getObject("aktivitetskort_id", UUID::class.java)
                                    .toString(),
                                fnr = resultSet.getString("fnr"),
                                tittel = resultSet.getString("tittel"),
                                beskrivelse = resultSet.getString("beskrivelse"),
                                startDato = resultSet.getTimestamp("start_dato").toLocalDateTime().toLocalDate(),
                                sluttDato = resultSet.getTimestamp("slutt_dato").toLocalDateTime().toLocalDate(),
                                actionType = resultSet.getString("action_type").let(::enumValueOf),
                                endretAv = resultSet.getString("endret_av"),
                                endretAvType = resultSet.getString("endret_av_type").let(::enumValueOf),
                                endretTidspunkt = resultSet.getTimestamp("endret_tidspunkt").toInstant().atOslo(),
                                aktivitetsStatus = resultSet.getString("aktivitets_status").let(::enumValueOf),
                                detaljer = AktivitetskortDetalj.fraAkaasJson(resultSet.getString("detaljer")),
                                handlinger = AktivitetskortHandling.fraAkaasJson(resultSet.getString("handlinger")),
                                etiketter = AktivitetskortEtikett.fraAkaasJson(resultSet.getString("etiketter")),
                                oppgave = resultSet.getString("oppgave")
                                    ?.let { AktivitetskortOppgave.fraAkaasJson(it) },
                                avtaltMedNav = resultSet.getBoolean("avtalt_med_nav"),
                                sendtTidspunkt = null,
                            ),
                            rekrutteringstreffId = resultSet.getObject("rekrutteringstreff_id", UUID::class.java)
                                .toString(),
                            errorMessage = resultSet.getString("error_message"),
                            errorType = resultSet.getString("error_type").let(::enumValueOf),
                        )
                    } else {
                        null
                    }
                }.toList()
            }
        }

    fun lagreFeilkøHendelse(messageId: UUID, failingMessage: String, errorMessage: String, errorType: ErrorType) =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                    INSERT INTO aktivitetskort_hendelse_feil (
                        message_id, failing_Message, error_message, error_type, timestamp
                    ) VALUES (?, ?, ?, ?, ?)
                    """.trimIndent()
            ).apply {
                setObject(1, messageId)
                setString(2, failingMessage)
                setString(3, errorMessage)
                setString(4, errorType.name)
                setTimestamp(5, Timestamp.valueOf(ZonedDateTime.now().toLocalDateTime()))
            }.executeUpdate()
        }

    fun hentAktivitetskortId(fnr: String, rekrutteringstreffId: UUID) = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT aktivitetskort_id FROM rekrutteringstreff
                WHERE fnr = ? AND rekrutteringstreff_id = ?
            """.trimIndent()
        ).apply {
            setString(1, fnr)
            setObject(2, rekrutteringstreffId)
        }.executeQuery().use { resultSet ->
            if (!resultSet.next()) {
                return@use null
            }
            resultSet.getString("aktivitetskort_id")?.let { UUID.fromString(it) }
        }
    }

    fun oppdaterAktivitetsstatus(
        aktivitetskortId: UUID,
        aktivitetsStatus: AktivitetsStatus,
        endretAv: String,
        endretAvType: EndretAvType
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO aktivitetskort
                (message_id, aktivitetskort_id, fnr, tittel, aktivitets_status, beskrivelse, start_dato, 
                slutt_dato, detaljer, handlinger, etiketter, oppgave, action_type, avtalt_med_nav, endret_av, 
                endret_av_type, endret_tidspunkt)
                SELECT
                    ?,
                    aktivitetskort_id,
                    fnr,
                    tittel,
                    ?,
                    beskrivelse,
                    start_dato,
                    slutt_dato,
                    detaljer,
                    handlinger,
                    etiketter,
                    oppgave,
                    action_type,
                    avtalt_med_nav,
                    ?,
                    ?,
                    ?
                FROM aktivitetskort
                WHERE aktivitetskort_id = ?
                ORDER BY endret_tidspunkt DESC
                LIMIT 1
                """.trimIndent()
            ).apply {
                setObject(1, UUID.randomUUID())
                setString(2, aktivitetsStatus.name)
                setString(3, endretAv)
                setString(4, endretAvType.name)
                setTimestamp(5, Timestamp.valueOf(ZonedDateTime.now().toLocalDateTime()))
                setObject(6, aktivitetskortId)
            }.executeUpdate()
        }.let { rowsUpdated ->
            if (rowsUpdated != 1) {
                secureLog.error("$rowsUpdated rader oppdatert i aktivitetskort for aktivitetskortId: $aktivitetskortId, aktivitetsstatus: $aktivitetsStatus, forventet 1 rad oppdatert")
            } else {
                secureLog.info("Oppdaterte aktivitetsstatus for aktivitetskortId: $aktivitetskortId til $aktivitetsStatus")
            }
        }
    }

    fun oppdaterRekrutteringstreffAktivitetskort(
        fnr: String,
        rekrutteringstreffId: UUID,
        tittel: String,
        startDato: LocalDate,
        sluttDato: LocalDate,
        tid: String,
        gateAdresse: String,
        postnummer: String,
        poststed: String
    ) {
        val aktivitetskortId = hentAktivitetskortId(fnr, rekrutteringstreffId)
            ?: if (rekrutteringstreffId.toString() == "a118f78b-7615-449f-8271-5c38a8b15ee2") {
                log.info("Skip melding for rekrutteringstreff $rekrutteringstreffId")
                return
            } else {
                throw IllegalStateException("Fant ikke aktivitetskort for rekrutteringstreff $rekrutteringstreffId og fnr")
            }

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO aktivitetskort
                (message_id, aktivitetskort_id, fnr, tittel, aktivitets_status, beskrivelse, start_dato, 
                slutt_dato, detaljer, handlinger, etiketter, oppgave, action_type, avtalt_med_nav, endret_av, 
                endret_av_type, endret_tidspunkt)
                SELECT
                    ?,
                    aktivitetskort_id,
                    fnr,
                    ?,
                    aktivitets_status,
                    beskrivelse,
                    ?,
                    ?,
                    ?::json,
                    handlinger,
                    etiketter,
                    oppgave,
                    action_type,
                    avtalt_med_nav,
                    ?,
                    ?,
                    ?
                FROM aktivitetskort
                WHERE aktivitetskort_id = ?
                ORDER BY endret_tidspunkt DESC
                LIMIT 1
                """.trimIndent()
            ).apply {
                setObject(1, UUID.randomUUID())
                setString(2, tittel)
                setObject(3, startDato)
                setObject(4, sluttDato)
                setString(
                    5, listOf(
                        AktivitetskortDetalj("Tid", tid),
                        AktivitetskortDetalj("Sted", "$gateAdresse, $postnummer $poststed"),
                    ).joinToJson(AktivitetskortDetalj::tilAkaasJson)
                )
                setString(6, EndretAvType.SYSTEM.name)
                setString(7, EndretAvType.SYSTEM.name)
                setTimestamp(8, Timestamp.valueOf(ZonedDateTime.now().toLocalDateTime()))
                setObject(9, aktivitetskortId)
            }.executeUpdate()
        }.let { rowsUpdated ->
            if (rowsUpdated != 1) {
                secureLog.error("$rowsUpdated rader oppdatert i aktivitetskort for rekrutteringstreff $rekrutteringstreffId, forventet 1 rad oppdatert")
            } else {
                secureLog.info("Oppdaterte aktivitetskort for rekrutteringstreff $rekrutteringstreffId")
            }
        }
    }

    init {
        Flyway.configure()
            .loggers("slf4j")
            .dataSource(databaseConfig.lagDatasource())
            .load()
            .migrate()
    }
}