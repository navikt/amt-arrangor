package no.nav.arrangor.configuration

import no.nav.arrangor.utils.UrlUtils.toUriString
import no.nav.common.rest.client.RestClient
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.net.InetAddress

@Component
class LeaderElection(
	@Value($$"${elector.path}") private val electorPath: String,
	private val objectMapper: ObjectMapper,
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

		val uriString = toUriString(electorPath)

		val request =
			Request
				.Builder()
				.url(uriString)
				.get()
				.build()

		client.newCall(request).execute().use { response ->
			if (!response.isSuccessful) {
				val message = "Kall mot elector feiler med HTTP-${response.code}"
				log.error(message)
				throw RuntimeException(message)
			}

			response.body.string().let {
				val leader: Leader = objectMapper.readValue(it)
				return leader.name == hostname
			}
		}
	}

	private data class Leader(
		val name: String,
	)
}
