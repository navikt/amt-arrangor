package no.nav.arrangor.mock

import no.nav.arrangor.client.enhetsregister.Virksomhet
import okhttp3.mockwebserver.MockResponse
import tools.jackson.databind.ObjectMapper

class MockAmtEnhetsregiserServer(
	private val objectMapper: ObjectMapper,
) : MockHttpServer("amt-enhetsregiser-server") {
	fun addVirksomhet(virksomhet: Virksomhet) {
		addResponseHandler(
			path = "/api/enhet/${virksomhet.organisasjonsnummer}",
			response =
				MockResponse()
					.setResponseCode(200)
					.setBody(objectMapper.writeValueAsString(virksomhet)),
		)
	}
}
