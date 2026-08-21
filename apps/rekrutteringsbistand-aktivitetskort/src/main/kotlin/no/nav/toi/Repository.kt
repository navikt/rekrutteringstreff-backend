package no.nav.toi

import no.nav.toi.aktivitetskort.*
import org.flywaydb.core.Flyway
import java.sql.Statement
import java.sql.Timestamp
import java.sql.Types.VARCHAR
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.*

class Repository(databaseConfig: DatabaseConfig, private val minsideUrl: String, private val dabAktivitetskortTopic: String) {
    private val dataSource = databaseConfig.lagDatasource()
    private val secureLog = SecureLog(log)

    init {
        Flyway.configure()
            .loggers("slf4j")
            .dataSource(dataSource)
            .load()
            .migrate()
    }

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
                    connection.rollback()
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
                        detaljer, handlinger, etiketter, oppgave, action_type, avtalt_med_nav, aktivitetskort_type
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, '${AktivitetsStatus.FORSLAG.name}', ?, '${EndretAvType.NAVIDENT.name}', ?, ?::json, ?::json, ?::json, ?::json, '${ActionType.UPSERT_AKTIVITETSKORT_V1.name}', false, '${AktivitetskortType.REKRUTTERINGSTREFF.name}')
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
                            10, objectMapper.writeValueAsString(
                                listOf(
                                    AktivitetskortDetalj("Tid", tid),
                                    AktivitetskortDetalj("Sted", "$gateAdresse, $postnummer $poststed"),
                                )
                            )
                        )
                        setString(
                            11,
                            objectMapper.writeValueAsString(
                                listOf(
                                    AktivitetskortHandling(
                                        "Sjekk ut treffet",
                                        "Sjekk ut treffet og svar",
                                        "$minsideUrl/$rekrutteringstreffId",
                                        LenkeType.FELLES
                                    )
                                )
                            )
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
                        sendtTidspunkt = null,
                        aktivitetskortType = resultSet.getString("aktivitetskort_type").let(::enumValueOf),
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

    fun hentUsendteFeilkøHendelser(): List<AktivitetskortFeil> =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
            SELECT
                af.message_id,
                af.error_message,
                af.error_type,
                a.aktivitetskort_id,
                a.fnr,
                a.endret_av,
                a.aktivitetskort_type,
                rt.rekrutteringstreff_id,
                ds.stilling_id
            FROM aktivitetskort_hendelse_feil af
            JOIN aktivitetskort a ON af.message_id = a.message_id
            LEFT JOIN rekrutteringstreff rt ON a.aktivitetskort_id = rt.aktivitetskort_id
            LEFT JOIN delt_stilling ds ON a.aktivitetskort_id = ds.aktivitetskort_id
            WHERE af.sendt_tidspunkt IS NULL
            """.trimIndent()
            ).executeQuery().use { resultSet ->
                generateSequence {
                    if (resultSet.next()) {
                        val messageId = resultSet.getObject("message_id", UUID::class.java).toString()
                        val aktivitetskortId = resultSet.getObject("aktivitetskort_id", UUID::class.java).toString()
                        val fnr = resultSet.getString("fnr")
                        val endretAv = resultSet.getString("endret_av")
                        val errorMessage = resultSet.getString("error_message")
                        val errorType = resultSet.getString("error_type")
                        val aktivitetskortType = resultSet.getString("aktivitetskort_type")

                        val fellesMeldingsfelter = FellesMeldingsfelter(
                            messageId = messageId,
                            fnr = fnr,
                            aktivitetskortId = aktivitetskortId,
                            endretAv = endretAv,
                            errorMessage = errorMessage,
                            errorType = errorType,
                            aktivitetskortType = aktivitetskortType,
                            timestamp = ZonedDateTime.now().toString()
                        )
                        when (aktivitetskortType.let<_, AktivitetskortType>(::enumValueOf)) {
                            AktivitetskortType.REKRUTTERINGSTREFF -> RekrutteringstreffFeilMelding(
                                fellesMeldingsfelter = fellesMeldingsfelter,
                                rekrutteringstreffId = resultSet
                                    .getObject("rekrutteringstreff_id", UUID::class.java)
                                    ?.toString()
                                    ?: error("Mangler rekrutteringstreffId for aktivitetskort $aktivitetskortId"),
                            )

                            AktivitetskortType.DELTSTILLING -> DeltStillingFeilMelding(
                                fellesMeldingsfelter = fellesMeldingsfelter,
                                stillingId = resultSet.getObject("stilling_id", UUID::class.java)
                                    ?.toString()
                                    ?: error("Mangler stillingId for aktivitetskort $aktivitetskortId"),
                            )
                        }
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

    fun hentAktivitetskortIdForDeltStilling(fnr: String, stillingId: UUID) = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT aktivitetskort_id FROM delt_stilling
                WHERE fnr = ? AND stilling_id = ?
            """.trimIndent()
        ).apply {
            setString(1, fnr)
            setObject(2, stillingId)
        }.executeQuery().use { resultSet ->
            if (!resultSet.next()) {
                return@use null
            }
            resultSet.getString("aktivitetskort_id")?.let(UUID::fromString)
        }
    }

    fun hentSisteAktivitetsstatus(aktivitetskortId: UUID): AktivitetsStatus? = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
                SELECT aktivitets_status
                FROM aktivitetskort
                WHERE aktivitetskort_id = ?
                ORDER BY endret_tidspunkt DESC, db_id DESC
                LIMIT 1
            """.trimIndent()
        ).apply {
            setObject(1, aktivitetskortId)
        }.executeQuery().use { resultSet ->
            if (!resultSet.next()) {
                return@use null
            }
            resultSet.getString("aktivitets_status").let(::enumValueOf)
        }
    }

    fun veilederLukkerKandidatliste(
        stillingId: UUID,
        fnr: List<String>,
        endretAv: String,
    ) {
        val unikeFnr = fnr.distinct()
        if (unikeFnr.isEmpty()) return

        dataSource.connection.use { connection ->
            try {
                connection.autoCommit = false

                val aktivitetskortIder = connection.prepareStatement(
                    """
                    SELECT DISTINCT delt_stilling.aktivitetskort_id
                    FROM delt_stilling
                    JOIN LATERAL (
                        SELECT aktivitets_status
                        FROM aktivitetskort
                        WHERE aktivitetskort_id = delt_stilling.aktivitetskort_id
                        ORDER BY endret_tidspunkt DESC, db_id DESC
                        LIMIT 1
                    ) siste_aktivitetskort ON true
                    WHERE delt_stilling.stilling_id = ?
                      AND delt_stilling.fnr = ANY (?)
                      AND siste_aktivitetskort.aktivitets_status = ?
                    """.trimIndent()
                ).use { statement ->
                    statement.setObject(1, stillingId)
                    connection.createArrayOf("text", unikeFnr.toTypedArray()).let { fnrArray ->
                        try {
                            statement.setArray(2, fnrArray)
                            statement.setString(3, AktivitetsStatus.GJENNOMFORES.name)
                            statement.executeQuery().use { resultSet ->
                                generateSequence {
                                    if (resultSet.next()) resultSet.getObject("aktivitetskort_id", UUID::class.java) else null
                                }.toList()
                            }
                        } finally {
                            fnrArray.free()
                        }
                    }
                }

                if (aktivitetskortIder.isNotEmpty()) {
                    val endretTidspunkt = Timestamp.valueOf(LocalDateTime.now())
                    connection.prepareStatement(
                        """
                        INSERT INTO aktivitetskort
                        (message_id, aktivitetskort_id, fnr, tittel, aktivitets_status, beskrivelse, start_dato,
                        slutt_dato, detaljer, handlinger, etiketter, oppgave, action_type, avtalt_med_nav, endret_av,
                        endret_av_type, endret_tidspunkt, aktivitetskort_type)
                        SELECT
                            ?,
                            siste_aktivitetskort.aktivitetskort_id,
                            siste_aktivitetskort.fnr,
                            siste_aktivitetskort.tittel,
                            ?,
                            siste_aktivitetskort.beskrivelse,
                            siste_aktivitetskort.start_dato,
                            siste_aktivitetskort.slutt_dato,
                            siste_aktivitetskort.detaljer,
                            siste_aktivitetskort.handlinger,
                            siste_aktivitetskort.etiketter,
                            siste_aktivitetskort.oppgave,
                            siste_aktivitetskort.action_type,
                            siste_aktivitetskort.avtalt_med_nav,
                            ?,
                            ?,
                            ?,
                            siste_aktivitetskort.aktivitetskort_type
                        FROM (
                            SELECT *
                            FROM aktivitetskort
                            WHERE aktivitetskort_id = ?
                            ORDER BY endret_tidspunkt DESC, db_id DESC
                            LIMIT 1
                        ) siste_aktivitetskort
                        WHERE siste_aktivitetskort.aktivitets_status = ?
                        """.trimIndent()
                    ).use { statement ->
                        aktivitetskortIder.forEach { aktivitetskortId ->
                            statement.setObject(1, UUID.randomUUID())
                            statement.setString(2, AktivitetsStatus.FULLFORT.name)
                            statement.setString(3, endretAv)
                            statement.setString(4, EndretAvType.NAVIDENT.name)
                            statement.setTimestamp(5, endretTidspunkt)
                            statement.setObject(6, aktivitetskortId)
                            statement.setString(7, AktivitetsStatus.GJENNOMFORES.name)
                            statement.addBatch()
                        }

                        statement.executeBatch().forEach { rowsUpdated ->
                            check(rowsUpdated == 0 || rowsUpdated == 1 || rowsUpdated == Statement.SUCCESS_NO_INFO) {
                                "$rowsUpdated rader oppdatert ved lukking av kandidatliste, forventet maksimalt 1 rad"
                            }
                        }
                    }
                }

                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    fun oppdaterAktivitetsstatus(
        aktivitetskortId: UUID,
        aktivitetsStatus: AktivitetsStatus,
        endretAv: String,
        endretAvType: EndretAvType,
    ) {
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO aktivitetskort
                (message_id, aktivitetskort_id, fnr, tittel, aktivitets_status, beskrivelse, start_dato, 
                slutt_dato, detaljer, handlinger, etiketter, oppgave, action_type, avtalt_med_nav, endret_av, 
                endret_av_type, endret_tidspunkt, aktivitetskort_type)
                SELECT
                    ?,
                    siste_aktivitetskort.aktivitetskort_id,
                    siste_aktivitetskort.fnr,
                    siste_aktivitetskort.tittel,
                    ?,
                    siste_aktivitetskort.beskrivelse,
                    siste_aktivitetskort.start_dato,
                    siste_aktivitetskort.slutt_dato,
                    siste_aktivitetskort.detaljer,
                    siste_aktivitetskort.handlinger,
                    siste_aktivitetskort.etiketter,
                    siste_aktivitetskort.oppgave,
                    siste_aktivitetskort.action_type,
                    siste_aktivitetskort.avtalt_med_nav,
                    ?,
                    ?,
                    ?,
                    siste_aktivitetskort.aktivitetskort_type
                FROM (
                    SELECT *
                    FROM aktivitetskort
                    WHERE aktivitetskort_id = ?
                    ORDER BY endret_tidspunkt DESC, db_id DESC
                    LIMIT 1
                ) siste_aktivitetskort
                WHERE siste_aktivitetskort.aktivitets_status IS DISTINCT FROM ?
                   OR siste_aktivitetskort.endret_av IS DISTINCT FROM ?
                   OR siste_aktivitetskort.endret_av_type IS DISTINCT FROM ?
                """.trimIndent()
            ).apply {
                setObject(1, UUID.randomUUID())
                setString(2, aktivitetsStatus.name)
                setString(3, endretAv)
                setString(4, endretAvType.name)
                setTimestamp(5, Timestamp.valueOf(ZonedDateTime.now().toLocalDateTime()))
                setObject(6, aktivitetskortId)
                setString(7, aktivitetsStatus.name)
                setString(8, endretAv)
                setString(9, endretAvType.name)
            }.executeUpdate()
        }.let { rowsUpdated ->
            if (rowsUpdated == 0) {
                secureLog.warn("Aktivitetskort $aktivitetskortId har allerede aktivitetsstatus $aktivitetsStatus med samme endretAv og endretAvType")
            } else if (rowsUpdated != 1) {
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
            ?: throw IllegalStateException("Fant ikke aktivitetskort for rekrutteringstreff $rekrutteringstreffId og fnr")

        dataSource.connection.use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO aktivitetskort
                (message_id, aktivitetskort_id, fnr, tittel, aktivitets_status, beskrivelse, start_dato, 
                slutt_dato, detaljer, handlinger, etiketter, oppgave, action_type, avtalt_med_nav, endret_av, 
                endret_av_type, endret_tidspunkt, aktivitetskort_type)
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
                    ?,
                    aktivitetskort_type
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
                    5, objectMapper.writeValueAsString(
                        listOf(
                            AktivitetskortDetalj("Tid", tid),
                            AktivitetskortDetalj("Sted", "$gateAdresse, $postnummer $poststed"),
                        )
                    )
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

    fun opprettDeltStilling(
        fnr: String,
        stillingId: String,
        tittel: String,
        opprettetAv: String,
        arbeidsgiver: String,
        arbeidssted: String
    ): UUID? {
        val aktivitetskortId = UUID.randomUUID()

        dataSource.connection.use { connection ->
            try {
                connection.autoCommit = false

                val endredeLinjer = connection.prepareStatement(
                    """
                    INSERT INTO delt_stilling (aktivitetskort_id, fnr, stilling_id)
                    VALUES (?, ?, ?)
                    ON CONFLICT (stilling_id, fnr) DO NOTHING
                    """.trimIndent()
                ).apply {
                    setObject(1, aktivitetskortId)
                    setString(2, fnr)
                    setObject(3, UUID.fromString(stillingId))
                }.executeUpdate()

                if (endredeLinjer == 0) {
                    connection.rollback()
                    secureLog.info("Aktivitetskort finnes allerede for stilling $stillingId og fnr")
                    return null
                }

                connection.prepareStatement(
                    """
                    INSERT INTO aktivitetskort (
                        fnr, tittel, beskrivelse, message_id, aktivitetskort_id, aktivitets_status,
                        endret_av, endret_av_type, endret_tidspunkt, detaljer, handlinger, etiketter,
                        oppgave, action_type, avtalt_med_nav, aktivitetskort_type
                    ) VALUES (
                        ?, ?, ?, ?, ?, '${AktivitetsStatus.FORSLAG.name}',
                        ?, '${EndretAvType.NAVIDENT.name}', ?, ?::json, ?::json, ?::json,
                        ?::json, '${ActionType.UPSERT_AKTIVITETSKORT_V1.name}', false, '${AktivitetskortType.DELTSTILLING.name}'
                    )
                    """.trimIndent()
                ).apply {
                    setString(1, fnr)
                    setString(2, tittel)
                    setString(
                        3,
                        "Nav hjelper en arbeidsgiver med å finne kandidater til en stilling, og tror den kan passe for deg."
                    )
                    setObject(4, UUID.randomUUID())
                    setObject(5, aktivitetskortId)
                    setString(6, opprettetAv)
                    setObject(7, ZonedDateTime.now().toLocalDateTime())
                    setString(
                        8,
                        objectMapper.writeValueAsString(
                            listOf(
                                AktivitetskortDetalj("Arbeidsgiver", arbeidsgiver),
                                AktivitetskortDetalj("Arbeidssted", arbeidssted),
                            )
                        )
                    )
                    setString(9, "[]")
                    setString(10, "[]")
                    setNull(11, VARCHAR)
                }.executeUpdate()

                connection.commit()
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }

        return aktivitetskortId
    }
}