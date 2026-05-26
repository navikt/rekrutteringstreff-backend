package no.nav.toi.formidling

import java.util.UUID

class StillingKlientStub : StillingKlient {
    var sisteRequest: OpprettRekrutteringstreffFormidling? = null
        private set

    var respons = OpprettFormidlingStillingRespons(
        stillingId = UUID.randomUUID(),
        kandidatlisteId = UUID.randomUUID(),
    )

    override fun opprettFormidlingStillingOgKandidatliste(
        opprettFormidling: OpprettRekrutteringstreffFormidling,
        userToken: String,
    ): OpprettFormidlingStillingRespons {
        sisteRequest = opprettFormidling
        return respons
    }
}
