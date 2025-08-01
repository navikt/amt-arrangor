package no.nav.arrangor.ansatt

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.toJsonRequestBody
import no.nav.arrangor.utils.JsonUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.UUID

class AnsattServiceUserAPITest(
	private val ansattRepository: AnsattRepository,
) : IntegrationTest() {
	@AfterEach
	fun tearDown() = resetMockServers()

	@Test
	fun `getAnsatt - ikke gyldig token - unauthorized`() {
		val response =
			sendRequest(
				method = "POST",
				path = "/api/service/ansatt",
				body = JsonUtils.toJson(AnsattServiceUserAPI.AnsattRequestBody("12345678910")).toJsonRequestBody(),
			)

		response.code shouldBe 401
	}

	@Test
	fun `fjernTilgangerHosArrangor - no token - unauthorized`() {
		sendRequest("DELETE", "/api/service/ansatt/tilganger", "".toJsonRequestBody())
			.also { it.code shouldBe 401 }
	}

	@Test
	fun `fjernTilgangerHosArrangor - tokenx token - unauthorized`() {
		sendRequest(
			method = "DELETE",
			path = "/api/service/ansatt/tilganger",
			body = "".toJsonRequestBody(),
			headers = mapOf("Authorization" to "Bearer ${getTokenxToken(fnr = "foobar")}"),
		).also { it.code shouldBe 401 }
	}

	@Test
	fun `getAnsatt - autentisert - returnerer ansatt`() {
		val arrangorOne = testDatabase.insertArrangor()
		val arrangorTwo = testDatabase.insertArrangor()
		val personident = "12345678910"
		val personId = UUID.randomUUID()
		mockAltinnServer.addRoller(
			personident,
			mapOf(
				arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR),
				arrangorTwo.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER),
			),
		)
		mockPersonServer.setPerson(personident, personId, "Test", null, "Testersen")

		val response =
			sendRequest(
				method = "POST",
				path = "/api/service/ansatt",
				body = JsonUtils.toJson(AnsattServiceUserAPI.AnsattRequestBody(personident)).toJsonRequestBody(),
				headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}"),
			)

		response.code shouldBe 200
		val ansatt = JsonUtils.fromJson<Ansatt>(response.body.string())
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
			emptyMap(),
		)
		mockPersonServer.setPerson(personident, personId, "Test", null, "Testersen")

		val response =
			sendRequest(
				method = "POST",
				path = "/api/service/ansatt",
				body = JsonUtils.toJson(AnsattServiceUserAPI.AnsattRequestBody(personident)).toJsonRequestBody(),
				headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}"),
			)

		response.code shouldBe 404
		ansattRepository.get(personident) shouldBe null
	}

	@Test
	fun `getAnsatt - autentisert, personident har feil format - returnerer 400 og oppretter ikke ansatt`() {
		val personident = "123456789101"
		val personId = UUID.randomUUID()
		mockAltinnServer.addRoller(
			personident,
			emptyMap(),
		)
		mockPersonServer.setPerson(personident, personId, "Test", null, "Testersen")

		val response =
			sendRequest(
				method = "POST",
				path = "/api/service/ansatt",
				body = JsonUtils.toJson(AnsattServiceUserAPI.AnsattRequestBody(personident)).toJsonRequestBody(),
				headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}"),
			)

		response.code shouldBe 400
		ansattRepository.get(personident) shouldBe null
	}

	@Test
	fun `getAnsatt (id) - ikke gyldig token - unauthorized`() {
		val response =
			sendRequest(
				method = "GET",
				path = "/api/service/ansatt/${UUID.randomUUID()}",
			)

		response.code shouldBe 401
	}

	@Test
	fun `getAnsatt (id) - autentisert - returnerer ansatt`() {
		val arrangorOne = testDatabase.insertArrangor()
		val personident = "12345678910"
		val ansattId = UUID.randomUUID()
		val personId = UUID.randomUUID()
		ansattRepository.insertOrUpdate(
			AnsattDbo(
				id = ansattId,
				personId = personId,
				personident = personident,
				fornavn = "Test",
				mellomnavn = null,
				etternavn = "Testersen",
				arrangorer =
					listOf(
						ArrangorDbo(
							arrangorId = arrangorOne.id,
							roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
							koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID())),
							veileder = emptyList(),
						),
					),
			),
		)
		mockPersonServer.setPerson(personident, personId, "Test", null, "Testersen")

		val response =
			sendRequest(
				method = "GET",
				path = "/api/service/ansatt/$ansattId",
				headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}"),
			)

		response.code shouldBe 200
		val ansatt = JsonUtils.fromJson<Ansatt>(response.body.string())
		ansatt.personalia.personId shouldBe personId
		ansatt.arrangorer.size shouldBe 1
		ansattRepository.get(personident) shouldNotBe null
	}

	@Test
	fun `getAnsatt (id) - autentisert, ansatt finnes ikke - returnerer 404 og oppretter ikke ansatt`() {
		val ansattId = UUID.randomUUID()

		val response =
			sendRequest(
				method = "GET",
				path = "/api/service/ansatt/$ansattId",
				headers = mapOf("Authorization" to "Bearer ${getAzureAdToken()}"),
			)

		response.code shouldBe 404
		ansattRepository.get(ansattId) shouldBe null
	}
}
