package no.nav.arrangor.mock

import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.utils.JsonUtils
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.RecordedRequest

class MockAltinnServer : MockHttpServer("altinn-server") {
	fun addRoller(
		personident: String,
		roller: AltinnAclClient.ResponseWrapper,
	) {
		val request = JsonUtils.toJson(AltinnAclClient.HentRollerRequest(personident))

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
					.setBody(JsonUtils.toJson(roller)),
		)
	}

	fun addRoller(
		personident: String,
		roller: Map<String, List<AnsattRolle>>,
	) {
		val request = JsonUtils.toJson(AltinnAclClient.HentRollerRequest(personident))

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
						JsonUtils.toJson(
							AltinnAclClient.ResponseWrapper(
								roller.map { AltinnAclClient.ResponseEntry(it.key, it.value.map { it.name }) },
							),
						),
					),
		)
	}
}
