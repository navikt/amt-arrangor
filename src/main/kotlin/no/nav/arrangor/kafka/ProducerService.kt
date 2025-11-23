package no.nav.arrangor.kafka

import no.nav.arrangor.MetricsService
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.dto.AMT_ARRANGOR_SOURCE
import no.nav.arrangor.dto.AnsattDto
import no.nav.arrangor.dto.ArrangorDto
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

@Component
class ProducerService(
	private val template: KafkaTemplate<String, String>,
	private val metricsService: MetricsService,
	private val objectMapper: ObjectMapper,
) {
	private val logger = LoggerFactory.getLogger(javaClass)

	fun publishArrangor(arrangor: Arrangor) {
		template
			.send(
				ARRANGOR_TOPIC,
				arrangor.id.toString(),
				objectMapper.writeValueAsString(arrangor.toDto()),
			).get()
			.also { metricsService.incPubliserteArrangorer() }
			.also { logger.info("Publiserte arrangør med id ${arrangor.id}") }
	}

	fun publishAnsatt(ansatt: Ansatt) {
		template
			.send(
				ANSATT_TOPIC,
				ansatt.id.toString(),
				objectMapper.writeValueAsString(ansatt.toDto()),
			).get()
			.also { metricsService.incPubliserteAnsatte() }
			.also { logger.info("Publiserte ansatt med id ${ansatt.id}") }
	}

	private fun Arrangor.toDto(): ArrangorDto = ArrangorDto(
		id = id,
		source = AMT_ARRANGOR_SOURCE,
		navn = navn,
		organisasjonsnummer = organisasjonsnummer,
		overordnetArrangorId = overordnetArrangorId,
	)

	private fun Ansatt.toDto(): AnsattDto = AnsattDto(
		id = id,
		source = AMT_ARRANGOR_SOURCE,
		personalia = personalia,
		arrangorer = arrangorer,
	)
}
