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

    /**
     * Sjekker om innlogget bruker har tilgang til treffet via et av kontorene
     * treffet er tilknyttet.
     *
     * Brukes for arbeidsgiverrettet tilgang til formidlingslisten — gir
     * alt-eller-ingenting-tilgang basert på treffets kontorer.
     */
    fun harTilgangViaTreffkontor(treffId: TreffId, tilknyttedeEnheter: List<String>): Boolean {
        if (tilknyttedeEnheter.isEmpty()) return false
        val treff = rekrutteringstreffRepository.hent(treffId) ?: return false
        val tilknyttedeEnheterSet = tilknyttedeEnheter.toSet()
        return treff.kontorer.any { it in tilknyttedeEnheterSet }
    }

    fun leggTilEierMedKontor(connection: Connection, treffId: TreffId, navIdent: String, kontorEnhetId: String, eierNavn: String? = null, kontorNavn: String? = null) {
        require(kontorEnhetId.isNotBlank()) { "Eier må ha kontortilknytning" }
        val eiere = eierRepository.hent(connection, treffId, forUpdate = true)
            ?: throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")

        eierRepository.leggTil(connection, treffId, navIdent, kontorEnhetId, eierNavn)
        if (navIdent !in eiere.tilNavIdenter()) {
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, treffId, RekrutteringstreffHendelsestype.EIER_LAGT_TIL, navIdent,
                subjektId = navIdent, subjektNavn = navIdent,
            )
        }

        oppdaterKontorerOgHendelser(connection, treffId, eiere, navIdent, kontorNavn = kontorNavn)
    }

    fun leggTilEierMedKontor(treffId: TreffId, navIdent: String, kontorEnhetId: String, eierNavn: String? = null, kontorNavn: String? = null) {
        dataSource.executeInTransaction { connection ->
            leggTilEierMedKontor(connection, treffId, navIdent, kontorEnhetId, eierNavn, kontorNavn)
        }
    }

    fun slettEier(treffId: TreffId, eierNavIdent: String, utførtAv: String, kontorNavn: String? = null) {
        dataSource.executeInTransaction { connection ->
            val eiere = eierRepository.hent(connection, treffId, forUpdate = true)
                ?: throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")
            if (eierNavIdent !in eiere.tilNavIdenter()) {
                throw NotFoundResponse("Eier med navIdent $eierNavIdent finnes ikke for rekrutteringstreff ${treffId.somString}")
            }
            if (eiere.size <= 1) {
                throw BadRequestResponse("Kan ikke slette siste eier for rekrutteringstreff ${treffId.somString}")
            }
            check(eierRepository.slett(connection, treffId, eierNavIdent)) {
                "Kunne ikke slette eier for rekrutteringstreff ${treffId.somString}"
            }
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, treffId, RekrutteringstreffHendelsestype.EIER_FJERNET, utførtAv,
                subjektId = eierNavIdent, subjektNavn = eierNavIdent,
            )
            oppdaterKontorerOgHendelser(connection, treffId, eiere, utførtAv, kontorNavn = kontorNavn)
        }
    }

    private fun oppdaterKontorerOgHendelser(
        connection: Connection,
        treffId: TreffId,
        eiereFør: List<Eier>,
        utførtAv: String,
        kontorNavn: String? = null,
    ) {
        val eiereEtter = eierRepository.hent(connection, treffId)
            ?: throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")
        val kontorerFør = eiereFør.map { it.kontorEnhetId }.toSet()
        val kontorerEtter = eiereEtter.map { it.kontorEnhetId }.toSet()
        rekrutteringstreffRepository.oppdaterKontorer(connection, treffId)
        (kontorerEtter - kontorerFør).forEach { kontor ->
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, treffId, RekrutteringstreffHendelsestype.KONTOR_LAGT_TIL, utførtAv,
                subjektId = kontor, subjektNavn = kontorNavn ?: kontor,
            )
        }
        (kontorerFør - kontorerEtter).forEach { kontor ->
            rekrutteringstreffRepository.leggTilHendelseForTreff(
                connection, treffId, RekrutteringstreffHendelsestype.KONTOR_FJERNET, utførtAv,
                subjektId = kontor, subjektNavn = kontorNavn ?: kontor,
            )
        }
    }
}