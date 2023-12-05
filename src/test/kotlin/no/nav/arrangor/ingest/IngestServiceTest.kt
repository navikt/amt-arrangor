package no.nav.arrangor.ingest

import io.kotest.matchers.date.shouldBeWithin
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import no.nav.arrangor.IntegrationTest
import no.nav.arrangor.ansatt.repository.AnsattDbo
import no.nav.arrangor.ansatt.repository.AnsattRepository
import no.nav.arrangor.ansatt.repository.ArrangorDbo
import no.nav.arrangor.ansatt.repository.RolleDbo
import no.nav.arrangor.ansatt.repository.VeilederDeltakerDbo
import no.nav.arrangor.arrangor.ArrangorRepository
import no.nav.arrangor.client.enhetsregister.Virksomhet
import no.nav.arrangor.domain.AnsattRolle
import no.nav.arrangor.domain.VeilederType
import no.nav.arrangor.ingest.model.AnsattPersonaliaDto
import no.nav.arrangor.ingest.model.DeltakerDto
import no.nav.arrangor.ingest.model.DeltakerStatus
import no.nav.arrangor.ingest.model.DeltakerStatusDto
import no.nav.arrangor.ingest.model.VirksomhetDto
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

class IngestServiceTest : IntegrationTest() {
	@Autowired
	private lateinit var datasource: DataSource

	@Autowired
	private lateinit var ingestService: IngestService

	@Autowired
	private lateinit var arrangorRepository: ArrangorRepository

