package no.nav.arrangor.client.enhetsregister

data class Virksomhet(
	val organisasjonsnummer: String,
	val navn: String,
	val overordnetEnhetOrganisasjonsnummer: String?,
	val overordnetEnhetNavn: String?
)

fun defaultVirksomhet(organisasjonsnummer: String): Virksomhet = Virksomhet(
	organisasjonsnummer = organisasjonsnummer,
	navn = "Ukjent virksomhet",
	overordnetEnhetOrganisasjonsnummer = null,
	overordnetEnhetNavn = null
)
