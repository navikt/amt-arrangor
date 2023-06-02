package no.nav.arrangor.ansatt

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import no.nav.arrangor.toJsonRequestBody
import no.nav.arrangor.utils.JsonUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.UUID
import javax.sql.DataSource

class AnsattControllerServiceUserTest : IntegrationTest() {
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

	@Test
	fun `getAnsatt - ikke gyldig token - unauthorized`() {
		val response = sendRequest(
			method = "POST",
			path = "/api/service/ansatt",
			body = JsonUtils.toJson(AnsattControllerServiceUser.AnsattRequestBody("12345678910")).toJsonRequestBody()
		)

		response.code shouldBe 401
	}

	@Test
	fun `getAnsatt - autentisert - returnerer ansatt`() {
		val arrangorOne = db.insertArrangor()
		val arrangorTwo = db.insertArrangor()
		val personident = "12345678910"
		val personId = UUID.randomUUID()
		mockAltinnServer.addRoller(
			personident,
			mapOf(
				arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR),
				arrangorTwo.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER)
			)
		)
		mockPersonServer.setPerson(personident, personId, "Test", null, "Testersen")

		val response = sendRequest(
			method = "POST",
			path = "/api/service/ansatt",
			body = JsonUtils.toJson(AnsattControllerServiceUser.AnsattRequestBody(personident)).toJsonRequestBody(),
			headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}")
		)

		response.code shouldBe 200
		val ansatt = JsonUtils.fromJson<Ansatt>(response.body!!.string())
		ansatt.personalia.personId shouldBe personId
		ansatt.arrangorer.size shouldBe 2
		ansattRepository.get(personident) shouldNotBe null
	}

	@Test
	fun `getAnsatt - autentisert, ansatt finnes ikke og har ingen roller - returnerer 404 og oppretter ikke ansatt`() {
		val personident = "12345678910"
		val personId = UUID.randomUUID()
		mockAltinnServer.addRoller(
			personident,
			emptyMap()
		)
		mockPersonServer.setPerson(personident, personId, "Test", null, "Testersen")

		val response = sendRequest(
			method = "POST",
			path = "/api/service/ansatt",
			body = JsonUtils.toJson(AnsattControllerServiceUser.AnsattRequestBody(personident)).toJsonRequestBody(),
			headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}")
		)

		response.code shouldBe 404
		ansattRepository.get(personident) shouldBe null
	}
}
