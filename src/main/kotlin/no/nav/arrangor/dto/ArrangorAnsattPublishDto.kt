package no.nav.arrangor.dto

import java.util.*

data class ArrangorAnsattPublishDto(
    val id: UUID,
    val personalia: PersonPublishDto,
    val arrangorer: List<TilknyttetArrangor>,
)

data class PersonPublishDto(
    val personident: String,
    val navn: Navn
)

data class Navn(
    val fornavn: String,
    val mellomnavn: String?,
    val etternavn: String
)

data class TilknyttetArrangor(
    val arrangorId: UUID,
    val roller: List<AnsattRolle>,
    val veileder: List<Veileder>,
    val koordinator: List<UUID>
)

data class Veileder(
    val deltakerId: UUID,
    val type: VeilederType
)

enum class AnsattRolle {
    KOORDINATOR,
    VEILEDER
}

enum class VeilederType {
    VEILEDER,
    MEDVEILEDER
}
