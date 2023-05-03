package no.nav.arrangor.arrangor

import io.kotest.matchers.shouldBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.client.enhetsregister.Virksomhet
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.*

class ArrangorServiceTest : IntegrationTest() {

    @Autowired
    lateinit var arrangorService: ArrangorService

    @Test
    internal fun test() {
        mockAmtEnhetsregiserServer.addVirksomhet(
            Virksomhet(
                organisasjonsnummer = "123",
                navn = "Navnesen",
                overordnetEnhetOrganisasjonsnummer = null,
                overordnetEnhetNavn = null
            )
        )

        val response = sendRequest(
            method = "GET",
            path = "/api/arrangor/organisasjonsnummer/123"
        )

        println(response)

        "a" shouldBe "a"
    }
}
