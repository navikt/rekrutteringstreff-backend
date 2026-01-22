package no.nav.toi.rekrutteringstreff

import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortOppdatering
import no.nav.toi.jobbsoker.aktivitetskort.Aktivitetskortinvitasjon
import no.nav.toi.jobbsoker.aktivitetskort.RekrutteringstreffSvarOgStatus
import no.nav.toi.rekrutteringstreff.dto.RekrutteringstreffDto
import java.time.ZonedDateTime
import java.util.*

class Rekrutteringstreff(
    val id: TreffId,
    val tittel: String,
    val beskrivelse: String?,
    val fraTid: ZonedDateTime?,
    val tilTid: ZonedDateTime?,
    val svarfrist: ZonedDateTime?,
    val gateadresse: String?,
    val postnummer: String?,
    val poststed: String?,
    val kommune: String?,
    val kommunenummer: String?,
    val fylke: String?,
    val fylkesnummer: String?,
    val status: RekrutteringstreffStatus,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
    val eiere: List<String>,
    val sistEndret: ZonedDateTime,
    val sistEndretAv: String,
) {
    fun tilRekrutteringstreffDto(antallArbeidsgivere: Int, antallJobsøkere: Int) = RekrutteringstreffDto(
        tittel = tittel,
        beskrivelse = beskrivelse,
        fraTid = fraTid,
        tilTid = tilTid,
        svarfrist = svarfrist,
        gateadresse = gateadresse,
        postnummer = postnummer,
        poststed = poststed,
        kommune = kommune,
        kommunenummer = kommunenummer,
        fylke = fylke,
        fylkesnummer = fylkesnummer,
        status = status,
        opprettetAvPersonNavident = opprettetAvPersonNavident,
        opprettetAvNavkontorEnhetId = opprettetAvNavkontorEnhetId,
        opprettetAvTidspunkt = opprettetAvTidspunkt,
        id = id.somUuid,
        antallArbeidsgivere = antallArbeidsgivere,
        antallJobbsøkere = antallJobsøkere,
        eiere = eiere,
        sistEndret = sistEndret,
        sistEndretAv = sistEndretAv
    )
    fun aktivitetskortInvitasjonFor(fnr: String, hendelseId: UUID, avsenderNavident: String?) = Aktivitetskortinvitasjon.opprett(
        fnr = fnr,
        rekrutteringstreffId = id,
        hendelseId = hendelseId,
        tittel = tittel,
        fraTid = fraTid,
        tilTid = tilTid,
        opprettetAv = avsenderNavident ?: opprettetAvPersonNavident,
        opprettetTidspunkt = opprettetAvTidspunkt,
        gateadresse = gateadresse,
        postnummer = postnummer,
        poststed = poststed,
        svarfrist = svarfrist
    )
    fun aktivitetskortSvarOgStatusFor(
        fnr: String,
        hendelseId: UUID,
        endretAvPersonbruker: Boolean,
        svar: Boolean? = null,
        treffstatus: String? = null,
        endretAv: String? = null,
    ) = RekrutteringstreffSvarOgStatus(
        fnr = fnr,
        rekrutteringstreffId = id,
        endretAv = endretAv ?: if (endretAvPersonbruker) fnr else opprettetAvPersonNavident,
        endretAvPersonbruker = endretAvPersonbruker,
        hendelseId = hendelseId,
        svar = svar,
        treffstatus = treffstatus,
    )

    fun aktivitetskortOppdateringFor(
        fnr: String,
        hendelseId: UUID,
        avsenderNavident: String?,
        endredeFelter: List<Endringsfelttype>? = null
    ) = AktivitetskortOppdatering(
        fnr = fnr,
        rekrutteringstreffId = id,
        hendelseId = hendelseId,
        tittel = tittel,
        fraTid = fraTid!!,
        tilTid = tilTid!!,
        gateadresse = gateadresse!!,
        postnummer = postnummer!!,
        poststed = poststed!!,
        endretAv = avsenderNavident,
        endredeFelter = endredeFelter
    )
}

data class TreffId(private val id: UUID) {
    constructor(uuid: String) : this(UUID.fromString(uuid))

    val somUuid = id
    val somString = id.toString()
    override fun toString() = somString
}
