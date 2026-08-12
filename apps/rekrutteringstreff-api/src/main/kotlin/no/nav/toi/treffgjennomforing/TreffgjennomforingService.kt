package no.nav.toi.treffgjennomforing

import io.javalin.http.BadRequestResponse
import io.javalin.http.NotFoundResponse
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.executeInTransaction
import no.nav.toi.jobbsoker.PersonTreffId
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
    private val lesRepository: TreffgjennomforingLesRepository,
    private val skrivRepository: TreffgjennomforingSkrivRepository,
    private val hendelser: TreffgjennomforingHendelser,
) {

    /** Rent lesende. Finnes ingen lagret treffgjennomføring, er svaret tomtilstanden. */
    fun hent(treffId: TreffId): TreffgjennomforingDto = dataSource.executeInTransaction { connection ->
        val kontekst = hentKontekst(connection, treffId)
        lesRepository.hentAggregat(connection, kontekst).tilDto(treffId.somString)
    }

    fun oppdaterOppmøte(treffId: TreffId, dto: OppmøteRequestDto, navIdent: String): TreffgjennomforingDto =
        skriv(treffId) { connection, kontekst, rad ->
            val person = PersonTreffId(dto.personTreffId)
            val jobbsøkerId = kontekst.jobbsøkerId(person)
                ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")
            val aggregat = lesRepository.hentAggregat(connection, kontekst)

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
        // Kortbunken finnes bare på WorkOp, og da skal andre treff heller ikke late som.
        val deltakernummer =
            if (kontekst.erWorkOp) skrivRepository.tildelDeltakernummer(connection, kontekst.treffDbId, jobbsøkerId)
            else null

        hendelser.jobbsøker(
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
        val registreringer = skrivRepository.tellRegistreringer(connection, jobbsøkerId)
        if (registreringer.finnesNoen() && !bekreftet) throw OppmøteHarRegistreringerException(registreringer)

        skrivRepository.slettRegistreringerFor(connection, jobbsøkerId)
        hendelser.jobbsøker(
            connection, person, JobbsøkerHendelsestype.ANGRE_MØTT_OPP, navIdent,
            mapOf(
                "interesser" to registreringer.interesser,
                "intervjuplasser" to registreringer.intervjuplasser,
                "vurderinger" to registreringer.vurderinger,
            ),
        )
    }

    /**
     * Vanlig oppdatering, ikke en engangsoperasjon. Tidene styrer bare timeplanen,
     * så en endring regenererer verken rom, interesser eller vurderinger. Første
     * kall — når det ennå ikke finnes rom — oppretter fordelingen og rotasjonen.
     */
    fun lagreMøteoppsett(treffId: TreffId, dto: MøteoppsettRequestDto, navIdent: String): TreffgjennomforingDto =
        skriv(treffId) { connection, kontekst, rad ->
            krevWorkOp(kontekst)
            val møteoppsett = TreffgjennomforingValidering.møteoppsett(dto)
            val aggregat = lesRepository.hentAggregat(connection, kontekst)
            skrivRepository.lagreMøteoppsett(connection, rad.id, møteoppsett)

            if (aggregat.rom.isNotEmpty()) {
                hendelser.treff(
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
        skrivRepository.erstattRomfordeling(connection, kontekst.treffDbId, rom, kontekst)

        val rotasjon = kontekst.arbeidsgiverIder.mapIndexed { indeks, arbeidsgiver ->
            ArbeidsgiverRotasjon(arbeidsgiver, indeks)
        }
        skrivRepository.lagreRotasjon(connection, rotasjon, kontekst)
        rotasjon.forEach {
            hendelser.arbeidsgiver(
                connection, it.arbeidsgiverTreffId, ArbeidsgiverHendelsestype.ROTASJON_TILDELT, navIdent,
                mapOf("startPosisjon" to it.startPosisjon),
            )
        }

        hendelser.treff(
            connection, kontekst.treffId, RekrutteringstreffHendelsestype.TREFFGJENNOMFORING_OPPRETTET, navIdent,
            mapOf(
                "antallRom" to kontekst.antallRom,
                "starttidspunkt" to dto.starttidspunkt,
                "varighetPerMøteMinutter" to dto.varighetPerMøteMinutter,
                "antallFremmøtte" to aggregat.oppmøte.size,
            ),
        )
        skrivRepository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.ROM)
    }

    fun lagreRomfordeling(treffId: TreffId, rom: List<RomDto>, navIdent: String): TreffgjennomforingDto =
        skriv(treffId) { connection, kontekst, _ ->
            krevWorkOp(kontekst)
            val aggregat = lesRepository.hentAggregat(connection, kontekst)
            val ny = TreffgjennomforingValidering.romfordeling(rom, kontekst.antallRom, aggregat.oppmøte)

            skrivRepository.erstattRomfordeling(connection, kontekst.treffDbId, ny, kontekst)
            skrivRomhendelser(connection, aggregat.rom, ny, navIdent)
        }

    /** Manuell flytting av én person gir PLASSERT_I_ROM, slik at vi vet hvem som ble flyttet. */
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
                hendelser.jobbsøker(
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

            val aggregat = lesRepository.hentAggregat(connection, kontekst)
            if (dto.interessert && person !in aggregat.oppmøte) {
                throw BadRequestResponse("Bare fremmøtte jobbsøkere kan registrere interesse")
            }

            if (!skrivRepository.settInteresse(connection, jobbsøkerId, arbeidsgiverId, dto.interessert)) return@skriv

            hendelser.par(
                connection, person, arbeidsgiver,
                if (dto.interessert) JobbsøkerHendelsestype.INTERESSE_REGISTRERT
                else JobbsøkerHendelsestype.ANGRE_INTERESSE_REGISTRERT,
                if (dto.interessert) ArbeidsgiverHendelsestype.INTERESSE_REGISTRERT
                else ArbeidsgiverHendelsestype.ANGRE_INTERESSE_REGISTRERT,
                navIdent,
            )

            speilInteresseIFordeling(connection, kontekst, aggregat, person, arbeidsgiver, dto.interessert)
            skrivRepository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.INTERESSE)
        }

    /**
     * Ny interesse legges bakerst blant de inkluderte, trukket interesse fjernes
     * fra begge lister. Uten dette ville fordelingen pekt på interesser som ikke finnes.
     */
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
        skrivRepository.erstattIntervjufordelinger(connection, listOf(oppdatert), kontekst)
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
        val før = lesRepository.hentAggregat(connection, kontekst).intervjufordelinger
            .firstOrNull { it.arbeidsgiverTreffId == arbeidsgiver }

        skrivRepository.erstattIntervjufordelinger(connection, listOf(ny), kontekst)
        skrivFordelingshendelser(connection, før, ny, navIdent)
        skrivRepository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.FORDELING)
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
            hendelser.par(
                connection, person, etter.arbeidsgiverTreffId,
                JobbsøkerHendelsestype.SATT_OPP_TIL_INTERVJU,
                ArbeidsgiverHendelsestype.SATT_OPP_TIL_INTERVJU, navIdent,
            )
        }
        (inkludertFør - inkludertEtter).forEach { person ->
            hendelser.par(
                connection, person, etter.arbeidsgiverTreffId,
                JobbsøkerHendelsestype.ANGRE_SATT_OPP_TIL_INTERVJU,
                ArbeidsgiverHendelsestype.ANGRE_SATT_OPP_TIL_INTERVJU, navIdent,
            )
        }
    }

    /**
     * Samme servicefunksjon som førstegangsopprettelsen — det skal ikke finnes to
     * kodeveier. Erstatter hele fordelingen i én transaksjon.
     */
    fun fordelIntervjuer(treffId: TreffId, navIdent: String): TreffgjennomforingDto =
        skriv(treffId) { connection, kontekst, rad ->
            krevWorkOp(kontekst)
            val aggregat = lesRepository.hentAggregat(connection, kontekst)
            val fordelinger = Intervjufordeler.fordel(
                interesser = aggregat.interesser,
                eksisterendeFordelinger = aggregat.intervjufordelinger,
                arbeidsgivere = kontekst.arbeidsgiverIder,
            )
            skrivRepository.erstattIntervjufordelinger(connection, fordelinger, kontekst)

            hendelser.treff(
                connection, treffId,
                RekrutteringstreffHendelsestype.TREFFGJENNOMFORING_INTERVJUFORDELING_FORDELT, navIdent,
                mapOf(
                    "antallArbeidsgivere" to fordelinger.size,
                    "antallPlasseringer" to fordelinger.sumOf { it.inkludertePersonTreffIder.size },
                ),
            )
            skrivRepository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.FORDELING)
        }

    fun lagreVurdering(treffId: TreffId, dto: VurderingDto, navIdent: String): TreffgjennomforingDto =
        skriv(treffId) { connection, kontekst, rad ->
            val ny = TreffgjennomforingValidering.vurdering(dto)
            val jobbsøkerId = kontekst.jobbsøkerId(ny.personTreffId)
                ?: throw BadRequestResponse("Jobbsøkeren finnes ikke på treffet")
            val arbeidsgiverId = kontekst.arbeidsgiverId(ny.arbeidsgiverTreffId)
                ?: throw BadRequestResponse("Arbeidsgiveren finnes ikke på treffet")

            val før = lesRepository.hentAggregat(connection, kontekst).vurderinger.firstOrNull {
                it.personTreffId == ny.personTreffId && it.arbeidsgiverTreffId == ny.arbeidsgiverTreffId
            }

            if (ny.harRegistrertNoe()) skrivRepository.lagreVurdering(connection, jobbsøkerId, arbeidsgiverId, ny)
            else skrivRepository.slettVurdering(connection, jobbsøkerId, arbeidsgiverId)

            skrivVurderingshendelser(connection, før, ny, navIdent)
            skrivRepository.settFase(connection, kontekst.treffDbId, rad.fase, TreffgjennomføringFase.VURDERING)
        }

    /**
     * Én hendelse per faktisk endring. Autolagring sender ofte samme verdi på nytt,
     * og uten denne regelen ville tidslinja blitt ubrukelig.
     */
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
        ) = hendelser.par(connection, person, arbeidsgiver, jobbsøkertype, arbeidsgivertype, navIdent, ekstra)

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
     * Alle skriveoperasjoner låser treffgjennomføringsraden først, og svarer med
     * hele aggregatet slik frontend forventer.
     */
    private fun skriv(
        treffId: TreffId,
        block: (Connection, Treffkontekst, Treffgjennomforingsrad) -> Unit,
    ): TreffgjennomforingDto = dataSource.executeInTransaction { connection ->
        val kontekst = hentKontekst(connection, treffId)
        val rad = skrivRepository.sikreOgLås(connection, kontekst.treffDbId)
        block(connection, kontekst, rad)
        lesRepository.hentAggregat(connection, kontekst).tilDto(treffId.somString)
    }
}
