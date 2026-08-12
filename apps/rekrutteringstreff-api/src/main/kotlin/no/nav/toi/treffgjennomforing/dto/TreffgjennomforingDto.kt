package no.nav.toi.treffgjennomforing.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import no.nav.toi.treffgjennomforing.ArbeidsgiverIntervjufordeling
import no.nav.toi.treffgjennomforing.ArbeidsgiverRotasjon
import no.nav.toi.treffgjennomforing.Deltakernummer
import no.nav.toi.treffgjennomforing.Interesse
import no.nav.toi.treffgjennomforing.Rom
import no.nav.toi.treffgjennomforing.Treffgjennomføring
import no.nav.toi.treffgjennomforing.TreffgjennomføringFase
import no.nav.toi.treffgjennomforing.Vurdering
import no.nav.toi.treffgjennomforing.Vurderingsvalg
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

/** Svaret på samtlige endepunkter, også skriveoperasjonene. */
data class TreffgjennomforingDto(
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

/**
 * `antallRom` sendes ikke inn. En klient som likevel gjør det skal ignoreres —
 * antall rom er ikke en klientavgjørelse.
 */
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

/** Svaret når oppmøte fjernes for en som har registreringer, uten bekreftelse. */
data class RegistreringerDto(val interesser: Int, val intervjuplasser: Int, val vurderinger: Int)

data class KaskadeAdvarselDto(
    val feil: String,
    val hint: String,
    val registreringer: RegistreringerDto,
)

fun Treffgjennomføring.tilDto(rekrutteringstreffId: String) = TreffgjennomforingDto(
    rekrutteringstreffId = rekrutteringstreffId,
    fase = fase,
    antallRom = antallRom,
    starttidspunkt = møteoppsett.starttidspunkt.format(KLOKKESLETT),
    varighetPerMøteMinutter = møteoppsett.varighetPerMøteMinutter,
    oppmøte = oppmøte.map { it.somString },
    deltakernummer = deltakernummer.map { it.tilDto() },
    rom = rom.map { it.tilDto() },
    arbeidsgiverRekkefølge = arbeidsgiverRekkefølge.map { it.tilDto() },
    interesser = interesser.map { it.tilDto() },
    intervjufordelinger = intervjufordelinger.map { it.tilDto() },
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
