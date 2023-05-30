package no.nav.arrangor.ansatt

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repositories.AnsattRepository
import no.nav.arrangor.ansatt.repositories.RolleRepository
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import javax.sql.DataSource

const val KOORDINATOR = "KOORDINATOR"
const val VEILEDER = "VEILEDER"

class AnsattRolleServiceTest : IntegrationTest() {

	@Autowired
	lateinit var rolleService: AnsattRolleService

	@Autowired
	lateinit var rolleRepository: RolleRepository

	@Autowired
	lateinit var arrangorRepository: ArrangorRepository

	@Autowired
	lateinit var dataSource: DataSource

	lateinit var db: DbTestData

	lateinit var ansatt: AnsattRepository.AnsattDbo

	lateinit var arrangorOne: ArrangorRepository.ArrangorDbo
	lateinit var arrangorTwo: ArrangorRepository.ArrangorDbo
	lateinit var arrangorThree: ArrangorRepository.ArrangorDbo

	@BeforeEach
	fun setUp() {
		db = DbTestData(NamedParameterJdbcTemplate(dataSource))

		ansatt = db.insertAnsatt()

		arrangorOne = db.insertArrangor()
		arrangorTwo = db.insertArrangor()
		arrangorThree = db.insertArrangor()
	}

	@AfterEach
	fun tearDown() {
		DbTestDataUtils.cleanDatabase(dataSource)
	}

	@Test
	fun `oppdaterRoller - nye roller, ingen gamle roller`() {
		mockAltinnServer.addRoller(
			ansatt.personident,
			AltinnAclClient.ResponseWrapper(
				listOf(
					AltinnAclClient.ResponseEntry(arrangorOne.organisasjonsnummer, listOf(KOORDINATOR)),
					AltinnAclClient.ResponseEntry(arrangorTwo.organisasjonsnummer, listOf(KOORDINATOR, VEILEDER))
				)
			)
		)

		val roller = rolleService.oppdaterRoller(ansatt.id, ansatt.personident)
			.also { it.isUpdated shouldBe true }
			.data

		roller.size shouldBe 3

		val rollerArrangorOne = roller.filter { it.arrangorId == arrangorOne.id }
		rollerArrangorOne.size shouldBe 1
		rollerArrangorOne[0].ansattId shouldBe ansatt.id
		rollerArrangorOne[0].rolle shouldBe AnsattRolle.KOORDINATOR
		rollerArrangorOne[0].gyldigTil shouldBe null

		val rollerArrangorTwo = roller.filter { it.arrangorId == arrangorTwo.id }
		rollerArrangorTwo.size shouldBe 2
		rollerArrangorTwo.map { it.rolle } shouldContainAll listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER)
	}

	@Test
	fun `oppdaterRoller - nye roller, ny arrangor - skal lagre roller og ny arrangor`() {
		val nyArrangorOrgnummer = "123456789"
		mockAltinnServer.addRoller(
			ansatt.personident,
			AltinnAclClient.ResponseWrapper(
				listOf(
					AltinnAclClient.ResponseEntry(nyArrangorOrgnummer, listOf(KOORDINATOR))
				)
			)
		)
		mockAmtEnhetsregiserServer.addVirksomhet(
			Virksomhet(
				organisasjonsnummer = nyArrangorOrgnummer,
				navn = "Ny Arrangør AS",
				overordnetEnhetOrganisasjonsnummer = "456",
				overordnetEnhetNavn = "overordnetArrangor"
			)
		)

		val roller = rolleService.oppdaterRoller(ansatt.id, ansatt.personident)
			.also { it.isUpdated shouldBe true }
			.data

		roller.size shouldBe 1

		val rolle = roller.first()
		rolle.ansattId shouldBe ansatt.id
		rolle.rolle shouldBe AnsattRolle.KOORDINATOR
		rolle.organisasjonsnummer shouldBe nyArrangorOrgnummer
		rolle.gyldigTil shouldBe null

		arrangorRepository.get(nyArrangorOrgnummer) shouldNotBe null
	}

	@Test
	fun `oppdaterRoller - likt i altinn og database - ingen endringer`() {
		rolleRepository.leggTilRoller(
			listOf(
				RolleRepository.RolleInput(ansatt.id, arrangorOne.organisasjonsnummer, AnsattRolle.KOORDINATOR)
			)
		)

		mockAltinnServer.addRoller(
			ansatt.personident,
			AltinnAclClient.ResponseWrapper(
				listOf(AltinnAclClient.ResponseEntry(arrangorOne.organisasjonsnummer, listOf(KOORDINATOR)))
			)
		)

		val roller = rolleService.oppdaterRoller(ansatt.id, ansatt.personident)
			.also { it.isUpdated shouldBe false }
			.data

		roller.size shouldBe 1
		roller[0].arrangorId shouldBe arrangorOne.id
		roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
	}

	@Test
	fun `oppdaterRoller - i database, men ingen i altinn - fjerner roller`() {
		rolleRepository.leggTilRoller(
			listOf(
				RolleRepository.RolleInput(ansatt.id, arrangorOne.organisasjonsnummer, AnsattRolle.KOORDINATOR)
			)
		)

		mockAltinnServer.addRoller(ansatt.personident, AltinnAclClient.ResponseWrapper(listOf()))

		val roller = rolleService.oppdaterRoller(ansatt.id, ansatt.personident)
			.also { it.isUpdated shouldBe true }
			.data

		roller.isEmpty() shouldBe true
	}
}
