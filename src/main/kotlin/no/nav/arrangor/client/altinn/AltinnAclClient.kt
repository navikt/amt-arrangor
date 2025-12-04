package no.nav.arrangor.client.altinn

import no.nav.arrangor.domain.AnsattRolle
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
import java.util.function.Supplier

class AltinnAclClient(
	private val baseUrl: String,
	private val tokenProvider: Supplier<String>,
	private val objectMapper: ObjectMapper,
	private val client: OkHttpClient = RestClient.baseClient(),
) {
	private val log = LoggerFactory.getLogger(javaClass)

	fun hentRoller(personident: String): Result<List<AltinnRolle>> {
		val request =
			Request
				.Builder()
				.url("$baseUrl/api/v1/rolle/tiltaksarrangor")
				.addHeader(HttpHeaders.AUTHORIZATION, "Bearer ${tokenProvider.get()}")
				.post(
					objectMapper
						.writeValueAsString(HentRollerRequest(personident))
						.toRequestBody(MediaType.APPLICATION_JSON_VALUE.toMediaType()),
				).build()

		val roller =
			client
				.newCall(request)
				.execute()
				.also { res -> isFailure(res, log)?.let { ex -> return Result.failure(ex) } }
				.body
				.string()
				.let { objectMapper.readValue<ResponseWrapper>(it).roller }
				.map { roller -> AltinnRolle(roller.organisasjonsnummer, roller.roller.map(::mapTiltaksarrangorRolle)) }
				.also { log.debug("Hentet roller for person") }

		return Result.success(roller)
	}

	private fun mapTiltaksarrangorRolle(rolle: String): AnsattRolle = when (rolle) {
		"KOORDINATOR" -> AnsattRolle.KOORDINATOR
		"VEILEDER" -> AnsattRolle.VEILEDER
		else -> throw IllegalArgumentException("Ukjent tiltaksarrangør rolle $rolle")
	}

	data class HentRollerRequest(
		val personident: String,
	)

	data class ResponseWrapper(
		val roller: List<ResponseEntry>,
	)

	data class ResponseEntry(
		val organisasjonsnummer: String,
		val roller: List<String>,
	)
}
