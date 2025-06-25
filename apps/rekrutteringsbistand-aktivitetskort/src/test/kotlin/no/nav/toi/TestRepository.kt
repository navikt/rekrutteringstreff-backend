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
            connection.prepareStatement(
                "DELETE FROM aktivitetskort"
            ).execute()
        }
    }

    @OptIn(InternalAPI::class)
    fun hentAlle() = databaseConfig.lagDatasource().connection.use { connection ->
        connection.prepareStatement(
            """
                    SELECT * FROM aktivitetskort 
                    JOIN aktivitetskort_hendelse ON aktivitetskort.aktivitetskort_id = aktivitetskort_hendelse.aktivitetskort_id
                    LEFT JOIN aktivitetskort_hendelse_feil ON aktivitetskort_hendelse.message_id = aktivitetskort_hendelse_feil.message_id
                """.trimIndent()
        ).executeQuery().use { resultSet ->
            generateSequence {
                if (resultSet.next()) {
                    RekrutteringstreffInvitasjon(
                        id = resultSet.getString("db_id"),
                        tittel = resultSet.getString("tittel"),
                        beskrivelse = resultSet.getString("beskrivelse"),
                        fraTid = resultSet.getObject("start_dato", LocalDate::class.java),
                        tilTid = resultSet.getObject("slutt_dato", LocalDate::class.java),
                        aktivitetskortId = UUID.fromString(resultSet.getString("aktivitetskort_id")),
                        rekrutteringstreffId = UUID.fromString(resultSet.getString("rekrutteringstreff_id")),
                        aktivitetsStatus = resultSet.getString("aktivitets_status"),
                        opprettetAv = resultSet.getString("endret_av"),
                        opprettetAvType = resultSet.getString("endret_av_type"),
                        opprettetTidspunkt = resultSet.getTimestamp("endret_tidspunkt").toZonedDateTime(),
                        messageId = UUID.fromString(resultSet.getString("message_id")),
                        feil = resultSet.getString("failing_message")?.let { failingMessage ->
                            RekrutteringstreffFeil(
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
    val beskrivelse: String?,
    val fraTid: LocalDate,
    val tilTid: LocalDate,
    val aktivitetskortId: UUID,
    val rekrutteringstreffId: UUID,
    val aktivitetsStatus: String,
    val opprettetAv: String,
    val opprettetAvType: String,
    val opprettetTidspunkt: ZonedDateTime,
    val messageId: UUID,
    val feil: RekrutteringstreffFeil?
)
class RekrutteringstreffFeil(
    val timestamp: ZonedDateTime,
    val failingMessage: String,
    val errorMessage: String,
    val errorType: ErrorType
)