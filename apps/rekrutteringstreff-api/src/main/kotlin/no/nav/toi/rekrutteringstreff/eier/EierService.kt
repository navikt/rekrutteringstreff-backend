package no.nav.toi.rekrutteringstreff.eier

import io.javalin.http.BadRequestResponse
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.authenticatedUser
import no.nav.toi.executeInTransaction
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.eier.Eier.Companion.tilNavIdenter
import javax.sql.DataSource

class EierService(
    private val eierRepository: EierRepository,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val dataSource: DataSource,
) {
    fun erEierEllerUtvikler(treffId: TreffId, navIdent: String, context: Context): Boolean {
        val eiere = eierRepository.hent(treffId)?.tilNavIdenter()
            ?: throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")

        return context.authenticatedUser().erUtvikler() || eiere.contains(navIdent)
    }

    fun leggTilMegSomEier(treffId: TreffId, navIdent: String): Boolean {
        return dataSource.executeInTransaction { connection ->
            val eiere = eierRepository.hent(connection, treffId)?.tilNavIdenter()
                ?: throw IllegalStateException("Rekrutteringstreff med id ${treffId.somString} har ingen eiere")

            if (eiere.contains(navIdent)) return@executeInTransaction false

            eierRepository.leggTil(connection, treffId, listOf(navIdent))
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, treffId, RekrutteringstreffHendelsestype.EIER_LAGT_TIL, navIdent,
                subjektId = navIdent, subjektNavn = navIdent,
            )
            true
        }
    }

    fun leggTilEiere(treffId: TreffId, nyeEiere: List<String>, navIdent: String) {
        dataSource.executeInTransaction { connection ->
            eierRepository.leggTil(connection, treffId, nyeEiere)
            nyeEiere.forEach { eierIdent ->
                rekrutteringstreffRepository.leggTilHendelseForTreff(
                    connection, treffId, RekrutteringstreffHendelsestype.EIER_LAGT_TIL, navIdent,
                    subjektId = eierIdent, subjektNavn = eierIdent,
                )
            }
        }
    }

    fun slettEier(treffId: TreffId, eierNavIdent: String) {
        val fjernet = eierRepository.slett(treffId, eierNavIdent)
        if (!fjernet) {
            throw BadRequestResponse("Kan ikke slette siste eier for rekrutteringstreff ${treffId.somString}")
        }
    }
}