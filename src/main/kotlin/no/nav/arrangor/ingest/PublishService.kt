package no.nav.arrangor.ingest

import no.nav.arrangor.MetricsService
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.dto.ArrangorDto
import no.nav.arrangor.utils.JsonUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component

@Component
class PublishService(
	private val template: KafkaTemplate<String, String>,
	private val metricsService: MetricsService,
	@Value("\${publish.ansatt.enabled}") private val publishAnsatte: Boolean
) {

	private val logger = LoggerFactory.getLogger(javaClass)

	fun publishArrangor(arrangor: Arrangor) {
		template.send(ARRANGOR_TOPIC, arrangor.id.toString(), JsonUtils.toJson(arrangor.toDto()))
			.also { metricsService.incPubliserteArrangorer() }
			.also { logger.info("Publiserte arrangør med id ${arrangor.id}") }
	}

	fun publishAnsatt(ansatt: Ansatt) {
		if (publishAnsatte) {
			template.send(ANSATT_TOPIC, JsonUtils.toJson(ansatt))
				.also { metricsService.incPubliserteAnsatte() }
				.also { logger.info("Publiserte ansatt med id ${ansatt.id}") }
		} else {
			logger.info("Publisering av meldinger er skrudd av, publiserer ikke ansatt med id ${ansatt.id}")
		}
	}

	private fun Arrangor.toDto(): ArrangorDto = ArrangorDto(
		id = id,
		navn = navn,
		organisasjonsnummer = organisasjonsnummer,
		overordnetArrangorId = overordnetArrangorId
	)
}
