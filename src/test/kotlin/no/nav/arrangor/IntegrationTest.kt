package no.nav.arrangor

import no.nav.arrangor.mock.MockAmtEnhetsregiserServer
import no.nav.arrangor.mock.MockMachineToMachineHttpServer
import no.nav.arrangor.testutils.DbTestDataUtils
import no.nav.arrangor.testutils.SingletonPostgresContainer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.junit.jupiter.SpringExtension
import java.time.Duration
import java.util.*

@ActiveProfiles("test")
@TestConfiguration("application-test.properties")
@ExtendWith(SpringExtension::class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IntegrationTest {

    @LocalServerPort
    private var port: Int = 0

    fun serverUrl() = "http://localhost:$port"

    private val client = OkHttpClient.Builder()
        .callTimeout(Duration.ofMinutes(5))
        .build()

    @AfterEach
    fun cleanDatabase() {
        DbTestDataUtils.cleanDatabase(postgresDataSource)
    }

    companion object {
        val mockAmtEnhetsregiserServer = MockAmtEnhetsregiserServer()
        val mockMachineToMachineHttpServer = MockMachineToMachineHttpServer()

        val postgresDataSource = SingletonPostgresContainer.getDataSource()

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            mockMachineToMachineHttpServer.start()
            registry.add("nais.env.azureOpenIdConfigTokenEndpoint") {
                mockMachineToMachineHttpServer.serverUrl() + MockMachineToMachineHttpServer.tokenPath
            }

            mockAmtEnhetsregiserServer.start()
            registry.add("amt-enhetsregister.url") { mockAmtEnhetsregiserServer.serverUrl() }
            registry.add("amt-enhetsregister.scope") { "test.enhetsregister.scope" }

            val container = SingletonPostgresContainer.getContainer()

            registry.add("spring.datasource.url") { container.jdbcUrl }
            registry.add("spring.datasource.username") { container.username }
            registry.add("spring.datasource.password") { container.password }
            registry.add("spring.datasource.hikari.maximum-pool-size") { 3 }
        }
    }

    fun sendRequest(
        method: String,
        path: String,
        body: RequestBody? = null,
        headers: Map<String, String> = emptyMap()
    ): Response {
        val reqBuilder = Request.Builder()
            .url("${serverUrl()}$path")
            .method(method, body)

        headers.forEach {
            reqBuilder.addHeader(it.key, it.value)
        }

        return client.newCall(reqBuilder.build()).execute()
    }
}

fun String.toJsonRequestBody(): RequestBody {
    val mediaTypeJson = "application/json".toMediaType()
    return this.toRequestBody(mediaTypeJson)
}
