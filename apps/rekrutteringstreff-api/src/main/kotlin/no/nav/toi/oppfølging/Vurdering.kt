package no.nav.toi.oppfølging

import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.PersonTreffId
import java.time.LocalDate

enum class Vurderingsvalg { AKTUELL, KANSKJE, IKKE_AKTUELL }

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
