package no.nav.toi

import com.github.kittinunf.fuel.core.FuelError
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.result.Result
import com.github.kittinunf.result.Result.Failure
import com.github.kittinunf.result.Result.Success
import org.assertj.core.api.Assertions.assertThat

/**
 * Denne funksjonens eksistensberettigelse er å få kastet den underliggende exception når et HTTP kall har feilet uten
 * at vi har fått noen responsstatuskode, f.eks. java.net.SocketException: Unexpected end of file from server */
fun assertStatuscodeEquals(
    expectedStatuscode: Int,
    actualResponse: Response,
    actualResult: Result<*, FuelError>
) {
    when (actualResult) {
        is Success -> assertThat(expectedStatuscode).isEqualTo(actualResponse.statusCode)
        is Failure -> if (actualResponse.statusCode == -1) {
            throw actualResult.error
        } else {
            assertThat(expectedStatuscode).isEqualTo(actualResponse.statusCode)
        }
    }
}
