package no.nav.arrangor.client.person

import no.nav.arrangor.domain.Navn
import no.nav.arrangor.domain.Personalia
import no.nav.common.rest.client.RestClient
import okhttp3.OkHttpClient
import org.slf4j.LoggerFactory
import java.util.function.Supplier

class PersonClient(
    private val baseUrl: String,
    private val tokenProvider: Supplier<String>,
    private val client: OkHttpClient = RestClient.baseClient()
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun hentPersonalia(personident: String): Personalia {
        return Personalia(
            personident = personident,
            navn = Navn(
                fornavn = "Test",
                mellomnavn = "Mellom",
                etternavn = "Testersen"
            )
        )
    }
}
