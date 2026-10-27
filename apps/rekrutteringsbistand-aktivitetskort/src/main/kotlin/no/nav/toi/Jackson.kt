package no.nav.toi

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.databind.DeserializationFeature as Jackson3DeserializationFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder

val objectMapper = jacksonObjectMapper()
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

val jackson3ObjectMapper = jacksonMapperBuilder()
    .disable(Jackson3DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()
