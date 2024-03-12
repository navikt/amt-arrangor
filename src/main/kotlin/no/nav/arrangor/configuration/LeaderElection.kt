package no.nav.arrangor.configuration

import no.nav.arrangor.utils.JsonUtils.fromJson
import no.nav.common.rest.client.RestClient
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.util.UriComponentsBuilder
import java.net.InetAddress

@Component
class LeaderElection(
	@Value("\${elector.path}") private val electorPath: String,
) {
	private val client: OkHttpClient = RestClient.baseClient()
	private val log = LoggerFactory.getLogger(javaClass)

	fun isLeader(): Boolean {
		if (electorPath == "dont_look_for_leader") {
			log.info("Ser ikke etter leader, returnerer at jeg er leader")
			return true
		}
		return kallElector()
	}

	private fun kallElector(): Boolean {
		val hostname: String = InetAddress.getLocalHost().hostName

		val uriString =
			UriComponentsBuilder.fromHttpUrl(getHttpPath(electorPath))
				.toUriString()
		val request =
			Request.Builder()
				.url(uriString)
				.get()
				.build()

		client.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				val message = "Kall mot elector feiler med HTTP-${response.code}"
				log.error(message)
				throw RuntimeException(message)
			}

			response.body?.string()?.let {
				val leader: Leader = fromJson(it)
				return leader.name == hostname
			}
		}

		val message = "Kall mot elector returnerer ikke data"
		log.error(message)
		throw RuntimeException(message)
	}

	private fun getHttpPath(url: String): String = when (url.startsWith("http://")) {
		true -> url
		else -> "http://$url"
	}

	private data class Leader(val name: String)
}
