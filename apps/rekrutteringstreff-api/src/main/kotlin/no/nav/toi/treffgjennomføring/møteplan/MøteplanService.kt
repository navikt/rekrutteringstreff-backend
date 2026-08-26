package no.nav.toi.treffgjennomføring.møteplan

import io.javalin.http.BadRequestResponse
import no.nav.toi.HendelseWriter
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.StegRepository
import no.nav.toi.treffgjennomføring.TreffgjennomføringWriter
import no.nav.toi.treffgjennomføring.TreffgjennomføringSteg
import no.nav.toi.treffgjennomføring.Treffkontekst
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomføring.dto.RomDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import java.sql.Connection

class MøteplanService(
    private val writer: TreffgjennomføringWriter,
    private val repository: MøteplanRepository,
    private val oppmøteRepository: OppmøteRepository,
    private val stegRepository: StegRepository,
    private val hendelseWriter: HendelseWriter,
) {

    fun lagreMøteoppsett(treffId: TreffId, dto: MøteoppsettRequestDto, navIdent: String): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, rad ->
            kontekst.krevWorkOp()
            val møteoppsett = MøteplanValidering.møteoppsett(dto)
            val erEndring = repository.harMøteoppsett(connection, rad.id)
            repository.lagreMøteoppsett(connection, rad.id, møteoppsett)

            if (erEndring) {
                hendelseWriter.forTreff(
                    connection, treffId, RekrutteringstreffHendelsestype.TREFFGJENNOMFØRING_OPPSETT_ENDRET, navIdent,
                    mapOf(
                        "starttidspunkt" to dto.starttidspunkt,
                        "varighetPerMøteMinutter" to dto.varighetPerMøteMinutter,
                    ),
                )
                return@skriv
            }

            opprettMøteplan(connection, kontekst, dto, rad.gjeldendeSteg, navIdent)
        }

    private fun opprettMøteplan(
        connection: Connection,
        kontekst: Treffkontekst,
        dto: MøteoppsettRequestDto,
        nåværendeSteg: TreffgjennomføringSteg,
        navIdent: String,
    ) {
        val oppmøte = oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)
        if (oppmøte.isEmpty()) throw BadRequestResponse("Minst én jobbsøker må være registrert møtt")
        if (kontekst.arbeidsgivere.isEmpty()) throw BadRequestResponse("Treffet må ha minst én arbeidsgiver")

        val rom = Romfordeler.fordelJevnt(oppmøte, kontekst.antallRom)
        repository.erstattRomfordeling(connection, kontekst.treffDbId, rom, kontekst)

        val rotasjon = kontekst.arbeidsgiverTreffIder.mapIndexed { indeks, arbeidsgiver ->
            ArbeidsgiverRotasjon(arbeidsgiver, indeks + 1)
        }
        repository.lagreArbeidsgiverRotasjon(connection, rotasjon, kontekst)

        hendelseWriter.forTreff(
            connection, kontekst.treffId, RekrutteringstreffHendelsestype.TREFFGJENNOMFØRING_OPPRETTET, navIdent,
            mapOf(
                "antallRom" to kontekst.antallRom,
                "starttidspunkt" to dto.starttidspunkt,
                "varighetPerMøteMinutter" to dto.varighetPerMøteMinutter,
                "antallFremmøtte" to oppmøte.size,
            ),
        )
        stegRepository.settGjeldendeSteg(connection, kontekst.treffDbId, nåværendeSteg, TreffgjennomføringSteg.ROM)
    }

    fun lagreRomfordeling(treffId: TreffId, rom: List<RomDto>): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, _ ->
            kontekst.krevWorkOp()
            val oppmøte = oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)
            val ny = MøteplanValidering.romfordeling(rom, kontekst.antallRom, oppmøte)

            repository.erstattRomfordeling(connection, kontekst.treffDbId, ny, kontekst)
        }

    fun fordelRomPåNytt(treffId: TreffId): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, _ ->
            kontekst.krevWorkOp()
            val oppmøte = oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)
            val rom = Romfordeler.fordelJevnt(oppmøte, kontekst.antallRom)
            repository.erstattRomfordeling(connection, kontekst.treffDbId, rom, kontekst)
        }
}
