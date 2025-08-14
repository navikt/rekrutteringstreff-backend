package no.nav.toi.rekrutteringstreff.ki

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import no.nav.toi.JacksonConfig
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffResponsDto
import java.util.UUID
import kotlin.system.measureTimeMillis

object OpenAiClient {
    private val mapper = JacksonConfig.mapper

    private val chatUrl: String =
        System.getenv("OPENAI_CHAT_COMPLETIONS_URL")
            ?: System.getenv("AZURE_OPENAI_CHAT_URL")
            ?: System.getenv("OPENAI_URL")
            ?: "http://localhost:1234/v1/chat/completions"

    private val bearerKey: String? = System.getenv("OPENAI_API_KEY")
    private val azureKey: String? = System.getenv("AZURE_OPENAI_API_KEY")

    // Metadata for logging
    private val modelOrDeployment: String =
        System.getenv("OPENAI_MODEL")
            ?: System.getenv("AZURE_OPENAI_DEPLOYMENT")
            ?: "unknown"

    private const val temperature = 0.0
    private const val maxTokens = 200
    private const val topP = 1.0


    @Volatile
    private var repo: KiLoggRepository? = null

    fun configureKiLoggRepository(repo: KiLoggRepository) {
        this.repo = repo
    }

    fun validateRekrutteringstreff(dto: ValiderRekrutteringstreffDto): ValiderRekrutteringstreffResponsDto {
        val (_, result) = callOpenAi(dto.tekst)
        return result ?: ValiderRekrutteringstreffResponsDto(
            bryterRetningslinjer = false,
            begrunnelse = "KI utilgjengelig – ingen brudd registrert"
        )
    }

    fun validateRekrutteringstreffOgLogg(
        treffDbId: Long,
        feltType: String,
        tekst: String
    ): Pair<ValiderRekrutteringstreffResponsDto, UUID?> {
        val (elapsedMs, result) = callOpenAi(tekst)

        val response = result ?: ValiderRekrutteringstreffResponsDto(
            bryterRetningslinjer = false,
            begrunnelse = "KI utilgjengelig – ingen brudd registrert"
        )

        val insert = KiLoggInsert(
            treffDbId = treffDbId,
            feltType = feltType,
            sporringFraFrontend = tekst,
            sporringFiltrert = tekst,
            systemprompt = VALIDATION_SYSTEM_MESSAGE,
            ekstraParametre = mapOf(
                "temperature" to temperature,
                "max_tokens" to maxTokens,
                "top_p" to topP,
                "model" to modelOrDeployment
            ),
            bryterRetningslinjer = response.bryterRetningslinjer,
            begrunnelse = response.begrunnelse,
            kiNavn = "openai",
            kiVersjon = modelOrDeployment,
            svartidMs = elapsedMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        )

        val id = try {
            repo?.insert(insert)
        } catch (e: Exception) {
            log.warn("KI-logg insert feilet")
            secure(log).error("KI-logg insert feilet", e)
            null
        }

        return response to id
    }


    private fun callOpenAi(tekst: String): Pair<Long, ValiderRekrutteringstreffResponsDto?> {
        var parsed: ValiderRekrutteringstreffResponsDto? = null
        val req = OpenAiRequest(
            messages = listOf(
                OpenAiMessage(role = "system", content = VALIDATION_SYSTEM_MESSAGE),
                OpenAiMessage(role = "user", content = tekst)
            ),
            temperature = temperature,
            max_tokens = maxTokens,
            top_p = topP,
            response_format = ResponseFormat(type = "json_object")
        )

        val elapsed = measureTimeMillis {
            try {
                val payload = mapper.writeValueAsString(req)
                val headers = mutableMapOf(
                    "Content-Type" to "application/json"
                ).apply {
                    bearerKey?.let { put("Authorization", "Bearer $it") }
                    azureKey?.let { put("api-key", it) }
                }

                val (_, response, result) = chatUrl
                    .httpPost()
                    .header(headers)
                    .body(payload)
                    .responseString()

                when (result) {
                    is Result.Success -> {
                        val body = result.get()
                        val chat = mapper.readValue<OpenAiChatResponse>(body)
                        val content = chat.choices?.firstOrNull()?.message?.content
                        if (!content.isNullOrBlank()) {
                            // content skal være et JSON-objekt
                            parsed = mapper.readValue<ValiderRekrutteringstreffResponsDto>(content)
                        } else {
                            log.warn("Tomt KI-svar (status ${response.statusCode})")
                            secure(log).warn("Tomt KI-svar: $body")
                        }
                    }
                    is Result.Failure -> {
                        log.warn("KI-kall feilet (status ${response.statusCode})")
                        secure(log).error("KI-kall feilet", result.getException())
                    }
                }
            } catch (e: Exception) {
                log.warn("Uventet feil ved KI-kall")
                secure(log).error("Uventet feil ved KI-kall", e)
            }
        }
        return elapsed to parsed
    }
}


