package no.nav.toi.treffgjennomføring.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.toi.treffgjennomføring.matching.ArbeidsgiverIntervjufordeling
import no.nav.toi.treffgjennomføring.møteplan.ArbeidsgiverRotasjon
import no.nav.toi.jobbsoker.oppmøte.Deltakernummer
import no.nav.toi.treffgjennomføring.matching.Interesse
import no.nav.toi.treffgjennomføring.møteplan.Rom
import no.nav.toi.treffgjennomføring.Treffgjennomføring
import no.nav.toi.treffgjennomføring.TreffgjennomføringFase
import no.nav.toi.oppfølging.Vurdering
import no.nav.toi.oppfølging.Vurderingsvalg
import java.time.format.DateTimeFormatter

private val KLOKKESLETT = DateTimeFormatter.ofPattern("HH:mm")

data class DeltakernummerDto(val personTreffId: String, val nummer: Int)

data class RomDto(val romnummer: Int, val jobbsøkere: List<String>)

data class ArbeidsgiverRotasjonDto(val arbeidsgiverTreffId: String, val startPosisjon: Int)

data class InteresseDto(val personTreffId: String, val arbeidsgiverTreffId: String)

data class ArbeidsgiverIntervjufordelingDto(
    val arbeidsgiverTreffId: String,
    val inkludertePersonTreffIder: List<String> = emptyList(),
    val ekskludertePersonTreffIder: List<String> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class VurderingDto(
    val personTreffId: String,
    val arbeidsgiverTreffId: String,
    val vurdering: Vurderingsvalg? = null,
    val notater: List<String> = emptyList(),
    val andregangsintervju: Boolean = false,
    val andregangsintervjuDato: String? = null,
    val jobbtilbud: Boolean = false,
)

data class TreffgjennomføringDto(
    val rekrutteringstreffId: String,
    val fase: TreffgjennomføringFase,
    val antallRom: Int,
    val starttidspunkt: String,
    val varighetPerMøteMinutter: Int,
    val oppmøte: List<String>,
    val deltakernummer: List<DeltakernummerDto>,
    val rom: List<RomDto>,
    val arbeidsgiverRekkefølge: List<ArbeidsgiverRotasjonDto>,
    val interesser: List<InteresseDto>,
    val intervjufordelinger: List<ArbeidsgiverIntervjufordelingDto>,
    val vurderinger: List<VurderingDto>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OppmøteRequestDto(
    val personTreffId: String,
    val møtt: Boolean,
    val bekreftSlettRegistreringer: Boolean = false,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MøteoppsettRequestDto(
    val starttidspunkt: String,
    val varighetPerMøteMinutter: Int,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class InteresseRequestDto(
    val personTreffId: String,
    val arbeidsgiverTreffId: String,
    val interessert: Boolean,
)

data class RegistreringerDto(val interesser: Int, val intervjuplasser: Int, val vurderinger: Int)

data class KaskadeAdvarselDto(
    val feil: String,
    val hint: String,
    val registreringer: RegistreringerDto,
)

fun Treffgjennomføring.tilDto(rekrutteringstreffId: String, vurderinger: List<Vurdering>) = TreffgjennomføringDto(
    rekrutteringstreffId = rekrutteringstreffId,
    fase = fase,
    antallRom = antallRom,
    starttidspunkt = møteplan.møteoppsett.starttidspunkt.format(KLOKKESLETT),
    varighetPerMøteMinutter = møteplan.møteoppsett.varighetPerMøteMinutter,
    oppmøte = oppmøte.map { it.somString },
    deltakernummer = deltakernummer.map { it.tilDto() },
    rom = møteplan.rom.map { it.tilDto() },
    arbeidsgiverRekkefølge = møteplan.arbeidsgiverRekkefølge.map { it.tilDto() },
    interesser = matching.interesser.map { it.tilDto() },
    intervjufordelinger = matching.intervjufordelinger.map { it.tilDto() },
    vurderinger = vurderinger.map { it.tilDto() },
)

private fun Deltakernummer.tilDto() = DeltakernummerDto(personTreffId.somString, nummer)

private fun Rom.tilDto() = RomDto(romnummer, jobbsøkere.map { it.somString })

private fun ArbeidsgiverRotasjon.tilDto() = ArbeidsgiverRotasjonDto(arbeidsgiverTreffId.somString, startPosisjon)

private fun Interesse.tilDto() = InteresseDto(personTreffId.somString, arbeidsgiverTreffId.somString)

private fun ArbeidsgiverIntervjufordeling.tilDto() = ArbeidsgiverIntervjufordelingDto(
    arbeidsgiverTreffId = arbeidsgiverTreffId.somString,
    inkludertePersonTreffIder = inkludertePersonTreffIder.map { it.somString },
    ekskludertePersonTreffIder = ekskludertePersonTreffIder.map { it.somString },
)

private fun Vurdering.tilDto() = VurderingDto(
    personTreffId = personTreffId.somString,
    arbeidsgiverTreffId = arbeidsgiverTreffId.somString,
    vurdering = vurdering,
    notater = notater.map { it.name },
    andregangsintervju = andregangsintervju,
    andregangsintervjuDato = andregangsintervjuDato?.toString(),
    jobbtilbud = jobbtilbud,
)
