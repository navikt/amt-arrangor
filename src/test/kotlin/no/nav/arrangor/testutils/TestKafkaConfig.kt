package no.nav.arrangor.testutils

import no.nav.arrangor.kafka.config.KafkaConfig
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.kafka.core.DefaultKafkaConsumerFactory

@TestConfiguration(proxyBeanMethods = false)
class TestKafkaConfig(
	private val kafkaConfig: KafkaConfig,
) {
	fun testConsumerProps(groupId: String) = mapOf(
		ConsumerConfig.GROUP_ID_CONFIG to groupId,
		ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "latest",
		ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
		ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
	) + kafkaConfig.commonKafkaConfig

	@Bean
	fun testKafkaConsumer(): Consumer<String, String> = DefaultKafkaConsumerFactory(
		testConsumerProps("amt-arrangor-consumer"),
		StringDeserializer(),
		StringDeserializer(),
	).createConsumer()

	@Bean
	fun testKafkaProducer(): KafkaProducer<String, String> {
		val config =
			mapOf(
				ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
				ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
				ProducerConfig.ACKS_CONFIG to "all",
				ProducerConfig.RETRIES_CONFIG to 10,
				ProducerConfig.RETRY_BACKOFF_MS_CONFIG to 100,
			) + kafkaConfig.commonKafkaConfig
		return KafkaProducer(config)
	}
}
