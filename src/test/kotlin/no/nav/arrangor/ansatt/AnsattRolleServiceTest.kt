package no.nav.arrangor.ansatt

import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.altinn.AltinnAclClient
import no.nav.arrangor.client.altinn.AltinnRolle
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class AnsattRolleServiceTest : IntegrationTest() {

	@Autowired
	lateinit var rolleService: AnsattRolleService

	@Autowired
	lateinit var arrangorRepository: ArrangorRepository

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

		arrangorOne = db.insertArrangor()
		arrangorTwo = db.insertArrangor()
		arrangorThree = db.insertArrangor()
	}

	@AfterEach
	fun tearDown() {
		DbTestDataUtils.cleanDatabase(dataSource)
	}

	@Test
	fun `mapAltinnRollerTilArrangorListeForNyAnsatt - alle arrangorer finnes - returnerer liste`() {
		val altinnRoller = listOf(
			AltinnRolle(arrangorOne.organisasjonsnummer, listOf(AnsattRolle.KOORDINATOR)),
			AltinnRolle(arrangorTwo.organisasjonsnummer, listOf(AnsattRolle.VEILEDER))
		)

		val arrangorliste = rolleService.mapAltinnRollerTilArrangorListeForNyAnsatt(altinnRoller)

		arrangorliste.size shouldBe 2
		val arrangor = arrangorliste.find { it.arrangorId == arrangorOne.id } ?: throw RuntimeException("Mangler arrangør")
		arrangor.roller.size shouldBe 1
		arrangor.roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
		arrangor.roller[0].erGyldig() shouldBe true
		arrangor.veileder.size shouldBe 0
		arrangor.koordinator.size shouldBe 0

		val arrangor2 = arrangorliste.find { it.arrangorId == arrangorTwo.id } ?: throw RuntimeException("Mangler arrangør")
		arrangor2.roller.size shouldBe 1
		arrangor2.roller[0].rolle shouldBe AnsattRolle.VEILEDER
		arrangor2.roller[0].erGyldig() shouldBe true
		arrangor2.veileder.size shouldBe 0
		arrangor2.koordinator.size shouldBe 0
	}

	@Test
	fun `mapAltinnRollerTilArrangorListeForNyAnsatt - en arrangor mangler - lagrer arrangor som mangler`() {
		val nyttOrgnummer = "112233"
		mockAmtEnhetsregiserServer.addVirksomhet(
			Virksomhet(
				organisasjonsnummer = nyttOrgnummer,
				navn = "Ny Arrangør AS",
				overordnetEnhetOrganisasjonsnummer = "456",
				overordnetEnhetNavn = "overordnetArrangor"
			)
		)
		val altinnRoller = listOf(
			AltinnRolle(arrangorOne.organisasjonsnummer, listOf(AnsattRolle.KOORDINATOR)),
			AltinnRolle(nyttOrgnummer, listOf(AnsattRolle.VEILEDER))
		)

		val arrangorliste = rolleService.mapAltinnRollerTilArrangorListeForNyAnsatt(altinnRoller)

		arrangorliste.size shouldBe 2
		arrangorliste.find { it.arrangorId == arrangorOne.id } shouldNotBe null
	}

	@Test
	fun `getAnsattDboMedOppdaterteRoller - to nye roller, en eksisterende rolle - returnerer objekt med korrekte roller`() {
		ansatt = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
					veileder = emptyList(),
					koordinator = emptyList()
				)
			),
			lastSynchronized = LocalDateTime.now().minusMonths(1)
		)

		mockAltinnServer.addRoller(
			ansatt.personident,
			mapOf(
				arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR),
				arrangorTwo.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER)
			)
		)

		val ansattDbo = rolleService.getAnsattDboMedOppdaterteRoller(ansatt)
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

		ansattDbo.lastSynchronized.shouldBeWithin(Duration.ofSeconds(10), LocalDateTime.now())
	}

	@Test
	fun `getAnsattDboMedOppdaterteRoller - eksisterende rolle er utlopt men aktivert igjen fra altinn - ny rolle opprettes`() {
		ansatt = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR, gyldigTil = ZonedDateTime.now().minusDays(5))),
					veileder = emptyList(),
					koordinator = emptyList()
				)
			)
		)
		mockAltinnServer.addRoller(
			ansatt.personident,
			mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR))
		)

		val ansattDbo = rolleService.getAnsattDboMedOppdaterteRoller(ansatt)
			.also { it.isUpdated shouldBe true }
			.data

		ansattDbo.arrangorer.size shouldBe 1

		val arrangorOne = ansattDbo.arrangorer[0]
		arrangorOne.roller.size shouldBe 2

		val deaktivertRolle = arrangorOne.roller.first { !it.erGyldig() }
		val gyldigRolle = arrangorOne.roller.first { it.erGyldig() }

		gyldigRolle.rolle shouldBe AnsattRolle.KOORDINATOR
		gyldigRolle.gyldigTil shouldBe null
		gyldigRolle.erGyldig() shouldBe true

		deaktivertRolle.rolle shouldBe AnsattRolle.KOORDINATOR
		deaktivertRolle.gyldigTil shouldNotBe null
		deaktivertRolle.erGyldig() shouldBe false
	}

	@Test
	fun `mapAltinnRollerTilArrangorListeForNyAnsatt - nye roller, ny arrangor - returnerer objekt med korrekte roller og lagrer ny arrangor`() {
		ansatt = db.insertAnsatt(arrangorer = listOf(ArrangorDbo(arrangorOne.id, listOf(RolleDbo(AnsattRolle.KOORDINATOR, gyldigTil = ZonedDateTime.now().minusDays(5))), emptyList(), emptyList())))
		val nyArrangorOrgnummer = "123456789"
		mockAltinnServer.addRoller(
			ansatt.personident,
			mapOf(nyArrangorOrgnummer to listOf(AnsattRolle.KOORDINATOR))
		)
		mockAmtEnhetsregiserServer.addVirksomhet(
			Virksomhet(
				organisasjonsnummer = nyArrangorOrgnummer,
				navn = "Ny Arrangør AS",
				overordnetEnhetOrganisasjonsnummer = "456",
				overordnetEnhetNavn = "overordnetArrangor"
			)
		)

		val ansattDbo = rolleService.getAnsattDboMedOppdaterteRoller(ansatt)
			.also { it.isUpdated shouldBe true }
			.data

		ansattDbo.arrangorer.size shouldBe 2

		val nyArrangor = ansattDbo.arrangorer.find { it.arrangorId != arrangorOne.id }
		nyArrangor!!.roller.size shouldBe 1
		nyArrangor.roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
		nyArrangor.roller[0].gyldigTil shouldBe null

		arrangorRepository.get(nyArrangorOrgnummer) shouldNotBe null
	}

	@Test
	fun `getAnsattDboMedOppdaterteRoller - likt i altinn og database - ingen endringer`() {
		ansatt = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
					veileder = emptyList(),
					koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID()))
				)
			)
		)

		mockAltinnServer.addRoller(
			ansatt.personident,
			mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR))
		)

		val ansattDbo = rolleService.getAnsattDboMedOppdaterteRoller(ansatt)
			.also { it.isUpdated shouldBe false }
			.data

		ansattDbo.arrangorer.size shouldBe 1
		ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
		ansattDbo.arrangorer[0].roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
		ansattDbo.arrangorer[0].roller[0].gyldigTil shouldBe null
		ansattDbo.arrangorer[0].koordinator[0].gyldigTil shouldBe null
	}

	@Test
	fun `getAnsattDboMedOppdaterteRoller - har roller i database, men ingen i altinn - setter roller til ugyldig`() {
		ansatt = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
					veileder = emptyList(),
					koordinator = emptyList()
				)
			)
		)

		mockAltinnServer.addRoller(ansatt.personident, AltinnAclClient.ResponseWrapper(listOf()))

		val ansattDbo = rolleService.getAnsattDboMedOppdaterteRoller(ansatt)
			.also { it.isUpdated shouldBe true }
			.data

		ansattDbo.arrangorer.size shouldBe 1
		ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
		ansattDbo.arrangorer[0].roller[0].rolle shouldBe AnsattRolle.KOORDINATOR
		ansattDbo.arrangorer[0].roller[0].gyldigTil shouldNotBe null
		ansattDbo.arrangorer[0].roller[0].erGyldig() shouldBe false
	}

	@Test
	fun `getAnsattDboMedOppdaterteRoller - mister tilgang til arrangor og er veileder og har lagt til deltakerlister - fjerner roller og tilganger`() {
		val deltakerId = UUID.randomUUID()
		ansatt = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR), RolleDbo(AnsattRolle.VEILEDER)),
					veileder = listOf(VeilederDeltakerDbo(deltakerId, VeilederType.VEILEDER)),
					koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID()))
				)
			)
		)

		mockAltinnServer.addRoller(ansatt.personident, AltinnAclClient.ResponseWrapper(listOf()))

		val ansattDbo = rolleService.getAnsattDboMedOppdaterteRoller(ansatt)
			.also { it.isUpdated shouldBe true }
			.data

		ansattDbo.arrangorer.size shouldBe 1
		ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
		ansattDbo.arrangorer[0].roller.size shouldBe 2
		ansattDbo.arrangorer[0].roller.filter { it.erGyldig() }.size shouldBe 0
		ansattDbo.arrangorer[0].veileder[0].gyldigTil shouldNotBe null
		ansattDbo.arrangorer[0].koordinator[0].gyldigTil shouldNotBe null
		ansattDbo.arrangorer[0].koordinator[0].erGyldig() shouldBe false
	}

	@Test
	fun `getAnsattDboMedOppdaterteRoller - har deaktivert rolle blir tildelt ny rolle - legger til ny rolle fjerner ikke deaktiver rolle`() {
		ansatt = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR, gyldigTil = ZonedDateTime.now())),
					veileder = emptyList(),
					koordinator = listOf(KoordinatorsDeltakerlisteDbo(UUID.randomUUID()))
				)
			)
		)

		mockAltinnServer.addRoller(ansatt.personident, mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.KOORDINATOR)))

		val ansattDbo = rolleService.getAnsattDboMedOppdaterteRoller(ansatt)
			.also { it.isUpdated shouldBe true }
			.data

		ansattDbo.arrangorer.size shouldBe 1
		ansattDbo.arrangorer[0].arrangorId shouldBe arrangorOne.id
		ansattDbo.arrangorer[0].roller.size shouldBe 2
		ansattDbo.arrangorer[0].roller.any { it.rolle == AnsattRolle.KOORDINATOR && it.erGyldig() } shouldBe true
		ansattDbo.arrangorer[0].roller.any { it.rolle == AnsattRolle.KOORDINATOR && !it.erGyldig() } shouldBe true
	}
}
