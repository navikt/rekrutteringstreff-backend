package no.nav.toi.aktivitetskort

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.toi.Repository
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDate
import java.time.ZonedDateTime

class Aktivitetskort(
    private val aktivitetskortId: String,
    private val rekrutteringstreffId: String,
    private val fnr: String,
    private val tittel: String,
    private val beskrivelse: String,
    private val startDato: LocalDate,
    private val sluttDato: LocalDate,
    private val opprettetAv: String,
    private val opprettetAvType: String,
    private val opprettetTidspunkt: ZonedDateTime
) {

    class AktivitetskortHendelse(
        private val repository: Repository,
        private val messageId: String,
        private val aktivitetskort: Aktivitetskort,
        private val actionType: ActionType,
        private val endretAv: String,
        private val endretAvType: EndretAvType,
        private val endretTidspunkt: ZonedDateTime,
        private val aktivitetsStatus: AktivitetsStatus,
        private val sendtTidspunkt: ZonedDateTime?,
        private val detaljer: List<AktivitetskortDetalj>,
        private val handlinger: List<AktivitetskortHandling>,
        private val etiketter: List<AktivitetskortEtikett>,
        private val oppgave: AktivitetskortOppgave?
    ) {
        fun send(producer: Producer<String, String>) {
            val record = ProducerRecord(
                "dab.aktivitetskort-v1.1",
                aktivitetskort.aktivitetskortId,
                tilAkaasJson(),
            )
            try {
                producer.send(record).get()
                repository.markerAktivitetskorthendelseSomSendt(messageId)
            } catch (e: Exception) {
                throw RuntimeException("Failed to send aktivitetskort hendelse ${aktivitetskort.aktivitetskortId}", e)
            }
        }

        private fun tilAkaasJson() = """
            {
                "messageId": "$messageId",
                "source": "REKRUTTERINGSBISTAND",
                "aktivitetskortType": "REKRUTTERINGSTREFF",
                "actionType": "${actionType.name}",
                "aktivitetskort": ${aktivitetskort.tilAkaasJson(aktivitetsStatus, endretAv, endretAvType, endretTidspunkt, detaljer, handlinger, etiketter, oppgave)}
            }
        """.trimIndent()

        class AktivitetskortHendelseFeil(
            private val aktivitetskortHendelse: AktivitetskortHendelse,
            private val errorMessage: String,
            private val errorType: ErrorType
        ) {
            fun sendTilRapid(rapidPublish: (String, String) -> Unit) {
                val now = ZonedDateTime.now()
                rapidPublish(aktivitetskortHendelse.aktivitetskort.fnr,
                    """
                        {
                            "@event_name": "aktivitetskort-feil",
                            "fnr": "${aktivitetskortHendelse.aktivitetskort.fnr}",
                            "aktivitetskortId": "${aktivitetskortHendelse.aktivitetskort.aktivitetskortId}",
                            "messageId": "${aktivitetskortHendelse.messageId}",
                            "errorMessage": "$errorMessage",
                            "errorType": "${errorType.name}",
                            "timestamp": "$now"
                        }
                    """.trimIndent())
                aktivitetskortHendelse.repository.markerFeilkøhendelseSomSendt(aktivitetskortHendelse.messageId)
            }
        }
    }

    private fun tilAkaasJson(aktivitetsStatus: AktivitetsStatus, endretAv: String, endretAvType: EndretAvType, endretTidspunkt: ZonedDateTime, detaljer: List<AktivitetskortDetalj>, handlinger: List<AktivitetskortHandling>, etiketter: List<AktivitetskortEtikett>, oppgave: AktivitetskortOppgave?) = """
        {
            "id": "$aktivitetskortId",
            "personIdent": "$fnr",
            "tittel": "$tittel",
            "aktivitetStatus": "$aktivitetsStatus",
            "startDato": "$startDato",
            "sluttDato": "$sluttDato",
            "beskrivelse": "$beskrivelse",
            "endretAv": {
                "ident": "$endretAv",
                "identType": "$endretAvType"
            },
            "endretTidspunkt": "$endretTidspunkt",
            "avtaltMedNav": false,
            "detaljer": ${detaljer.joinToJson(AktivitetskortDetalj::tilAkaasJson)},
            "handlinger": ${handlinger.joinToJson(AktivitetskortHandling::tilAkaasJson)},
            "etiketter": ${etiketter.joinToJson(AktivitetskortEtikett::tilAkaasJson)},
            "oppgave": ${oppgave?.tilAkaasJson()}
        }
    """.trimIndent()
}

