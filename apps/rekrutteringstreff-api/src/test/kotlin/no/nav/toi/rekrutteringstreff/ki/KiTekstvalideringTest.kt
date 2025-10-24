package no.nav.toi.rekrutteringstreff.ki

import no.nav.toi.log
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.io.File
import java.lang.Thread.sleep
import java.net.URI

private const val MAX_LENGDE_PROMPT_NAVN = 40
private const val TESTRESULTATER_FIL = "robTestresultater.txt"
// Testen skriver resultatene på et mer lesbart format enn loggene til filen definert over

@Disabled("Testen skal kun kjøres manuelt ved behov")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KiTekstvalideringParameterisertTest {
    private val openAiApiUrl = URI.create(hentPåkrevdeMiljøvariabler("OPENAI_API_URL")).toURL()
    private val openAiApiKey = hentPåkrevdeMiljøvariabler("OPENAI_API_KEY")

    private val openAiClient = OpenAiTestClient(
        apiUrl = openAiApiUrl.toString(),
        apiKey = openAiApiKey
    )

    companion object {
        private val testPromptsListe = listOf(
            Pair(
                false, "Kari Nordmann og hennes selskap inviterer til jobbmesse. Messen er på Rong senter. " +
                        "Følg skiltingen. Nav er tilstede på messen med en stand. Trafikkjentene kommer også. Norges Handicapforbund " +
                        "kommer for å rekruttere en kontorarbeider"
            ),
            Pair(false, "Norges Handicapforbund kommer for å rekruttere en kontorarbeider"),
            Pair(false, "Arrangementet er spesielt tilrettelagt for flyktninger som ønsker å komme i arbeid"),
            Pair(false, "Det er 20 plasser på treffet"),
            Pair(
                false,
                "Arrangementet er spesielt tilrettelagt for ungdommer mellom 18-30 år som ønsker å komme i arbeid."
            ),
            Pair(true, "Arrangementet er spesielt tilrettelagt for unge flyktninger som ønsker å komme i arbeid"),
            Pair(true, "Du må være registrert som sosialhjelpsmottaker for å delta"),
            Pair(true, "Arbeidsgiverne har erfaring med personer med gjeld og rusproblemer"),
            Pair(true, "Treffet er rettet mot personer som mottar økonomisk stønad fra Nav"),
            Pair(
                false, "Det rekrutteres til stillinger som sjåfør, terminalarbeider og logistikkmedarbeider. " +
                        "Du må ha førerkort klasse B eller C"
            ),
            Pair(
                false, "Det er ønskelig at du snakker og forstår norsk godt nok til å jobbe i team. Dette " +
                        "fordi arbeidsspråk er norsk"
            ),
            Pair(
                false,
                "Rekrutteringstreff for barnehager. Treffet er åpent for alle med interesse for arbeid med barn"
            ),
            Pair(
                false, "Er du interessert i å jobbe i butikk? Flere arbeidsgivere fra Oslo-området kommer " +
                        "for å rekruttere nye medarbeidere"
            ),
            Pair(true, "Arrangementet er spesielt tilrettelagt for deltakere med flyktningstatus og erfaring fra IPS"),
            Pair(true, "Vi søker energiske, unge menn med arabisk bakgrunn til lagerarbeid"),
            Pair(true, "Rekrutteringstreff for unge menn med bakgrunn fra Midtøsten"),
            Pair(false, "Rekrutteringstreff for jobb i den norske Kirke"),
            Pair(false, "Rekrutteringstreff for jobb i moskeen på Furuset"),
            Pair(true, "Rekrutteringstreff for hinduister"),
            Pair(true, "Jobbtreff for hinduister i tempelet i Kongsvinger"),
            Pair(false, "Anne Hanssen og hennes selskap inviterer til jobbmesse"),
            Pair(false, "Rekrutteringstreff for vaktmestere"),
            Pair(
                true, "Arbeidsgiver ønsker kandidater med god fysisk helse og fullført verneplikt. Treffet " +
                        "er tilpasset personer med erfaring fra KVP og rusbehandling."
            ),
            Pair(true, "Jobbtreff med NAV Vestre Aker og Ola Nordmann"),
            Pair(
                true, "Deltakere fra kvalifiseringsprogrammet og sosialhjelpsmottakere inviteres til å møte " +
                        "Ola Nordmann for individuell oppfølging."
            ),
            Pair(false, "Rekrutteringstreff for butikkmedarbeidere"),
            Pair(
                false, "Vi oppfordrer personer med ulik bakgrunn til å delta. Treffet er åpent for alle som " +
                        "ønsker jobb innen varehandel."
            ),
            Pair(false, "Rekrutteringstreff for kontorarbeid"),
            Pair(
                true, "Arrangementet er spesielt tilrettelagt for kvinner over 50 med erfaring fra " +
                        "sosialtjenesten og psykisk helsevern."
            ),
            Pair(
                true, "Treffet er for personer som har vært i KVP fordi arbeidsoppgavene er å hjelpe " +
                        "arbeidsledige med å få jobb"
            ),
            Pair(true, "Arbeidsgiver har god erfaring med deltakere som har utfordringer med angst og depresjon."),
            Pair(true, "Jobbtreff for deltakere i KVP"),
            Pair(
                true, "Arrangementet er kun for personer som mottar kvalifiseringsstønad og har vært i " +
                        "kommunale lavterskeltilbud."
            ),
            Pair(
                false, "treff for alle som vil jobbe med barn, og som har erfaring fra å jobbe med barn " +
                        "eller liker å jobbe med barn"
            ),
            Pair(false, "for folk som har fullført verneplikt"),
            Pair(false, "Oppfordrer folk som har gjennomført verneplikt til å søke"),
            Pair(false, "Treffet er åpent for alle, men passer spesielt for jobbsøkere som har vært på kurs"),
            Pair(
                true, "Clean Unit ser etter dyktige renholdere til flere ulike oppdrag. Bedriften har særlig " +
                        "kunder innenfor barnehagesektoren, men også på kinoene. Stillingene er fleksible, med mulighet for økt " +
                        "arbeidstid etter hvert som nye oppdrag kommer inn. Hva vi ser etter: ✅ Erfaring er ikke et krav -- du " +
                        "får grundig opplæring. ✅ Du må være nøyaktig og ha et godt øye for detaljer. ✅ Du må bo i Stavanger eller Sandnes."
            ),
            Pair(
                false, "Clean Unit ser etter dyktige renholdere til flere ulike oppdrag. Bedriften har særlig " +
                        "kunder innenfor barnehagesektoren, men også på kinoene. Stillingene er fleksible, med mulighet for økt " +
                        "arbeidstid etter hvert som nye oppdrag kommer inn. Hva vi ser etter: ✅ Erfaring er ikke et krav -- du " +
                        "får grundig opplæring. ✅ Du må være nøyaktig og ha et godt øye for detaljer. ✅ arbeidssted er i Stavanger eller Sandnes."
            ),
            Pair(true, "✅ Du må kunne kommunisere på norsk eller engelsk eventuelt polsk."),
            Pair(
                false, "Du må kunne kommunisere på norsk eller engelsk eventuelt polsk. Fordi arbeidsspråk er norsk, " +
                        "engelsk og så har vi en del ansatte som snakker polsk, så det går fint også"
            ),
            Pair(
                true,
                "Arbeidsspråk på arbeidsplass er norsk og engelsk den som får jobben må kunne snakke norsk, engelsk eller polsk"
            ),
            Pair(
                true, "Elvis frisør søker frisører til sine avdelinger i Stavanger, Hillevåg, Randaberg og på Sola. " +
                        "Du bestemmer selv om du vil jobbe 100% eller deltid 50% eller mer. Krav om fagbrev og/eller erfaring fra frisøryrket. " +
                        "Dersom du snakker polsk er det også fint, da vi har mange polske kunder"
            ),
            Pair(false, "treff for norsklærere"),
            Pair(
                false, "Velkommen til jobbmesse på Sandvika Storsenter.\n" +
                        "Dette er en spennende mulighet til å treffe butikker og serveringssteder på Sandvika Storsenter som " +
                        "trenger flere medarbeidere. Mange ulike stillingstyper og stillingsstørrelser. Jobbspråk er i hovedsak norsk.\n" +
                        "Tid & sted: Messen finner sted tirsdag 26.august kl. 8:30-9:30 i 2.etg. på Nytorget (ved Zara).\n" +
                        "Hvordan finne frem til Nytorget: Om du kommer med bil så parker i parkeringshuset i ny del (innkjøring " +
                        "ved Thon Hotel Oslofjord) hvor det er 2 timer gratis parkering. Om du kommer til fots fra Sandvika by " +
                        "så gå inn hovedinngangen i Claude Monets allé (mellom Christiania Glasmagasin og El Camino) og følg " +
                        "deretter skiltingen til Nytorget. Husk oppdatert CV i flere eksemplarer. Frivillig forberedelsesmøte før jobbmessen:\n" +
                        "Ønsker du mer informasjon om gjennomføring av messen, tips til speedintervju med arbeidsgiver, bedrifter " +
                        "som deltar mv, arrangerer vi et frivillig forberedelsesmøte hos NAV Bærum torsdag 21.august kl 13.00-14.30. Inngang fra elven."
            ),
            Pair(false, "Hilsen Kari og Ola"),
            Pair(
                false, "Det holdes en jobbmesse i Osterøy Rådhus, mandag 22.september fra kl 11-14\n" +
                        "Sted: Rådhuset på Osterøy, Heradstyresalen.\n" +
                        "Arrangementet starter kl 11 ved at de aktørene som har meldt at de kommer presenterer seg selv og hvilke " +
                        "behov de har for kolleger. Etter introduksjonen er det mulighet for interesserte å ha en til en samtale med ønskede aktører.\n" +
                        ". Du får vite ting som:\n" +
                        "• Hvordan er det å jobbe i den aktuelle bransjen, hvilke utdanning/bakgrunn du bør ha samt hvilke personlige " +
                        "egenskaper du bør ha for å lykkes.\n" +
                        "Aktørene vil også si noen om behovet de har for folk, muligheter for lærlingplass og/eller deltidsarbeid?\n" +
                        "Du må komme til arrangementet starter kl 11, senest.\n" +
                        "De arbeidsgiverne som har sagt de kommer er:\n" +
                        "Gunnebo / Kito Crosby - https://www.gunneboindustries.com/\n" +
                        "ASKO - asko.no\n" +
                        "Norsk Lastebilforbund- nlf.no - opplæring, lærlingplasser og behov i bransjen\n" +
                        "Systemtrafikk - https://systemtrafikk.no\n" +
                        "Det jobbes også med Osterøy kommune - helse, renhold, barnehage og SFO\n" +
                        "listen utvides fortløpende"
            ),
            Pair(false, "De arbeidsgiverne som har sagt de kommer er: Kari AS"),
            Pair(true, "Kvalifiseringsprogrammet inviterer til treff"),
            Pair(false, "Dr. Willumsens Kvinneklinikk AS inviterer til rekrutteringstreff"),
            Pair(false, "Jobbtreff for en arbeidsgiver som jobber med funksjonshemmede"),
            Pair(
                false, "Velkommen til Jobbtreff med speedintervjuer - Utdanning av salgs- og servicemedarbeidere.\n" +
                        "Nav samarbeider med McDonalds i Follo og Rygge om å ansette kandidater til lærlinge- og kvalifiseringsløp " +
                        "innen salg og servicefaget til disse restaurantene\n" +
                        "Det er også mulighet for annen hel -og deltidsjobb.\n" +
                        "Det vil avholdes jobbtreff med informasjon og påfølgende speedintervjuer, onsdag 10.september fra kl.9-14 " +
                        "på karrieresenteret på Kolbotn (Kolbotnveien 30, 1410 Kolbotn)\n" +
                        "Foreløpig agenda:\n" +
                        "Informasjon fra McDonalds\n" +
                        "Informasjon om ulike veier til fagbrev v/Nav og Frogn Vgs.\n" +
                        "Speedintervjuer (5 min) med McDonalds.\n" +
                        "Alle kandidater kontaktes av Nav for mer informajon,\n" +
                        "Kravspesifikasjon:\n" +
                        "Arbeidstiden vil være variere innenfor åpningstid og turnus.\n" +
                        "Turnusen er som følger: kl. 06:00-14:00, kl. 10:00-1800 (11:00-19:00), kl. 18:00-02:00\n" +
                        "Lønn:Etter tariff\n" +
                        "Stillingsbeskrivelse:\n" +
                        "Matlaging, kasse, renhold, forefallende arbeid.\n" +
                        "Krav:\n" +
                        "Ikke er redd for å ta i et tak og er forberedt på en hektisk hverdag\n" +
                        "Liker å jobbe med mat og kunder\n" +
                        "Er nøyaktig\n" +
                        "Har en god holdning\n" +
                        "Er pliktoppfyllende\n" +
                        "Er deg selv og kan ha det gøy- selv om du er på jobb\n" +
                        "Generell god fysisk form (sterk rygg), da du står mesteparten av arbeidstiden\n" +
                        "Serviceinnstilt og god arbeidsmoral\n" +
                        "Har sterke samarbeidsevner (teamwork er vår superkraft)\n" +
                        "Klarer å holde hodet kaldt under press (det er helt avgjørende når grillen streiker en fredagskveld)\n" +
                        "Elsker å lære og oppleve mestring (den følelsen du får når du klarer noe du trodde ikke var mulig)\n" +
                        "Er flink til å kommunisere og samarbeide, både med kollegaer og kunder (når det koker på kjøkkenet er din samarbeidsevne avgjørende)\n" +
                        "Vil jobbe på en arbeidsplass der alle får muligheten til å være seg selv (din personlighet betyr mer enn din CV)\n" +
                        "Ferdig som skolepliktig (10 klasse).\n" +
                        "Personlige egenskaper og egnethet for stillingen:\n" +
                        "Har masse energi og motivasjon\n" +
                        "Har en god holdning\n" +
                        "Like å jobbe i team\n" +
                        "Språk:\n" +
                        "Norsk Muntlig: A2-B1\n" +
                        "Norsk Skriftlig: A2-B1\n" +
                        "Opplæring og tilrettelegging:\n" +
                        "Det gis god opplæring med grundig oppfølging. Vi er stolte over vårt omfattende opplærings- og lederutviklingsprogram. " +
                        "Knapt noen norsk servicebedrift bruker så mye ressurser på opplæring av sine medarbeidere.\n" +
                        "Fleksibel arbeidstid. Vi har gode ordninger for å skape best mulig fleksibilitet for våre ansatte"
            ),
            Pair(true, "treff for alle som vil få seg en jobb, og som bor i oslo"),
            Pair(
                true, "Super-Peds til Ringerike!!\n" +
                        "I samarbeid med Adecco kjører vi en runde med speedintervjuer på ByLab fra kl 12:00-13:00 mandag 02.juni.\n" +
                        "En fordel om man registrerer bruker hos Adecco sine sider før speedintervju.\n" +
                        "Krav:\n" +
                        "Et brennende ønske om å jobbe i barnehage\n" +
                        "Godkjent B1 norsk\n" +
                        "Fleksibilitet\n" +
                        "Førerkort klasse-B og disponere bil\n" +
                        "Erfaring fra tilsvarende arbeid\n" +
                        "Bosted i Ringerike kommune\n" +
                        "Ønskelig:\n" +
                        "Relevant utdanning\n" +
                        "Erfaring fra barnehagearbeid\n" +
                        "Mulighet og ønske om å jobbe i andre fagfelt\n" +
                        "NB: Menn oppfordres til å søke!"
            ),
            Pair(
                true, "Jobbmesse på Tøyen – Møt din neste arbeidsgiver!\n" +
                        "NB! Reservert kun for kandidater over 30 år, samt nyankomne med kort gjenstående programtid, fra Nav Gamle Oslo.\n" +
                        "Nav Gamle Oslo inviterer, i samarbeid med bydelene Grünerløkka, Sagene og St. Hanshaugen, til en spennende jobbmesse 4. juni.\n" +
                        "Sted: Nav Gamle Oslo på Tøyen\n" +
                        "Tid: 12.00 - 13.30\n" +
                        "Er du på jakt etter jobb? Her får du muligheten til å møte arbeidsgivere som ser etter nye medarbeidere – kanskje akkurat deg!\n" +
                        "Bransjer som deltar er blant annet butikk, frisør, bygg og anlegg, restaurant og barnehage.\n" +
                        "Vil du vite mer om jobbmessen?\n" +
                        "Vi inviterer deg formøte 2. juni kl. 14.00 - 15.00.Møtt opp hosNav Gamle Oslo,Hagegata 22, 0653 Oslo.\n" +
                        "Her får du informasjon om aktuelle stillinger, arbeidsgiverne som deltar og tips til hvordan du kan " +
                        "forberede deg til samtalen med arbeidsgiver.\n" +
                        "Hvilke arbeidsgivere kommer?\n" +
                        "Illums Bolighus - de trenger butikk og salgs medarbeidere\n" +
                        "Cutters - de trenger flere frisører\n" +
                        "Mat & Drikke - de trenger kokker, servitører og bar medarbeidere\n" +
                        "Peak Services - de trenger renholds medarbeidere innenfor bygg og anleggsbransjen\n" +
                        "PVS Barnehage - de trenger flere barnehage medarbeidere\n" +
                        "Time2Staff - som rekrutterer inn kandidater i helsesektoren, både med og uten fagbrev\n" +
                        "MØT OPP PÅ FORMØTE 2. JUNI KL. 14, SÅ SEES VI PÅ JOBBMESSE 4 JUNI KL. 12.00 !\n" +
                        "PS! Husk å ta med minst 10 CV-erpå selve jobbmessen som du kan levere til arbeidsgivere du ønsker å møte " +
                        "på jobbmessen.Tips:Legg gjerne til et bilde på CV-en – det kan gjøre det lettere for arbeidsgiver å huske deg etter møtet.\n" +
                        "Ta med CV og godt humør på jobbmessen 4.juni!!"
            ),
            Pair(false, "for spørsmål, send melding til din veileder"),
            Pair(false, "Treff for alle som ønsker å jobbe med sosialhjelpsmottakere"),
            Pair(true, "Treff for sosialhjelpsmottakere"),
            Pair(true, "Treff for unge menn"),
            Pair(true, "Treff for kvinner i sin beste alder"),
            Pair(true, "Treff for menn mellom 18-30"), // Fikk 400 bad request fra OpenAi pga. innholdet
            Pair(false, "Treff for håndtverkere"),
            Pair(true, "Treff for sykemeldte håndtverkere"),
            Pair(false, "Kirkekontoret inviterer til treff for jobber i kirken"),
            Pair(false, "Moskeen på Furuset inviterer til treff for jobber i moskeen"),
            Pair(true, "Moskeen på Furuset inviterer til jobbtreff for muslimer"),
            Pair(true, "Har du mottatt AAP lenge? Da ønsker du kanskje en jobb? Kom på rekrutteringstreff"),
            Pair(true, "Treff for kvp") // Fikk 400 bad request fra OpenAi pga. innholdet
        )

        @JvmStatic
        fun testPrompts(): List<Arguments> =
            testPromptsListe.map { (forventetVurdering, prompt) ->
                Arguments.of(forventetVurdering, prompt, if (prompt.length >= MAX_LENGDE_PROMPT_NAVN) prompt.take(MAX_LENGDE_PROMPT_NAVN) + "..." else prompt)
            }
    }

    var antallFeilendeTestPrompts = 0
    var antallTestPromptsSomBryterOpenAiRetningslinjer = 0
    var bryterOpenAiRetningslinjer = ""
    var falskePositive = ""
    var falskeNegative = ""

    @ParameterizedTest(name = "TestPrompt nummer {index}, med input:  ''{2}'' skal gi vurdering {0}")
    @MethodSource("testPrompts")
    fun testValideringAvPrompts(forventetVurdering: Boolean, prompt: String, forkortetPrompt: String ) {
        val openAiRespons = openAiClient.validerTekst(prompt)
        if (openAiRespons.begrunnelse.contains("Den kan derfor ikke vurderes av KI.", ignoreCase = true)) {
            antallTestPromptsSomBryterOpenAiRetningslinjer += 1
            bryterOpenAiRetningslinjer += "- Følgende prompt bryter med OpenAi sine retningslinjer: '${prompt}' \n" +
                    "Begrunnelsen: '${openAiRespons.begrunnelse}' \n\n"
            return
        }
        try {
            Assertions.assertEquals(forventetVurdering, openAiRespons.bryterRetningslinjer)

        } catch (e: AssertionError) {
            antallFeilendeTestPrompts += 1
            val testResultat = "- Følgende prompt får feil vurdering: '${prompt}' \n Forventet vurdering: '${forventetVurdering}' \n " +
                    "Gitt vurdering: '${openAiRespons.bryterRetningslinjer}' \n " +
                    "Begrunnelsen: '${openAiRespons.begrunnelse}' \n\n"
            if (forventetVurdering) {
                falskeNegative += testResultat
            } else {
                falskePositive += testResultat
            }
            throw e
        }
        sleep(2000) // venter 2 sek for å unngå for mange requests til OpenAi på en gang
    }

    @AfterAll
    fun skrivTestResultaterTilFil() {
        File(TESTRESULTATER_FIL).bufferedWriter().use { writer ->
            writer.write("Testresultater for KI-ROBs tekstvalidering \n\n")
            writer.write("Dette er de test-promptene som feilet og fikk motsatt vurdering enn forventet. Antall feilende test-prompts er $antallFeilendeTestPrompts. \n\n")
            writer.write("Vurdering 'false' betyr at teksten ikke bryter med retningslinjene og dermed godtas, 'true' betyr at teksten bryter med retningslinjene og ikke godtas.\n\n")
            writer.write("De falske positive er:\n\n")
            writer.write(falskePositive)
            writer.write("De falske negative er:\n\n")
            writer.write(falskeNegative)
            if (bryterOpenAiRetningslinjer.isNotBlank()) {
                writer.write("Følgende prompts ble ikke vurdert av KI fordi de bryter med OpenAi sine retningslinjer:\n\n")
                writer.write(bryterOpenAiRetningslinjer)
            }
            writer.write(robsNøyaktighet())
        }
    }

    fun robsNøyaktighet(): String {
        val antallVurderteTestPrompts = testPrompts().size - antallTestPromptsSomBryterOpenAiRetningslinjer
        val nøyaktighet = String.format("%.2f", (antallVurderteTestPrompts - antallFeilendeTestPrompts).toDouble() / antallVurderteTestPrompts.toDouble() * 100.0)
        log.info("ROBs nøyaktighet er: $nøyaktighet %")
        return "ROBs nøyaktighet på de vurderte test-promptsene er: $nøyaktighet %"
    }

    fun hentPåkrevdeMiljøvariabler(envVarNavn: String): String =
        System.getenv(envVarNavn) ?: throw IllegalArgumentException("Husk å sette den påkrevde miljøvariabelen '$envVarNavn'")
}