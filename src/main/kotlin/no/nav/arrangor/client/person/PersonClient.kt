package no.nav.arrangor.client.person

import no.nav.arrangor.domain.Navn
import no.nav.arrangor.utils.JsonUtils
import no.nav.arrangor.utils.isFailure
import no.nav.common.rest.client.RestClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.function.Supplier

class PersonClient(
	private val baseUrl: String,
	private val tokenProvider: Supplier<String>,
	private val client: OkHttpClient = RestClient.baseClient(),
) {
	private val log = LoggerFactory.getLogger(javaClass)
	private val mediaTypeJson = "application/json".toMediaType()

	fun hentPersonalia(personident: String): Result<PersonResponse> {
		val request =
			Request
				.Builder()
				.url("$baseUrl/api/arrangor-ansatt")
				.addHeader("Authorization", "Bearer ${tokenProvider.get()}")
				.post(JsonUtils.toJson(PersonRequest(personident)).toRequestBody(mediaTypeJson))
				.build()

		val response =
			client
				.newCall(request)
				.execute()
				.also { res -> isFailure(res, log)?.let { ex -> return Result.failure(ex) } }
				.let { it.body?.string() ?: return Result.failure(IllegalStateException("Forventet body")) }
				.let { JsonUtils.fromJson<PersonResponse>(it) }
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
