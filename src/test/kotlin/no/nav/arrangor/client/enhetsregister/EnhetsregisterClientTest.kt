package no.nav.arrangor.client.enhetsregister

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.util.function.Supplier

class EnhetsregisterClientTest {
    private val tokenProvider = Supplier { "token" }
    private val objectMapper = ObjectMapper()

    private fun newClient(
        baseUrl: String,
        allowedHosts: Set<String> = setOf("data.brreg.no"),
    ) = EnhetsregisterClient(
        baseUrl = baseUrl,
        tokenProvider = tokenProvider,
        objectMapper = objectMapper,
        allowedHosts = allowedHosts,
    )

    @Test
    fun `validateBaseUrl - host matcher eksakt i allowedHosts - oppretter klient`() {
        newClient("https://data.brreg.no")
    }

    @Test
    fun `validateBaseUrl - subdomene av allowed host - oppretter klient`() {
        newClient("https://api.data.brreg.no")
    }

    @Test
    fun `validateBaseUrl - http er tillatt (SSRF-sjekk er pa allowedHosts, ikke skjema)`() {
        newClient("http://data.brreg.no")
    }

    @Test
    fun `validateBaseUrl - host matcher ikke allowedHosts - kaster IllegalArgumentException`() {
        val ex = shouldThrow<IllegalArgumentException> {
            newClient("https://evil.example.com")
        }
        ex.message shouldContain "host er ikke tillatt"
    }

    @Test
    fun `validateBaseUrl - host ender pa allowed uten punktum foran - kaster IllegalArgumentException`() {
        val ex = shouldThrow<IllegalArgumentException> {
            // "evildata.brreg.no" inneholder "data.brreg.no" men er ikke et ekte subdomene
            newClient("https://evildata.brreg.no", allowedHosts = setOf("data.brreg.no"))
        }
        ex.message shouldContain "host er ikke tillatt"
    }

    @Test
    fun `validateBaseUrl - ugyldig URL - kaster IllegalArgumentException`() {
        val ex = shouldThrow<IllegalArgumentException> {
            newClient("ikke en url")
        }
        ex.message shouldContain "Ugyldig baseUrl"
    }

    @Test
    fun `validateBaseUrl - tom allowedHosts - kaster IllegalArgumentException`() {
        val ex = shouldThrow<IllegalArgumentException> {
            newClient("https://data.brreg.no", allowedHosts = emptySet())
        }
        ex.message shouldContain "allowedHosts"
    }

    @Test
    fun `validateBaseUrl - flere allowedHosts, matcher en - oppretter klient`() {
        newClient(
            baseUrl = "http://amt-enhetsregister.amt.svc.cluster.local",
            allowedHosts = setOf("data.brreg.no", "amt-enhetsregister.amt.svc.cluster.local"),
        )
    }

    @Test
    fun `hentVirksomhet - orgnr med farre enn 9 siffer - returnerer failure`() {
        val result = newClient("https://data.brreg.no").hentVirksomhet("12345678")

        result.isFailure shouldBe true
        val ex = result.exceptionOrNull()
        ex.shouldBeInstanceOf<IllegalArgumentException>()
        ex.message shouldContain "Ugyldig organisasjonsnummer"
    }

    @Test
    fun `hentVirksomhet - orgnr med flere enn 9 siffer - returnerer failure`() {
        val result = newClient("https://data.brreg.no").hentVirksomhet("1234567890")

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `hentVirksomhet - orgnr med bokstaver - returnerer failure`() {
        val result = newClient("https://data.brreg.no").hentVirksomhet("12345abcd")

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }

    @Test
    fun `hentVirksomhet - tomt orgnr - returnerer failure`() {
        val result = newClient("https://data.brreg.no").hentVirksomhet("")

        result.isFailure shouldBe true
        result.exceptionOrNull().shouldBeInstanceOf<IllegalArgumentException>()
    }
}




