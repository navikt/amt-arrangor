package no.nav.arrangor.consumer.model

data class VirksomhetDto(
	val organisasjonsnummer: String,
	val navn: String,
	val overordnetEnhetOrganisasjonsnummer: String?,
)
