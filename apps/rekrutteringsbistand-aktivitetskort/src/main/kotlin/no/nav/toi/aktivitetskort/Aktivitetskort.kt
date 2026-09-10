package no.nav.toi.aktivitetskort

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.core.type.TypeReference
import no.nav.toi.Repository
import no.nav.toi.objectMapper
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.sql.ResultSet
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.UUID

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
    private val aktivitetskortType: AktivitetskortType = RekrutteringstreffType
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
            aktivitetskortType = aktivitetskortType.akaasType,
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

@JsonInclude(JsonInclude.Include.NON_NULL)
class AktivitetskortEtikett(
    val tekst: String,
    val sentiment: Sentiment,
    val kode: String? = null,
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

interface AktivitetskortType{
    val eventName: String
    val beskrivelse: String
    val handlingTittel: String
    val handlingSubtekst: String
    val akaasType: String
    val dbType: String
        get() = akaasType
    companion object {
        fun fraDbKode(verdi: String) = listOf(RekrutteringstreffType, WorkOpType, DeleCvMedArbeidsgiverType)
            .firstOrNull { it.dbType == verdi }
            ?: throw IllegalArgumentException("Ukjent aktivitetskorttype: $verdi")
    }
    fun tilFeil(
        fellesMeldingsfelter: FellesMeldingsfelter,
        resultSet: ResultSet,
        aktivitetskortId: String
    ): AktivitetskortFeil
}

object RekrutteringstreffType: AktivitetskortType {
    override val eventName = "rekrutteringstreffinvitasjon"
    override val beskrivelse =
        "Nav arrangerer rekrutteringstreff. På treffet møter du arbeidsgivere med behov for å ansette. Kanskje finner du nye og spennende jobbmuligheter? Følg lenken under for å svare JA eller NEI på om du planlegger å delta. Husk å svare innen fristen som du vil se når du åpner lenken."
    override val handlingTittel = "Sjekk ut treffet"
    override val handlingSubtekst = "Sjekk ut treffet og svar"
    override val akaasType = "REKRUTTERINGSTREFF"
    override fun tilFeil(
        fellesMeldingsfelter: FellesMeldingsfelter,
        resultSet: ResultSet,
        aktivitetskortId: String
    ) = RekrutteringstreffFeilMelding(
            fellesMeldingsfelter = fellesMeldingsfelter,
            rekrutteringstreffId = resultSet
                .getObject("rekrutteringstreff_id", UUID::class.java)
                ?.toString()
                ?: error("Mangler rekrutteringstreffId for aktivitetskort $aktivitetskortId"),
        )
}
object WorkOpType: AktivitetskortType {
    override val eventName = "workopinvitasjon"
    override val beskrivelse =
        "Nav arrangerer WorkOp. På WorkOp-en møter du arbeidsgivere med behov for å ansette. Kanskje finner du nye og spennende jobbmuligheter? Følg lenken under for å svare JA eller NEI på om du planlegger å delta. Husk å svare innen fristen som du vil se når du åpner lenken."
    override val handlingTittel = "Sjekk ut WorkOp-en"
    override val handlingSubtekst = "Sjekk ut WorkOp-en og svar"
    override val akaasType = "WORKOP"
    override fun tilFeil(
        fellesMeldingsfelter: FellesMeldingsfelter,
        resultSet: ResultSet,
        aktivitetskortId: String
    ) = WorkOpFeilMelding(
            fellesMeldingsfelter = fellesMeldingsfelter,
            rekrutteringstreffId = resultSet
                .getObject("rekrutteringstreff_id", UUID::class.java)
                ?.toString()
                ?: error("Mangler rekrutteringstreffId for aktivitetskort $aktivitetskortId")
        )
}
object DeleCvMedArbeidsgiverType: AktivitetskortType {
    override val eventName = "deltstilling"
    override val beskrivelse = "Nav hjelper en arbeidsgiver med å finne kandidater til en stilling, og tror den kan passe for deg."
    override val handlingTittel = "Sjekk ut stillingen"
    override val handlingSubtekst = "Sjekk ut stillingen og svar"
    override val dbType = "DELE_CV_MED_ARBEIDSGIVER"
    override val akaasType = "REKRUTTERINGSTREFF"   // Midlertidig inntil "DELE_CV_MED_ARBEIDSGIVER" er på plass

    override fun tilFeil(
        fellesMeldingsfelter: FellesMeldingsfelter,
        resultSet: ResultSet,
        aktivitetskortId: String
    ) = DeltStillingFeilMelding(
        fellesMeldingsfelter = fellesMeldingsfelter,
        stillingId = resultSet.getObject("stilling_id", UUID::class.java)
            ?.toString()
            ?: error("Mangler stillingId for aktivitetskort $aktivitetskortId"),
    )
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