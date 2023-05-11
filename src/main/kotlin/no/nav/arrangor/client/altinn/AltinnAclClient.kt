package no.nav.arrangor.client.altinn

import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.utils.JsonUtils
import no.nav.arrangor.utils.isFailure
import no.nav.common.rest.client.RestClient
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import java.util.function.Supplier

class AltinnAclClient(
	private val baseUrl: String,
	private val tokenProvider: Supplier<String>,
	private val client: OkHttpClient = RestClient.baseClient()
) {

	private val log = LoggerFactory.getLogger(javaClass)

	fun hentRoller(personident: String): Result<List<AltinnRolle>> {
		val request = Request.Builder()
			.url("$baseUrl/api/v1/rolle/tiltaksarrangor?norskIdent=$personident")
			.addHeader("Authorization", "Bearer ${tokenProvider.get()}")
			.get()
			.build()

		val roller = client.newCall(request).execute()
			.also { res -> isFailure(res, log)?.let { ex -> return Result.failure(ex) } }
			.let { it.body?.string() ?: return Result.failure(IllegalStateException("Forventet body")) }
			.let { JsonUtils.fromJson<ResponseWrapper>(it).roller }
			.map { roller -> AltinnRolle(roller.organisasjonsnummer, roller.roller.map(::mapTiltaksarrangorRolle)) }
			.also { log.debug("Hentet roller for person") }

		return Result.success(roller)
	}

	private fun mapTiltaksarrangorRolle(rolle: String): AnsattRolle {
		return when (rolle) {
			"KOORDINATOR" -> AnsattRolle.KOORDINATOR
			"VEILEDER" -> AnsattRolle.VEILEDER
			else -> throw IllegalArgumentException("Ukjent tiltaksarrangør rolle $rolle")
		}
	}

	data class ResponseWrapper(
		val roller: List<ResponseEntry>
	)

	data class ResponseEntry(
		val organisasjonsnummer: String,
		val roller: List<String>
	)
}
