package no.nav.toi.aktivitetskort

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import no.nav.toi.Repository
import no.nav.toi.objectMapper
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.time.LocalDate
import java.time.ZonedDateTime

enum class AktivitetskortType {
    REKRUTTERINGSTREFF,
    DELTSTILLING,
}

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
    private val sendtTidspunkt: ZonedDateTime?,
    private val aktivitetskortType: AktivitetskortType,
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
            aktivitetskortType = aktivitetskortType.name,
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

}

private class AkaasMelding(
    val messageId: String,
    val source: String,
    val aktivitetskortType: String,
    val actionType: String,
    val aktivitetskort: AkaasAktivitetskort,
)

private class AkaasAktivitetskort(
    val id: String,
    val personIdent: String,
    val tittel: String,
    val aktivitetStatus: String,
    val startDato: String?,
    val sluttDato: String?,
    val beskrivelse: String?,
    val endretAv: AkaasEndretAv,
    val endretTidspunkt: String,
    val avtaltMedNav: Boolean,
    val detaljer: List<AktivitetskortDetalj>,
    val handlinger: List<AktivitetskortHandling>?,
    val etiketter: List<AktivitetskortEtikett>,
    val oppgave: AktivitetskortOppgave?,
)

private class AkaasEndretAv(
    val ident: String,
    val identType: String,
)

class AktivitetskortDetalj(
    val label: String,
    val verdi: String,
) {
    companion object {
        fun fraAkaasJson(json: String) =
            objectMapper.readValue(json, object : TypeReference<List<AktivitetskortDetalj>>(){})
    }
}

class AktivitetskortHandling(
    val tekst: String,
    val subtekst: String,
    val url: String,
    val lenkeType: LenkeType,
) {
    companion object {
        fun fraAkaasJson(json: String) =
            objectMapper.readValue(json, object : TypeReference<List<AktivitetskortHandling>>(){})
    }
}

class AktivitetskortEtikett(
    val tekst: String,
    val label: Sentiment,
) {
    companion object {
        fun fraAkaasJson(json: String) =
            objectMapper.readValue(json, object : TypeReference<List<AktivitetskortEtikett>>(){})
    }
}

@JsonInclude(JsonInclude.Include.NON_NULL)
class AktivitetskortOppgave(
    val ekstern: AktivitetskortSubOppgave?,
    val intern: AktivitetskortSubOppgave?,
) {
    companion object {
        fun fraAkaasJson(json: String) = objectMapper.readValue(json, AktivitetskortOppgave::class.java)
    }
}

class AktivitetskortSubOppgave(
    val tekst: String,
    val subtekst: String,
    val url: String,
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