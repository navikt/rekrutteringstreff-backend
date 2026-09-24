package no.nav.toi.treffgjennomføring.møteplan

import io.javalin.http.BadRequestResponse
import no.nav.toi.HendelseWriter
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.jobbsoker.oppmøte.OppmøteRepository
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.StegRepository
import no.nav.toi.treffgjennomføring.TreffgjennomføringSteg
import no.nav.toi.treffgjennomføring.TreffgjennomføringWriter
import no.nav.toi.treffgjennomføring.Treffgjennomføringsrad
import no.nav.toi.treffgjennomføring.Treffkontekst
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import java.sql.Connection
import java.time.format.DateTimeFormatter

class MøteplanService(
    private val writer: TreffgjennomføringWriter,
    private val repository: MøteplanRepository,
    private val oppmøteRepository: OppmøteRepository,
    private val stegRepository: StegRepository,
    private val hendelseWriter: HendelseWriter,
) {

    /** Første gang opprettes romfordeling og rotasjon. Senere endres bare tidene. */
    fun lagreMøteoppsett(treffId: TreffId, dto: MøteoppsettRequestDto, navIdent: String): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, rad ->
            kontekst.krevWorkOp()
            val nytt = MøteplanValidering.møteoppsett(dto)
            val lagret = repository.hentMøteoppsett(connection, kontekst.treffDbId)
            if (lagret == nytt) return@skriv

            repository.lagreMøteoppsett(connection, rad.id, nytt)
            if (lagret == null) {
                opprettMøteplan(connection, kontekst, rad, nytt, navIdent)
            } else {
                hendelseWriter.forTreff(
                    connection, treffId, RekrutteringstreffHendelsestype.TREFFGJENNOMFØRING_OPPSETT_ENDRET, navIdent,
                    nytt.somHendelsedata(),
                )
            }
        }

    private fun opprettMøteplan(
        connection: Connection,
        kontekst: Treffkontekst,
        rad: Treffgjennomføringsrad,
        møteoppsett: Møteoppsett,
        navIdent: String,
    ) {
        val oppmøte = oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)
        if (oppmøte.isEmpty()) throw BadRequestResponse("Minst én jobbsøker må være registrert møtt")
        if (kontekst.arbeidsgivere.isEmpty()) throw BadRequestResponse("Treffet må ha minst én arbeidsgiver")

        repository.erstattRomfordeling(connection, kontekst, Romfordeler.fordelJevnt(oppmøte, kontekst.antallRom))
        val rotasjon = kontekst.arbeidsgiverTreffIder.mapIndexed { indeks, arbeidsgiver ->
            ArbeidsgiverRotasjon(arbeidsgiver, førsteRomnummer = indeks + 1)
        }
        repository.lagreArbeidsgiverRotasjon(connection, rotasjon, kontekst)

        hendelseWriter.forTreff(
            connection, kontekst.treffId, RekrutteringstreffHendelsestype.TREFFGJENNOMFØRING_OPPRETTET, navIdent,
            mapOf("antallRom" to kontekst.antallRom) + møteoppsett.somHendelsedata() + mapOf("antallFremmøtte" to oppmøte.size),
        )
        stegRepository.flyttFramTil(connection, rad, TreffgjennomføringSteg.ROM)
    }

    /** Flytter én jobbsøker basert på fersk servertilstand, så samtidige flyttinger ikke overskriver hverandre. */
    fun flyttJobbsøkerTilRom(treffId: TreffId, personTreffId: PersonTreffId, målromnummer: Int): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, rad ->
            krevMøteoppsett(connection, kontekst, rad)
            if (målromnummer !in 1..kontekst.antallRom) {
                throw BadRequestResponse("Ugyldig romnummer: $målromnummer. Må være mellom 1 og ${kontekst.antallRom}")
            }
            kontekst.krevJobbsøkerId(personTreffId)
            val oppmøte = oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)
            if (personTreffId !in oppmøte) {
                throw BadRequestResponse("Bare fremmøtte jobbsøkere kan plasseres i rom")
            }

            val gjeldendeRom = repository.hentMøteplan(connection, kontekst, oppmøte).rom
            repository.erstattRomfordeling(connection, kontekst, Romfordeler.flytt(gjeldendeRom, personTreffId, målromnummer))
        }

    fun fordelRomPåNytt(treffId: TreffId): TreffgjennomføringDto =
        writer.skriv(treffId) { connection, kontekst, rad ->
            krevMøteoppsett(connection, kontekst, rad)
            val oppmøte = oppmøteRepository.hentFremmøtteJobbsøkere(connection, kontekst.treffDbId)
            repository.erstattRomfordeling(connection, kontekst, Romfordeler.fordelJevnt(oppmøte, kontekst.antallRom))
        }

    private fun krevMøteoppsett(connection: Connection, kontekst: Treffkontekst, rad: Treffgjennomføringsrad) {
        kontekst.krevWorkOp()
        if (!repository.harMøteoppsett(connection, rad.id)) {
            throw BadRequestResponse("Møteoppsettet må opprettes før romfordeling kan endres")
        }
    }

    private fun Møteoppsett.somHendelsedata(): Map<String, Any> = mapOf(
        "starttidspunkt" to starttidspunkt.format(KLOKKESLETT),
        "varighetPerMøteMinutter" to varighetPerMøteMinutter,
    )

    private companion object {
        val KLOKKESLETT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
