package no.nav.toi.rekrutteringstreff.ki

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.kittinunf.fuel.httpPost
import com.github.kittinunf.result.Result
import no.nav.toi.JacksonConfig
import no.nav.toi.SecureLogLogger.Companion.secure
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.PersondataFilter
import no.nav.toi.rekrutteringstreff.ValiderRekrutteringstreffResponsDto
import java.util.UUID
import kotlin.system.measureTimeMillis

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenAiMessage(val role: String, val content: String)

private data class ResponseFormat(val type: String = "json_object")

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenAiRequest(
    val messages: List<OpenAiMessage>,
    val temperature: Double,
    val max_tokens: Int,
    val top_p: Double,
    val response_format: ResponseFormat
)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class Choice(val message: OpenAiMessage?)

@JsonIgnoreProperties(ignoreUnknown = true)
private data class OpenAiResponse(val choices: List<Choice>?)

class OpenAiClient(
    private val repo: KiLoggRepository,
    private val apiUrl: String =
        System.getenv("OPENAI_API_URL")
            ?: "http://localhost:9955/openai/deployments/toi-gpt-4o/chat/completions?api-version=2024-12-01-preview",
    private val apiKey: String = System.getenv("OPENAI_API_KEY") ?: "test-key"
) {
    private val mapper = JacksonConfig.mapper

    fun validateRekrutteringstreffOgLogg(
        treffId: UUID,
        feltType: String,
        tekst: String
    ): Pair<ValiderRekrutteringstreffResponsDto, UUID?> {
        lateinit var result: ValiderRekrutteringstreffResponsDto
        lateinit var filtered: String

        val elapsedMs = measureTimeMillis {
            val userMessageFiltered = PersondataFilter.filtrerUtPersonsensitiveData(tekst)
            secure(log).info("melding før filter: $tekst etter filter: $userMessageFiltered")

            val body = mapper.writeValueAsString(
                OpenAiRequest(
                    messages = listOf(
                        OpenAiMessage(role = "system", content = VALIDATION_SYSTEM_MESSAGE),
                        OpenAiMessage(role = "user", content = userMessageFiltered)
                    ),
                    temperature = temperature,
                    max_tokens = maxTokens,
                    top_p = topP,
                    response_format = responseFormat
                )
            )

            val (_, _, responseResult) = apiUrl.httpPost()
                .header("api-key" to apiKey, "Content-Type" to "application/json")
                .body(body)
                .responseString()

            val raw = when (responseResult) {
                is Result.Failure -> throw responseResult.error
                is Result.Success -> responseResult.get()
            }

            val content = mapper
                .readValue<OpenAiResponse>(raw)
                .choices?.firstOrNull()?.message?.content
                ?: error("Ingen respons fra OpenAI")

            result = mapper.readValue(content.trim())
            filtered = userMessageFiltered
        }

        val id = repo.insert(
            KiLoggInsert(
                treffId = treffId,
                feltType = feltType,
                spørringFraFrontend = tekst,
                spørringFiltrert = filtered,
                systemprompt = VALIDATION_SYSTEM_MESSAGE,
                ekstraParametreJson = null,
                bryterRetningslinjer = result.bryterRetningslinjer,
                begrunnelse = result.begrunnelse,
                kiNavn = kiNavn,
                kiVersjon = kiVersjon,
                svartidMs = elapsedMs.toInt()
            )
        )

        return result to id
    }

    companion object {
        private const val kiNavn = "azure-openai"
        private const val kiVersjon = "toi-gpt-4o"
        private const val temperature = 0.0
        private const val maxTokens = 400
        private const val topP = 1.0
        private val responseFormat = ResponseFormat()
    }
}

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