package no.nav.arrangor.arrangor

import io.kotest.matchers.shouldBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.utils.JsonUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.UUID

class ArrangorControllerTest : IntegrationTest() {

	@Autowired
	lateinit var arrangorRepository: ArrangorRepository

	@Test
	fun `get - unauthorized`() {
		sendRequest(
			method = "GET",
			path = "/api/arrangor/organisasjonsnummer/4543589"
		)
			.also { it.code shouldBe 401 }
	}

	@Test
	internal fun `get - ikke finnes fra for, legger til i database`() {
		val orgNr = "123"

		arrangorRepository.get(orgNr) shouldBe null

		mockAmtEnhetsregiserServer.addVirksomhet(
			Virksomhet(
				organisasjonsnummer = orgNr,
				navn = "Navnesen",
				overordnetEnhetOrganisasjonsnummer = null,
				overordnetEnhetNavn = null
			)
		)

		val response = sendRequest(
			method = "GET",
			path = "/api/arrangor/organisasjonsnummer/$orgNr",
			headers = mapOf("Authorization" to "Bearer ${getTokenxToken(fnr = "12345678910")}")
		)
			.also { it.code shouldBe 200 }
			.let { JsonUtils.fromJson<Arrangor>(it.body!!.string()) }

		response.organisasjonsnummer shouldBe orgNr
		response.overordnetArrangorId shouldBe null
	}

	@Test
	fun `get by id - finnes ikke - returnerer 404`() {
		sendRequest(
			method = "GET",
			path = "/api/arrangor/${UUID.randomUUID()}",
			headers = mapOf("Authorization" to "Bearer ${getTokenxToken(fnr = "12345678910")}")
		)
			.also { it.code shouldBe 404 }
	}
}
