package no.nav.toi

import no.nav.toi.aktivitetskort.ActionType
import no.nav.toi.aktivitetskort.AktivitetsStatus
import no.nav.toi.aktivitetskort.AktivitetskortDetalj
import no.nav.toi.aktivitetskort.Aktivitetskort
import no.nav.toi.aktivitetskort.AktivitetskortEtikett
import no.nav.toi.aktivitetskort.AktivitetskortHandling
import no.nav.toi.aktivitetskort.AktivitetskortOppgave
import no.nav.toi.aktivitetskort.EndretAvType
import no.nav.toi.aktivitetskort.ErrorType
import no.nav.toi.aktivitetskort.LenkeType
import no.nav.toi.aktivitetskort.atOslo
import no.nav.toi.aktivitetskort.joinToJson
import org.flywaydb.core.Flyway
import java.sql.Timestamp
import java.sql.Types.VARCHAR
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

class Repository(databaseConfig: DatabaseConfig, private val minsideUrl: String) {
    private val dataSource = databaseConfig.lagDatasource()
    fun opprettRekrutteringstreffInvitasjon(
        fnr: String,
        rekrutteringstreffId: UUID,
        tittel: String,
        beskrivelse: String,
        startDato: LocalDate,
        sluttDato: LocalDate,
        tid: String,
        endretAv: String,
        endretTidspunkt: ZonedDateTime,
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

                if(endredeLinjer==0) {
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
                        setTimestamp(9, Timestamp.valueOf(endretTidspunkt.toLocalDateTime()))
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
                                    "$minsideUrl/rekrutteringstreff/$rekrutteringstreffId",
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

    fun hentUsendteFeilkøHendelser(): List<Aktivitetskort.AktivitetskortFeil> = dataSource.connection.use { connection ->
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
                            repository = this,
                            messageId = resultSet.getObject("message_id", UUID::class.java).toString(),
                            aktivitetskortId = resultSet.getObject("aktivitetskort_id", UUID::class.java).toString(),
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
                            oppgave = resultSet.getString("oppgave")?.let { AktivitetskortOppgave.fraAkaasJson(it) },
                            avtaltMedNav = resultSet.getBoolean("avtalt_med_nav"),
                            sendtTidspunkt = null,
                        ),
                        rekrutteringstreffId = resultSet.getObject("rekrutteringstreff_id", UUID::class.java).toString(),
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

    init {
        Flyway.configure()
            .loggers("slf4j")
            .dataSource(databaseConfig.lagDatasource())
            .load()
            .migrate()
    }
}