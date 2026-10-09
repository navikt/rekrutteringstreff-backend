package no.nav.toi.oppfølging

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import java.time.LocalDate

enum class Vurderingsvalg { AKTUELL, KANSKJE, IKKE_AKTUELL }

/**
 * Notater som ikke lenger finnes her, hoppes over når vurderinger leses fra databasen.
 * Bytt navn på verdien når betydningen endres, i stedet for å gi gammel verdi ny tekst.
 */
enum class Vurderingsnotat {
    AG_GODT_INNTRYKK,
    AG_AVVENTER_ANNEN_STILLING,
    AG_VIL_INVITERE_TIL_BESØK,
    AG_MANGLER_KOMPETANSE,
    AG_MANGLER_SPRÅK,
    AG_MANGLER_FORMELLE_KRAV,
    AG_IKKE_RIKTIG_MATCH,
    AG_ANDRE_PASSET_BEDRE,
    JS_POSITIV,
    JS_VIL_TENKE,
    JS_ØNSKER_MER_INFO,
    JS_VURDERER_ANDRE,
    JS_IKKE_RIKTIG_MATCH,
    JS_ARBEIDSTID,
    JS_REISEVEI,
    JS_INDIVIDUELLE_FORUTSETNINGER,
}

data class Vurdering(
    val personTreffId: PersonTreffId,
    val arbeidsgiverTreffId: ArbeidsgiverTreffId,
    val vurderingsstatus: Vurderingsvalg?,
    val vurderingsnotat: List<Vurderingsnotat>,
    val avtaltIntervju: Boolean,
    val avtaltIntervjuDato: LocalDate?,
    val jobbtilbud: Boolean,
) {
    fun harRegistrertNoe() =
        vurderingsstatus != null || vurderingsnotat.isNotEmpty() || avtaltIntervju ||
            avtaltIntervjuDato != null || jobbtilbud
}
