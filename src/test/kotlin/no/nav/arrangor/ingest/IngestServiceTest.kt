package no.nav.arrangor.ingest

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.ingest.model.VirksomhetDto
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.UUID
import javax.sql.DataSource

class IngestServiceTest : IntegrationTest() {

	@Autowired
	private lateinit var datasource: DataSource

	@Autowired
	private lateinit var ingestService: IngestService

	@Autowired
	private lateinit var arrangorRepository: ArrangorRepository

	private lateinit var db: DbTestData

	val personIdent = "12345678910"
	val personId: UUID = UUID.randomUUID()

	@BeforeEach
	fun setUp() {
		db = DbTestData(NamedParameterJdbcTemplate(datasource))
		mockPersonServer.setPerson(personIdent, personId, "Test", null, "Testersen")
	}

	@AfterEach
	fun tearDown() {
		DbTestDataUtils.cleanDatabase(datasource)
		resetMockServers()
	}

	@Test
	fun `handleVirksomhetEndring - finnes i db med annet navn - arrangornavn oppdateres i db`() {
		val overordnetArrangorId = UUID.randomUUID()
		val overordnetOrgnummer = "888887776"
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = overordnetArrangorId,
				navn = "Overordnet arrangør",
				organisasjonsnummer = overordnetOrgnummer,
				overordnetArrangorId = null
			)
		)

		val arrangorId = UUID.randomUUID()
		val orgnummer = "999988888"
		mockAmtEnhetsregiserServer.addVirksomhet(
			Virksomhet(
				organisasjonsnummer = orgnummer,
				navn = "Arrangør",
				overordnetEnhetOrganisasjonsnummer = overordnetOrgnummer,
				overordnetEnhetNavn = "Overordnet arrangør"
			)
		)
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = arrangorId,
				navn = UUID.randomUUID().toString(),
				organisasjonsnummer = orgnummer,
				overordnetArrangorId = overordnetArrangorId
			)
		)

		ingestService.handleVirksomhetEndring(
			VirksomhetDto(
				organisasjonsnummer = orgnummer,
				navn = "Nytt navn",
				overordnetEnhetOrganisasjonsnummer = overordnetOrgnummer
			)
		)

		val oppdatertArrangor = arrangorRepository.get(arrangorId)
		oppdatertArrangor?.navn shouldBe "Nytt navn"
		oppdatertArrangor?.overordnetArrangorId shouldBe overordnetArrangorId
	}

	@Test
	fun `handleVirksomhetEndring - finnes i db med annen overordnet arrangor og ny overordnet arrangor finnes i db - overordnet arrangor oppdateres i db`() {
		val overordnetArrangorId = UUID.randomUUID()
		val overordnetOrgnummer = "888887776"
		val nyOverordnetArrangorId = UUID.randomUUID()
		val nyOverordnetOrgnummer = "111122222"
		val arrangorId = UUID.randomUUID()
		val orgnummer = "999988888"
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = overordnetArrangorId,
				navn = "Overordnet navn",
				organisasjonsnummer = overordnetOrgnummer,
				overordnetArrangorId = null
			)
		)
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = nyOverordnetArrangorId,
				navn = "Ny overordnet navn",
				organisasjonsnummer = nyOverordnetOrgnummer,
				overordnetArrangorId = null
			)
		)
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = arrangorId,
				navn = "Navn",
				organisasjonsnummer = orgnummer,
				overordnetArrangorId = overordnetArrangorId
			)
		)

		ingestService.handleVirksomhetEndring(
			VirksomhetDto(
				organisasjonsnummer = orgnummer,
				navn = "Nytt navn",
				overordnetEnhetOrganisasjonsnummer = nyOverordnetOrgnummer
			)
		)

		val oppdatertArrangor = arrangorRepository.get(arrangorId)
		oppdatertArrangor?.navn shouldBe "Nytt navn"
		oppdatertArrangor?.overordnetArrangorId shouldBe nyOverordnetArrangorId
	}

	@Test
	fun `handleVirksomhetEndring - finnes i db med annen overordnet arrangor og ny overordnet arrangor finnes ikke i db - ny overordnet arrangor lagres i db`() {
		val overordnetArrangorId = UUID.randomUUID()
		val overordnetOrgnummer = "888887776"
		val nyOverordnetOrgnummer = "111122222"
		val arrangorId = UUID.randomUUID()
		val orgnummer = "999988888"
		mockAmtEnhetsregiserServer.addVirksomhet(
			Virksomhet(
				organisasjonsnummer = nyOverordnetOrgnummer,
				navn = "Ny Overordnet arrangør",
				overordnetEnhetOrganisasjonsnummer = null,
				overordnetEnhetNavn = null
			)
		)
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = overordnetArrangorId,
				navn = "Overordnet navn",
				organisasjonsnummer = overordnetOrgnummer,
				overordnetArrangorId = null
			)
		)
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = arrangorId,
				navn = "Navn",
				organisasjonsnummer = orgnummer,
				overordnetArrangorId = overordnetArrangorId
			)
		)

		ingestService.handleVirksomhetEndring(
			VirksomhetDto(
				organisasjonsnummer = orgnummer,
				navn = "Nytt navn",
				overordnetEnhetOrganisasjonsnummer = nyOverordnetOrgnummer
			)
		)

		val oppdatertArrangor = arrangorRepository.get(arrangorId)
		oppdatertArrangor?.navn shouldBe "Nytt navn"
		oppdatertArrangor?.overordnetArrangorId shouldNotBe overordnetArrangorId
		val nyOverordnetArrangor = arrangorRepository.get(nyOverordnetOrgnummer)
		nyOverordnetArrangor shouldNotBe null
		nyOverordnetArrangor?.navn shouldBe "Ny Overordnet arrangør"
		oppdatertArrangor?.overordnetArrangorId shouldBe nyOverordnetArrangor?.id
	}
}
