package no.nav.arrangor.ansatt

import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.repositories.RolleRepository
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.client.altinn.AltinnRolle
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.utils.DataUpdateWrapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class AnsattRolleService(
	private val rolleRepository: RolleRepository,
	private val altinnClient: AltinnAclClient,
	private val metricsService: MetricsService,
	private val arrangorService: ArrangorService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun getRollerFraAltinn(personIdent: String): List<AltinnRolle> {
		return altinnClient.hentRoller(personIdent).getOrThrow()
	}

	fun getRoller(ansattId: UUID): List<RolleRepository.RolleDbo> {
		return rolleRepository.getAktiveRoller(ansattId)
	}

	fun lagreRollerForNyAnsatt(ansattId: UUID, roller: List<AltinnRolle>) {
		val unikeOrgnummer = roller.map { it.organisasjonsnummer }.distinct()
		unikeOrgnummer.forEach { arrangorService.getOrUpsert(it) }
		val orgRoller = altinnToOrgRolle(roller)
		rolleRepository.leggTilRoller(orgRoller.map { it.toInput(ansattId) })
			.also { logLagtTil(ansattId, orgRoller) }
	}

	fun oppdaterRoller(ansattId: UUID, personident: String): DataUpdateWrapper<List<RolleRepository.RolleDbo>> {
		val gamleRoller = dboToOrgRolle(rolleRepository.getAktiveRoller(ansattId))
		val nyeRoller = altinnToOrgRolle(altinnClient.hentRoller(personident).getOrThrow())

		val updated = oppdaterRoller(ansattId, gamleRoller, nyeRoller)

		return DataUpdateWrapper(
			isUpdated = updated,
			data = rolleRepository.getAktiveRoller(ansattId)
		)
	}

	fun oppdaterRoller(ansattId: UUID, gamleRoller: List<OrgRolle>, nyeRoller: List<OrgRolle>): Boolean {
		val rollerSomSkalSlettes =
			gamleRoller.filter { nyeRoller.find { nyRolle -> nyRolle.rolle == it.rolle && nyRolle.organisasjonsnummer == it.organisasjonsnummer } == null }
		val rollerSomSkalLeggesTil =
			nyeRoller.filter { gamleRoller.find { gammelRolle -> gammelRolle.rolle == it.rolle && gammelRolle.organisasjonsnummer == it.organisasjonsnummer } == null }

		rolleRepository.deaktiverRoller(rollerSomSkalSlettes.map { it.id })
			.also { logFjernet(ansattId, rollerSomSkalSlettes) }

		val unikeOrgnummer = rollerSomSkalLeggesTil.map { it.organisasjonsnummer }.distinct()
		unikeOrgnummer.forEach { arrangorService.getOrUpsert(it) }
		rolleRepository.leggTilRoller(rollerSomSkalLeggesTil.map { it.toInput(ansattId) })
			.also { logLagtTil(ansattId, rollerSomSkalLeggesTil) }

		return (rollerSomSkalSlettes.isNotEmpty() || rollerSomSkalLeggesTil.isNotEmpty())
			.also { if (it) metricsService.incEndretAnsattRolle(rollerSomSkalLeggesTil.size + rollerSomSkalSlettes.size) }
	}

	private fun logFjernet(ansattId: UUID, fjernet: List<OrgRolle>) = fjernet.forEach {
		logger.info("Ansatt med $ansattId mistet ${it.rolle} hos ${it.organisasjonsnummer}")
	}

	private fun logLagtTil(ansattId: UUID, lagtTil: List<OrgRolle>) = lagtTil.forEach {
		logger.info("Ansatt med $ansattId fikk ${it.rolle} hos ${it.organisasjonsnummer}")
	}

	private fun dboToOrgRolle(roller: List<RolleRepository.RolleDbo>): List<OrgRolle> = roller
		.map { OrgRolle(it.id, it.organisasjonsnummer, it.rolle) }

	private fun altinnToOrgRolle(roller: List<AltinnRolle>): List<OrgRolle> = roller
		.flatMap { entry -> entry.roller.map { entry.organisasjonsnummer to it } }
		.map { OrgRolle(-1, it.first, it.second) }

	data class OrgRolle(
		val id: Int,
		val organisasjonsnummer: String,
		val rolle: AnsattRolle
	) {
		fun toInput(ansattId: UUID): RolleRepository.RolleInput = RolleRepository.RolleInput(
			ansattId,
			organisasjonsnummer,
			rolle
		)

		override fun equals(other: Any?): Boolean {
			if (this === other) return true
			if (javaClass != other?.javaClass) return false

			other as OrgRolle

			if (organisasjonsnummer != other.organisasjonsnummer) return false
			return rolle == other.rolle
		}

		override fun hashCode(): Int {
			var result = organisasjonsnummer.hashCode()
			result = 31 * result + rolle.hashCode()
			return result
		}
	}
}
