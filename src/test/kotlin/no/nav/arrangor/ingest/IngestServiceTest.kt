package no.nav.arrangor.ingest

import io.kotest.matchers.shouldBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repositories.KoordinatorDeltakerlisteRepository
import no.nav.arrangor.ansatt.repositories.RolleRepository
import no.nav.arrangor.ansatt.repositories.VeilederDeltakerRepository
import no.nav.arrangor.domain.Ansatt
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.Arrangor
import no.nav.arrangor.domain.Navn
import no.nav.arrangor.domain.Personalia
import no.nav.arrangor.domain.TilknyttetArrangor
import no.nav.arrangor.domain.Veileder
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.dto.ArrangorDto
import no.nav.arrangor.ingest.mulighetsrommet.MulighetsrommetGjennomforingDto
import no.nav.arrangor.testutils.DbTestData
import no.nav.arrangor.testutils.DbTestDataUtils
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.LocalDate
import java.util.UUID
import javax.sql.DataSource

class IngestServiceTest : IntegrationTest() {

	@Autowired
	private lateinit var datasource: DataSource

	@Autowired
	private lateinit var ingestService: IngestService

	@Autowired
	private lateinit var koordinatorDeltakerlisteRepository: KoordinatorDeltakerlisteRepository

	@Autowired
	private lateinit var veilederDeltakerlisteRepository: VeilederDeltakerRepository

	@Autowired
	private lateinit var rolleRepository: RolleRepository

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
			getGjennomforing(arrangor.organisasjonsnummer),
			getGjennomforing(arrangor.organisasjonsnummer),
			getGjennomforing(arrangorTwo.organisasjonsnummer),
			getGjennomforing(arrangorTwo.organisasjonsnummer)
		)
		gjennomforinger.forEach { ingestService.handleGjennomforing(it) }

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
					koordinator = listOf(gjennomforinger[0].id, gjennomforinger[1].id)
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
					koordinator = listOf(gjennomforinger[2].id, gjennomforinger[3].id)
				)
			)
		)

		ingestService.handleAnsatt(ansatt)
		ingestService.handleAnsatt(ansatt)

		val koordinator = koordinatorDeltakerlisteRepository.getAll(ansatt.id)
		val veileder = veilederDeltakerlisteRepository.getAll(ansatt.id)
		val roller = rolleRepository.getAktiveRoller(ansatt.id)

		koordinator.size shouldBe 4
		veileder.size shouldBe 3
		roller.size shouldBe 3
	}

	fun getGjennomforing(orgnummer: String): MulighetsrommetGjennomforingDto {
		val id = UUID.randomUUID()
		return MulighetsrommetGjennomforingDto(
			id = id,
			tiltakstype = MulighetsrommetGjennomforingDto.Tiltakstype(
				id = UUID.randomUUID(),
				navn = "Oppfølging",
				arenaKode = "INDOPPFAG"
			),
			navn = "Gjennomføring $id",
			startDato = LocalDate.now().minusDays(5),
			sluttDato = null,
			status = MulighetsrommetGjennomforingDto.Status.GJENNOMFORES,
			virksomhetsnummer = orgnummer
		)
	}
}
