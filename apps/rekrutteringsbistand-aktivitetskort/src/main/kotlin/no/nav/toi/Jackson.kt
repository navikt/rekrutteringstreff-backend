package no.nav.toi

import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.introspect.DefaultAccessorNamingStrategy
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.kotlinModule

// Jackson 2-standarder gir samme JSON-format som før migreringen til Jackson 3.
// accessorNaming trengs for at felter som starter med æ, ø eller å skal tas med.
val objectMapper: JsonMapper = JsonMapper.builderWithJackson2Defaults()
    .addModule(kotlinModule())
    .accessorNaming(DefaultAccessorNamingStrategy.Provider().withFirstCharAcceptance(true, true))
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build()
