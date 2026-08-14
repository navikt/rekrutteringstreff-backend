package no.nav.toi

import com.fasterxml.jackson.databind.ObjectMapper
import no.nav.toi.arbeidsgiver.ArbeidsgiverRepository
import no.nav.toi.arbeidsgiver.ArbeidsgiverTreffId
import no.nav.toi.jobbsoker.JobbsøkerRepository
import no.nav.toi.jobbsoker.PersonTreffId
import no.nav.toi.rekrutteringstreff.RekrutteringstreffRepository
import no.nav.toi.rekrutteringstreff.TreffId
import java.sql.Connection

class HendelseWriter(
    private val jobbsøkerRepository: JobbsøkerRepository,
    private val arbeidsgiverRepository: ArbeidsgiverRepository,
    private val rekrutteringstreffRepository: RekrutteringstreffRepository,
    private val mapper: ObjectMapper,
) {

    fun forJobbsøker(
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

    fun forArbeidsgiver(
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

    fun forTreff(
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

    fun forJobbsøkerOgArbeidsgiver(
        connection: Connection,
        personTreffId: PersonTreffId,
        arbeidsgiverTreffId: ArbeidsgiverTreffId,
        jobbsøkertype: JobbsøkerHendelsestype,
        arbeidsgivertype: ArbeidsgiverHendelsestype,
        navIdent: String,
        ekstra: Map<String, Any?> = emptyMap(),
    ) {
        forJobbsøker(
            connection, personTreffId, jobbsøkertype, navIdent,
            ekstra + ("arbeidsgiverTreffId" to arbeidsgiverTreffId.somString),
        )
        forArbeidsgiver(
            connection, arbeidsgiverTreffId, arbeidsgivertype, navIdent,
            ekstra + ("personTreffId" to personTreffId.somString),
        )
    }

    private fun hendelseData(felt: Map<String, Any?>): String? =
        if (felt.isEmpty()) null else mapper.writeValueAsString(felt)
}
