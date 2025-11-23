package no.nav.arrangor.mock

import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.domain.AnsattRolle
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest
import tools.jackson.databind.ObjectMapper

class MockAltinnServer(
	private val objectMapper: ObjectMapper,
) : MockHttpServer("altinn-server") {
	fun addRoller(personident: String, roller: AltinnAclClient.ResponseWrapper) {
		val request = objectMapper.writeValueAsString(AltinnAclClient.HentRollerRequest(personident))

		val requestPredicate = { req: RecordedRequest ->
			req.path == "/api/v1/rolle/tiltaksarrangor" &&
				req.method == "POST" &&
				req.getBodyAsString() == request
		}

		addResponseHandler(
			requestPredicate,
			response =
				MockResponse()
					.setResponseCode(200)
					.setBody(objectMapper.writeValueAsString(roller)),
		)
	}

	fun addRoller(personident: String, roller: Map<String, List<AnsattRolle>>) {
		val request = objectMapper.writeValueAsString(AltinnAclClient.HentRollerRequest(personident))

		val requestPredicate = { req: RecordedRequest ->
			req.path == "/api/v1/rolle/tiltaksarrangor" &&
				req.method == "POST" &&
				req.getBodyAsString() == request
		}

		addResponseHandler(
			requestPredicate,
			response =
				MockResponse()
					.setResponseCode(200)
					.setBody(
						objectMapper.writeValueAsString(
							AltinnAclClient.ResponseWrapper(
								roller.map {
									AltinnAclClient.ResponseEntry(
										organisasjonsnummer = it.key,
										roller = it.value.map { ansattRolle -> ansattRolle.name },
									)
								},
							),
						),
					),
		)
	}
}
