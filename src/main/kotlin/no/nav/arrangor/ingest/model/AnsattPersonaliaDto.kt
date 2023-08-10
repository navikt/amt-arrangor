package no.nav.arrangor.ingest.model

import java.util.UUID

data class AnsattPersonaliaDto(
	val id: UUID,
	val personident: String,
	val fornavn: String,
	val mellomnavn: String?,
	val etternavn: String
)
