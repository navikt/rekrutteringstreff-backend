package no.nav.toi.treffgjennomføring

import io.javalin.http.BadRequestResponse
import no.nav.toi.Miljø
import no.nav.toi.rekrutteringstreff.TreffId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import java.util.UUID

class MiljøsperreTest {

    private fun kontekst(erWorkOp: Boolean) = Treffkontekst(
        treffId = TreffId(UUID.randomUUID()),
        treffDbId = 1L,
        erWorkOp = erWorkOp,
        jobbsøkere = emptyMap(),
        arbeidsgivere = emptyMap(),
    )

    // Vi ønsker å ha et subset av treffgjennomføringsstegene for workop, også tilgjengelig for vanlige treff, men kun lokalt for videre utvikling der.

    @Test
    fun `cluster-navn oversettes til miljoe`() {
        assertThat(Miljø.fraClusterNavn("prod-gcp")).isEqualTo(Miljø.PROD_GCP)
        assertThat(Miljø.fraClusterNavn("dev-gcp")).isEqualTo(Miljø.DEV_GCP)
        assertThat(Miljø.fraClusterNavn(null)).isEqualTo(Miljø.LOKALT)
        assertThat(Miljø.fraClusterNavn("noe-annet")).isEqualTo(Miljø.LOKALT)
    }

    @Test
    fun `treffgjennomføring-steg under utvikling er stengt i prod ogsaa for workop`() {
        assertThatThrownBy { kontekst(erWorkOp = true).krevStegUnderUtvikling(Miljø.PROD_GCP) }
            .isInstanceOf(BadRequestResponse::class.java)
        assertThatThrownBy { kontekst(erWorkOp = false).krevStegUnderUtvikling(Miljø.PROD_GCP) }
            .isInstanceOf(BadRequestResponse::class.java)
    }

    @Test
    fun `treffgjennomføring-steg under utvikling krever workop i dev`() {
        assertThatCode { kontekst(erWorkOp = true).krevStegUnderUtvikling(Miljø.DEV_GCP) }
            .doesNotThrowAnyException()
        assertThatThrownBy { kontekst(erWorkOp = false).krevStegUnderUtvikling(Miljø.DEV_GCP) }
            .isInstanceOf(BadRequestResponse::class.java)
    }

    @Test
    fun `treffgjennomføring-steg under utvikling er aapne lokalt`() {
        assertThatCode { kontekst(erWorkOp = false).krevStegUnderUtvikling(Miljø.LOKALT) }
            .doesNotThrowAnyException()
    }
}
