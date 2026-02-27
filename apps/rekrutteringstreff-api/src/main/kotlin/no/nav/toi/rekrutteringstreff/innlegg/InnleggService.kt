package no.nav.toi.rekrutteringstreff.innlegg

import no.nav.toi.exception.RekrutteringstreffIkkeFunnetException
import no.nav.toi.log
import no.nav.toi.rekrutteringstreff.RekrutteringstreffService
import no.nav.toi.rekrutteringstreff.RekrutteringstreffStatus
import no.nav.toi.rekrutteringstreff.TreffId
import org.slf4j.Logger
import java.util.UUID

class InnleggService(
    private val innleggRepository: InnleggRepository,
    private val rekrutteringstreffService: RekrutteringstreffService,
) {
    private val logger: Logger = log

    fun opprettInnlegg(treffId: TreffId, dto: OpprettInnleggRequestDto, navIdent: String): Innlegg {
        val rekrutteringstreff = rekrutteringstreffService.hentRekrutteringstreff(treffId)
            ?: throw RekrutteringstreffIkkeFunnetException("Rekrutteringstreff med id $treffId finnes ikke")

        // Vi har hatt flere feil i frontend som har gjort at samme innlegg har blitt opprettet flere ganger.
        // For å unngå dette, oppdaterer vi eksisterende innlegg når treffet er i status UTKAST.
        if (rekrutteringstreff.status == RekrutteringstreffStatus.UTKAST) {
            val innleggForTreff = innleggRepository.hentForTreff(treffId)
            logger.info("Prøver å opprette innleff for treff $treffId som er i status UTKAST, og har ${innleggForTreff.size} innlegg")
            if (innleggForTreff.isNotEmpty()) {
                log.warn("Innlegg finnes allerede for treff $treffId - overskriver eksisterende innlegg")
                val eksisterendeInnlegg = innleggForTreff.first()
                innleggRepository.oppdater(eksisterendeInnlegg.id, treffId, dto.tilOppdaterInnleggRequestDto())
                logger.info("Oppdaterte innlegg med id ${eksisterendeInnlegg.id} for rekrutteringstreff med id $treffId")
            }
        }

        val innlegg = innleggRepository.opprett(treffId, dto, navIdent)
        logger.info("Opprettet innlegg med id ${innlegg.id} for rekrutteringstreff med id $treffId")
        return innlegg
    }

    fun hentById(innleggId: UUID): Innlegg? {
        return innleggRepository.hentById(innleggId)
    }

    fun hentForTreff(treffId: TreffId): List<Innlegg> {
        return innleggRepository.hentForTreff(treffId);
    }

    fun oppdater(innleggId: UUID, treffId: TreffId, dto: OppdaterInnleggRequestDto): Innlegg {
        return innleggRepository.oppdater(innleggId, treffId, dto);
    }

    fun slett(innleggId: UUID): Boolean {
        return innleggRepository.slett(innleggId);
    }
}
