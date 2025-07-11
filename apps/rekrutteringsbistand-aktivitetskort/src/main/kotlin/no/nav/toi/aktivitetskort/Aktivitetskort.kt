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
                "aktivitetskort": ${aktivitetskort.tilAkaasJson(aktivitetsStatus, endretAv, endretAvType, endretTidspunkt)}
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
            "detaljer": [
                {
                    "label": "Sted",
                    "verdi": "Quality Hotel, Bøngerudveien 23,<br>0123 Hafsrud"
                },
                {
                    "label": "Antall plasser",
                    "verdi": "42"
                }
            ],
            "handlinger": [
                {
                    "tekst": "Handling intern",
                    "subtekst": "Subtekst intern",
                    "url": "https://example.nav.no/handling1",
                    "lenkeType": "INTERN"
                },
                {
                    "tekst": "Handling ekstern",
                    "subtekst": "Subtekst ekstern",
                    "url": "https://example.nav.no/handling2",
                    "lenkeType": "EKSTERN"
                },
                {
                    "tekst": "Handling felles",
                    "subtekst": "Subtekst felles",
                    "url": "https://example.nav.no/handling3",
                    "lenkeType": "FELLES"
                }
            ],
            "etiketter": [
                {
                    "tekst": "Eksempel Etikett positiv",
                    "sentiment": "POSITIVE"
                },
                {
                    "tekst": "Eksempel Etikett negativ",
                    "sentiment": "NEGATIVE"
                },
                {
                    "tekst": "Eksempel Etikett neural",
                    "sentiment": "NEUTRAL"
                }
            ],
            "oppgave": {
                "ekstern": {
                    "tekst": "Ekstern oppgave",
                    "subtekst": "Subtekst ekstern oppgave",
                    "url": "https://example.nav.no/oppgave",
                },
                "intern": {
                    "tekst": "Intern oppgave",
                    "subtekst": "Subtekst intern oppgave",
                    "url": "https://example.nav.no/oppgave-intern"
                }
            }
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