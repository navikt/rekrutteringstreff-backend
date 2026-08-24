package no.nav.toi.treffgjennomføring.dto

import no.nav.toi.jobbsoker.oppmøte.Deltakernummer
import no.nav.toi.treffgjennomføring.matching.ArbeidsgiverIntervjufordeling
import no.nav.toi.treffgjennomføring.møteplan.ArbeidsgiverRotasjon
import no.nav.toi.treffgjennomføring.matching.Interesse
import no.nav.toi.treffgjennomføring.møteplan.Rom
import no.nav.toi.treffgjennomføring.Treffgjennomføring
import no.nav.toi.treffgjennomføring.TreffgjennomføringSteg
import no.nav.toi.oppfølging.Vurdering
import no.nav.toi.oppfølging.Vurderingsvalg
import java.time.format.DateTimeFormatter

private val KLOKKESLETT = DateTimeFormatter.ofPattern("HH:mm")

data class DeltakernummerDto(val personTreffId: String, val deltakernummer: Int)

data class RomDto(val romnummer: Int, val jobbsøkere: List<String>)

data class ArbeidsgiverRotasjonDto(val arbeidsgiverTreffId: String, val førsteRomnummer: Int)

data class InteresseDto(val personTreffId: String, val arbeidsgiverTreffId: String)

data class ArbeidsgiverIntervjufordelingDto(
    val arbeidsgiverTreffId: String,
    val inkludertePersonTreffIder: List<String> = emptyList(),
    val ekskludertePersonTreffIder: List<String> = emptyList(),
)

data class VurderingDto(
    val personTreffId: String,
    val arbeidsgiverTreffId: String,
    val vurderingsstatus: Vurderingsvalg? = null,
    val vurderingsnotat: List<String> = emptyList(),
    val avtaltIntervju: Boolean = false,
    val avtaltIntervjuDato: String? = null,
    val jobbtilbud: Boolean = false,
)

data class TreffgjennomføringDto(
    val rekrutteringstreffId: String,
    val gjeldendeSteg: TreffgjennomføringSteg,
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

data class OppmøteRequestDto(
    val personTreffId: String,
    val møtt: Boolean,
)

data class StegRequestDto(val steg: TreffgjennomføringSteg)

data class MøteoppsettRequestDto(
    val starttidspunkt: String,
    val varighetPerMøteMinutter: Int,
)

data class InteresseRequestDto(
    val personTreffId: String,
    val arbeidsgiverTreffId: String,
    val interessert: Boolean,
)

data class RegistreringerDto(val interesser: Int, val vurderinger: Int)

data class OppmøteBlokkertDto(
    val feil: String,
    val hint: String,
    val registreringer: RegistreringerDto,
)

fun Treffgjennomføring.tilDto(rekrutteringstreffId: String, vurderinger: List<Vurdering>) = TreffgjennomføringDto(
    rekrutteringstreffId = rekrutteringstreffId,
    gjeldendeSteg = gjeldendeSteg,
    antallRom = antallRom,
    starttidspunkt = møteplan.møteoppsett.starttidspunkt.format(KLOKKESLETT),
    varighetPerMøteMinutter = møteplan.møteoppsett.varighetPerMøteMinutter,
    oppmøte = oppmøte.map { it.somString },
    deltakernummer = deltakernumre.map { it.tilDto() },
    rom = møteplan.rom.map { it.tilDto() },
    arbeidsgiverRekkefølge = møteplan.arbeidsgiverRekkefølge.map { it.tilDto() },
    interesser = matching.interesser.map { it.tilDto() },
    intervjufordelinger = matching.intervjufordelinger.map { it.tilDto() },
    vurderinger = vurderinger.map { it.tilDto() },
) 

private fun Deltakernummer.tilDto() = DeltakernummerDto(personTreffId.somString, deltakernummer)

private fun Rom.tilDto() = RomDto(romnummer, jobbsøkere.map { it.somString })

private fun ArbeidsgiverRotasjon.tilDto() = ArbeidsgiverRotasjonDto(arbeidsgiverTreffId.somString, førsteRomnummer)

private fun Interesse.tilDto() = InteresseDto(personTreffId.somString, arbeidsgiverTreffId.somString)

private fun ArbeidsgiverIntervjufordeling.tilDto() = ArbeidsgiverIntervjufordelingDto(
    arbeidsgiverTreffId = arbeidsgiverTreffId.somString,
    inkludertePersonTreffIder = inkludertePersonTreffIder.map { it.somString },
    ekskludertePersonTreffIder = ekskludertePersonTreffIder.map { it.somString },
)

private fun Vurdering.tilDto() = VurderingDto(
    personTreffId = personTreffId.somString,
    arbeidsgiverTreffId = arbeidsgiverTreffId.somString,
    vurderingsstatus = vurderingsstatus,
    vurderingsnotat = vurderingsnotat.map { it.name },
    avtaltIntervju = avtaltIntervju,
    avtaltIntervjuDato = avtaltIntervjuDato?.toString(),
    jobbtilbud = jobbtilbud,
)
