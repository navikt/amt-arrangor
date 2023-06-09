package no.nav.arrangor.utils

import okhttp3.Response
import org.slf4j.Logger
import org.springframework.http.HttpStatusCode
import org.springframework.web.client.HttpClientErrorException

fun isFailure(response: Response, log: Logger): Exception? {
	if (response.code == 404) {
		log.warn("Fikk responskode 404 på utgående kall")
		return NoSuchElementException("[${response.request.method}] ${response.request.url}: 404").also { response.close() }
	} else if (!response.isSuccessful) {
		val errorMessage =
			"[${response.request.method}] ${response.request.url}: Expected call to succeed, was ${response.code}"
		log.error(errorMessage)
		return HttpClientErrorException(HttpStatusCode.valueOf(response.code), errorMessage).also { response.close() }
	}

	return null
}
