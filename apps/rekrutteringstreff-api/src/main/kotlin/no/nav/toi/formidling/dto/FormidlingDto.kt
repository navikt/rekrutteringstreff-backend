package no.nav.toi.formidling.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime
import java.util.ArrayList
import java.util.HashMap

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpprettFormidlingDto(
    val rekrutteringstreffId: String,
    val eierNavKontorEnhetId: String,
    val orgnr: String,
    val fødselsnumre: List<String>,
    val stilling: StillingDto,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StillingDto(
    val employer: ArbeidsgiverDto,
    val locationList: List<LocationDto>,
    val categoryList: List<CategoryDto>,
    val properties: Map<String, String> = HashMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CategoryDto(
    val id: Int?,
    val code: String?,
    val categoryType: String?,
    val name: String?,
    val description: String?,
    val parentId: Int?
)

class ArbeidsgiverDto(
    val id: Int?,
    val uuid: String?,
    val created: String?,
    val createdBy: String?,
    val updated: String?,
    val updatedBy: String?,
    val contactList: List<ContactDto> = ArrayList(),
    val location: LocationDto?,
    val locationList: List<LocationDto> = ArrayList(),
    val properties: Map<String, String> = HashMap(),
    val name: String?,
    val orgnr: String?,
    val status: String?,
    val parentOrgnr: String?,
    val publicName: String?,
    val deactivated: LocalDateTime?,
    val orgform: String?,
    val employees: Int?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ContactDto(
    val name: String?,
    val email: String?,
    val phone: String?,
    val role: String?,
    val title: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LocationDto(
    val address: String?,
    val postalCode: String?,
    val county: String?,
    val municipal: String?,
    val municipalCode: String?,
    val city: String?,
    val country: String?,
)

data class FormidlingOutboundDto(
    val formidlingId: String,
    val opprettetTidspunkt: String,
)

