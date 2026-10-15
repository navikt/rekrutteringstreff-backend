package no.nav.toi

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.cfg.DateTimeFeature
import tools.jackson.databind.cfg.EnumFeature
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

object JacksonConfig {
    // Jackson 2-standarder gir samme JSON-format som før migreringen til Jackson 3.
    // accessorNaming trengs for at felter som starter med æ, ø eller å skal tas med.
    val mapper: JsonMapper = JsonMapper.builderWithJackson2Defaults()
        .addModule(kotlinModule())
        .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
        .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
        .enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(EnumFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL)
        .build()
}
