package no.nav.toi.rekrutteringsbistand

import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val svarfristFormatter = DateTimeFormatter.ofPattern("dd.\u00A0MMMM\u00A0yyyy", Locale.forLanguageTag("no-NO"))

// skal vise svarfrist på aktivitetskort som f.eks. 26. november 2026
fun formaterSvarfrist(svarfrist: ZonedDateTime): String =
    svarfristFormatter.format(svarfrist.withZoneSameInstant(ZoneOffset.UTC).toLocalDate())
