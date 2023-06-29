package no.nav.arrangor.ansatt

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.util.UUID
import javax.sql.DataSource

class AnsattServiceTest : IntegrationTest() {
	@Autowired
	lateinit var ansattRepository: AnsattRepository

	@Autowired
	lateinit var ansattService: AnsattService

	@Autowired
	lateinit var dataSource: DataSource

	lateinit var db: DbTestData
	lateinit var arrangorOne: ArrangorRepository.ArrangorDbo

	@BeforeEach
	fun setUp() {
		db = DbTestData(NamedParameterJdbcTemplate(dataSource))

		arrangorOne = db.insertArrangor()
	}

	@AfterEach
	fun tearDown() {
		DbTestDataUtils.cleanDatabase(dataSource)
	}

	@Test
	fun `oppdaterVeiledereForDeltaker - ansatt har ikke koordinatortilgang - kaster feil`() {
		val ansatt = db.insertAnsatt(arrangorer = listOf(ArrangorDbo(arrangorOne.id, listOf(RolleDbo(AnsattRolle.VEILEDER)), emptyList(), emptyList())))

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
}
