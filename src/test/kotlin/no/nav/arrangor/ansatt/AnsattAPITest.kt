package no.nav.arrangor.ansatt

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.AnsattRolle.KOORDINATOR
import no.nav.arrangor.domain.AnsattRolle.VEILEDER
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.toJsonRequestBody
import no.nav.arrangor.utils.JsonUtils
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class AnsattAPITest(
	private val ansattRepository: AnsattRepository,
) : IntegrationTest() {
	@Nested
	@DisplayName("Tester at alle endepunkt er sikret")
	inner class EndpointsSecuredTests {
		@Test
		fun `getByPersonident - no token - unauthorized`() {
			sendRequest("GET", "/api/ansatt")
				.also { it.code shouldBe 401 }
		}

		@Test
		fun `setKoordinatorForDeltakerliste - no token - unauthorized`() {
			sendRequest("POST", "/api/ansatt/koordinator/${UUID.randomUUID()}/${UUID.randomUUID()}", "".toJsonRequestBody())
				.also { it.code shouldBe 401 }
		}

		@Test
		fun `fjernKoordinatorForDeltakerliste - no token - unauthorized`() {
			sendRequest("DELETE", "/api/ansatt/koordinator/${UUID.randomUUID()}/${UUID.randomUUID()}")
				.also { it.code shouldBe 401 }
		}

		@Test
		fun `oppdaterVeiledereForDeltaker - no token - unauthorized`() {
			sendRequest(
				"POST",
				"/api/ansatt/veiledere/${UUID.randomUUID()}",
				JsonUtils
					.toJson(
						AnsattAPI.OppdaterVeiledereForDeltakerRequest(
							arrangorId = UUID.randomUUID(),
							veilederSomLeggesTil = listOf(AnsattAPI.VeilederAnsatt(UUID.randomUUID(), VeilederType.VEILEDER)),
							veilederSomFjernes = emptyList(),
						),
					).toJsonRequestBody(),
			).also { it.code shouldBe 401 }
		}
	}

	@Nested
	@DisplayName("/api/ansatt/ tester")
	inner class GetByPersonidentTests {
		@Test
		fun `getAnsattByPersonident - returnerer Ansatt`() {
			val arrangorOne = testDatabase.insertArrangor()
			val arrangorTwo = testDatabase.insertArrangor()
			val personident = UUID.randomUUID().toString()
			val personId = UUID.randomUUID()

			mockAltinnServer.addRoller(
				personident,
				mapOf(
					arrangorOne.organisasjonsnummer to listOf(KOORDINATOR),
					arrangorTwo.organisasjonsnummer to listOf(KOORDINATOR, VEILEDER),
				),
			)

			mockPersonServer.setPerson(personident, personId, "Test", null, "Testersen")

			val ansatt = getAnsatt(personident)

			ansatt.personalia.personident shouldBe personident
			ansatt.arrangorer.size shouldBe 2

			getRoller(ansatt, arrangorOne.id) shouldContainExactly listOf(KOORDINATOR)
			getRoller(ansatt, arrangorTwo.id) shouldContainExactly listOf(KOORDINATOR, VEILEDER)
		}

		@Test
		fun `getAnsattByPersonident - om lastSynchronized over 1 time - oppdater ansattroller`() {
			val arrangorOne = testDatabase.insertArrangor()
			val arrangorTwo = testDatabase.insertArrangor()
			val arrangorThree = testDatabase.insertArrangor()
			val personident = UUID.randomUUID().toString()
			val personId = UUID.randomUUID()

			mockAltinnServer.addRoller(
				personident,
				mapOf(
					arrangorOne.organisasjonsnummer to listOf(KOORDINATOR),
					arrangorTwo.organisasjonsnummer to listOf(KOORDINATOR, VEILEDER),
				),
			)

			mockPersonServer.setPerson(personident, personId, "Test", null, "Testersen")
			val oldAnsatt = getAnsatt(personident)

			ansattRepository.setSynchronized(oldAnsatt.id, LocalDateTime.now().minusMinutes(61))

			resetMockServers()
			mockAltinnServer.addRoller(
				personident,
				mapOf(
					arrangorTwo.organisasjonsnummer to listOf(KOORDINATOR),
					arrangorThree.organisasjonsnummer to listOf(KOORDINATOR, VEILEDER),
				),
			)

			val nyAnsatt = getAnsatt(personident)

			nyAnsatt.personalia.navn.fornavn shouldBe "Test"

			nyAnsatt.arrangorer.map { it.arrangorId } shouldContainExactly listOf(arrangorTwo.id, arrangorThree.id)

			getRoller(nyAnsatt, arrangorTwo.id) shouldContainExactly listOf(KOORDINATOR)
			getRoller(nyAnsatt, arrangorThree.id) shouldContainExactly listOf(KOORDINATOR, VEILEDER)
		}
	}

	private fun getRoller(ansatt: Ansatt, arrangorId: UUID): List<AnsattRolle> = ansatt.arrangorer.first { it.arrangorId == arrangorId }.roller

	private fun getAnsatt(personident: String): Ansatt = sendRequest(
		method = "GET",
		path = "/api/ansatt",
		headers = mapOf("Authorization" to "Bearer ${getTokenxToken(fnr = personident)}"),
	).also { it.code shouldBe 200 }
		.body
		.string()
		.let { JsonUtils.fromJson(it) }
}
