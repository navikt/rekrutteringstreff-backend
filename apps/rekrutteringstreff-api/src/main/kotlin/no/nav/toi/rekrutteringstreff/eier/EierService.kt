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
import java.sql.Connection
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

    fun leggTilEierMedKontor(connection: Connection, treffId: TreffId, navIdent: String, kontorEnhetId: String? = null) {
        val eiere = eierRepository.hent(connection, treffId, forUpdate = true)?.tilNavIdenter()
            ?: throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")
        if (eiere.contains(navIdent)) return

        eierRepository.leggTil(connection, treffId, listOf(navIdent))
        rekrutteringstreffRepository.leggTilHendelseForTreff(
            connection, treffId, RekrutteringstreffHendelsestype.EIER_LAGT_TIL, navIdent,
            subjektId = navIdent, subjektNavn = navIdent,
        )

        if (kontorEnhetId != null) {
            val nyttKontor = rekrutteringstreffRepository.leggTilKontor(connection, treffId, kontorEnhetId)
            if (nyttKontor) {
                rekrutteringstreffRepository.leggTilHendelseForTreff(
                    connection, treffId, RekrutteringstreffHendelsestype.KONTOR_LAGT_TIL, navIdent,
                    subjektId = kontorEnhetId, subjektNavn = kontorEnhetId,
                )
            }
        }
    }

    fun leggTilEierMedKontor(treffId: TreffId, navIdent: String, kontorEnhetId: String? = null) {
        dataSource.executeInTransaction { connection ->
            leggTilEierMedKontor(connection, treffId, navIdent, kontorEnhetId)
        }
    }

    fun slettEier(treffId: TreffId, eierNavIdent: String, utførtAv: String) {
        dataSource.executeInTransaction { connection ->
            val eiere = eierRepository.hent(connection, treffId, forUpdate = true)?.tilNavIdenter()
                ?: throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")
            if (!eiere.contains(eierNavIdent)) {
                throw NotFoundResponse("Eier med navIdent $eierNavIdent finnes ikke for rekrutteringstreff ${treffId.somString}")
            }
            if (eiere.size <= 1) {
                throw BadRequestResponse("Kan ikke slette siste eier for rekrutteringstreff ${treffId.somString}")
            }
            eierRepository.slett(connection, treffId, eierNavIdent)
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, treffId, RekrutteringstreffHendelsestype.EIER_FJERNET, utførtAv,
                subjektId = eierNavIdent, subjektNavn = eierNavIdent,
            )
        }
    }
}