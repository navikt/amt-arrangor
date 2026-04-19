package no.nav.arrangor.client.enhetsregister

import no.nav.arrangor.utils.Orgnummer
import no.nav.arrangor.utils.isFailure
import no.nav.common.rest.client.RestClient.baseClient
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.time.Duration
import java.time.Instant
import java.util.function.Supplier

class EnhetsregisterClient(
    baseUrl: String,
    private val tokenProvider: Supplier<String>,
    private val objectMapper: ObjectMapper,
    private val allowedHosts: Set<String>,
    private val client: OkHttpClient = baseClient(),
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val validatedBaseUrl = validateBaseUrl(baseUrl)

    private fun validateBaseUrl(url: String): HttpUrl {
        require(allowedHosts.isNotEmpty()) {
            "allowedHosts for Enhetsregister må være konfigurert (baseUrl=$url)"
        }

        val parsed = url.toHttpUrlOrNull()
            ?: throw IllegalArgumentException("Ugyldig baseUrl for Enhetsregister: baseUrl=$url")

        val host = parsed.host
        val hostAllowed = allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }

        if (!hostAllowed) {
            throw IllegalArgumentException(
                "Ugyldig baseUrl for Enhetsregister: baseUrl=$url, host=$host, allowedHosts=$allowedHosts",
            )
        }

        return parsed
    }

    fun hentVirksomhet(orgNr: String): Result<Virksomhet> {
        if (!Orgnummer.erGyldig(orgNr)) {
            return Result.failure(IllegalArgumentException("Ugyldig organisasjonsnummer"))
        }

        val start = Instant.now()
        val url = validatedBaseUrl
            .newBuilder()
            .addPathSegment("api")
            .addPathSegment("enhet")
            .addPathSegment(orgNr)
            .build()

        val request = Request
            .Builder()
            .url(url)
            .addHeader(HttpHeaders.AUTHORIZATION, "Bearer ${tokenProvider.get()}")
            .get()
            .build()

        val virksomhet = client
            .newCall(request)
            .execute()
            .also { res -> isFailure(res, log)?.let { exception -> return Result.failure(exception) } }
            .body
            .string()
            .let { objectMapper.readValue<Virksomhet>(it) }
            .also {
                log.info(
                    "hentVirksomhet $orgNr executed in ${
                        Duration.between(start, Instant.now()).toMillis()
                    } ms.",
                )
            }

        return Result.success(virksomhet)
    }
}
