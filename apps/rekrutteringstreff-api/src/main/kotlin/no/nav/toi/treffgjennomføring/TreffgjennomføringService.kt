package no.nav.toi.treffgjennomføring

import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.HendelseWriter
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.låsTreff
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomføring.dto.ArbeidsgiverIntervjufordelingDto
import no.nav.toi.treffgjennomføring.dto.InteresseRequestDto
import no.nav.toi.treffgjennomføring.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomføring.dto.RomDto
import no.nav.toi.treffgjennomføring.dto.TreffgjennomføringDto
import java.sql.Connection
import javax.sql.DataSource

class TreffgjennomføringService(
    private val dataSource: DataSource,
    private val kontekstRepository: TreffkontekstRepository,
    private val repository: TreffgjennomføringRepository,
    private val reader: TreffgjennomføringReader,
    private val hendelseWriter: HendelseWriter,
) {

    fun hent(treffId: TreffId): TreffgjennomføringDto = dataSource.executeInTransaction { connection ->
        val kontekst = hentKontekst(connection, treffId)
        reader.les(connection, kontekst)
    }

    fun lagreMøteoppsett(treffId: TreffId, dto: MøteoppsettRequestDto, navIdent: String): TreffgjennomføringDto =
        skriv(treffId) { connection, kontekst, rad ->
            krevWorkOp(kontekst)
            val møteoppsett = TreffgjennomføringValidering.møteoppsett(dto)
            val aggregat = repository.hentAggregat(connection, kontekst)
            repository.lagreMøteoppsett(connection, rad.id, møteoppsett)

            if (aggregat.rom.isNotEmpty()) {
                hendelseWriter.forTreff(
                    connection, treffId, RekrutteringstreffHendelsestype.TREFFGJENNOMFØRING_OPPSETT_ENDRET, navIdent,
                    mapOf(
                        "starttidspunkt" to dto.starttidspunkt,
                        "varighetPerMøteMinutter" to dto.varighetPerMøteMinutter,
                    ),
                )
                return@skriv
            }

            opprettMøteplan(connection, kontekst, aggregat, dto, rad, navIdent)
        }

    private fun opprettMøteplan(
        connection: Connection,
        kontekst: Treffkontekst,
        aggregat: Treffgjennomføring,
        dto: MøteoppsettRequestDto,
        rad: Treffgjennomføringsrad,
        navIdent: String,
    ) {
        if (aggregat.oppmøte.isEmpty()) throw BadRequestResponse("Minst én jobbsøker må være registrert møtt")
        if (kontekst.arbeidsgivere.isEmpty()) throw BadRequestResponse("Treffet må ha minst én arbeidsgiver")

        val rom = Romfordeler.fordelJevnt(aggregat.oppmøte, kontekst.antallRom)
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
                "antallFremmøtte" to aggregat.oppmøte.size,
            ),
        )
        repository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.ROM)
    }

    fun lagreRomfordeling(treffId: TreffId, rom: List<RomDto>, navIdent: String): TreffgjennomføringDto =
        skriv(treffId) { connection, kontekst, _ ->
            krevWorkOp(kontekst)
            val aggregat = repository.hentAggregat(connection, kontekst)
            val ny = TreffgjennomføringValidering.romfordeling(rom, kontekst.antallRom, aggregat.oppmøte)

            repository.erstattRomfordeling(connection, kontekst.treffDbId, ny, kontekst)
            skrivRomhendelser(connection, aggregat.rom, ny, navIdent)
        }

    private fun skrivRomhendelser(
        connection: Connection,
        før: List<Rom>,
        etter: List<Rom>,
        navIdent: String,
    ) {
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

    fun settInteresse(treffId: TreffId, dto: InteresseRequestDto, navIdent: String): TreffgjennomføringDto =
        skriv(treffId) { connection, kontekst, rad ->
            val person = PersonTreffId(dto.personTreffId)
            val arbeidsgiver = ArbeidsgiverTreffId(dto.arbeidsgiverTreffId)
            val jobbsøkerId = kontekst.jobbsøkerId(person)
                ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")
            val arbeidsgiverId = kontekst.arbeidsgiverId(arbeidsgiver)
                ?: throw BadRequestResponse("Arbeidsgiveren finnes ikke på treffet")

            val aggregat = repository.hentAggregat(connection, kontekst)
            if (dto.interessert && person !in aggregat.oppmøte) {
                throw BadRequestResponse("Bare fremmøtte jobbsøkere kan registrere interesse")
            }

            if (!repository.settInteresse(connection, jobbsøkerId, arbeidsgiverId, dto.interessert)) return@skriv

            hendelseWriter.forJobbsøkerOgArbeidsgiver(
                connection, person, arbeidsgiver,
                if (dto.interessert) JobbsøkerHendelsestype.INTERESSE_REGISTRERT
                else JobbsøkerHendelsestype.ANGRE_INTERESSE_REGISTRERT,
                if (dto.interessert) ArbeidsgiverHendelsestype.INTERESSE_REGISTRERT
                else ArbeidsgiverHendelsestype.ANGRE_INTERESSE_REGISTRERT,
                navIdent,
            )

            speilInteresseIFordeling(connection, kontekst, aggregat, person, arbeidsgiver, dto.interessert)
            repository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.INTERESSE)
        }

    private fun speilInteresseIFordeling(
        connection: Connection,
        kontekst: Treffkontekst,
        aggregat: Treffgjennomføring,
        person: PersonTreffId,
        arbeidsgiver: ArbeidsgiverTreffId,
        interessert: Boolean,
    ) {
        val eksisterende = aggregat.intervjufordelinger.firstOrNull { it.arbeidsgiverTreffId == arbeidsgiver }
            ?: return

        val oppdatert = if (interessert) {
            if (person in eksisterende.inkludertePersonTreffIder || person in eksisterende.ekskludertePersonTreffIder) return
            eksisterende.copy(inkludertePersonTreffIder = eksisterende.inkludertePersonTreffIder + person)
        } else {
            eksisterende.copy(
                inkludertePersonTreffIder = eksisterende.inkludertePersonTreffIder - person,
                ekskludertePersonTreffIder = eksisterende.ekskludertePersonTreffIder - person,
            )
        }
        repository.erstattIntervjufordelinger(connection, listOf(oppdatert), kontekst)
    }

    fun lagreIntervjufordeling(
        treffId: TreffId,
        dto: ArbeidsgiverIntervjufordelingDto,
        navIdent: String,
    ): TreffgjennomføringDto = skriv(treffId) { connection, kontekst, rad ->
        krevWorkOp(kontekst)
        TreffgjennomføringValidering.intervjufordeling(dto.inkludertePersonTreffIder, dto.ekskludertePersonTreffIder)

        val arbeidsgiver = ArbeidsgiverTreffId(dto.arbeidsgiverTreffId)
        if (!kontekst.kjenner(arbeidsgiver)) throw BadRequestResponse("Arbeidsgiveren finnes ikke på treffet")

        val ny = ArbeidsgiverIntervjufordeling(
            arbeidsgiverTreffId = arbeidsgiver,
            inkludertePersonTreffIder = dto.inkludertePersonTreffIder.map(::PersonTreffId).krevPåTreff(kontekst),
            ekskludertePersonTreffIder = dto.ekskludertePersonTreffIder.map(::PersonTreffId).krevPåTreff(kontekst),
        )
        val før = repository.hentAggregat(connection, kontekst).intervjufordelinger
            .firstOrNull { it.arbeidsgiverTreffId == arbeidsgiver }

        repository.erstattIntervjufordelinger(connection, listOf(ny), kontekst)
        skrivFordelingshendelser(connection, før, ny, navIdent)
        repository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.FORDELING)
    }

    private fun List<PersonTreffId>.krevPåTreff(kontekst: Treffkontekst): List<PersonTreffId> = also {
        firstOrNull { !kontekst.kjenner(it) }?.let {
            throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")
        }
    }

    private fun skrivFordelingshendelser(
        connection: Connection,
        før: ArbeidsgiverIntervjufordeling?,
        etter: ArbeidsgiverIntervjufordeling,
        navIdent: String,
    ) {
        val inkludertFør = før?.inkludertePersonTreffIder.orEmpty().toSet()
        val inkludertEtter = etter.inkludertePersonTreffIder.toSet()

        (inkludertEtter - inkludertFør).forEach { person ->
            hendelseWriter.forJobbsøkerOgArbeidsgiver(
                connection, person, etter.arbeidsgiverTreffId,
                JobbsøkerHendelsestype.SATT_OPP_TIL_INTERVJU,
                ArbeidsgiverHendelsestype.SATT_OPP_TIL_INTERVJU, navIdent,
            )
        }
        (inkludertFør - inkludertEtter).forEach { person ->
            hendelseWriter.forJobbsøkerOgArbeidsgiver(
                connection, person, etter.arbeidsgiverTreffId,
                JobbsøkerHendelsestype.ANGRE_SATT_OPP_TIL_INTERVJU,
                ArbeidsgiverHendelsestype.ANGRE_SATT_OPP_TIL_INTERVJU, navIdent,
            )
        }
    }

    fun fordelIntervjuer(treffId: TreffId, navIdent: String): TreffgjennomføringDto =
        skriv(treffId) { connection, kontekst, rad ->
            krevWorkOp(kontekst)
            val aggregat = repository.hentAggregat(connection, kontekst)
            val fordelinger = Intervjufordeler.fordel(
                interesser = aggregat.interesser,
                eksisterendeFordelinger = aggregat.intervjufordelinger,
                arbeidsgivere = kontekst.arbeidsgiverIder,
            )
            repository.erstattIntervjufordelinger(connection, fordelinger, kontekst)

            hendelseWriter.forTreff(
                connection, treffId,
                RekrutteringstreffHendelsestype.TREFFGJENNOMFØRING_INTERVJUFORDELING_FORDELT, navIdent,
                mapOf(
                    "antallArbeidsgivere" to fordelinger.size,
                    "antallPlasseringer" to fordelinger.sumOf { it.inkludertePersonTreffIder.size },
                ),
            )
            repository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.FORDELING)
        }

    private fun hentKontekst(connection: Connection, treffId: TreffId): Treffkontekst =
        kontekstRepository.hent(connection, treffId)
            ?: throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somString} finnes ikke")

    /**
     * Hendelsene skrives på samme connection som operasjonen de hører til, og alt
     * kalles fra [skriv]. Da kan ikke en registrering bli stående uten hendelse,
     * eller motsatt, om noe feiler underveis.
     *
     * Aktøren er alltid den som klikket, også når noe registreres på vegne av en
     * arbeidsgiver. Ingen personopplysninger i hendelsedata — bare ID-er og enkle verdier.
     */
    private fun skriv(
        treffId: TreffId,
        block: (Connection, Treffkontekst, Treffgjennomføringsrad) -> Unit,
    ): TreffgjennomføringDto = dataSource.executeInTransaction { connection ->
        val kontekst = hentKontekst(connection, treffId)
        connection.låsTreff(kontekst.treffDbId)
        val rad = repository.sikreRad(connection, kontekst.treffDbId)
        block(connection, kontekst, rad)
        reader.les(connection, kontekst)
    }
}