	@Autowired
	private lateinit var ansattRepository: AnsattRepository

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
				overordnetArrangorId = null,
			),
		)

		val arrangorId = UUID.randomUUID()
		val orgnummer = "999988888"
		mockAmtEnhetsregiserServer.addVirksomhet(
			Virksomhet(
				organisasjonsnummer = orgnummer,
				navn = "Arrangør",
				overordnetEnhetOrganisasjonsnummer = overordnetOrgnummer,
				overordnetEnhetNavn = "Overordnet arrangør",
			),
		)
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = arrangorId,
				navn = UUID.randomUUID().toString(),
				organisasjonsnummer = orgnummer,
				overordnetArrangorId = overordnetArrangorId,
			),
		)

		ingestService.handleVirksomhetEndring(
			VirksomhetDto(
				organisasjonsnummer = orgnummer,
				navn = "Nytt navn",
				overordnetEnhetOrganisasjonsnummer = overordnetOrgnummer,
			),
		)

		val oppdatertArrangor = arrangorRepository.get(arrangorId)
		oppdatertArrangor?.navn shouldBe "Nytt navn"
		oppdatertArrangor?.overordnetArrangorId shouldBe overordnetArrangorId
	}

	@Test
	fun `handleVirksomhetEndring - finnes med annen overordnet arrangor, ny overordnet arrangor finnes - overordnet arrangor oppdateres`() {
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
				overordnetArrangorId = null,
			),
		)
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = nyOverordnetArrangorId,
				navn = "Ny overordnet navn",
				organisasjonsnummer = nyOverordnetOrgnummer,
				overordnetArrangorId = null,
			),
		)
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = arrangorId,
				navn = "Navn",
				organisasjonsnummer = orgnummer,
				overordnetArrangorId = overordnetArrangorId,
			),
		)

		ingestService.handleVirksomhetEndring(
			VirksomhetDto(
				organisasjonsnummer = orgnummer,
				navn = "Nytt navn",
				overordnetEnhetOrganisasjonsnummer = nyOverordnetOrgnummer,
			),
		)

		val oppdatertArrangor = arrangorRepository.get(arrangorId)
		oppdatertArrangor?.navn shouldBe "Nytt navn"
		oppdatertArrangor?.overordnetArrangorId shouldBe nyOverordnetArrangorId
	}

	@Test
	fun `handleVirksomhetEndring - finnes med annen overordnet arrangor,ny overordnet arrangor finnes ikke - ny overordnet arrangor lagres`() {
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
				overordnetEnhetNavn = null,
			),
		)
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = overordnetArrangorId,
				navn = "Overordnet navn",
				organisasjonsnummer = overordnetOrgnummer,
				overordnetArrangorId = null,
			),
		)
		arrangorRepository.insertOrUpdate(
			ArrangorRepository.ArrangorDbo(
				id = arrangorId,
				navn = "Navn",
				organisasjonsnummer = orgnummer,
				overordnetArrangorId = overordnetArrangorId,
			),
		)

		ingestService.handleVirksomhetEndring(
			VirksomhetDto(
				organisasjonsnummer = orgnummer,
				navn = "Nytt navn",
				overordnetEnhetOrganisasjonsnummer = nyOverordnetOrgnummer,
			),
		)

		val oppdatertArrangor = arrangorRepository.get(arrangorId)
		oppdatertArrangor?.navn shouldBe "Nytt navn"
		oppdatertArrangor?.overordnetArrangorId shouldNotBe overordnetArrangorId
		val nyOverordnetArrangor = arrangorRepository.get(nyOverordnetOrgnummer)
		nyOverordnetArrangor shouldNotBe null
		nyOverordnetArrangor?.navn shouldBe "Ny Overordnet arrangør"
		oppdatertArrangor?.overordnetArrangorId shouldBe nyOverordnetArrangor?.id
	}

	@Test
	fun `handleAnsattPersonalia - ansatt har endringer - oppdaterer ansatt`() {
		val ansatt =
			AnsattDbo(
				id = UUID.randomUUID(),
				personident = "123456",
				personId = UUID.randomUUID(),
				fornavn = "Test",
				mellomnavn = "Mellom",
				etternavn = "Testersen",
				arrangorer = emptyList(),
			)
		ansattRepository.insertOrUpdate(ansatt)

		val nyPersonalia =
			AnsattPersonaliaDto(
				ansatt.personId,
				"ny ident",
				"nytt",
				null,
				"navn",
			)

		ingestService.handleAnsattPersonalia(nyPersonalia)

		val oppdatertAnsatt = ansattRepository.get(ansatt.id)!!
		oppdatertAnsatt.personident shouldBe nyPersonalia.personident
		oppdatertAnsatt.fornavn shouldBe nyPersonalia.fornavn
		oppdatertAnsatt.mellomnavn shouldBe nyPersonalia.mellomnavn
		oppdatertAnsatt.etternavn shouldBe nyPersonalia.etternavn
	}

	@Test
	fun `handleAnsattPersonalia - ansatt har ikke endringer - oppdaterer ikke ansatt`() {
		val ansatt =
			AnsattDbo(
				id = UUID.randomUUID(),
				personident = "123456",
				personId = UUID.randomUUID(),
				fornavn = "Test",
				mellomnavn = "Mellom",
				etternavn = "Testersen",
				arrangorer = emptyList(),
				modifiedAt = LocalDateTime.now().minusMonths(1),
			)
		ansattRepository.insertOrUpdate(ansatt)

		val personalia =
			AnsattPersonaliaDto(
				ansatt.personId,
				ansatt.personident,
				ansatt.fornavn,
				ansatt.mellomnavn,
				ansatt.etternavn,
			)

		ingestService.handleAnsattPersonalia(personalia)

		val faktiskAnsatt = ansattRepository.get(ansatt.id)!!
		faktiskAnsatt.personident shouldBe ansatt.personident
		faktiskAnsatt.fornavn shouldBe ansatt.fornavn
		faktiskAnsatt.mellomnavn shouldBe ansatt.mellomnavn
		faktiskAnsatt.etternavn shouldBe ansatt.etternavn
		faktiskAnsatt.modifiedAt.shouldBeWithin(Duration.ofSeconds(1), ansatt.modifiedAt)
	}

	@Test
	fun `handleDeltakerEndret - avsluttende status, aktive veiledere - setter gyldigTil på aktive veiledere 20 dager frem i tid`() {
		val deltakerId1 = UUID.randomUUID()
		val deltakerId2 = UUID.randomUUID()
		val arrangor = UUID.randomUUID()

		val ansatt1 =
			veileder(
				arrangor,
				listOf(
					VeilederDeltakerDbo(deltakerId1, VeilederType.MEDVEILEDER),
					VeilederDeltakerDbo(deltakerId2, VeilederType.VEILEDER),
				),
			)
		val ansatt2 =
			veileder(
				arrangor,
				listOf(
					VeilederDeltakerDbo(deltakerId1, VeilederType.VEILEDER),
					VeilederDeltakerDbo(deltakerId2, VeilederType.MEDVEILEDER),
				),
			)
		ansattRepository.insertOrUpdate(ansatt1)
		ansattRepository.insertOrUpdate(ansatt2)

		val deltakerDto =
			DeltakerDto(
				id = deltakerId1,
				status = DeltakerStatusDto(DeltakerStatus.HAR_SLUTTET, LocalDateTime.now(), LocalDateTime.now()),
			)

		ingestService.handleDeltakerEndring(deltakerId1, deltakerDto)

		val forventetDeaktiveringsdato = ZonedDateTime.now().plusDays(20)

		val oppdatertAnsatt1 = ansattRepository.get(ansatt1.id)
		oppdatertAnsatt1?.arrangorer?.forEach { arr ->
			arr.veileder.find { it.deltakerId == deltakerId1 }!!
				.gyldigTil!!
				.shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)

			arr.veileder.find { it.deltakerId == deltakerId2 }!!.gyldigTil shouldBe null
		}

		val oppdatertAnsatt2 = ansattRepository.get(ansatt2.id)
		oppdatertAnsatt2?.arrangorer?.forEach { arr ->
			arr.veileder.find { it.deltakerId == deltakerId1 }!!
				.gyldigTil!!
				.shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)

			arr.veileder.find { it.deltakerId == deltakerId2 }!!.gyldigTil shouldBe null
		}
	}

	@Test
	fun `handleDeltakerEndret - skjult status, aktive veiledere - setter gyldigTil på aktive veiledere til 20 dager frem i tid`() {
		val deltakerId1 = UUID.randomUUID()
		val deltakerId2 = UUID.randomUUID()
		val arrangor = UUID.randomUUID()

		val ansatt1 =
			veileder(
				arrangor,
				listOf(
					VeilederDeltakerDbo(deltakerId1, VeilederType.MEDVEILEDER),
					VeilederDeltakerDbo(deltakerId2, VeilederType.VEILEDER),
				),
			)
		val ansatt2 =
			veileder(
				arrangor,
				listOf(
					VeilederDeltakerDbo(deltakerId1, VeilederType.VEILEDER),
					VeilederDeltakerDbo(deltakerId2, VeilederType.MEDVEILEDER),
				),
			)
		ansattRepository.insertOrUpdate(ansatt1)
		ansattRepository.insertOrUpdate(ansatt2)

		val deltakerDto =
			DeltakerDto(
				id = deltakerId1,
				status = DeltakerStatusDto(DeltakerStatus.PABEGYNT_REGISTRERING, LocalDateTime.now(), LocalDateTime.now()),
			)

		ingestService.handleDeltakerEndring(deltakerId1, deltakerDto)

		val forventetDeaktiveringsdato = ZonedDateTime.now().plusDays(20)

		val oppdatertAnsatt1 = ansattRepository.get(ansatt1.id)
		oppdatertAnsatt1?.arrangorer?.forEach { arr ->
			arr.veileder.find { it.deltakerId == deltakerId1 }!!
				.gyldigTil!!
				.shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)

			arr.veileder.find { it.deltakerId == deltakerId2 }!!.gyldigTil shouldBe null
		}

		val oppdatertAnsatt2 = ansattRepository.get(ansatt2.id)
		oppdatertAnsatt2?.arrangorer?.forEach { arr ->
			arr.veileder.find { it.deltakerId == deltakerId1 }!!
				.gyldigTil!!
				.shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)

			arr.veileder.find { it.deltakerId == deltakerId2 }!!.gyldigTil shouldBe null
		}
	}

	@Test
	fun `handleDeltakerEndret - deltaker slettet, aktive veiledere - setter gyldigTil på aktive veiledere til 20 dager frem i tid`() {
		val deltakerId1 = UUID.randomUUID()
		val arrangor = UUID.randomUUID()

		val ansatt1 =
			veileder(
				arrangor,
				listOf(
					VeilederDeltakerDbo(deltakerId1, VeilederType.MEDVEILEDER),
				),
			)
		val ansatt2 =
			veileder(
				arrangor,
				listOf(
					VeilederDeltakerDbo(deltakerId1, VeilederType.VEILEDER),
				),
			)
		ansattRepository.insertOrUpdate(ansatt1)
		ansattRepository.insertOrUpdate(ansatt2)

		val deltakerDto =
			DeltakerDto(
				id = deltakerId1,
				status = DeltakerStatusDto(DeltakerStatus.PABEGYNT_REGISTRERING, LocalDateTime.now(), LocalDateTime.now()),
			)

		ingestService.handleDeltakerEndring(deltakerId1, deltakerDto)

		val forventetDeaktiveringsdato = ZonedDateTime.now().plusDays(20)

		val oppdatertAnsatt1 = ansattRepository.get(ansatt1.id)
		oppdatertAnsatt1?.arrangorer?.forEach { arr ->
			arr.veileder.find { it.deltakerId == deltakerId1 }!!
				.gyldigTil!!
				.shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)
		}

		val oppdatertAnsatt2 = ansattRepository.get(ansatt2.id)
		oppdatertAnsatt2?.arrangorer?.forEach { arr ->
			arr.veileder.find { it.deltakerId == deltakerId1 }!!
				.gyldigTil!!
				.shouldBeWithin(Duration.ofSeconds(10), forventetDeaktiveringsdato)
		}
	}

	@Test
	fun `handleDeltakerEndret - avsluttet status, inaktive veiledere frem i tid, til DELTAR - fjerner ikke passert gyldigTil for veiledere`() {
		val deltakerId1 = UUID.randomUUID()
		val deltakerId2 = UUID.randomUUID()
		val arrangor = UUID.randomUUID()

		val ansatt1 =
			veileder(
				arrangor,
				listOf(
					VeilederDeltakerDbo(
						deltakerId1,
						VeilederType.MEDVEILEDER,
						gyldigTil = ZonedDateTime.now().plusDays(8),
					),
					VeilederDeltakerDbo(deltakerId2, VeilederType.VEILEDER),
				),
			)
		val ansatt2 =
			veileder(
				arrangor,
				listOf(
					VeilederDeltakerDbo(
						deltakerId1,
						VeilederType.VEILEDER,
						gyldigTil = ZonedDateTime.now().minusDays(2),
					),
					VeilederDeltakerDbo(deltakerId2, VeilederType.MEDVEILEDER),
				),
			)
		ansattRepository.insertOrUpdate(ansatt1)
		ansattRepository.insertOrUpdate(ansatt2)

		val deltakerDto =
			DeltakerDto(
				id = deltakerId1,
				status = DeltakerStatusDto(DeltakerStatus.DELTAR, LocalDateTime.now(), LocalDateTime.now()),
			)

		ingestService.handleDeltakerEndring(deltakerId1, deltakerDto)

		val oppdatertAnsatt1 = ansattRepository.get(ansatt1.id)
		oppdatertAnsatt1?.arrangorer?.forEach { arr ->
			arr.veileder.find { it.deltakerId == deltakerId1 }!!
				.gyldigTil
				.shouldBe(null)

			arr.veileder.find { it.deltakerId == deltakerId2 }!!.gyldigTil shouldBe null
		}

		val oppdatertAnsatt2 = ansattRepository.get(ansatt2.id)
		oppdatertAnsatt2?.arrangorer?.forEach { arr ->
			arr.veileder.find { it.deltakerId == deltakerId1 }!!
				.gyldigTil!!
				.shouldBeWithin(Duration.ofSeconds(10), ZonedDateTime.now().minusDays(2))

			arr.veileder.find { it.deltakerId == deltakerId2 }!!.gyldigTil shouldBe null
		}
	}

	private fun veileder(
		arrangor: UUID,
		veilderDeltakere: List<VeilederDeltakerDbo>,
	): AnsattDbo {
		return db.ansatt(
			arrangorer =
				listOf(
					ArrangorDbo(
						arrangor,
						listOf(RolleDbo(AnsattRolle.VEILEDER, ZonedDateTime.now().minusDays(7), null)),
						veilderDeltakere,
						emptyList(),
					),
				),
		)
	}
}
