package no.nav.toi.treffgjennomføring.møteplan

import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.TreffkontekstRepository
import no.nav.toi.treffgjennomføring.krevKontekst
import java.sql.Connection

/**
 * Holder lagret romfordeling og rotasjon i takt når fremmøtte eller arbeidsgivere endres.
 *
 * Plasseringer for nye fremmøtte og nye arbeidsgivere beregnes ved lesing. Lagres møteplanen både
 * før og etter en endring, blir beregnede plasseringer faste. Da bytter ingen rom fordi antall rom
 * eller fremmøtte endret seg. Treff uten møteplan hoppes over etter én eksistenssjekk.
 */
class Møteplansynk(
    private val kontekstRepository: TreffkontekstRepository,
    private val møteplanRepository: MøteplanRepository,
    private val oppmøteRepository: OppmøteRepository,
) {

    fun <T> medLagretMøteplan(connection: Connection, treffId: TreffId, endring: () -> T): T {
        if (!møteplanRepository.harMøteplan(connection, treffId)) return endring()
        lagre(connection, treffId)
        return endring().also { lagre(connection, treffId) }
    }

    /** For endringer som rydder møteplanen selv etterpå, som sletting av en arbeidsgiver. */
    fun lagreFørEndring(connection: Connection, treffId: TreffId) {
        if (møteplanRepository.harMøteplan(connection, treffId)) lagre(connection, treffId)
    }

    private fun lagre(connection: Connection, treffId: TreffId) {
        val kontekst = kontekstRepository.krevKontekst(connection, treffId)
        val oppmøte = oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)
        val møteplan = møteplanRepository.hentMøteplan(connection, kontekst, oppmøte)
        møteplanRepository.lagreMøteplan(connection, kontekst, møteplan)
    }
}
