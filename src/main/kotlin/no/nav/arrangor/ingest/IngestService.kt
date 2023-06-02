package no.nav.arrangor.ingest

import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.AnsattService
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.enhetsregister.EnhetsregisterClient
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.dto.ArrangorDto
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
}
