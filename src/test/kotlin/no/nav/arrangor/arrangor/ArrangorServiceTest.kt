package no.nav.arrangor.arrangor

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.arrangor.domain.Arrangor
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.utils.JsonUtils
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

class ArrangorServiceTest : IntegrationTest() {

    @Autowired
    lateinit var arrangorService: ArrangorService

    @Autowired
    lateinit var arrangorRepository: ArrangorRepository

    @Test
    internal fun `get - ikke finnes fra for, legger til i database`() {
        val orgNr = "123"

        arrangorRepository.get(orgNr) shouldBe null

        mockAmtEnhetsregiserServer.addVirksomhet(
            Virksomhet(
                organisasjonsnummer = orgNr,
                navn = "Navnesen",
                overordnetEnhetOrganisasjonsnummer = null,
                overordnetEnhetNavn = null
            )
        )

        val response = sendRequest(
            method = "GET",
            path = "/api/arrangor/organisasjonsnummer/$orgNr"
        )
            .also { it.code shouldBe 200 }
            .let { JsonUtils.fromJson<Arrangor>(it.body!!.string()) }

        response.organisasjonsnummer shouldBe orgNr
        response.overordnetArrangorId shouldBe null
    }

    @Test
    fun `get - Finnes fra for - oppdaterer`() {
        val input = ArrangorRepository.ArrangorInput(
            navn = "originaltNavn",
            organisasjonsnummer = "123",
            overordnetArrangorId = null
        )

        arrangorRepository.insertOrUpdateArrangor(input)

        mockAmtEnhetsregiserServer.addVirksomhet(
            Virksomhet(
                organisasjonsnummer = input.organisasjonsnummer,
                navn = "nyttNavn",
                overordnetEnhetOrganisasjonsnummer = "456",
                overordnetEnhetNavn = "overordnetArrangor"
            )
        )

        arrangorService.oppdaterArrangorer(synchronizedBefore = LocalDateTime.now().plusMinutes(1))

        val response = sendRequest(
            method = "GET",
            path = "/api/arrangor/organisasjonsnummer/${input.organisasjonsnummer}"
        )
            .also { it.code shouldBe 200 }
            .let { JsonUtils.fromJson<Arrangor>(it.body!!.string()) }

        response.navn shouldBe "nyttNavn"
        response.overordnetArrangorId shouldNotBe null
    }
}