@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiMessage(val role: String, val content: String)

data class ResponseFormat(val type: String = "json_object")

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiRequest(
    val messages: List<OpenAiMessage>,
    val temperature: Double,
    val max_tokens: Int,
    val top_p: Double,
    val response_format: ResponseFormat
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiChatResponse(
    val choices: List<OpenAiChoice>?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiChoice(
    val message: OpenAiMessage?
)

private const val VALIDATION_SYSTEM_MESSAGE = """
    

Du er en ekspert på å vurdere informasjon, og på å aldri oppgi personsensitiv informasjon som for eksempel navn, e-postadresse, telefonnummer, fødselsdato eller andre identifiserende opplysninger i begrunnelsen din. 

Vurder om tittel og beskrivelse for et rekrutteringstreff overholder NAVs retningslinjer, gjeldende lovverk innenfor personvernlovgivning og likestillings- og diskrimineringsloven. Retningslinjene gjelder for både arbeidstaker og arbeidsgiver. Analyser teksten under for tegn på diskriminerende, biased, ekskluderende, umoralsk eller uetisk tekst i input eller biased språk basert på kjønn, alder, etnisitet, religion, alder eller funksjonsevne. Med bias menes forutinntatthet knyttet til spesifikke egenskaper hos brukergrupper eller enkeltindivider. Vurderingen må sikre prinsippet om forklarbarhet.
Identifiser og forklar hvorfor teksten eventuelt bryter med inkluderende språkpraksis, og gi veiledning til en alternativ formulering og begrunnelse for alternativ formulering. Sikre at teksten og svaret:
1. Ikke kompromitter andres personvern ved å avsløre, behandle, utlede eller generere personopplysninger, deltakere, ytelser eller brukergrupper.
2. Tydelig formidler at arrangementet er et rekrutteringstreff, der arbeidsgivere og potensielle deltakere møtes med rekrutteringsformål. 
3. Bruker relevante og inkluderende formuleringer som fremmer mangfold, uten unødvendige eller indirekte diskriminerende krav. Eksempel på diskriminering er kjønn, religion, livssyn, hudfarge, nasjonal eller etnisk opprinnelse, flyktningsstatus, politisk syn, medlemskap i arbeidstakerorganisasjon, seksuell orientering eller funksjonshemming. Forbudet omfatter også indirekte diskriminering; for eksempel at det stilles krav om gode norskkunnskaper, uten at slike krav er nødvendige for å utføre stillingens arbeidsoppgaver på en forsvarlig måte.
Du kan bruke følgende nav-spesifikke stikkord som innspill til vurderingen:
         *** Stikkord start ***
         IPS, KVP, Kvalifiseringsprogram, Kvalifiseringslønn, Kvalifiseringsstønad, Aktivitetsplikt, Angst, Arbeid med støtte, Arbeidsevne, Arbeids- og utdanningsreiser, Arbeidsforberedende trening, AAP, Arbeidsavklaringspenger, Arbeidsmarkedskurs, Arbeidspraksis, Arbeidsrettet rehabilitering, Arbeidstrening, AU-reiser, Avklaringstiltak, Barn, Behandling, Behov, Behovsliste, Booppfølging, Dagpenger, Deltaker, Depresjon, Diagnoser, Fastlege, Flyktning, Fravær, Gjeld, Helseavklaring, Husleie, Individuell jobbstøtte, Introduksjonsprogram, Introduksjonsstønad, Jobbklar, Jobbspesialist, Kognitive utfordringer, Kognitive problemer, Kognitivt, Kommunale lavterskeltilbud, Kommunale tiltak, Kommunale tjenester, Koordinert bistand, Lån, Langvarig, Livsopphold, Lønnstilskudd, Mentor, Mentortilskudd, Midlertidig bolig, Midlertidig botilbud, Nedsatt arbeidsevne, Nedsatt funksjon, Norskferdigheter, Oppfølging, Oppfølgingstiltak, Oppfølgning i bolig, Opplæring, Opplæringstiltak, Pengestøtte, Problemer, Psykiatri, Psykolog, Rehabilitering, Restanse, Rus, Sommerjobb, Sommerjobbtiltak, Sosial oppfølging, Sosialfaglig oppfølging, Sosialhjelp, Sosialhjelpsmottaker, Sosialstønad, Sosiale problemer, Sosiale utfordringer, Supplerende stønad, Supported Employment, Syk, Sykdom, Sykemeldt, Sykt barn, Tilskudd, Tiltak, Tiltaksdeltaker, Ukrain, Ukraina, Ungdom, Utfordringer, Utvidet oppfølging, Varig tilrettelagt arbeid, Venteliste, Økonomi, Økonomisk kartlegging, Økonomisk rådgivning, Økonomisk sosialhjelp, Økonomisk stønad
          *** Stikkord slutt ***

   Det er veldig viktig at du vurderer hvordan ordene brukes. Om de omtaler et arbeidsområde, noe den som skal ansettes får ansvar for, er det ikke personopplysninger. Eksempel på akseptabel formulering: "Arbeidsgiver søker bussjåfører på vestlandet." Eksempel på formulering som ikke er akseptabel: Arbeidsgiver søker menn til byggningsarbeid på vestlandet".
   Men om det omtaler en egenskap ved de som ansettes, er det en risiko for personopplysningssikerheten, siden vi kan senere knytte og identifisere personer til rekrutteringstreffet. Vi vil for eksempel ikke avsløre at de som knyttes til rekrutteringstreffet som kandidater er IPS brukere.
Eksempel på akseptabel formulering: "Vi oppfordrer søkere med ulik bakgrunn til å delta". Eksempel på formulering som ikke er akseptabel: "Arrangementet er spesielt tilrettelagt unge, energiske deltakere og flyktninger".
   Det er lov å kun sende inn kun tittel eller beskrivelse for å få vurdert kun en av delene. Ikke kommenter at beskrivelse mangler om den er tom eller null. 
Den anbefalte formuleringen skal være så spesifikk som mulig. Eksempel på tilbakemelding som er altfor generell "'Vi oppfordrer alle kvalifiserte kandidater til å delta, uavhengig av bakgrunn.'. Eksempel på akseptabel spesifikk tilbakemelding: "Treffet er åpent for alle, men passer spesielt for jobbsøkere som har gjennomført verneplikt"
------
Returner JSON uten markdown med feltene:
- bryterRetningslinjer (boolean)
- begrunnelse (string)

Begrens meldinger som ikke bryter retningslinjene til høyst to setninger, og de som bryter til høyst tre setninger.
Presiseringer av ord som skal godtas og hva som ikke skal godtas:

Godta "treff for norsklærere", da det tydelig er et yrke treffet rekrutterer til. 
Godta lenker til spesifikke arbeidsgivere. 
Tillat personnavn dersom det er tydelig at det er navnet på en arbeidsgiver. Akseptert formulering: "Dr. Willumsens Kvinneklinikk AS inviterer til rekrutteringstreff". Ikke akseptabel formulering: "Dr. Willumsen som jobber med kvinner". 
Godta at man skriver at de kan sende spørsmål til sin veileder. 
Tillat ord som "i hovedsak". Eksempel "jobbspråket er i hovedsak norsk". Det er også akseptabelt å skrive "• Snakker og forstår norsk godt nok til å jobbe i butikk." Men det er ikke lov å skrive for eksempel "Det er i hovedsak norske medarbeidere på arbeidsplassen". 
Godta at arbeidsoppgavene er for spesifikke grupper. Eksempel skal du godta "Jobbtreff for en arbeidsgiver som jobber med funksjonshemmede". 
Tillat at treffene har antallsbegrensninger. 
Ikke godta krav om å bo et sted. Eksempel: Ikke godta "Du må bo i Stavanger eller Sandnes.". Aksepter "Arbeidssted er i Stavanger eller Sandnes"
Godta personopplysninger dersom det er navn på arrangør
Personopplysnigner er kun tillatt dersom det er navn på arrangør av treff, eller viser til kontaktperson for treffet. Eksempel: Godta "Hilsen Lars Martin" og godta "Har du spørsmål, ring Lars Martin på 999 999 333". Ikke godta andre personopplysninger
Eksempel på svar som er akseptabelt: Teksten inneholder personopplysninger, som navn og telefonnummer. Dette er i strid med NAVs retningslinjer som krever at informasjon om enkeltpersoner ikke skal gjengis.

"""