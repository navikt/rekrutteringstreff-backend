package no.nav.toi.aktivitetskort

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import no.nav.toi.Repository
import no.nav.toi.objectMapper
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDate
import java.time.ZonedDateTime

class Aktivitetskort (
    private val dabAktivitetskortTopic: String,
    private val repository: Repository,
    private val aktivitetskortId: String,
    private val messageId: String,
    private val fnr: String,
    private val tittel: String,
    private val aktivitetsStatus: AktivitetsStatus,
    private val beskrivelse: String?,
    private val startDato: LocalDate?,
    private val sluttDato: LocalDate?,
    private val detaljer: List<AktivitetskortDetalj>,
    private val handlinger: List<AktivitetskortHandling>?,
    private val etiketter: List<AktivitetskortEtikett>,
    private val oppgave: AktivitetskortOppgave?,
    private val actionType: ActionType,
    private val avtaltMedNav: Boolean,
    private val endretAv: String,
    private val endretAvType: EndretAvType,
    private val endretTidspunkt: ZonedDateTime,
    private val sendtTidspunkt: ZonedDateTime?
) {

    fun send(producer: Producer<String, String>) {
        val record = ProducerRecord(
            dabAktivitetskortTopic,
            aktivitetskortId,
            tilAkaasJson(),
        )
        try {
            producer.send(record).get()
            repository.markerAktivitetskorthendelseSomSendt(messageId)
        } catch (e: Exception) {
            throw RuntimeException("Failed to send aktivitetskort hendelse ${aktivitetskortId}", e)
        }
    }

    private fun tilAkaasJson(): String {
        val melding = AkaasMelding(
            messageId = messageId,
            source = "REKRUTTERINGSBISTAND",
            aktivitetskortType = "REKRUTTERINGSTREFF",
            actionType = actionType.name,
            aktivitetskort = AkaasAktivitetskort(
                id = aktivitetskortId,
                personIdent = fnr,
                tittel = tittel,
                aktivitetStatus = aktivitetsStatus.name,
                startDato = startDato?.toString(),
                sluttDato = sluttDato?.toString(),
                beskrivelse = beskrivelse,
                endretAv = AkaasEndretAv(ident = endretAv, identType = endretAvType.name),
                endretTidspunkt = endretTidspunkt.toString(),
                avtaltMedNav = false,
                detaljer = detaljer,
                handlinger = handlinger,
                etiketter = etiketter,
                oppgave = oppgave,
            ),
        )
        return objectMapper.writeValueAsString(melding)
    }

        class AktivitetskortFeil(
            private val aktivitetskortHendelse: Aktivitetskort,
            private val rekrutteringstreffId: String,
            private val errorMessage: String,
            private val errorType: ErrorType
        ) {
            fun sendTilRapid(rapidPublish: (String, String) -> Unit) {
                val melding = AkaasFeilMelding(
                    eventName = "aktivitetskort-feil",
                    fnr = aktivitetskortHendelse.fnr,
                    aktivitetskortId = aktivitetskortHendelse.aktivitetskortId,
                    rekrutteringstreffId = rekrutteringstreffId,
                    endretAv = aktivitetskortHendelse.endretAv,
                    messageId = aktivitetskortHendelse.messageId,
                    errorMessage = errorMessage,
                    errorType = errorType.name,
                    timestamp = ZonedDateTime.now().toString(),
                )
                rapidPublish(aktivitetskortHendelse.fnr, objectMapper.writeValueAsString(melding))
                aktivitetskortHendelse.repository.markerFeilkøhendelseSomSendt(aktivitetskortHendelse.messageId)
            }
        }
}

private class AkaasMelding(
    private val messageId: String,
    private val source: String,
    private val aktivitetskortType: String,
    private val actionType: String,
    private val aktivitetskort: AkaasAktivitetskort,
)

private class AkaasAktivitetskort(
    private val id: String,
    private val personIdent: String,
    private val tittel: String,
    private val aktivitetStatus: String,
    private val startDato: String?,
    private val sluttDato: String?,
    private val beskrivelse: String?,
    private val endretAv: AkaasEndretAv,
    private val endretTidspunkt: String,
    private val avtaltMedNav: Boolean,
    private val detaljer: List<AktivitetskortDetalj>,
    private val handlinger: List<AktivitetskortHandling>?,
    private val etiketter: List<AktivitetskortEtikett>,
    private val oppgave: AktivitetskortOppgave?,
)

private class AkaasEndretAv(
    private val ident: String,
    private val identType: String,
)

private class AkaasFeilMelding(
    @field:JsonProperty("@event_name") private val eventName: String,
    private val fnr: String,
    private val aktivitetskortId: String,
    private val rekrutteringstreffId: String,
    private val endretAv: String,
    private val messageId: String,
    private val errorMessage: String,
    private val errorType: String,
    private val timestamp: String,
)

class AktivitetskortDetalj(
    private val label: String,
    private val verdi: String,
) {
    companion object {
        fun fraAkaasJson(json: String) =
            objectMapper.readValue(json, object : TypeReference<List<AktivitetskortDetalj>>(){})
    }
}

class AktivitetskortHandling(
    private val tekst: String,
    private val subtekst: String,
    private val url: String,
    private val lenkeType: LenkeType,
) {
    companion object {
        fun fraAkaasJson(json: String) =
            objectMapper.readValue(json, object : TypeReference<List<AktivitetskortHandling>>(){})
    }
}

class AktivitetskortEtikett(
    private val tekst: String,
    private val label: Sentiment,
) {
    companion object {
        fun fraAkaasJson(json: String) =
            objectMapper.readValue(json, object : TypeReference<List<AktivitetskortEtikett>>(){})
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
class AktivitetskortOppgave(
    private val ekstern: AktivitetskortSubOppgave?,
    private val intern: AktivitetskortSubOppgave?,
) {
    companion object {
        fun fraAkaasJson(json: String) = objectMapper.readValue(json, AktivitetskortOppgave::class.java)
    }
}

class AktivitetskortSubOppgave(
    private val tekst: String,
    private val subtekst: String,
    private val url: String,
)

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
    PERSONBRUKERIDENT,
    TILTAKSARRANGOER,
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