package no.nav.toi.minside

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinFeature
import tools.jackson.module.kotlin.jacksonMapperBuilder

object JacksonConfig {
    val mapper: JsonMapper = jacksonMapperBuilder {
        // Jackson 3 avviser null i f.eks. List<String> og Map<String, String>. Jackson 2 godtok det
        disable(KotlinFeature.StrictNullChecks)
    }
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        // Jackson 3 avviser null eller manglende verdi for Int/Boolean. Jackson 2 ga 0/false
        .disable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        // Jackson 3 avviser innhold etter JSON-verdien. Jackson 2 ignorerte det
        .disable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        // Uten dette blir properties som starter med æ, ø eller å ignorert
        .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
        .build()
}
