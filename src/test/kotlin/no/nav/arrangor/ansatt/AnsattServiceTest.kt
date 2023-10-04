package no.nav.arrangor.ansatt

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.MetricsService
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.KoordinatorsDeltakerlisteDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.arrangor.ArrangorService
import no.nav.arrangor.client.person.PersonClient
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.ingest.PublishService
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.ZonedDateTime
import java.util.UUID
import javax.sql.DataSource

class AnsattServiceTest : IntegrationTest() {
	val publishService = mockk<PublishService>(relaxed = true)
	val metricsService = mockk<MetricsService>(relaxed = true)

	@Autowired
	lateinit var personClient: PersonClient

	@Autowired
	lateinit var rolleService: AnsattRolleService

	@Autowired
	lateinit var ansattRepository: AnsattRepository

	@Autowired
	lateinit var arrangorService: ArrangorService

	lateinit var ansattService: AnsattService

	@Autowired
	lateinit var dataSource: DataSource

	lateinit var db: DbTestData
	lateinit var arrangorOne: ArrangorRepository.ArrangorDbo
	lateinit var arrangorTwo: ArrangorRepository.ArrangorDbo

	@BeforeEach
	fun setUp() {
		resetMockServers()
		clearMocks(publishService)
		db = DbTestData(NamedParameterJdbcTemplate(dataSource))
		ansattService =
			AnsattService(personClient, ansattRepository, rolleService, publishService, metricsService, arrangorService)

		arrangorOne = db.insertArrangor()
		arrangorTwo = db.insertArrangor()
	}

	@AfterEach
	fun tearDown() {
		DbTestDataUtils.cleanDatabase(dataSource)
	}

