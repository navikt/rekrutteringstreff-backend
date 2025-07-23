package no.nav.toi.rekrutteringstreff.rekrutteringstreff

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import no.nav.toi.JacksonConfig
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffDto
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffResponsDto

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiRequest(
    val messages: List<OpenAiMessage>,
    val temperature: Double,
    val max_tokens: Int,
    val top_p: Double,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiMessage(val role: String, val content: String)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenAiResponse(val choices: List<Choice>?)

@JsonIgnoreProperties(ignoreUnknown = true)
data class Choice(val message: OpenAiMessage?)

object OpenAiClient {
    private val openAiApiUrl =
        System.getenv("OPENAI_API_URL")
            ?: "http://localhost:9955/openai/deployments/toi-gpt-4o/chat/completions?api-version=2023-03-15-preview"
    private val openAiApiKey = System.getenv("OPENAI_API_KEY") ?: "test-key"
    private val objectMapper = JacksonConfig.mapper

    private inline fun <reified R> call(
        systemMessage: String,
        userMessage: String,
        temperature: Double,
        max_tokens: Int,
        top_p: Double
    ): R {
        val body = objectMapper.writeValueAsString(
            OpenAiRequest(
                messages = listOf(
                    OpenAiMessage("system", systemMessage),
                    OpenAiMessage("user", userMessage)
                ),
                temperature = temperature,
                max_tokens = max_tokens,
                top_p = top_p,
            )
        )

        val (_, _, result) = openAiApiUrl.httpPost()
            .header(
                mapOf(
                    "api-key" to openAiApiKey,
                    "Content-Type" to "application/json"
                )
            )
            .body(body)
            .responseString()

        val rawJson = when (result) {
            is Result.Failure -> throw result.error
            is Result.Success -> result.get()
        }

        val content = objectMapper
            .readValue<OpenAiResponse>(rawJson)
            .choices?.firstOrNull()?.message?.content
            ?: error("Ingen respons fra OpenAI")

        val cleaned = content
            .removePrefix("```json")
            .removeSuffix("```")
            .trim()

        return objectMapper.readValue(cleaned)
    }

    fun validateRekrutteringstreff(dto: ValiderRekrutteringstreffDto): ValiderRekrutteringstreffResponsDto =
        call(
            VALIDATION_SYSTEM_MESSAGE,
            dto.tekst,
            max_tokens = 800,
            temperature = 0.7,
            top_p = 0.95
        )

    fun sanitizeValidationResponse(svar: ValiderRekrutteringstreffResponsDto): ValiderRekrutteringstreffResponsDto =
        call(
            SANITIZATION_SYSTEM_MESSAGE,
            objectMapper.writeValueAsString(svar),
            max_tokens = 800,
            temperature = 0.7,
            top_p = 0.95
        )

    private const val VALIDATION_SYSTEM_MESSAGE = """
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
    """

    private const val SANITIZATION_SYSTEM_MESSAGE = """
       
        Du er en ekspert på personvern og anonymisering. 
        Returner kun det sanerte JSON‑objektet, uten noen omkringliggende tekst eller markdown.
        Begrens meldinger som ikke bryter retningslinjene til høyst to setninger, og de som bryter til høyst tre setninger.
        
        Svaret skal ikke inneholde noen som helst personopplysninger (for eksempel navn, e-postadresse, telefonnummer, fødselsdato eller andre identifiserende opplysninger).  Du skal ikke gjengi, referere til, eller på noen måte bruke denne informasjonen i svaret ditt. Du skal ikke generere personopplysninger ved å gjengi det som er skrevet inn. 
        Eksempel på svar som ikke er akseptabel fordi den inneholder personnavn som Jenny Hansen : "Teksten inneholder personopplysninger, som navn ("Jenny Hansen") og telefonnummer. Dette er i strid med NAVs retningslinjer som krever at informasjon om enkeltpersoner ikke skal gjengis."
        Eksempel på svar som er akseptabelt: Teksten inneholder personopplysninger, som navn og telefonnummer. Dette er i strid med NAVs retningslinjer som krever at informasjon om enkeltpersoner ikke skal gjengis.
    """
}
