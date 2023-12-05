package no.nav.arrangor.mock

import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.utils.JsonUtils
import okhttp3.mockwebserver.MockResponse

class MockAmtEnhetsregiserServer : MockHttpServer("amt-enhetsregiser-server") {
	fun addVirksomhet(virksomhet: Virksomhet) {
		addResponseHandler(
			path = "/api/enhet/${virksomhet.organisasjonsnummer}",
			response =
				MockResponse()
					.setResponseCode(200)
					.setBody(JsonUtils.toJson(virksomhet)),
		)
	}

	fun addVirksomhetFailure(
		orgNr: String,
		errorCode: Int = 500,
	) {
		addResponseHandler(
			path = "/api/enhet/$orgNr",
			response =
				MockResponse()
					.setResponseCode(errorCode),
		)
	}
}
