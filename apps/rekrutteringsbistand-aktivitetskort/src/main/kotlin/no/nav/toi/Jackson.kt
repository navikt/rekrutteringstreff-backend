package no.nav.toi

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.PropertyAccessor
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

val objectMapper = jacksonObjectMapper().apply {
    setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY)
    setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.NONE)
    setVisibility(PropertyAccessor.IS_GETTER, JsonAutoDetect.Visibility.NONE)
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
}
