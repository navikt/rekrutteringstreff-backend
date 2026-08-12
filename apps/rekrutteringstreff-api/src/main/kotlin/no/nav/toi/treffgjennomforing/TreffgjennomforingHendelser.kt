package no.nav.toi.treffgjennomforing

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.AktørType
import no.nav.toi.ArbeidsgiverHendelsestype
import no.nav.toi.JobbsøkerHendelsestype
import no.nav.toi.RekrutteringstreffHendelsestype
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection

/**
 * Treffgjennomføringen skriver til de tre eksisterende hendelsestabellene.
 * Aktøren er alltid den som klikket, også når noe registreres på vegne av en
 * arbeidsgiver. Ingen personopplysninger i hendelsedata — bare ID-er og enkle verdier.
 */
class TreffgjennomforingHendelser(
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val arbeidsgiverRepository: ArbeidsgiverRepository,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val mapper: ObjectMapper,
) {
    private val aktørType = AktørType.MARKEDSKONTAKT_ELLER_VEILEDER

    private fun json(felt: Map<String, Any?>): String? =
        if (felt.isEmpty()) null else mapper.writeValueAsString(felt)

    fun jobbsøker(
        connection: Connection,
        personTreffId: PersonTreffId,
        type: JobbsøkerHendelsestype,
        navIdent: String,
        data: Map<String, Any?> = emptyMap(),
    ) = jobbsøkerRepository.leggTilHendelse(
        connection = connection,
        personTreffId = personTreffId,
        hendelsestype = type,
        aktørType = aktørType,
        opprettetAv = navIdent,
        hendelseData = json(data),
    )

    fun arbeidsgiver(
        connection: Connection,
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        type: ArbeidsgiverHendelsestype,
        navIdent: String,
        data: Map<String, Any?> = emptyMap(),
    ) = arbeidsgiverRepository.leggTilHendelse(
        connection = connection,
        arbeidsgiverTreffId = arbeidsgiverTreffId,
        hendelsestype = type,
        opprettetAvAktørType = aktørType,
        aktøridentifikasjon = navIdent,
        hendelseData = json(data),
    )

    fun treff(
        connection: Connection,
        treffId: TreffId,
        type: RekrutteringstreffHendelsestype,
        navIdent: String,
        data: Map<String, Any?> = emptyMap(),
    ) = rekrutteringstreffRepository.leggTilHendelseForTreff(
        connection = connection,
        treff = treffId,
        hendelsestype = type,
        ident = navIdent,
        hendelseData = json(data),
    )

    /**
     * Registreringer i steg 3, 4 og 5 gjelder et par, og skrives derfor begge
     * steder. Begge parter har en reell historikk, og begge skal kunne lese sin
     * egen uten å kjenne den andres.
     */
    fun par(
        connection: Connection,
        personTreffId: PersonTreffId,
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        jobbsøkertype: JobbsøkerHendelsestype,
        arbeidsgivertype: ArbeidsgiverHendelsestype,
        navIdent: String,
        ekstra: Map<String, Any?> = emptyMap(),
    ) {
        jobbsøker(
            connection, personTreffId, jobbsøkertype, navIdent,
            ekstra + ("arbeidsgiverTreffId" to arbeidsgiverTreffId.somString),
        )
        arbeidsgiver(
            connection, arbeidsgiverTreffId, arbeidsgivertype, navIdent,
            ekstra + ("personTreffId" to personTreffId.somString),
        )
    }
}
