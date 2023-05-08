package no.nav.arrangor.mock

import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.utils.JsonUtils
import okhttp3.mockwebserver.MockResponse

class MockAltinnServer : MockHttpServer("altinn-server") {

    fun addRoller(personident: String, roller: AltinnAclClient.ResponseWrapper) {
        addResponseHandler(
            path = "/api/v1/rolle/tiltaksarrangor?norskIdent=$personident",
            response = MockResponse()
                .setResponseCode(200)
                .setBody(JsonUtils.toJson(roller))
        )
    }

    fun addFailure(personident: String, errorCode: Int = 500) {
        addResponseHandler(
            path = "/api/v1/rolle/tiltaksarrangor?norskIdent=$personident",
            response = MockResponse()
                .setResponseCode(errorCode)
        )
    }
}
