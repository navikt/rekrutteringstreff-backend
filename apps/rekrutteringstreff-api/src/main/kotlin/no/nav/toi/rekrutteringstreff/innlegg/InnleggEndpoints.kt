package no.nav.toi.rekrutteringstreff.innlegg

import io.javalin.Javalin
import io.javalin.http.Context
import io.javalin.http.NotFoundResponse
import io.javalin.http.bodyAsClass
import io.javalin.openapi.*
import no.nav.toi.Rolle
import no.nav.toi.authenticatedUser
import no.nav.toi.rekrutteringstreff.TreffId
import no.nav.toi.rekrutteringstreff.endepunktRekrutteringstreff
import java.net.HttpURLConnection.*
import java.util.UUID

private const val REKRUTTERINGSTREFF_ID_PARAM = "rekrutteringstreffId"
private const val INNLEGG_ID_PARAM            = "innleggId"

private const val INNLEGG_BASE_PATH = "$endepunktRekrutteringstreff/{$REKRUTTERINGSTREFF_ID_PARAM}/innlegg"
private const val INNLEGG_ITEM_PATH = "$INNLEGG_BASE_PATH/{$INNLEGG_ID_PARAM}"

fun Javalin.handleInnlegg(repo: InnleggRepository) {
    get   (INNLEGG_BASE_PATH, hentAlleInnleggForTreff(repo))
    get   (INNLEGG_ITEM_PATH, hentEttInnlegg(repo))
    post  (INNLEGG_BASE_PATH, opprettInnlegg(repo))
    put   (INNLEGG_ITEM_PATH, oppdaterEttInnlegg(repo))
    delete(INNLEGG_ITEM_PATH, slettEttInnlegg(repo))
}

private fun hentAlleInnleggForTreff(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.BORGER)
    val treffId = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM))
    ctx.json(repo.hentForTreff(treffId).map(Innlegg::toResponseDto))
}

private fun hentEttInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.BORGER)
    val id = UUID.fromString(ctx.pathParam(INNLEGG_ID_PARAM))
    ctx.json(repo.hentById(id)?.toResponseDto() ?: throw NotFoundResponse())
}

private fun opprettInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.BORGER)
    val treffId   = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM))
    val dto       = ctx.bodyAsClass<OpprettInnleggRequestDto>()
    val navIdent  = ctx.authenticatedUser().extractNavIdent()
    try {
        ctx.status(HTTP_CREATED).json(repo.opprett(treffId, dto, navIdent).toResponseDto())
    } catch (e: IllegalStateException) {
        if (e.message?.contains("finnes ikke") == true)
            throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somUuid} ikke funnet.")
        throw e
    }
}

private fun oppdaterEttInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.BORGER)
    val treffId   = TreffId(ctx.pathParam(REKRUTTERINGSTREFF_ID_PARAM))
    val innleggId = UUID.fromString(ctx.pathParam(INNLEGG_ID_PARAM))
    val dto       = ctx.bodyAsClass<OppdaterInnleggRequestDto>()
    try {
        ctx.status(HTTP_OK).json(repo.oppdater(innleggId, treffId, dto).toResponseDto())
    } catch (e: IllegalStateException) {
        when {
            e.message?.contains("Treff")   == true -> throw NotFoundResponse("Rekrutteringstreff med id ${treffId.somUuid} ikke funnet.")
            e.message?.contains("Update")  == true -> throw NotFoundResponse("Innlegg med id $innleggId ikke funnet for treff ${treffId.somUuid}.")
            else -> throw e
        }
    }
}

private fun slettEttInnlegg(repo: InnleggRepository): (Context) -> Unit = { ctx ->
    ctx.authenticatedUser().verifiserAutorisasjon(Rolle.ARBEIDSGIVER_RETTET, Rolle.BORGER)
    val id = UUID.fromString(ctx.pathParam(INNLEGG_ID_PARAM))
    if (repo.slett(id)) ctx.status(HTTP_NO_CONTENT) else throw NotFoundResponse()
}
