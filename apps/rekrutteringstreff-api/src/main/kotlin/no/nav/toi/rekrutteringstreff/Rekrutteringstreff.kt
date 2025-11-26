package no.nav.toi.rekrutteringstreff

import no.nav.toi.jobbsoker.aktivitetskort.AktivitetskortOppdatering
import no.nav.toi.jobbsoker.aktivitetskort.Aktivitetskortinvitasjon
import no.nav.toi.jobbsoker.aktivitetskort.RekrutteringstreffSvarOgStatus
import no.nav.toi.rekrutteringstreff.dto.EndringerDto
import no.nav.toi.rekrutteringstreff.dto.RekrutteringstreffDto
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
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
        eiere = eiere
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
    fun aktivitetskortSvarOgStatusFor(
        fnr: String,
        svar: Boolean? = null,
        treffstatus: String? = null,
        endretAvPersonbruker: Boolean,
        endretAv: String? = null
    ) = RekrutteringstreffSvarOgStatus(
        fnr = fnr,
        rekrutteringstreffId = id,
        endretAv = endretAv ?: if (endretAvPersonbruker) fnr else opprettetAvPersonNavident,
        endretAvPersonbruker = endretAvPersonbruker,
        svar = svar,
        treffstatus = treffstatus
    )

    fun aktivitetskortOppdateringFor(fnr: String) = AktivitetskortOppdatering(
        fnr = fnr,
        rekrutteringstreffId = id,
        tittel = tittel,
        fraTid = fraTid!!,
        tilTid = tilTid!!,
        gateadresse = gateadresse!!,
        postnummer = postnummer!!,
        poststed = poststed!!
    )

    fun harRelevanteEndringerForAktivitetskort(endringer: EndringerDto): Boolean {
        return endringer.tittel != null ||
               endringer.fraTid != null ||
               endringer.tilTid != null ||
               endringer.postnummer != null ||
               endringer.poststed != null ||
               endringer.gateadresse != null
    }

    fun verifiserEndringerMotDatabase(endringer: EndringerDto): VerificationResult {
        val feil = mutableListOf<String>()

        endringer.tittel?.let {
            if (it.nyVerdi != tittel) {
                feil.add("tittel: forventet '${it.nyVerdi}', faktisk '$tittel'")
            }
        }

        endringer.fraTid?.let {
            val nyVerdiParsed = it.nyVerdi?.let { v -> ZonedDateTime.parse(v) }
            if (nyVerdiParsed != null && fraTid != null) {
                // Sammenlign med trunkering til millisekunder for å unngå database presisjonsproblemer
                if (nyVerdiParsed.truncatedTo(ChronoUnit.MILLIS) !=
                    fraTid.truncatedTo(ChronoUnit.MILLIS)) {
                    feil.add("fraTid: forventet '${it.nyVerdi}', faktisk '$fraTid'")
                }
            }
        }

        endringer.tilTid?.let {
            val nyVerdiParsed = it.nyVerdi?.let { v -> ZonedDateTime.parse(v) }
            if (nyVerdiParsed != null && tilTid != null) {
                // Sammenlign med trunkering til millisekunder for å unngå database presisjonsproblemer
                if (nyVerdiParsed.truncatedTo(ChronoUnit.MILLIS) !=
                    tilTid.truncatedTo(ChronoUnit.MILLIS)) {
                    feil.add("tilTid: forventet '${it.nyVerdi}', faktisk '$tilTid'")
                }
            }
        }

        endringer.postnummer?.let {
            if (it.nyVerdi != postnummer) {
                feil.add("postnummer: forventet '${it.nyVerdi}', faktisk '$postnummer'")
            }
        }

        endringer.poststed?.let {
            if (it.nyVerdi != poststed) {
                feil.add("poststed: forventet '${it.nyVerdi}', faktisk '$poststed'")
            }
        }

        endringer.gateadresse?.let {
            if (it.nyVerdi != gateadresse) {
                feil.add("gateadresse: forventet '${it.nyVerdi}', faktisk '$gateadresse'")
            }
        }

        return if (feil.isEmpty()) {
            VerificationResult(erGyldig = true)
        } else {
            VerificationResult(erGyldig = false, feilmelding = feil.joinToString(", "))
        }
    }

    data class VerificationResult(
        val erGyldig: Boolean,
        val feilmelding: String? = null
    )
}

data class TreffId(private val id: UUID) {
    constructor(uuid: String) : this(UUID.fromString(uuid))

    val somUuid = id
    val somString = id.toString()
    override fun toString() = somString
}
