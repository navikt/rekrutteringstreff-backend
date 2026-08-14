package no.nav.toi.treffgjennomføring.møteplan

import io.javalin.http.BadRequestResponse
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.HendelseWriter
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.FaseRepository
import no.nav.toi.treffgjennomføring.TreffgjennomføringWriter
import no.nav.toi.treffgjennomføring.TreffgjennomføringFase
import no.nav.toi.treffgjennomføring.Treffkontekst
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomføring.dto.RomDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import java.sql.Connection

class MøteplanService(
    private val writer: TreffgjennomføringWriter,
    private val repository: MøteplanRepository,
    private val oppmøteRepository: OppmøteRepository,
    private val faseRepository: FaseRepository,
    private val hendelseWriter: HendelseWriter,
) {

    fun lagreMøteoppsett(treffId: TreffId, dto: MøteoppsettRequestDto, navIdent: String): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, rad ->
            krevWorkOp(kontekst)
            val møteoppsett = MøteplanValidering.møteoppsett(dto)
            val oppmøte = oppmøteRepository.hentFremmøtte(connection, kontekst.treffDbId)
            val eksisterende = repository.hentFor(connection, kontekst, oppmøte)
            repository.lagreMøteoppsett(connection, rad.id, møteoppsett)

            if (eksisterende.rom.isNotEmpty()) {
                hendelseWriter.forTreff(
                    connection, treffId, RekrutteringstreffHendelsestype.TREFFGJENNOMFØRING_OPPSETT_ENDRET, navIdent,
                    mapOf(
                        "starttidspunkt" to dto.starttidspunkt,
                        "varighetPerMøteMinutter" to dto.varighetPerMøteMinutter,
                    ),
                )
                return@skriv
            }

            opprettMøteplan(connection, kontekst, oppmøte, dto, rad.fase, navIdent)
        }

    private fun opprettMøteplan(
        connection: Connection,
        kontekst: Treffkontekst,
        oppmøte: List<no.nav.toi.jobbsoker.PersonTreffId>,
        dto: MøteoppsettRequestDto,
        nåværendeFase: TreffgjennomføringFase,
        navIdent: String,
    ) {
        if (oppmøte.isEmpty()) throw BadRequestResponse("Minst én jobbsøker må være registrert møtt")
        if (kontekst.arbeidsgivere.isEmpty()) throw BadRequestResponse("Treffet må ha minst én arbeidsgiver")

        val rom = Romfordeler.fordelJevnt(oppmøte, kontekst.antallRom)
        repository.erstattRomfordeling(connection, kontekst.treffDbId, rom, kontekst)

        val rotasjon = kontekst.arbeidsgiverIder.mapIndexed { indeks, arbeidsgiver ->
            ArbeidsgiverRotasjon(arbeidsgiver, indeks)
        }
        repository.lagreRotasjon(connection, rotasjon, kontekst)
        rotasjon.forEach {
            hendelseWriter.forArbeidsgiver(
                connection, it.arbeidsgiverTreffId, ArbeidsgiverHendelsestype.ROTASJON_TILDELT, navIdent,
                mapOf("startPosisjon" to it.startPosisjon),
            )
        }

        hendelseWriter.forTreff(
            connection, kontekst.treffId, RekrutteringstreffHendelsestype.TREFFGJENNOMFØRING_OPPRETTET, navIdent,
            mapOf(
                "antallRom" to kontekst.antallRom,
                "starttidspunkt" to dto.starttidspunkt,
                "varighetPerMøteMinutter" to dto.varighetPerMøteMinutter,
                "antallFremmøtte" to oppmøte.size,
            ),
        )
        faseRepository.settFase(connection, kontekst.treffDbId, nåværendeFase, TreffgjennomføringFase.ROM)
    }

    fun lagreRomfordeling(treffId: TreffId, rom: List<RomDto>, navIdent: String): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, _ ->
            krevWorkOp(kontekst)
            val oppmøte = oppmøteRepository.hentFremmøtte(connection, kontekst.treffDbId)
            val eksisterende = repository.hentFor(connection, kontekst, oppmøte)
            val ny = MøteplanValidering.romfordeling(rom, kontekst.antallRom, oppmøte)

            repository.erstattRomfordeling(connection, kontekst.treffDbId, ny, kontekst)
            skrivRomhendelser(connection, eksisterende.rom, ny, navIdent)
        }

    private fun skrivRomhendelser(connection: Connection, før: List<Rom>, etter: List<Rom>, navIdent: String) {
        val tidligere = før.flatMap { rom -> rom.jobbsøkere.map { it to rom.romnummer } }.toMap()
        etter.forEach { rom ->
            rom.jobbsøkere.forEach { person ->
                val forrige = tidligere[person]
                if (forrige == rom.romnummer) return@forEach
                hendelseWriter.forJobbsøker(
                    connection, person, JobbsøkerHendelsestype.PLASSERT_I_ROM, navIdent,
                    mapOf("romnummer" to rom.romnummer, "forrigeRomnummer" to forrige),
                )
            }
        }
    }

    private fun krevWorkOp(kontekst: Treffkontekst) {
        if (!kontekst.erWorkOp) throw BadRequestResponse("Steget finnes bare på treff av kategorien WORKOP")
    }
}
