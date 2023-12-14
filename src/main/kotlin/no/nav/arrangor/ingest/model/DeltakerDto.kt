package no.nav.arrangor.ingest.model

import java.time.LocalDateTime
import java.util.UUID

data class DeltakerDto(
	val id: UUID,
	val status: DeltakerStatusDto,
)

data class DeltakerStatusDto(
	val type: DeltakerStatus,
	val gyldigFra: LocalDateTime,
	val opprettetDato: LocalDateTime,
)

enum class DeltakerStatus {
	VENTER_PA_OPPSTART,
	DELTAR,
	HAR_SLUTTET,
	IKKE_AKTUELL,
	FEILREGISTRERT,
	SOKT_INN,
	VURDERES,
	VENTELISTE,
	AVBRUTT,
	FULLFORT,
	PABEGYNT_REGISTRERING,
	UTKAST_TIL_PAMELDING,
	AVBRUTT_UTKAST,
}

val AVSLUTTENDE_STATUSER =
	listOf(
		DeltakerStatus.HAR_SLUTTET,
		DeltakerStatus.FULLFORT,
		DeltakerStatus.IKKE_AKTUELL,
		DeltakerStatus.AVBRUTT,
	)

val SKJULES_ALLTID_STATUSER =
	listOf(
		DeltakerStatus.SOKT_INN,
		DeltakerStatus.VENTELISTE,
		DeltakerStatus.PABEGYNT_REGISTRERING,
		DeltakerStatus.FEILREGISTRERT,
		DeltakerStatus.UTKAST_TIL_PAMELDING,
		DeltakerStatus.AVBRUTT_UTKAST,
	)
