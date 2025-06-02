package no.nav.toi.rekrutteringstreff.innlegg

import java.time.ZonedDateTime
import java.util.UUID

data class Innlegg(
    val id: Long,
    val rekrutteringstreffId: UUID,
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
    val opprettetAvPersonNavident: String,
    val opprettetAvPersonNavn: String,
    val opprettetAvPersonBeskrivelse: String,
    val sendesTilJobbsokerTidspunkt: ZonedDateTime?,
    val htmlContent: String
)

data class InnleggResponseDto(
    val id: Long,
    val rekrutteringstreffId: UUID, // Included to show association, could be omitted if context is clear
    val tittel: String,
    val opprettetAvPersonNavident: String,
    val opprettetAvPersonNavn: String,
    val opprettetAvPersonBeskrivelse: String,
    val sendesTilJobbsokerTidspunkt: ZonedDateTime?,
    val htmlContent: String,
    val opprettetTidspunkt: ZonedDateTime,
    val sistOppdatertTidspunkt: ZonedDateTime
)

fun Innlegg.toResponseDto(): InnleggResponseDto = InnleggResponseDto(
    id = this.id,
    rekrutteringstreffId = this.rekrutteringstreffId,
    tittel = this.tittel,
    opprettetAvPersonNavident = this.opprettetAvPersonNavident,
    opprettetAvPersonNavn = this.opprettetAvPersonNavn,
    opprettetAvPersonBeskrivelse = this.opprettetAvPersonBeskrivelse,
    sendesTilJobbsokerTidspunkt = this.sendesTilJobbsokerTidspunkt,
    htmlContent = this.htmlContent,
    opprettetTidspunkt = this.opprettetTidspunkt,
    sistOppdatertTidspunkt = this.sistOppdatertTidspunkt
)