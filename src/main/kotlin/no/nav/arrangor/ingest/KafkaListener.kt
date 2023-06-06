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
		topics = [ARRANGOR_TOPIC, VIRKSOMHET_TOPIC], // Legg til lesing av ansatt-topicen her når deltakere er migrert inn i amt-person-service
		properties = ["auto.offset.reset = latest"], // bør endres tilbake til earliest når vi får offset på virksomhet-topic og før vi leser ansatte
		containerFactory = "kafkaListenerContainerFactory"
	)
	fun listener(record: ConsumerRecord<String, String>, ack: Acknowledgment) {
		when (record.topic()) {
			ARRANGOR_TOPIC -> ingestService.handleArrangor(record.value()?.let { JsonUtils.fromJson(it) })
			ANSATT_TOPIC -> ingestService.handleAnsatt(record.value()?.let { JsonUtils.fromJson(it) })
			VIRKSOMHET_TOPIC -> ingestService.handleVirksomhetEndring(record.value()?.let { JsonUtils.fromJson(it) })
			else -> throw IllegalStateException("Mottok melding på ukjent topic: ${record.topic()}")
		}
		ack.acknowledge()
	}
}
