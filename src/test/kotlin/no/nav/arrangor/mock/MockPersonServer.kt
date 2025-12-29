package no.nav.arrangor.mock

import no.nav.arrangor.client.person.PersonClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class MockPersonServer(
	private val objectMapper: ObjectMapper,
) : MockHttpServer("amt-person") {
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
						objectMapper.writeValueAsString(
							PersonClient.PersonResponse(
								id = personId,
								personident = personident,
								fornavn,
								mellomnavn,
								etternavn,
							),
						),
					).setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
		)
	}
}
