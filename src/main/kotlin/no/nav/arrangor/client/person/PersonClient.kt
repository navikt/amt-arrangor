package no.nav.arrangor.client.person

import no.nav.arrangor.domain.Navn
import no.nav.arrangor.utils.isFailure
import no.nav.common.rest.client.RestClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.util.UUID
import java.util.function.Supplier

class PersonClient(
	private val baseUrl: String,
	private val tokenProvider: Supplier<String>,
	private val objectMapper: ObjectMapper,
	private val client: OkHttpClient = RestClient.baseClient(),
) {
	private val log = LoggerFactory.getLogger(javaClass)
	private val mediaTypeJson = MediaType.APPLICATION_JSON_VALUE.toMediaType()

	fun hentPersonalia(personident: String): Result<PersonResponse> {
		val request =
			Request
				.Builder()
				.url("$baseUrl/api/arrangor-ansatt")
				.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ${tokenProvider.get()}")
				.post(objectMapper.writeValueAsString(PersonRequest(personident)).toRequestBody(mediaTypeJson))
				.build()

		val response =
			client
				.newCall(request)
				.execute()
				.also { res -> isFailure(res, log)?.let { ex -> return Result.failure(ex) } }
				.body
				.string()
				.let { objectMapper.readValue<PersonResponse>(it) }
				.also { log.debug("Hentet personalia for person") }

		return Result.success(response)
	}

	data class PersonRequest(
		val personident: String,
	)

	data class PersonResponse(
		val id: UUID,
		val personident: String,
		val fornavn: String,
		val mellomnavn: String?,
		val etternavn: String,
	) {
		fun navn() = Navn(fornavn, mellomnavn, etternavn)
	}
}
