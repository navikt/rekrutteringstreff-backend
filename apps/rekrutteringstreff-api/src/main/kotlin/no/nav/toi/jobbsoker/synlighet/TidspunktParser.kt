package no.nav.toi.jobbsoker.synlighet

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val osloZone = ZoneId.of("Europe/Oslo")

/**
 * Parser @opprettet-tidspunkt fra rapids-and-rivers meldinger.
 *
 * Tidspunktet kan komme i flere formater:
 * - Med Z suffix (UTC): '2026-01-13T09:07:38.955620324Z'
 * - Med offset: '2026-01-13T10:07:38.955620324+01:00'
 * - Uten tidssone (lokal tid): '2026-01-13T10:07:38.955620324'
 *
 * Når tidssone mangler, antar vi Oslo-tid fordi:
 * - Synlighetsmotor kjører i Norge
 * - Resten av applikasjonen bruker Oslo-tid konsistent
 *
 * Returnerer ZonedDateTime i Oslo-tidssone for konsistens med resten av applikasjonen.
 */
fun parseOpprettetTidspunkt(opprettetTekst: String): ZonedDateTime {
    return try {
        // Prøv først som ISO instant med Z suffix
        if (opprettetTekst.endsWith("Z")) {
            Instant.parse(opprettetTekst).atZone(osloZone)
        }
        // Prøv som ZonedDateTime/OffsetDateTime (med offset)
        else if (opprettetTekst.contains('+') || (opprettetTekst.length > 10 && opprettetTekst.substring(10).contains('-'))) {
            ZonedDateTime.parse(opprettetTekst).withZoneSameInstant(osloZone)
        }
        // Anta Oslo-tid hvis ingen tidssone er spesifisert
        else {
            LocalDateTime.parse(opprettetTekst).atZone(osloZone)
        }
    } catch (e: DateTimeParseException) {
        // Fallback: prøv med en mer fleksibel parser
        try {
            ZonedDateTime.parse(opprettetTekst, DateTimeFormatter.ISO_ZONED_DATE_TIME)
                .withZoneSameInstant(osloZone)
        } catch (e2: DateTimeParseException) {
            // Siste utvei: parse som LocalDateTime og anta Oslo-tid
            LocalDateTime.parse(opprettetTekst, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(osloZone)
        }
    }
}

fun parseOpprettetTidspunktAsInstant(opprettetTekst: String): Instant =
    parseOpprettetTidspunkt(opprettetTekst).toInstant()
