package no.nav.toi.treffgjennomforing

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import java.time.LocalDate
import java.time.LocalTime

enum class TreffgjennomføringFase {
    OPPMØTE, ROM, INTERESSE, FORDELING, VURDERING;

    /** Fasen går bare framover. Et angret oppmøte lukker ikke et steg brukeren har vært innom. */
    fun senesteAv(annen: TreffgjennomføringFase) = if (ordinal >= annen.ordinal) this else annen
}

enum class Vurderingsvalg { AKTUELL, KANSKJE, IKKE_AKTUELL }

/** Backend eier lista. Prefikset sier hvem som sa det. Speiler `notatvalg.ts` i frontend. */
enum class Vurderingsnotat {
    AG_GODT_INNTRYKK,
    AG_VIL_MØTE_FLERE,
    AG_IKKE_BEHOV_NÅ,
    AG_AVVENTER_STILLING,
    AG_ØNSKER_PRAKSIS,
    AG_MANGLER_KOMPETANSE,
    AG_MANGLER_SPRÅK,
    AG_MANGLER_FORMELLE_KRAV,
    AG_ANDRE_PASSET_BEDRE,
    JS_POSITIV,
    JS_VIL_TENKE,
    JS_ØNSKER_MER_INFO,
    JS_VURDERER_ANDRE,
    JS_IKKE_INTERESSERT,
    JS_ARBEIDSTID,
    JS_REISEVEI,
    JS_HELSE_KAPASITET,
}

data class Deltakernummer(val personTreffId: PersonTreffId, val nummer: Int)

data class Rom(val romnummer: Int, val jobbsøkere: List<PersonTreffId>)

data class ArbeidsgiverRotasjon(val arbeidsgiverTreffId: ArbeidsgiverTreffId, val startPosisjon: Int)

data class Interesse(val personTreffId: PersonTreffId, val arbeidsgiverTreffId: ArbeidsgiverTreffId)

data class ArbeidsgiverIntervjufordeling(
    val arbeidsgiverTreffId: ArbeidsgiverTreffId,
    val inkludertePersonTreffIder: List<PersonTreffId>,
    val ekskludertePersonTreffIder: List<PersonTreffId>,
)

data class Vurdering(
    val personTreffId: PersonTreffId,
    val arbeidsgiverTreffId: ArbeidsgiverTreffId,
    val vurdering: Vurderingsvalg?,
    val notater: List<Vurderingsnotat>,
    val andregangsintervju: Boolean,
    val andregangsintervjuDato: LocalDate?,
    val jobbtilbud: Boolean,
) {
    /**
     * Samme regel som `harRegistrertNoe` i frontend. En rad uten innhold er ikke
     * en tom registrering, den er fravær av registrering, og skal slettes.
     */
    fun harRegistrertNoe() =
        vurdering != null || notater.isNotEmpty() || andregangsintervju ||
            andregangsintervjuDato != null || jobbtilbud
}

data class Møteoppsett(val starttidspunkt: LocalTime, val varighetPerMøteMinutter: Int)

data class Treffgjennomføring(
    val fase: TreffgjennomføringFase,
    val antallRom: Int,
    val møteoppsett: Møteoppsett,
    val oppmøte: List<PersonTreffId>,
    val deltakernummer: List<Deltakernummer>,
    val rom: List<Rom>,
    val arbeidsgiverRekkefølge: List<ArbeidsgiverRotasjon>,
    val interesser: List<Interesse>,
    val intervjufordelinger: List<ArbeidsgiverIntervjufordeling>,
    val vurderinger: List<Vurdering>,
) {
    companion object {
        val STANDARD_STARTTIDSPUNKT: LocalTime = LocalTime.of(10, 0)
        const val STANDARD_VARIGHET_MINUTTER = 10

        /** Tomtilstanden. Speiler `lagTreffgjennomføringStartdata` i frontendmocken. */
        fun tom(antallArbeidsgivere: Int) = Treffgjennomføring(
            fase = TreffgjennomføringFase.OPPMØTE,
            antallRom = beregnAntallRom(antallArbeidsgivere),
            møteoppsett = Møteoppsett(STANDARD_STARTTIDSPUNKT, STANDARD_VARIGHET_MINUTTER),
            oppmøte = emptyList(),
            deltakernummer = emptyList(),
            rom = emptyList(),
            arbeidsgiverRekkefølge = emptyList(),
            interesser = emptyList(),
            intervjufordelinger = emptyList(),
            vurderinger = emptyList(),
        )

        fun beregnAntallRom(antallArbeidsgivere: Int) = maxOf(antallArbeidsgivere, 1)
    }
}

/** Antall registreringer som forsvinner hvis oppmøtet fjernes. */
data class Registreringer(val interesser: Int, val intervjuplasser: Int, val vurderinger: Int) {
    fun finnesNoen() = interesser + intervjuplasser + vurderinger > 0

    companion object {
        val INGEN = Registreringer(0, 0, 0)
    }
}
