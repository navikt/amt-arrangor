package no.nav.arrangor.ingest

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.domain.Navn
import no.nav.arrangor.domain.Personalia
import no.nav.arrangor.domain.TilknyttetArrangor
import no.nav.arrangor.domain.Veileder
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.dto.ArrangorDto
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
	private lateinit var ansattRepository: AnsattRepository

	private lateinit var db: DbTestData

	@BeforeEach
	fun setUp() {
		db = DbTestData(NamedParameterJdbcTemplate(datasource))
	}

	@AfterEach
	fun tearDown() {
		DbTestDataUtils.cleanDatabase(datasource)
		resetMockServers()
	}

	@Test
	fun `ingest ansatt - not exists`() {
		val arrangor = ArrangorDto(
			id = UUID.randomUUID(),
			navn = UUID.randomUUID().toString(),
			organisasjonsnummer = UUID.randomUUID().toString(),
			overordnetArrangorId = null
		).also { ingestService.handleArrangor(it) }

		val arrangorTwo = ArrangorDto(
			id = UUID.randomUUID(),
			navn = UUID.randomUUID().toString(),
			organisasjonsnummer = UUID.randomUUID().toString(),
			overordnetArrangorId = null
		).also { ingestService.handleArrangor(it) }
		val gjennomforinger = listOf(
			UUID.randomUUID(),
			UUID.randomUUID(),
			UUID.randomUUID(),
			UUID.randomUUID()
		)

		val ansatt = Ansatt(
			id = UUID.randomUUID(),
			personalia = Personalia(
				personident = UUID.randomUUID().toString(),
				personId = UUID.randomUUID(),
				navn = Navn(
					fornavn = UUID.randomUUID().toString(),
					mellomnavn = null,
					etternavn = UUID.randomUUID().toString()
				)
			),
			arrangorer = listOf(
				TilknyttetArrangor(
					arrangorId = arrangor.id,
					arrangor = Arrangor(
						id = arrangor.id,
						navn = arrangor.navn,
						organisasjonsnummer = arrangor.organisasjonsnummer,
						overordnetArrangorId = null
					),
					overordnetArrangor = null,
					roller = listOf(AnsattRolle.KOORDINATOR, AnsattRolle.VEILEDER),
					veileder = listOf(
						Veileder(deltakerId = UUID.randomUUID(), type = VeilederType.VEILEDER),
						Veileder(deltakerId = UUID.randomUUID(), type = VeilederType.MEDVEILEDER)
					),
					koordinator = listOf(gjennomforinger[0], gjennomforinger[1])
				),
				TilknyttetArrangor(
					arrangorId = arrangorTwo.id,
					arrangor = Arrangor(
						id = arrangorTwo.id,
						navn = arrangorTwo.navn,
						organisasjonsnummer = arrangorTwo.organisasjonsnummer,
						overordnetArrangorId = null
					),
					overordnetArrangor = null,
					roller = listOf(AnsattRolle.VEILEDER),
					veileder = listOf(
						Veileder(deltakerId = UUID.randomUUID(), type = VeilederType.MEDVEILEDER)
					),
					koordinator = listOf(gjennomforinger[2], gjennomforinger[3])
				)
			)
		)

		ingestService.handleAnsatt(ansatt)
		ingestService.handleAnsatt(ansatt)

		val ansattDbo = ansattRepository.get(ansatt.id) ?: throw RuntimeException("Fant ikke ansatt")

		ansattDbo.arrangorer.size shouldBe 2
		val arrangorDb = ansattDbo.arrangorer.find { it.arrangorId == arrangor.id }
		arrangorDb shouldNotBe null
		arrangorDb!!.roller.size shouldBe 2
		arrangorDb.veileder.size shouldBe 2
		arrangorDb.koordinator.size shouldBe 1

		val arrangorTwoDb = ansattDbo.arrangorer.find { it.arrangorId == arrangorTwo.id }
		arrangorTwoDb shouldNotBe null
		arrangorTwoDb!!.roller.size shouldBe 1
		arrangorTwoDb.veileder.size shouldBe 1
		arrangorTwoDb.koordinator.size shouldBe 2
	}
}
