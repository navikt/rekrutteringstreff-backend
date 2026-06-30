package no.nav.toi.formidling.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpprettFormidlingDto(
    val kontornummer: String,
    val kontornavn: String? = null,
    val orgnr: String,
    val fødselsnumre: List<String>,
    val stilling: StillingDto,
    val yrkestittel: String? = null,
    val janzzKonseptId: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class StillingDto(
    val employer: ArbeidsgiverDto,
    val locationList: List<LocationDto> = emptyList(),
    val categoryList: List<CategoryDto> = emptyList(),
    val properties: Map<String, String?> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CategoryDto(
    val id: Int? = null,
    val code: String? = null,
    val categoryType: String? = null,
    val name: String? = null,
    val description: String? = null,
    val parentId: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
class ArbeidsgiverDto(
    val id: Int? = null,
    val uuid: String? = null,
    val created: String? = null,
    val createdBy: String? = null,
    val updated: String? = null,
    val updatedBy: String? = null,
    val contactList: List<ContactDto> = emptyList(),
    val location: LocationDto? = null,
    val locationList: List<LocationDto> = emptyList(),
    val properties: Map<String, String> = emptyMap(),
    val name: String,
    val orgnr: String,
    val status: String? = null,
    val parentOrgnr: String? = null,
    val publicName: String,
    val deactivated: LocalDateTime? = null,
    val orgform: String? = null,
    val employees: Int? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class ContactDto(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val role: String? = null,
    val title: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class LocationDto(
    val address: String? = null,
    val postalCode: String? = null,
    val county: String? = null,
    val municipal: String? = null,
    val municipalCode: String? = null,
    val city: String? = null,
    val country: String? = null,
)

data class FormidlingOpprettetDto(
    val id: UUID,
    val stillingId: UUID,
    val opprettetTidspunkt: ZonedDateTime,
)

data class FormidlingDto(
    val id: UUID,
    val opprettetTidspunkt: ZonedDateTime,
    val fødselsnummer: String?,
    val fornavn: String?,
    val etternavn: String?,
    val orgnr: String,
    val orgnavn: String?,
    val stillingId: UUID,
    val yrkestittel: String?,
    val sperret: Boolean,
)

