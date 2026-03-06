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
    fun hentEiere(treffId: TreffId): List<Eier> {
        return eierRepository.hent(treffId)
            ?: throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")
    }

    fun erEierEllerUtvikler(treffId: TreffId, navIdent: String, context: Context): Boolean {
        val eiere = hentEiere(treffId).tilNavIdenter()
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

    fun slettEier(treffId: TreffId, eierNavIdent: String, utførtAv: String) {
        dataSource.executeInTransaction { connection ->
            val fjernet = eierRepository.slett(connection, treffId, eierNavIdent)
            if (!fjernet) {
                throw BadRequestResponse("Kan ikke slette siste eier for rekrutteringstreff ${treffId.somString}")
            }
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, treffId, RekrutteringstreffHendelsestype.EIER_FJERNET, utførtAv,
                subjektId = eierNavIdent, subjektNavn = eierNavIdent,
            )
        }
    }
}