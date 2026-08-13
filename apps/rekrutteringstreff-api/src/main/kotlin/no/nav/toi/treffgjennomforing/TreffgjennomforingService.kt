package no.nav.toi.treffgjennomforing

import com.fasterxml.jackson.databind.ObjectMapper
import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import no.nav.toi.AktørType
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.treffgjennomforing.dto.ArbeidsgiverIntervjufordelingDto
import no.nav.toi.treffgjennomforing.dto.InteresseRequestDto
import no.nav.toi.treffgjennomforing.dto.MøteoppsettRequestDto
import no.nav.toi.treffgjennomforing.dto.OppmøteRequestDto
import no.nav.toi.treffgjennomforing.dto.RomDto
import no.nav.toi.treffgjennomforing.dto.TreffgjennomforingDto
import no.nav.toi.treffgjennomforing.dto.VurderingDto
import no.nav.toi.treffgjennomforing.dto.tilDto
import java.sql.Connection
import javax.sql.DataSource

class TreffgjennomforingService(
    private val dataSource: DataSource,
    private val kontekstRepository: TreffkontekstRepository,
    private val repository: TreffgjennomforingRepository,
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val arbeidsgiverRepository: ArbeidsgiverRepository,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val mapper: ObjectMapper,
) {

    /** Rent lesende. Finnes ingen lagret treffgjennomføring, er svaret tomtilstanden. */
    fun hent(treffId: TreffId): TreffgjennomforingDto = dataSource.executeInTransaction { connection ->
        val kontekst = hentKontekst(connection, treffId)
        repository.hentAggregat(connection, kontekst).tilDto(treffId.somString)
    }

    fun oppdaterOppmøte(treffId: TreffId, dto: OppmøteRequestDto, navIdent: String): TreffgjennomforingDto =
        skriv(treffId) { connection, kontekst, rad ->
            val person = PersonTreffId(dto.personTreffId)
            val jobbsøkerId = kontekst.jobbsøkerId(person)
                ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")
            val aggregat = repository.hentAggregat(connection, kontekst)

            if (dto.møtt == aggregat.oppmøte.contains(person)) return@skriv
            if (dto.møtt) registrerOppmøte(connection, kontekst, person, jobbsøkerId, navIdent)
            else fjernOppmøte(connection, person, jobbsøkerId, dto.bekreftSlettRegistreringer, navIdent)
        }

    private fun registrerOppmøte(
        connection: Connection,
        kontekst: Treffkontekst,
        person: PersonTreffId,
        jobbsøkerId: Long,
        navIdent: String,
    ) {

        val deltakernummer =
            if (kontekst.erWorkOp) repository.tildelDeltakernummer(connection, kontekst.treffDbId, jobbsøkerId)
            else null

        leggTilHendelseForJobbsøker(
            connection, person, JobbsøkerHendelsestype.MØTT_OPP, navIdent,
            deltakernummer?.let { mapOf("deltakernummer" to it) } ?: emptyMap(),
        )
    }

    private fun fjernOppmøte(
        connection: Connection,
        person: PersonTreffId,
        jobbsøkerId: Long,
        bekreftet: Boolean,
        navIdent: String,
    ) {
        val registreringer = repository.tellRegistreringer(connection, jobbsøkerId)
        if (registreringer.finnesNoen() && !bekreftet) throw OppmøteHarRegistreringerException(registreringer)

        repository.slettRegistreringerFor(connection, jobbsøkerId)
        leggTilHendelseForJobbsøker(
            connection, person, JobbsøkerHendelsestype.ANGRE_MØTT_OPP, navIdent,
            mapOf(
                "interesser" to registreringer.interesser,
                "intervjuplasser" to registreringer.intervjuplasser,
                "vurderinger" to registreringer.vurderinger,
            ),
        )
    }

    fun lagreMøteoppsett(treffId: TreffId, dto: MøteoppsettRequestDto, navIdent: String): TreffgjennomforingDto =
        skriv(treffId) { connection, kontekst, rad ->
            krevWorkOp(kontekst)
            val møteoppsett = TreffgjennomforingValidering.møteoppsett(dto)
            val aggregat = repository.hentAggregat(connection, kontekst)
            repository.lagreMøteoppsett(connection, rad.id, møteoppsett)

            if (aggregat.rom.isNotEmpty()) {
                leggTilHendelseForTreff(
                    connection, treffId, RekrutteringstreffHendelsestype.TREFFGJENNOMFORING_OPPSETT_ENDRET, navIdent,
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
        rad: Treffgjennomforingsrad,
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
            leggTilHendelseForArbeidsgiver(
                connection, it.arbeidsgiverTreffId, ArbeidsgiverHendelsestype.ROTASJON_TILDELT, navIdent,
                mapOf("startPosisjon" to it.startPosisjon),
            )
        }

        leggTilHendelseForTreff(
            connection, kontekst.treffId, RekrutteringstreffHendelsestype.TREFFGJENNOMFORING_OPPRETTET, navIdent,
            mapOf(
                "antallRom" to kontekst.antallRom,
                "starttidspunkt" to dto.starttidspunkt,
                "varighetPerMøteMinutter" to dto.varighetPerMøteMinutter,
                "antallFremmøtte" to aggregat.oppmøte.size,
            ),
        )
        repository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.ROM)
    }

    fun lagreRomfordeling(treffId: TreffId, rom: List<RomDto>, navIdent: String): TreffgjennomforingDto =
        skriv(treffId) { connection, kontekst, _ ->
            krevWorkOp(kontekst)
            val aggregat = repository.hentAggregat(connection, kontekst)
            val ny = TreffgjennomforingValidering.romfordeling(rom, kontekst.antallRom, aggregat.oppmøte)

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
                leggTilHendelseForJobbsøker(
                    connection, person, JobbsøkerHendelsestype.PLASSERT_I_ROM, navIdent,
                    mapOf("romnummer" to rom.romnummer, "forrigeRomnummer" to forrige),
                )
            }
        }
    }

    private fun krevWorkOp(kontekst: Treffkontekst) {
        if (!kontekst.erWorkOp) throw BadRequestResponse("Steget finnes bare på treff av kategorien WORKOP")
    }

    fun settInteresse(treffId: TreffId, dto: InteresseRequestDto, navIdent: String): TreffgjennomforingDto =
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

            leggTilHendelseForPar(
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
    ): TreffgjennomforingDto = skriv(treffId) { connection, kontekst, rad ->
        krevWorkOp(kontekst)
        TreffgjennomforingValidering.intervjufordeling(dto.inkludertePersonTreffIder, dto.ekskludertePersonTreffIder)

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
            leggTilHendelseForPar(
                connection, person, etter.arbeidsgiverTreffId,
                JobbsøkerHendelsestype.SATT_OPP_TIL_INTERVJU,
                ArbeidsgiverHendelsestype.SATT_OPP_TIL_INTERVJU, navIdent,
            )
        }
        (inkludertFør - inkludertEtter).forEach { person ->
            leggTilHendelseForPar(
                connection, person, etter.arbeidsgiverTreffId,
                JobbsøkerHendelsestype.ANGRE_SATT_OPP_TIL_INTERVJU,
                ArbeidsgiverHendelsestype.ANGRE_SATT_OPP_TIL_INTERVJU, navIdent,
            )
        }
    }

    fun fordelIntervjuer(treffId: TreffId, navIdent: String): TreffgjennomforingDto =
        skriv(treffId) { connection, kontekst, rad ->
            krevWorkOp(kontekst)
            val aggregat = repository.hentAggregat(connection, kontekst)
            val fordelinger = Intervjufordeler.fordel(
                interesser = aggregat.interesser,
                eksisterendeFordelinger = aggregat.intervjufordelinger,
                arbeidsgivere = kontekst.arbeidsgiverIder,
            )
            repository.erstattIntervjufordelinger(connection, fordelinger, kontekst)

            leggTilHendelseForTreff(
                connection, treffId,
                RekrutteringstreffHendelsestype.TREFFGJENNOMFORING_INTERVJUFORDELING_FORDELT, navIdent,
                mapOf(
                    "antallArbeidsgivere" to fordelinger.size,
                    "antallPlasseringer" to fordelinger.sumOf { it.inkludertePersonTreffIder.size },
                ),
            )
            repository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.FORDELING)
        }

    fun lagreVurdering(treffId: TreffId, dto: VurderingDto, navIdent: String): TreffgjennomforingDto =
        skriv(treffId) { connection, kontekst, rad ->
            val ny = TreffgjennomforingValidering.vurdering(dto)
            val jobbsøkerId = kontekst.jobbsøkerId(ny.personTreffId)
                ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")
            val arbeidsgiverId = kontekst.arbeidsgiverId(ny.arbeidsgiverTreffId)
                ?: throw BadRequestResponse("Arbeidsgiveren finnes ikke på treffet")

            val før = repository.hentAggregat(connection, kontekst).vurderinger.firstOrNull {
                it.personTreffId == ny.personTreffId && it.arbeidsgiverTreffId == ny.arbeidsgiverTreffId
            }

            if (ny.harRegistrertNoe()) repository.lagreVurdering(connection, jobbsøkerId, arbeidsgiverId, ny)
            else repository.slettVurdering(connection, jobbsøkerId, arbeidsgiverId)

            skrivVurderingshendelser(connection, før, ny, navIdent)
            repository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.VURDERING)
        }

    private fun skrivVurderingshendelser(
        connection: Connection,
        før: Vurdering?,
        etter: Vurdering,
        navIdent: String,
    ) {
        val person = etter.personTreffId
        val arbeidsgiver = etter.arbeidsgiverTreffId

        fun par(
            jobbsøkertype: JobbsøkerHendelsestype,
            arbeidsgivertype: ArbeidsgiverHendelsestype,
            ekstra: Map<String, Any?> = emptyMap(),
        ) = leggTilHendelseForPar(connection, person, arbeidsgiver, jobbsøkertype, arbeidsgivertype, navIdent, ekstra)

        // VURDERT erstatter forrige verdi. Uten forrigeVurdering kan ikke tidslinja
        // fortelle at noen gikk fra «Aktuell» til «Ikke aktuell».
        if (før?.vurdering != etter.vurdering) {
            par(
                JobbsøkerHendelsestype.VURDERT, ArbeidsgiverHendelsestype.VURDERT,
                mapOf("vurdering" to etter.vurdering?.name, "forrigeVurdering" to før?.vurdering?.name),
            )
        }

        val notaterFør = før?.notater.orEmpty().toSet()
        val notaterEtter = etter.notater.toSet()
        (notaterEtter - notaterFør).forEach {
            par(
                JobbsøkerHendelsestype.NOTAT_LAGT_TIL, ArbeidsgiverHendelsestype.NOTAT_LAGT_TIL,
                mapOf("notat" to it.name),
            )
        }
        (notaterFør - notaterEtter).forEach {
            par(
                JobbsøkerHendelsestype.NOTAT_FJERNET, ArbeidsgiverHendelsestype.NOTAT_FJERNET,
                mapOf("notat" to it.name),
            )
        }

        if ((før?.andregangsintervju ?: false) != etter.andregangsintervju) {
            if (etter.andregangsintervju) {
                par(
                    JobbsøkerHendelsestype.ANDREGANGSINTERVJU_AVTALT,
                    ArbeidsgiverHendelsestype.ANDREGANGSINTERVJU_AVTALT,
                    mapOf("dato" to etter.andregangsintervjuDato?.toString()),
                )
            } else {
                par(
                    JobbsøkerHendelsestype.ANGRE_ANDREGANGSINTERVJU_AVTALT,
                    ArbeidsgiverHendelsestype.ANGRE_ANDREGANGSINTERVJU_AVTALT,
                )
            }
        }

        if ((før?.jobbtilbud ?: false) != etter.jobbtilbud) {
            if (etter.jobbtilbud) {
                par(JobbsøkerHendelsestype.JOBBTILBUD_GITT, ArbeidsgiverHendelsestype.JOBBTILBUD_GITT)
            } else {
                par(JobbsøkerHendelsestype.ANGRE_JOBBTILBUD_GITT, ArbeidsgiverHendelsestype.ANGRE_JOBBTILBUD_GITT)
            }
        }
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
    private fun hendelseData(felt: Map<String, Any?>): String? =
        if (felt.isEmpty()) null else mapper.writeValueAsString(felt)

    private fun leggTilHendelseForJobbsøker(
        connection: Connection,
        personTreffId: PersonTreffId,
        hendelsestype: JobbsøkerHendelsestype,
        navIdent: String,
        data: Map<String, Any?> = emptyMap(),
    ) = jobbsøkerRepository.leggTilHendelse(
        connection = connection,
        personTreffId = personTreffId,
        hendelsestype = hendelsestype,
        aktørType = AktørType.MARKEDSKONTAKT_ELLER_VEILEDER,
        opprettetAv = navIdent,
        hendelseData = hendelseData(data),
    )

    private fun leggTilHendelseForArbeidsgiver(
        connection: Connection,
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        hendelsestype: ArbeidsgiverHendelsestype,
        navIdent: String,
        data: Map<String, Any?> = emptyMap(),
    ) = arbeidsgiverRepository.leggTilHendelse(
        connection = connection,
        arbeidsgiverTreffId = arbeidsgiverTreffId,
        hendelsestype = hendelsestype,
        opprettetAvAktørType = AktørType.MARKEDSKONTAKT_ELLER_VEILEDER,
        aktøridentifikasjon = navIdent,
        hendelseData = hendelseData(data),
    )

    private fun leggTilHendelseForTreff(
        connection: Connection,
        treffId: TreffId,
        hendelsestype: RekrutteringstreffHendelsestype,
        navIdent: String,
        data: Map<String, Any?> = emptyMap(),
    ) = rekrutteringstreffRepository.leggTilHendelseForTreff(
        connection = connection,
        treff = treffId,
        hendelsestype = hendelsestype,
        ident = navIdent,
        hendelseData = hendelseData(data),
    )

    /**
     * Registreringer i steg 3, 4 og 5 gjelder et par, og skrives derfor begge
     * steder. Begge parter har en reell historikk, og begge skal kunne lese sin
     * egen uten å kjenne den andres.
     */
    private fun leggTilHendelseForPar(
        connection: Connection,
        personTreffId: PersonTreffId,
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        jobbsøkertype: JobbsøkerHendelsestype,
        arbeidsgivertype: ArbeidsgiverHendelsestype,
        navIdent: String,
        ekstra: Map<String, Any?> = emptyMap(),
    ) {
        leggTilHendelseForJobbsøker(
            connection, personTreffId, jobbsøkertype, navIdent,
            ekstra + ("arbeidsgiverTreffId" to arbeidsgiverTreffId.somString),
        )
        leggTilHendelseForArbeidsgiver(
            connection, arbeidsgiverTreffId, arbeidsgivertype, navIdent,
            ekstra + ("personTreffId" to personTreffId.somString),
        )
    }

    private fun skriv(
        treffId: TreffId,
        block: (Connection, Treffkontekst, Treffgjennomforingsrad) -> Unit,
    ): TreffgjennomforingDto = dataSource.executeInTransaction { connection ->
        val kontekst = hentKontekst(connection, treffId)
        val rad = repository.sikreOgLås(connection, kontekst.treffDbId)
        block(connection, kontekst, rad)
        repository.hentAggregat(connection, kontekst).tilDto(treffId.somString)
    }
}
