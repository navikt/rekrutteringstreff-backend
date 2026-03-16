package no.nav.toi.rekrutteringstreff

object PersondataFilter {

    // Regex for å matche e-postadresser
    private val emailRegex = "[a-zA-Z0-9_+.%-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,6}".toRegex()

    // Regex for å matche tall med 3 eller flere sifre, med eller uten mellomrom eller bindestrek
    private val numberRegex = "\\d(?:[ -]?\\d){2,}".toRegex()

    fun filtrerUtPersonsensitiveData(tekst: String, replacementEmail: String = "emailadresse"): String {
        return tekst
            .let { emailRegex.replace(it, replacementEmail) }
            .let { numberRegex.replace(it, "") }
    }

    fun erTomTekstEtterFiltrering(tekst: String): Boolean {
        return filtrerUtPersonsensitiveData(tekst, "").isBlank()
    }

    fun inneholderEpost(tekst: String): Boolean {
        return emailRegex.containsMatchIn(tekst)
    }

    fun inneholderTall(tekst: String): Boolean {
        return numberRegex.containsMatchIn(tekst)
    }
}
