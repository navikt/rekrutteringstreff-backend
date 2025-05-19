package no.nav.toi.rekrutteringstreff.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import no.nav.toi.JacksonConfig
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffResponsDto

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiRequest(val messages: List<OpenAiMessage>, val temperature: Double, val max_tokens: Int)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiMessage(val role: String, val content: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiResponse(val id: String?, val choices: List<Choice>?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Choice(val message: OpenAiMessage?, val finish_reason: String?)

object OpenAiClient {
    private val openAiApiUrl = System.getenv("OPENAI_API_URL")
        ?: "http://localhost:9955/openai/deployments/toi-gpt-4o/chat/completions?api-version=2023-03-15-preview"
    private val openAiApiKey = System.getenv("OPENAI_API_KEY") ?: "test-key"
    private val objectMapper = JacksonConfig.mapper

    fun validateRekrutteringstreff(dto: ValiderRekrutteringstreffDto): ValiderRekrutteringstreffResponsDto {
        val systemMessage = VALIDATION_SYSTEM_MESSAGE
        val userMessage = "Tittel: ${dto.tittel}\nBeskrivelse: ${dto.beskrivelse}"
        val request = OpenAiRequest(
            messages = listOf(
                OpenAiMessage(role = "system", content = systemMessage),
                OpenAiMessage(role = "user", content = userMessage)
            ),
            temperature = 0.5,
            max_tokens = 1000
        )
        val requestBody = objectMapper.writeValueAsString(request)
        val (_, response, result) = openAiApiUrl.httpPost()
            .header(mapOf("api-key" to openAiApiKey, "Content-Type" to "application/json"))
            .body(requestBody)
            .responseString()
        return when (result) {
            is Result.Failure -> throw result.error
            is Result.Success -> {
                val openAiResponse = objectMapper.readValue<OpenAiResponse>(result.get())
                val content = openAiResponse.choices?.firstOrNull()?.message?.content
                    ?: throw IllegalStateException("Ingen respons fra OpenAI")

                // Fjern eventuelle markdown fra opeani endepunkt
                val cleanedContent = content.removePrefix("```json").removeSuffix("```").trim()

                objectMapper.readValue(cleanedContent, ValiderRekrutteringstreffResponsDto::class.java)
            }
        }
    }

    private val VALIDATION_SYSTEM_MESSAGE = """
        Vurder om tittel eller beskrivelse, eller eventuelt begge deler om både beskrivelse og tittel er med,  for et rekrutteringstreff overholder NAVs retningslinjer. Retningslinjene gjelder for både arbeidstaker og arbeidsgiver. Sjekk at teksten:
        1. Ikke avslører sensitiv informasjon om enkeltpersoner eller deltakere.
        2. Tydelig formidler at arrangementet er et rekrutteringstreff, der arbeidsgivere og potensielle deltakere møtes med jobbformål.
        3. Bruker relevante og inkluderende formuleringer som fremmer mangfold, uten unødvendige eller indirekte diskriminerende krav. Eksempel på diskriminering er kjønn, religion, livssyn, hudfarge, nasjonal eller etnisk opprinnelse, politisk syn, medlemskap i arbeidstakerorganisasjon, seksuell orientering, funksjonshemming eller alder. Forbudet omfatter også indirekte diskriminering; for eksempel at det stilles krav om gode norskkunnskaper eller avtjent verneplikt, uten at slike krav er nødvendige for å utføre stillingens arbeidsoppgaver på en forsvarlig måte.
           Du kan bruke følgende nav-spesifikke stikkord som innspill til vurderingen:
                *** Stikkord start ***
                IPS, KVP, Kvalifiseringsprogram, Kvalifiseringslønn, Kvalifiseringsstønad, Aktivitetsplikt, Angst, Arbeid med støtte, Arbeidsevne, Arbeids- og utdanningsreiser, Arbeidsforberedende trening, Arbeidsmarkedskurs, Arbeidspraksis, Arbeidsrettet rehabilitering, Arbeidstrening, AU-reiser, Avklaringstiltak, Barn, Behandling, Behov, Behovsliste, Booppfølging, Deltaker, Depresjon, Diagnoser, Fastlege, Flyktning, Fravær, Gjeld, Helseavklaring, Husleie, Individuell jobbstøtte, Introduksjonsprogram, Introduksjonsstønad, Jobbklar, Jobbspesialist, Kognitive utfordringer, Kognitive problemer, Kognitivt, Kommunale lavterskeltilbud, Kommunale tiltak, Kommunale tjenester, Koordinert bistand, Lån, Langvarig, Livsopphold, Lønnstilskudd, Mentor, Mentortilskudd, Midlertidig bolig, Midlertidig botilbud, Nedsatt arbeidsevne, Nedsatt funksjon, Norskferdigheter, Oppfølging, Oppfølgingstiltak, Oppfølgning i bolig, Opplæring, Opplæringstiltak, Pengestøtte, Problemer, Psykiatri, Psykolog, Rehabilitering, Restanse, Rus, Sommerjobb, Sommerjobbtiltak, Sosial oppfølging, Sosialfaglig oppfølging, Sosialhjelp, Sosialhjelpsmottaker, Sosialstønad, Sosiale problemer, Sosiale utfordringer, Supplerende stønad, Supported Employment, Syk, Sykdom, Sykemeldt, Sykt barn, Tilskudd, Tiltak, Tiltaksdeltaker, Ukrain, Ukraina, Ungdom, Utfordringer, Utvidet oppfølging, Varig tilrettelagt arbeid, Venteliste, Økonomi, Økonomisk kartlegging, Økonomisk rådgivning, Økonomisk sosialhjelp, Økonomisk stønad
                 *** Stikkord slutt ***
        
          Det er veldig viktig at du vurderer hvordan ordene brukes. Om de omtaler et arbeidsområde, noe den som skal ansettes får ansvar for, er det ikke personvernssensitivt. 
          Men om det omtaler en egenskap ved de som ansettes, er det en personvernsufordring, siden vi kan senere knytte personer til rekrutteringstreffet. Vi vil for eksempel ikke avsløre at de som knyttes til rekrutteringstreffet som kandidater er IPS brukere.
          Det er lov å kun sende inn tittel eller beskrivelse for å få vurdert kun en av delene. Men om begge er med skal de vurderes i sammenheng. Tittel har mindre krav til lengde og detalj enn beskrivelse.
        
        Returner JSON uten markdown med feltene:
        - bryterRetningslinjer (boolean)
        - begrunnelse (string)
    """.trimIndent()
}
