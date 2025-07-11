package no.nav.arrangor.utils

import org.springframework.web.util.UriComponentsBuilder

object UrlUtils {
	fun toUriString(pathToVerify: String): String = UriComponentsBuilder
		.fromUriString(getHttpPath(pathToVerify), UriComponentsBuilder.ParserType.RFC)
		.toUriString()

	private fun getHttpPath(url: String): String = when (url.startsWith("http://")) {
		true -> url
		else -> "http://$url"
	}
}