	@Test
	fun `oppdaterRoller - ansatt mister eneste rolle hos arrangor - ansatt lagres med deaktivert rolle og publiseres uten arrangoren`() {
		val ansattDbo = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(arrangorOne.id, listOf(RolleDbo(AnsattRolle.VEILEDER)), emptyList(), emptyList()),
				ArrangorDbo(arrangorTwo.id, listOf(RolleDbo(AnsattRolle.VEILEDER)), emptyList(), emptyList())
			)
		)
		mockAltinnServer.addRoller(
			ansattDbo.personident,
			mapOf(arrangorTwo.organisasjonsnummer to listOf(AnsattRolle.VEILEDER))
		)

		ansattService.oppdaterRoller(ansattDbo)

		val oppdatertAnsatt = ansattRepository.get(ansattDbo.id)
		oppdatertAnsatt?.arrangorer?.size shouldBe 2
		oppdatertAnsatt?.arrangorer?.find { it.arrangorId == arrangorOne.id }?.roller?.first()
			?.erGyldig() shouldBe false
		oppdatertAnsatt?.arrangorer?.find { it.arrangorId == arrangorTwo.id }?.roller?.first()?.erGyldig() shouldBe true

		verify(exactly = 1) { publishService.publishAnsatt(match { it.arrangorer.size == 1 && it.arrangorer.first().arrangorId == arrangorTwo.id }) }
	}

	@Test
	fun `oppdaterRoller - ansatt mister en rolle hos arrangor - ansatt lagres med deaktivert rolle og publiseres uten rollen`() {
		val ansattDbo = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorOne.id,
					listOf(RolleDbo(AnsattRolle.VEILEDER), RolleDbo(AnsattRolle.KOORDINATOR)),
					emptyList(),
					emptyList()
				)
			)
		)
		mockAltinnServer.addRoller(
			ansattDbo.personident,
			mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.VEILEDER))
		)

		ansattService.oppdaterRoller(ansattDbo)

		val oppdatertAnsatt = ansattRepository.get(ansattDbo.id)
		oppdatertAnsatt?.arrangorer?.size shouldBe 1
		oppdatertAnsatt?.arrangorer?.first()?.roller?.size shouldBe 2
		oppdatertAnsatt?.arrangorer?.first()?.roller?.find { it.rolle == AnsattRolle.KOORDINATOR }
			?.erGyldig() shouldBe false
		oppdatertAnsatt?.arrangorer?.first()?.roller?.find { it.rolle == AnsattRolle.VEILEDER }
			?.erGyldig() shouldBe true

		verify(exactly = 1) {
			publishService.publishAnsatt(
				match {
					it.arrangorer.first().roller.size == 1 &&
						it.arrangorer.first().roller.first() == AnsattRolle.VEILEDER
				}
			)
		}
	}

	@Test
	fun `oppdaterRoller - ansatt mister en rolle hos arrangor - ansatt lagres med deaktivert rolle og publiseres uten koordinator for`() {
		val koordinatorFor = UUID.randomUUID()
		val ansattDbo = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorOne.id,
					listOf(RolleDbo(AnsattRolle.VEILEDER), RolleDbo(AnsattRolle.KOORDINATOR)),
					emptyList(),
					listOf(
						KoordinatorsDeltakerlisteDbo(koordinatorFor)
					)
				)
			)
		)
		mockAltinnServer.addRoller(ansattDbo.personident, mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.VEILEDER)))

		ansattService.oppdaterRoller(ansattDbo)

		val oppdatertAnsatt = ansattRepository.get(ansattDbo.id)
		oppdatertAnsatt?.arrangorer?.size shouldBe 1
		oppdatertAnsatt?.arrangorer?.first()?.roller?.size shouldBe 2
		oppdatertAnsatt?.arrangorer?.first()?.roller?.find { it.rolle == AnsattRolle.KOORDINATOR }?.erGyldig() shouldBe false
		oppdatertAnsatt?.arrangorer?.first()?.roller?.find { it.rolle == AnsattRolle.VEILEDER }?.erGyldig() shouldBe true
		oppdatertAnsatt?.arrangorer?.first()?.koordinator?.first()?.erGyldig() shouldBe false

		verify(exactly = 1) {
			publishService.publishAnsatt(
				match {
					it.arrangorer.first().roller.size == 1 &&
						it.arrangorer.first().roller.first() == AnsattRolle.VEILEDER &&
						it.arrangorer.first().koordinator.isEmpty()
				}
			)
		}
	}

	@Test
	fun `oppdaterRoller - ansatt får tilbake deaktivert rolle - oppretter ny rolle og publiserer ansatt`() {
		val ansattDbo = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorOne.id,
					listOf(RolleDbo(AnsattRolle.VEILEDER, gyldigTil = ZonedDateTime.now().minusDays(7))),
					emptyList(),
					emptyList(),
				)
			)
		)
		mockAltinnServer.addRoller(ansattDbo.personident, mapOf(arrangorOne.organisasjonsnummer to listOf(AnsattRolle.VEILEDER)))

		ansattService.oppdaterRoller(ansattDbo)

		val oppdatertAnsatt = ansattRepository.get(ansattDbo.id)
		oppdatertAnsatt?.arrangorer?.size shouldBe 1
		oppdatertAnsatt?.arrangorer?.first()?.roller?.size shouldBe 2
		oppdatertAnsatt?.arrangorer?.first()?.roller?.any { it.rolle == AnsattRolle.VEILEDER && it.erGyldig() } shouldBe true
		oppdatertAnsatt?.arrangorer?.first()?.roller?.any { it.rolle == AnsattRolle.VEILEDER && !it.erGyldig() } shouldBe true

		verify(exactly = 1) {
			publishService.publishAnsatt(
				match {
					it.arrangorer.first().roller.size == 1 &&
						it.arrangorer.first().roller.first() == AnsattRolle.VEILEDER &&
						it.arrangorer.first().koordinator.isEmpty()
				}
			)
		}

	}

	@Test
	fun `oppdaterVeiledereForDeltaker - ansatt har ikke koordinatortilgang - kaster feil`() {
		val ansatt = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorOne.id,
					listOf(RolleDbo(AnsattRolle.VEILEDER)),
					emptyList(),
					emptyList()
				)
			)
		)

		assertThrows<IllegalArgumentException> {
			ansattService.oppdaterVeiledereForDeltaker(
				ansatt.personident,
				UUID.randomUUID(),
				AnsattController.OppdaterVeiledereForDeltakerRequest(
					arrangorId = arrangorOne.id,
					veilederSomLeggesTil = listOf(
						AnsattController.VeilederAnsatt(
							UUID.randomUUID(),
							VeilederType.VEILEDER
						)
					),
					veilederSomFjernes = emptyList()
				)
			)
		}
	}

	@Test
	fun `oppdaterVeiledereForDeltaker - skal legge til ansatt som veileder - ansatt blir oppdatert`() {
		val deltakerId = UUID.randomUUID()
		val koordinator = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
					veileder = emptyList(),
					koordinator = emptyList()
				)
			)
		)
		val veileder1 = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
					veileder = listOf(
						VeilederDeltakerDbo(
							deltakerId = deltakerId,
							veilederType = VeilederType.MEDVEILEDER
						)
					),
					koordinator = emptyList()
				)
			)
		)
		val veileder2 = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
					veileder = emptyList(),
					koordinator = emptyList()
				)
			)
		)
		val request = AnsattController.OppdaterVeiledereForDeltakerRequest(
			arrangorId = arrangorOne.id,
			veilederSomLeggesTil = listOf(
				AnsattController.VeilederAnsatt(
					veileder2.id,
					VeilederType.VEILEDER
				)
			),
			veilederSomFjernes = emptyList()
		)

		ansattService.oppdaterVeiledereForDeltaker(koordinator.personident, deltakerId, request)

		val veileder1Db = ansattRepository.get(veileder1.id)
		val veileder1Arrangor = veileder1Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
		veileder1Arrangor?.veileder?.size shouldBe 1
		veileder1Arrangor?.veileder?.find { it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && it.erGyldig() } shouldNotBe null

		val veileder2Db = ansattRepository.get(veileder2.id)
		val veileder2Arrangor = veileder2Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
		veileder2Arrangor?.veileder?.size shouldBe 1
		veileder2Arrangor?.veileder?.find { it.deltakerId == deltakerId && it.veilederType == VeilederType.VEILEDER && it.erGyldig() } shouldNotBe null
	}

	@Test
	fun `oppdaterVeiledereForDeltaker - skal fjerne ansatt som veileder - ansatt blir oppdatert`() {
		val deltakerId = UUID.randomUUID()
		val koordinator = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
					veileder = emptyList(),
					koordinator = emptyList()
				)
			)
		)
		val veileder1 = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
					veileder = listOf(
						VeilederDeltakerDbo(
							deltakerId = deltakerId,
							veilederType = VeilederType.MEDVEILEDER
						)
					),
					koordinator = emptyList()
				)
			)
		)
		val veileder2 = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
					veileder = listOf(
						VeilederDeltakerDbo(
							deltakerId = deltakerId,
							veilederType = VeilederType.VEILEDER
						)
					),
					koordinator = emptyList()
				)
			)
		)
		val request = AnsattController.OppdaterVeiledereForDeltakerRequest(
			arrangorId = arrangorOne.id,
			veilederSomLeggesTil = emptyList(),
			veilederSomFjernes = listOf(
				AnsattController.VeilederAnsatt(
					veileder1.id,
					VeilederType.MEDVEILEDER
				)
			)
		)

		ansattService.oppdaterVeiledereForDeltaker(koordinator.personident, deltakerId, request)

		val veileder1Db = ansattRepository.get(veileder1.id)
		val veileder1Arrangor = veileder1Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
		veileder1Arrangor?.veileder?.size shouldBe 1
		veileder1Arrangor?.veileder?.find { it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && it.erGyldig() } shouldBe null
		veileder1Arrangor?.veileder?.find { it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && !it.erGyldig() } shouldNotBe null

		val veileder2Db = ansattRepository.get(veileder2.id)
		val veileder2Arrangor = veileder2Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
		veileder2Arrangor?.veileder?.size shouldBe 1
		veileder2Arrangor?.veileder?.find { it.deltakerId == deltakerId && it.veilederType == VeilederType.VEILEDER && it.erGyldig() } shouldNotBe null
	}

	@Test
	fun `oppdaterVeiledereForDeltaker - skal legge til og fjerne ansatt som veileder - ansatte blir oppdatert`() {
		val deltakerId = UUID.randomUUID()
		val koordinator = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
					veileder = emptyList(),
					koordinator = emptyList()
				)
			)
		)
		val veileder1 = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
					veileder = listOf(
						VeilederDeltakerDbo(
							deltakerId = deltakerId,
							veilederType = VeilederType.MEDVEILEDER
						)
					),
					koordinator = emptyList()
				)
			)
		)
		val veileder2 = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
					veileder = listOf(
						VeilederDeltakerDbo(
							deltakerId = deltakerId,
							veilederType = VeilederType.VEILEDER
						)
					),
					koordinator = emptyList()
				)
			)
		)
		val veileder3 = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
					veileder = emptyList(),
					koordinator = emptyList()
				)
			)
		)
		val request = AnsattController.OppdaterVeiledereForDeltakerRequest(
			arrangorId = arrangorOne.id,
			veilederSomLeggesTil = listOf(
				AnsattController.VeilederAnsatt(
					veileder1.id,
					VeilederType.VEILEDER
				),
				AnsattController.VeilederAnsatt(
					veileder2.id,
					VeilederType.MEDVEILEDER
				),
				AnsattController.VeilederAnsatt(
					veileder3.id,
					VeilederType.MEDVEILEDER
				)
			),
			veilederSomFjernes = listOf(
				AnsattController.VeilederAnsatt(
					veileder1.id,
					VeilederType.MEDVEILEDER
				),
				AnsattController.VeilederAnsatt(
					veileder2.id,
					VeilederType.VEILEDER
				)
			)
		)

		ansattService.oppdaterVeiledereForDeltaker(koordinator.personident, deltakerId, request)

		val veileder1Db = ansattRepository.get(veileder1.id)
		val veileder1Arrangor = veileder1Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
		veileder1Arrangor?.veileder?.size shouldBe 2
		veileder1Arrangor?.veileder?.find { it.deltakerId == deltakerId && it.veilederType == VeilederType.VEILEDER && it.erGyldig() } shouldNotBe null
		veileder1Arrangor?.veileder?.find { it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && !it.erGyldig() } shouldNotBe null

		val veileder2Db = ansattRepository.get(veileder2.id)
		val veileder2Arrangor = veileder2Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
		veileder2Arrangor?.veileder?.size shouldBe 2
		veileder2Arrangor?.veileder?.find { it.deltakerId == deltakerId && it.veilederType == VeilederType.VEILEDER && !it.erGyldig() } shouldNotBe null
		veileder2Arrangor?.veileder?.find { it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && it.erGyldig() } shouldNotBe null

		val veileder3Db = ansattRepository.get(veileder3.id)
		val veileder3Arrangor = veileder3Db?.arrangorer?.find { it.arrangorId == arrangorOne.id }
		veileder3Arrangor?.veileder?.size shouldBe 1
		veileder3Arrangor?.veileder?.find { it.deltakerId == deltakerId && it.veilederType == VeilederType.MEDVEILEDER && it.erGyldig() } shouldNotBe null
	}

	@Test
	fun `fjernKoordinatorForDeltakerliste - skal fjerne sette gyldigTil til nå`() {
		val deltakerliste = KoordinatorsDeltakerlisteDbo(UUID.randomUUID())
		val koordinator = db.insertAnsatt(
			arrangorer = listOf(
				ArrangorDbo(
					arrangorId = arrangorOne.id,
					roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR)),
					veileder = emptyList(),
					koordinator = listOf(deltakerliste)
				)
			)
		)

		ansattService.fjernKoordinatorForDeltakerliste(
			koordinator.personident,
			koordinator.arrangorer[0].arrangorId,
			deltakerliste.deltakerlisteId
		)

		ansattRepository.get(koordinator.id)!!.arrangorer[0].koordinator[0].gyldigTil shouldNotBe null
	}

	@Test
	fun `fjernTilgangerHosArrangor - koordinator skal fjernes`() {
		val deltakerliste = UUID.randomUUID()
		val arrangor = db.ansattArrangorDbo(koordinator = listOf(KoordinatorsDeltakerlisteDbo(deltakerliste)))
		val ansatt = db.insertAnsatt(arrangorer = listOf(arrangor))

		ansattService.fjernTilgangerHosArrangor(deltakerliste, emptyList(), arrangor.arrangorId)
		ansattRepository.get(ansatt.id)!!.arrangorer[0].koordinator[0].gyldigTil shouldNotBe null
	}

	@Test
	fun `fjernTilgangerHosArrangor - ansatt er koordinator for flere lister hos arrangor - riktig tilgang skal fjernes`() {
		val deltakerliste1 = UUID.randomUUID()
		val deltakerliste2 = UUID.randomUUID()

		val arrangor = db.ansattArrangorDbo(
			koordinator = listOf(
				KoordinatorsDeltakerlisteDbo(deltakerliste1),
				KoordinatorsDeltakerlisteDbo(deltakerliste2)
			)
		)
		val ansatt = db.insertAnsatt(arrangorer = listOf(arrangor))

		ansattService.fjernTilgangerHosArrangor(deltakerliste1, emptyList(), arrangor.arrangorId)

		val ansattArrangorTilganger = ansattRepository.get(ansatt.id)!!.arrangorer.find { it.arrangorId == arrangor.arrangorId }!!
		ansattArrangorTilganger.koordinator.first { it.deltakerlisteId == deltakerliste1 }.erGyldig() shouldBe false
		ansattArrangorTilganger.koordinator.first { it.deltakerlisteId == deltakerliste2 }.erGyldig() shouldBe true
	}

	@Test
	fun `fjernTilgangerHosArrangor - veileder skal fjernes`() {
		val deltaker = UUID.randomUUID()
		val arrangor = db.ansattArrangorDbo(
			roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
			veileder = listOf(VeilederDeltakerDbo(deltaker, VeilederType.VEILEDER))
		)
		val ansatt = db.insertAnsatt(arrangorer = listOf(arrangor))

		ansattService.fjernTilgangerHosArrangor(UUID.randomUUID(), listOf(deltaker), arrangor.arrangorId)
		ansattRepository.get(ansatt.id)!!.arrangorer[0].veileder[0].gyldigTil shouldNotBe null
	}

	@Test
	fun `fjernTilgangerHosArrangor - veileder har flere tilganger hos arrangor - kun tilgang til gitte deltakere skal fjernes`() {
		val deltaker1 = UUID.randomUUID()
		val deltaker2 = UUID.randomUUID()
		val deltaker3 = UUID.randomUUID()

		val arrangor1 = db.ansattArrangorDbo(
			roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
			veileder = listOf(
				VeilederDeltakerDbo(deltaker1, VeilederType.VEILEDER),
				VeilederDeltakerDbo(deltaker2, VeilederType.VEILEDER)
			)
		)
		val arrangor2 = db.ansattArrangorDbo(
			roller = listOf(RolleDbo(AnsattRolle.VEILEDER)),
			veileder = listOf(
				VeilederDeltakerDbo(deltaker3, VeilederType.VEILEDER)
			)
		)
		val ansatt = db.insertAnsatt(arrangorer = listOf(arrangor1, arrangor2))

		ansattService.fjernTilgangerHosArrangor(UUID.randomUUID(), listOf(deltaker1, deltaker2), arrangor1.arrangorId)
		val ansattArrangor1Tilganger = ansattRepository.get(ansatt.id)!!.arrangorer.find { it.arrangorId == arrangor1.arrangorId }!!

		ansattArrangor1Tilganger.veileder.first { it.deltakerId == deltaker1 }.erGyldig() shouldBe false
		ansattArrangor1Tilganger.veileder.first { it.deltakerId == deltaker2 }.erGyldig() shouldBe false

		val ansattArrangor2Tilganger = ansattRepository.get(ansatt.id)!!.arrangorer.find { it.arrangorId == arrangor2.arrangorId }!!

		ansattArrangor2Tilganger.veileder.first { it.deltakerId == deltaker3 }.erGyldig() shouldBe true
	}

	@Test
	fun `fjernTilgangerHosArrangor - ansatt er koordinator og veileder hos arrangor - fjerner riktige tilganger`() {
		val deltaker1 = UUID.randomUUID()

		val deltakerliste = UUID.randomUUID()

		val arrangor1 = db.ansattArrangorDbo(
			roller = listOf(RolleDbo(AnsattRolle.KOORDINATOR), RolleDbo(AnsattRolle.VEILEDER)),
			veileder = listOf(
				VeilederDeltakerDbo(deltaker1, VeilederType.VEILEDER)
			),
			koordinator = listOf(KoordinatorsDeltakerlisteDbo(deltakerliste))
		)
		val ansatt = db.insertAnsatt(arrangorer = listOf(arrangor1))

		ansattService.fjernTilgangerHosArrangor(deltakerliste, listOf(deltaker1), arrangor1.arrangorId)
		val ansattArrangor1Tilganger = ansattRepository.get(ansatt.id)!!.arrangorer.find { it.arrangorId == arrangor1.arrangorId }!!

		ansattArrangor1Tilganger.veileder.first { it.deltakerId == deltaker1 }.erGyldig() shouldBe false
		ansattArrangor1Tilganger.koordinator.first { it.deltakerlisteId == deltakerliste }.erGyldig() shouldBe false
	}
}
