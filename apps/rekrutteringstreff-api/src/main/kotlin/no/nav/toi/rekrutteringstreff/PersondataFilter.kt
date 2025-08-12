package no.nav.toi.rekrutteringstreff

object PersondataFilter {

    // Regex for å matche e-postadresser
    private val emailRegex = "[a-zA-Z0-9_+.%-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}".toRegex()

    // Regex for å matche tall med 3 eller flere sifre, med eller uten mellomrom eller bindestrek
    private val numberRegex = "\\d(?:[ -]?\\d){2,}".toRegex()

    fun filtrerUtPersonsensitiveData(tekst: String): String {
        return tekst
            .let { emailRegex.replace(it, "emailadresse") }
            .let { numberRegex.replace(it, "") }
    }
}