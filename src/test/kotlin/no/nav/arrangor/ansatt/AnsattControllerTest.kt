package no.nav.arrangor.ansatt

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repositories.AnsattRepository
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.AnsattRolle.KOORDINATOR
import no.nav.arrangor.domain.AnsattRolle.VEILEDER
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import no.nav.arrangor.toJsonRequestBody
import no.nav.arrangor.utils.JsonUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDateTime
import java.util.UUID
import javax.sql.DataSource

class AnsattControllerTest : IntegrationTest() {

	@Autowired
	private lateinit var datasource: DataSource

	@Autowired
	private lateinit var ansattRepository: AnsattRepository

	private lateinit var db: DbTestData

	@BeforeEach
	fun setUp() {
		db = DbTestData(NamedParameterJdbcTemplate(datasource))
	}

	@AfterEach
	fun tearDown() {
		DbTestDataUtils.cleanDatabase(datasource)
		resetMockServers()
	}

	@Nested
	@DisplayName("Tester at alle endepunkt er sikret")
	inner class EndpointsSecuredTests {

		@Test
		fun `get (id) - no token - unauthorized`() {
			sendRequest("GET", "/api/ansatt/${UUID.randomUUID()}")
				.also { it.code shouldBe 401 }
		}

		@Test
		fun `getByPersonident - no token - unauthorized`() {
			sendRequest("GET", "/api/ansatt")
				.also { it.code shouldBe 401 }
		}

		@Test
		fun `setKoordinatorForDeltakerliste - no token - unauthorized`() {
			sendRequest("POST", "/api/ansatt/koordinator/${UUID.randomUUID()}", "".toJsonRequestBody())
				.also { it.code shouldBe 401 }
		}

		@Test
		fun `fjernKoordinatorForDeltakerliste - no token - unauthorized`() {
			sendRequest("DELETE", "/api/ansatt/koordinator/${UUID.randomUUID()}")
				.also { it.code shouldBe 401 }
		}

		@Test
		fun `setVeileder - no token - unauthorized`() {
			sendRequest(
				"POST",
				"/api/ansatt/veileder",
				JsonUtils.toJson(
					AnsattController.SetVeilederForDeltakerRequestBody(
						deltakerId = UUID.randomUUID(),
						arrangorId = UUID.randomUUID(),
						type = VeilederType.VEILEDER
					)
				).toJsonRequestBody()
			)
				.also { it.code shouldBe 401 }
		}

		@Test
		fun `fjernVeilederForDeltaker - no token - unauthorized`() {
			sendRequest("DELETE", "/api/ansatt/veileder/${UUID.randomUUID()}")
				.also { it.code shouldBe 401 }
		}
	}

	@Nested
	@DisplayName("/api/ansatt/ tester")
	inner class GetByPersonidentTests {

		@Test
		fun `getAnsattByPersonident - returnerer Ansatt`() {
			val arrangorOne = db.insertArrangor()
			val arrangorTwo = db.insertArrangor()
			val personident = UUID.randomUUID().toString()
			val personId = UUID.randomUUID()

			mockAltinnServer.addRoller(
				personident,
				mapOf(
					arrangorOne.organisasjonsnummer to listOf(KOORDINATOR),
					arrangorTwo.organisasjonsnummer to listOf(KOORDINATOR, VEILEDER)
				)
			)

			mockPersonServer.setPerson(personident, personId, "Test", null, "Testersen")

			val ansatt = getAnsatt(personident)

			ansatt.personalia.personident shouldBe personident
			ansatt.arrangorer.size shouldBe 2

			getRoller(ansatt, arrangorOne.id) shouldContainExactly listOf(KOORDINATOR)
			getRoller(ansatt, arrangorTwo.id) shouldContainExactly listOf(KOORDINATOR, VEILEDER)
		}

		@Test
		fun `getAnsattByPersonident - om lastSynchronized over 1 time - oppdater Ansatt`() {
			val arrangorOne = db.insertArrangor()
			val arrangorTwo = db.insertArrangor()
			val arrangorThree = db.insertArrangor()
			val personident = UUID.randomUUID().toString()
			val personId = UUID.randomUUID()

			mockAltinnServer.addRoller(
				personident,
				mapOf(
					arrangorOne.organisasjonsnummer to listOf(KOORDINATOR),
					arrangorTwo.organisasjonsnummer to listOf(KOORDINATOR, VEILEDER)
				)
			)

			mockPersonServer.setPerson(personident, personId, "Test", null, "Testersen")
			val oldAnsatt = getAnsatt(personident)

			ansattRepository.setSynchronized(oldAnsatt.id, LocalDateTime.now().minusMinutes(61))

			resetMockServers()
			mockAltinnServer.addRoller(
				personident,
				mapOf(
					arrangorTwo.organisasjonsnummer to listOf(KOORDINATOR),
					arrangorThree.organisasjonsnummer to listOf(KOORDINATOR, VEILEDER)
				)
			)

			mockPersonServer.setPerson(personident, personId, "Test2", "Mellom", "Testersen2")
			val nyAnsatt = getAnsatt(personident)

			nyAnsatt.personalia.navn.fornavn shouldBe "Test2"
			nyAnsatt.personalia.navn.mellomnavn shouldBe "Mellom"
			nyAnsatt.personalia.navn.etternavn shouldBe "Testersen2"

			nyAnsatt.arrangorer.map { it.arrangorId } shouldContainExactly listOf(arrangorTwo.id, arrangorThree.id)

			getRoller(nyAnsatt, arrangorTwo.id) shouldContainExactly listOf(KOORDINATOR)
			getRoller(nyAnsatt, arrangorThree.id) shouldContainExactly listOf(KOORDINATOR, VEILEDER)
		}
	}

	private fun getRoller(ansatt: Ansatt, arrangorId: UUID): List<AnsattRolle> {
		return ansatt.arrangorer.find { it.arrangorId == arrangorId }!!.roller
	}

	private fun getAnsatt(personident: String): Ansatt = sendRequest(
		method = "GET",
		path = "/api/ansatt",
		headers = mapOf("Authorization" to "Bearer ${getTokenxToken(fnr = personident)}")
	)
		.also { it.code shouldBe 200 }
		.let { it.body?.string() ?: throw IllegalStateException("Body skal ikke være tom") }
		.let { JsonUtils.fromJson(it) }
}
