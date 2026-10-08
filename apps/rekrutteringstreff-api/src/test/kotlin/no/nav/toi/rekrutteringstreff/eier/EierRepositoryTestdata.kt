package no.nav.toi.rekrutteringstreff.eier

import no.nav.toi.executeInTransaction
import no.nav.toi.rekrutteringstreff.TreffId

/**
 * Testdata uten lås og uten hendelser. Appen endrer eiere gjennom [EierService], som låser treffet.
 */
fun EierRepository.leggTil(treff: TreffId, eierNavIdent: String, kontorEnhetId: String, eierNavn: String? = null) =
    dataSource.executeInTransaction { connection -> leggTil(connection, treff, eierNavIdent, kontorEnhetId, eierNavn) }

fun EierRepository.slett(treff: TreffId, eier: String): Boolean =
    dataSource.executeInTransaction { connection -> slett(connection, treff, eier) }
