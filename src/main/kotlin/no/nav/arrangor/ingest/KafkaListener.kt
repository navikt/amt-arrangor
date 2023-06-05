package no.nav.arrangor.ingest

import no.nav.arrangor.utils.JsonUtils
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.stereotype.Component

@Component
class KafkaListener(
	private val ingestService: IngestService
) {

	@KafkaListener(
		topics = [ARRANGOR_TOPIC], // Leser kun arrangørene først for å få lest inn arrangører med samme id som i amt-tiltak
		properties = ["auto.offset.reset = earliest"],
		containerFactory = "kafkaListenerContainerFactory"
	)
	fun listener(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
		when (record.topic()) {
			ARRANGOR_TOPIC -> ingestService.handleArrangor(record.value()?.let { JsonUtils.fromJson(it) })
			ANSATT_TOPIC -> ingestService.handleAnsatt(record.value()?.let { JsonUtils.fromJson(it) })
			else -> throw IllegalStateException("Mottok melding på ukjent topic: ${record.topic()}")
		}
		ack.acknowledge()
	}
}
