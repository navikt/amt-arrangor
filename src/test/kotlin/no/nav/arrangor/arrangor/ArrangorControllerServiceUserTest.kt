package no.nav.arrangor.arrangor

import io.kotest.matchers.shouldBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.arrangor.model.ArrangorMedOverordnetArrangor
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import no.nav.arrangor.utils.JsonUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.UUID
import javax.sql.DataSource

class ArrangorControllerServiceUserTest : IntegrationTest() {
	@Autowired
	private lateinit var datasource: DataSource

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
	fun `getArrangor - ikke gyldig token - unauthorized`() {
		val response = sendRequest(
			method = "GET",
			path = "/api/service/arrangor/organisasjonsnummer/123456789"
		)

		response.code shouldBe 401
	}

	@Test
	fun `getArrangor - autentisert, orgnummer har feil format - returnerer 400`() {
		val orgnummer = "12345678910"

		val response = sendRequest(
			method = "GET",
			path = "/api/service/arrangor/organisasjonsnummer/$orgnummer",
			headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}")
		)

		response.code shouldBe 400
	}

	@Test
	fun `getArrangor - autentisert, arrangor har ikke overordnet arrangor - returnerer arrangor`() {
		val orgnummer = "123456789"
		db.insertArrangor(navn = "Navn", organisasjonsnummer = orgnummer, overordnetArrangorId = null)

		val response = sendRequest(
			method = "GET",
			path = "/api/service/arrangor/organisasjonsnummer/$orgnummer",
			headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}")
		)

		response.code shouldBe 200
		val arrangor = JsonUtils.fromJson<ArrangorMedOverordnetArrangor>(response.body!!.string())
		arrangor.organisasjonsnummer shouldBe orgnummer
		arrangor.navn shouldBe "Navn"
		arrangor.overordnetArrangor shouldBe null
	}

	@Test
	fun `getArrangor - autentisert, arrangor har overordnet arrangor - returnerer arrangor`() {
		val orgnummerOverordnetArrangor = "987654321"
		val overordnetArrangor = db.insertArrangor(navn = "Overordnet", organisasjonsnummer = orgnummerOverordnetArrangor, overordnetArrangorId = null)
		val orgnummer = "123456789"
		db.insertArrangor(navn = "Navn", organisasjonsnummer = orgnummer, overordnetArrangorId = overordnetArrangor.id)

		val response = sendRequest(
			method = "GET",
			path = "/api/service/arrangor/organisasjonsnummer/$orgnummer",
			headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}")
		)

		response.code shouldBe 200
		val arrangor = JsonUtils.fromJson<ArrangorMedOverordnetArrangor>(response.body!!.string())
		arrangor.organisasjonsnummer shouldBe orgnummer
		arrangor.navn shouldBe "Navn"
		arrangor.overordnetArrangor?.id shouldBe overordnetArrangor.id
		arrangor.overordnetArrangor?.navn shouldBe "Overordnet"
		arrangor.overordnetArrangor?.organisasjonsnummer shouldBe orgnummerOverordnetArrangor
	}

	@Test
	fun `getArrangor (id) - ikke gyldig token - unauthorized`() {
		val response = sendRequest(
			method = "GET",
			path = "/api/service/arrangor/${UUID.randomUUID()}"
		)

		response.code shouldBe 401
	}

	@Test
	fun `getArrangor (id) - autentisert, arrangor finnes ikke - returnerer 404`() {
		val response = sendRequest(
			method = "GET",
			path = "/api/service/arrangor/${UUID.randomUUID()}",
			headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}")
		)

		response.code shouldBe 404
	}

	@Test
	fun `getArrangor (id) - autentisert, arrangor har overordnet arrangor - returnerer arrangor`() {
		val orgnummerOverordnetArrangor = "987654321"
		val overordnetArrangor = db.insertArrangor(navn = "Overordnet", organisasjonsnummer = orgnummerOverordnetArrangor, overordnetArrangorId = null)
		val orgnummer = "123456789"
		val arrangor = db.insertArrangor(navn = "Navn", organisasjonsnummer = orgnummer, overordnetArrangorId = overordnetArrangor.id)

		val response = sendRequest(
			method = "GET",
			path = "/api/service/arrangor/${arrangor.id}",
			headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}")
		)

		response.code shouldBe 200
		val arrangorResponse = JsonUtils.fromJson<ArrangorMedOverordnetArrangor>(response.body!!.string())
		arrangorResponse.organisasjonsnummer shouldBe orgnummer
		arrangorResponse.navn shouldBe "Navn"
		arrangorResponse.overordnetArrangor?.id shouldBe overordnetArrangor.id
		arrangorResponse.overordnetArrangor?.navn shouldBe "Overordnet"
		arrangorResponse.overordnetArrangor?.organisasjonsnummer shouldBe orgnummerOverordnetArrangor
	}
}
