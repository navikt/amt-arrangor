package no.nav.arrangor.ingest.mulighetsrommet

import java.time.LocalDate
import java.util.UUID

class MulighetsrommetGjennomforingDto(
    val id: UUID,
    val tiltakstype: Tiltakstype,
    val navn: String,
    val startDato: LocalDate,
    val sluttDato: LocalDate? = null,
    val status: Status,
    val virksomhetsnummer: String
) {

    data class Tiltakstype(
        val id: UUID,
        val navn: String,
        val arenaKode: String
    )

    enum class Status {
        GJENNOMFORES,
        AVBRUTT,
        AVLYST,
        AVSLUTTET,
        APENT_FOR_INNSOK;
    }
}