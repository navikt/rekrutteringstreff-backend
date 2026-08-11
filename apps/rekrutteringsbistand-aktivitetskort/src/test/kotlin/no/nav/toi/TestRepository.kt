package no.nav.toi

import io.ktor.server.util.toZonedDateTime
import io.ktor.utils.io.InternalAPI
import no.nav.toi.aktivitetskort.ErrorType
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID
import kotlin.use

class TestRepository(private val databaseConfig: DatabaseConfig) {

    fun slettAlt() {
        databaseConfig.lagDatasource().connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("DELETE FROM delt_stilling")
                statement.execute("DELETE FROM rekrutteringstreff")
                statement.execute("DELETE FROM aktivitetskort")
            }
        }
    }

    @OptIn(InternalAPI::class)
    fun hentAlleRekrutteringstreffInvitasjoner() = databaseConfig.lagDatasource().connection.use { connection ->
        connection.prepareStatement(
            """
                    SELECT * FROM aktivitetskort
                    INNER JOIN rekrutteringstreff ON aktivitetskort.aktivitetskort_id = rekrutteringstreff.aktivitetskort_id
                    LEFT JOIN aktivitetskort_hendelse_feil ON aktivitetskort.message_id = aktivitetskort_hendelse_feil.message_id
                    ORDER BY aktivitetskort.endret_tidspunkt ASC
                """.trimIndent()
        ).executeQuery().use { resultSet ->
            generateSequence {
                if (resultSet.next()) {
                    RekrutteringstreffInvitasjon(
                        id = resultSet.getString("db_id"),
                        fnr = resultSet.getString("fnr"),
                        tittel = resultSet.getString("tittel"),
                        beskrivelse = resultSet.getString("beskrivelse"),
                        fraTid = resultSet.getObject("start_dato", LocalDate::class.java),
                        tilTid = resultSet.getObject("slutt_dato", LocalDate::class.java),
                        detaljer = resultSet.getString("detaljer"),
                        aktivitetskortId = UUID.fromString(resultSet.getString("aktivitetskort_id")),
                        rekrutteringstreffId = UUID.fromString(resultSet.getString("rekrutteringstreff_id")),
                        endretAv = resultSet.getString("endret_av"),
                        aktivitetsStatus = resultSet.getString("aktivitets_status"),
                        opprettetAv = resultSet.getString("endret_av"),
                        opprettetAvType = resultSet.getString("endret_av_type"),
                        opprettetTidspunkt = resultSet.getTimestamp("endret_tidspunkt").toZonedDateTime(),
                        messageId = UUID.fromString(resultSet.getString("message_id")),
                        feil = resultSet.getString("failing_message")?.let { failingMessage ->
                            AktivitetskortFeil(
                                timestamp = resultSet.getTimestamp("timestamp").toZonedDateTime(),
                                failingMessage = failingMessage,
                                errorMessage = resultSet.getString("error_message"),
                                errorType = ErrorType.valueOf(resultSet.getString("error_type"))
                            )
                        }
                    )
                } else {
                    null
                }
            }.toList()
        }
    }

    @OptIn(InternalAPI::class)
    fun hentAlleRekrutteringsbistandStillinger() = databaseConfig.lagDatasource().connection.use { connection ->
        connection.prepareStatement(
            """
                        SELECT * FROM aktivitetskort
                        INNER JOIN delt_stilling ON aktivitetskort.aktivitetskort_id = delt_stilling.aktivitetskort_id
                        LEFT JOIN aktivitetskort_hendelse_feil ON aktivitetskort.message_id = aktivitetskort_hendelse_feil.message_id
                        ORDER BY aktivitetskort.endret_tidspunkt ASC
                    """.trimIndent()
        ).executeQuery().use { resultSet ->
            generateSequence {
                if (resultSet.next()) {
                    RekrutteringsbistandStilling(
                        id = resultSet.getString("db_id"),
                        fnr = resultSet.getString("fnr"),
                        tittel = resultSet.getString("tittel"),
                        beskrivelse = resultSet.getString("beskrivelse"),
                        detaljer = resultSet.getString("detaljer"),
                        aktivitetskortId = UUID.fromString(resultSet.getString("aktivitetskort_id")),
                        stillingId = UUID.fromString(resultSet.getString("stilling_id")),
                        endretAv = resultSet.getString("endret_av"),
                        aktivitetsStatus = resultSet.getString("aktivitets_status"),
                        opprettetAv = resultSet.getString("endret_av"),
                        opprettetAvType = resultSet.getString("endret_av_type"),
                        opprettetTidspunkt = resultSet.getTimestamp("endret_tidspunkt").toZonedDateTime(),
                        messageId = UUID.fromString(resultSet.getString("message_id")),
                        feil = resultSet.getString("failing_message")?.let { failingMessage ->
                            AktivitetskortFeil(
                                timestamp = resultSet.getTimestamp("timestamp").toZonedDateTime(),
                                failingMessage = failingMessage,
                                errorMessage = resultSet.getString("error_message"),
                                errorType = ErrorType.valueOf(resultSet.getString("error_type"))
                            )
                        }
                    )
                } else {
                    null
                }
            }.toList()
        }
    }
}

class RekrutteringstreffInvitasjon(
    val id: String,
    val tittel: String,
    val fnr: String,
    val beskrivelse: String?,
    val fraTid: LocalDate,
    val tilTid: LocalDate,
    val detaljer: String,
    val aktivitetskortId: UUID,
    val rekrutteringstreffId: UUID,
    val endretAv: String,
    val aktivitetsStatus: String,
    val opprettetAv: String,
    val opprettetAvType: String,
    val opprettetTidspunkt: ZonedDateTime,
    val messageId: UUID,
    val feil: AktivitetskortFeil?
)
class AktivitetskortFeil(
    val timestamp: ZonedDateTime,
    val failingMessage: String,
    val errorMessage: String,
    val errorType: ErrorType
)


class RekrutteringsbistandStilling(
    val id: String,
    val tittel: String,
    val fnr: String,
    val beskrivelse: String?,
    val detaljer: String,
    val aktivitetskortId: UUID,
    val stillingId: UUID,
    val endretAv: String,
    val aktivitetsStatus: String,
    val opprettetAv: String,
    val opprettetAvType: String,
    val opprettetTidspunkt: ZonedDateTime,
    val messageId: UUID,
    val feil: AktivitetskortFeil?
)