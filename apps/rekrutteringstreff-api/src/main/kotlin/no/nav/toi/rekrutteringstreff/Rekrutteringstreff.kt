package no.nav.toi.rekrutteringstreff

import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortSvartJaTreffstatusEndret
import no.nav.toi.jobbsoker.aktivitetskort.Aktivitetskortinvitasjon
import no.nav.toi.jobbsoker.aktivitetskort.Aktivitetskortsvar
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
    val status: RekrutteringstreffStatus,
    val opprettetAvPersonNavident: String,
    val opprettetAvNavkontorEnhetId: String,
    val opprettetAvTidspunkt: ZonedDateTime,
) {
    fun tilRekrutteringstreffDto() = RekrutteringstreffDto(
        tittel = tittel,
        beskrivelse = beskrivelse,
        fraTid = fraTid,
        tilTid = tilTid,
        svarfrist = svarfrist,
        gateadresse = gateadresse,
        postnummer = postnummer,
        poststed = poststed,
        status = status,
        opprettetAvPersonNavident = opprettetAvPersonNavident,
        opprettetAvNavkontorEnhetId = opprettetAvNavkontorEnhetId,
        opprettetAvTidspunkt = opprettetAvTidspunkt,
        id = id.somUuid
    )
    fun aktivitetskortInvitasjonFor(fnr: String) = Aktivitetskortinvitasjon.opprett(
        fnr = fnr,
        rekrutteringstreffId = id,
        tittel = tittel,
        fraTid = fraTid,
        tilTid = tilTid,
        opprettetAv = opprettetAvPersonNavident,
        opprettetTidspunkt = opprettetAvTidspunkt,
        gateadresse = gateadresse,
        postnummer = postnummer,
        poststed = poststed,
        svarfrist = svarfrist
    )
    fun aktivitetskortSvarFor(fnr: String, svar: Boolean) = Aktivitetskortsvar(
        fnr = fnr,
        rekrutteringstreffId = id,
        endretAv = fnr,
        svartJa = svar
    )

    fun aktivitetskortSvartJaTreffstatusEndretFor(fnr: String, treffstatus: String) = AktivitetskortSvartJaTreffstatusEndret(
        fnr = fnr,
        rekrutteringstreffId = id,
        endretAv = fnr,
        treffstatus = treffstatus
    )
}

data class TreffId(private val id: UUID) {
    constructor(uuid: String) : this(UUID.fromString(uuid))

    val somUuid = id
    val somString = id.toString()
    override fun toString() = somString
}