fun <T> List<T>.joinToJson(transform: (T) -> String) =
    joinToString(prefix = "[", postfix = "]", separator = ",", transform = transform)

private val objectMapper = jacksonObjectMapper()

class AktivitetskortDetalj(
    val label: String,
    val verdi: String
) {
    fun tilAkaasJson() = """
        {
            "label": "$label",
            "verdi": "$verdi"
        }
    """.trimIndent()

    companion object {
        fun fraAkaasJson(json: String) =
            objectMapper.readValue(json, object : TypeReference<List<AktivitetskortDetalj>>(){})
    }
}

class AktivitetskortHandling(
    val tekst: String,
    val subtekst: String,
    val url: String,
    val lenkeType: LenkeType
) {
    fun tilAkaasJson() = """
        {
            "tekst": "$tekst",
            "subtekst": "$subtekst",
            "url": "$url",
            "lenkeType": "${lenkeType.name}"
        }
    """.trimIndent()

    companion object {
        fun fraAkaasJson(json: String) =
            objectMapper.readValue(json, object : TypeReference<List<AktivitetskortHandling>>(){})
    }
}

class AktivitetskortEtikett(
    val tekst: String,
    val label: Sentiment
) {
    fun tilAkaasJson() = """
        {
            "tekst": "$label",
            "label": "$label"
        }
    """.trimIndent()

    companion object {
        fun fraAkaasJson(json: String) =
            objectMapper.readValue(json, object : TypeReference<List<AktivitetskortEtikett>>(){})
    }
}

class AktivitetskortOppgave(
    val ekstern: AktivitetskortSubOppgave?,
    val intern: AktivitetskortSubOppgave?
) {
    fun tilAkaasJson() = listOfNotNull(
        ekstern?.let { "ekstern" to it.tilAkaasJson() },
        intern?.let { "intern" to it.tilAkaasJson() }
    ).map {
        """
            "${it.first}": ${it.second}
        """.trimIndent()
    }.let {
        """
            {
                ${it.joinToString(",\n")
            }
        """.trimIndent()
    }

    companion object {
        fun fraAkaasJson(json: String) = objectMapper.readValue(json, AktivitetskortOppgave::class.java)
    }
}

class AktivitetskortSubOppgave(
    val tekst: String,
    val subtekst: String,
    val url: String
) {
    fun tilAkaasJson() = """
        {
            "tekst": "$tekst",
            "subtekst": "$subtekst",
            "url": "$url"
        }
    """.trimIndent()
}

enum class Sentiment {
    POSITIVE,
    NEGATIVE,
    NEUTRAL
}

enum class LenkeType {
    INTERN,
    EKSTERN,
    FELLES
}

enum class AktivitetsStatus {
    FORSLAG,
    PLANLAGT,
    GJENNOMFORES,
    FULLFORT,
    AVBRUTT
}

enum class ActionType {
    UPSERT_AKTIVITETSKORT_V1,
    KASSER_AKTIVITET
}

enum class EndretAvType {
    ARENAIDENT,
    NAVIDENT,
    PERSONBRUKER,
    TILTAKSARRAGOER,
    ARBEIDSGIVER,
    SYSTEM
}

enum class ErrorType {
    AKTIVITET_IKKE_FUNNET,
    DESERIALISERINGSFEIL,
    DUPLIKATMELDINGFEIL,
    KAFKA_KEY_ULIK_AKTIVITETSID,
    MANGLER_OPPFOLGINGSPERIODE,
    MESSAGEID_LIK_AKTIVITETSID,
    UGYLDIG_IDENT,
    ULOVLIG_ENDRING
}