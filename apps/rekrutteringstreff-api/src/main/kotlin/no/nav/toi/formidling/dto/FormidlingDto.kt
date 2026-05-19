package no.nav.toi.formidling.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpprettFormidlingDto(
    val rekrutteringstreffId: String,
    val fødselsnumre: List<String>,
    val orgnr: String,
    val stilling: StillingDto,
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class StillingDto(
    val locationList: List<LocationDto>,
    val properties: StillingPropertiesDto,

    // TODO: Legg til alle feltene for stillingen
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LocationDto(
    val address: String?,
    val postalCode: String?,
    val municipal: String?,
    val municipalCode: String?,
    val city: String?,
    val country: String?,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StillingPropertiesDto(
    val extent: String,
    val positioncount: String,
    val sector: String,
    val workday: String,
)

data class FormidlingOutboundDto(
    val id: String,
    val opprettetTidspunkt: String,
)

