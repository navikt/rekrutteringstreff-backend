package no.nav.toi.jobbsoker

import no.nav.toi.JobbsøkerHendelsestype

enum class Oppmøte(val hendelsestype: JobbsøkerHendelsestype) {
    REGISTRERT_OPPMØTE(JobbsøkerHendelsestype.REGISTRERT_OPPMØTE),
    REGISTRERT_OPPMØTE_FJERNET(JobbsøkerHendelsestype.REGISTRERT_OPPMØTE_FJERNET);

    val harMøtt: Boolean get() = this == REGISTRERT_OPPMØTE

    companion object {
        fun fraDatabase(verdi: String?): Oppmøte? = entries.firstOrNull { it.name == verdi }

        fun harMøtt(verdi: String?): Boolean = fraDatabase(verdi)?.harMøtt == true
    }
}
