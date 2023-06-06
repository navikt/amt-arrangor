package no.nav.arrangor.ingest

import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.AnsattService
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.dto.ArrangorDto
import no.nav.arrangor.ingest.model.VirksomhetDto
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class IngestService(
	private val arrangorRepository: ArrangorRepository,
	private val ansattService: AnsattService,
	private val enhetsregisterClient: EnhetsregisterClient,
	private val metricsService: MetricsService
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun handleArrangor(arrangor: ArrangorDto?) {
		if (arrangor == null) return

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
			val overordnetArrangorId = if (virksomhetDto.overordnetEnhetOrganisasjonsnummer != null) {
				val overordnetArrangor = arrangorRepository.get(virksomhetDto.overordnetEnhetOrganisasjonsnummer)
				if (overordnetArrangor == null) {
					// når amt-arrangør er master for arrangører skal arrangøren opprettes hvis denne er null
					logger.warn("Fant ikke overordnet arrangør for orgnummer ${virksomhetDto.overordnetEnhetOrganisasjonsnummer}, lagrer arrangør ${arrangor.id} uten overordnet arrangør")
					null
				} else if (overordnetArrangor.id != arrangor.overordnetArrangorId) {
					logger.info("Arrangør ${arrangor.id} har fått ny overordnet arrangør med id ${overordnetArrangor.id}")
					overordnetArrangor.id
				} else {
					arrangor.overordnetArrangorId
				}
			} else {
				null
			}
			arrangorRepository.insertOrUpdate(
				arrangor.copy(
					navn = virksomhetDto.navn,
					organisasjonsnummer = virksomhetDto.organisasjonsnummer,
					overordnetArrangorId = overordnetArrangorId
				)
			)
			logger.info("Oppdatert arrangør med id ${arrangor.id}")
		}
	}
}
