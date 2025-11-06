package no.nav.toi.rekrutteringstreff

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PersondataFilterTest {

    @Test
    fun `skal erstatte e-postadresse`() {
        val input = "Send en e-post til test@eksempel.no."
        val expected = "Send en e-post til emailadresse."
        assertThat(PersondataFilter.filtrerUtPersonsensitiveData(input)).isEqualTo(expected)
    }

    @Test
    fun `skal fjerne tall med 3 eller flere sifre uten separator`() {
        val input = "Fødselsnummer: 12345678901."
        val expected = "Fødselsnummer: ."
        assertThat(PersondataFilter.filtrerUtPersonsensitiveData(input)).isEqualTo(expected)
    }

    @Test
    fun `skal fjerne tall med bindestrek`() {
        val input = "Kontakt oss på 123-45-678."
        val expected = "Kontakt oss på ."
        assertThat(PersondataFilter.filtrerUtPersonsensitiveData(input)).isEqualTo(expected)
    }

    @Test
    fun `skal fjerne tall med mellomrom`() {
        val input = "Prosjektkode: 123 45 678."
        val expected = "Prosjektkode: ."
        assertThat(PersondataFilter.filtrerUtPersonsensitiveData(input)).isEqualTo(expected)
    }

    @Test
    fun `skal ikke fjerne tall med færre enn 3 sifre`() {
        val input = "Prosjekt 55 er viktig."
        assertThat(PersondataFilter.filtrerUtPersonsensitiveData(input)).isEqualTo(input)
    }

    @Test
    fun `skal fjerne både e-post og tall i samme streng`() {
        val input = "Kontakt meg på test@nav.no eller ring 98765432."
        val expected = "Kontakt meg på emailadresse eller ring ."
        assertThat(PersondataFilter.filtrerUtPersonsensitiveData(input)).isEqualTo(expected)
    }

    @Test
    fun `skal håndtere tekst uten sensitiv data`() {
        val input = "Dette er en helt vanlig tekst."
        assertThat(PersondataFilter.filtrerUtPersonsensitiveData(input)).isEqualTo(input)
    }

    @Test
    fun `skal håndtere tom streng`() {
        val input = ""
        assertThat(PersondataFilter.filtrerUtPersonsensitiveData(input)).isEmpty()
    }

    @Test
    fun `skal fjerne tall selv om det er bokstaver rett etterpå`() {
        val input = "ID: 1234567A"
        val expected = "ID: A"
        assertThat(PersondataFilter.filtrerUtPersonsensitiveData(input)).isEqualTo(expected)
    }

    @Test
    fun `skal fjerne tall på slutten av en streng`() {
        val input = "Ring meg på 12345678"
        val expected = "Ring meg på "
        assertThat(PersondataFilter.filtrerUtPersonsensitiveData(input)).isEqualTo(expected)
    }

    @Test
    fun `skal fjerne tall med blanding av separatorer`() {
        val input = "Nummeret er 123 45-678."
        val expected = "Nummeret er ."
        assertThat(PersondataFilter.filtrerUtPersonsensitiveData(input)).isEqualTo(expected)
    }
}