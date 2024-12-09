package no.nav.arrangor.mock

import no.nav.arrangor.client.person.PersonClient
import no.nav.arrangor.utils.JsonUtils
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import java.util.UUID

class MockPersonServer : MockHttpServer("amt-person") {
	fun setPerson(
		personident: String,
		personId: UUID,
		fornavn: String,
		mellomnavn: String? = null,
		etternavn: String,
	) {
		resetHttpServer()

		addResponseHandler(
			predicate = { req: RecordedRequest ->
				req.path == "/api/arrangor-ansatt" &&
					req.method == "POST"
			},
			response =
				MockResponse()
					.setResponseCode(200)
					.setBody(
						JsonUtils.toJson(
							PersonClient.PersonResponse(
								id = personId,
								personident = personident,
								fornavn,
								mellomnavn,
								etternavn,
							),
						),
					).setHeader("content-type", "application/json"),
		)
	}
}
