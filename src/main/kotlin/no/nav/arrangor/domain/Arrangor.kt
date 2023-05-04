package no.nav.arrangor.domain

import java.util.*

data class Arrangor(
    val id: UUID,
    val navn: String,
    val organisasjonsnummer: String,
    val overordnetArrangorId: UUID?,
    val deltakerlister: Set<UUID>
)
