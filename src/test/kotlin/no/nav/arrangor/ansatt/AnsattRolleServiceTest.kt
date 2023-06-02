package no.nav.arrangor.ansatt

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.UUID
import javax.sql.DataSource

const val KOORDINATOR = "KOORDINATOR"
const val VEILEDER = "VEILEDER"

class AnsattRolleServiceTest : IntegrationTest() {

	@Autowired
	lateinit var rolleService: AnsattRolleService

	@Autowired
	lateinit var ansattRepository: AnsattRepository

	@Autowired
	lateinit var dataSource: DataSource

	lateinit var db: DbTestData

	lateinit var ansatt: AnsattDbo

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

		val ansattDbo = rolleService.getAnsattDboMedOppdaterteRoller(ansatt, ansatt.personident)
			.also { it.isUpdated shouldBe true }
			.data

		ansattDbo.arrangorer.size shouldBe 2

		val arrangorOne = ansattDbo.arrangorer.find { it.arrangorId == arrangorOne.id }
		arrangorOne!!.roller.size shouldBe 1
		arrangorOne.roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
		arrangorOne.roller[0].gyldigTil shouldBe null

		val arrangorTwo = ansattDbo.arrangorer.find { it.arrangorId == arrangorTwo.id }
		arrangorTwo!!.roller.size shouldBe 2
		arrangorTwo.roller.map { it.rolle } shouldContainAll listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER)
	}

	/*@Test Kommentert ut frem til amt-arrangør er master
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
	}*/

	@Test
	fun `oppdaterRoller - likt i altinn og database - ingen endringer`() {
		ansattRepository.insertOrUpdate(
			ansatt.copy(
				arrangorer = listOf(
					ArrangorDbo(
						arrangorId = arrangorOne.id,
						roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
						veileder = emptyList(),
						koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID()))
					)
				)
			)
		)

		mockAltinnServer.addRoller(
			ansatt.personident,
			AltinnAclClient.ResponseWrapper(
				listOf(AltinnAclClient.ResponseEntry(arrangorOne.organisasjonsnummer, listOf(KOORDINATOR)))
			)
		)

		val ansattDbo = rolleService.getAnsattDboMedOppdaterteRoller(ansatt, ansatt.personident)
			.also { it.isUpdated shouldBe false }
			.data

		ansattDbo.arrangorer.size shouldBe 1
		ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
		ansattDbo.arrangorer[0].roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
		ansattDbo.arrangorer[0].roller[0].gyldigTil shouldBe null
		ansattDbo.arrangorer[0].koordinator[0].gyldigTil shouldBe null
	}

	@Test
	fun `oppdaterRoller - i database, men ingen i altinn - fjerner roller`() {
		ansattRepository.insertOrUpdate(
			ansatt.copy(
				arrangorer = listOf(
					ArrangorDbo(
						arrangorId = arrangorOne.id,
						roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
						veileder = emptyList(),
						koordinator = emptyList()
					)
				)
			)
		)

		mockAltinnServer.addRoller(ansatt.personident, AltinnAclClient.ResponseWrapper(listOf()))

		val ansattDbo = rolleService.getAnsattDboMedOppdaterteRoller(ansatt, ansatt.personident)
			.also { it.isUpdated shouldBe true }
			.data

		ansattDbo.arrangorer.size shouldBe 1
		ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
		ansattDbo.arrangorer[0].roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
		ansattDbo.arrangorer[0].roller[0].gyldigTil shouldNotBe null
	}

	@Test
	fun `oppdaterRoller - mister tilgang til arrangor og er veileder og har lagt til deltakerlister - fjerner roller og tilganger`() {
		val deltakerId = UUID.randomUUID()
		ansattRepository.insertOrUpdate(
			ansatt.copy(
				arrangorer = listOf(
					ArrangorDbo(
						arrangorId = arrangorOne.id,
						roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR), RolleDbo(AnsattRolle.VEILEDER)),
						veileder = listOf(VeilederDeltakerDbo(deltakerId, VeilederType.VEILEDER)),
						koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID()))
					)
				)
			)
		)

		mockAltinnServer.addRoller(ansatt.personident, AltinnAclClient.ResponseWrapper(listOf()))

		val ansattDbo = rolleService.getAnsattDboMedOppdaterteRoller(ansatt, ansatt.personident)
			.also { it.isUpdated shouldBe true }
			.data

		ansattDbo.arrangorer.size shouldBe 1
		ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
		ansattDbo.arrangorer[0].roller.size shouldBe 2
		ansattDbo.arrangorer[0].roller.filter { it.erGyldig() }.size shouldBe 0
		ansattDbo.arrangorer[0].veileder[0].gyldigTil shouldNotBe null
		ansattDbo.arrangorer[0].koordinator[0].gyldigTil shouldNotBe null
	}
}
