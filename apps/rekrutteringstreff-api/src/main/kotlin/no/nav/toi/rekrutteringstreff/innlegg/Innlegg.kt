package no.nav.toi.rekrutteringstreff.innlegg

import java.time.ZonedDateTime
import java.util.UUID

data class Innlegg(
    val id: UUID,
    val treffId: UUID,
    val tittel: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvPersonNavn: String,
    val opprettetAvPersonBeskrivelse: String,
    val sendesTilJobbsokerTidspunkt: ZonedDateTime?,
    val htmlContent: String,
    val opprettetTidspunkt: ZonedDateTime,
    val sistOppdatertTidspunkt: ZonedDateTime
)

data class OpprettInnleggRequestDto(
    val tittel: String,
    val opprettetAvPersonNavn: String,
    val opprettetAvPersonBeskrivelse: String,
    val sendesTilJobbsokerTidspunkt: ZonedDateTime?,
    val htmlContent: String,
    val innleggKiLoggId: String? = null,
    val lagreLikevel: Boolean = false
)

data class OppdaterInnleggRequestDto(
    val tittel: String,
    val opprettetAvPersonNavn: String,
    val opprettetAvPersonBeskrivelse: String,
    val sendesTilJobbsokerTidspunkt: ZonedDateTime?,
    val htmlContent: String,
    val innleggKiLoggId: String? = null,
    val lagreLikevel: Boolean = false
)

data class InnleggResponseDto(
    val id: UUID,
    val treffId: UUID,
    val tittel: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvPersonNavn: String,
    val opprettetAvPersonBeskrivelse: String,
    val sendesTilJobbsokerTidspunkt: ZonedDateTime?,
    val htmlContent: String,
    val opprettetTidspunkt: ZonedDateTime,
    val sistOppdatertTidspunkt: ZonedDateTime
)

fun Innlegg.toResponseDto() = InnleggResponseDto(
    id,
    treffId,
    tittel,
    opprettetAvPersonNavident,
    opprettetAvPersonNavn,
    opprettetAvPersonBeskrivelse,
    sendesTilJobbsokerTidspunkt,
    htmlContent,
    opprettetTidspunkt,
    sistOppdatertTidspunkt
)
