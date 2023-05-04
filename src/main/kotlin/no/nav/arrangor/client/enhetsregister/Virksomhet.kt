package no.nav.arrangor.client.enhetsregister

data class Virksomhet(
    val organisasjonsnummer: String,
    val navn: String,
    val overordnetEnhetOrganisasjonsnummer: String?,
    val overordnetEnhetNavn: String?
)

fun defaultVirksomhet(): Virksomhet = Virksomhet(
    organisasjonsnummer = "999999999",
    navn = "Ukjent virksomhet",
    overordnetEnhetOrganisasjonsnummer = null,
    overordnetEnhetNavn = null
)
