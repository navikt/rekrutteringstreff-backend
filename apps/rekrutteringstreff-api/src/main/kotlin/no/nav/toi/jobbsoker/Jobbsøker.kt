package no.nav.toi.jobbsoker

import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.jobbsoker.dto.JobbsøkerHendelse
import no.nav.toi.rekrutteringstreff.TreffId
import java.util.*

data class Fødselsnummer(private val fødselsnummer: String) {
    init {
        if (!(fødselsnummer.length == 11 && fødselsnummer.all { it.isDigit() })) {
            throw IllegalArgumentException("Fødselsnummer må være 11 siffer. Mottok [$fødselsnummer].")
        }
    }
    val asString: String = fødselsnummer
    override fun toString(): String = asString
}

data class Fornavn(private val fornavn: String) {
    init {
        if (fornavn.isEmpty()) throw IllegalArgumentException("Fornavn må være ikke-tomt.")
    }
    val asString: String = fornavn
    override fun toString(): String = asString
}

data class Etternavn(private val etternavn: String) {
    init {
        if (etternavn.isEmpty()) throw IllegalArgumentException("Etternavn må være ikke-tomt.")
    }
    val asString: String = etternavn
    override fun toString(): String = asString
}

data class Kontor(
    val kontornummer: String,
    val kontornavn: String?,
)

data class VeilederNavn(private val navn: String) {
    init {
        if (navn.isEmpty()) {
            throw IllegalArgumentException("VeilederNavn kan ikke være tomt.")
        }
    }
    val asString: String = navn
    override fun toString(): String = asString
}

class VeilederNavIdent(ident: String) {
    val asString: String = ident.trim().uppercase().also {
        if (it.isEmpty()) {
            throw IllegalArgumentException("VeilederNavIdent kan ikke være tom.")
        }
    }

    override fun toString(): String = asString

    override fun equals(other: Any?): Boolean =
        other is VeilederNavIdent && asString == other.asString

    override fun hashCode(): Int = asString.hashCode()
}

data class Innsatsgruppe(private val verdi: String) {
    init {
        if (verdi.isEmpty()) {
            throw IllegalArgumentException("Innsatsgruppe kan ikke være tom.")
        }
    }
    val asString: String = verdi
    override fun toString(): String = asString
}

/**
 * Kandidatnummer brukes kun for on-demand henting fra ekstern API (kandidatsøk-api).
 * Det lagres ikke lenger i databasen, men brukes som type-sikker wrapper for API-respons.
 */
data class Kandidatnummer(private val kandidatnummer: String) {
    val asString: String = kandidatnummer
    override fun toString(): String = asString
}

data class LeggTilJobbsøker(
    val fødselsnummer: Fødselsnummer,
    val fornavn: Fornavn,
    val etternavn: Etternavn,
    val kontor: Kontor? = null,
    val veilederNavn: VeilederNavn? = null,
    val veilederNavIdent: VeilederNavIdent? = null,
    val alder: Int? = null,
    val innsatsgruppe: Innsatsgruppe? = null,
)

enum class JobbsøkerStatus {
    LAGT_TIL, INVITERT, SVART_JA, SVART_NEI, MØTT_OPP, FÅTT_JOBB, SLETTET
}

enum class AktuellForTreffStatus {
    VURDERES,
    KONTAKTET,
    AKTUELL,
    IKKE_AKTUELL,
}

data class Jobbsøker(
    val personTreffId: PersonTreffId,
    val treffId: TreffId,
    val fødselsnummer: Fødselsnummer,
    val fornavn: Fornavn,
    val etternavn: Etternavn,
    val kontor: Kontor?,
    val veilederNavn: VeilederNavn?,
    val veilederNavIdent: VeilederNavIdent?,
    val status: JobbsøkerStatus,
    val aktuellForTreffStatus: AktuellForTreffStatus = AktuellForTreffStatus.VURDERES,
    val hendelser: List<JobbsøkerHendelse> = emptyList(),
    val alder: Int? = null,
    val innsatsgruppe: Innsatsgruppe? = null,
    val sperret: Boolean = false,
) {
    fun harAktivtSvarJa(): Boolean =
        status == JobbsøkerStatus.SVART_JA

    fun erInvitert(): Boolean =
        hendelser.any { it.hendelsestype == JobbsøkerHendelsestype.INVITERT }

    fun harSvart(): Boolean = gjeldendeSvar() != null

    fun harSvartJa(): Boolean = gjeldendeSvar() == true

    fun gjeldendeSvar(): Boolean? = when (status) {
        JobbsøkerStatus.SVART_JA -> true
        JobbsøkerStatus.SVART_NEI -> false
        JobbsøkerStatus.MØTT_OPP, JobbsøkerStatus.FÅTT_JOBB ->
            hendelser.filter { it.hendelsestype in SVARHENDELSER }
                .maxByOrNull { it.tidspunkt }
                ?.hendelsestype
                ?.let(::svarFraHendelse)
        else -> null
    }

    private companion object {
        val SVARHENDELSER = setOf(
            JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON,
            JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON_AV_EIER,
            JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON,
            JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON_AV_EIER,
            JobbsøkerHendelsestype.SVAR_FJERNET_AV_EIER,
        )

        fun svarFraHendelse(hendelsestype: JobbsøkerHendelsestype): Boolean? = when (hendelsestype) {
            JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON,
            JobbsøkerHendelsestype.SVART_JA_TIL_INVITASJON_AV_EIER -> true
            JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON,
            JobbsøkerHendelsestype.SVART_NEI_TIL_INVITASJON_AV_EIER -> false
            else -> null
        }
    }
}

data class PersonTreffId(private val id: UUID) {
    constructor(uuid: String) : this(UUID.fromString(uuid))

    val somUuid = id
    val somString = id.toString()
    override fun toString() = somString
}
