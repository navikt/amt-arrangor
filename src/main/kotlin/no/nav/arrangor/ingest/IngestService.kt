package no.nav.arrangor.ingest

import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.AnsattService
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.dto.AMT_ARRANGOR_SOURCE
import no.nav.arrangor.dto.ArrangorDto
import no.nav.arrangor.ingest.model.VirksomhetDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class IngestService(
	private val arrangorRepository: ArrangorRepository,
	private val ansattService: AnsattService,
	private val enhetsregisterClient: EnhetsregisterClient,
	private val metricsService: MetricsService,
	private val publishService: PublishService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun handleArrangor(arrangor: ArrangorDto?) {
		if (arrangor == null) return
		if (arrangor.source == AMT_ARRANGOR_SOURCE) return

		var overordnetArrangor: ArrangorRepository.ArrangorDbo? = null

		if (arrangor.overordnetArrangorId != null) {
			enhetsregisterClient.hentVirksomhet(arrangor.organisasjonsnummer).let { result ->
				result.getOrNull()?.let {
					if (it.overordnetEnhetOrganisasjonsnummer != null && it.overordnetEnhetNavn != null) {
						overordnetArrangor = arrangorRepository.insertOrUpdate(
							ArrangorRepository.ArrangorDbo(
								id = arrangor.overordnetArrangorId,
								navn = it.overordnetEnhetNavn,
								organisasjonsnummer = it.overordnetEnhetOrganisasjonsnummer,
								overordnetArrangorId = null
							)
						)
					}
				}
			}
		}

		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = arrangor.id,
				navn = arrangor.navn,
				organisasjonsnummer = arrangor.organisasjonsnummer,
				overordnetArrangorId = overordnetArrangor?.id
			)
		)

		logger.info("Håndterte arrangør med id ${arrangor.id}")
		metricsService.incConsumedArrangor()
	}

	fun handleAnsatt(ansatt: Ansatt?) {
		if (ansatt == null) return

		ansattService.ingestAnsatt(ansatt)

		logger.info("Konsumerte ansatt med id ${ansatt.id}")
		metricsService.incConsumedAnsatt()
	}

	fun handleVirksomhetEndring(virksomhetDto: VirksomhetDto?) {
		if (virksomhetDto == null) return

		val arrangor = arrangorRepository.get(virksomhetDto.organisasjonsnummer)

		if (arrangor != null) {
			val overordnetArrangorId = virksomhetDto.overordnetEnhetOrganisasjonsnummer?.let {
				getOverordnetArrangorId(overordnetEnhetOrganisasjonsnummer = it, arrangor = arrangor)
			}
			arrangorRepository.insertOrUpdate(
				arrangor.copy(
					navn = virksomhetDto.navn,
					organisasjonsnummer = virksomhetDto.organisasjonsnummer,
					overordnetArrangorId = overordnetArrangorId
				)
			)
				.also { publishService.publishArrangor(it.toDomain()) }
				.also { metricsService.incEndredeArrangorer() }
			logger.info("Oppdatert arrangør med id ${arrangor.id}")
			metricsService.incConsumedVirksomhetEndring()
		}
	}

	private fun getOverordnetArrangorId(
		overordnetEnhetOrganisasjonsnummer: String,
		arrangor: ArrangorRepository.ArrangorDbo
	): UUID? {
		val overordnetArrangorFraDb = arrangorRepository.get(overordnetEnhetOrganisasjonsnummer)
		if (overordnetArrangorFraDb?.id == arrangor.overordnetArrangorId) {
			return arrangor.overordnetArrangorId
		}

		return if (overordnetArrangorFraDb != null) {
			logger.info("Arrangør ${arrangor.id} har fått ny overordnet arrangør med id ${overordnetArrangorFraDb.id}")
			overordnetArrangorFraDb.id
		} else {
			logger.warn("Fant ikke overordnet arrangør for orgnummer $overordnetEnhetOrganisasjonsnummer, oppretter overordnet arrangør for arrangør ${arrangor.id}")
			val nyOverordnetArrangor =
				enhetsregisterClient.hentVirksomhet(overordnetEnhetOrganisasjonsnummer).let { result ->
					result.getOrNull()?.let {
						arrangorRepository.insertOrUpdate(
							ArrangorRepository.ArrangorDbo(
								id = UUID.randomUUID(),
								navn = it.navn,
								organisasjonsnummer = it.organisasjonsnummer,
								overordnetArrangorId = null
							)
						)
					}
				}
			if (nyOverordnetArrangor != null) {
				logger.info("Opprettet ny overordnet arrangør med id ${nyOverordnetArrangor.id}")
			} else {
				logger.warn("Kunne ikke opprette ovrordnet arrangør for orgnummer $overordnetEnhetOrganisasjonsnummer")
			}
			nyOverordnetArrangor?.id
		}
	}
}
