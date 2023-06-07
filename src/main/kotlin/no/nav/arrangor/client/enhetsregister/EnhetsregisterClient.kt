package no.nav.arrangor.client.enhetsregister

import no.nav.arrangor.utils.JsonUtils
import no.nav.arrangor.utils.isFailure
import no.nav.common.rest.client.RestClient.baseClient
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.Instant
import java.util.function.Supplier

class EnhetsregisterClient(
	private val baseUrl: String,
	private val tokenProvider: Supplier<String>,
	private val client: OkHttpClient = baseClient()
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun hentVirksomhet(orgNr: String): Result<Virksomhet> {
		val start = Instant.now()
		val request = Request.Builder()
			.url("$baseUrl/api/enhet/$orgNr")
			.addHeader("Authorization", "Bearer ${tokenProvider.get()}")
			.get()
			.build()

		val virksomhet = client.newCall(request).execute()
			.also { res -> isFailure(res, log)?.let { exception -> return Result.failure(exception) } }
			.let {
				it.body?.string()
					?: return Result.failure(IllegalStateException("Forventet body for organisasjonsnummer $orgNr"))
			}
			.let { JsonUtils.fromJson<Virksomhet>(it) }
			.also {
				log.info(
					"hentVirksomhet $orgNr executed in ${
					Duration.between(start, Instant.now()).toMillis()
					} ms."
				)
			}

		return Result.success(virksomhet)
	}
}
