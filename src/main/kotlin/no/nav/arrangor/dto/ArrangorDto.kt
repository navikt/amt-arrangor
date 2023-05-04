package no.nav.arrangor.dto

import java.util.*

data class ArrangorDto(
    val id: UUID,
    val navn: String,
    val organisasjonsnummer: String,
    val overordnetArrangorId: UUID?,
    val deltakerlister: List<UUID>
)
