package no.nav.toi.rekrutteringstreff.eier

import io.javalin.http.Context
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
            ?: throw IllegalStateException("Rekrutteringstreff med id ${treffId.somString} har ingen eiere")

        return context.authenticatedUser().erUtvikler() || eiere.contains(navIdent)
    }

    fun leggTilMegSomEier(treffId: TreffId, navIdent: String): Boolean {
        val eiere = eierRepository.hent(treffId)?.tilNavIdenter()
            ?: throw IllegalStateException("Rekrutteringstreff med id ${treffId.somString} har ingen eiere")

        if (eiere.contains(navIdent)) return false

        val hendelseData = """{"navIdentLagtTil": "$navIdent"}"""
        dataSource.executeInTransaction { connection ->
            eierRepository.leggTil(connection, treffId, listOf(navIdent))
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, treffId, RekrutteringstreffHendelsestype.EIER_LAGT_TIL, navIdent, hendelseData
            )
        }
        return true
    }
}