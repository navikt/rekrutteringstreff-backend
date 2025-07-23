package no.nav.toi.rekrutteringstreff.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import no.nav.toi.JacksonConfig
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffResponsDto

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiMessage(val role: String, val content: String)

data class ResponseFormat(val type: String = "json_object")

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiRequest(
    val messages: List<OpenAiMessage>,
    val temperature: Double,
    val max_tokens: Int,
    val top_p: Double,
    val response_format: ResponseFormat,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Choice(val message: OpenAiMessage?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiResponse(val choices: List<Choice>?)

object OpenAiClient {
    private val apiUrl =
        System.getenv("OPENAI_API_URL")
            ?: "http://localhost:9955/openai/deployments/toi-gpt-4o/chat/completions?api-version=2024-12-01-preview"
    private val apiKey = System.getenv("OPENAI_API_KEY") ?: "test-key"
    private val mapper = JacksonConfig.mapper

    private val responseFormat = ResponseFormat()

    private inline fun <reified R> call(
        systemMessage: String,
        userMessage: String,
        temperature: Double,
        maxTokens: Int,
        topP: Double,
    ): R {
        val body = mapper.writeValueAsString(
            OpenAiRequest(
                messages = listOf(
                    OpenAiMessage("system", systemMessage),
                    OpenAiMessage("user", userMessage),
                ),
                temperature = temperature,
                max_tokens = maxTokens,
                top_p = topP,
                response_format = responseFormat,
            )
        )

        val (_, _, result) = apiUrl.httpPost()
            .header("api-key" to apiKey, "Content-Type" to "application/json")
            .body(body)
            .responseString()

        val raw = when (result) {
            is Result.Failure -> throw result.error
            is Result.Success -> result.get()
        }

        val content = mapper
            .readValue<OpenAiResponse>(raw)
            .choices?.firstOrNull()?.message?.content
            ?: error("Ingen respons fra OpenAI")

        return mapper.readValue(content.trim())
    }

    fun validateRekrutteringstreff(dto: ValiderRekrutteringstreffDto): ValiderRekrutteringstreffResponsDto =
        call(VALIDATION_SYSTEM_MESSAGE, dto.tekst, 0.0, 400, 1.0)

}

private const val VALIDATION_SYSTEM_MESSAGE = """
       Du er en ekspert på å vurdere informasjon, og på å aldri oppgi personsensitiv informasjon som for eksempel navn, e-postadresse, telefonnummer, fødselsdato eller andre identifiserende opplysninger i begrunnelsen din. 
 
       Vurder om tittel og beskrivelse for et rekrutteringstreff overholder NAVs retningslinjer, gjeldende lovverk innenfor personvernlovgivning og likestillings- og diskrimineringsloven. Retningslinjene gjelder for både arbeidstaker og arbeidsgiver. Analyser teksten under for tegn på diskriminerende, biased, ekskluderende, umoralsk eller uetisk tekst i input eller biased språk basert på kjønn, alder, etnisitet, religion, alder eller funksjonsevne. Med bias menes forutinntatthet knyttet til spesifikke egenskaper hos brukergrupper eller enkeltindivider. Vurderingen må sikre at prinsippet om forklarbarhet, 
        Identifiser og forklar hvorfor teksten eventuelt bryter med inkluderende språkpraksis, og gi veiledning til en alternativ formulering og begrunnelse for alternativ formulering. Sikre at teksten og svaret:
        1. Ikke kompromitter andres personvern ved å avsløre, behandle, utlede eller generere personopplysninger, deltakere, ytelser eller brukergrupper.
        2. Tydelig formidler at arrangementet er et rekrutteringstreff, der arbeidsgivere og potensielle deltakere møtes med rekrutteringsformål. 
        3. Bruker relevante og inkluderende formuleringer som fremmer mangfold, uten unødvendige eller indirekte diskriminerende krav. Eksempel på diskriminering er kjønn, religion, livssyn, hudfarge, nasjonal eller etnisk opprinnelse, flyktningsstatus, politisk syn, medlemskap i arbeidstakerorganisasjon, seksuell orientering, funksjonshemming eller alder. Forbudet omfatter også indirekte diskriminering; for eksempel at det stilles krav om gode norskkunnskaper eller avtjent verneplikt, uten at slike krav er nødvendige for å utføre stillingens arbeidsoppgaver på en forsvarlig måte.
        Du kan bruke følgende nav-spesifikke stikkord som innspill til vurderingen:
                *** Stikkord start ***
                IPS, KVP, Kvalifiseringsprogram, Kvalifiseringslønn, Kvalifiseringsstønad, Aktivitetsplikt, Angst, Arbeid med støtte, Arbeidsevne, Arbeids- og utdanningsreiser, Arbeidsforberedende trening, Arbeidsmarkedskurs, Arbeidspraksis, Arbeidsrettet rehabilitering, Arbeidstrening, AU-reiser, Avklaringstiltak, Barn, Behandling, Behov, Behovsliste, Booppfølging, Deltaker, Depresjon, Diagnoser, Fastlege, Flyktning, Fravær, Gjeld, Helseavklaring, Husleie, Individuell jobbstøtte, Introduksjonsprogram, Introduksjonsstønad, Jobbklar, Jobbspesialist, Kognitive utfordringer, Kognitive problemer, Kognitivt, Kommunale lavterskeltilbud, Kommunale tiltak, Kommunale tjenester, Koordinert bistand, Lån, Langvarig, Livsopphold, Lønnstilskudd, Mentor, Mentortilskudd, Midlertidig bolig, Midlertidig botilbud, Nedsatt arbeidsevne, Nedsatt funksjon, Norskferdigheter, Oppfølging, Oppfølgingstiltak, Oppfølgning i bolig, Opplæring, Opplæringstiltak, Pengestøtte, Problemer, Psykiatri, Psykolog, Rehabilitering, Restanse, Rus, Sommerjobb, Sommerjobbtiltak, Sosial oppfølging, Sosialfaglig oppfølging, Sosialhjelp, Sosialhjelpsmottaker, Sosialstønad, Sosiale problemer, Sosiale utfordringer, Supplerende stønad, Supported Employment, Syk, Sykdom, Sykemeldt, Sykt barn, Tilskudd, Tiltak, Tiltaksdeltaker, Ukrain, Ukraina, Ungdom, Utfordringer, Utvidet oppfølging, Varig tilrettelagt arbeid, Venteliste, Økonomi, Økonomisk kartlegging, Økonomisk rådgivning, Økonomisk sosialhjelp, Økonomisk stønad
                 *** Stikkord slutt ***
        
          Det er veldig viktig at du vurderer hvordan ordene brukes. Om de omtaler et arbeidsområde, noe den som skal ansettes får ansvar for, er det ikke personopplysninger. Eksempel på akseptabel formulering: "Arbeidsgiver søker bussjåfører på vestlandet." Eksempel på formulering som ikke er akseptabel: Arbeidsgiver søker menn til byggningsarbeid på vestlandet".
          Men om det omtaler en egenskap ved de som ansettes, er det en risiko for personopplysningssikerheten, siden vi kan senere knytte og identifisere personer til rekrutteringstreffet. Vi vil for eksempel ikke avsløre at de som knyttes til rekrutteringstreffet som kandidater er IPS brukere.
        Eksempel på akseptabel formulering: "Vi oppfordrer søkere med ulik bakgrunn til å delta". Eksempel på formulering som ikke er akseptabel: "Arrangementet er spesielt tilrettelagt unge, energiske deltakere og flyktninger".
          Det er lov å kun sende inn kun tittel eller beskrivelse for å få vurdert kun en av delene. Ikke kommenter at beskrivelse mangler om den er tom eller null. 
        ------
        Returner JSON uten markdown med feltene:
        - bryterRetningslinjer (boolean)
        - begrunnelse (string)

        Begrens meldinger som ikke bryter retningslinjene til høyst to setninger, og de som bryter til høyst tre setninger.
    """

