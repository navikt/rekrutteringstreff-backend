package no.nav.toi.treffgjennomføring

import no.nav.toi.Miljø
import no.nav.toi.executeInReadOnlyTransaction
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.GjennomføringJobbsøkereRequestDto
import no.nav.toi.treffgjennomføring.dto.GjennomføringJobbsøkersideDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import javax.sql.DataSource

class TreffgjennomføringService(
    private val dataSource: DataSource,
    private val kontekstRepository: TreffkontekstRepository,
    private val reader: TreffgjennomføringReader,
    private val writer: TreffgjennomføringWriter,
    private val stegRepository: StegRepository,
    private val miljø: Miljø,
    private val jobbsøkerRepository: GjennomføringJobbsøkerRepository = GjennomføringJobbsøkerRepository(),
) {

    fun hent(treffId: TreffId): TreffgjennomføringDto = dataSource.executeInReadOnlyTransaction { connection ->
        val kontekst = kontekstRepository.krevKontekst(connection, treffId)
        kontekst.krevWorkOpEllerLokalUtvikling(miljø)
        reader.les(connection, kontekst)
    }

    fun hentJobbsøkere(treffId: TreffId, request: GjennomføringJobbsøkereRequestDto): GjennomføringJobbsøkersideDto =
        dataSource.executeInReadOnlyTransaction { connection ->
            kontekstRepository.krevKontekst(connection, treffId).krevWorkOpEllerLokalUtvikling(miljø)
            jobbsøkerRepository.hentSide(connection, treffId, request)
        }

    fun settGjeldendeSteg(treffId: TreffId, steg: TreffgjennomføringSteg): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, _, rad ->
            stegRepository.flyttFramTil(connection, rad, steg)
        }
}
