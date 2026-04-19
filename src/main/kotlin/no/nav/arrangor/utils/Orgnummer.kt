package no.nav.arrangor.utils

object Orgnummer {
    private val REGEX = Regex("""[89]\d{8}""")

    fun erGyldig(value: String): Boolean = REGEX.matches(value)

    fun krevGyldig(value: String) = require(erGyldig(value)) {
        "Ugyldig organisasjonsnummer"
    }
}
