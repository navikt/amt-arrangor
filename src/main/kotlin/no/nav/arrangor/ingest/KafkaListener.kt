package no.nav.arrangor.ingest

import no.nav.arrangor.utils.JsonUtils
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class KafkaListener(
	private val ingestService: IngestService,
) {
	@KafkaListener(
		topics = [VIRKSOMHET_TOPIC, ANSATT_PERSONALIA_TOPIC],
		properties = ["auto.offset.reset = earliest"],
		containerFactory = "kafkaListenerContainerFactory",
	)
	fun listener(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
		when (record.topic()) {
			VIRKSOMHET_TOPIC -> ingestService.handleVirksomhetEndring(record.value()?.let { JsonUtils.fromJson(it) })
			ANSATT_PERSONALIA_TOPIC -> ingestService.handleAnsattPersonalia(JsonUtils.fromJson(record.value()))
			else -> throw IllegalStateException("Mottok melding på ukjent topic: ${record.topic()}")
		}
		ack.acknowledge()
	}

	@KafkaListener(
		topics = [DELTAKER_TOPIC],
		properties = ["auto.offset.reset = earliest"],
		containerFactory = "kafkaListenerContainerFactoryDeltakerTopic",
	)
	fun deltakerListener(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
		when (record.topic()) {
			DELTAKER_TOPIC ->
				ingestService.handleDeltakerEndring(
					UUID.fromString(record.key()),
					record.value()?.let { JsonUtils.fromJson(it) },
				)
			else -> throw IllegalStateException("Mottok melding på ukjent topic: ${record.topic()}")
		}
		ack.acknowledge()
	}
}
