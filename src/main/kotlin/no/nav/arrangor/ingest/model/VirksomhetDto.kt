package no.nav.arrangor.ingest.model

data class VirksomhetDto(
	val organisasjonsnummer: String,
	val navn: String,
	val overordnetEnhetOrganisasjonsnummer: String?
)
