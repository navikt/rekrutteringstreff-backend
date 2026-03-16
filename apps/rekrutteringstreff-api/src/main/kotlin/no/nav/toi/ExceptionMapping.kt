package no.nav.toi

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.HttpStatus
import io.opentelemetry.api.trace.Span
import no.nav.toi.exception.*
import java.sql.SQLException
import java.time.LocalDateTime

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ProblemDetails(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String,
    val instance: String,
    val timestamp: LocalDateTime,
    val traceid: String?,
    // for bakoverkompatibilitet:
    val hint: String?,
    val feil: String?,
    val feilkode: String?
) {

    companion object {

        fun fromThrowable(
            throwable: Throwable,
            status: HttpStatus,
            ctx: Context,
            timestamp : LocalDateTime = LocalDateTime.now(),
            type: String = "about:blank",
            traceid: String = traceIdFraOpenTelemetry(),
            hint: String? = null,
            feil: String? = null,
            feilkode: String? = null,
            melding: String? = null
        ): ProblemDetails {
            return ProblemDetails(
                type = type,
                title = throwable.javaClass.simpleName, // Tas fra exceptionen eller statusen??
                status = status.code,
                detail = melding ?: throwable.message ?: "Ukjent feil",
                instance = ctx.req().requestURI.toString(),
                timestamp = timestamp,
                traceid = traceid,
                hint = hint,
                feil = feil,
                feilkode = feilkode,
            )
        }

        private fun traceIdFraOpenTelemetry(): String = Span.current().spanContext.traceId
    }
}

object ExceptionMapping {
    private val secureLog = SecureLog(log)

    fun Javalin.exceptionMapping() {
        // TODO exceptions kan også skje steder hvor disse feilmeldingene ikke gir mening.
        exception(JsonParseException::class.java) { e, ctx ->
            ctx.status(400).json(
                ProblemDetails.fromThrowable(
                    throwable = e,
                    status = HttpStatus.BAD_REQUEST,
                    ctx = ctx,
                    feil = "Ugyldig JSON i request-body.",
                    hint = "Sett Content-Type: application/json, bruk gyldig JSON med \"-siterte feltnavn og ISO-8601 dato/tid med tidsone (f.eks. 2025-09-10T08:00:00+02:00)."
                )
            )
        }
        exception(JsonMappingException::class.java) { e, ctx ->
            ctx.status(400).json(
                ProblemDetails.fromThrowable(
                    throwable = e,
                    status = HttpStatus.BAD_REQUEST,
                    ctx = ctx,
                    feil = "Klarte ikke å lese request-body til forventet format.",
                    hint = "Body må være JSON som matcher skjemaet for OppdaterRekrutteringstreffDto. Dato/tid må inkludere tidsone (f.eks. +02:00)."
                )
            )
        }
        // I enkelte Javalin-versjoner pakkes Jackson-feil i JsonMapperException
        try {
            val k = Class.forName("io.javalin.json.JsonMapperException") as Class<out Exception>
            @Suppress("UNCHECKED_CAST")
            exception(k) { e, ctx ->
                ctx.status(400).json(
                    ProblemDetails.fromThrowable(
                        throwable = e,
                        status = HttpStatus.BAD_REQUEST,
                        ctx = ctx,
                        feil = "Ugyldig request-body (JSON).",
                        hint = "Sett Content-Type: application/json og bruk ISO-8601 dato/tid med tidsone på alle datoer."
                    )
                )
            }
        } catch (_: ClassNotFoundException) {
            // Ignorer – typen finnes ikke i denne Javalin-versjonen
        }

        exception(SQLException::class.java) { e, ctx ->
            if (e.sqlState == "23503") {
                ctx.status(409).json(
                    ProblemDetails.fromThrowable(
                        throwable = e,
                        status = HttpStatus.CONFLICT,
                        ctx = ctx,
                        feil = "Kan ikke slette rekrutteringstreff fordi avhengige rader finnes. Slett barn først."
                    )
                )
            } else {
                secureLog.error("SQL-feil", e)
                ctx.status(500).json(
                    ProblemDetails.fromThrowable(
                        throwable = e,
                        status = HttpStatus.INTERNAL_SERVER_ERROR,
                        ctx = ctx,
                        feil = "En databasefeil oppstod på serveren."
                    )
                )
            }
        }
        exception(UlovligSlettingException::class.java) { e, ctx ->
            ctx.status(409).json(
                ProblemDetails.fromThrowable(
                    throwable = e,
                    status = HttpStatus.CONFLICT,
                    ctx = ctx,
                    feil = e.message
                )
            )
        }

        exception(UlovligOppdateringException::class.java) { e, ctx ->
            ctx.status(409).json(
                ProblemDetails.fromThrowable(
                    throwable = e,
                    status = HttpStatus.CONFLICT,
                    ctx = ctx,
                    feil = e.message
                )
            )
        }

        exception(IllegalArgumentException::class.java) { e, ctx ->
            ctx.status(400).json(
                ProblemDetails.fromThrowable(
                    throwable = e,
                    status = HttpStatus.BAD_REQUEST,
                    ctx = ctx,
                    feil = e.message ?: "Ugyldig input"
                )
            )
        }

        exception(`SvarfristUtløptException`::class.java) { e, ctx ->
            ctx.status(400).json(
                ProblemDetails.fromThrowable(
                    throwable = e,
                    status = HttpStatus.BAD_REQUEST,
                    ctx = ctx,
                    feil = e.message
                )
            )
        }

        exception(RekrutteringstreffIkkeFunnetException::class.java) { e, ctx ->
            ctx.status(404).json(
                ProblemDetails.fromThrowable(
                    throwable = e,
                    status = HttpStatus.NOT_FOUND,
                    ctx = ctx,
                    feil = e.message
                )
            )
        }

        exception(`JobbsøkerIkkeFunnetException`::class.java) { e, ctx ->
            ctx.status(404).json(
                ProblemDetails.fromThrowable(
                    throwable = e,
                    status = HttpStatus.NOT_FOUND,
                    ctx = ctx,
                    feil = e.message
                )
            )
        }

        exception(`JobbsøkerIkkeSynligException`::class.java) { e, ctx ->
            ctx.status(403).json(
                ProblemDetails.fromThrowable(
                    throwable = e,
                    status = HttpStatus.FORBIDDEN,
                    ctx = ctx,
                    feil = e.message
                )
            )
        }

        exception(KiValideringsException::class.java) { e, ctx ->
            ctx.status(422).json(
                ProblemDetails.fromThrowable(
                    throwable = e,
                    status = HttpStatus.UNPROCESSABLE_CONTENT,
                    ctx = ctx,
                    feilkode = e.feilkode,
                    melding = e.melding
                )
            )
        }

        exception(Exception::class.java) { e, ctx ->
            log.error("Uventet feil", e)
            ctx.status(500).json(
                ProblemDetails.fromThrowable(
                    throwable = e,
                    status = HttpStatus.INTERNAL_SERVER_ERROR,
                    ctx = ctx,
                    feil = "En ukjent feil oppstod på serveren.",
                )
            )
        }
    }
}