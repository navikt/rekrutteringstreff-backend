package no.nav.toi.arbeidsgiver

import no.nav.toi.AuthenticatedUser
import no.nav.toi.rekrutteringstreff.RekrutteringstreffKategori

internal fun AuthenticatedUser.skalSkjermeArbeidsgivere(kategori: RekrutteringstreffKategori): Boolean =
    erBorger && kategori == RekrutteringstreffKategori.WORKOP
