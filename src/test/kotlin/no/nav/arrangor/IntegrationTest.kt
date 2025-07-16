package no.nav.arrangor

import no.nav.arrangor.mock.MockAltinnServer
import no.nav.arrangor.mock.MockAmtEnhetsregiserServer
import no.nav.arrangor.mock.MockMachineToMachineHttpServer
import no.nav.arrangor.mock.MockPersonServer
import no.nav.arrangor.utils.Issuer
import no.nav.security.mock.oauth2.MockOAuth2Server
import no.nav.security.mock.oauth2.token.DefaultOAuth2TokenCallback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import java.time.Duration
import java.util.UUID

@SpringBootTest(classes = [ArrangorApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
abstract class IntegrationTest : RepositoryTestBase() {
	@LocalServerPort
	private var port: Int = 0

	fun serverUrl() = "http://localhost:$port"

	private val client =
		OkHttpClient
			.Builder()
			.callTimeout(Duration.ofMinutes(5))
			.build()

	@AfterEach
	fun resetMockHttpServer() = mockAmtEnhetsregiserServer.resetHttpServer()

	companion object {
		private val mockOAuth2Server = MockOAuth2Server()
		val mockAmtEnhetsregiserServer = MockAmtEnhetsregiserServer()
		private val mockMachineToMachineHttpServer = MockMachineToMachineHttpServer()
		val mockAltinnServer = MockAltinnServer()
		val mockPersonServer = MockPersonServer()

		private fun getDiscoveryUrl(issuer: String = Issuer.TOKEN_X): String = mockOAuth2Server.wellKnownUrl(issuer).toString()

		@JvmStatic
		@DynamicPropertySource
		@Suppress("unused")
		fun registerProperties(registry: DynamicPropertyRegistry) {
			mockOAuth2Server.start()
			registry.add("no.nav.security.jwt.issuer.azuread.discovery-url") { getDiscoveryUrl(Issuer.AZURE_AD) }
			registry.add("no.nav.security.jwt.issuer.azuread.accepted-audience") { "test-aud" }
			registry.add("no.nav.security.jwt.issuer.tokenx.discovery-url") { getDiscoveryUrl(Issuer.TOKEN_X) }
			registry.add("no.nav.security.jwt.issuer.tokenx.accepted-audience") { "amt-arrangor-client-id" }

			mockMachineToMachineHttpServer.start()
			registry.add("nais.env.azureOpenIdConfigTokenEndpoint") {
				mockMachineToMachineHttpServer.serverUrl() + MockMachineToMachineHttpServer.TOKEN_PATH
			}

			mockAmtEnhetsregiserServer.start()
			registry.add("amt-enhetsregister.url") { mockAmtEnhetsregiserServer.serverUrl() }
			registry.add("amt-enhetsregister.scope") { "test.enhetsregister.scope" }

			mockAltinnServer.start()
			registry.add("amt-altinn.url") { mockAltinnServer.serverUrl() }
			registry.add("amt-altinn.scope") { "test.altinn.scope" }

			mockPersonServer.start()
			registry.add("amt-person.url") { mockPersonServer.serverUrl() }
			registry.add("amt-person.scope") { "test.person.scope" }

			KafkaContainer(DockerImageName.parse("apache/kafka"))
				.withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094")
				// workaround for https://github.com/testcontainers/testcontainers-java/issues/9506
				.apply {
					start()
					System.setProperty("KAFKA_BROKERS", bootstrapServers)
				}
		}
	}

	protected fun resetMockServers() {
		mockAmtEnhetsregiserServer.resetHttpServer()
		mockAltinnServer.resetHttpServer()
		mockPersonServer.resetHttpServer()
	}

	protected fun sendRequest(
		method: String,
		path: String,
		body: RequestBody? = null,
		headers: Map<String, String> = emptyMap(),
	): Response {
		val reqBuilder =
			Request
				.Builder()
				.url("${serverUrl()}$path")
				.method(method, body)

		headers.forEach {
			reqBuilder.addHeader(it.key, it.value)
		}

		return client.newCall(reqBuilder.build()).execute()
	}

	protected fun getTokenxToken(
		fnr: String,
		audience: String = "amt-arrangor-client-id",
		issuerId: String = Issuer.TOKEN_X,
		clientId: String = "amt-tiltaksarrangor-bff",
		claims: Map<String, Any> =
			mapOf(
				"acr" to "Level4",
				"idp" to "idporten",
				"client_id" to clientId,
				"pid" to fnr,
			),
	): String = mockOAuth2Server
		.issueToken(
			issuerId,
			clientId,
			DefaultOAuth2TokenCallback(
				issuerId = issuerId,
				subject = UUID.randomUUID().toString(),
				audience = listOf(audience),
				claims = claims,
				expiry = 3600,
			),
		).serialize()

	protected fun getAzureAdToken(
		subject: String = "test",
		audience: String = "test-aud",
		issuerId: String = Issuer.AZURE_AD,
		claims: Map<String, Any> = emptyMap(),
	): String = mockOAuth2Server.issueToken(issuerId, subject, audience, claims).serialize()
}

fun String.toJsonRequestBody(): RequestBody {
	val mediaTypeJson = "application/json".toMediaType()
	return this.toRequestBody(mediaTypeJson)
}
