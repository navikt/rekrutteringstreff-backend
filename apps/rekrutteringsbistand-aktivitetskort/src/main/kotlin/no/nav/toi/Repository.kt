package no.nav.toi

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
import java.sql.Types
import java.sql.Types.VARCHAR
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

class Repository(databaseConfig: DatabaseConfig) {
    private val dataSource = databaseConfig.lagDatasource()
    fun opprettRekrutteringstreffInvitasjon(
        fnr: String,
        rekrutteringstreffId: UUID,
        tittel: String,
        beskrivelse: String,
        startDato: LocalDate,
        sluttDato: LocalDate,
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
                    INSERT INTO aktivitetskort (
                        fnr, tittel, beskrivelse, start_dato, slutt_dato, 
                        aktivitetskort_id, rekrutteringstreff_id, 
                        opprettet_av, opprettet_av_type, opprettet_tidspunkt
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (fnr, rekrutteringstreff_id) DO NOTHING
                    """.trimIndent()
                ).apply {
                    setString(1, fnr)
                    setString(2, tittel)
                    setString(3, beskrivelse)
                    setObject(4, startDato)
                    setObject(5, sluttDato)
                    setObject(6, aktivitietskortId)
                    setObject(7, rekrutteringstreffId)
                    setString(8, endretAv)
                    setString(9, EndretAvType.NAVIDENT.name)
                    setTimestamp(10, Timestamp.valueOf(endretTidspunkt.toLocalDateTime()))
                }.executeUpdate()

                if(endredeLinjer==0) {
                    log.error("Prøvde å opprette aktivitetskort for person på treff som allerede har aktivitetskort: $rekrutteringstreffId")
                    return null
                } else {
                    val messageId = UUID.randomUUID()
                    connection.prepareStatement(
                        """
                    INSERT INTO aktivitetskort_hendelse (
                        aktivitetskort_id, message_id, action_type,
                        endret_av, endret_av_type, endret_tidspunkt, 
                        aktivitets_status
                    ) VALUES (?, ?, 'UPSERT_AKTIVITETSKORT_V1', ?, ?, ?, 'FORSLAG')
                    """.trimIndent()
                    ).apply {
                        setObject(1, aktivitietskortId)
                        setObject(2, messageId)
                        setString(3, endretAv)
                        setString(4, EndretAvType.NAVIDENT.name)
                        setTimestamp(5, Timestamp.valueOf(endretTidspunkt.toLocalDateTime()))
                    }.executeUpdate()

                    connection.prepareStatement(
                        """
                    INSERT INTO aktivitetskort_dynamisk (
                        message_id, detaljer, handlinger, etiketter, oppgave
                    ) VALUES (?, ?, ?, ?, ?)
                    """.trimIndent()
                    ).apply {
                        setObject(1, messageId)
                        setString(
                            2, listOf(
                                AktivitetskortDetalj("Sted", "$gateAdresse, $postnummer $poststed"),
                            ).joinToJson(AktivitetskortDetalj::tilAkaasJson)
                        )
                        setString(
                            3,
                            listOf(
                                AktivitetskortHandling(
                                    "Sjekk ut treffet",
                                    "Sjekk ut treffet og svar",
                                    "https://rekrutteringstreff.dev.nav.no/test",
                                    LenkeType.FELLES
                                )
                            ).joinToJson(AktivitetskortHandling::tilAkaasJson)
                        )
                        setString(4, "[]")
                        setNull(5, VARCHAR)
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
            SELECT ah.*, a.*, ad.*
            FROM aktivitetskort_hendelse ah
            JOIN aktivitetskort a ON ah.aktivitetskort_id = a.aktivitetskort_id
            JOIN aktivitetskort_dynamisk ad ON ah.message_id = ad.message_id
            WHERE sendt_tidspunkt IS NULL
            """.trimIndent()
        ).executeQuery().use { resultSet ->
            generateSequence {
                if (resultSet.next()) {
                    Aktivitetskort.AktivitetskortHendelse(
                        repository = this,
                        messageId = resultSet.getObject("message_id", UUID::class.java).toString(),
                        aktivitetskort = Aktivitetskort(
                            aktivitetskortId = resultSet.getObject("aktivitetskort_id", UUID::class.java).toString(),
                            rekrutteringstreffId = resultSet.getObject("rekrutteringstreff_id", UUID::class.java)
                                .toString(),
                            fnr = resultSet.getString("fnr"),
                            tittel = resultSet.getString("tittel"),
                            beskrivelse = resultSet.getString("beskrivelse"),
                            startDato = resultSet.getTimestamp("start_dato").toLocalDateTime().toLocalDate(),
                            sluttDato = resultSet.getTimestamp("slutt_dato").toLocalDateTime().toLocalDate(),
                            opprettetAv = resultSet.getString("opprettet_av"),
                            opprettetAvType = resultSet.getString("opprettet_av_type"),
                            opprettetTidspunkt = resultSet.getTimestamp("opprettet_tidspunkt").toInstant().atOslo(),
                        ),
                        actionType = resultSet.getString("action_type").let(::enumValueOf),
                        endretAv = resultSet.getString("endret_av"),
                        endretAvType = resultSet.getString("endret_av_type").let(::enumValueOf),
                        endretTidspunkt = resultSet.getTimestamp("endret_tidspunkt").toInstant().atOslo(),
                        aktivitetsStatus = resultSet.getString("aktivitets_status").let(::enumValueOf),
                        detaljer = AktivitetskortDetalj.fraAkaasJson(resultSet.getString("detaljer")),
                        handlinger = AktivitetskortHandling.fraAkaasJson(resultSet.getString("handlinger")),
                        etiketter = AktivitetskortEtikett.fraAkaasJson(resultSet.getString("etiketter")),
                        oppgave = resultSet.getString("oppgave")?.let { AktivitetskortOppgave.fraAkaasJson(it) },
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
                UPDATE aktivitetskort_hendelse 
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

    fun hentUsendteFeilkøHendelser(): List<Aktivitetskort.AktivitetskortHendelse.AktivitetskortHendelseFeil> = dataSource.connection.use { connection ->
        connection.prepareStatement(
            """
            SELECT af.*, ah.*, a.*, ad.*
            FROM aktivitetskort_hendelse_feil af
            JOIN aktivitetskort_hendelse ah ON af.message_id = ah.message_id
            JOIN aktivitetskort a ON ah.aktivitetskort_id = a.aktivitetskort_id
            JOIN aktivitetskort_dynamisk ad ON ah.message_id = ad.message_id
            WHERE af.sendt_tidspunkt IS NULL
            """.trimIndent()
        ).executeQuery().use { resultSet ->
            generateSequence {
                if (resultSet.next()) {
                    Aktivitetskort.AktivitetskortHendelse.AktivitetskortHendelseFeil(
                        Aktivitetskort.AktivitetskortHendelse(
                            repository = this,
                            messageId = resultSet.getObject("message_id", UUID::class.java).toString(),
                            aktivitetskort = Aktivitetskort(
                                aktivitetskortId = resultSet.getObject("aktivitetskort_id", UUID::class.java).toString(),
                                rekrutteringstreffId = resultSet.getObject("rekrutteringstreff_id", UUID::class.java)
                                    .toString(),
                                fnr = resultSet.getString("fnr"),
                                tittel = resultSet.getString("tittel"),
                                beskrivelse = resultSet.getString("beskrivelse"),
                                startDato = resultSet.getTimestamp("start_dato").toLocalDateTime().toLocalDate(),
                                sluttDato = resultSet.getTimestamp("slutt_dato").toLocalDateTime().toLocalDate(),
                                opprettetAv = resultSet.getString("opprettet_av"),
                                opprettetAvType = resultSet.getString("opprettet_av_type"),
                                opprettetTidspunkt = resultSet.getTimestamp("opprettet_tidspunkt").toInstant().atOslo(),
                            ),
                            actionType = resultSet.getString("action_type").let(::enumValueOf),
                            endretAv = resultSet.getString("endret_av"),
                            endretAvType = resultSet.getString("endret_av_type").let(::enumValueOf),
                            endretTidspunkt = resultSet.getTimestamp("endret_tidspunkt").toInstant().atOslo(),
                            aktivitetsStatus = resultSet.getString("aktivitets_status").let(::enumValueOf),
                            detaljer = AktivitetskortDetalj.fraAkaasJson(resultSet.getString("detaljer")),
                            handlinger = AktivitetskortHandling.fraAkaasJson(resultSet.getString("handlinger")),
                            etiketter = AktivitetskortEtikett.fraAkaasJson(resultSet.getString("etiketter")),
                            oppgave = resultSet.getString("oppgave")?.let { AktivitetskortOppgave.fraAkaasJson(it) },
                            sendtTidspunkt = null
                        ),
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