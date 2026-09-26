package no.nav.arrangor.ansatt

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.arrangor.ansatt.repository.AnsattArrangorRepository
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.domain.AnsattRolle
import org.junit.jupiter.api.Test
import java.util.UUID

class AnsattArrangorReadServiceTest {
    private val ansattRepository = mockk<AnsattRepository>()
    private val ansattArrangorRepository = mockk<AnsattArrangorRepository>()
    private val featureToggle = mockk<AnsattArrangorFeatureToggle>()
    private val service = AnsattArrangorReadService(ansattRepository, ansattArrangorRepository, featureToggle)

    @Test
    fun `toggle av - beholder jsonb som lesekilde`() {
        val ansatt = ansattMedRolle(AnsattRolle.VEILEDER)
        every { featureToggle.lesFraNormaliserteTabeller() } returns false

        service.ansattMedValgtArrangorkilde(ansatt) shouldBe ansatt

        verify(exactly = 0) { ansattArrangorRepository.getArrangorerForAnsatte(any()) }
    }

    @Test
    fun `toggle pa - erstatter jsonb med normalisert tilstand`() {
        val ansatt = ansattMedRolle(AnsattRolle.VEILEDER)
        val normalisert = listOf(
            ArrangorDbo(
                arrangorId = UUID.randomUUID(),
                roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
                veileder = emptyList(),
                koordinator = emptyList(),
            ),
        )
        every { featureToggle.lesFraNormaliserteTabeller() } returns true
        every {
            ansattArrangorRepository.getArrangorerForAnsatte(listOf(ansatt.id))
        } returns mapOf(ansatt.id to normalisert)

        service.ansattMedValgtArrangorkilde(ansatt).arrangorer shouldBe normalisert
    }

    @Test
    fun `toggle pa - manglende normalisert tilstand faller ikke tilbake til jsonb`() {
        val ansatt = ansattMedRolle(AnsattRolle.VEILEDER)
        every { featureToggle.lesFraNormaliserteTabeller() } returns true
        every {
            ansattArrangorRepository.getArrangorerForAnsatte(listOf(ansatt.id))
        } returns mapOf(ansatt.id to emptyList())

        service.ansattMedValgtArrangorkilde(ansatt).arrangorer.shouldBeEmpty()
    }

    @Test
    fun `normalisert lesekilde - bulkendring bruker normaliserte ansattider`() {
        val jsonbAnsatt = ansattMedRolle(AnsattRolle.VEILEDER)
        val normalisertAnsatt = ansattMedRolle(AnsattRolle.KOORDINATOR)
        val endring = EndredeAnsatte(
            ansatteEndretIJsonb = listOf(jsonbAnsatt),
            ansattIderEndretINormaliserteTabeller = listOf(normalisertAnsatt.id),
        )
        every { featureToggle.lesFraNormaliserteTabeller() } returns true
        every { ansattRepository.getAnsatte(listOf(normalisertAnsatt.id)) } returns listOf(normalisertAnsatt)
        every {
            ansattArrangorRepository.getArrangorerForAnsatte(listOf(normalisertAnsatt.id))
        } returns mapOf(normalisertAnsatt.id to normalisertAnsatt.arrangorer)

        service.endredeAnsatteFraValgtKilde(endring) shouldBe listOf(normalisertAnsatt)
    }

    @Test
    fun `toggle av - arrangoroppslag bruker jsonb`() {
        val arrangorId = UUID.randomUUID()
        val ansatt = ansattMedRolle(AnsattRolle.VEILEDER)
        every { featureToggle.lesFraNormaliserteTabeller() } returns false
        every { ansattRepository.getAnsatteHosArrangor(arrangorId) } returns listOf(ansatt)

        service.getAnsatteHosArrangor(arrangorId) shouldBe listOf(ansatt)

        verify(exactly = 0) { ansattArrangorRepository.getAnsattIderForArrangor(any()) }
    }

    @Test
    fun `toggle pa - arrangoroppslag bruker normaliserte tabeller`() {
        val arrangorId = UUID.randomUUID()
        val ansatt = ansattMedRolle(AnsattRolle.KOORDINATOR)
        every { featureToggle.lesFraNormaliserteTabeller() } returns true
        every { ansattArrangorRepository.getAnsattIderForArrangor(arrangorId) } returns listOf(ansatt.id)
        every { ansattRepository.getAnsatte(listOf(ansatt.id)) } returns listOf(ansatt)
        every {
            ansattArrangorRepository.getArrangorerForAnsatte(listOf(ansatt.id))
        } returns mapOf(ansatt.id to ansatt.arrangorer)

        service.getAnsatteHosArrangor(arrangorId) shouldBe listOf(ansatt)

        verify(exactly = 0) { ansattRepository.getAnsatteHosArrangor(any()) }
    }

    private fun ansattMedRolle(rolle: AnsattRolle): AnsattDbo = AnsattDbo(
        id = UUID.randomUUID(),
        personId = UUID.randomUUID(),
        personident = UUID.randomUUID().toString(),
        fornavn = "Fornavn",
        mellomnavn = null,
        etternavn = "Etternavn",
        arrangorer = listOf(
            ArrangorDbo(
                arrangorId = UUID.randomUUID(),
                roller = listOf(RolleDbo(rolle)),
                veileder = emptyList(),
                koordinator = emptyList(),
            ),
        ),
    )
}
