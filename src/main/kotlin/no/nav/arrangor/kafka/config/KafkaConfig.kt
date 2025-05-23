package no.nav.arrangor.kafka.config

import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.listener.ContainerProperties

@Configuration
class KafkaConfig(
	@Value("\${KAFKA_BROKERS}") private val kafkaBrokers: String,
	@Value("\${KAFKA_SECURITY_PROTOCOL:SSL}") private val kafkaSecurityProtocol: String,
	@Value("\${KAFKA_TRUSTSTORE_PATH}") private val kafkaTruststorePath: String,
	@Value("\${KAFKA_CREDSTORE_PASSWORD}") private val kafkaCredstorePassword: String,
	@Value("\${KAFKA_KEYSTORE_PATH}") private val kafkaKeystorePath: String,
	@Value("\${kafka.auto-offset-reset}") private val kafkaAutoOffsetReset: String,
) {
	private val javaKeystore = "JKS"
	private val pkcs12 = "PKCS12"

	fun commonConfig() = mapOf(
		BOOTSTRAP_SERVERS_CONFIG to kafkaBrokers,
		ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
		ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
	) + securityConfig()

	private fun securityConfig() = mapOf(
		CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to kafkaSecurityProtocol,
		// Disable server host name verification
		SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG to "",
		SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG to javaKeystore,
		SslConfigs.SSL_KEYSTORE_TYPE_CONFIG to pkcs12,
		SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to kafkaTruststorePath,
		SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
		SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to kafkaKeystorePath,
		SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to kafkaCredstorePassword,
		SslConfigs.SSL_KEY_PASSWORD_CONFIG to kafkaCredstorePassword,
	)

	@Bean
	fun kafkaListenerContainerFactory(kafkaErrorHandler: KafkaErrorHandler): ConcurrentKafkaListenerContainerFactory<String, String> {
		val config =
			mapOf(
				ConsumerConfig.GROUP_ID_CONFIG to "amt-arrangor-consumer-2",
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
				ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
				ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
			) + commonConfig()
		val consumerFactory = DefaultKafkaConsumerFactory<String, String>(config)

		val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
		factory.consumerFactory = consumerFactory
		factory.setCommonErrorHandler(kafkaErrorHandler)
		factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
		return factory
	}

	@Bean
	fun kafkaListenerContainerFactoryDeltakerTopic(
		kafkaErrorHandler: KafkaErrorHandler,
	): ConcurrentKafkaListenerContainerFactory<String, String> {
		val config =
			mapOf(
				ConsumerConfig.GROUP_ID_CONFIG to "amt-arrangor-consumer-4",
				ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to kafkaAutoOffsetReset,
				ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false,
				ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
				ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
				ConsumerConfig.MAX_POLL_RECORDS_CONFIG to "1",
			) + commonConfig()
		val consumerFactory = DefaultKafkaConsumerFactory<String, String>(config)

		val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
		factory.consumerFactory = consumerFactory
		factory.setCommonErrorHandler(kafkaErrorHandler)
		factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL_IMMEDIATE
		return factory
	}

	@Bean
	fun kafkaProducerFactory(): ProducerFactory<String, String> = DefaultKafkaProducerFactory(commonConfig())

	@Bean
	fun kafkaTemplate(): KafkaTemplate<String, String> = KafkaTemplate(kafkaProducerFactory())
}
