package no.nav.arrangor.dto

import java.util.UUID

data class ArrangorDto(
	val id: UUID,
	val navn: String,
	val organisasjonsnummer: String,
	val overordnetArrangorId: UUID?
)
