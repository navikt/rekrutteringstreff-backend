package no.nav.toi.aktivitetskort

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
        private val sendtTidspunkt: ZonedDateTime?
    ) {
        fun send(producer: Producer<String, String>) {
            val record = ProducerRecord(
                "aktivitetskort-v1.1",
                aktivitetskort.aktivitetskortId,
                tilAkaasJson(),
            )
            try {
                producer.send(record).get()
                repository.markerSomSendt(messageId)
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
                "aktivitetskort": ${aktivitetskort.tilAkaasJson(aktivitetsStatus, endretAv, endretAvType, endretTidspunkt)}
            }
        """.trimIndent()
    }

    private fun tilAkaasJson(aktivitetsStatus: AktivitetsStatus, endretAv: String, endretAvType: EndretAvType, endretTidspunkt: ZonedDateTime) = """
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
            "detaljer": [],
            "etiketter": []
        }
    """.trimIndent()
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