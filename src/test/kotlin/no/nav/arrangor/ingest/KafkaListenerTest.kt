package no.nav.arrangor.ingest

import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.dto.ArrangorDto
import no.nav.arrangor.testutils.DbTestDataUtils
import no.nav.arrangor.testutils.subscribeHvisIkkeSubscribed
import no.nav.arrangor.utils.JsonUtils
import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.awaitility.Awaitility
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*
import java.util.concurrent.TimeUnit

class KafkaListenerTest : IntegrationTest() {

	@Autowired
	lateinit var testKafkaProducer: KafkaProducer<String, String>

	@Autowired
	lateinit var testKafkaConsumer: Consumer<String, String>

	@Autowired
	lateinit var arrangorService: ArrangorService

	@Autowired
	lateinit var arrangorRepository: ArrangorRepository

	@BeforeEach
	fun setUp() {
		testKafkaConsumer.subscribeHvisIkkeSubscribed(ARRANGOR_TOPIC)
	}

	@AfterEach
	fun tearDown() {
		DbTestDataUtils.cleanDatabase(postgresDataSource)
	}

	@Test
	fun `listen - lagre arrangor`() {
		val arrangor = arrangor()

		testKafkaProducer.send(
			ProducerRecord(
				ARRANGOR_TOPIC,
				null,
				arrangor.id.toString(),
				JsonUtils.toJson(arrangor)
			)
		)

		Awaitility.await().atMost(5, TimeUnit.SECONDS).until {
			arrangorRepository.get(arrangor.organisasjonsnummer) != null
		}

		arrangorService.get(arrangor.id) shouldNotBe null
	}

	fun arrangor(
		id: UUID = UUID.randomUUID(),
		navn: String = "navn",
		organisasjonsnummer: String = "123",
		overordnetArrangorId: UUID? = null,
		deltakerlister: List<UUID> = listOf(UUID.randomUUID())
	) = ArrangorDto(
		id = id,
		navn = navn,
		organisasjonsnummer = organisasjonsnummer,
		overordnetArrangorId = overordnetArrangorId,
		deltakerlister = deltakerlister
	)
}
